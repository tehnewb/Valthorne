package valthorne.math.geometry;

import org.joml.Vector2f;

/**
 * Mutable axis-aligned rounded rectangle represented by reusable boundary points.
 * Positive-radius corners use segmentsPerCorner plus one samples each, including
 * arc endpoints; near-zero radius uses four square corners. Position and size
 * changes rebuild coordinates immediately, retaining vector objects when the
 * required point count is unchanged. The points accessor exposes this live storage.
 *
 * <p>Dimensions are stored unchecked; use finite nonnegative sizes. Radius is
 * clamped to half the smaller nonnegative dimension. Construction first raises
 * requested radius to at least one, unlike setRadius, which accepts zero. Thus
 * the four-argument constructor can produce rounded corners despite passing zero.</p>
 *
 * @author Albert Beaupre
 */
public class RoundedRectangle extends Shape {

    /**
     * Radius threshold below which the four-point square representation is used.
     */
    private static final float EPSILON = 0.0001f;

    private float x, y, width, height; // Stored origin XY and unchecked rectangle dimensions in geometry units.
    private float radius; // Effective radius clamped against nonnegative dimensions.
    private int segmentsPerCorner; // Line-segment count per quarter arc, at least one.

    private Vector2f[] points; // Live reusable boundary vectors, replaced when required count changes.

