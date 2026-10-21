package valthorne.graphics.font.slug;

import valthorne.graphics.font.TextSelection;

import java.util.Arrays;

/**
 * Immutable native-resource-independent HarfBuzz glyph layout in normalized em
 * units. Actual glyph indices, offsets, bidi order, and grapheme caret boundaries
 * are shared by measurement, live outline drawing, editing, and hinted UI views.
 * Glyph rasterization is deliberately separate, so changing physical DPI does not
 * reshape the text or change its logical advance. No system fallback family is
 * substituted; unsupported characters use the supplied font's missing glyph.
 */
public final class SlugShape {
    private final String text; // Source UTF-16 indices used by carets and selections.
    private final int[] glyphs; // Actual OpenType glyph indices in visual order.
    private final float[] xs; // Per-glyph em X including shaping offsets.
    private final float[] ys; // Per-glyph em Y relative to the first baseline.
    private final float[] carets; // Leading em X for each UTF-16 insertion position.
    private final int[] caretLines; // Logical line containing each insertion position.
    private final boolean[] boundaries; // Grapheme-safe insertion positions.
    private final int[] starts; // Logical cluster starts in visual order.
    private final int[] ends; // Logical cluster limits in visual order.
    private final float[] lefts; // Visual cluster lefts in em units.
    private final float[] widths; // Visual cluster advances in em units.
    private final int[] clusterLines; // Line numbers for visual cluster rectangles.
    private final boolean[] clusterRtl; // Local logical direction for partial ligature selection.
    private final float width; // Maximum logical line advance in em units.
    private final float height; // Total logical line height in em units.
    private final float lineHeight; // Baseline spacing in em units.
    private TextSelection selection; // Last lazily computed selection geometry.
    private int selectionStart; // Logical start of the cached selection.
    private int selectionEnd; // Logical limit of the cached selection.
    private float selectionSize; // World/logical em size of the cached rectangles.

    /**
     * Adopts completed staging arrays when already compact, copying only excess capacity.
     *
     * @param builder completed layout whose native scratch has no retained ownership
     */
    SlugShape(SlugShapeBuilder builder) {
        text = builder.text;
        glyphs = builder.glyphs.length == builder.glyphCount ? builder.glyphs : Arrays.copyOf(builder.glyphs, builder.glyphCount);
        xs = builder.xs.length == builder.glyphCount ? builder.xs : Arrays.copyOf(builder.xs, builder.glyphCount);
        ys = builder.ys.length == builder.glyphCount ? builder.ys : Arrays.copyOf(builder.ys, builder.glyphCount);
        carets = builder.carets;
        caretLines = builder.caretLines;
        boundaries = builder.boundaries;
        starts = builder.clusterStarts.length == builder.clusterCount ? builder.clusterStarts : Arrays.copyOf(builder.clusterStarts, builder.clusterCount);
        ends = builder.clusterEnds.length == builder.clusterCount ? builder.clusterEnds : Arrays.copyOf(builder.clusterEnds, builder.clusterCount);
        lefts = builder.clusterLefts.length == builder.clusterCount ? builder.clusterLefts : Arrays.copyOf(builder.clusterLefts, builder.clusterCount);
        widths = builder.clusterWidths.length == builder.clusterCount ? builder.clusterWidths : Arrays.copyOf(builder.clusterWidths, builder.clusterCount);
        clusterLines = builder.clusterLines.length == builder.clusterCount ? builder.clusterLines : Arrays.copyOf(builder.clusterLines, builder.clusterCount);
        clusterRtl = builder.clusterRtl.length == builder.clusterCount ? builder.clusterRtl : Arrays.copyOf(builder.clusterRtl, builder.clusterCount);
        width = builder.width;
        lineHeight = builder.lineHeight;
        height = text.isEmpty() ? 0 : builder.lines * lineHeight;
    }

    /**
     * Returns the number of shaped glyphs, including invisible whitespace.
     *
     * @return glyph count
     */
    public int glyphCount() { return glyphs.length; }

    /**
     * Returns an actual font glyph index after OpenType substitution.
     *
     * @param index visual glyph index
     * @return font glyph index
     */
    public int glyphIndex(int index) { return glyphs[index]; }

