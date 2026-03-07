package valthorne.math.geometry;

import valthorne.math.Vector2f;

/**
 * Represents a triangle defined by three vertices in 2D space.
 *
 * @author Albert Beaupre
 * @since January 31st, 2026
 */
public class Triangle extends Shape {

    private final Vector2f a;
    private final Vector2f b;
    private final Vector2f c;

    // reused objects
    private final Vector2f center = new Vector2f();
    private final Vector2f[] points = new Vector2f[3];

    public Triangle(Vector2f a, Vector2f b, Vector2f c) {
        this.a = new Vector2f(a);
        this.b = new Vector2f(b);
        this.c = new Vector2f(c);

        points[0] = this.a;
        points[1] = this.b;
        points[2] = this.c;
    }

    public Vector2f getA() {
        return a;
    }

    public Vector2f getB() {
        return b;
    }

    public Vector2f getC() {
        return c;
    }

    public void setA(Vector2f a) {
        this.a.set(a);
    }

    public void setB(Vector2f b) {
        this.b.set(b);
    }

    public void setC(Vector2f c) {
        this.c.set(c);
    }

    /**
     * Returns a reused center vector.
     */
    public Vector2f getCenter() {
        center.set((a.getX() + b.getX() + c.getX()) / 3f, (a.getY() + b.getY() + c.getY()) / 3f);
        return center;
    }

    /**
     * Moves the triangle without allocating new vectors.
     */
    public void move(Vector2f offset) {
        a.add(offset);
        b.add(offset);
        c.add(offset);
    }

    @Override
    public Vector2f[] points() {
        return points;
    }
}