package valthorne.math.physics;

import com.github.stephengold.joltjni.DistanceConstraintSettings;
import com.github.stephengold.joltjni.PhysicsSystem;
import com.github.stephengold.joltjni.PointConstraintSettings;
import com.github.stephengold.joltjni.TwoBodyConstraint;
import com.github.stephengold.joltjni.TwoBodyConstraintSettings;
import com.github.stephengold.joltjni.enumerate.EConstraintSpace;

import java.util.Objects;

/**
 * Lazily allocated native joint ownership for one planar world. A fixed dense
 * array bounds memory and supports constant-time independent removal; body
 * destruction scans that prefix to remove attached relationships first.
 * All creation, mutation and cleanup runs on the world's owner thread outside
 * native simulation. Constraints retain local anchors after construction and
 * never install implicit body collision exclusions.
 */
final class PhysicsJoints2D implements AutoCloseable {
    private final PhysicsWorld2D world; // Owner used for body membership and native body access.
    private final PhysicsSystem system; // Borrowed native system, valid until this subsystem closes.
    private final PhysicsJoint2D[] joints; // Fixed-capacity dense live joint handles.
    private int jointCount; // Occupied prefix length of the joint array.

    /**
     * Allocates joint storage on the world's first constraint operation.
     *
     * @param world owning live simulation
     * @param system borrowed native system
     * @param capacity maximum simultaneous joints
     */
    PhysicsJoints2D(PhysicsWorld2D world, PhysicsSystem system, int capacity) {
        this.world = world;
        this.system = system;
        joints = new PhysicsJoint2D[capacity];
    }

    /**
     * Validates a live handle before passing its ID to native constraints.
     *
     * @param body body expected to belong to this world
     * @throws IllegalArgumentException if ownership is foreign
     * @throws IllegalStateException if the body has been destroyed
     */
    private void requireBody(RigidBody2D body) {
        Objects.requireNonNull(body, "body");
        if (body.world != world) throw new IllegalArgumentException("Body belongs to another world");
        if (body.destroyed) throw new IllegalStateException("Rigid body has been destroyed");
    }

    /**
     * Requires a solvable body pair and available native joint capacity.
     *
     * @param first first attached body
     * @param second second attached body
     */
    private void prepare(RigidBody2D first, RigidBody2D second) {
        requireBody(first);
        requireBody(second);
        if (first == second || first.getMotionType() != MotionType2D.DYNAMIC && second.getMotionType() != MotionType2D.DYNAMIC)
            throw new IllegalArgumentException("Joint requires distinct bodies and at least one dynamic body");
        if (jointCount == joints.length) throw new IllegalStateException("Physics joint capacity reached");
    }

    /**
     * Creates a distance constraint with validated world-space anchors and limits.
     *
     * @param first first attached body
     * @param second second attached body
     * @param anchorFirstX first anchor world X in meters
     * @param anchorFirstY first anchor world Y in meters
     * @param anchorSecondX second anchor world X in meters
     * @param anchorSecondY second anchor world Y in meters
     * @param minimum nonnegative minimum distance in meters
     * @param maximum positive maximum distance, at least minimum
     * @return newly owned native relationship
     */
    PhysicsJoint2D createDistanceJoint(RigidBody2D first, RigidBody2D second, float anchorFirstX, float anchorFirstY, float anchorSecondX, float anchorSecondY, float minimum, float maximum) {
        prepare(first, second);
        PhysicsValidation2D.finite(anchorFirstX, "first anchor x");
        PhysicsValidation2D.finite(anchorFirstY, "first anchor y");
        PhysicsValidation2D.finite(anchorSecondX, "second anchor x");
        PhysicsValidation2D.finite(anchorSecondY, "second anchor y");
        PhysicsValidation2D.nonnegative(minimum, "minimum distance");
        PhysicsValidation2D.positive(maximum, "maximum distance");
        if (maximum < minimum) throw new IllegalArgumentException("Maximum distance must be at least the minimum");
        try (DistanceConstraintSettings settings = new DistanceConstraintSettings()) {
            settings.setSpace(EConstraintSpace.WorldSpace);
            settings.setPoint1(anchorFirstX, anchorFirstY, 0);
            settings.setPoint2(anchorSecondX, anchorSecondY, 0);
            settings.setMinDistance(minimum);
            settings.setMaxDistance(maximum);
            return add(first, second, settings, JointType2D.DISTANCE);
        }
    }