    /**
     * Returns a glyph's horizontal shaped baseline position.
     *
     * @param index visual glyph index
     * @return em X coordinate
     */
    public float x(int index) { return xs[index]; }

    /**
     * Returns a glyph's vertical shaped baseline position.
     *
     * @param index visual glyph index
     * @return em Y coordinate
     */
    public float y(int index) { return ys[index]; }

    /**
     * Returns the largest line advance, including trailing whitespace.
     *
     * @return width in em units
     */
    public float width() { return width; }

    /**
     * Returns the full line-box height.
     *
     * @return height in em units
     */
    public float height() { return height; }

    /**
     * Returns a full-context leading caret position. Interior surrogate/combining
     * positions share their enclosing grapheme's leading position.
     *
     * @param position UTF-16 insertion position
     * @return horizontal caret X in em units
     */
    public float caretX(int position) {
        if (position < 0 || position > text.length()) throw new IndexOutOfBoundsException(position);
        return carets[position];
    }

    /**
     * Selects the closest grapheme-safe insertion position on a logical line.
     *
     * @param x horizontal point in em units
     * @param y top-down point in em units
     * @return UTF-16 insertion position
     */
    public int hitTest(float x, float y) {
        int line = Math.max(0, Math.min(Math.max(0, Math.round(height / lineHeight) - 1), (int) Math.floor(y / lineHeight)));
        float distance = Float.POSITIVE_INFINITY;
        int selected = 0;
        for (int i = 0; i < carets.length; i++) {
            if (!boundaries[i] || caretLines[i] != line) continue;
            float next = Math.abs(x - carets[i]);
            if (next < distance) {
                distance = next;
                selected = i;
            }
        }
        return selected;
    }

    /**
     * Borrows cached visual selection rectangles at a logical em size.
     * Bidirectional ranges retain disjoint rectangles; adjacent selected clusters
     * on the same line merge. Whole graphemes are selected at partial boundaries.
     *
     * @param start inclusive UTF-16 position
     * @param end exclusive UTF-16 position
     * @param size finite nonnegative logical em size
     * @return immutable visual rectangles relative to line-box top-left
     */
    public TextSelection selection(int start, int end, float size) {
        if (start < 0 || end < start || end > text.length() || !Float.isFinite(size) || size < 0) throw new IllegalArgumentException("Invalid shaped selection");
        if (selection != null && selectionStart == start && selectionEnd == end && selectionSize == size) return selection;
        float[] rectangles = new float[starts.length * 4];
        int count = 0;
        for (int i = 0; i < starts.length; i++) {
            if (start == end || end <= starts[i] || start >= ends[i]) continue;
            float left = lefts[i];
            float right = left + widths[i];
            int from = Math.max(start, starts[i]);
            int to = Math.min(end, ends[i]);
            if (from > starts[i] || to < ends[i]) {
                while (from > starts[i] && !boundaries[from]) from--;
                while (to < ends[i] && !boundaries[to]) to++;
                int total = 0;
                int before = 0;
                int selected = 0;
                for (int offset = starts[i]; offset < ends[i]; offset++) {
                    if (!boundaries[offset]) continue;
                    total++;
                    if (offset < from) before++;
                    if (offset >= from && offset < to) selected++;
                }
                if (total > 0) {
                    float fraction = widths[i] / total;
                    left += (clusterRtl[i] ? total - before - selected : before) * fraction;
                    right = left + selected * fraction;
                }
            }
            float top = clusterLines[i] * lineHeight * size;
            left *= size;
            right *= size;
            if (right <= left) continue;
            if (count > 0 && rectangles[count - 3] == top && Math.abs(rectangles[count - 4] + rectangles[count - 2] - left) < .0001f) {
                rectangles[count - 2] = right - rectangles[count - 4];
            } else {
                rectangles[count++] = left;
                rectangles[count++] = top;
                rectangles[count++] = right - left;
                rectangles[count++] = lineHeight * size;
            }
        }
        selection = new SlugSelection(Arrays.copyOf(rectangles, count));
        selectionStart = start;
        selectionEnd = end;
        selectionSize = size;
        return selection;
    }
}
