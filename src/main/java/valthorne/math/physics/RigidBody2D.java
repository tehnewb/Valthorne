package valthorne.math.physics;

import com.github.stephengold.joltjni.enumerate.EActivation;
import org.joml.Vector2f;
import valthorne.math.geometry.Shape;

import java.util.Objects;

/**
 * World-owned handle for a planar rigid body. Pose getters read the last captured
 * simulation state; velocity getters read current native state. Mutators accept
 * meters, seconds, kilograms, Newtons, and counterclockwise radians around Z.
 * All access is confined to the creating world's thread.
 *
 * <p>Closing destroys the native body. Clearing or closing the world invalidates
 * every outstanding handle. Destruction is idempotent, while pose and velocity
 * operations on a destroyed handle throw. Native body IDs may be reused; the
 * handle's destroyed flag prevents stale handles from accessing replacement
 * bodies. Motion type and rotation-lock state are fixed at creation.</p>
 *
 * <p>Interpolation blends the previous and current captured poses using the
 * world's fractional frame remainder, introducing the usual one-step render
 * delay. Rotation follows the shortest angular arc; rotations exceeding half a
 * turn within one step cannot be reconstructed from endpoint poses. Teleports
 * reset both endpoints, so they never smear across the intervening space.</p>
 *
 * <p>Destination-based reads and primitive mutators reuse the world's scratch
 * storage and allocate no temporary vectors. Forces and torques are consumed by
 * the next native step; impulses immediately alter dynamic velocity. Native
 * maximum velocities and contact tolerances remain in effect.</p>
 *
 * <p>Position reads fill the supplied Vector2f and return this body for immediate
 * pixel chaining: {@code ball.getPosition().asPixels()}. No-argument reads lazily
 * reuse one body-owned vector; destination overloads remain available. Conversion
 * uses the owning world's configuration and returns the same destination vector.
 * No conversion or position wrapper is needed.
 * Passing a destination selects the output reused by subsequent reads; each
 * read replaces the interpolation mode.</p>
 */
public final class RigidBody2D implements AutoCloseable {
    final PhysicsGeometry2D geometry; // Optional immutable geometry snapshot shared with the original settings.
    final PhysicsWorld2D world; // Owning simulation and reusable owner-thread scratch storage.
    final int id; // Native body identifier, valid only until this handle is destroyed.
    int index; // Current index in the world's dense live-handle array.
    boolean destroyed; // Whether this handle has relinquished its native body.
    private final MotionType2D motion; // Immutable simulation response selected at construction.
    private final boolean fixedRotation; // Whether angular simulation around Z is disabled.
    private final boolean sensor; // Whether collisions are overlaps without physical response.
    private final CollisionShape2D shape; // Immutable geometry used for exact planar point queries.
    private Vector2f positionDestination; // Last caller-owned destination used by a fluent position read.
    private boolean interpolatedPosition; // Whether the pending conversion reads the interpolated pose.
    private float x; // Current captured horizontal center in meters.
    private float y; // Current captured vertical center in meters.
    private float angle; // Current captured orientation in radians, normalized to [-pi, pi].
    private float previousX; // Horizontal center captured before the latest step.
    private float previousY; // Vertical center captured before the latest step.
    private float previousAngle; // Orientation captured before the latest step.

    /**
     * Captures the initial native pose and immutable body configuration.
     *
     * @param world owning world
     * @param id successfully created native body ID
     * @param index insertion index in the dense world array
     * @param settings configuration whose values are copied
     */
    RigidBody2D(PhysicsWorld2D world, int id, int index, BodySettings2D settings) {
        this.world = world;
        this.id = id;
        this.index = index;
        motion = settings.motion;
        fixedRotation = settings.fixedRotation;
        sensor = settings.sensor;
        shape = settings.shape;
        geometry = settings.geometry;
        capture();
        remember();
    }

    /**
     * Returns the original geometry object, without allocating or copying it.
     * @return attached geometry, or null when created from a collision descriptor
     */
    public Shape getGeometry() {
        world.checkOwner();
        return geometry == null ? null : geometry.shape;
    }

    /**
     * Writes the current physics pose into attached geometry immediately. Normal
     * frame updates do this automatically with interpolation; this method is useful
     * after a teleport or when drawing current simulation state.
     * @return this body
     * @throws IllegalStateException if no geometry is attached or the body is invalid
     */
    public RigidBody2D syncGeometry() {
        check();
        if (geometry == null) throw new IllegalStateException("Body has no attached geometry");
        geometry.sync(this, false);
        return this;
    }