    /**
     * Creates a point constraint whose remaining freedom is planar Z rotation.
     *
     * @param first first attached body
     * @param second second attached body
     * @param anchorX shared world X in meters
     * @param anchorY shared world Y in meters
     * @return newly owned pivot relationship
     */
    PhysicsJoint2D createPivotJoint(RigidBody2D first, RigidBody2D second, float anchorX, float anchorY) {
        prepare(first, second);
        PhysicsValidation2D.finite(anchorX, "anchor x");
        PhysicsValidation2D.finite(anchorY, "anchor y");
        try (PointConstraintSettings settings = new PointConstraintSettings()) {
            settings.setSpace(EConstraintSpace.WorldSpace);
            settings.setPoint1(anchorX, anchorY, 0);
            settings.setPoint2(anchorX, anchorY, 0);
            return add(first, second, settings, JointType2D.PIVOT);
        }
    }

    /**
     * Registers native joint ownership with rollback on construction failure.
     *
     * @param first first validated body
     * @param second second validated body
     * @param settings temporary native constraint description
     * @param type relationship exposed by the handle
     * @return registered joint handle
     */
    private PhysicsJoint2D add(RigidBody2D first, RigidBody2D second, TwoBodyConstraintSettings settings, JointType2D type) {
        TwoBodyConstraint constraint = world.bodies.createConstraint(settings, first.id, second.id);
        boolean added = false;
        try {
            PhysicsJoint2D joint = new PhysicsJoint2D(world, first, second, constraint, type, jointCount);
            system.addConstraint(constraint);
            added = true;
            first.activate();
            second.activate();
            joints[jointCount++] = joint;
            return joint;
        } catch (RuntimeException | Error failure) {
            try {
                if (added) system.removeConstraint(constraint);
            } catch (RuntimeException | Error cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            try {
                constraint.close();
            } catch (RuntimeException | Error cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
    }

    /**
     * Counts relationships that still own native references.
     *
     * @return live joint count
     */
    int getCount() {
        return jointCount;
    }

    /**
     * Removes a validated owned joint in constant time and closes its reference.
     *
     * @param joint live owned handle
     */
    void destroy(PhysicsJoint2D joint) {
        system.removeConstraint(joint.constraint);
        int last = --jointCount;
        if (joint.index != last) {
            PhysicsJoint2D moved = joints[last];
            joints[joint.index] = moved;
            moved.index = joint.index;
        }
        joints[last] = null;
        joint.destroyed = true;
        joint.constraint.close();
    }

    /**
     * Removes every relationship attached to a body before native body deletion.
     *
     * @param body live body being removed
     */
    void destroyAttached(RigidBody2D body) {
        for (int i = jointCount - 1; i >= 0; i--)
            if (joints[i].first == body || joints[i].second == body) destroy(joints[i]);
    }

    @Override
    public void close() {
        Throwable failure = null;
        while (jointCount != 0) {
            PhysicsJoint2D joint = joints[--jointCount];
            joints[jointCount] = null;
            joint.destroyed = true;
            try {
                system.removeConstraint(joint.constraint);
            } catch (RuntimeException | Error cleanupFailure) {
                if (failure == null) failure = cleanupFailure;
                else failure.addSuppressed(cleanupFailure);
            }
            try {
                joint.constraint.close();
            } catch (RuntimeException | Error cleanupFailure) {
                if (failure == null) failure = cleanupFailure;
                else failure.addSuppressed(cleanupFailure);
            }
        }
        if (failure instanceof RuntimeException exception) throw exception;
        if (failure instanceof Error error) throw error;
    }
}
