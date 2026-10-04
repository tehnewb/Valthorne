package valthorne.math.physics;

import com.github.stephengold.joltjni.DistanceConstraint;
import com.github.stephengold.joltjni.TwoBodyConstraint;

/**
 * World-owned handle for a native distance or pivot constraint between two
 * distinct bodies. At least one attached body must be dynamic. Joint anchors
 * are supplied in world meters at creation and retained in each body's local
 * coordinates by Jolt. Disabling a joint preserves its native resource.
 *
 * <p>All access belongs to the creating world's thread. Closing the joint,
 * destroying either attached body, or clearing/closing the world invalidates
 * this handle. Disposal is idempotent. Bodies connected by a joint still collide
 * normally; joints do not install collision exclusions. Dynamic motion remains
 * planar because both attached bodies retain their original degree constraints.</p>
 */
public final class PhysicsJoint2D implements AutoCloseable {
    final PhysicsWorld2D world; // Simulation that owns this constraint and its body handles.
    final RigidBody2D first; // First attached body, potentially destroyed after joint disposal.
    final RigidBody2D second; // Second attached body, potentially destroyed after joint disposal.
    final TwoBodyConstraint constraint; // Native constraint reference owned until destruction.
    int index; // Current slot in the world's dense joint array.
    boolean destroyed; // Whether the native constraint has been released.

    /**
     * Wraps a successfully constructed native constraint awaiting registration.
     *
     * @param world owning simulation
     * @param first first attached live body
     * @param second second attached live body
     * @param constraint owned native constraint
     * @param index insertion slot in the dense joint array
     */
    PhysicsJoint2D(PhysicsWorld2D world, RigidBody2D first, RigidBody2D second, TwoBodyConstraint constraint, int index) {
        this.world = world;
        this.first = first;
        this.second = second;
        this.constraint = constraint;
        this.index = index;
    }

    /**
     * Requires a live joint on an open world's owning thread.
     *
     * @throws IllegalStateException if this handle can no longer access native state
     */
    private void check() {
        world.check();
        if (destroyed) throw new IllegalStateException("Physics joint has been destroyed");
    }

    /**
     * Requires the distance relationship before accessing its limits.
     *
     * @return live native distance constraint
     * @throws IllegalStateException if the joint is invalid or is a pivot
     */
    private DistanceConstraint distance() {
        check();
        if (!(constraint instanceof DistanceConstraint distance))
            throw new IllegalStateException("Operation requires a distance joint");
        return distance;
    }

    /**
     * Reports destruction, including after its world has closed.
     *
     * @return whether native resources were relinquished
     */
    public boolean isDestroyed() {
        world.checkOwner();
        return destroyed;
    }

    /**
     * Returns the immutable joint relationship.
     *
     * @return distance or pivot relationship
     */
    public JointType2D getType() {
        world.checkOwner();
        return constraint instanceof DistanceConstraint ? JointType2D.DISTANCE : JointType2D.PIVOT;
    }

    /**
     * Returns the first attached handle, which may since have been destroyed.
     *
     * @return first body supplied at creation
     */
    public RigidBody2D getFirstBody() {
        world.checkOwner();
        return first;
    }

    /**
     * Returns the second attached handle, which may since have been destroyed.
     *
     * @return second body supplied at creation
     */
    public RigidBody2D getSecondBody() {
        world.checkOwner();
        return second;
    }

    /**
     * Reports whether the constraint currently participates in solving.
     *
     * @return whether the native joint is enabled
     */
    public boolean isEnabled() {
        check();
        return constraint.getEnabled();
    }

    /**
     * Changes solver participation and wakes attached moving bodies.
     *
     * @param enabled whether to solve this joint
     * @return this joint
     */
    public PhysicsJoint2D setEnabled(boolean enabled) {
        check();
        constraint.setEnabled(enabled);
        first.activate();
        second.activate();
        return this;
    }

    /**
     * Returns the lower anchor-separation bound for a distance joint.
     *
     * @return minimum distance in meters
     * @throws IllegalStateException if this joint is not a live distance joint
     */
    public float getMinDistance() {
        return distance()
                .getMinDistance();
    }

    /**
     * Returns the upper anchor-separation bound for a distance joint.
     *
     * @return maximum distance in meters
     * @throws IllegalStateException if this joint is not a live distance joint
     */
    public float getMaxDistance() {
        return distance()
                .getMaxDistance();
    }

    /**
     * Changes a distance joint's separation limits and wakes attached bodies.
     * Equal limits create a fixed-length relationship; minimum zero creates a
     * rope-like upper bound. A strictly positive maximum avoids degenerate axes.
     *
     * @param minimum finite nonnegative minimum in meters
     * @param maximum finite positive maximum, at least the minimum
     * @return this joint
     * @throws IllegalArgumentException if limits are invalid
     * @throws IllegalStateException if this joint is not a live distance joint
     */
    public PhysicsJoint2D setDistance(float minimum, float maximum) {
        DistanceConstraint distance = distance();
        PhysicsValidation2D.nonnegative(minimum, "minimum distance");
        PhysicsValidation2D.positive(maximum, "maximum distance");
        if (maximum < minimum) throw new IllegalArgumentException("Maximum distance must be at least the minimum");
        distance.setDistance(minimum, maximum);
        first.activate();
        second.activate();
        return this;
    }

    @Override
    public void close() {
        world.destroyJoint(this);
    }
}
