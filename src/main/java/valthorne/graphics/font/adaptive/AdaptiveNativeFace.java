package valthorne.graphics.font.adaptive;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.freetype.FT_Bitmap;
import org.lwjgl.util.freetype.FT_Face;
import org.lwjgl.util.freetype.FT_GlyphSlot;
import org.lwjgl.util.msdfgen.MSDFGenBitmap;
import org.lwjgl.util.msdfgen.MSDFGenBounds;
import org.lwjgl.util.msdfgen.MSDFGenTransform;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

import static org.lwjgl.system.MemoryUtil.*;
import static org.lwjgl.util.freetype.FreeType.*;
import static org.lwjgl.util.harfbuzz.HarfBuzz.*;
import static org.lwjgl.util.harfbuzz.OpenType.hb_ot_font_set_funcs;
import static org.lwjgl.util.msdfgen.MSDFGen.*;
import static org.lwjgl.util.msdfgen.MSDFGenExt.*;

/**
 * Owns the encoded memory, FreeType raster face, immutable HarfBuzz font and
 * msdfgen face for one adaptive font. All calls are confined to the font's owner
 * thread for rendering. Preparation instances are used serially by background
 * tasks and never shared with rendering. HarfBuzz uses design units so hinting
 * never alters layout advances.
 * Raster buffers are transient and belong to the caller; all native handles are
 * released even when initialization fails. No OpenGL access occurs here.
 */
final class AdaptiveNativeFace implements AutoCloseable {
    /**
     * Language-neutral shaping selection, interned once by HarfBuzz.
     */
    static final long LANGUAGE_UND = hb_language_from_string("und");
    /**
     * Distance-field resolution per em; independent of requested drawing size.
     */
    static final int MSDF_EM = 64;
    /**
     * Full signed-distance interval in atlas texels.
     */
    static final int MSDF_RANGE = 8;

    private ByteBuffer bytes; // Owned canonical font memory retained by every native backend.
    private long library; // Owned FreeType library.
    private FT_Face face; // Owned mutable raster face.
    private long font; // Owned immutable HarfBuzz OpenType font.
    private long buffer; // Owned reusable HarfBuzz shaping buffer.
    private long msdfLibrary; // Owned msdfgen FreeType integration.
    private long msdfFont; // Owned msdfgen memory font.
    private boolean closed; // Prevents duplicate destruction.

    /**
     * Opens each native backend over an independent immutable byte snapshot.
     * @param source encoded OpenType bytes
     */
    AdaptiveNativeFace(byte[] source) {
        bytes = memAlloc(source.length);
        bytes.put(source).flip();
        long blob = 0;
        long hbFace = 0;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            OpenTypeContainer.validate(bytes);
            PointerBuffer out = stack.mallocPointer(1);
            check(FT_Init_FreeType(out));
            library = out.get(0);
            check(FT_New_Memory_Face(library, bytes, 0, out));
            face = FT_Face.create(out.get(0));
            if (face.units_per_EM() <= 0) throw new IllegalArgumentException("Font must contain scalable outlines");
            if (!FT_IS_SFNT(face)) throw new IllegalArgumentException("Font must contain TrueType/OpenType tables; convert legacy Type 1 fonts to OTF before loading");
            if (OpenTypeContainer.isCompressed(bytes)) {
                ByteBuffer decoded = OpenTypeContainer.decode(face);
                int status = FT_Done_Face(face);
                face = null;
                memFree(bytes);
                bytes = decoded;
                check(status);
                check(FT_New_Memory_Face(library, bytes, 0, out));
                face = FT_Face.create(out.get(0));
            }
            blob = hb_blob_create_or_fail(bytes, HB_MEMORY_MODE_READONLY, 0, null);
            if (blob == 0) throw new IllegalArgumentException("Cannot read OpenType font");
            hbFace = hb_face_create(blob, 0);
            if (hb_face_get_upem(hbFace) <= 0 || hb_face_get_glyph_count(hbFace) == 0)
                throw new IllegalArgumentException("Font has no scalable glyphs");
            font = hb_font_create(hbFace);
            hb_ot_font_set_funcs(font);
            hb_font_set_scale(font, face.units_per_EM(), face.units_per_EM());
            hb_font_make_immutable(font);
            buffer = hb_buffer_create();
            if (!hb_buffer_allocation_successful(buffer)) throw new OutOfMemoryError("HarfBuzz buffer allocation failed");
        } catch (RuntimeException | Error failure) {
            try {
                close();
            } catch (RuntimeException | Error cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        } finally {
            if (hbFace != 0) hb_face_destroy(hbFace);
            if (blob != 0) hb_blob_destroy(blob);
        }
    }

