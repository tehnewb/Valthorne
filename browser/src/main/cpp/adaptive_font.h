#pragma once
#include <cstdint>
#include <cstring>
#include <cstdlib>
#include <cstddef>
#include <ft2build.h>
#include FT_FREETYPE_H
#include FT_TRUETYPE_TABLES_H
#include <hb.h>
#include <hb-ot.h>
#include <msdfgen.h>
#include <ext/import-font.h>
#include <vector>
#include <cmath>
#include <algorithm>
#include <new>

struct BrowserShapedGlyph {
    uint32_t glyph;
    uint32_t cluster;
    float advance;
    float x;
    float y;
};
static_assert(sizeof(BrowserShapedGlyph) == 20, "Review shared font shaping layout");

struct BrowserAdaptiveFont {
    std::vector<uint8_t> bytes;
    std::vector<BrowserShapedGlyph> shaped;
    FT_Library library = nullptr;
    FT_Face face = nullptr;
    hb_font_t* font = nullptr;
    hb_buffer_t* buffer = nullptr;
    msdfgen::FontHandle* distanceFont = nullptr;
    ~BrowserAdaptiveFont() {
        if (distanceFont) msdfgen::destroyFont(distanceFont);
        if (buffer) hb_buffer_destroy(buffer);
        if (font) hb_font_destroy(font);
        if (face) FT_Done_Face(face);
        if (library) FT_Done_FreeType(library);
    }
};

static uint32_t fontFloatWord(float value) {
    uint32_t word;
    std::memcpy(&word, &value, sizeof(word));
    return word;
}