    /**
     * Teleports this body to the attached geometry's current position and degree
     * rotation, resetting interpolation. Use after moving a shape manually; this
     * copies its pose only, so changing dimensions requires recreating the body.
     * @return this body
     * @throws IllegalStateException if no geometry is attached or the body is invalid
     */
    public RigidBody2D setTransformFromGeometry() {
        check();
        if (geometry == null) throw new IllegalStateException("Body has no attached geometry");
        geometry.push(this);
        return this;
    }

    /**
     * Requires a live handle and an open world on the owner thread.
     *
     * @throws IllegalStateException if native access would be invalid
     */
    private void check() {
        world.check();
        if (destroyed) throw new IllegalStateException("Rigid body has been destroyed");
    }

    /**
     * Requires a dynamic body for mass-dependent operations.
     *
     * @throws IllegalStateException if the handle is invalid or nondynamic
     */
    private void requireDynamic() {
        check();
        if (motion != MotionType2D.DYNAMIC)
            throw new IllegalStateException("Operation requires a dynamic body");
    }

    /**
     * Requires motion properties before prescribing velocity.
     *
     * @throws IllegalStateException if the handle is invalid or static
     */
    private void requireMoving() {
        check();
        if (motion == MotionType2D.STATIC)
            throw new IllegalStateException("Static bodies have no velocity");
    }

    /**
     * Reports whether this handle has been invalidated; remains available after
     * its world closes, on the owning thread.
     *
     * @return whether the native body has been destroyed
     */
    public boolean isDestroyed() {
        world.checkOwner();
        return destroyed;
    }

    /**
     * Returns the immutable motion type for this body.
     *
     * @return static, kinematic, or dynamic motion
     */
    public MotionType2D getMotionType() {
        world.checkOwner();
        return motion;
    }

    /**
     * Reports whether rotation is locked while planar translation remains free.
     *
     * @return whether angular simulation is disabled
     */
    public boolean isFixedRotation() {
        world.checkOwner();
        return fixedRotation;
    }

    /**
     * Reports whether the body detects overlap without collision response.
     *
     * @return whether this body is a sensor
     */
    public boolean isSensor() {
        world.checkOwner();
        return sensor;
    }

    /**
     * Returns the last captured horizontal center.
     *
     * @return horizontal world position in meters
     */
    public float getX() {
        check();
        return x;
    }

    /**
     * Returns the last captured vertical center.
     *
     * @return vertical world position in meters
     */
    public float getY() {
        check();
        return y;
    }

    /**
     * Returns the last captured counterclockwise orientation.
     *
     * @return orientation around Z in radians, between -pi and pi
     */
    public float getRotation() {
        check();
        return angle;
    }

    /**
     * Reads the current captured center using body-owned reusable storage.
     * The first no-argument position read creates one Vector2f; later reads reuse
     * it. Call asPixels() immediately to obtain that vector in render coordinates.
     * The vector belongs to this body and subsequent no-argument reads overwrite
     * it, so copy its coordinates when retaining a snapshot across frames. Passing
     * a destination selects that vector for subsequent no-argument reads.
     * @return this body for getPosition().asPixels() chaining
     */
    public RigidBody2D getPosition() {
        check();
        if (positionDestination == null) positionDestination = new Vector2f();
        return getPosition(positionDestination);
    }

    /**
     * Reads the interpolated center using the same body-owned reusable storage.
     * The first no-argument read creates one Vector2f; subsequent reads allocate
     * nothing. Call asPixels() immediately to obtain interpolated render pixels.
     * @return this body for getInterpolatedPosition().asPixels() chaining
     */
    public RigidBody2D getInterpolatedPosition() {
        check();
        if (positionDestination == null) positionDestination = new Vector2f();
        return getInterpolatedPosition(positionDestination);
    }

    /**
     * Copies the current captured center into caller-owned storage.
     *
     * @param destination output vector, not null
     * @return this body for immediate asPixels() chaining; destination contains world meters
     */
    public RigidBody2D getPosition(Vector2f destination) {
        copyPosition(destination, false);
        positionDestination = destination;
        interpolatedPosition = false;
        return this;
    }

