package valthorne.math.geometry;

import valthorne.math.Vector2f;

/**
 * Represents a polygon defined by a sequence of vertices in 2D space.
 * A polygon is required to have a minimum of three vertices.
 * Implements the {@link Area} interface to provide operations related to
 * geometric areas such as retrieving points that define the boundary.
 *
 * @author Albert Beaupre
 * @since January 31st, 2026
 */
public class Polygon extends Shape {

    private Vector2f[] points;

    /**
     * Creates a polygon from the given vertices.
     *
     * @param points polygon vertices (must contain at least 3 points)
     */
    public Polygon(Vector2f... points) {
        setPoints(points);
    }

    /**
     * Sets the vertices of the polygon. A valid polygon must have at least three points.
     * If the provided points are null or less than three, an {@code IllegalArgumentException} is thrown.
     *
     * @param points an array of {@code Vector2f} objects representing the polygon's vertices
     * @throws IllegalArgumentException if the provided points array is null or contains fewer than three points
     */
    public void setPoints(Vector2f... points) {
        if (points == null || points.length < 3)
            throw new IllegalArgumentException("Polygon requires at least 3 points.");
        this.points = points;
    }

    @Override
    public void move(Vector2f offset) {
        for (int i = 0; i < points.length; i++) {
            Vector2f p = points[i];
            if (p == null) continue;
            points[i] = new Vector2f(p.getX() + offset.getX(), p.getY() + offset.getY());
        }
    }

    @Override
    public Vector2f[] points() {
        return points;
    }
}
