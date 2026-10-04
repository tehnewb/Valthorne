package valthorne.math.physics;

/**
 * Defines how a planar rigid body participates in simulation. Static bodies
 * remain fixed, kinematic bodies follow prescribed velocities, and dynamic
 * bodies respond to gravity, forces, impulses, and collision constraints.
 */
public enum MotionType2D {
    /**
     * Fixed geometry that does not respond to forces or prescribed velocities.
     */
    STATIC,

    /**
     * Moving geometry driven by prescribed velocity rather than solver forces.
     */
    KINEMATIC,

    /**
     * Moving geometry that responds to gravity, forces and collision constraints.
     */
    DYNAMIC
}
