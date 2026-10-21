package valthorne.graphics.font.slug;

import org.lwjgl.util.harfbuzz.hb_glyph_info_t;
import org.lwjgl.util.harfbuzz.hb_glyph_position_t;

import java.lang.Character.UnicodeScript;
import java.nio.ByteBuffer;
import java.text.Bidi;
import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static org.lwjgl.system.MemoryUtil.memAlloc;
import static org.lwjgl.system.MemoryUtil.memFree;
import static org.lwjgl.util.harfbuzz.HarfBuzz.*;

/**
 * Unicode paragraph/script analysis and HarfBuzz layout assembly. This builder
 * is single-use: completed arrays transfer to immutable SlugShape storage;
 * native UTF-16 scratch is borrowed from the face, with oversized input released
 * before build returns. Printable ASCII still uses full HarfBuzz shaping while
 * avoiding unnecessary bidi, script and grapheme itemization. Directional runs are
 * reordered visually while their cluster indices retain absolute UTF-16 positions.
 */
final class SlugShapeBuilder {
    final String text; // Original source whose logical indices are preserved.
    final float lineHeight; // Baseline spacing in em units.
    final float[] carets; // Leading caret positions for every UTF-16 offset.
    final int[] caretLines; // Logical line number for each insertion position.
    final boolean[] boundaries; // Extended grapheme insertion boundaries.
    int[] glyphs; // Growable actual OpenType glyph indices in visual draw order.
    float[] xs; // Per-glyph horizontal em positions, including shaping offsets.
    float[] ys; // Per-glyph baseline-relative vertical em positions.
    int[] clusterStarts; // Logical inclusive cluster indices in visual order.
    int[] clusterEnds; // Logical exclusive cluster indices in visual order.
    float[] clusterLefts; // Visual cluster left coordinates in em units.
    float[] clusterWidths; // Visual cluster advances in em units.
    int[] clusterLines; // Visual cluster logical line numbers.
    boolean[] clusterRtl; // Cluster-local caret direction, independent of neighboring bidi affinity.
    int glyphCount; // Populated glyph entries.
    int clusterCount; // Populated visual cluster entries.
    int lines = 1; // Number of logical lines, including a trailing empty line.
    float width; // Largest line advance in em units.
    private final SlugNativeFace face; // Borrowed native face and reusable HarfBuzz scratch.
    private ByteBuffer utf16; // Borrowed or temporary absolute-indexed UTF-16 native input.
    private float penX; // Current visual pen X in em units.
    private int line; // Current logical line number.

    /**
     * Allocates proportional cold-path primitive staging for a text layout.
     *
     * @param face live exact-file shaping face
     * @param text complete UTF-16 source
     * @param lineHeight baseline spacing in em units
     */
    SlugShapeBuilder(SlugNativeFace face, String text, float lineHeight) {
        this.face = face;
        this.text = text;
        this.lineHeight = lineHeight;
        int capacity = Math.max(8, text.length());
        glyphs = new int[capacity];
        xs = new float[capacity];
        ys = new float[capacity];
        clusterStarts = new int[capacity];
        clusterEnds = new int[capacity];
        clusterLefts = new float[capacity];
        clusterWidths = new float[capacity];
        clusterLines = new int[capacity];
        clusterRtl = new boolean[capacity];
        carets = new float[text.length() + 1];
        caretLines = new int[text.length() + 1];
        boundaries = new boolean[text.length() + 1];
    }