    /**
     * Rejects failed native operations without substituting invisible glyphs.
     * @param status native result, zero on success
     */
    static void check(int status) {
        if (status != 0) throw new IllegalStateException("Native font operation failed: " + status);
    }

    /**
     * Returns the borrowed immutable shaping font.
     * @return HarfBuzz handle
     */
    long font() { return font; }

    /**
     * Resets the reusable buffer before a new shaping segment.
     * @return borrowed empty buffer
     */
    long buffer() { hb_buffer_reset(buffer); return buffer; }

    /**
     * Returns the raster face for design metrics only.
     * @return borrowed FreeType face
     */
    FT_Face face() { return face; }

    /**
     * Rasterizes grayscale coverage using the selected outline hinting policy
     * while retaining natural layout. NONE changes grid fitting, not antialiasing.
     * A transparent one-pixel image border preserves filtered edge coverage at
     * fractional glyph positions; bearings include that border in layout units.
     * @param glyph shaped glyph index
     * @param size physical pixels per em
     * @param hinting trusted non-null small-text hinting policy
     * @return owned bottom-up R8 coverage image, possibly empty
     */
    FontRaster bitmap(int glyph, int size, FontHinting hinting) {
        check(FT_Set_Pixel_Sizes(face, 0, size));
        check(FT_Load_Glyph(face, glyph, FT_LOAD_NO_BITMAP | hinting.loadFlags()));
        FT_GlyphSlot slot = face.glyph();
        check(FT_Render_Glyph(slot, FT_RENDER_MODE_NORMAL));
        FT_Bitmap bitmap = slot.bitmap();
        int width = bitmap.width();
        int height = bitmap.rows();
        if (width == 0 || height == 0) return new FontRaster(null, 0, 0, 0, 0, size);
        if (width > FontAtlasPage.MAX_SIDE - 4 || height > FontAtlasPage.MAX_SIDE - 4) throw new IllegalArgumentException("Glyph exceeds atlas dimensions");
        if (bitmap.pixel_mode() != FT_PIXEL_MODE_GRAY) throw new IllegalStateException("Expected grayscale FreeType glyph");
        int paddedWidth = width + 2;
        int paddedHeight = height + 2;
        ByteBuffer pixels = memCalloc(paddedWidth * paddedHeight);
        int pitch = bitmap.pitch();
        long origin = memGetAddress(bitmap.address() + FT_Bitmap.BUFFER);
        long destination = memAddress(pixels) + paddedWidth + 1;
        for (int y = 0; y < height; y++) {
            long row = origin + (long) (height - 1 - y) * pitch;
            memCopy(row, destination + (long) y * paddedWidth, width);
        }
        return new FontRaster(pixels, paddedWidth, paddedHeight, (slot.bitmap_left() - 1) / (float) size, (slot.bitmap_top() - height - 1) / (float) size, size);
    }

