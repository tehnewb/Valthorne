package valthorne.math.physics;

import com.github.stephengold.joltjni.TwoBodyConstraint;

/**
 * Represents a native distance constraint registered with a {@link PhysicsWorld3D}.
 * The world creates the constraint from two distinct bodies, world-space anchors,
 * and an allowed distance interval. At least one body must be dynamic. This
 * handle exposes lifecycle management; it does not expose runtime anchor or
 * distance-limit editing.
 *
 * <p>The owning world removes the constraint when either body is destroyed or
 * the world closes. Calling {@link #close()} releases only the joint, leaving
 * both bodies alive. An already-destroyed joint can be closed again harmlessly.</p>
 *
 * <p>Close a live joint on the world's creating thread, outside a native update.
 * The handle and its destruction flag are not synchronized. Retaining this Java
 * object does not keep a removed native constraint alive.</p>
 *
 * @author Albert Beaupre
 */
public final class DistanceJoint3D implements AutoCloseable {
    final PhysicsWorld3D world; // World responsible for native registration and destruction.
    final RigidBody3D a, b; // Borrowed endpoint bodies; destroying either also removes this joint.
    final TwoBodyConstraint constraint; // Native constraint released through the owning world.
    boolean destroyed; // Set by the world after native removal and release finish.

    /**
     * Retains an already-created native constraint and its endpoint references.
     * Registration, validation and failure cleanup remain the world factory's
     * responsibility; this constructor neither registers nor validates the inputs.
     *
     * @param world      the world managing this joint's lifetime
     * @param a          the first endpoint body
     * @param b          the second endpoint body
     * @param constraint the native constraint associated with these bodies
     */
    DistanceJoint3D(PhysicsWorld3D world, RigidBody3D a, RigidBody3D b, TwoBodyConstraint constraint) {
        this.world = world;
        this.a = a;
        this.b = b;
        this.constraint = constraint;
    }

    /**
     * Returns the destruction flag recorded by the world without querying native
     * state or validating the current thread. Read it under the world's normal
     * thread-ownership rules because the flag is not volatile.
     *
     * @return whether this joint has been removed and its native constraint released
     */
    public boolean isDestroyed() {return destroyed;}

    /**
     * Requests removal and release through the owning world unless destruction
     * has already completed. Both endpoint bodies remain owned by the world.
     * An already-destroyed handle returns without accessing world state.
     *
     * @throws IllegalStateException if a live joint is closed from the wrong
     *                               thread, after world closure, or during a native update
     */
    @Override
    public void close() {if (!destroyed) world.destroyJoint(this);}
}
