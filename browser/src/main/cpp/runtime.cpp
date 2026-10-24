#include "operations.h"
#include <bgfx/bgfx.h>
#include <emscripten/emscripten.h>
#include <cstdint>
#include <cstring>
#include <cstdlib>
#define STBI_NO_STDIO
#define STB_IMAGE_STATIC
#define STB_IMAGE_IMPLEMENTATION
#include <stb_image.h>
#define STB_TRUETYPE_IMPLEMENTATION
#include <stb_truetype.h>
#define STB_IMAGE_RESIZE_STATIC
#define STB_IMAGE_RESIZE_IMPLEMENTATION
#include <stb_image_resize2.h>
#include "font_resource.h"
#include <nanovg.h>
#include <nanovg_bgfx.h>

static_assert(BGFX_API_VERSION == 156, "Browser bindings require the bgfx API shipped with LWJGL 3.4.3");

namespace {
    uint64_t wide(const uint32_t* words, unsigned offset) {
        return uint64_t(words[offset]) | (uint64_t(words[offset + 1]) << 32);
    }
    template<typename Handle> Handle handle(uint32_t value) { return Handle{uint16_t(value)}; }
    bgfx::VertexLayout* layout(uint32_t address) { return reinterpret_cast<bgfx::VertexLayout*>(address); }
    const bgfx::Memory* memory(uint32_t address) { return reinterpret_cast<const bgfx::Memory*>(address); }
    uint32_t floatBits(float value) {
        uint32_t result;
        std::memcpy(&result, &value, sizeof(result));
        return result;
    }
    BrowserFont* font(uint32_t pointer) { return reinterpret_cast<BrowserFont*>(pointer); }
    NVGcontext* context(uint32_t pointer) { return reinterpret_cast<NVGcontext*>(pointer); }
    NVGcolor color(const float* f) { return NVGcolor{ f[0], f[1], f[2], f[3] }; }
    NVGpaint paint(const float* f, int image) {
        NVGpaint result;
        std::memcpy(result.xform, f, 6 * sizeof(float));
        std::memcpy(result.extent, f + 6, 2 * sizeof(float));
        result.radius = f[8]; result.feather = f[9];
        result.innerColor = color(f + 10); result.outerColor = color(f + 14);
        result.image = image; return result;
    }
    void writePaint(const NVGpaint& value, uint32_t* words) {
        for (int i=0;i<6;i++) words[i] = floatBits(value.xform[i]);
        for (int i=0;i<2;i++) words[6+i] = floatBits(value.extent[i]);
        words[8] = floatBits(value.radius); words[9] = floatBits(value.feather);
        for (int i=0;i<4;i++) { words[10+i] = floatBits(value.innerColor.rgba[i]); words[14+i] = floatBits(value.outerColor.rgba[i]); }
        words[18] = value.image;
    }
    bool active = false;
    char* canvasSelector = nullptr;
    uint32_t uniformCount = 0;
    bgfx::UniformHandle uniformHandles[1024];
    bgfx::UniformInfo uniformInfo;
}

