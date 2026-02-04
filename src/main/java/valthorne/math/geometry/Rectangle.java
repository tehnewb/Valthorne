package valthorne.math.geometry;

import valthorne.math.Vector2f;

/**
 * Represents a rectangle defined by its top-left corner coordinates and its dimensions.
 * Provides methods to retrieve and manipulate the rectangle's properties,
 * as well as to calculate its center and geometric transformations.
 *
 * @author Albert Beaupre
 * @since January 31st, 2026
 */
public class Rectangle extends Shape {

    private float x, y, width, height;

    /**
     * Constructs a new Rectangle with the specified top-left corner coordinates and dimensions.
     *
     * @param x      the x-coordinate of the top-left corner of the rectangle
     * @param y      the y-coordinate of the top-left corner of the rectangle
     * @param width  the width of the rectangle
     * @param height the height of the rectangle
     */
    public Rectangle(float x, float y, float width, float height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    /**
     * Retrieves the x-coordinate of the top-left corner of the rectangle.
     *
     * @return the x-coordinate of the top-left corner
     */
    public float getX() {
        return x;
    }

    /**
     * Retrieves the y-coordinate of the top-left corner of the rectangle.
     *
     * @return the y-coordinate of the top-left corner
     */
    public float getY() {
        return y;
    }

    /**
     * Retrieves the width of the rectangle.
     *
     * @return the width of the rectangle
     */
    public float getWidth() {
        return width;
    }

    /**
     * Retrieves the height of the rectangle.
     *
     * @return the height of the rectangle
     */
    public float getHeight() {
        return height;
    }

    /**
     * Sets the x-coordinate of the top-left corner of the rectangle.
     *
     * @param x the new x-coordinate of the top-left corner
     */
    public void setX(float x) {
        this.x = x;
    }

    /**
     * Sets the y-coordinate of the top-left corner of the rectangle.
     *
     * @param y the new y-coordinate of the top-left corner
     */
    public void setY(float y) {
        this.y = y;
    }

    /**
     * Sets the width of the rectangle.
     *
     * @param width the new width of the rectangle
     */
    public void setWidth(float width) {
        this.width = width;
    }

    /**
     * Sets the height of the rectangle.
     *
     * @param height the new height of the rectangle
     */
    public void setHeight(float height) {
        this.height = height;
    }

    /**
     * Calculates and retrieves the center point of the rectangle.
     *
     * @return a {@code Vector2f} representing the coordinates of the center point of the rectangle
     */
    public Vector2f getCenter() {
        return new Vector2f(x + width / 2, y + height / 2);
    }

    @Override
    public void move(Vector2f offset) {
        x += offset.getX();
        y += offset.getY();
    }

    @Override
    public Vector2f[] points() {
        return new Vector2f[]{new Vector2f(x, y), new Vector2f(x + width, y), new Vector2f(x + width, y + height), new Vector2f(x, y + height)};
    }
}
