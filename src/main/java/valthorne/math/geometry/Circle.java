package valthorne.math.geometry;

import valthorne.math.Vector2f;

/**
 * Represents a geometric circle that extends the {@code Shape} class.
 * The circle is defined by its bottom-left position, radius, and a number of segments
 * which control the resolution of its boundary representation.
 *
 * @author Albert Beaupre
 * @since January 31st, 2026
 */
public class Circle extends Shape {

    private Vector2f[] points;
    private float x; // bottom-left x
    private float y; // bottom-left y
    private float radius;
    private int segments;

    /**
     * Creates a circle.
     *
     * @param x        bottom-left x
     * @param y        bottom-left y
     * @param radius   radius (must be >= 0)
     * @param segments number of perimeter points (minimum 3)
     */
    public Circle(float x, float y, float radius, int segments) {
        this.x = x;
        this.y = y;
        this.radius = Math.max(0f, radius);
        this.segments = Math.max(3, segments);
        this.points = new Vector2f[segments];
        this.updatePoints();
    }

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
        this.updatePoints();
    }

    public void setY(float y) {
        this.y = y;
        this.updatePoints();
    }

    public void setRadius(float radius) {
        this.radius = Math.max(0f, radius);
        this.updatePoints();
    }

    public void setSegments(int segments) {
        this.segments = Math.max(3, segments);
        this.updatePoints();
    }

    public Vector2f getCenter() {
        return new Vector2f(x + radius, y + radius);
    }

    public void move(Vector2f offset) {
        x += offset.getX();
        y += offset.getY();
        this.updatePoints();
    }

    private void updatePoints() {
        if (this.points.length != this.segments) this.points = new Vector2f[segments];

        double step = (Math.PI * 2.0) / segments;

        float centerX = x + radius;
        float centerY = y + radius;

        for (int i = 0; i < segments; i++) {
            double a = i * step;
            points[i] = new Vector2f((float) (centerX + Math.cos(a) * radius), (float) (centerY + Math.sin(a) * radius));
        }
    }

    @Override
    public Vector2f[] points() {
        return points;
    }
}
