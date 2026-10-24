package valthorne.graphics.font.adaptive;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.harfbuzz.hb_glyph_info_t;
import org.lwjgl.util.harfbuzz.hb_glyph_position_t;
import valthorne.graphics.Color;
import valthorne.graphics.font.TextRun;
import valthorne.graphics.texture.TextureBatch;

import java.text.Bidi;
import java.util.Arrays;
import java.lang.Character.UnicodeScript;

import static org.lwjgl.system.MemoryUtil.*;
import static org.lwjgl.util.harfbuzz.HarfBuzz.*;

/**
 * Retained HarfBuzz glyph-index layout. Repeated unchanged rebuilds and warm
 * draws allocate no glyph objects or native buffers. Positions use natural em
 * metrics in both rendering modes; resizing only scales those positions.
 * Newlines establish separate bidi paragraphs; tabs advance by four spaces.
 * Single-line editing lazily retains grapheme and visual-cluster geometry.
 * The font is borrowed, thread confined, and must outlive this layout.
 */
public final class AdaptiveTextRun implements TextRun {
    private final AdaptiveFont font; // Borrowed owner of native shaping and glyph caches.
    int[] glyphs = new int[0]; // Shaped indices borrowed by the owning font's trusted drawing loop.
    float[] xs = new float[0]; // Normalized horizontal positions borrowed by the font.
    float[] ys = new float[0]; // Normalized vertical positions borrowed by the font.
    AtlasGlyph[] images = new AtlasGlyph[0]; // Reused image references validated against the font's eviction version.
    long imageVersion = -1; // Font cache version supplying all image references; negative means invalid.
    int imageRaster = -1; // Packed physical coverage size and hinting mode, or zero for shared MSDF images.
    String text; // Immutable source key borrowed by the font's layout cache; null before a successful rebuild.
    float size; // Logical units per em borrowed by the owning font's drawing loop.
    private float width; // Normalized maximum advance across lines.
    private float height; // Normalized line-box height.
    int count; // Used prefix of parallel glyph arrays, borrowed by the font.
    int[] clusters; // Lazy glyph-to-source offsets for styled drawing, null for ordinary layouts.
    private int sourceLength; // Full UTF-16 length supplied as context to HarfBuzz during rebuilding.
    private float penX; // Temporary normalized horizontal shaping pen.
    private float penY; // Temporary normalized baseline position.
    private AdaptiveTextCarets carets; // Lazy single-line editing geometry, null for drawing-only layouts.
    private boolean multiline; // Whether editing requires separate per-line layouts.

    /**
     * Creates an empty reusable layout; rebuilding supplies text and size.
     * @param font borrowed live font
     */
    AdaptiveTextRun(AdaptiveFont font) { this.font = font; }

    @Override
    public AdaptiveFont font() { return font; }

    @Override
    public float width() { return width * size; }

    @Override
    public float height() { return height * size; }

