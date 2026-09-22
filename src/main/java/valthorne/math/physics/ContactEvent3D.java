package valthorne.math.physics;

import org.joml.Vector3f;

/**
 * Captures one native subshape-contact notification for deferred listener delivery.
 * The world copies native contact data during simulation and invokes application
 * listeners afterward on its owning thread. A body pair can have several
 * simultaneous subshape contacts, so notifications are not unique per body pair.
 *
 * <p>The normal is defensively copied, but body references remain live handles.
 * A body can already be destroyed when a notification is delivered; check its
 * state before invoking operations that require a live native body. Retaining
 * an event does not extend the body's native lifetime.</p>
 *
 * <p>World-generated removed notifications contain a zero normal and zero
 * penetration because their callback supplies identifiers rather than a manifold.
 * Direct construction does not enforce those conventions or validate identifiers,
 * type, penetration or body references.</p>
 *
 * @param type        the lifecycle phase reported for this subshape contact
 * @param bodyA       the first body reference associated with the native notification
 * @param bodyB       the second body reference associated with the native notification
 * @param subShapeA   the native subshape identifier within the first body
 * @param subShapeB   the native subshape identifier within the second body
 * @param normal      the native manifold's world-space normal, or zero for a removed contact
 * @param penetration the reported penetration depth in world units, or zero on removal
 * @author Albert Beaupre
 */
public record ContactEvent3D(Type type, RigidBody3D bodyA, RigidBody3D bodyB, int subShapeA, int subShapeB, Vector3f normal, float penetration) {
    /**
     * Copies the normal and retains all other components unchanged. This is a
     * data snapshot operation and does not query native body state.
     *
     * @param type        the contact phase to retain
     * @param bodyA       the first borrowed body reference
     * @param bodyB       the second borrowed body reference
     * @param subShapeA   the first native subshape identifier
     * @param subShapeB   the second native subshape identifier
     * @param normal      the nonnull normal vector to copy
     * @param penetration the reported penetration depth
     * @throws NullPointerException if normal is null
     */
    public ContactEvent3D {
        normal = new Vector3f(normal);
    }

    /**
     * Returns a defensive copy of the recorded normal, preserving its magnitude.
     * The zero vector for a removed contact supplies no surface direction.
     *
     * @return a newly allocated copy of the snapshot's world-space normal
     */
    @Override
    public Vector3f normal() {
        return new Vector3f(normal);
    }

    /**
     * Identifies the native contact callback represented by a snapshot.
     * Phases apply to a subshape pair rather than all contacts between two bodies.
     * A persisted notification should not be interpreted as a new collision.
     *
     * @author Albert Beaupre
     */
    public enum Type {
        /**
         * Reports that a subshape contact was added, with current manifold data.
         */
        ADDED,
        /**
         * Reports an existing subshape contact observed again by the simulation.
         */
        PERSISTED,
        /**
         * Reports removal of a subshape contact without current manifold data.
         */
        REMOVED
    }
}