    /**
     * Converts the destination from the last position read into pixels without a
     * wrapper or temporary vector. Use immediately after getPosition(destination)
     * or getInterpolatedPosition(destination). A subsequent read replaces the
     * destination; use the chain before changing the simulation pose. Repeated
     * calls recompute from the captured body pose and never double-convert pixels.
     * @return the existing caller-owned destination containing render pixels
     * @throws IllegalStateException if no position has been read or the body is invalid
     */
    public Vector2f asPixels() {
        check();
        if (positionDestination == null) throw new IllegalStateException("Read a position before converting to pixels");
        if (!interpolatedPosition) return world.toScreen(x, y, positionDestination);
        float alpha = world.getInterpolationAlpha();
        return world.toScreen(previousX + (x - previousX) * alpha, previousY + (y - previousY) * alpha, positionDestination);
    }

    /**
     * Teleports from render pixels while retaining the native radian convention.
     * @param x horizontal pixel center
     * @param y vertical pixel center
     * @param radians counterclockwise world rotation in radians
     * @return this body
     */
    public RigidBody2D setTransformPixels(float x, float y, float radians) {
        check();
        return setTransform(world.worldX(x), world.worldY(y), radians);
    }

    /**
     * Blends captured centers using the world's frame interpolation fraction.
     *
     * @param destination output vector, not null
     * @return this body for immediate asPixels() chaining; destination contains interpolated meters
     */
    public RigidBody2D getInterpolatedPosition(Vector2f destination) {
        copyPosition(destination, true);
        positionDestination = destination;
        interpolatedPosition = true;
        return this;
    }

    /**
     * Copies a captured center without altering the caller's pending fluent read.
     * Internal geometry synchronization uses this path to preserve its destination.
     * @param destination reusable output vector, not null
     * @param interpolated whether to blend previous and current endpoints
     * @return the destination containing world meters
     */
    Vector2f copyPosition(Vector2f destination, boolean interpolated) {
        check();
        Objects.requireNonNull(destination, "destination");
        if (!interpolated) return destination.set(x, y);
        float alpha = world.getInterpolationAlpha();
        return destination.set(previousX + (x - previousX) * alpha, previousY + (y - previousY) * alpha);
    }

    /**
     * Blends captured orientations along the shortest angular arc.
     *
     * @return interpolated counterclockwise radians, possibly outside [-pi, pi]
     */
    public float getInterpolatedRotation() {
        check();
        float delta = angle - previousAngle;
        // Captured angles are normalized, so at most one turn needs removal.
        float shortest = delta;
        if (delta > Math.PI) shortest = (float) (delta - 2.0 * Math.PI);
        else if (delta < -Math.PI) shortest = (float) (delta + 2.0 * Math.PI);
        return previousAngle + shortest * world.getInterpolationAlpha();
    }

    /**
     * Teleports the center and orientation without changing velocity. Both
     * interpolation endpoints are reset. Moving bodies are awakened.
     *
     * @param x horizontal world position in meters
     * @param y vertical world position in meters
     * @param radians finite counterclockwise orientation around Z
     * @return this body
     */
    public RigidBody2D setTransform(float x, float y, float radians) {
        check();
        PhysicsValidation2D.finite(x, "x");
        PhysicsValidation2D.finite(y, "y");
        PhysicsValidation2D.finite(radians, "rotation");
        world.positionScratch.set(x, y, 0);
        float halfAngle = radians * 0.5f;
        world.rotationScratch.set(0, 0, (float) Math.sin(halfAngle), (float) Math.cos(halfAngle));
        world.bodies.setPositionAndRotation(id, world.positionScratch, world.rotationScratch,
                motion == MotionType2D.STATIC ? EActivation.DontActivate : EActivation.Activate);
        capture();
        remember();
        if (geometry != null) geometry.sync(this, false);
        return this;
    }

    /**
     * Reads current native linear velocity. Static bodies return zero.
     *
     * @param destination output vector, not null
     * @return the destination containing meters per second
     */
    public Vector2f getLinearVelocity(Vector2f destination) {
        check();
        Objects.requireNonNull(destination, "destination");
        if (motion == MotionType2D.STATIC) return destination.set(0, 0);
        world.bodies.getLinearVelocity(id, world.vectorScratch);
        return destination.set(world.vectorScratch.getX(), world.vectorScratch.getY());
    }

