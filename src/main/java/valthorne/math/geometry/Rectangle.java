package valthorne.math.geometry;

import valthorne.math.Vector2f;

/**
 * Represents a rectangle defined by its top-left corner coordinates and its dimensions.
 * Provides methods to retrieve and manipulate the rectangle's properties,
 * as well as to calculate its center and geometric transformations.
 * <p>
 * This implementation performs NO allocations after construction.
 *
 * @author Albert Beaupre
 * @since January 31st, 2026
 */
public class Rectangle extends Shape {

    private float x, y, width, height;
    private final Vector2f center = new Vector2f();
    private final Vector2f[] points = {new Vector2f(), new Vector2f(), new Vector2f(), new Vector2f()};

    /**
     * Constructs a new {@code Rectangle} with uninitialized position and dimensions.
     * By default, all fields such as x, y, width, and height will be set to their
     * default values (e.g., zero for numerical fields).
     */
    public Rectangle() {

    }

    /**
     * Constructs a new {@code Rectangle} with the specified position and dimensions.
     *
     * @param x      the x-coordinate of the rectangle's top-left corner
     * @param y      the y-coordinate of the rectangle's top-left corner
     * @param width  the width of the rectangle
     * @param height the height of the rectangle
     */
    public Rectangle(float x, float y, float width, float height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        updatePoints();
    }

    /**
     * Retrieves the x-coordinate of the rectangle's top-left corner.
     *
     * @return the x-coordinate of the rectangle
     */
    public float getX() {
        return x;
    }

    /**
     * Retrieves the y-coordinate of the rectangle's top-left corner.
     *
     * @return the y-coordinate of the rectangle
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
     * Updates the x-coordinate of the rectangle's top-left corner
     * and recalculates its corner points.
     *
     * @param x the new x-coordinate of the rectangle
     */
    public void setX(float x) {
        this.x = x;
        updatePoints();
    }

    /**
     * Updates the y-coordinate of the rectangle's top-left corner
     * and recalculates its corner points.
     *
     * @param y the new y-coordinate of the rectangle
     */
    public void setY(float y) {
        this.y = y;
        updatePoints();
    }

    /**
     * Sets the position of the rectangle by updating its x and y coordinates.
     * Recalculates the rectangle's corner points after the position is updated.
     *
     * @param x the new x-coordinate of the rectangle's top-left corner
     * @param y the new y-coordinate of the rectangle's top-left corner
     */
    public void setPosition(float x, float y) {
        this.x = x;
        this.y = y;
        updatePoints();
    }

    /**
     * Updates the width of the rectangle and recalculates its corner points.
     *
     * @param width the new width of the rectangle
     */
    public void setWidth(float width) {
        this.width = width;
        updatePoints();
    }

    /**
     * Updates the height of the rectangle and recalculates its corner points.
     *
     * @param height the new height of the rectangle
     */
    public void setHeight(float height) {
        this.height = height;
        updatePoints();
    }

    /**
     * Updates the dimensions of the rectangle by setting its width and height.
     * Recalculates the rectangle's corner points after the dimensions are updated.
     *
     * @param width  the new width of the rectangle
     * @param height the new height of the rectangle
     */
    public void setSize(float width, float height) {
        this.width = width;
        this.height = height;
        updatePoints();
    }

    /**
     * Retrieves the center point of the rectangle without allocating.
     *
     * @return a reused {@code Vector2f} representing the rectangle center
     */
    public Vector2f getCenter() {
        center.set(x + width * 0.5f, y + height * 0.5f);
        return center;
    }

    @Override
    public void move(Vector2f offset) {
        x += offset.getX();
        y += offset.getY();
        updatePoints();
    }

    @Override
    public Vector2f[] points() {
        return points;
    }

    private void updatePoints() {
        points[0].set(x, y);
        points[1].set(x + width, y);
        points[2].set(x + width, y + height);
        points[3].set(x, y + height);
    }
}
