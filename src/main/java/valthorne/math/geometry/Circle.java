package valthorne.math.geometry;

import org.joml.Vector2f;

/**
 * A polygonal circle defined by the bottom-left corner of its bounding square,
 * a nonnegative radius, and at least three perimeter samples. The center is
 * (x + radius, y + radius); changing radius therefore moves the center while
 * preserving the bottom-left anchor. Perimeter points run counterclockwise from
 * the positive-X direction.
 * <p>
 * Center and point getters expose mutable cached storage. Setters rebuild those
 * values from the defining fields, overwriting external edits. Segment changes
 * replace the point array, so callers needing stable geometry must copy it.
 * </p>
 * @author Albert Beaupre
 * @since January 31st, 2026
 */
public class Circle extends Shape {

    private final Vector2f center; // Reusable center derived from the bottom-left anchor plus radius.
    private Vector2f[] points; // Mutable perimeter samples, replaced when segment count changes.
    private float x; // bottom-left x
    private float y; // bottom-left y
    private float radius; // Stored radius, clamped against zero by setters.
    private int segments; // Perimeter sample count, at least three.

    /**
     * Creates a sampled circle, clamping radius to at least zero and segments to
     * at least three. Coordinates and radius are not checked for finiteness.
     *
     * @param x bounding-square left coordinate
     * @param y bounding-square bottom coordinate
     * @param radius radius, with negative values clamped to zero
     * @param segments perimeter count, clamped to at least three
     */
    public Circle(float x, float y, float radius, int segments) {
        this.x = x;
        this.y = y;
        this.radius = Math.max(0f, radius);
        this.segments = Math.max(3, segments);
        this.center = new Vector2f();
        this.points = new Vector2f[this.segments];

        for (int i = 0; i < this.segments; i++) {
            this.points[i] = new Vector2f();
        }

        updatePoints();
    }

    /**
     * Creates a circle with 32 perimeter samples.
     *
     * @param x bounding-square left coordinate
     * @param y bounding-square bottom coordinate
     * @param radius radius, with negative values clamped to zero
     */
    public Circle(float x, float y, float radius) {
        this(x, y, radius, 32);
    }

    /**
     * Returns the left edge of the circle's bounding square.
     *
     * @return bottom-left anchor X
     */
    public float getX() {
        return x;
    }

    /**
     * Moves the bounding-square left edge and rebuilds center/perimeter storage.
     *
     * @param x new anchor X
     */
    public void setX(float x) {
        this.x = x;
        updatePoints();
    }

    /**
     * Returns the bottom edge of the circle's bounding square.
     *
     * @return bottom-left anchor Y
     */
    public float getY() {
        return y;
    }

    /**
     * Moves the bounding-square bottom edge and rebuilds center/perimeter storage.
     *
     * @param y new anchor Y
     */
    public void setY(float y) {
        this.y = y;
        updatePoints();
    }

    /**
     * Returns the stored radius in the same coordinate units as the anchor.
     *
     * @return current radius
     */
    public float getRadius() {
        return radius;
    }

    /**
     * Clamps negative radius to zero and rebuilds geometry while retaining the
     * bottom-left anchor. NaN is not rejected.
     *
     * @param radius new radius
     */
    public void setRadius(float radius) {
        this.radius = Math.max(0f, radius);
        updatePoints();
    }

    /**
     * Returns the number of equally spaced perimeter vertices.
     *
     * @return sample count, at least three
     */
    public int getSegments() {
        return segments;
    }

    /**
     * Clamps the sample count to at least three and replaces perimeter storage when
     * the effective count changes. Retained old arrays cease to reflect this circle.
     *
     * @param segments requested perimeter count
     */
    public void setSegments(int segments) {
        int newSegments = Math.max(3, segments);
        if (this.segments == newSegments) {
            return;
        }

        this.segments = newSegments;
        this.points = new Vector2f[this.segments];

        for (int i = 0; i < this.segments; i++) {
            this.points[i] = new Vector2f();
        }

        updatePoints();
    }

    /**
     * Returns the live cached center. Mutating it alone does not update the anchor
     * or perimeter, and the next geometry rebuild overwrites the edit.
     *
     * @return mutable cached center
     */
    public Vector2f getCenter() {
        return center;
    }

    /**
     * Translates the bottom-left anchor by an offset and rebuilds cached geometry.
     *
     * @param offset nonnull translation vector
     * @throws NullPointerException if offset is null
     */
    public void move(Vector2f offset) {
        x += offset.x();
        y += offset.y();
        updatePoints();
    }

    /**
     * Recomputes center and all equally spaced perimeter points from anchor, radius,
     * and sample count, reusing the current vectors.
     */
    private void updatePoints() {
        double step = (Math.PI * 2.0) / segments;

        float centerX = x + radius;
        float centerY = y + radius;

        center.set(centerX, centerY);

        for (int i = 0; i < segments; i++) {
            double angle = i * step;
            float px = (float) (centerX + Math.cos(angle) * radius);
            float py = (float) (centerY + Math.sin(angle) * radius);
            points[i].set(px, py);
        }
    }

    /**
     * Returns the live perimeter array without repeating the first point at the end.
     * Coordinate setters overwrite its vectors, and segment changes replace the array.
     *
     * @return mutable sampled boundary
     */
    @Override
    public Vector2f[] points() {
        return points;
    }
}
