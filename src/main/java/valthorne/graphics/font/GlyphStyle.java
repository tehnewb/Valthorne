package valthorne.graphics.font;

import valthorne.graphics.Color;

/**
 * Represents the visual styling attributes for a glyph.
 * <p>
 * This class provides various properties to define the appearance of a glyph,
 * including its color, scale, position offset, and visibility. Additionally,
 * this class allows for resetting the styling attributes to their default values
 * and dynamically modifying the color settings.
 *
 * @author Albert Beaupre
 * @since March 5th, 2026
 */
public class GlyphStyle {
    private final Color color = new Color(1, 1, 1, 1); // The color of the glyph (defaults to white with full opacity)
    private float offsetX; // Horizontal offset for glyph positioning
    private float offsetY; // Vertical offset for glyph positioning
    private float scaleX = 1f; // Horizontal scale for glyph size (default is no scale change)
    private float scaleY = 1f; // Vertical scale for glyph size (default is no scale change)
    private boolean visible = true; // Visibility flag indicating if the glyph is rendered
    private boolean hasColor; // Flag indicating if a custom color is applied to the glyph

    /**
     * Retrieves the horizontal offset of the glyph.
     *
     * @return the horizontal offset.
     */
    public float getOffsetX() {return offsetX;}

    /**
     * Retrieves the vertical offset of the glyph.
     *
     * @return the vertical offset.
     */
    public float getOffsetY() {return offsetY;}

    /**
     * Retrieves the horizontal scale of the glyph.
     *
     * @return the horizontal scale.
     */
    public float getScaleX() {return scaleX;}

    /**
     * Retrieves the vertical scale of the glyph.
     *
     * @return the vertical scale.
     */
    public float getScaleY() {return scaleY;}

    /**
     * Checks if the glyph is visible.
     *
     * @return {@code true} if the glyph is visible, otherwise {@code false}.
     */
    public boolean isVisible() {return visible;}

    /**
     * Checks if the glyph has a custom color set.
     *
     * @return {@code true} if a custom color is applied, otherwise {@code false}.
     */
    public boolean hasColor() {return hasColor;}

    /**
     * Sets the horizontal offset for the glyph.
     *
     * @param offsetX the value to set as the horizontal offset.
     */
    public void setOffsetX(float offsetX) {this.offsetX = offsetX;}

    /**
     * Sets the vertical offset for the glyph.
     *
     * @param offsetY the value to set as the vertical offset.
     */
    public void setOffsetY(float offsetY) {this.offsetY = offsetY;}

    /**
     * Sets both horizontal and vertical offsets for the glyph.
     *
     * @param offsetX the value to set as the horizontal offset.
     * @param offsetY the value to set as the vertical offset.
     */
    public void setOffset(float offsetX, float offsetY) {
        this.offsetX = offsetX;
        this.offsetY = offsetY;
    }

    /**
     * Sets the horizontal scale of the glyph.
     *
     * @param scaleX the value to set as the horizontal scale.
     */
    public void setScaleX(float scaleX) {this.scaleX = scaleX;}

    /**
     * Sets the vertical scale of the glyph.
     *
     * @param scaleY the value to set as the vertical scale.
     */
    public void setScaleY(float scaleY) {this.scaleY = scaleY;}

    /**
     * Sets both horizontal and vertical scales for the glyph.
     *
     * @param scaleX the value to set as the horizontal scale.
     * @param scaleY the value to set as the vertical scale.
     */
    public void setScale(float scaleX, float scaleY) {
        this.scaleX = scaleX;
        this.scaleY = scaleY;
    }

    /**
     * Sets the visibility of the glyph.
     *
     * @param visible {@code true} to make the glyph visible, {@code false} to hide it.
     */
    public void setVisible(boolean visible) {this.visible = visible;}

    /**
     * Hides the glyph by setting its visibility to {@code false}.
     */
    public void hide() {this.visible = false;}

    /**
     * Shows the glyph by setting its visibility to {@code true}.
     */
    public void show() {this.visible = true;}

    /**
     * Retrieves the current color of the glyph.
     * <p>
     * The color returned is an immutable reference to the {@link Color} object.
     *
     * @return the color of the glyph.
     */
    public Color getColor() {
        return color;
    }

    /**
     * Clears the custom color of the glyph, resetting it to the default state.
     * Sets {@code hasColor} to {@code false}.
     */
    public void clearColor() {this.hasColor = false;}

    /**
     * Sets a custom color for the glyph using individual RGBA components.
     *
     * @param r the red component (0.0 - 1.0).
     * @param g the green component (0.0 - 1.0).
     * @param b the blue component (0.0 - 1.0).
     * @param a the alpha (opacity) component (0.0 - 1.0).
     */
    public void setColor(float r, float g, float b, float a) {
        this.hasColor = true;
        this.color.set(r, g, b, a);
    }

    /**
     * Sets a custom color for the glyph by copying the values from another {@link Color} instance.
     * If the provided color is {@code null}, the custom color is cleared.
     *
     * @param color the {@link Color} instance to copy values from, or {@code null} to clear the color.
     */
    public void setColor(Color color) {
        if (color == null) {
            clearColor();
            return;
        }
        setColor(color.r(), color.g(), color.b(), color.a());
    }

    /**
     * Retrieves the red component of the glyph's color.
     *
     * @return the red component of the color.
     */
    public float r() {return color.r();}

    /**
     * Retrieves the green component of the glyph's color.
     *
     * @return the green component of the color.
     */
    public float g() {return color.g();}

    /**
     * Retrieves the blue component of the glyph's color.
     *
     * @return the blue component of the color.
     */
    public float b() {return color.b();}

    /**
     * Retrieves the alpha (opacity) component of the glyph's color.
     *
     * @return the alpha component of the color.
     */
    public float a() {return color.a();}

    /**
     * Resets all the styling attributes of the glyph to their default values.
     * <p>
     * This includes resetting offsets, scales, visibility, and color state.
     * The color remains white with full opacity, but any custom color is cleared.
     */
    public void reset() {
        offsetX = 0f;
        offsetY = 0f;
        scaleX = 1f;
        scaleY = 1f;
        visible = true;
        hasColor = false;
    }
}