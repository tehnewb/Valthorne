package valthorne.math.geometry;

import valthorne.graphics.Color;

/**
 * Represents a border with a specified color and thickness.
 * This class allows customization of a border's appearance
 * by providing methods to get and set its color and thickness.
 *
 * @author Albert Beaupre
 * @since January 31st, 2026
 */
public class Border {

    private Color color; // Borrowed border tint, possibly null.
    private float thickness; // Unvalidated border width interpreted by the consuming renderer.

    /**
     * Creates an opaque white border with thickness one, allocating an independent
     * mutable color.
     */
    public Border() {
        this(new Color(1f, 1f, 1f, 1f), 1f);
    }

    /**
     * Stores a borrowed color and thickness without validation. Individual renderers
     * decide how null colors and nonpositive widths affect drawing.
     *
     * @param color borrowed border tint, possibly null
     * @param thickness requested border width
     */
    public Border(Color color, float thickness) {
        this.color = color;
        this.thickness = thickness;
    }

    /**
     * Returns the borrowed mutable tint used for border drawing.
     *
     * @return current color, possibly null
     */
    public Color getColor() {
        return color;
    }

    /**
     * Replaces the tint reference without copying or disposing either color.
     *
     * @param color new borrowed tint, possibly null
     */
    public void setColor(Color color) {
        this.color = color;
    }

    /**
     * Returns the stored border width. Units and supported ranges depend on the
     * renderer using this descriptor.
     *
     * @return requested thickness
     */
    public float getThickness() {
        return thickness;
    }

    /**
     * Stores border thickness without clamping or finite-value validation.
     *
     * @param thickness new requested width
     */
    public void setThickness(float thickness) {
        this.thickness = thickness;
    }
}