    /**
     * Stores geometry, clamps the requested radius first to at least one and then
     * to the size-dependent maximum, and constructs boundary points. Sampling count
     * is clamped to at least one segment per corner.
     *
     * @param x rectangle origin X
     * @param y rectangle origin Y
     * @param width finite nonnegative width expected by the caller
     * @param height finite nonnegative height expected by the caller
     * @param radius requested corner radius in geometry units
     * @param segmentsPerCorner requested line-segment count for each quarter arc
     */
    public RoundedRectangle(float x, float y, float width, float height, float radius, int segmentsPerCorner) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.radius = Math.max(1f, radius);
        this.segmentsPerCorner = Math.max(1, segmentsPerCorner);
        ensureValidRadius();
        updatePoints();
    }

    /**
     * Constructs with four segments per corner and an initial requested radius of
     * zero. The delegated constructor raises that radius to one before size clamping;
     * call setRadius(0) afterward when square corners are required.
     *
     * @param x rectangle origin X
     * @param y rectangle origin Y
     * @param width rectangle width
     * @param height rectangle height
     */
    public RoundedRectangle(float x, float y, float width, float height) {
        this(x, y, width, height, 0f, 4);
    }

    /**
     * Writes evenly spaced angular samples into preallocated destination vectors,
     * including both arc endpoints. Angles use radians from positive X, and the
     * caller supplies at least two samples and sufficient destination capacity.
     *
     * @param dst preallocated mutable point storage
     * @param idx first destination index
     * @param cx arc center X
     * @param cy arc center Y
     * @param r arc radius
     * @param a0 starting angle in radians
     * @param a1 ending angle in radians
     * @param pointCount number of samples, at least two
     * @return first unwritten destination index
     */
    private static int writeArc(Vector2f[] dst, int idx, float cx, float cy, float r, float a0, float a1, int pointCount) {
        float da = (a1 - a0) / (pointCount - 1);

        for (int i = 0; i < pointCount; i++) {
            float a = a0 + da * i;
            float px = cx + (float) Math.cos(a) * r;
            float py = cy + (float) Math.sin(a) * r;
            dst[idx++].set(px, py);
        }

        return idx;
    }

    /**
     * Reads stored origin X without deriving it from exposed boundary points.
     *
     * @return origin X in geometry units
     */
    public float getX() {
        return x;
    }

    /**
     * Replaces origin X and rewrites current boundary coordinates immediately.
     * Size, radius, and sample count are unchanged.
     *
     * @param x finite origin X
     */
    public void setX(float x) {
        this.x = x;
        updatePoints();
    }

    /**
     * Reads stored origin Y without deriving it from exposed boundary points.
     *
     * @return origin Y in geometry units
     */
    public float getY() {
        return y;
    }

    /**
     * Replaces origin Y and rewrites current boundary coordinates immediately.
     * Size, radius, and sample count are unchanged.
     *
     * @param y finite origin Y
     */
    public void setY(float y) {
        this.y = y;
        updatePoints();
    }

    /**
     * Reads stored width without deriving it from exposed boundary points.
     *
     * @return width in geometry units
     */
    public float getWidth() {
        return width;
    }

    /**
     * Replaces width, reclamps radius to fit, and immediately rebuilds points.
     * A radius reduced by shrinking is not automatically restored after growth.
     *
     * @param width finite nonnegative width expected by the caller
     */
    public void setWidth(float width) {
        this.width = width;
        ensureValidRadius();
        updatePoints();
    }

    /**
     * Reads stored height without deriving it from exposed boundary points.
     *
     * @return height in geometry units
     */
    public float getHeight() {
        return height;
    }

    /**
     * Replaces height, reclamps radius to fit, and immediately rebuilds points.
     * A radius reduced by shrinking is not automatically restored after growth.
     *
     * @param height finite nonnegative height expected by the caller
     */
    public void setHeight(float height) {
        this.height = height;
        ensureValidRadius();
        updatePoints();
    }

    /**
     * Reads the effective clamped corner radius used by the last geometry rebuild.
     *
     * @return current radius in geometry units
     */
    public float getRadius() {
        return radius;
    }

    /**
     * Clamps a requested radius to zero through half the smaller nonnegative size
     * and rebuilds points immediately. Unlike construction, this permits zero.
     *
     * @param radius finite requested corner radius
     */
    public void setRadius(float radius) {
        this.radius = radius;
        ensureValidRadius();
        updatePoints();
    }

    /**
     * Reads configured sampling density; the square-corner path still uses four
     * points regardless of this value.
     *
     * @return line segments per quarter arc, at least one
     */
    public int getSegmentsPerCorner() {
        return segmentsPerCorner;
    }

    /**
     * Clamps density to at least one and rebuilds geometry. A changed total point
     * count replaces the exposed array and its vectors; excessive sizes are not guarded.
     *
     * @param segmentsPerCorner requested segments per quarter arc
     */
    public void setSegmentsPerCorner(int segmentsPerCorner) {
        this.segmentsPerCorner = Math.max(1, segmentsPerCorner);
        updatePoints();
    }

    /**
     * Replaces both origin coordinates and updates points without changing size,
     * radius, or sampling density.
     *
     * @param x finite origin X
     * @param y finite origin Y
     */
    public void setPosition(float x, float y) {
        this.x = x;
        this.y = y;
        updatePoints();
    }

    /**
     * Replaces dimensions, clamps the existing radius to fit, and rebuilds points.
     * Growing later does not restore a radius previously reduced by this operation.
     *
     * @param width finite nonnegative width expected by the caller
     * @param height finite nonnegative height expected by the caller
     */
    public void setSize(float width, float height) {
        this.width = width;
        this.height = height;
        ensureValidRadius();
        updatePoints();
    }

    /**
     * Adds a borrowed displacement to the origin and refreshes all boundary points.
     * The supplied vector is read only and is not retained.
     *
     * @param offset non-null translation in geometry units
     * @throws NullPointerException if offset is null
     */
    @Override
    public void move(Vector2f offset) {
        this.x += offset.x();
        this.y += offset.y();
        updatePoints();
    }

    /**
     * Exposes the current mutable boundary array and its vectors without copying.
     * Do not overwrite its entries; updates reuse them, or replace the entire array
     * when sampling count changes. External coordinate edits do not update origin/size.
     *
     * @return live world/geometry-space boundary points
     */
    @Override
    public Vector2f[] points() {
        return points;
    }

    /**
     * Clamps finite radius to zero through half the smaller nonnegative dimension.
     * Negative dimensions imply a maximum radius of zero but remain stored as given.
     * NaN is not corrected by these comparisons.
     */
    private void ensureValidRadius() {
        float maxR = 0.5f * Math.min(Math.max(0f, width), Math.max(0f, height));
        if (radius < 0f) {
            radius = 0f;
        }
        if (radius > maxR) {
            radius = maxR;
        }
    }

    /**
     * Retains existing storage when its length matches; otherwise allocates exactly
     * the requested number of fresh vectors. Older exposed arrays become detached.
     *
     * @param count required boundary point count
     */
    private void ensurePointCount(int count) {
        if (points != null && points.length == count) {
            return;
        }

        points = new Vector2f[count];
        for (int i = 0; i < count; i++) {
            points[i] = new Vector2f();
        }
    }

    /**
     * Rewrites four square corners when radius is at most EPSILON; otherwise writes
     * four inclusive quarter arcs with segmentsPerCorner plus one samples each.
     * The boundary is cyclic without an extra repeated first point. Radius validation
     * must be performed by size/radius mutators before entering this helper.
     */
    private void updatePoints() {
        if (radius <= EPSILON) {
            ensurePointCount(4);
            points[0].set(x, y);
            points[1].set(x + width, y);
            points[2].set(x + width, y + height);
            points[3].set(x, y + height);
            return;
        }

        int arcPointCount = segmentsPerCorner + 1;
        int totalPointCount = arcPointCount * 4;
        ensurePointCount(totalPointCount);

        float tlCx = x + radius;
        float tlCy = y + radius;

        float trCx = x + width - radius;
        float trCy = y + radius;

        float brCx = x + width - radius;
        float brCy = y + height - radius;

        float blCx = x + radius;
        float blCy = y + height - radius;

        int idx = 0;
        idx = writeArc(points, idx, tlCx, tlCy, radius, (float) Math.PI, (float) (1.5 * Math.PI), arcPointCount);
        idx = writeArc(points, idx, trCx, trCy, radius, (float) (1.5 * Math.PI), (float) (2.0 * Math.PI), arcPointCount);
        idx = writeArc(points, idx, brCx, brCy, radius, 0f, (float) (0.5 * Math.PI), arcPointCount);
        writeArc(points, idx, blCx, blCy, radius, (float) (0.5 * Math.PI), (float) Math.PI, arcPointCount);
    }
}
