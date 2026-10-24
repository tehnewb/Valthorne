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
 * transfers completed editing arrays to immutable SlugShape storage; draw-only
 * instances stream directly into their borrowed destination without staging
 * glyph or editing arrays. Native UTF-16 scratch is borrowed from the face, with oversized input released
 * before build returns. Printable ASCII still uses full HarfBuzz shaping while
 * avoiding unnecessary bidi, script and grapheme itemization. Directional runs are
 * reordered visually while their cluster indices retain absolute UTF-16 positions.
 */
final class SlugShapeBuilder {
    String text; // Original source whose logical indices are preserved.
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
    private SlugTextRun drawTarget; // Borrowed draw-only destination, cleared before a rebuild returns.
    private boolean editing = true; // Whether caret and selection metadata is required by this build.
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
        this(face, text, lineHeight, true);
    }

    /**
     * Creates editing staging or a draw-only stream with no editing arrays.
     *
     * @param face borrowed exact-file shaping face
     * @param text initial UTF-16 input
     * @param lineHeight baseline spacing in em units
     * @param editing whether immutable caret/selection layouts will be published
     */
    SlugShapeBuilder(SlugNativeFace face, String text, float lineHeight, boolean editing) {
        this.face = face;
        this.text = text;
        this.lineHeight = lineHeight;
        this.editing = editing;
        int capacity = editing ? Math.max(8, text.length()) : 0;
        glyphs = new int[capacity];
        xs = new float[capacity];
        ys = new float[capacity];
        clusterStarts = new int[capacity];
        clusterEnds = new int[capacity];
        clusterLefts = new float[capacity];
        clusterWidths = new float[capacity];
        clusterLines = new int[capacity];
        clusterRtl = new boolean[capacity];
        carets = new float[editing ? text.length() + 1 : 0];
        caretLines = new int[editing ? text.length() + 1 : 0];
        boundaries = new boolean[editing ? text.length() + 1 : 0];
    }

    /**
     * Reuses draw-only staging after the previous borrower consumed its positions.
     * Full HarfBuzz substitution, positioning and Unicode itemization remain active;
     * editing-only arrays are untouched because no immutable shape escapes.
     *
     * @param text replacement UTF-16 input
     * @param target borrowed destination receiving fully shaped glyph positions
     */
    void rebuildRun(String text, SlugTextRun target) {
        this.text = text;
        glyphCount = clusterCount = line = 0;
        lines = 1;
        width = penX = 0;
        editing = false;
        drawTarget = target;
        try {
            buildLayout();
        } finally {
            drawTarget = null;
        }
    }

    /**
     * Shapes paragraphs and returns a native-resource-independent layout.
     *
     * @return immutable normalized layout
     */
    SlugShape build() {
        buildLayout();
        return new SlugShape(this);
    }

    /**
     * Executes the shared shaping algorithm into this builder's primitive arrays.
     * Only immutable public layouts require editing metadata; drawing consumes
     * glyph indices and offsets directly before this storage can be reused.
     */
    private void buildLayout() {
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
                if (editing) Arrays.fill(boundaries, true);
                shapeSegment(0, text.length(), false);
                width = penX;
                return;
            }
            if (editing) {
                BreakIterator graphemes = BreakIterator.getCharacterInstance(Locale.ROOT);
                graphemes.setText(text);
                for (int index = graphemes.first(); index != BreakIterator.DONE; index = graphemes.next()) boundaries[index] = true;
            }
            int start = 0;
            while (start <= text.length()) {
                int end = start;
                while (end < text.length() && text.charAt(end) != '\n' && text.charAt(end) != '\r') end++;
                shapeLine(start, end);
                width = Math.max(width, penX);
                if (end == text.length()) break;
                if (editing) caretLines[end] = line;
                int next = end + 1;
                if (text.charAt(end) == '\r' && next < text.length() && text.charAt(next) == '\n') next++;
                for (int index = end + 1; editing && index < next; index++) {
                    carets[index] = carets[end];
                    caretLines[index] = line;
                }
                start = next;
                line++;
                lines++;
                penX = 0;
            }
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
        if (editing) {
            carets[start] = 0;
            caretLines[start] = line;
        }
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
        int length = hb_buffer_get_length(buffer);
        long info = nhb_buffer_get_glyph_infos(buffer, 0L);
        long positions = nhb_buffer_get_glyph_positions(buffer, 0L);
        if (info == 0L || positions == 0L) return;
        float inverseEm = 1f / face.unitsPerEm();
        if (drawTarget != null) {
            streamGlyphs(info, positions, length, inverseEm);
            return;
        }
        int firstGlyph = 0;
        int previousClusterEnd = end;
        while (firstGlyph < length) {
            int cluster = hb_glyph_info_t.ncluster(info + (long) firstGlyph * hb_glyph_info_t.SIZEOF);
            float left = penX;
            int limit = firstGlyph;
            while (limit < length && hb_glyph_info_t.ncluster(info + (long) limit * hb_glyph_info_t.SIZEOF) == cluster) {
                int glyph = hb_glyph_info_t.ncodepoint(info + (long) limit * hb_glyph_info_t.SIZEOF);
                float x = penX + hb_glyph_position_t.nx_offset(positions + (long) limit * hb_glyph_position_t.SIZEOF) * inverseEm;
                float y = -line * lineHeight + hb_glyph_position_t.ny_offset(positions + (long) limit * hb_glyph_position_t.SIZEOF) * inverseEm;
                growGlyphs();
                glyphs[glyphCount] = glyph;
                xs[glyphCount] = x;
                ys[glyphCount] = y;
                penX += hb_glyph_position_t.nx_advance(positions + (long) limit * hb_glyph_position_t.SIZEOF) * inverseEm;
                glyphCount++;
                limit++;
            }
            // MONOTONE_GRAPHEMES guarantees ascending LTR or descending RTL clusters.
            int logicalEnd = rtl ? previousClusterEnd : limit < length ? hb_glyph_info_t.ncluster(info + (long) limit * hb_glyph_info_t.SIZEOF) : end;
            addCluster(cluster, logicalEnd, left, penX - left, rtl);
            previousClusterEnd = cluster;
            firstGlyph = limit;
        }
    }

    /**
     * Streams native visual-order positions without rebuilding editing clusters.
     * HarfBuzz has already resolved substitutions, clusters and bidi-local glyph
     * order; drawing consumes only indices, offsets and cumulative advances.
     *
     * @param info live HarfBuzz glyph-info storage
     * @param positions matching live native glyph positions
     * @param length native visual glyph count
     * @param inverseEm font-unit to em-space conversion
     */
    private void streamGlyphs(long info, long positions, int length, float inverseEm) {
        float baseline = -line * lineHeight;
        for (int i = 0; i < length; i++) {
            long glyph = info + (long) i * hb_glyph_info_t.SIZEOF;
            long position = positions + (long) i * hb_glyph_position_t.SIZEOF;
            float x = penX + hb_glyph_position_t.nx_offset(position) * inverseEm;
            float y = baseline + hb_glyph_position_t.ny_offset(position) * inverseEm;
            drawTarget.appendShapedGlyph(hb_glyph_info_t.ncodepoint(glyph), x, y);
            penX += hb_glyph_position_t.nx_advance(position) * inverseEm;
        }
        glyphCount += length;
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
        if (!editing) return;
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
