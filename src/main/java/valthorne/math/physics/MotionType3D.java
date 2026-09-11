package valthorne.math.physics;

/**
 * Selects the motion category assigned when a rigid body is created.
 * {@link BodySettings3D} retains this choice and {@link PhysicsWorld3D} maps it
 * to the corresponding native motion type. It also controls which velocity,
 * force and kinematic operations {@link RigidBody3D} permits.
 *
 * <p>Triangle-mesh collision shapes require static bodies in this integration.
 * Static/static collision pairs are excluded by the world's native filters;
 * kinematic and dynamic bodies occupy the moving side of those filters.</p>
 *
 * @author Albert Beaupre
 */
public enum MotionType3D {
    /**
     * Represents stationary collision geometry. The wrapper rejects velocity,
     * force and impulse operations, but permits explicit transform changes.
     */
    STATIC,
    /**
     * Represents application-driven motion. Velocity setters and kinematic target
     * movement are supported; dynamic-only force and impulse methods are rejected.
     */
    KINEMATIC,
    /**
     * Represents a simulated rigid body eligible for forces, impulses, torque,
     * velocity changes and sleeping. Motion is advanced by the world's fixed steps.
     */
    DYNAMIC
}
