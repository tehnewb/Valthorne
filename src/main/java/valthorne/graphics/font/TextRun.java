package valthorne.graphics.font;

import valthorne.graphics.Color;
import valthorne.graphics.texture.TextureBatch;

/**
 * Retained text layout shared by UI controls and font backends. The source font
 * is borrowed and must outlive drawing. Rebuilding unchanged text should reuse
 * storage; this object owns no GPU resources and needs no explicit disposal.
 */
public interface TextRun {
    /**
     * Returns the font supplying this layout's metrics.
     * @return borrowed font
     */
    TextFont font();

    /**
     * Rebuilds this layout when text or logical size changes.
     * @param text new source
     * @param size logical units per em
     */
    void rebuild(String text, float size);

    /**
     * Returns the logical advance width.
     * @return width in logical units
     */
    float width();

    /**
     * Returns the logical line-box height.
     * @return height in logical units
     */
    float height();

    /**
     * Returns a primary visual caret position in a single-line layout. Offsets
     * inside an extended grapheme snap backward. At a bidi boundary the lower
     * embedding level supplies the primary caret; equal levels prefer the next
     * grapheme. Ligature advances are divided among their graphemes.
     * Editing geometry is created lazily and reused by unchanged layouts.
     *
     * @param index UTF-16 insertion offset, clamped to the source
     * @return horizontal position relative to the baseline origin
     * @throws IllegalStateException if the layout contains a newline
     */
    float caretX(int index);

    /**
     * Finds the nearest primary caret in a single-line layout without assuming
     * that logical character offsets increase from left to right.
     *
     * @param x finite horizontal position relative to the baseline origin
     * @return UTF-16 insertion offset at an extended grapheme boundary
     * @throws IllegalStateException if the layout contains a newline
     */
    int hitTest(float x);

    /**
     * Writes visual selection intervals as consecutive left/right pairs. Bidi
     * selections can contain several disjoint intervals. The returned count
     * includes pairs that do not fit, allowing the caller to grow its reusable
     * array and retry. Endpoints are clamped and expanded to grapheme boundaries.
     *
     * @param start first UTF-16 selection endpoint
     * @param end second UTF-16 selection endpoint
     * @param ranges caller-owned output storage; an empty array queries capacity
     * @return number of visual intervals required
     * @throws IllegalStateException if the layout contains a newline
     */
    int selectionRanges(int start, int end, float[] ranges);

    /**
     * Draws the retained layout at a baseline origin.
     * @param batch active destination
     * @param x horizontal origin
     * @param y baseline origin
     * @param tint borrowed color, or null for white
     */
    void draw(TextureBatch batch, float x, float y, Color tint);

    /**
     * Draws ordered logical color spans using the complete layout's visual glyph
     * order. Ligatures and combining clusters retain the color of their first
     * UTF-16 unit. This preserves contextual shapes, overlapping glyph order and
     * bidi placement across color boundaries. Source indices are retained lazily;
     * warm styled drawing reuses their storage and visits each glyph once.
     *
     * @param batch active borrowed batch
     * @param x baseline origin X
     * @param y baseline origin Y
     * @param ends nondecreasing exclusive UTF-16 span ends; the last must equal source length
     * @param colors borrowed span colors; a null entry means white
     * @param count used prefix of both arrays; zero is valid only for empty text
     * @throws IllegalArgumentException if the used spans do not cover the complete source
     */
    void drawStyled(TextureBatch batch, float x, float y, int[] ends, Color[] colors, int count);
}
