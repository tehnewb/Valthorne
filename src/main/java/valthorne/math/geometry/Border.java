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

    private Color color;
    private float thickness;

    public Border() {
        this(new Color(1f, 1f, 1f, 1f), 1f);
    }

    public Border(Color color, float thickness) {
        this.color = color;
        this.thickness = thickness;
    }

    public Color getColor() {
        return color;
    }

    public void setColor(Color color) {
        this.color = color;
    }

    public float getThickness() {
        return thickness;
    }

    public void setThickness(float thickness) {
        this.thickness = thickness;
    }
}
