package valthorne.math.physics;

import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.Vec3;
import com.github.stephengold.joltjni.enumerate.EActivation;
import valthorne.graphics.model.ModelInstance3D;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * A handle to a native rigid body owned by one {@link PhysicsWorld3D}. The world
 * controls creation, stepping, and destruction; closing this handle also destroys
 * joints attached to it. Physics operations must run on the world's creating
 * thread while the world and body are alive, outside the native update.
 * <p>
 * Pose getters copy the last captured simulation pose, whereas velocity getters
 * read native state. A borrowed model may be bound for automatic visual pose
 * synchronization; interpolation affects the model only, never the physical body.
 * World positions, forces, and velocities use the world's consistent unit system,
 * with Z pointing up. Returned position and rotation values are independent copies.
 * Metadata getters remain readable after destruction but are not synchronization
 * primitives.
 * </p>
 * @author Albert Beaupre
 */
public final class RigidBody3D implements AutoCloseable {
    final PhysicsWorld3D world; // Owning world that controls this native body's lifetime.
    final int id; // Native body identifier retained after destruction for metadata access.
    final MotionType3D motion; // Immutable motion category copied at creation.
    final int layer; // Application collision-layer index before native encoding.
    final boolean sensor; // Creation-time contact sensor flag.
    final Vector3f previousPosition = new Vector3f(), currentPosition = new Vector3f(); // Previous and current captured world positions for visual interpolation.
    final Quaternionf previousRotation = new Quaternionf(), currentRotation = new Quaternionf(); // Previous and current normalized orientations for visual interpolation.
    private final RVec3 nativePosition = new RVec3(); // Reusable native position destination for pose capture.
    private final Quat nativeRotation = new Quat(); // Reusable native quaternion destination for pose capture.
    private Vec3 nativeVector; // Lazily allocated native vector scratch for velocities and forces.
    private Vector3f interpolatedPosition; // Lazily allocated interpolated position scratch.
    private Quaternionf interpolatedRotation; // Lazily allocated interpolated orientation scratch.
    boolean destroyed; // Whether native destruction has invalidated this handle.
    private ModelInstance3D model; // Borrowed model whose visual pose follows this body, or null.
    private Object userData; // Borrowed application attachment retained until replaced or the handle is collected.

    /**
     * Wraps a newly added native body and captures identical previous/current poses,
     * preventing interpolation from an unrelated initial pose. Called by the owning
     * world after successful native allocation.
     *
     * @param world owning world
     * @param id allocated native body ID
     * @param settings creation settings supplying immutable handle metadata
     */
    RigidBody3D(PhysicsWorld3D world, int id, BodySettings3D settings) {
        this.world = world;
        this.id = id;
        motion = settings.motion;
        layer = settings.layer;
        sensor = settings.sensor;
        capture();
        remember();
    }

    /**
     * Enforces the owning world's thread, lifetime, and native-update restrictions,
     * then checks that this body has not been destroyed.
     *
     * @throws IllegalStateException if world access is prohibited or the body is destroyed
     */
    void check() {
        world.check();
        if (destroyed) throw new IllegalStateException("Rigid body has been destroyed");
    }

    /**
     * Checks legal access and requires dynamic motion for force, impulse, and sleep
     * operations.
     *
     * @throws IllegalStateException if access is prohibited or motion is not dynamic
     */
    private void dynamic() {
        check();
        if (motion != MotionType3D.DYNAMIC) throw new IllegalStateException("Operation requires a dynamic body");
    }

    /**
     * Returns the native ID assigned at creation. It remains available after
     * destruction and must not then be used to access native body state.
     *
     * @return body ID
     */
    public int getId() {return id;}

    /**
     * Returns the motion category copied from creation settings. This metadata
     * getter does not query native state.
     *
     * @return static, kinematic, or dynamic motion type
     */
    public MotionType3D getMotionType() {return motion;}

    /**
     * Returns the application collision-layer index, before the world's internal
     * static/moving layer encoding.
     *
     * @return creation-time collision layer
     */
    public int getLayer() {return layer;}

