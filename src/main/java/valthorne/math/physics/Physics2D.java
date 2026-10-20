package valthorne.math.physics;

import valthorne.math.geometry.Shape;

/**
 * Owns a planar Jolt simulation with direct, fluent geometry-body creation.
 * Create this instance once per scene, advance it with elapsed seconds, and
 * close it when that scene ends. Original geometry is synchronized automatically
 * for rendering. Geometry coordinates are pixels; simulation forces, velocities
 * and impulses use meters, kilograms and seconds. Access belongs exclusively to
 * the creating thread, including when native worker threads are configured.
 *
 * <pre>{@code
 * Physics2D physics = new Physics2D(new PhysicsWorldSettings2D().pixelsYDown(true));
 * RigidBody2D ball = physics.rigid(new Circle(200, 100, 24))
 *         .restitution(0.6f)
 *         .friction(0.3f);
 * physics.rigid(new Rectangle(0, 500, 800, 32)).motion(MotionType2D.STATIC);
 * physics.update(deltaSeconds);
 * Vector2f position = ball.getInterpolatedPosition().asPixels();
 * physics.close();
 * }</pre>
 *
 * <p>Creation returns the live handle, with no builder or deferred registration.
 * Fluent body calls modify it immediately. One lightweight scene coordinator
 * owns the underlying world; no additional per-body wrapper is allocated.
 * Advanced queries, joints and contact listeners remain available through
 * {@link #world()}. Closing this instance invalidates all its body handles.</p>
 */
public final class Physics2D implements AutoCloseable {
    private final PhysicsWorld2D world; // Owned solver, coordinate mapping and native resources.

    /**
     * Creates a scene simulation using 64 pixels per meter and Y-up rendering.
     */
    public Physics2D() {
        world = new PhysicsWorld2D();
    }

    /**
     * Creates a scene simulation using explicit timing, rendering and capacities.
     * @param settings world configuration copied during construction, not null
     */
    public Physics2D(PhysicsWorldSettings2D settings) {
        world = new PhysicsWorld2D(settings);
    }

    /**
     * Creates a live one-kilogram dynamic body from pixel geometry. Configure
     * motion and contact material directly on the returned body. Geometry must
     * have a nondegenerate outline and retain its dimensions while attached.
     * @param shape supported geometry shape, not null
     * @return live body retaining and automatically updating the original shape
     */
    public RigidBody2D rigid(Shape shape) {
        return world.createBody(shape);
    }

    /**
     * Creates a deformable native mesh from a convex pixel shape. Its original
     * perimeter vectors update automatically; configure mass, edge compliance,
     * material properties and vertex pins directly on the returned handle.
     * @param shape supported nondegenerate convex geometry, not null
     * @return live deformable body owned by this simulation
     */
    public SoftBody2D soft(Shape shape) {
        return world.createSoftBody(shape);
    }

    /**
     * Advances bounded fixed steps and updates geometry with render interpolation.
     * @param deltaSeconds finite nonnegative elapsed frame time in seconds
     * @return completed fixed-step count
     */
    public int update(float deltaSeconds) {
        return world.update(deltaSeconds);
    }

    /**
     * Advances exactly one fixed step and writes current geometry poses.
     */
    public void step() {
        world.step();
    }

    /**
     * Exposes this instance's world for queries, joints, contacts and camera mapping.
     * @return owned world; the caller must not outlive this instance's lifecycle
     */
    public PhysicsWorld2D world() {
        return world;
    }

    @Override
    public void close() {
        world.close();
    }
}
