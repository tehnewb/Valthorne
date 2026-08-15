package valthorne.graphics.font.slug;

/**
 * A single quadratic Bezier segment stored in normalized em-space.
 *
 * <p>Straight line segments are represented as degenerate quadratics whose control point is the
 * midpoint between the segment endpoints.</p>
 *
 * @param p1x first endpoint x in em-space
 * @param p1y first endpoint y in em-space
 * @param p2x control point x in em-space
 * @param p2y control point y in em-space
 * @param p3x second endpoint x in em-space
 * @param p3y second endpoint y in em-space
 * @author Albert Beaupre
 * @since July 7th, 2026
 */
public record SlugCurve(float p1x, float p1y, float p2x, float p2y, float p3x, float p3y) {

    /**
     * Returns the smallest x coordinate used by this curve.
     *
     * @return minimum x coordinate
     */
    public float minX() {
        return Math.min(Math.min(p1x, p2x), p3x);
    }

    /**
     * Returns the largest x coordinate used by this curve.
     *
     * @return maximum x coordinate
     */
    public float maxX() {
        return Math.max(Math.max(p1x, p2x), p3x);
    }

    /**
     * Returns the smallest y coordinate used by this curve.
     *
     * @return minimum y coordinate
     */
    public float minY() {
        return Math.min(Math.min(p1y, p2y), p3y);
    }

    /**
     * Returns the largest y coordinate used by this curve.
     *
     * @return maximum y coordinate
     */
    public float maxY() {
        return Math.max(Math.max(p1y, p2y), p3y);
    }
}
