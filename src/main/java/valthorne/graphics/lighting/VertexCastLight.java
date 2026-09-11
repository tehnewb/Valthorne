package valthorne.graphics.lighting;

import valthorne.graphics.Color;
import org.joml.Vector2f;

import java.util.Arrays;

/**
 * Base for lights that accumulate, sort, and compact ray angles before casting.
 * Angle storage grows as needed and is reused across updates. Numerical deduplication
 * compares stored radians directly, so equivalent angles across a full-turn boundary
 * are not automatically merged. Derived lights choose which angles to collect.
 *
 * @author Albert Beaupre
 */
abstract class VertexCastLight extends Light {

    /**
     * Angular offset in radians for rays passing just beside an occluder vertex.
     */
    protected static final float EPSILON = 0.0002f;
    /**
     * Minimum retained numerical separation between sorted ray angles in radians.
     */
    protected static final float MIN_ANGLE_DELTA = 0.0001f;

    private float[] angles = new float[64]; // Reusable angle storage, with valid entries before angleCount.
    private int angleCount; // Number of currently collected ray angles.

    /**
     * Initializes shared light state and endpoint storage. The constructor retains the
     * handler but does not register this light with its render list; geometry is rebuilt
     * by update when active and dirty.
     *
     * @param rayHandler handler providing the occlusion world
     * @param rays base ray count, at least three
     * @param color color copied into the light
     * @param distance radial extent in world units
     * @param x world-space center X
     * @param y world-space center Y
     * @throws NullPointerException if handler or color is null
     * @throws IllegalArgumentException if rays is below three
     */
    protected VertexCastLight(RayHandler rayHandler, int rays, Color color, float distance, float x, float y) {
        super(rayHandler, rays, color, distance, x, y);
    }

    /**
     * Clears the logical angle count while retaining allocated storage for reuse.
     */
    protected final void resetAngles() {
        angleCount = 0;
    }

    /**
     * Appends a raw angle, growing storage when needed. Does not normalize its range,
     * reject non-finite values, or deduplicate until compaction.
     *
     * @param angle ray direction in radians
     */
    protected final void addAngle(float angle) {
        ensureAngleCapacity(angleCount + 1);
        angles[angleCount++] = angle;
    }

    /**
     * Reads angle storage directly without checking against the logical count.
     *
     * @param index valid collected-angle index
     * @return stored radians
     */
    protected final float angleAt(int index) {
        return angles[index];
    }

    /**
     * Returns the number of valid collected angles before or after compaction.
     *
     * @return logical angle count
     */
    protected final int getAngleCount() {
        return angleCount;
    }

    /**
     * Sorts collected angles ascending and retains values separated from the last kept
     * angle by at least MIN_ANGLE_DELTA. Compacts in place without shrinking storage.
     * Angles are not wrapped modulo a full turn.
     */
    protected final void sortAndCompactAngles() {
        if (angleCount <= 1) {
            return;
        }

        Arrays.sort(angles, 0, angleCount);

        int write = 1;
        float last = angles[0];

        for (int read = 1; read < angleCount; read++) {
            float current = angles[read];
            if (Math.abs(current - last) >= MIN_ANGLE_DELTA) {
                angles[write++] = current;
                last = current;
            }
        }

        angleCount = write;
    }

    /**
     * Sorts and compacts angles, sizes endpoint arrays to that count, and casts one ray
     * per angle using shared occlusion policy. Clears dirty state after completion.
     */
    protected final void rebuildFromAngles() {
        sortAndCompactAngles();
        ensureRayCapacity(angleCount);

        for (int i = 0; i < angleCount; i++) {
            float angle = angles[i];
            float targetX = x + (float) Math.cos(angle) * distance;
            float targetY = y + (float) Math.sin(angle) * distance;
            applyRayResult(i, targetX, targetY);
        }

        dirty = false;
    }

    /**
     * Checks whether any non-null vertex lies within the squared radius around this
     * light. This is a vertex test, not a polygon-edge intersection test.
     *
     * @param points candidate vertices, possibly null
     * @param maxDistanceSquared squared radial limit
     * @return true if at least one vertex is within the limit
     */
    protected final boolean isPotentialOccluder(Vector2f[] points, float maxDistanceSquared) {
        if (points == null) {
            return false;
        }

        for (Vector2f point : points) {
            if (point == null) {
                continue;
            }

            float dx = point.x() - x;
            float dy = point.y() - y;
            float distanceSquared = dx * dx + dy * dy;
            if (distanceSquared <= maxDistanceSquared) {
                return true;
            }
        }

        return false;
    }

    /**
     * Doubles angle storage until it can hold the requested count, preserving collected
     * values. Existing sufficient capacity is retained.
     *
     * @param count required element capacity
     */
    private void ensureAngleCapacity(int count) {
        if (angles.length >= count) {
            return;
        }

        int newSize = angles.length;
        while (newSize < count) {
            newSize <<= 1;
        }
        angles = Arrays.copyOf(angles, newSize);
    }
}