    /**
     * Reports whether this body was created as a contact sensor.
     *
     * @return creation-time sensor flag
     */
    public boolean isSensor() {return sensor;}

    /**
     * Reports whether the world has invalidated this handle. Reading the flag does
     * not establish thread safety for other body operations.
     *
     * @return true after body destruction
     */
    public boolean isDestroyed() {return destroyed;}

    /**
     * Returns the application attachment without copying it or checking body
     * lifetime. The attachment remains stored after destruction.
     *
     * @return borrowed user value, possibly null
     */
    public Object getUserData() {return userData;}

    /**
     * Stores an application attachment without taking ownership. Replacing it does
     * not release the previous value.
     *
     * @param data arbitrary value, or null to clear it
     * @return this body
     * @throws IllegalStateException if world access is prohibited or this body is destroyed
     */
    public RigidBody3D setUserData(Object data) {
        check();
        userData = data;
        return this;
    }

    /**
     * Allocates and returns a copy of the last captured world position. Model
     * interpolation and changes to the returned vector do not change the physical pose.
     *
     * @return independent position vector
     * @throws IllegalStateException if world access is prohibited or this body is destroyed
     */
    public Vector3f getPosition() {
        return getPosition(new Vector3f());
    }

    /**
     * Copies the last captured world position into caller storage without a native
     * pose query.
     *
     * @param destination nonnull output vector
     * @return destination
     * @throws NullPointerException if destination is null
     * @throws IllegalStateException if world access is prohibited or this body is destroyed
     */
    public Vector3f getPosition(Vector3f destination) {
        check();
        return destination.set(currentPosition);
    }

    /**
     * Allocates and returns the last captured normalized world orientation.
     * Modifying the result does not rotate the body.
     *
     * @return independent orientation quaternion
     * @throws IllegalStateException if world access is prohibited or this body is destroyed
     */
    public Quaternionf getRotation() {
        return getRotation(new Quaternionf());
    }

    /**
     * Copies the last captured normalized world orientation into caller storage.
     *
     * @param destination nonnull output quaternion
     * @return destination
     * @throws NullPointerException if destination is null
     * @throws IllegalStateException if world access is prohibited or this body is destroyed
     */
    public Quaternionf getRotation(Quaternionf destination) {
        check();
        return destination.set(currentRotation);
    }

    /**
     * Reads current native linear velocity into a newly allocated vector.
     *
     * @return independent world-space velocity in distance units per second
     * @throws IllegalStateException if world access is prohibited or this body is destroyed
     */
    public Vector3f getLinearVelocity() {
        return getLinearVelocity(new Vector3f());
    }

    /**
     * Reads native linear velocity using reusable native scratch storage and writes
     * the result into the caller's vector.
     *
     * @param destination nonnull output vector
     * @return destination, in world distance units per second
     * @throws NullPointerException if destination is null
     * @throws IllegalStateException if world access is prohibited or this body is destroyed
     */
    public Vector3f getLinearVelocity(Vector3f destination) {
        check();
        java.util.Objects.requireNonNull(destination, "destination");
        Vec3 scratch = nativeVector();
        world.bodies.getLinearVelocity(id, scratch);
        return destination.set(scratch.getX(), scratch.getY(), scratch.getZ());
    }

    /**
     * Copies a world-space linear velocity into a nonstatic body and activates it.
     *
     * @param velocity finite velocity in distance units per second
     * @return this body
     * @throws NullPointerException if velocity is null
     * @throws IllegalArgumentException if a component is nonfinite
     * @throws IllegalStateException if world access is prohibited or this body is destroyed or static
     */
    public RigidBody3D setLinearVelocity(Vector3f velocity) {
        java.util.Objects.requireNonNull(velocity, "velocity");
        return setLinearVelocity(velocity.x(), velocity.y(), velocity.z());
    }

