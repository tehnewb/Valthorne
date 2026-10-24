package valthorne.graphics.font;

import valthorne.graphics.Color;
import valthorne.graphics.texture.TextureBatch;

/**
 * Measurement and drawing contract used by UI controls and adaptive fonts.
 * Sizes are logical units per em and drawing coordinates specify the baseline.
 * Fonts borrow the caller's active batch and own their native/GPU resources.
 * Use and dispose each font on its owning graphics thread.
 */
public interface TextFont {
    /**
     * Measures a complete string using the same advances as drawing.
     * @param text source text
     * @param size logical units per em
     * @return logical advance width
     */
    float getWidth(String text, float size);

    /**
     * Measures a UTF-16 range using the font's layout rules.
     * @param text complete source
     * @param startInclusive first character
     * @param endExclusive character boundary after the range
     * @param size logical units per em
     * @return logical advance width
     */
    float getWidth(String text, int startInclusive, int endExclusive, float size);

    /**
     * Measures the logical line-box height of text.
     * @param text source text
     * @param size logical units per em
     * @return logical height
     */
    float getHeight(String text, float size);

    /**
     * Returns normalized distance above the baseline.
     * @return ascent in em units
     */
    float ascent();

    /**
     * Returns normalized distance below the baseline, normally negative.
     * @return descent in em units
     */
    float descent();

    /**
     * Returns normalized baseline spacing.
     * @return line height in em units
     */
    float lineHeight();

    /**
     * Creates a reusable layout borrowing this font.
     * @param text source text
     * @param size logical units per em
     * @return independently mutable retained layout
     */
    TextRun createRun(String text, float size);

    /**
     * Borrows a cached layout for immediate measurement, editing or drawing.
     * The next font operation may rebuild or resize this layout; use
     * {@link #createRun(String, float)} when retaining it across operations.
     * Callers must not rebuild the borrowed result themselves.
     *
     * @param text source text, or null for an empty layout
     * @param size finite nonnegative logical units per em
     * @return borrowed layout valid until another font operation
     */
    TextRun layout(String text, float size);

    /**
     * Draws text in the caller's active batch without taking ownership of it.
     * @param batch active destination
     * @param text source text
     * @param x baseline origin X
     * @param y baseline origin Y
     * @param size logical units per em
     * @param tint borrowed color, or null for white
     */
    void draw(TextureBatch batch, String text, float x, float y, float size, Color tint);

    /**
     * Reports whether native and GPU resources have been released.
     * @return whether this font is disposed
     */
    boolean isDisposed();

    /**
     * Releases owned resources; repeated calls do nothing.
     */
    void dispose();
}
