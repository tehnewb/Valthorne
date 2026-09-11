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
 * World-owned body handle. All operations run on the world's owner thread. Closing destroys the body.
 */
public final class RigidBody3D implements AutoCloseable {
    final PhysicsWorld3D world;
    final int id;
    final MotionType3D motion;
    final int layer;
    final boolean sensor;
    final Vector3f previousPosition = new Vector3f(), currentPosition = new Vector3f();
    final Quaternionf previousRotation = new Quaternionf(), currentRotation = new Quaternionf();
    private final RVec3 nativePosition = new RVec3();
    private final Quat nativeRotation = new Quat();
    private Vec3 nativeVector;
    private Vector3f interpolatedPosition;
    private Quaternionf interpolatedRotation;
    boolean destroyed;
    private ModelInstance3D model;
    private Object userData;

    RigidBody3D(PhysicsWorld3D world, int id, BodySettings3D settings) {
        this.world = world;
        this.id = id;
        motion = settings.motion;
        layer = settings.layer;
        sensor = settings.sensor;
        capture();
        remember();
    }

    void check() {
        world.check();
        if (destroyed) throw new IllegalStateException("Rigid body has been destroyed");
    }

    private void dynamic() {
        check();
        if (motion != MotionType3D.DYNAMIC) throw new IllegalStateException("Operation requires a dynamic body");
    }

    public int getId() {return id;}

    public MotionType3D getMotionType() {return motion;}

    public int getLayer() {return layer;}

    public boolean isSensor() {return sensor;}

    public boolean isDestroyed() {return destroyed;}

    public Object getUserData() {return userData;}

    public RigidBody3D setUserData(Object data) {
        check();
        userData = data;
        return this;
    }

    public Vector3f getPosition() {
        return getPosition(new Vector3f());
    }

    /** Copies the last captured world position into caller-owned storage. */
    public Vector3f getPosition(Vector3f destination) {
        check();
        return destination.set(currentPosition);
    }

    public Quaternionf getRotation() {
        return getRotation(new Quaternionf());
    }

    /** Copies the last captured orientation into caller-owned storage. */
    public Quaternionf getRotation(Quaternionf destination) {
        check();
        return destination.set(currentRotation);
    }

    public Vector3f getLinearVelocity() {
        return getLinearVelocity(new Vector3f());
    }

    /** Reads native linear velocity without allocating a destination vector. */
    public Vector3f getLinearVelocity(Vector3f destination) {
        check();
        java.util.Objects.requireNonNull(destination, "destination");
        Vec3 scratch = nativeVector();
        world.bodies.getLinearVelocity(id, scratch);
        return destination.set(scratch.getX(), scratch.getY(), scratch.getZ());
    }

    public RigidBody3D setLinearVelocity(Vector3f velocity) {
        java.util.Objects.requireNonNull(velocity, "velocity");
        return setLinearVelocity(velocity.x(), velocity.y(), velocity.z());
    }

    public Vector3f getAngularVelocity() {
        return getAngularVelocity(new Vector3f());
    }

    /** Reads native angular velocity into caller-owned storage. */
    public Vector3f getAngularVelocity(Vector3f destination) {
        check();
        java.util.Objects.requireNonNull(destination, "destination");
        Vec3 scratch = nativeVector();
        world.bodies.getAngularVelocity(id, scratch);
        return destination.set(scratch.getX(), scratch.getY(), scratch.getZ());
    }

    public RigidBody3D setAngularVelocity(Vector3f velocity) {
        check();
        if (motion == MotionType3D.STATIC) throw new IllegalStateException("Static bodies have no velocity");
        world.bodies.setAngularVelocity(id, PhysicsMath3D.vector(velocity));
        activate();
        return this;
    }

    public boolean isActive() {
        check();
        return world.bodies.isActive(id);
    }

    public RigidBody3D activate() {
        check();
        if (motion != MotionType3D.STATIC) world.bodies.activateBody(id);
        return this;
    }

    public RigidBody3D sleep() {
        dynamic();
        world.bodies.deactivateBody(id);
        return this;
    }

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

    public RigidBody3D addImpulse(Vector3f impulse) {
        dynamic();
        world.bodies.addImpulse(id, PhysicsMath3D.vector(impulse));
        return this;
    }

    public RigidBody3D addImpulse(Vector3f impulse, Vector3f worldPoint) {
        dynamic();
        world.bodies.addImpulse(id, PhysicsMath3D.vector(impulse), PhysicsMath3D.position(worldPoint));
        return this;
    }

    public RigidBody3D addAngularImpulse(Vector3f impulse) {
        dynamic();
        world.bodies.addAngularImpulse(id, PhysicsMath3D.vector(impulse));
        return this;
    }

    /**
     * Adds force for the next simulation step. For sustained force, call from a before-step listener.
     */
    public RigidBody3D addForce(Vector3f force) {
        dynamic();
        PhysicsMath3D.check(force);
        Vec3 scratch = nativeVector();
        scratch.set(force.x(), force.y(), force.z());
        world.bodies.addForce(id, scratch);
        return this;
    }

    private Vec3 nativeVector() {
        if (nativeVector == null) nativeVector = new Vec3();
        return nativeVector;
    }

    public RigidBody3D addTorque(Vector3f torque) {
        dynamic();
        world.bodies.addTorque(id, PhysicsMath3D.vector(torque));
        return this;
    }

    /**
     * Teleports and resets visual interpolation. Dynamic velocity is retained.
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
     * Moves toward a target during the next fixed step. Call once in a before-step listener.
     */
    public RigidBody3D moveKinematic(Vector3f position, Quaternionf rotation) {
        check();
        if (motion != MotionType3D.KINEMATIC) throw new IllegalStateException("Body is not kinematic");
        world.bodies.moveKinematic(id, PhysicsMath3D.position(position), PhysicsMath3D.rotation(rotation), world.getFixedTimeStep());
        return this;
    }

    /**
     * Binds a borrowed model in world space. Clears its parent transform and preserves its visual scale.
     */
    public RigidBody3D bind(ModelInstance3D model) {
        check();
        this.model = java.util.Objects.requireNonNull(model);
        model.setParentTransform(new Matrix4f());
        sync(1);
        return this;
    }

    public void unbind() {
        check();
        model = null;
    }

    void remember() {
        previousPosition.set(currentPosition);
        previousRotation.set(currentRotation);
    }

    void capture() {
        world.bodies.getPositionAndRotation(id, nativePosition, nativeRotation);
        currentPosition.set((float) nativePosition.xx(), (float) nativePosition.yy(), (float) nativePosition.zz());
        PhysicsMath3D.normalizeRotation(currentRotation, nativeRotation.getX(), nativeRotation.getY(), nativeRotation.getZ(), nativeRotation.getW());
    }

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

    void invalidate() {
        destroyed = true;
        model = null;
    }

    @Override
    public void close() {if (!destroyed) world.destroyBody(this);}
}