    /**
     * Reads current native angular velocity into a new vector. Its direction is the
     * world rotation axis and its magnitude is angular speed.
     *
     * @return independent angular velocity in radians per second
     * @throws IllegalStateException if world access is prohibited or this body is destroyed
     */
    public Vector3f getAngularVelocity() {
        return getAngularVelocity(new Vector3f());
    }

    /**
     * Reads native angular velocity into caller storage using reusable native scratch.
     *
     * @param destination nonnull output vector
     * @return destination, containing world-axis angular velocity in radians per second
     * @throws NullPointerException if destination is null
     * @throws IllegalStateException if world access is prohibited or this body is destroyed
     */
    public Vector3f getAngularVelocity(Vector3f destination) {
        check();
        java.util.Objects.requireNonNull(destination, "destination");
        Vec3 scratch = nativeVector();
        world.bodies.getAngularVelocity(id, scratch);
        return destination.set(scratch.getX(), scratch.getY(), scratch.getZ());
    }

    /**
     * Copies angular velocity into a nonstatic body and activates it.
     *
     * @param velocity finite world-axis angular velocity in radians per second
     * @return this body
     * @throws NullPointerException if velocity is null
     * @throws IllegalArgumentException if a component is nonfinite
     * @throws IllegalStateException if world access is prohibited or this body is destroyed or static
     */
    public RigidBody3D setAngularVelocity(Vector3f velocity) {
        check();
        if (motion == MotionType3D.STATIC) throw new IllegalStateException("Static bodies have no velocity");
        world.bodies.setAngularVelocity(id, PhysicsMath3D.vector(velocity));
        activate();
        return this;
    }

    /**
     * Queries whether the native body currently participates as an active body.
     * This reflects simulation activation, independently of any bound model visibility.
     *
     * @return native activation state
     * @throws IllegalStateException if world access is prohibited or this body is destroyed
     */
    public boolean isActive() {
        check();
        return world.bodies.isActive(id);
    }

    /**
     * Requests activation for dynamic or kinematic motion. Static bodies are left
     * unchanged after validating access.
     *
     * @return this body
     * @throws IllegalStateException if world access is prohibited or this body is destroyed
     */
    public RigidBody3D activate() {
        check();
        if (motion != MotionType3D.STATIC) world.bodies.activateBody(id);
        return this;
    }

    /**
     * Deactivates a dynamic body immediately through the native body interface.
     * Later interactions or explicit activation may wake it again.
     *
     * @return this body
     * @throws IllegalStateException if world access is prohibited or this body is destroyed or nondynamic
     */
    public RigidBody3D sleep() {
        dynamic();
        world.bodies.deactivateBody(id);
        return this;
    }

    /**
     * Sets a nonstatic body's world linear velocity and activates it. Components
     * are copied directly after finite-value validation.
     *
     * @param x X velocity in distance units per second
     * @param y Y velocity in distance units per second
     * @param z Z velocity in distance units per second
     * @return this body
     * @throws IllegalArgumentException if a component is nonfinite
     * @throws IllegalStateException if world access is prohibited or this body is destroyed or static
     */
    public RigidBody3D setLinearVelocity(float x, float y, float z) {
        check();
        if (motion == MotionType3D.STATIC) throw new IllegalStateException("Static bodies have no velocity");
        PhysicsMath3D.finite(x, "x");
        PhysicsMath3D.finite(y, "y");
        PhysicsMath3D.finite(z, "z");
        world.bodies.setLinearVelocity(id, x, y, z);
        activate();
        return this;
    }

    /**
     * Applies an instantaneous linear impulse at the dynamic body's center of mass.
     * The resulting velocity change depends on body mass; this is not a force that
     * must be integrated over a timestep.
     *
     * @param impulse finite world-space momentum change
     * @return this body
     * @throws NullPointerException if impulse is null
     * @throws IllegalArgumentException if a component is nonfinite
     * @throws IllegalStateException if world access is prohibited or this body is destroyed or nondynamic
     */
    public RigidBody3D addImpulse(Vector3f impulse) {
        dynamic();
        world.bodies.addImpulse(id, PhysicsMath3D.vector(impulse));
        return this;
    }

