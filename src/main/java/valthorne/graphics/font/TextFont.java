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
     * Enables a round outline using logical drawing units, without changing layout.
     * Shared-font changes affect immediate text and all existing retained runs.
     * Configure on the graphics owner thread. Implementations may bound the radius.
     *
     * @param width positive outline radius in logical units
     * @param color outline color copied by the font; null retains its current color
     * @return this font for chaining
     */
    TextFont setOutline(int width, Color color);

    /**
     * Disables outlining for subsequent draws, including already retained runs.
     */
    void disableOutline();

    /**
     * Reports whether outlining is enabled.
     * @return whether subsequent draws include an outline
     */
    boolean isOutlineEnabled();

    /**
     * Returns the configured logical outline radius, even when disabled.
     * @return positive outline radius
     */
    int getOutlineWidth();

    /**
     * Enables a shadow behind the complete run and its outline. Offsets use logical
     * drawing units; positive X points right and positive Y points up in the batch's
     * bottom-up coordinates. Rotated runs rotate the offset with the text. Colors
     * are copied and alpha multiplies the corresponding fill/style opacity.
     * @param offsetX finite horizontal displacement
     * @param offsetY finite vertical displacement
     * @param color copied shadow color, or null to keep the current color
     * @return this font for chaining
     * @throws IllegalArgumentException if an offset is non-finite
     */
    TextFont setShadow(float offsetX, float offsetY, Color color);

    /**
     * Disables the shadow while retaining its configured offsets and color.
     */
    void disableShadow();

    /**
     * Reports whether subsequent immediate and retained draws include a shadow.
     * @return shadow state
     */
    boolean isShadowEnabled();

    /**
     * Returns the configured horizontal displacement in logical drawing units.
     * @return shadow X offset, positive right
     */
    float getShadowOffsetX();

    /**
     * Returns the configured vertical displacement in logical drawing units.
     * @return shadow Y offset, positive up in the drawing batch
     */
    float getShadowOffsetY();

    /**
     * Copies the configured shadow color without exposing mutable font state.
     * This getter allocates a small color snapshot; it is not needed for drawing.
     * @return caller-owned shadow color copy
     */
    Color getShadowColor();

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