    /**
     * Prescribes planar linear velocity and wakes a moving body.
     *
     * @param x horizontal meters per second
     * @param y vertical meters per second
     * @return this body
     * @throws IllegalStateException if the body is static
     */
    public RigidBody2D setLinearVelocity(float x, float y) {
        requireMoving();
        PhysicsValidation2D.finite(x, "velocity x");
        PhysicsValidation2D.finite(y, "velocity y");
        world.bodies.setLinearVelocity(id, x, y, 0);
        world.bodies.activateBody(id);
        return this;
    }

    /**
     * Reads angular velocity around Z. Static bodies return zero.
     *
     * @return counterclockwise radians per second
     */
    public float getAngularVelocity() {
        check();
        if (motion == MotionType2D.STATIC) return 0;
        world.bodies.getAngularVelocity(id, world.vectorScratch);
        return world.vectorScratch.getZ();
    }

    /**
     * Prescribes angular velocity around Z and wakes the body.
     *
     * @param radiansPerSecond finite counterclockwise angular velocity
     * @return this body
     * @throws IllegalStateException if static or nonzero rotation is locked
     */
    public RigidBody2D setAngularVelocity(float radiansPerSecond) {
        requireMoving();
        PhysicsValidation2D.finite(radiansPerSecond, "angular velocity");
        if (fixedRotation && radiansPerSecond != 0)
            throw new IllegalStateException("Body rotation is fixed");
        world.vectorScratch.set(0, 0, radiansPerSecond);
        world.bodies.setAngularVelocity(id, world.vectorScratch);
        world.bodies.activateBody(id);
        return this;
    }

    /**
     * Moves a kinematic body toward a planar target over a specified duration
     * by prescribing native velocities. Simulation must still be stepped; this
     * method does not teleport or advance time. Call again as targets change.
     *
     * @param x target horizontal center in meters
     * @param y target vertical center in meters
     * @param radians target counterclockwise orientation
     * @param seconds finite positive duration in seconds
     * @return this body
     * @throws IllegalStateException if the body is not kinematic or a locked rotation changes
     */
    public RigidBody2D moveKinematic(float x, float y, float radians, float seconds) {
        check();
        if (motion != MotionType2D.KINEMATIC)
            throw new IllegalStateException("Operation requires a kinematic body");
        PhysicsValidation2D.finite(x, "x");
        PhysicsValidation2D.finite(y, "y");
        PhysicsValidation2D.finite(radians, "rotation");
        PhysicsValidation2D.positive(seconds, "duration");
        if (fixedRotation && Math.abs(Math.atan2(Math.sin(radians - angle), Math.cos(radians - angle))) > 0.000001)
            throw new IllegalStateException("Body rotation is fixed");
        world.positionScratch.set(x, y, 0);
        float halfAngle = (fixedRotation ? angle : radians) * 0.5f;
        world.rotationScratch.set(0, 0, (float) Math.sin(halfAngle), (float) Math.cos(halfAngle));
        world.bodies.moveKinematic(id, world.positionScratch, world.rotationScratch, seconds);
        return this;
    }

    /**
     * Adds a force at the center of mass and wakes a dynamic body. Jolt clears
     * accumulated force after the next native step, including an update's first
     * catch-up step; reapply once per direct physics step for sustained force.
     *
     * @param x horizontal force in Newtons
     * @param y vertical force in Newtons
     * @return this body
     */
    public RigidBody2D addForce(float x, float y) {
        requireDynamic();
        PhysicsValidation2D.finite(x, "force x");
        PhysicsValidation2D.finite(y, "force y");
        world.vectorScratch.set(x, y, 0);
        world.bodies.addForce(id, world.vectorScratch);
        return this;
    }

    /**
     * Adds an immediate center-of-mass impulse and wakes a dynamic body.
     *
     * @param x horizontal impulse in kilogram meters per second
     * @param y vertical impulse in kilogram meters per second
     * @return this body
     */
    public RigidBody2D addImpulse(float x, float y) {
        requireDynamic();
        PhysicsValidation2D.finite(x, "impulse x");
        PhysicsValidation2D.finite(y, "impulse y");
        world.vectorScratch.set(x, y, 0);
        world.bodies.addImpulse(id, world.vectorScratch);
        return this;
    }

