package valthorne.math.geometry;

import valthorne.math.Vector2f;

/**
 * Represents a triangle defined by three vertices in 2D space.
 * The Triangle class extends the Shape class and provides methods
 * to manipulate and retrieve information about the triangle's vertices.
 *
 * @author Albert Beaupre
 * @since January 31st, 2026
 */
public class Triangle extends Shape {

    private Vector2f a;
    private Vector2f b;
    private Vector2f c;

    /**
     * Constructs a Triangle with three vertices specified by the given points.
     *
     * @param a the first vertex of the triangle
     * @param b the second vertex of the triangle
     * @param c the third vertex of the triangle
     */
    public Triangle(Vector2f a, Vector2f b, Vector2f c) {
        this.a = a;
        this.b = b;
        this.c = c;
    }

    /**
     * Retrieves the first vertex of the triangle.
     *
     * @return the first vertex of the triangle as a Vector2f
     */
    public Vector2f getA() {
        return a;
    }

    /**
     * Retrieves the second vertex of the triangle.
     *
     * @return the second vertex of the triangle as a Vector2f
     */
    public Vector2f getB() {
        return b;
    }

    /**
     * Retrieves the third vertex of the triangle.
     *
     * @return the third vertex of the triangle as a Vector2f
     */
    public Vector2f getC() {
        return c;
    }

    /**
     * Sets the first vertex of the triangle.
     *
     * @param a the new value for the first vertex of the triangle
     */
    public void setA(Vector2f a) {
        this.a = a;
    }

    public void setB(Vector2f b) {
        this.b = b;
    }

    public void setC(Vector2f c) {
        this.c = c;
    }

    /**
     * Calculates and retrieves the center point of the triangle.
     * The center is determined by averaging the x and y coordinates
     * of the three vertices of the triangle.
     *
     * @return the center point of the triangle as a Vector2f
     */
    public Vector2f getCenter() {
        return new Vector2f((a.getX() + b.getX() + c.getX()) / 3f, (a.getY() + b.getY() + c.getY()) / 3f);
    }

    /**
     * Moves the triangle by applying the given offset to each of its vertices.
     *
     * @param offset the vector by which all vertices of the triangle will be shifted
     */
    public void move(Vector2f offset) {
        a = new Vector2f(a.getX() + offset.getX(), a.getY() + offset.getY());
        b = new Vector2f(b.getX() + offset.getX(), b.getY() + offset.getY());
        c = new Vector2f(c.getX() + offset.getX(), c.getY() + offset.getY());
    }

    @Override
    public Vector2f[] points() {
        return new Vector2f[]{a, b, c};
    }
}