extern "C" {
EMSCRIPTEN_KEEPALIVE void valthorne_font_free(uint32_t pointer) {
    if (!pointer) return;
    auto* value = font(pointer);
    std::free(value->data);
    std::free(value);
}

// All data buffers are caller-owned scratch memory. bgfx::copy transfers upload
// contents to frame-owned storage before Java reuses the scratch buffer.
EMSCRIPTEN_KEEPALIVE uint32_t valthorne_command(uint32_t operation, uint32_t* w, float* f, const uint8_t* bytes, uint32_t length) {
    switch (operation) {
    case INITIALIZE: {
        if (active) return 0;
        if (length < 2 || bytes[length - 1] != 0) return 0;
        canvasSelector = static_cast<char*>(std::malloc(length));
        if (!canvasSelector) return 0;
        std::memcpy(canvasSelector, bytes, length);
        bgfx::renderFrame();
        bgfx::Init init;
        init.type = bgfx::RendererType::OpenGLES;
        init.fallback = false;
        init.platformData.nwh = canvasSelector;
        init.resolution.width = w[0];
        init.resolution.height = w[1];
        init.resolution.reset = w[2];
        active = bgfx::init(init);
        if (!active) { std::free(canvasSelector); canvasSelector = nullptr; }
        return active ? 1 : 0;
    }
    case SHUTDOWN: if (active) { bgfx::shutdown(); active = false; std::free(canvasSelector); canvasSelector = nullptr; } return 0;
    case FRAME: return bgfx::frame(w[0]);
    case RESET: bgfx::reset(w[0], w[1], w[2]); return 0;
    case CAPABILITIES: {
        const auto* caps = bgfx::getCaps();
        w[0] = uint32_t(caps->supported); w[1] = uint32_t(caps->supported >> 32);
        w[2] = caps->limits.maxTextureSize; w[3] = caps->limits.maxTextures;
        w[4] = caps->homogeneousDepth; w[5] = caps->originBottomLeft;
        w[6] = caps->rendererType; w[7] = caps->vendorId;
        return 0;
    }
    case LAYOUT_CREATE: return reinterpret_cast<uintptr_t>(new bgfx::VertexLayout());
    case LAYOUT_FREE: delete layout(w[0]); return 0;
    case LAYOUT_BEGIN: layout(w[0])->begin(bgfx::RendererType::OpenGLES); return 0;
    case LAYOUT_ADD: layout(w[0])->add(bgfx::Attrib::Enum(w[1]), uint8_t(w[2]), bgfx::AttribType::Enum(w[3]), w[4] != 0, w[5] != 0); return layout(w[0])->getStride();
    case LAYOUT_SKIP: layout(w[0])->skip(uint8_t(w[1])); return layout(w[0])->getStride();
    case LAYOUT_END: layout(w[0])->end(); return layout(w[0])->getStride();
    case LAYOUT_COPY: *layout(w[0]) = *layout(w[1]); return layout(w[0])->getStride();
    case LAYOUT_HAS: return layout(w[0])->has(bgfx::Attrib::Enum(w[1]));
    case LAYOUT_DECODE: {
        auto* l = layout(w[0]); auto attr = bgfx::Attrib::Enum(w[1]);
        uint8_t count; bgfx::AttribType::Enum type; bool normalized; bool integer;
        l->decode(attr, count, type, normalized, integer);
        w[0] = count; w[1] = type; w[2] = normalized; w[3] = integer; w[4] = l->getOffset(attr);
        return 0;
    }
    case COPY: return reinterpret_cast<uintptr_t>(bgfx::copy(bytes, length));
    case VERTEX_CREATE: return bgfx::createVertexBuffer(memory(w[0]), *layout(w[1]), uint16_t(w[2])).idx;
    case DYNAMIC_VERTEX_CREATE: return bgfx::createDynamicVertexBuffer(w[0], *layout(w[1]), uint16_t(w[2])).idx;
    case DYNAMIC_VERTEX_UPDATE: bgfx::update(handle<bgfx::DynamicVertexBufferHandle>(w[0]), w[1], memory(w[2])); return 0;
    case VERTEX_DESTROY: bgfx::destroy(handle<bgfx::VertexBufferHandle>(w[0])); return 0;
    case DYNAMIC_VERTEX_DESTROY: bgfx::destroy(handle<bgfx::DynamicVertexBufferHandle>(w[0])); return 0;
    case SHADER_CREATE: return bgfx::createShader(memory(w[0])).idx;
    case SHADER_DESTROY: bgfx::destroy(handle<bgfx::ShaderHandle>(w[0])); return 0;
    case PROGRAM_CREATE: return bgfx::createProgram(handle<bgfx::ShaderHandle>(w[0]), handle<bgfx::ShaderHandle>(w[1]), w[2] != 0).idx;
    case PROGRAM_DESTROY: bgfx::destroy(handle<bgfx::ProgramHandle>(w[0])); return 0;
    case UNIFORM_CREATE: return bgfx::createUniform(reinterpret_cast<const char*>(bytes), bgfx::UniformType::Enum(w[0]), uint16_t(w[1])).idx;
    case UNIFORM_DESTROY: bgfx::destroy(handle<bgfx::UniformHandle>(w[0])); return 0;
    case SHADER_UNIFORMS: {
        uniformCount = bgfx::getShaderUniforms(handle<bgfx::ShaderHandle>(w[0]), uniformHandles, 1024);
        if (uniformCount > 1024) return UINT32_MAX;
        return uniformCount;
    }
    case SHADER_UNIFORM_HANDLE: return w[0] < uniformCount ? uniformHandles[w[0]].idx : UINT16_MAX;
    case UNIFORM_INFO: bgfx::getUniformInfo(handle<bgfx::UniformHandle>(w[0]), uniformInfo); w[0] = uniformInfo.type; w[1] = uniformInfo.num; return reinterpret_cast<uintptr_t>(uniformInfo.name);
    case UNIFORM_SET: bgfx::setUniform(handle<bgfx::UniformHandle>(w[0]), f, uint16_t(w[1])); return 0;
    case TEXTURE_CREATE: return bgfx::createTexture2D(uint16_t(w[0]), uint16_t(w[1]), w[2] != 0, uint16_t(w[3]), bgfx::TextureFormat::Enum(w[4]), wide(w, 5), memory(w[7])).idx;
    case TEXTURE_DESTROY: bgfx::destroy(handle<bgfx::TextureHandle>(w[0])); return 0;
    case TEXTURE_VALID: return bgfx::isTextureValid(0, false, 1, bgfx::TextureFormat::Enum(w[0]), wide(w, 1));
    case FRAMEBUFFER_ATTACHMENTS: {
        bgfx::TextureHandle textures[8];
        if (w[0] > 8) return UINT16_MAX;
        for (uint32_t i = 0; i < w[0]; ++i) textures[i] = handle<bgfx::TextureHandle>(w[2 + i]);
        return bgfx::createFrameBuffer(uint8_t(w[0]), textures, w[1] != 0).idx;
    }
    case FRAMEBUFFER_CREATE: return bgfx::createFrameBuffer(uint16_t(w[0]), uint16_t(w[1]), bgfx::TextureFormat::Enum(w[2]), wide(w, 3)).idx;
    case FRAMEBUFFER_DESTROY: bgfx::destroy(handle<bgfx::FrameBufferHandle>(w[0])); return 0;
    case FRAMEBUFFER_TEXTURE: return bgfx::getTexture(handle<bgfx::FrameBufferHandle>(w[0]), uint8_t(w[1])).idx;
    case VIEW_RECT: bgfx::setViewRect(uint16_t(w[0]), uint16_t(w[1]), uint16_t(w[2]), uint16_t(w[3]), uint16_t(w[4])); return 0;
    case VIEW_SCISSOR: bgfx::setViewScissor(uint16_t(w[0]), uint16_t(w[1]), uint16_t(w[2]), uint16_t(w[3]), uint16_t(w[4])); return 0;
    case VIEW_FRAMEBUFFER: bgfx::setViewFrameBuffer(uint16_t(w[0]), handle<bgfx::FrameBufferHandle>(w[1])); return 0;
    case VIEW_MODE: bgfx::setViewMode(uint16_t(w[0]), bgfx::ViewMode::Enum(w[1])); return 0;
    case VIEW_CLEAR: bgfx::setViewClear(uint16_t(w[0]), uint16_t(w[1]), w[2], f[0], uint8_t(w[3])); return 0;
    case PALETTE: bgfx::setPaletteColor(uint8_t(w[0]), f); return 0;
    case VIEW_CLEAR_ATTACHMENTS: bgfx::setViewClear(uint16_t(w[0]), uint16_t(w[1]), f[0], uint8_t(w[2]), uint8_t(w[3]), uint8_t(w[4]), uint8_t(w[5]), uint8_t(w[6]), uint8_t(w[7]), uint8_t(w[8]), uint8_t(w[9]), uint8_t(w[10])); return 0;
    case TOUCH: bgfx::touch(uint16_t(w[0])); return 0;
    case STATE: bgfx::setState(wide(w, 0), w[2]); return 0;
    case TEXTURE_SET: bgfx::setTexture(uint8_t(w[0]), handle<bgfx::UniformHandle>(w[1]), handle<bgfx::TextureHandle>(w[2]), w[3]); return 0;
    case VERTEX_SET: bgfx::setVertexBuffer(uint8_t(w[0]), handle<bgfx::VertexBufferHandle>(w[1]), w[2], w[3]); return 0;
    case DYNAMIC_VERTEX_SET: bgfx::setVertexBuffer(uint8_t(w[0]), handle<bgfx::DynamicVertexBufferHandle>(w[1]), w[2], w[3]); return 0;
    case INSTANCE_SET: bgfx::setInstanceDataBuffer(handle<bgfx::DynamicVertexBufferHandle>(w[0]), w[1], w[2]); return 0;
    case SUBMIT: bgfx::submit(uint16_t(w[0]), handle<bgfx::ProgramHandle>(w[1]), w[2], uint8_t(w[3])); return 0;
    case VIEW_TRANSFORM: bgfx::setViewTransform(uint16_t(w[0]), w[1] ? f : nullptr, w[2] ? f + 16 : nullptr); return 0;
    case TRANSIENT_AVAILABLE: return bgfx::getAvailTransientVertexBuffer(w[0], *layout(w[1]));
    case TRANSIENT_SET: {
        bgfx::TransientVertexBuffer buffer;
        if (bgfx::getAvailTransientVertexBuffer(w[0], *layout(w[1])) != w[0]) return 0;
        bgfx::allocTransientVertexBuffer(&buffer, w[0], *layout(w[1]));
        if (length != buffer.size) return 0;
        std::memcpy(buffer.data, bytes, length);
        bgfx::setVertexBuffer(uint8_t(w[2]), &buffer, 0, w[0]); return 1;
    }
    case TEXTURE_UPDATE: bgfx::updateTexture2D(handle<bgfx::TextureHandle>(w[0]), uint16_t(w[1]), uint8_t(w[2]), uint16_t(w[3]), uint16_t(w[4]), uint16_t(w[5]), uint16_t(w[6]), memory(w[7]), uint16_t(w[8])); return 0;
    case IMAGE_DECODE: {
        int width = 0, height = 0, channels = 0;
        uint8_t* result = stbi_load_from_memory(bytes, int(length), &width, &height, &channels, int(w[0]));
        w[0] = width; w[1] = height; w[2] = channels;
        return reinterpret_cast<uintptr_t>(result);
    }
    case IMAGE_FREE: stbi_image_free(reinterpret_cast<void*>(w[0])); return 0;
    case IMAGE_FAILURE: return reinterpret_cast<uintptr_t>(stbi_failure_reason());
    case IMAGE_FLIP: stbi_set_flip_vertically_on_load(w[0] != 0); return 0;
    case FONT_CREATE: {
        if (length < 12) return 0;
        auto* value = static_cast<BrowserFont*>(std::calloc(1, sizeof(BrowserFont)));
        if (!value) return 0;
        value->data = static_cast<uint8_t*>(std::malloc(length));
        if (!value->data) { std::free(value); return 0; }
        std::memcpy(value->data, bytes, length);
        int offset = stbtt_GetFontOffsetForIndex(value->data, 0);
        if (offset < 0 || !stbtt_InitFont(&value->info, value->data, offset)) {
            std::free(value->data); std::free(value); return 0;
        }
        return reinterpret_cast<uintptr_t>(value);
    }
    case FONT_FREE: valthorne_font_free(w[0]); return 0;
    case FONT_METRICS: {
        auto* info = &font(w[0])->info;
        int ascent, descent, gap;
        stbtt_GetFontVMetrics(info, &ascent, &descent, &gap);
        w[0] = ascent; w[1] = descent; w[2] = gap;
        w[3] = floatBits(stbtt_ScaleForMappingEmToPixels(info, 1.0f)); return 0;
    }
    case FONT_BAKE: {
        const uint32_t pointer = w[0], side = w[1], first = w[2], count = w[3];
        if (!pointer || side < 1 || side > 8192 || first > 65535 || count < 1 || count > 65536 - first || !(f[0] > 0)) return 0;
        static_assert(sizeof(stbtt_packedchar) == 28, "Review browser packed glyph layout");
        const size_t pixels = size_t(side) * side;
        auto* allocation = static_cast<uint8_t*>(std::calloc(1, pixels + size_t(count) * sizeof(stbtt_packedchar)));
        if (!allocation) return 0;
        auto* chars = reinterpret_cast<stbtt_packedchar*>(allocation + pixels);
        stbtt_pack_context packing;
        if (!stbtt_PackBegin(&packing, allocation, int(side), int(side), 0, 1, nullptr)) {
            std::free(allocation); return 0;
        }
        stbtt_PackSetOversampling(&packing, 1, 1);
        int packed = stbtt_PackFontRange(&packing, font(pointer)->data, 0, f[0], int(first), int(count), chars);
        stbtt_PackEnd(&packing);
        if (!packed) { std::free(allocation); return 0; }
        return reinterpret_cast<uintptr_t>(allocation);
    }
    case FONT_HORIZONTAL: {
        int advance, bearing;
        stbtt_GetCodepointHMetrics(&font(w[0])->info, int(w[1]), &advance, &bearing);
        w[0] = advance; w[1] = bearing; return 0;
    }
    case FONT_BOX: {
        int x0 = 0, y0 = 0, x1 = 0, y1 = 0;
        int present = stbtt_GetCodepointBox(&font(w[0])->info, int(w[1]), &x0, &y0, &x1, &y1);
        w[0] = x0; w[1] = y0; w[2] = x1; w[3] = y1; return present;
    }
    case FONT_KERNING: return stbtt_GetCodepointKernAdvance(&font(w[0])->info, int(w[1]), int(w[2]));
    case FONT_SHAPE: {
        stbtt_vertex* vertices = nullptr;
        int count = stbtt_GetCodepointShape(&font(w[0])->info, int(w[1]), &vertices);
        w[0] = count; return reinterpret_cast<uintptr_t>(vertices);
    }
    case FONT_VERTEX: {
        const auto& vertex = reinterpret_cast<const stbtt_vertex*>(w[0])[w[1]];
        w[0] = vertex.type; w[1] = vertex.x; w[2] = vertex.y;
        w[3] = vertex.cx; w[4] = vertex.cy; w[5] = vertex.cx1; w[6] = vertex.cy1; return 0;
    }
    case FONT_SHAPE_FREE: stbtt_FreeShape(&font(w[0])->info, reinterpret_cast<stbtt_vertex*>(w[1])); return 0;
    case NVG_BEGIN_PATH: nvgBeginPath(context(w[0])); return 0;
    case NVG_CLOSE_PATH: nvgClosePath(context(w[0])); return 0;
    case NVG_FILL: nvgFill(context(w[0])); return 0;
    case NVG_STROKE: nvgStroke(context(w[0])); return 0;
    case NVG_SAVE: nvgSave(context(w[0])); return 0;
    case NVG_RESTORE: nvgRestore(context(w[0])); return 0;
    case NVG_RESET_TRANSFORM: nvgResetTransform(context(w[0])); return 0;
    case NVG_RESET_SCISSOR: nvgResetScissor(context(w[0])); return 0;
    case NVG_END_FRAME: nvgEndFrame(context(w[0])); return 0;
    case NVG_TRANSLATE: nvgTranslate(context(w[0]), f[0], f[1]); return 0;
    case NVG_SCALE: nvgScale(context(w[0]), f[0], f[1]); return 0;
    case NVG_TRANSFORM: nvgTransform(context(w[0]), f[0], f[1], f[2], f[3], f[4], f[5]); return 0;
    case NVG_RECT: nvgRect(context(w[0]), f[0], f[1], f[2], f[3]); return 0;
    case NVG_ROUNDED_RECT: nvgRoundedRect(context(w[0]), f[0], f[1], f[2], f[3], f[4]); return 0;
    case NVG_CIRCLE: nvgCircle(context(w[0]), f[0], f[1], f[2]); return 0;
    case NVG_SCISSOR: nvgScissor(context(w[0]), f[0], f[1], f[2], f[3]); return 0;
    case NVG_INTERSECT_SCISSOR: nvgIntersectScissor(context(w[0]), f[0], f[1], f[2], f[3]); return 0;
    case NVG_STROKE_WIDTH: nvgStrokeWidth(context(w[0]), f[0]); return 0;
    case NVG_GLOBAL_ALPHA: nvgGlobalAlpha(context(w[0]), f[0]); return 0;
    case NVG_FONT_SIZE: nvgFontSize(context(w[0]), f[0]); return 0;
    case NVG_BEGIN_FRAME: nvgBeginFrame(context(w[0]), f[0], f[1], f[2]); return 0;
    case NVG_LINE_CAP: nvgLineCap(context(w[0]), int(w[1])); return 0;
    case NVG_LINE_JOIN: nvgLineJoin(context(w[0]), int(w[1])); return 0;
    case NVG_TEXT_ALIGN: nvgTextAlign(context(w[0]), int(w[1])); return 0;
    case NVG_ARC: nvgArc(context(w[0]), f[0], f[1], f[2], f[3], f[4], int(w[1])); return 0;
    case NVG_FILL_COLOR: nvgFillColor(context(w[0]), color(f)); return 0;
    case NVG_STROKE_COLOR: nvgStrokeColor(context(w[0]), color(f)); return 0;
    case NVG_FILL_PAINT: nvgFillPaint(context(w[0]), paint(f, int(w[1]))); return 0;
    case NVG_STROKE_PAINT: nvgStrokePaint(context(w[0]), paint(f, int(w[1]))); return 0;
    case NVG_LINEAR_GRADIENT: writePaint(nvgLinearGradient(context(w[0]), f[0], f[1], f[2], f[3], color(f + 4), color(f + 8)), w); return 0;
    case NVG_BOX_GRADIENT: writePaint(nvgBoxGradient(context(w[0]), f[0], f[1], f[2], f[3], f[4], f[5], color(f + 6), color(f + 10)), w); return 0;
    case NVG_IMAGE_PATTERN: writePaint(nvgImagePattern(context(w[0]), f[0], f[1], f[2], f[3], f[4], int(w[1]), f[5]), w); return 0;
    case NVG_IMAGE_CREATE: if (uint64_t(w[1]) * w[2] * 4 != length) return 0; return nvgCreateImageRGBA(context(w[0]), int(w[1]), int(w[2]), int(w[3]), bytes);
    case NVG_IMAGE_DELETE: nvgDeleteImage(context(w[0]), int(w[1])); return 0;
    case NVG_IMAGE_UPDATE: { int width, height; auto* ctx = context(w[0]); nvgImageSize(ctx, int(w[1]), &width, &height); if (uint64_t(width) * height * 4 != length) return 0; nvgUpdateImage(ctx, int(w[1]), bytes); return 1; }
    case NVG_FONT_CREATE: { if (w[1] >= length) return uint32_t(-1); auto* data = static_cast<uint8_t*>(std::malloc(w[1])); if (!data) return uint32_t(-1); std::memcpy(data, bytes, w[1]); return nvgCreateFontMem(context(w[0]), reinterpret_cast<const char*>(bytes + w[1]), data, int(w[1]), 1); }
    case NVG_FONT_FACE: nvgFontFace(context(w[0]), reinterpret_cast<const char*>(bytes)); return 0;
    case NVG_TEXT: return floatBits(nvgText(context(w[0]), f[0], f[1], reinterpret_cast<const char*>(bytes), reinterpret_cast<const char*>(bytes + length - 1)));
    case NVG_TEXT_BOUNDS: { float bounds[4]; float width = nvgTextBounds(context(w[0]), f[0], f[1], reinterpret_cast<const char*>(bytes), reinterpret_cast<const char*>(bytes + length - 1), bounds); for (int i=0;i<4;i++) w[i] = floatBits(bounds[i]); return floatBits(width); }
    case NVG_TEXT_METRICS: { float ascent, descent, height; nvgTextMetrics(context(w[0]), &ascent, &descent, &height); w[0] = floatBits(ascent); w[1] = floatBits(descent); w[2] = floatBits(height); return 0; }
    case NVG_CREATE: return reinterpret_cast<uintptr_t>(nvgCreate(int(w[0]), bgfx::ViewId(w[1])));
    case NVG_DELETE: nvgDelete(context(w[0])); return 0;
    case NVG_VIEW: nvgSetViewId(context(w[0]), bgfx::ViewId(w[1])); return 0;
    case NVG_MOVE_TO: nvgMoveTo(context(w[0]), f[0], f[1]); return 0;
    case NVG_LINE_TO: nvgLineTo(context(w[0]), f[0], f[1]); return 0;
    case MIPMAP_GENERATE: {
        int width = int(w[0]);
        int height = int(w[1]);
        if (width < 1 || height < 1 || width > 65535 || height > 65535 || uint64_t(width) * height * 4 != length) return 0;
        uint64_t outputSize = 0;
        for (int x = width, y = height; x > 1 || y > 1; ) {
            x = x > 1 ? x / 2 : 1;
            y = y > 1 ? y / 2 : 1;
            outputSize += uint64_t(x) * y * 4;
        }
        if (!outputSize || outputSize > INT32_MAX || outputSize != w[2]) return 0;
        auto* output = static_cast<uint8_t*>(std::malloc(size_t(outputSize)));
        if (!output) return 0;
        const uint8_t* source = bytes;
        uint8_t* destination = output;
        while (width > 1 || height > 1) {
            int nextWidth = width > 1 ? width / 2 : 1;
            int nextHeight = height > 1 ? height / 2 : 1;
            STBIR_RESIZE settings;
            stbir_resize_init(&settings, source, width, height, width * 4, destination, nextWidth, nextHeight, nextWidth * 4, STBIR_4CHANNEL, STBIR_TYPE_UINT8);
            stbir_set_filters(&settings, STBIR_FILTER_BOX, STBIR_FILTER_BOX);
            if (!stbir_resize_extended(&settings)) { std::free(output); return 0; }
            source = destination;
            destination += size_t(nextWidth) * nextHeight * 4;
            width = nextWidth;
            height = nextHeight;
        }
        return reinterpret_cast<uintptr_t>(output);
    }
    default: std::abort();
    }
}
}
