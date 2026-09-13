package valthorne.graphics.lighting2d;

/**
 * Closed polygon used to block light in the XY world plane. Supply a simple
 * convex or concave boundary without holes; the final vertex connects back to
 * the first automatically. Local coordinates are copied at construction and
 * remain fixed, while a separate translation positions the polygon in the world.
 *
 * <pre>{@code
 * Occluder2D wall = Occluder2D.rectangle(20, -10, 4, 20)
 *         .setCategory(2);
 * lighting.addOccluder(wall);
 * light.setOcclusionMask(2);
 * }</pre>
 *
 * <p>Construction checks vertex count, finite coordinates and nonzero signed
 * area, but does not establish that edges never intersect. Callers are responsible
 * for supplying a simple boundary. Either winding order is accepted. Coordinates
 * and translation use the same world units as PointLight2D.</p>
 *
 * <p>Translation starts at zero and category defaults to all bits set. Position
 * and category setters advance a revision when values change, allowing lighting
 * updates to reconsider affected shadows. This object owns no rendering resource
 * and is not synchronized; avoid mutation while lighting reads its geometry.</p>
 *
 * @author Albert Beaupre
 */
public class Occluder2D {
    float[] vertices; // Polygon pairs, or independent endpoint pairs for alpha silhouettes.
    int coordinateCount;
    final boolean segments;
    float minX, minY, maxX, maxY;
    float x, y; // World translation added to local vertex coordinates.
    int category = -1; // Occluder category bits tested against each light's mask.
    long revision; // Change counter for translation and category updates.

    /**
     * Copies a polygon boundary and computes its local axis-aligned bounds.
     * At least three XY pairs are required. The absolute shoelace sum, which is
     * twice signed area, must be at least 1e-8; no self-intersection test is made.
     * Subsequent changes to the supplied array do not affect this polygon.
     *
     * @param xy alternating local X and Y coordinates in boundary order
     * @throws NullPointerException     if xy is null
     * @throws IllegalArgumentException if fewer than three pairs or an odd count
     *                                  is supplied, a coordinate is non-finite,
     *                                  or the absolute doubled area is below 1e-8
     */
    public Occluder2D(float... xy) {
        segments = false;
        if (xy.length < 6 || xy.length % 2 != 0)
            throw new IllegalArgumentException("At least three XY vertices are required");
        vertices = xy.clone();
        coordinateCount = xy.length;
        float ax = Float.POSITIVE_INFINITY, ay = ax, bx = Float.NEGATIVE_INFINITY, by = bx;
        double area = 0;
        for (int i = 0; i < xy.length; i += 2) {
            PointLight2D.finite(xy[i]);
            PointLight2D.finite(xy[i + 1]);
            ax = Math.min(ax, xy[i]);
            ay = Math.min(ay, xy[i + 1]);
            bx = Math.max(bx, xy[i]);
            by = Math.max(by, xy[i + 1]);
            int j = (i + 2) % xy.length;
            area += (double) xy[i] * xy[j + 1] - (double) xy[j] * xy[i + 1];
        }
        if (Math.abs(area) < 1e-8) throw new IllegalArgumentException("Occluder must enclose an area");
        minX = ax;
        minY = ay;
        maxX = bx;
        maxY = by;
    }

    Occluder2D() { segments = true; vertices = new float[0]; }
    void synchronize() { }
    int edgeStep() { return segments ? 4 : 2; }
    int edgeEnd(int i) { return segments ? i + 2 : (i + 2) % coordinateCount; }

    /**
     * Creates an axis-aligned rectangle with local corners from (0,0) through
     * (width,height), then translates that local origin to the requested position.
     * Polygon area validation also applies, so extremely small rectangles can be
     * rejected despite positive dimensions.
     *
     * @param x      the finite world X coordinate of the local origin
     * @param y      the finite world Y coordinate of the local origin
     * @param width  the finite positive X extent in world units
     * @param height the finite positive Y extent in world units
     * @return a new rectangular occluder with all category bits enabled
     * @throws IllegalArgumentException if a coordinate or dimension is non-finite,
     *                                  a dimension is not positive, or area is too small
     */
    public static Occluder2D rectangle(float x, float y, float width, float height) {
        PointLight2D.positive(width);
        PointLight2D.positive(height);
        return new Occluder2D(0, 0, width, 0, width, height, 0, height).setPosition(x, y);
    }

    /**
     * Changes world translation without rewriting local vertices or bounds.
     * Both coordinates are validated before assignment, and a changed coordinate
     * advances revision once; an equal position leaves revision unchanged.
     *
     * @param x the finite world X translation
     * @param y the finite world Y translation
     * @return this occluder for chaining
     * @throws IllegalArgumentException if either coordinate is non-finite
     */
    public Occluder2D setPosition(float x, float y) {
        PointLight2D.finite(x);
        PointLight2D.finite(y);
        if (this.x != x || this.y != y) {
            this.x = x;
            this.y = y;
            revision++;
        }
        return this;
    }

    /**
     * Assigns category bits used for light-mask filtering. Any shared bit allows
     * occlusion; zero matches no light mask. A different mask advances revision,
     * while reassigning the current mask is a no-op.
     *
     * @param bits the category bitmask, with all integer values permitted
     * @return this occluder for chaining
     */
    public Occluder2D setCategory(int bits) {
        if (category != bits) {
            category = bits;
            revision++;
        }
        return this;
    }

    /**
     * Returns the X translation applied to every local vertex, not the world
     * minimum X unless the polygon's local minimum is zero.
     *
     * @return the world X translation
     */
    public float getX() {return x;}

    /**
     * Returns the Y translation applied to every local vertex, independently of
     * the polygon's local minimum and maximum coordinates.
     *
     * @return the world Y translation
     */
    public float getY() {return y;}

    /**
     * Performs a broad-phase bounds test against the square enclosing a light's
     * influence circle. Touching bounds count as overlap. This does not test the
     * exact polygon, circle, cone, enabled flag or category-mask intersection.
     *
     * @param light the light whose XY center and radius define the query square
     * @return whether the translated polygon bounds overlap the query square
     * @throws NullPointerException if light is null
     */
    boolean overlaps(PointLight2D light) {return x + maxX >= light.x - light.radius && x + minX <= light.x + light.radius && y + maxY >= light.y - light.radius && y + minY <= light.y + light.radius;}

    /**
     * Tests a world point using an even-odd horizontal-ray crossing calculation
     * after subtracting translation. The lighting system uses this to detect a
     * light inside an occluder. Boundary points have no separate inclusive-edge
     * test and follow the calculation's strict comparisons; do not rely on a
     * uniform inside result for points exactly on an edge or vertex.
     *
     * @param px the world X coordinate, expected to be finite
     * @param py the world Y coordinate, expected to be finite
     * @return whether the ray-crossing parity classifies the point as inside
     */
    boolean contains(float px, float py) {
        px -= x;
        py -= y;
        boolean inside = false;
        for (int i = 0; i < coordinateCount; i += edgeStep()) {
            int j = edgeEnd(i);
            float xi = vertices[i], yi = vertices[i + 1], xj = vertices[j], yj = vertices[j + 1];
            if ((yi > py) != (yj > py) && px < (xj - xi) * (py - yi) / (yj - yi) + xi) inside = !inside;
        }
        return inside;
    }
}
