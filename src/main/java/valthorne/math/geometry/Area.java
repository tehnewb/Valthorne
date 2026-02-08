package valthorne.math.geometry;

import valthorne.math.Vector2f;

/**
 * Represents a geometric area defined by a sequence of points that form its boundary.
 * Provides geometric operations such as point containment and area intersection.
 *
 * @author Albert Beaupre
 * @since January 31st, 2026
 */
public interface Area {

    /**
     * Returns the points that define the shape or boundary of this area.
     *
     * @return An array of Vector2f objects representing the vertices of the area in sequence.
     */
    Vector2f[] points();

    /**
     * Determines if a given 2D point lies within the boundary defined by this area.
     * The method uses a ray-casting algorithm to test for point inclusion, with defensive
     * null checks on the vertices to ensure robustness.
     *
     * @param point the 2D point to check for containment within the area
     * @return true if the point lies within the area boundary; false otherwise
     */
    default boolean contains(Vector2f point) {
        Vector2f[] pts = points();
        int count = (pts == null) ? 0 : pts.length;

        if (count < 3) return false;

        boolean inside = false;

        for (int i = 0, j = count - 1; i < count; j = i++) {
            Vector2f pi = pts[i];
            Vector2f pj = pts[j];

            if (pi == null || pj == null) continue;

            boolean intersect = ((pi.getY() > point.getY()) != (pj.getY() > point.getY())) && (point.getX() < (pj.getX() - pi.getX()) * (point.getY() - pi.getY()) / (pj.getY() - pi.getY()) + pi.getX());

            if (intersect) inside = !inside;
        }

        return inside;
    }

    /**
     * Determines if this area overlaps with the given area.
     * Two areas are considered to overlap if any of their edges intersect
     * or if one area contains any point from the other area.
     *
     * @param other the other area to check for overlap, can be null
     * @return true if the two areas overlap, false otherwise
     */
    default boolean overlaps(Area other) {
        if (other == null) return false;

        Vector2f[] a = points();
        Vector2f[] b = other.points();

        int aCount = (a == null) ? 0 : a.length;
        int bCount = (b == null) ? 0 : b.length;

        if (aCount < 2 || bCount < 2) return false;

        for (int i = 0; i < aCount; i++) {
            Vector2f a1 = a[i];
            Vector2f a2 = a[(i + 1) % aCount];
            if (a1 == null || a2 == null) continue;

            for (int j = 0; j < bCount; j++) {
                Vector2f b1 = b[j];
                Vector2f b2 = b[(j + 1) % bCount];
                if (b1 == null || b2 == null) continue;

                if (segmentsIntersectInclusive(a1, a2, b1, b2)) {
                    return true;
                }
            }
        }

        Vector2f bAny = firstNonNull(b);
        if (bAny != null && contains(bAny)) return true;

        Vector2f aAny = firstNonNull(a);
        return aAny != null && other.contains(aAny);
    }

    /**
     * Returns the first non-null element from the given array of Vector2f objects.
     * If the array is null or does not contain any non-null elements, the method returns null.
     *
     * @param pts the array of Vector2f objects to search, may be null
     * @return the first non-null Vector2f object in the array, or null if none is found
     */
    private static Vector2f firstNonNull(Vector2f[] pts) {
        if (pts == null) return null;
        for (Vector2f p : pts) {
            if (p != null) return p;
        }
        return null;
    }

    /**
     * Determines if two line segments intersect, including cases where they are collinear and overlap.
     * This method checks the general intersection case as well as specific cases
     * where segments may touch or overlap.
     *
     * @param p1 the starting point of the first line segment
     * @param p2 the ending point of the first line segment
     * @param q1 the starting point of the second line segment
     * @param q2 the ending point of the second line segment
     * @return true if the segments intersect or overlap; false otherwise
     */
    private static boolean segmentsIntersectInclusive(Vector2f p1, Vector2f p2, Vector2f q1, Vector2f q2) {
        int o1 = orientation(p1, p2, q1);
        int o2 = orientation(p1, p2, q2);
        int o3 = orientation(q1, q2, p1);
        int o4 = orientation(q1, q2, p2);

        // General case
        if (o1 != o2 && o3 != o4) return true;

        // Collinear / touching cases
        if (o1 == 0 && onSegment(p1, q1, p2)) return true;
        if (o2 == 0 && onSegment(p1, q2, p2)) return true;
        if (o3 == 0 && onSegment(q1, p1, q2)) return true;
        if (o4 == 0 && onSegment(q1, p2, q2)) return true;

        return false;
    }

    /**
     * Determines the orientation of the triplet of points (a, b, c).
     * The orientation can be:
     * - 0 if the points are collinear.
     * - 1 if the orientation is clockwise.
     * - 2 if the orientation is counter-clockwise.
     *
     * @param a the first point in the triplet
     * @param b the second point in the triplet
     * @param c the third point in the triplet
     * @return 0 if the points are collinear, 1 if they are oriented clockwise, or 2 if counter-clockwise
     */
    private static int orientation(Vector2f a, Vector2f b, Vector2f c) {
        float v = (b.getY() - a.getY()) * (c.getX() - b.getX()) - (b.getX() - a.getX()) * (c.getY() - b.getY());

        final float eps = 1e-6f;
        if (Math.abs(v) <= eps) return 0;
        return (v > 0f) ? 1 : 2;
    }

    /**
     * Determines if point b lies on the line segment defined by points a and c.
     * A point is considered to be on the segment if it lies between the endpoints a and c,
     * both horizontally and vertically.
     *
     * @param a the first endpoint of the line segment
     * @param b the point to check for presence on the segment
     * @param c the second endpoint of the line segment
     * @return true if point b lies on the line segment defined by a and c, false otherwise
     */
    private static boolean onSegment(Vector2f a, Vector2f b, Vector2f c) {
        return b.getX() <= Math.max(a.getX(), c.getX()) && b.getX() >= Math.min(a.getX(), c.getX()) && b.getY() <= Math.max(a.getY(), c.getY()) && b.getY() >= Math.min(a.getY(), c.getY());
    }
}