    @Override
    public void rebuild(String source, float size) {
        font.checkOwner();
        if (!Float.isFinite(size) || size < 0) throw new IllegalArgumentException("Size must be finite and nonnegative");
        String normalized = source == null ? "" : source;
        if (normalized.equals(text)) {
            this.size = size;
            return;
        }
        text = null;
        imageVersion = -1;
        Arrays.fill(images, null);
        this.size = size;
        count = 0;
        width = 0;
        penY = 0;
        multiline = normalized.indexOf('\n') >= 0;
        if (carets != null && !multiline) carets.reset(normalized);
        int lines = 0;
        if (!normalized.isEmpty()) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                int bytes = Math.multiplyExact(normalized.length(), 2);
                long chars = bytes <= 8192 ? stack.nmalloc(2, bytes) : nmemAlloc(bytes);
                if (chars == 0) throw new OutOfMemoryError("UTF-16 layout staging allocation failed");
                try {
                    sourceLength = normalized.length();
                    for (int i = 0; i < sourceLength; i++) memPutShort(chars + (long) i * 2, (short) normalized.charAt(i));
                    int start = 0;
                    do {
                        int end = normalized.indexOf('\n', start);
                        if (end < 0) end = normalized.length();
                        int contentEnd = end > start && normalized.charAt(end - 1) == '\r' ? end - 1 : end;
                        penX = 0;
                        shapeLine(normalized, chars, start, contentEnd);
                        width = Math.max(width, penX);
                        penY -= font.lineHeight();
                        lines++;
                        start = end + 1;
                    } while (start <= normalized.length());
                } finally {
                    if (bytes > 8192) nmemFree(chars);
                }
            }
        }
        height = lines == 0 ? 0 : font.ascent() - font.descent() + (lines - 1) * font.lineHeight();
        if (carets != null && !multiline) carets.finish();
        text = normalized;
    }

    @Override
    public float caretX(int index) { return editing().caretX(index) * size; }

    @Override
    public int hitTest(float x) {
        if (!Float.isFinite(x)) throw new IllegalArgumentException("Hit-test position must be finite");
        return editing().hitTest(x, size);
    }

    @Override
    public int selectionRanges(int start, int end, float[] ranges) {
        return editing().selectionRanges(start, end, size, ranges);
    }

    /**
     * Builds editing geometry on first use by replaying the current shaping pass.
     * Drawing-only runs retain no cluster or grapheme arrays. Subsequent edits
     * update this storage during normal rebuilding without an extra shaping pass.
     *
     * @return live normalized editing geometry for this single-line layout
     */
    private AdaptiveTextCarets editing() {
        font.checkOwner();
        if (multiline) throw new IllegalStateException("Use a separate layout for each editable line");
        if (carets == null) {
            carets = new AdaptiveTextCarets();
            String source = text;
            text = null;
            rebuild(source, size);
        }
        return carets;
    }

    /**
     * Resolves directional runs in visual order before shaping each segment.
     * @param source complete source string
     * @param chars native UTF-16 source
     * @param start first character of paragraph
     * @param end paragraph boundary excluding newline
     */
    private void shapeLine(String source, long chars, int start, int end) {
        if (start == end) return;
        boolean directional = false;
        for (int i = start; i < end; i++) {
            if (source.charAt(i) < 0x590) continue;
            directional = true;
            break;
        }
        if (!directional) {
            shapeTabs(source, chars, start, end, 0);
            return;
        }
        String line = source.substring(start, end);
        Bidi bidi = new Bidi(line, Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT);
        int runs = bidi.getRunCount();
        byte[] levels = new byte[runs];
        Integer[] visual = new Integer[runs];
        for (int i = 0; i < runs; i++) {
            levels[i] = (byte) bidi.getRunLevel(i);
            visual[i] = i;
        }
        Bidi.reorderVisually(levels, 0, visual, 0, runs);
        for (int i = 0; i < runs; i++) {
            int run = visual[i];
            shapeTabs(source, chars, start + bidi.getRunStart(run), start + bidi.getRunLimit(run), bidi.getRunLevel(run));
        }
    }

    /**
     * Keeps tab controls out of OpenType substitution while preserving visual order.
     * @param source complete source
     * @param chars native UTF-16 source
     * @param start segment start
     * @param end segment boundary
     * @param level bidi embedding level controlling visual direction and carets
     */
    private void shapeTabs(String source, long chars, int start, int end, int level) {
        if ((level & 1) != 0) {
            int limit = end;
            for (int i = end - 1; i >= start; i--) {
                if (source.charAt(i) != '\t') continue;
                shapeScripts(source, chars, i + 1, limit, level);
                if (carets != null && !multiline) carets.cluster(i, i + 1, penX, font.spaceAdvance() * 4, level);
                penX += font.spaceAdvance() * 4;
                limit = i;
            }
            shapeScripts(source, chars, start, limit, level);
        } else {
            int first = start;
            for (int i = start; i < end; i++) {
                if (source.charAt(i) != '\t') continue;
                shapeScripts(source, chars, first, i, level);
                if (carets != null && !multiline) carets.cluster(i, i + 1, penX, font.spaceAdvance() * 4, level);
                penX += font.spaceAdvance() * 4;
                first = i + 1;
            }
            shapeScripts(source, chars, first, end, level);
        }
    }

    /**
     * Separates scripts within a directional run so HarfBuzz applies the correct
     * OpenType shaper to each writing system. Common and inherited characters
     * travel with their neighboring strong script; supplementary units stay paired.
     * @param source complete source
     * @param chars native UTF-16 source
     * @param start segment start
     * @param end segment boundary
     * @param level bidi embedding level controlling visual traversal
     */
    private void shapeScripts(String source, long chars, int start, int end, int level) {
        boolean rtl = (level & 1) != 0;
        UnicodeScript previous = null;
        int boundary = rtl ? end : start;
        int position = boundary;
        while (rtl ? position > start : position < end) {
            int codepoint = rtl ? source.codePointBefore(position) : source.codePointAt(position);
            int length = Character.charCount(codepoint);
            int characterStart = rtl ? position - length : position;
            UnicodeScript script = UnicodeScript.of(codepoint);
            if (script != UnicodeScript.COMMON && script != UnicodeScript.INHERITED && script != UnicodeScript.UNKNOWN) {
                if (previous != null && previous != script) {
                    if (rtl) shape(chars, position, boundary, level);
                    else shape(chars, boundary, position, level);
                    boundary = position;
                }
                previous = script;
            }
            position = rtl ? characterStart : position + length;
        }
        if (rtl) shape(chars, start, boundary, level);
        else shape(chars, boundary, end, level);
    }

    /**
     * Copies visual glyph indices and design positions from reusable native scratch.
     * @param chars complete native UTF-16 source
     * @param start first UTF-16 unit
     * @param end boundary after last unit
     * @param level bidi embedding level controlling HarfBuzz direction
     */
    private void shape(long chars, int start, int end, int level) {
        if (start == end) return;
        boolean rtl = (level & 1) != 0;
        AdaptiveNativeFace face = font.nativeFace();
        long buffer = face.buffer();
        nhb_buffer_add_utf16(buffer, chars, sourceLength, start, end - start);
        hb_buffer_set_direction(buffer, rtl ? HB_DIRECTION_RTL : HB_DIRECTION_LTR);
        hb_buffer_set_language(buffer, AdaptiveNativeFace.LANGUAGE_UND);
        hb_buffer_guess_segment_properties(buffer);
        hb_shape(face.font(), buffer, null);
        if (!hb_buffer_allocation_successful(buffer)) throw new OutOfMemoryError("HarfBuzz layout allocation failed");
        int length = hb_buffer_get_length(buffer);
        ensureCapacity(count + length);
        long info = nhb_buffer_get_glyph_infos(buffer, 0);
        long positions = nhb_buffer_get_glyph_positions(buffer, 0);
        float inverseEm = 1f / face.face().units_per_EM();
        float segmentX = penX;
        for (int i = 0; i < length; i++) {
            long position = positions + (long) i * hb_glyph_position_t.SIZEOF;
            glyphs[count] = hb_glyph_info_t.ncodepoint(info + (long) i * hb_glyph_info_t.SIZEOF);
            if (clusters != null) clusters[count] = hb_glyph_info_t.ncluster(info + (long) i * hb_glyph_info_t.SIZEOF);
            xs[count] = penX + hb_glyph_position_t.nx_offset(position) * inverseEm;
            ys[count] = penY + hb_glyph_position_t.ny_offset(position) * inverseEm;
            penX += hb_glyph_position_t.nx_advance(position) * inverseEm;
            count++;
        }
        if (carets == null || multiline) return;
        int limit = end;
        int i = 0;
        while (i < length) {
            int cluster = hb_glyph_info_t.ncluster(info + (long) i * hb_glyph_info_t.SIZEOF);
            float advance = 0;
            do {
                advance += hb_glyph_position_t.nx_advance(positions + (long) i * hb_glyph_position_t.SIZEOF) * inverseEm;
                i++;
            } while (i < length && hb_glyph_info_t.ncluster(info + (long) i * hb_glyph_info_t.SIZEOF) == cluster);
            int next = rtl ? limit : i == length ? end : hb_glyph_info_t.ncluster(info + (long) i * hb_glyph_info_t.SIZEOF);
            carets.cluster(cluster, next, segmentX, advance, level);
            segmentX += advance;
            limit = cluster;
        }
    }

    /**
     * Grows parallel primitive arrays together without per-glyph wrappers.
     * @param needed minimum element count
     */
    private void ensureCapacity(int needed) {
        if (needed <= glyphs.length) return;
        int capacity = Math.max(needed, Math.max(16, glyphs.length * 2));
        glyphs = Arrays.copyOf(glyphs, capacity);
        xs = Arrays.copyOf(xs, capacity);
        ys = Arrays.copyOf(ys, capacity);
        if (clusters != null) clusters = Arrays.copyOf(clusters, capacity);
    }

    @Override
    public void draw(TextureBatch batch, float x, float y, Color tint) {
        draw(batch, x, y, 0, tint);
    }

    @Override
    public void drawStyled(TextureBatch batch, float x, float y, int[] ends, Color[] colors, int count) {
        font.checkOwner();
        if (ends == null || colors == null) throw new NullPointerException("Style arrays cannot be null");
        if (count < 0 || count > ends.length || count > colors.length) throw new IllegalArgumentException("Invalid style count");
        int previous = 0;
        for (int i = 0; i < count; i++) {
            if (ends[i] < previous || ends[i] > text.length()) throw new IllegalArgumentException("Style ends must follow source order");
            previous = ends[i];
        }
        if (previous != text.length()) throw new IllegalArgumentException("Styles must cover the complete source");
        if (count == 0) return;
        if (count == 1) {
            draw(batch, x, y, colors[0]);
            return;
        }
        if (clusters == null) {
            clusters = new int[glyphs.length];
            String source = text;
            text = null;
            rebuild(source, size);
        }
        font.drawRun(batch, this, x, y, 0, null, ends, colors, count);
    }

    /**
     * Draws around the baseline origin; any rotation selects distance fields.
     * @param batch active borrowed batch
     * @param x baseline origin X
     * @param y baseline origin Y
     * @param radians counterclockwise rotation
     * @param tint borrowed color, null for white
     */
    public void draw(TextureBatch batch, float x, float y, float radians, Color tint) {
        font.drawRun(batch, this, x, y, radians, tint, null, null, 0);
    }
}