    /**
     * Shapes paragraphs and returns a native-resource-independent layout.
     *
     * @return immutable normalized layout
     */
    SlugShape build() {
        int bytes = Math.max(2, Math.multiplyExact(text.length(), 2));
        ByteBuffer borrowed = face == null ? null : face.textScratch(text.length());
        utf16 = borrowed == null ? memAlloc(bytes) : borrowed;
        try {
            boolean printableAscii = !text.isEmpty();
            for (int i = 0; i < text.length(); i++) {
                char character = text.charAt(i);
                utf16.putChar(i * 2, character);
                printableAscii &= character >= ' ' && character <= '~';
            }
            utf16.limit(text.length() * 2);
            if (printableAscii) {
                Arrays.fill(boundaries, true);
                shapeSegment(0, text.length(), false);
                width = penX;
                return new SlugShape(this);
            }
            BreakIterator graphemes = BreakIterator.getCharacterInstance(Locale.ROOT);
            graphemes.setText(text);
            for (int index = graphemes.first(); index != BreakIterator.DONE; index = graphemes.next()) boundaries[index] = true;
            int start = 0;
            while (start <= text.length()) {
                int end = start;
                while (end < text.length() && text.charAt(end) != '\n' && text.charAt(end) != '\r') end++;
                shapeLine(start, end);
                width = Math.max(width, penX);
                if (end == text.length()) break;
                caretLines[end] = line;
                int next = end + 1;
                if (text.charAt(end) == '\r' && next < text.length() && text.charAt(next) == '\n') next++;
                for (int index = end + 1; index < next; index++) {
                    carets[index] = carets[end];
                    caretLines[index] = line;
                }
                start = next;
                line++;
                lines++;
                penX = 0;
            }
            return new SlugShape(this);
        } finally {
            if (borrowed == null) memFree(utf16);
            utf16 = null;
        }
    }

    /**
     * Itemizes and visually reorders one line before shaping its script runs.
     *
     * @param start inclusive absolute line start
     * @param end exclusive absolute line end
     */
    private void shapeLine(int start, int end) {
        carets[start] = 0;
        caretLines[start] = line;
        if (start == end) return;
        Bidi bidi = new Bidi(text.substring(start, end), Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT);
        List<SlugScriptRun> logical = new ArrayList<>();
        for (int run = 0; run < bidi.getRunCount(); run++) {
            int first = start + bidi.getRunStart(run);
            int limit = start + bidi.getRunLimit(run);
            byte level = (byte) bidi.getRunLevel(run);
            int segment = first;
            UnicodeScript script = UnicodeScript.COMMON;
            for (int offset = first; offset < limit;) {
                int codepoint = text.codePointAt(offset);
                UnicodeScript next = UnicodeScript.of(codepoint);
                if (next != UnicodeScript.COMMON && next != UnicodeScript.INHERITED && next != UnicodeScript.UNKNOWN) {
                    if (script != UnicodeScript.COMMON && script != next) {
                        logical.add(new SlugScriptRun(segment, offset, level));
                        segment = offset;
                    }
                    script = next;
                }
                offset += Character.charCount(codepoint);
            }
            logical.add(new SlugScriptRun(segment, limit, level));
        }
        SlugScriptRun[] runs = logical.toArray(SlugScriptRun[]::new);
        byte[] levels = new byte[runs.length];
        for (int i = 0; i < runs.length; i++) levels[i] = runs[i].level();
        Bidi.reorderVisually(levels, 0, runs, 0, runs.length);
        for (SlugScriptRun run : runs) shapeRun(run.start(), run.end(), (run.level() & 1) != 0);
    }

    /**
     * Splits tab-delimited segments while retaining their visual directional order.
     *
     * @param start inclusive script run start
     * @param end exclusive script run end
     * @param rtl whether this run is right-to-left
     */
    private void shapeRun(int start, int end, boolean rtl) {
        boolean hasTab = false;
        for (int offset = start; offset < end; offset++) {
            if (text.charAt(offset) == '\t') {
                hasTab = true;
                break;
            }
        }
        if (!hasTab) {
            shapeSegment(start, end, rtl);
            return;
        }
        if (!rtl) {
            int segment = start;
            for (int offset = start; offset < end; offset++) {
                if (text.charAt(offset) != '\t') continue;
                shapeSegment(segment, offset, false);
                tab(offset, false);
                segment = offset + 1;
            }
            shapeSegment(segment, end, false);
        } else {
            int limit = end;
            for (int offset = end - 1; offset >= start; offset--) {
                if (text.charAt(offset) != '\t') continue;
                shapeSegment(offset + 1, limit, true);
                tab(offset, true);
                limit = offset;
            }
            shapeSegment(start, limit, true);
        }
    }

    /**
     * Records a four-space tab using the exact font's nominal space advance.
     *
     * @param position logical UTF-16 tab index
     * @param rtl whether the enclosing segment is right-to-left
     */
    private void tab(int position, boolean rtl) {
        float advance = face.spaceAdvance() * 4;
        addCluster(position, position + 1, penX, advance, rtl);
        penX += advance;
    }

