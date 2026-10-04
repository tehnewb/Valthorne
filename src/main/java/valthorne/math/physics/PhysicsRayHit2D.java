package valthorne.math.physics;

/**
 * Caller-owned reusable destination for a closest planar ray hit. A query
 * overwrites all values, including clearing the body on a miss. Coordinates
 * are world meters and the fraction measures progress from the segment's start
 * to its end. The stored handle may subsequently be destroyed by its world.
 * This destination is intended for owner-thread use and owns no native memory.
 */
public final class PhysicsRayHit2D {
    private RigidBody2D body; // Hit body; null before a query or after a miss.
    private float x; // World hit coordinate along X, or zero on a miss.
    private float y; // World hit coordinate along Y, or zero on a miss.
    private float fraction; // Segment fraction on a hit, or zero on a miss.

    /**
     * Creates an empty destination that may be reused for arbitrarily many casts.
     */
    public PhysicsRayHit2D() {
    }

    /**
     * Reports whether the latest cast hit a body.
     *
     * @return whether the destination contains a hit
     */
    public boolean hasHit() {
        return body != null;
    }

    /**
     * Returns the handle hit by the latest cast, possibly destroyed afterward.
     *
     * @return hit body, or null if no hit was recorded
     */
    public RigidBody2D getBody() {
        return body;
    }

    /**
     * Returns the world-space horizontal hit coordinate.
     *
     * @return meters along X, or zero on a miss
     */
    public float getX() {
        return x;
    }

    /**
     * Returns the world-space vertical hit coordinate.
     *
     * @return meters along Y, or zero on a miss
     */
    public float getY() {
        return y;
    }

    /**
     * Returns progress along the supplied ray segment.
     *
     * @return fraction between zero and one on a hit, or zero on a miss
     */
    public float getFraction() {
        return fraction;
    }

    /**
     * Replaces every result component after a successful query.
     *
     * @param body hit handle, or null to clear the destination
     * @param x world X in meters
     * @param y world Y in meters
     * @param fraction progress along the ray segment
     */
    void set(RigidBody2D body, float x, float y, float fraction) {
        this.body = body;
        this.x = x;
        this.y = y;
        this.fraction = fraction;
    }
}