    /**
     * Generates a multi-channel signed-distance image from the exact shaped glyph.
     * The four-texel border retains the full distance interval around the outline.
     * @param glyph OpenType glyph index
     * @return owned bottom-up RGB8 image, possibly empty
     */
    FontRaster msdf(int glyph) {
        ensureMsdf();
        long shape = 0;
        ByteBuffer pixels = null;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer out = stack.mallocPointer(1);
            check(msdf_ft_font_load_glyph_by_index(msdfFont, glyph, MSDF_FONT_SCALING_EM_NORMALIZED, null, out));
            shape = out.get(0);
            check(msdf_shape_get_edge_count(shape, out));
            if (out.get(0) == 0) return new FontRaster(null, 0, 0, 0, 0, MSDF_EM);
            check(msdf_shape_normalize(shape));
            check(msdf_shape_orient_contours(shape));
            check(msdf_shape_edge_colors_simple(shape, 3.0));
            MSDFGenBounds bounds = MSDFGenBounds.malloc(stack);
            check(msdf_shape_get_bounds(shape, bounds));
            double left = Math.floor(bounds.l() * MSDF_EM) - MSDF_RANGE / 2.0 - 1;
            double bottom = Math.floor(bounds.b() * MSDF_EM) - MSDF_RANGE / 2.0 - 1;
            int width = (int) (Math.ceil(bounds.r() * MSDF_EM) + MSDF_RANGE / 2 + 1 - left);
            int height = (int) (Math.ceil(bounds.t() * MSDF_EM) + MSDF_RANGE / 2 + 1 - bottom);
            if (width < 1 || height < 1 || width > 1022 || height > 1022) throw new IllegalArgumentException("Glyph exceeds atlas dimensions");
            MSDFGenTransform transform = MSDFGenTransform.calloc(stack);
            transform.scale().set(MSDF_EM, MSDF_EM);
            transform.translation().set(-left / MSDF_EM, -bottom / MSDF_EM);
            transform.distance_mapping().set(-MSDF_RANGE / (2.0 * MSDF_EM), MSDF_RANGE / (2.0 * MSDF_EM));
            MSDFGenBitmap bitmap = MSDFGenBitmap.calloc(stack);
            check(msdf_bitmap_alloc(MSDF_BITMAP_TYPE_MSDF, width, height, bitmap));
            try {
                check(msdf_generate_msdf(bitmap, shape, transform));
                check(msdf_bitmap_get_pixels(bitmap, out));
                FloatBuffer source = memFloatBuffer(out.get(0), width * height * 3);
                pixels = memAlloc(width * height * 3);
                for (int i = 0; i < width * height; i++) {
                    for (int channel = 0; channel < 3; channel++)
                        pixels.put((byte) Math.max(0, Math.min(255, Math.round(source.get() * 255))));
                }
                pixels.flip();
                return new FontRaster(pixels, width, height, (float) left / MSDF_EM, (float) bottom / MSDF_EM, MSDF_EM);
            } finally {
                msdf_bitmap_free(bitmap);
            }
        } catch (RuntimeException | Error failure) {
            if (pixels != null) memFree(pixels);
            throw failure;
        } finally {
            if (shape != 0) msdf_shape_free(shape);
        }
    }

    /**
     * Opens the additional msdfgen font only when a distance-field cache miss
     * actually needs it. Coverage-only UI fonts avoid a second native font face.
     */
    private void ensureMsdf() {
        if (msdfFont != 0) return;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer out = stack.mallocPointer(1);
            if (msdfLibrary == 0) {
                check(msdf_ft_init(out));
                msdfLibrary = out.get(0);
            }
            check(msdf_ft_load_font_data(msdfLibrary, bytes, out));
            msdfFont = out.get(0);
        }
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        if (buffer != 0) hb_buffer_destroy(buffer);
        if (font != 0) hb_font_destroy(font);
        if (msdfFont != 0) msdf_ft_font_destroy(msdfFont);
        if (msdfLibrary != 0) msdf_ft_deinit(msdfLibrary);
        int faceStatus = face == null ? 0 : FT_Done_Face(face);
        int libraryStatus = library == 0 ? 0 : FT_Done_FreeType(library);
        memFree(bytes);
        check(faceStatus);
        check(libraryStatus);
    }
}