    /**
     * Shapes a single script/directional segment into positioned glyph indices.
     *
     * @param start inclusive logical start
     * @param end exclusive logical end
     * @param rtl native shaping direction
     */
    private void shapeSegment(int start, int end, boolean rtl) {
        if (start == end) return;
        long buffer = face.buffer();
        hb_buffer_add_utf16(buffer, utf16, start, end - start);
        hb_buffer_set_direction(buffer, rtl ? HB_DIRECTION_RTL : HB_DIRECTION_LTR);
        hb_buffer_set_language(buffer, SlugNativeFace.LANGUAGE_UND);
        hb_buffer_set_cluster_level(buffer, HB_BUFFER_CLUSTER_LEVEL_MONOTONE_GRAPHEMES);
        hb_buffer_guess_segment_properties(buffer);
        hb_shape(face.font(), buffer, null);
        if (!hb_buffer_allocation_successful(buffer)) throw new OutOfMemoryError("HarfBuzz layout allocation failed");
        hb_glyph_info_t.Buffer info = hb_buffer_get_glyph_infos(buffer);
        hb_glyph_position_t.Buffer positions = hb_buffer_get_glyph_positions(buffer);
        if (info == null || positions == null) return;
        float inverseEm = 1f / face.unitsPerEm();
        int firstGlyph = 0;
        int previousClusterEnd = end;
        while (firstGlyph < info.remaining()) {
            int cluster = info.get(firstGlyph).cluster();
            float left = penX;
            int limit = firstGlyph;
            while (limit < info.remaining() && info.get(limit).cluster() == cluster) {
                growGlyphs();
                glyphs[glyphCount] = info.get(limit).codepoint();
                xs[glyphCount] = penX + positions.get(limit).x_offset() * inverseEm;
                ys[glyphCount] = -line * lineHeight + positions.get(limit).y_offset() * inverseEm;
                penX += positions.get(limit).x_advance() * inverseEm;
                glyphCount++;
                limit++;
            }
            // MONOTONE_GRAPHEMES guarantees ascending LTR or descending RTL clusters.
            int logicalEnd = rtl ? previousClusterEnd : limit < info.remaining() ? info.get(limit).cluster() : end;
            addCluster(cluster, logicalEnd, left, penX - left, rtl);
            previousClusterEnd = cluster;
            firstGlyph = limit;
        }
    }

    /**
     * Stores visual cluster geometry and grapheme-safe logical caret positions.
     * Ligature carets divide the cluster advance among its graphemes.
     *
     * @param start inclusive logical cluster index
     * @param end exclusive logical cluster index
     * @param left visual left in em units
     * @param advance cluster advance in em units
     * @param rtl whether logical caret order runs right-to-left
     */
    private void addCluster(int start, int end, float left, float advance, boolean rtl) {
        if (clusterCount == clusterStarts.length) {
            int next = clusterCount * 2;
            clusterStarts = Arrays.copyOf(clusterStarts, next);
            clusterEnds = Arrays.copyOf(clusterEnds, next);
            clusterLefts = Arrays.copyOf(clusterLefts, next);
            clusterWidths = Arrays.copyOf(clusterWidths, next);
            clusterLines = Arrays.copyOf(clusterLines, next);
            clusterRtl = Arrays.copyOf(clusterRtl, next);
        }
        clusterStarts[clusterCount] = start;
        clusterEnds[clusterCount] = end;
        clusterLefts[clusterCount] = left;
        clusterWidths[clusterCount] = advance;
        clusterRtl[clusterCount] = rtl;
        clusterLines[clusterCount++] = line;
        int graphemes = 0;
        for (int i = start; i < end; i++) if (boundaries[i]) graphemes++;
        int consumed = 0;
        for (int i = start; i <= end; i++) {
            if (i > start && boundaries[i]) consumed++;
            float fraction = graphemes == 0 ? 0 : Math.min(1, consumed / (float) graphemes);
            carets[i] = left + advance * (rtl ? 1 - fraction : fraction);
            caretLines[i] = line;
        }
    }

    /**
     * Grows glyph staging together when OpenType substitution expands the input.
     */
    private void growGlyphs() {
        if (glyphCount < glyphs.length) return;
        int next = glyphCount * 2;
        glyphs = Arrays.copyOf(glyphs, next);
        xs = Arrays.copyOf(xs, next);
        ys = Arrays.copyOf(ys, next);
    }
}
