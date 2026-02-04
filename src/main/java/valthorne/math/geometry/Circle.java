package valthorne.math.geometry;

import valthorne.math.Vector2f;

/**
 * Represents a geometric circle that extends the {@code Shape} class.
 * The circle is defined by its center coordinates, radius, and a number of segments
 * which control the resolution of its boundary representation.
 *
 * @author Albert Beaupre
 * @since January 31st, 2026
 */
public class Circle extends Shape {

    private float x;
    private float y;
    private float radius;

    /**
     * Number of points used to approximate the circle boundary.
     * Higher values = smoother circle and more accurate overlaps/contains, but more cost.
     */
    private int segments;

    /**
     * Creates a circle.
     *
     * @param x        center x
     * @param y        center y
     * @param radius   radius (must be >= 0)
     * @param segments number of perimeter points (minimum 3)
     */
    public Circle(float x, float y, float radius, int segments) {
        this.x = x;
        this.y = y;
        this.radius = Math.max(0f, radius);
        this.segments = Math.max(3, segments);
    }

    /**
     * Creates a circle with a default perimeter resolution.
     *
     * @param x      center x
     * @param y      center y
     * @param radius radius (must be >= 0)
     */
    public Circle(float x, float y, float radius) {
        this(x, y, radius, 32);
    }

    public float getX() {
        return x;
    }

    public float getY() {
        return y;
    }

    public float getRadius() {
        return radius;
    }

    public int getSegments() {
        return segments;
    }

    public void setX(float x) {
        this.x = x;
    }

    public void setY(float y) {
        this.y = y;
    }

    public void setRadius(float radius) {
        this.radius = Math.max(0f, radius);
    }

    public void setSegments(int segments) {
        this.segments = Math.max(3, segments);
    }

    public Vector2f getCenter() {
        return new Vector2f(x, y);
    }

    public void move(Vector2f offset) {
        x += offset.getX();
        y += offset.getY();
    }

    @Override
    public Vector2f[] points() {
        Vector2f[] pts = new Vector2f[segments];

        double step = (Math.PI * 2.0) / segments;

        for (int i = 0; i < segments; i++) {
            double a = i * step;
            pts[i] = new Vector2f((float) (x + Math.cos(a) * radius), (float) (y + Math.sin(a) * radius));
        }

        return pts;
    }
}