static uint32_t adaptiveFontCommand(uint32_t operation, uint32_t* words, const uint8_t* bytes, uint32_t length) {
    words[7] = 0;
    if (operation == ADAPTIVE_FONT_SCRIPT)
        return hb_unicode_script(hb_unicode_funcs_get_default(), words[0]);
    if (operation == ADAPTIVE_FONT_CREATE) {
        if (length < 12 || std::memcmp(bytes, "wOF2", 4) == 0) return 0;
        auto* value = new (std::nothrow) BrowserAdaptiveFont();
        if (!value) return 0;
        value->bytes.assign(bytes, bytes + length);
        if (FT_Init_FreeType(&value->library) || FT_New_Memory_Face(value->library, value->bytes.data(), value->bytes.size(), 0, &value->face)
                || !FT_IS_SFNT(value->face) || value->face->units_per_EM == 0) { delete value; return 0; }
        if (std::memcmp(bytes, "wOFF", 4) == 0) {
            FT_ULong size = 0;
            if (FT_Load_Sfnt_Table(value->face, 0, 0, nullptr, &size) || size == 0) { delete value; return 0; }
            std::vector<uint8_t> decoded(size);
            if (FT_Load_Sfnt_Table(value->face, 0, 0, decoded.data(), &size)) { delete value; return 0; }
            FT_Done_Face(value->face);
            value->face = nullptr;
            value->bytes.swap(decoded);
            if (FT_New_Memory_Face(value->library, value->bytes.data(), value->bytes.size(), 0, &value->face)) { delete value; return 0; }
        }
        hb_blob_t* blob = hb_blob_create(reinterpret_cast<const char*>(value->bytes.data()), value->bytes.size(), HB_MEMORY_MODE_READONLY, nullptr, nullptr);
        hb_face_t* face = hb_face_create(blob, 0);
        value->font = hb_font_create(face);
        hb_ot_font_set_funcs(value->font);
        hb_font_set_scale(value->font, value->face->units_per_EM, value->face->units_per_EM);
        hb_font_make_immutable(value->font);
        value->buffer = hb_buffer_create();
        bool valid = hb_face_get_upem(face) > 0 && hb_face_get_glyph_count(face) > 0 && hb_buffer_allocation_successful(value->buffer);
        hb_face_destroy(face);
        hb_blob_destroy(blob);
        if (!valid) { delete value; return 0; }
        return reinterpret_cast<uintptr_t>(value);
    }
    auto* value = reinterpret_cast<BrowserAdaptiveFont*>(words[0]);
    if (!value) { words[7] = 1; return 0; }
    if (operation == ADAPTIVE_FONT_FREE) { delete value; return 0; }
    const float inverseEm = 1.f / value->face->units_per_EM;
    if (operation == ADAPTIVE_FONT_METRICS) {
        hb_codepoint_t space = 0;
        hb_font_get_nominal_glyph(value->font, ' ', &space);
        words[0] = fontFloatWord(value->face->ascender * inverseEm);
        words[1] = fontFloatWord(value->face->descender * inverseEm);
        words[2] = fontFloatWord(std::max((value->face->ascender - value->face->descender) * inverseEm, value->face->height * inverseEm));
        words[3] = fontFloatWord(hb_font_get_glyph_h_advance(value->font, space) * inverseEm);
        return 0;
    }
    if (operation == ADAPTIVE_FONT_NOMINAL) {
        hb_codepoint_t glyph = 0;
        return hb_font_get_nominal_glyph(value->font, words[1], &glyph) ? glyph : 0;
    }
    if (operation == ADAPTIVE_FONT_SHAPE) {
        uint32_t start = words[1], end = words[2];
        if (length % 2 || start > end || end > length / 2) { words[7] = 1; return 0; }
        hb_buffer_reset(value->buffer);
        hb_buffer_add_utf16(value->buffer, reinterpret_cast<const uint16_t*>(bytes), length / 2, start, end - start);
        hb_buffer_set_direction(value->buffer, words[3] ? HB_DIRECTION_RTL : HB_DIRECTION_LTR);
        hb_buffer_set_language(value->buffer, hb_language_from_string("und", -1));
        hb_buffer_guess_segment_properties(value->buffer);
        hb_shape(value->font, value->buffer, nullptr, 0);
        if (!hb_buffer_allocation_successful(value->buffer)) { words[7] = 1; return 0; }
        unsigned count = hb_buffer_get_length(value->buffer);
        const hb_glyph_info_t* info = hb_buffer_get_glyph_infos(value->buffer, nullptr);
        const hb_glyph_position_t* positions = hb_buffer_get_glyph_positions(value->buffer, nullptr);
        value->shaped.resize(count);
        for (unsigned i = 0; i < count; i++)
            value->shaped[i] = {info[i].codepoint, info[i].cluster, positions[i].x_advance * inverseEm, positions[i].x_offset * inverseEm, positions[i].y_offset * inverseEm};
        words[0] = count;
        return reinterpret_cast<uintptr_t>(value->shaped.data());
    }
    if (operation != ADAPTIVE_FONT_RASTER) { words[7] = 1; return 0; }
    const uint32_t glyph = words[1], size = words[2], flags = words[3];
    words[0] = words[1] = 0;
    if (size) {
        if (size > 4096 || FT_Set_Pixel_Sizes(value->face, 0, size) || FT_Load_Glyph(value->face, glyph, FT_LOAD_NO_BITMAP | flags)
                || FT_Render_Glyph(value->face->glyph, FT_RENDER_MODE_NORMAL)) { words[7] = 1; return 0; }
        const FT_Bitmap& bitmap = value->face->glyph->bitmap;
        if (bitmap.width == 0 || bitmap.rows == 0) return 0;
        if (bitmap.width > 1022 || bitmap.rows > 1022 || bitmap.pixel_mode != FT_PIXEL_MODE_GRAY) { words[7] = 1; return 0; }
        auto* pixels = static_cast<uint8_t*>(std::malloc(size_t(bitmap.width) * bitmap.rows));
        if (!pixels) { words[7] = 1; return 0; }
        for (unsigned y = 0; y < bitmap.rows; y++)
            std::memcpy(pixels + size_t(y) * bitmap.width, bitmap.buffer + ptrdiff_t(bitmap.rows - y - 1) * bitmap.pitch, bitmap.width);
        words[0] = bitmap.width; words[1] = bitmap.rows;
        words[2] = fontFloatWord(value->face->glyph->bitmap_left / float(size));
        words[3] = fontFloatWord((value->face->glyph->bitmap_top - int(bitmap.rows)) / float(size));
        return reinterpret_cast<uintptr_t>(pixels);
    }
    if (!value->distanceFont) value->distanceFont = msdfgen::adoptFreetypeFont(value->face);
    if (!value->distanceFont) { words[7] = 1; return 0; }
    msdfgen::Shape shape;
    if (!msdfgen::loadGlyph(shape, value->distanceFont, msdfgen::GlyphIndex(glyph), msdfgen::FONT_SCALING_EM_NORMALIZED)) { words[7] = 1; return 0; }
    size_t edges = 0;
    for (const auto& contour : shape.contours) edges += contour.edges.size();
    if (edges == 0) return 0;
    shape.normalize(); shape.orientContours();
    msdfgen::edgeColoringSimple(shape, 3.0);
    const auto bounds = shape.getBounds();
    const double left = std::floor(bounds.l * 64) - 5, bottom = std::floor(bounds.b * 64) - 5;
    const int width = int(std::ceil(bounds.r * 64) + 5 - left), height = int(std::ceil(bounds.t * 64) + 5 - bottom);
    if (width < 1 || height < 1 || width > 1022 || height > 1022) { words[7] = 1; return 0; }
    msdfgen::Bitmap<float, 3> bitmap(width, height);
    msdfgen::generateMSDF(bitmap, shape, msdfgen::Projection(msdfgen::Vector2(64, 64), msdfgen::Vector2(-left / 64, -bottom / 64)), msdfgen::Range(8.0 / 64));
    auto* pixels = static_cast<uint8_t*>(std::malloc(size_t(width) * height * 3));
    if (!pixels) { words[7] = 1; return 0; }
    for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) for (int channel = 0; channel < 3; channel++)
        pixels[(size_t(y) * width + x) * 3 + channel] = uint8_t(std::clamp(int(std::floor(bitmap(x, y)[channel] * 255.f + .5f)), 0, 255));
    words[0] = width; words[1] = height;
    words[2] = fontFloatWord(float(left / 64)); words[3] = fontFloatWord(float(bottom / 64));
    return reinterpret_cast<uintptr_t>(pixels);
}
