package valthorne.math.geometry;

import valthorne.graphics.Color;
import org.joml.Vector2f;

/**
 * The Shape class serves as an abstract base class for defining 2D geometric shapes.
 * It provides core functionality such as color and border management, rendering methods,
 * and movement capabilities, as well as a contract for subclasses to implement behavior specific to each shape type.
 * Rotation is counterclockwise in degrees around the unrotated boundary's bounding
 * center. Defining coordinates and {@link #points()} remain unrotated; rendering
 * and posed bounds use {@link #getRotatedPoints()}. Its borrowed scratch vectors
 * observe external boundary edits and allocate only when perimeter capacity changes.
 *
 * @author Albert Beaupre
 * @since January 31st, 2026
 */
public abstract class Shape implements Area {

    private Border border; // Optional border style retained by this shape.
    private Color color; // Fill color retained by this shape.
    private float rotation; // Counterclockwise orientation in degrees around the boundary center.
    private float rotationCosine = 1; // Cached cosine refreshed only when orientation changes.
    private float rotationSine; // Cached sine refreshed only when orientation changes.
    private Vector2f[] rotatedPoints; // Lazily materialized posed boundary; null until rotation is used.

    /**
     * Constructs a new Shape instance with a default color of white.
     * The default color is represented as an RGBA value of (1f, 1f, 1f, 1f).
     */
    public Shape() {
        this.color = new Color(1f, 1f, 1f, 1f);
    }

    /**
     * Constructs a new Shape instance with a specified color.
     * If the provided color is null, the default color is set to white,
     * represented as an RGBA value of (1f, 1f, 1f, 1f).
     *
     * @param color the color of the shape. If null, the default color white is assigned.
     */
    public Shape(Color color) {
        this.color = (color == null) ? new Color(1f, 1f, 1f, 1f) : color;
    }

    /**
     * Moves the shape by a specified offset in 2D space.
     * The method adjusts the position of the shape based on the given offset vector.
     * This operation modifies the shape's position without altering its size, rotation, or other properties.
     *
     * @param offset the 2D vector specifying the movement in the x and y directions.
     *               It represents the amount by which the shape's position should be shifted.
     */
    public abstract void move(Vector2f offset);

    /**
     * Returns this shape's counterclockwise orientation.
     *
     * @return rotation in degrees
     */
    public float getRotation() {return rotation;}

    /**
     * Changes orientation without rewriting defining coordinates or perimeter vectors.
     * Call on the scene thread; external boundary edits remain observable afterward.
     *
     * @param degrees finite counterclockwise angle
     * @throws IllegalArgumentException when the angle is nonfinite
     */
    public void setRotation(float degrees) {
        if (!Float.isFinite(degrees)) throw new IllegalArgumentException("Shape rotation must be finite");
        if (rotation == degrees) return;
        rotation = degrees;
        double radians = Math.toRadians(degrees);
        rotationCosine = (float) Math.cos(radians);
        rotationSine = (float) Math.sin(radians);
    }

    /**
     * Borrows the current world boundary after rotation around its bounding center.
     * Zero rotation borrows the original perimeter. Otherwise scratch vectors are
     * refreshed in place; callers must copy values they retain across another call.
     * Null vertices are preserved and finite coordinate validation remains with the
     * consuming geometry system. Never mutate these posed vectors to edit the shape.
     *
     * @return current posed perimeter, with storage owned by this shape
     */
    public Vector2f[] getRotatedPoints() {
        Vector2f[] source = points();
        if (rotation == 0 || source == null) return source;
        if (rotatedPoints == null || rotatedPoints.length != source.length) rotatedPoints = new Vector2f[source.length];
        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        for (Vector2f point : source) {
            if (point == null) continue;
            minX = Math.min(minX, point.x);
            minY = Math.min(minY, point.y);
            maxX = Math.max(maxX, point.x);
            maxY = Math.max(maxY, point.y);
        }
        float centerX = (minX + maxX) * .5f;
        float centerY = (minY + maxY) * .5f;
        for (int index = 0; index < source.length; index++) {
            Vector2f point = source[index];
            if (point == null) {
                rotatedPoints[index] = null;
                continue;
            }
            Vector2f output = rotatedPoints[index];
            if (output == null) rotatedPoints[index] = output = new Vector2f();
            float x = point.x - centerX;
            float y = point.y - centerY;
            output.set(centerX + x * rotationCosine - y * rotationSine, centerY + x * rotationSine + y * rotationCosine);
        }
        return rotatedPoints;
    }


    /**
     * Retrieves the current color assigned to the shape.
     *
     * @return the color of the shape, which represents its fill color.
     */
    public Color getColor() {
        return color;
    }

    /**
     * Sets the color of the shape. If the provided color is null,
     * the color is set to the default value of white, represented
     * as an RGBA value of (1f, 1f, 1f, 1f).
     *
     * @param color the new color to assign to the shape. If null,
     *              the color is set to white.
     */
    public void setColor(Color color) {
        this.color = (color == null) ? new Color(1f, 1f, 1f, 1f) : color;
    }

    /**
     * Retrieves the border of the shape.
     *
     * @return the border assigned to the shape. If no border has been set,
     * the method may return null or a default border, depending on the
     * implementation.
     */
    public Border getBorder() {
        return border;
    }

    /**
     * Sets the border of the shape. The border specifies the appearance of the shape's outline,
     * including its color and thickness. If {@code null} is provided, the shape will have no border.
     *
     * @param border the border to assign to the shape, which defines its outline's color and thickness.
     *               If {@code null}, the border is removed.
     */
    public void setBorder(Border border) {
        this.border = border;
    }

    /**
     * Determines whether the shape has a defined border.
     * A shape is considered to have a border if the border is not null,
     * its thickness is greater than 0, and its color is defined.
     *
     * @return true if the shape has a border, false otherwise
     */
    public boolean hasBorder() {
        return border != null && border.getThickness() > 0f && border.getColor() != null;
    }

}