    /**
     * Adds an impulse at a world point, producing both translation and angular
     * motion when rotation is enabled. The application point lies at Z=0.
     *
     * @param x horizontal impulse in kilogram meters per second
     * @param y vertical impulse in kilogram meters per second
     * @param pointX horizontal world application point in meters
     * @param pointY vertical world application point in meters
     * @return this body
     */
    public RigidBody2D addImpulseAt(float x, float y, float pointX, float pointY) {
        requireDynamic();
        PhysicsValidation2D.finite(x, "impulse x");
        PhysicsValidation2D.finite(y, "impulse y");
        PhysicsValidation2D.finite(pointX, "point x");
        PhysicsValidation2D.finite(pointY, "point y");
        world.vectorScratch.set(x, y, 0);
        world.positionScratch.set(pointX, pointY, 0);
        world.bodies.addImpulse(id, world.vectorScratch, world.positionScratch);
        return this;
    }

    /**
     * Adds counterclockwise torque for the next native step. A fixed-rotation
     * dynamic body accepts the operation but does not acquire angular motion.
     *
     * @param newtonMeters finite torque around Z
     * @return this body
     */
    public RigidBody2D addTorque(float newtonMeters) {
        requireDynamic();
        PhysicsValidation2D.finite(newtonMeters, "torque");
        world.vectorScratch.set(0, 0, newtonMeters);
        world.bodies.addTorque(id, world.vectorScratch);
        return this;
    }

    /**
     * Adds immediate angular momentum around Z to a dynamic body. Fixed
     * rotation prevents angular motion even when an impulse is applied.
     *
     * @param impulse finite angular impulse in kilogram square meters per second
     * @return this body
     */
    public RigidBody2D addAngularImpulse(float impulse) {
        requireDynamic();
        PhysicsValidation2D.finite(impulse, "angular impulse");
        world.vectorScratch.set(0, 0, impulse);
        world.bodies.addAngularImpulse(id, world.vectorScratch);
        return this;
    }

    /**
     * Reports whether the body is currently active in simulation.
     *
     * @return false for static or sleeping bodies
     */
    public boolean isActive() {
        check();
        return world.bodies.isActive(id);
    }

    /**
     * Wakes a moving body, for example after changing world gravity. Static
     * bodies remain inactive.
     *
     * @return this body
     */
    public RigidBody2D activate() {
        check();
        if (motion != MotionType2D.STATIC) world.bodies.activateBody(id);
        return this;
    }

    /**
     * Reads both native pose components with one binding call into shared
     * scratch values. Called only by trusted owner-thread simulation code.
     */
    void capture() {
        world.bodies.getPositionAndRotation(id, world.positionScratch, world.rotationScratch);
        x = world.positionScratch.x();
        y = world.positionScratch.y();
        float z = world.rotationScratch.getZ();
        float w = world.rotationScratch.getW();
        angle = (float) Math.atan2(2.0 * z * w, 1.0 - 2.0 * z * z);
    }

    /**
     * Advances interpolation endpoints after a successful native update. Static
     * poses change only through teleportation, which already resets both endpoints.
     * Called only by trusted simulation code on the owning thread.
     *
     * @param moving whether bodies were active during this or the previous step
     */
    void captureStep(boolean moving) {
        if (motion == MotionType2D.STATIC) return;
        remember();
        if (moving) capture();
    }

    /**
     * Advances a moving body's interpolation endpoints from a native batch read.
     *
     * @param positionX horizontal center in meters
     * @param positionY vertical center in meters
     * @param rotationZ native quaternion Z component
     * @param rotationW native quaternion W component
     */
    void captureStep(double positionX, double positionY, float rotationZ, float rotationW) {
        remember();
        x = (float) positionX;
        y = (float) positionY;
        angle = (float) Math.atan2(2.0 * rotationZ * rotationW, 1.0 - 2.0 * rotationZ * rotationZ);
    }

    /**
     * Commits the current captured pose as the previous interpolation endpoint.
     */
    void remember() {
        previousX = x;
        previousY = y;
        previousAngle = angle;
    }

    /**
     * Tests world-space containment using the latest captured native pose.
     *
     * @param pointX horizontal world point in meters
     * @param pointY vertical world point in meters
     * @return whether the point lies within the supported planar geometry
     */
    boolean containsPoint(float pointX, float pointY) {
        double dx = (double) pointX - x;
        double dy = (double) pointY - y;
        return shape.contains(dx, dy, angle);
    }

    @Override
    public void close() {
        world.destroyBody(this);
    }
}
