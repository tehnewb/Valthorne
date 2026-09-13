package valthorne.math.physics;

import org.teavm.jso.JSObject;

public final class DistanceJoint3D implements AutoCloseable {
    final PhysicsWorld3D world;
    final RigidBody3D a, b;
    final JSObject constraint;
    boolean destroyed;

    DistanceJoint3D(PhysicsWorld3D world, RigidBody3D a, RigidBody3D b, JSObject constraint) {
        this.world = world;
        this.a = a;
        this.b = b;
        this.constraint = constraint;
    }

    public boolean isDestroyed() {return destroyed;}

    @Override
    public void close() {if (!destroyed) world.destroyJoint(this);}
}