    /**
     * Applies a world-space impulse at a world point. An offset from the center of
     * mass can also change angular velocity through the body's inertia.
     *
     * @param impulse finite world-space linear impulse
     * @param worldPoint finite world-space application point
     * @return this body
     * @throws NullPointerException if either vector is null
     * @throws IllegalArgumentException if a component is nonfinite
     * @throws IllegalStateException if world access is prohibited or this body is destroyed or nondynamic
     */
    public RigidBody3D addImpulse(Vector3f impulse, Vector3f worldPoint) {
        dynamic();
        world.bodies.addImpulse(id, PhysicsMath3D.vector(impulse), PhysicsMath3D.position(worldPoint));
        return this;
    }

    /**
     * Applies an instantaneous angular impulse to a dynamic body. Native inertia
     * determines the resulting change in angular velocity.
     *
     * @param impulse finite world-space angular impulse
     * @return this body
     * @throws NullPointerException if impulse is null
     * @throws IllegalArgumentException if a component is nonfinite
     * @throws IllegalStateException if world access is prohibited or this body is destroyed or nondynamic
     */
    public RigidBody3D addAngularImpulse(Vector3f impulse) {
        dynamic();
        world.bodies.addAngularImpulse(id, PhysicsMath3D.vector(impulse));
        return this;
    }

    /**
     * Accumulates a center-of-mass force for the next simulation step. For sustained
     * acceleration, apply it from a before-step listener so each fixed step receives
     * the force, including frames that perform multiple substeps.
     *
     * @param force finite world-space force in mass times distance per second squared
     * @return this body
     * @throws NullPointerException if force is null
     * @throws IllegalArgumentException if a component is nonfinite
     * @throws IllegalStateException if world access is prohibited or this body is destroyed or nondynamic
     */
    public RigidBody3D addForce(Vector3f force) {
        dynamic();
        PhysicsMath3D.check(force);
        Vec3 scratch = nativeVector();
        scratch.set(force.x(), force.y(), force.z());
        world.bodies.addForce(id, scratch);
        return this;
    }

    /**
     * Lazily obtains reusable native vector scratch for velocity reads and force
     * submission. Calls must remain confined to the owner thread.
     *
     * @return this body's scratch vector
     */
    private Vec3 nativeVector() {
        if (nativeVector == null) nativeVector = new Vec3();
        return nativeVector;
    }

    /**
     * Accumulates torque for a dynamic body's next simulation step. Reapply it in
     * each before-step callback for sustained rotational acceleration.
     *
     * @param torque finite world-space torque
     * @return this body
     * @throws NullPointerException if torque is null
     * @throws IllegalArgumentException if a component is nonfinite
     * @throws IllegalStateException if world access is prohibited or this body is destroyed or nondynamic
     */
    public RigidBody3D addTorque(Vector3f torque) {
        dynamic();
        world.bodies.addTorque(id, PhysicsMath3D.vector(torque));
        return this;
    }

    /**
     * Teleports the native body and resets both captured poses to the new transform,
     * then immediately synchronizes a bound model. Nonstatic bodies are activated;
     * existing velocity is retained. The supplied quaternion is normalized during
     * conversion without modifying the caller's value.
     *
     * @param position finite world position
     * @param rotation finite, nonzero world orientation
     * @return this body
     * @throws NullPointerException if either argument is null
     * @throws IllegalArgumentException if position or orientation is invalid
     * @throws IllegalStateException if world access is prohibited or this body is destroyed
     */
    public RigidBody3D setTransform(Vector3f position, Quaternionf rotation) {
        check();
        world.bodies.setPositionAndRotation(id, PhysicsMath3D.position(position), PhysicsMath3D.rotation(rotation),
                motion == MotionType3D.STATIC ? EActivation.DontActivate : EActivation.Activate);
        capture();
        remember();
        sync(1);
        return this;
    }

