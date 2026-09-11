package valthorne.math.physics;

import org.joml.Vector3f;

/**
 * Captures the closest collision-shape hit returned by a physical ray query.
 * {@link PhysicsWorld3D#raycast(org.joml.primitives.Rayf, float)} queries native
 * collision geometry independently of rendered visibility, textures or mesh picking.
 * Its distance is measured along the normalized query ray in world units.
 *
 * <p>Position and normal are copied on construction and on access. The body is
 * a borrowed live reference whose lifecycle remains owned by its physics world;
 * retaining a hit does not prevent body destruction. Direct record construction
 * does not validate distance, body state or vector finiteness.</p>
 *
 * @param body     the collision body identified by the query
 * @param distance distance from the ray origin in world units
 * @param position the world-space hit position to snapshot
 * @param normal   the world-space collision surface normal to snapshot
 * @author Albert Beaupre
 */
public record PhysicsRayHit3D(
        RigidBody3D body,
        float distance,
        Vector3f position,
        Vector3f normal
) {
    /**
     * Copies the hit point and normal while retaining the body reference and
     * distance unchanged. Later changes to the supplied vectors cannot alter the hit.
     *
     * @param body     the body reference to retain without validation
     * @param distance the distance to retain without clamping
     * @param position the nonnull world-space hit point
     * @param normal   the nonnull world-space surface normal
     * @throws NullPointerException if position or normal is null
     */
    public PhysicsRayHit3D {
        position = new Vector3f(position);
        normal = new Vector3f(normal);
    }

    /**
     * Returns an independent copy of the recorded world-space point. Mutating
     * the result does not affect this snapshot or move the associated body.
     *
     * @return a newly allocated hit-position vector
     */
    @Override
    public Vector3f position() {return new Vector3f(position);}

    /**
     * Returns an independent copy of the recorded surface normal. No additional
     * normalization is performed, including for directly constructed snapshots.
     *
     * @return a newly allocated world-space normal vector
     */
    @Override
    public Vector3f normal() {return new Vector3f(normal);}
}
