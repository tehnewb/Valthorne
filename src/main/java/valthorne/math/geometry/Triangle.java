package valthorne.math.geometry;

import org.joml.Vector2f;

/**
 * Mutable 2D triangle with three copied vertex positions. Vertex getters and
 * points expose the same internal vectors, so direct edits affect geometry.
 * The center is recomputed as the arithmetic mean when requested and is returned
 * in reusable scratch storage. No winding, degeneracy, or finiteness validation
 * is performed.
 * @author Albert Beaupre
 * @since January 31st, 2026
 */
public class Triangle extends Shape {

    private final Vector2f a; // Mutable defining vertex A, copied at construction.
    private final Vector2f b; // Mutable defining vertex B, copied at construction.
    private final Vector2f c; // Mutable defining vertex C, copied at construction.

    // reused objects
    private final Vector2f center = new Vector2f(); // Reusable centroid scratch, updated when requested.
    private final Vector2f[] points = new Vector2f[3]; // Live boundary array initially referencing the three defining vertices.

    /**
     * Copies three input vertices and installs them in the reusable boundary array.
     *
     * @param a first nonnull vertex
     * @param b second nonnull vertex
     * @param c third nonnull vertex
     * @throws NullPointerException if a vertex is null
     */
    public Triangle(Vector2f a, Vector2f b, Vector2f c) {
        this.a = new Vector2f(a);
        this.b = new Vector2f(b);
        this.c = new Vector2f(c);

        points[0] = this.a;
        points[1] = this.b;
        points[2] = this.c;
    }

    /**
     * Returns the live A vertex; coordinate mutations immediately affect geometry.
     *
     * @return internal A position
     */
    public Vector2f getA() {
        return a;
    }

    /**
     * Copies coordinates into the existing A vertex without replacing its identity.
     *
     * @param a nonnull source position
     * @throws NullPointerException if the source is null
     */
    public void setA(Vector2f a) {
        this.a.set(a);
    }

    /**
     * Returns the live B vertex; coordinate mutations immediately affect geometry.
     *
     * @return internal B position
     */
    public Vector2f getB() {
        return b;
    }

    /**
     * Copies coordinates into the existing B vertex without replacing its identity.
     *
     * @param b nonnull source position
     * @throws NullPointerException if the source is null
     */
    public void setB(Vector2f b) {
        this.b.set(b);
    }

    /**
     * Returns the live C vertex; coordinate mutations immediately affect geometry.
     *
     * @return internal C position
     */
    public Vector2f getC() {
        return c;
    }

    /**
     * Copies coordinates into the existing C vertex without replacing its identity.
     *
     * @param c nonnull source position
     * @throws NullPointerException if the source is null
     */
    public void setC(Vector2f c) {
        this.c.set(c);
    }

    /**
     * Recomputes the centroid from the current vertices and returns reusable storage.
     * Later calls overwrite it; mutating the result does not move the triangle.
     *
     * @return borrowed centroid vector
     */
    public Vector2f getCenter() {
        center.set((a.x() + b.x() + c.x()) / 3f, (a.y() + b.y() + c.y()) / 3f);
        return center;
    }

    /**
     * Adds an offset to each vertex in order. Use a separate offset vector: aliasing
     * one of the triangle's vertices can change the offset during this operation.
     *
     * @param offset nonnull translation
     * @throws NullPointerException if offset is null
     */
    public void move(Vector2f offset) {
        a.add(offset);
        b.add(offset);
        c.add(offset);
    }

    /**
     * Returns the live three-entry boundary array whose initial entries reference
     * the triangle's internal A/B/C vectors. Replacing array entries does not replace
     * the defining fields; modify their coordinates or use setters instead.
     *
     * @return mutable boundary array
     */
    @Override
    public Vector2f[] points() {
        return points;
    }
}
