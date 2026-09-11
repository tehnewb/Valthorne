package valthorne.graphics.lighting;

/**
 * Reusable mutable result of a two-dimensional segment cast. Position, segment
 * fraction, hit status, and borrowed collider identity are independent stored values;
 * setters do not enforce consistency or range constraints. The query producer defines
 * coordinates and resets the object before reuse. Copy values before retaining results.
 *
 * @author Albert Beaupre
 */
public final class RayCastHit {

    private boolean hit; // Stored query hit status.
    private float x; // Stored hit X coordinate.
    private float y; // Stored hit Y coordinate.
    private float fraction = 1f; // Stored segment parameter, initially one.
    private Object collider; // Borrowed collider identity, or null.

    /**
     * Creates a miss at zero coordinates with fraction one and no collider identity.
     */
    public RayCastHit() {
    }

    /**
     * Creates a hit at the supplied coordinates with fraction one and no collider.
     * Does not derive a segment fraction from the position.
     *
     * @param x hit X coordinate
     * @param y hit Y coordinate
     */
    public RayCastHit(float x, float y) {
        this.hit = true;
        this.x = x;
        this.y = y;
    }

    /**
     * Returns stored hit status without checking coordinates or collider identity.
     *
     * @return whether a hit was recorded
     */
    public boolean isHit() {
        return hit;
    }

    /**
     * Changes only hit status, leaving position, fraction, and collider unchanged.
     *
     * @param hit replacement hit flag
     */
    public void setHit(boolean hit) {
        this.hit = hit;
    }

    /**
     * Returns the stored hit X coordinate without interpreting or modifying the result.
     *
     * @return hit X coordinate
     */
    public float getX() {
        return x;
    }

    /**
     * Replaces only the hit X coordinate. Other result fields remain unchanged;
     * no consistency or range validation is performed.
     *
     * @param x replacement hit X coordinate
     */
    public void setX(float x) {
        this.x = x;
    }

    /**
     * Returns the stored hit Y coordinate without interpreting or modifying the result.
     *
     * @return hit Y coordinate
     */
    public float getY() {
        return y;
    }

    /**
     * Replaces only the hit Y coordinate. Other result fields remain unchanged;
     * no consistency or range validation is performed.
     *
     * @param y replacement hit Y coordinate
     */
    public void setY(float y) {
        this.y = y;
    }

    /**
     * Returns the stored segment parameter without interpreting or modifying the result.
     *
     * @return segment parameter
     */
    public float getFraction() {
        return fraction;
    }

    /**
     * Replaces only the segment parameter. Other result fields remain unchanged;
     * no consistency or range validation is performed.
     *
     * @param fraction replacement segment parameter
     */
    public void setFraction(float fraction) {
        this.fraction = fraction;
    }

    /**
     * Returns the stored borrowed collider identity without interpreting or modifying the result.
     *
     * @return borrowed collider identity
     */
    public Object getCollider() {
        return collider;
    }

    /**
     * Replaces only the borrowed collider identity. Other result fields remain unchanged;
     * no consistency or range validation is performed.
     *
     * @param collider replacement borrowed collider identity
     */
    public void setCollider(Object collider) {
        this.collider = collider;
    }

    /**
     * Replaces the entire result without validation or copying the collider object.
     * Use a consistent coordinate space and segment fraction when populating a result.
     *
     * @param hit whether the segment hit
     * @param x hit X coordinate
     * @param y hit Y coordinate
     * @param fraction segment parameter, conventionally zero through one
     * @param collider borrowed collision identity, or null
     */
    public void set(boolean hit, float x, float y, float fraction, Object collider) {
        this.hit = hit;
        this.x = x;
        this.y = y;
        this.fraction = fraction;
        this.collider = collider;
    }

    /**
     * Restores a miss with zero coordinates, fraction one, and no collider. Does not
     * modify or dispose any previously referenced collider.
     */
    public void clear() {
        this.hit = false;
        this.x = 0f;
        this.y = 0f;
        this.fraction = 1f;
        this.collider = null;
    }
}