    /**
     * Requests velocity-driven movement of a kinematic body toward a target over one
     * fixed timestep. Call once from a before-step listener. Captured pose and bound
     * model are updated when the simulation advances, rather than immediately.
     *
     * @param position finite target world position
     * @param rotation finite, nonzero target world orientation
     * @return this body
     * @throws NullPointerException if either argument is null
     * @throws IllegalArgumentException if position or orientation is invalid
     * @throws IllegalStateException if world access is prohibited or this body is destroyed or not kinematic
     */
    public RigidBody3D moveKinematic(Vector3f position, Quaternionf rotation) {
        check();
        if (motion != MotionType3D.KINEMATIC) throw new IllegalStateException("Body is not kinematic");
        world.bodies.moveKinematic(id, PhysicsMath3D.position(position), PhysicsMath3D.rotation(rotation), world.getFixedTimeStep());
        return this;
    }

    /**
     * Binds a borrowed model, clears its parent transform to identity, and immediately
     * copies the current physical pose. Preserves visual scale. Later world updates
     * or explicit model synchronization overwrite the model's position and rotation.
     * Replacing a binding does not dispose or restore the previous model.
     *
     * @param model nonnull visual instance
     * @return this body
     * @throws NullPointerException if model is null
     * @throws IllegalStateException if world access is prohibited or this body is destroyed
     */
    public RigidBody3D bind(ModelInstance3D model) {
        check();
        this.model = java.util.Objects.requireNonNull(model);
        model.setParentTransform(new Matrix4f());
        sync(1);
        return this;
    }

    /**
     * Stops visual synchronization without modifying or disposing the previously
     * bound model. Its last synchronized transform remains in place.
     *
     * @throws IllegalStateException if world access is prohibited or this body is destroyed
     */
    public void unbind() {
        check();
        model = null;
    }

    /**
     * Copies the captured current pose into previous-pose storage before a fixed
     * step or after a teleport. The owning world is responsible for legal access.
     */
    void remember() {
        previousPosition.set(currentPosition);
        previousRotation.set(currentRotation);
    }

    /**
     * Reads native position and orientation into current-pose storage. Position
     * components are narrowed to float precision and the orientation is normalized.
     * The owning world calls this outside native integration.
     *
     * @throws IllegalArgumentException if native orientation is nonfinite or zero
     */
    void capture() {
        world.bodies.getPositionAndRotation(id, nativePosition, nativeRotation);
        currentPosition.set((float) nativePosition.xx(), (float) nativePosition.yy(), (float) nativePosition.zz());
        PhysicsMath3D.normalizeRotation(currentRotation, nativeRotation.getX(), nativeRotation.getY(), nativeRotation.getZ(), nativeRotation.getW());
    }

    /**
     * Writes a bound model's visual pose, doing nothing when no model is attached.
     * Alpha one copies the current pose directly; other values linearly interpolate
     * position and spherically interpolate orientation using lazily allocated scratch.
     * The caller supplies the interpolation fraction; it is not clamped here.
     *
     * @param alpha interpolation fraction, normally between zero and one
     */
    void sync(float alpha) {
        if (model == null) return;
        if (alpha == 1f) {
            model.setPosition(currentPosition).setRotation(currentRotation);
        } else {
            if (interpolatedPosition == null) {
                interpolatedPosition = new Vector3f();
                interpolatedRotation = new Quaternionf();
            }
            model.setPosition(interpolatedPosition.set(previousPosition).lerp(currentPosition, alpha))
                    .setRotation(interpolatedRotation.set(previousRotation).slerp(currentRotation, alpha));
        }
    }

    /**
     * Marks the handle destroyed and drops its model reference after native removal.
     * Keeps metadata, captured poses, and user data stored for diagnostic access.
     */
    void invalidate() {
        destroyed = true;
        model = null;
    }

    /**
     * Asks the owning world to destroy this body and its attached joints. Repeated
     * calls after invalidation do nothing, including after the world is closed.
     * Does not dispose a formerly bound model.
     *
     * @throws IllegalStateException if a live body is closed from an invalid world access context
     */
    @Override
    public void close() {if (!destroyed) world.destroyBody(this);}
}
