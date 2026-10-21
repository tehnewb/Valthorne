package valthorne.graphics.font.slug;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.freetype.FT_Face;

import java.nio.ByteBuffer;
import java.util.List;

import static org.lwjgl.util.freetype.FreeType.*;
import static org.lwjgl.util.harfbuzz.HarfBuzz.*;
import static org.lwjgl.util.harfbuzz.OpenType.hb_ot_font_set_funcs;

/**
 * Owns one FreeType memory face and HarfBuzz OpenType shaping font over borrowed,
 * immutable encoded bytes. Shaping uses font-unit advances, independent of DPI;
 * UI geometry is hinted at its actual physical size before Slug evaluates curves.
 * This avoids requiring HarfBuzz's optional FreeType integration, which is absent
 * from the bundled LWJGL HarfBuzz builds. Confine mutable calls to one thread, or
 * hand ownership to another thread only while no call is in progress.
 */
final class SlugNativeFace implements AutoCloseable {
    private final ByteBuffer bytes; // Borrowed immutable source retained while native faces reference its memory.
    private long library; // Lazily owned FreeType library; world-only shaping does not initialize it.
    private FT_Face face; // Lazily owned hinted FreeType face borrowing the parent font's storage.
    private final long font; // Owned immutable OpenType shaping font.
    private final long buffer; // Owned reusable HarfBuzz shaping scratch.
    private final int unitsPerEm; // OpenType design units used to normalize shaped positions.
    private final int glyphCount; // Native face boundary for externally supplied glyph indices.
    private final float spaceAdvance; // Nominal space width in em units for tab positioning.
    private SlugHintedOutline outline; // Lazily owned native callbacks used only by hinted variants.
    private int physicalSize; // Last selected physical em size in 26.6 units.
    private boolean closed; // Prevents repeated native disposal.

    /**
     * Loads the exact memory font without installing or resolving a system family.
     *
     * @param bytes borrowed direct encoded font bytes that outlive this face
     */
    SlugNativeFace(ByteBuffer bytes) {
        this.bytes = bytes;
        long blob = 0;
        long hbFace = 0;
        long ownedFont = 0;
        long ownedBuffer = 0;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            blob = hb_blob_create_or_fail(bytes, HB_MEMORY_MODE_READONLY, 0, null);
            if (blob == 0) throw new IllegalArgumentException("Cannot create HarfBuzz font blob");
            hbFace = hb_face_create(blob, 0);
            unitsPerEm = hb_face_get_upem(hbFace);
            glyphCount = hb_face_get_glyph_count(hbFace);
            if (unitsPerEm <= 0 || glyphCount == 0) throw new IllegalArgumentException("Font contains no OpenType glyphs");
            ownedFont = hb_font_create(hbFace);
            hb_ot_font_set_funcs(ownedFont);
            hb_font_set_scale(ownedFont, unitsPerEm, unitsPerEm);
            hb_font_make_immutable(ownedFont);
            var space = stack.mallocInt(1);
            space.put(0, 0);
            hb_font_get_nominal_glyph(ownedFont, ' ', space);
            spaceAdvance = hb_font_get_glyph_h_advance(ownedFont, space.get(0)) / (float) unitsPerEm;
            ownedBuffer = hb_buffer_create();
            if (!hb_buffer_allocation_successful(ownedBuffer)) throw new OutOfMemoryError("Cannot allocate HarfBuzz shaping buffer");
            font = ownedFont;
            buffer = ownedBuffer;
        } catch (RuntimeException | Error failure) {
            if (ownedBuffer != 0) hb_buffer_destroy(ownedBuffer);
            if (ownedFont != 0) hb_font_destroy(ownedFont);
            throw failure;
        } finally {
            if (hbFace != 0) hb_face_destroy(hbFace);
            if (blob != 0) hb_blob_destroy(blob);
        }
    }

    /**
     * Checks a FreeType boundary result while preserving its native error code.
     *
     * @param status native FreeType result
     */
    private static void check(int status) {
        /*
         * Failed native operations cannot be treated as an empty or substituted glyph.
         */
        if (status != 0) throw new IllegalStateException("FreeType font operation failed: " + status);
    }

    /**
     * Borrows the immutable OpenType font for one shaping operation.
     *
     * @return live HarfBuzz font handle
     */
    long font() { return font; }

    /**
     * Resets and borrows scratch storage for the next script run.
     *
     * @return empty HarfBuzz buffer
     */
    long buffer() { hb_buffer_reset(buffer); return buffer; }

    /**
     * Returns the design-unit divisor for glyph offsets and advances.
     *
     * @return units per em
     */
    int unitsPerEm() { return unitsPerEm; }

    /**
     * Returns the actual glyph-index boundary parsed from the OpenType face.
     *
     * @return native glyph count
     */
    int glyphCount() { return glyphCount; }

    /**
     * Returns the exact font's nominal unhinted space advance.
     *
     * @return em advance for one space
     */
    float spaceAdvance() { return spaceAdvance; }

    /**
     * Copies a size-hinted outline while preserving natural unhinted run advances.
     * Normal grayscale hinting grid-fits available font instructions; run advances
     * remain natural OpenType metrics. CFF optical-size stem darkening uses the
     * driver's documented defaults alongside the portable gamma response.
     * The returned curves are normalized for the requested physical em size.
     *
     * @param glyphIndex actual shaped glyph index
     * @param pixelsPerEm finite positive physical em size, quantized to 1/64 pixel
     * @return independent hinted Slug curves
     */
    List<SlugCurve> hintedCurves(int glyphIndex, float pixelsPerEm) {
        ensureHinting();
        int selected = Math.round(pixelsPerEm * 64);
        if (physicalSize != selected) {
            check(FT_Set_Char_Size(face, selected, selected, 72, 72));
            physicalSize = selected;
        }
        check(FT_Load_Glyph(face, glyphIndex, FT_LOAD_NO_BITMAP | FT_LOAD_TARGET_NORMAL));
        if (outline == null) outline = new SlugHintedOutline();
        return outline.load(face.glyph().outline(), selected / 64f);
    }

    /**
     * Creates the hinting face only when UI geometry first needs it. The private
     * library pins the TrueType interpreter and enables the CFF driver's native
     * stem darkening for gamma-corrected small text without changing font bytes
     * or applying application-specific outline emboldening.
     */
    private void ensureHinting() {
        if (face != null) return;
        long ownedLibrary = 0;
        long ownedFace = 0;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer output = stack.mallocPointer(1);
            check(FT_Init_FreeType(output));
            ownedLibrary = output.get(0);
            check(FT_Property_Set(ownedLibrary, "truetype", "interpreter-version", stack.malloc(4).putInt(0, 40)));
            check(FT_Property_Set(ownedLibrary, "cff", "no-stem-darkening", stack.calloc(1)));
            check(FT_New_Memory_Face(ownedLibrary, bytes, 0, output));
            ownedFace = output.get(0);
            face = FT_Face.create(ownedFace);
            library = ownedLibrary;
        } catch (RuntimeException | Error failure) {
            if (ownedFace != 0) FT_Done_Face(FT_Face.create(ownedFace));
            if (ownedLibrary != 0) FT_Done_FreeType(ownedLibrary);
            throw failure;
        }
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        if (outline != null) outline.close();
        hb_buffer_destroy(buffer);
        hb_font_destroy(font);
        if (face != null) {
            int faceStatus = FT_Done_Face(face);
            int libraryStatus = FT_Done_FreeType(library);
            check(faceStatus);
            check(libraryStatus);
        }
    }
}
