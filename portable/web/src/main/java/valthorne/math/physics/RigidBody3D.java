package valthorne.math.physics;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.teavm.jso.typedarrays.Float32Array;
import valthorne.graphics.model.ModelInstance3D;
import java.util.Objects;

/** Browser implementation of the existing owned Jolt body handle. */
public final class RigidBody3D implements AutoCloseable {
    final PhysicsWorld3D world;
    final int id,layer;
    final MotionType3D motion;
    final boolean sensor;
    boolean destroyed;
    private Object userData;
    private ModelInstance3D model;
    private final Vector3f currentPosition=new Vector3f(),previousPosition=new Vector3f();
    private final Quaternionf currentRotation=new Quaternionf(),previousRotation=new Quaternionf();
    private Vector3f interpolatedPosition;
    private Quaternionf interpolatedRotation;
    RigidBody3D(PhysicsWorld3D world,int id,BodySettings3D settings){this.world=world;this.id=id;motion=settings.motion;layer=settings.layer;sensor=settings.sensor;capture();remember();}
    void check(){world.check();if(destroyed)throw new IllegalStateException("Rigid body has been destroyed");}
    private void dynamic(){check();if(motion!=MotionType3D.DYNAMIC)throw new IllegalStateException("Operation requires a dynamic body");}
    private void moving(){check();if(motion==MotionType3D.STATIC)throw new IllegalStateException("Static bodies have no velocity");}
    public int getId(){return id;}
    public MotionType3D getMotionType(){return motion;}
    public int getLayer(){return layer;}
    public boolean isSensor(){return sensor;}
    public boolean isDestroyed(){return destroyed;}
    public Object getUserData(){return userData;}
    public RigidBody3D setUserData(Object data){check();userData=data;return this;}
    public Vector3f getPosition(){return getPosition(new Vector3f());}
    public Vector3f getPosition(Vector3f destination){check();return destination.set(currentPosition);}
    public Quaternionf getRotation(){return getRotation(new Quaternionf());}
    public Quaternionf getRotation(Quaternionf destination){check();return destination.set(currentRotation);}
    public Vector3f getLinearVelocity(){return getLinearVelocity(new Vector3f());}
    public Vector3f getLinearVelocity(Vector3f destination){return velocity(destination,1);}
    public Vector3f getAngularVelocity(){return getAngularVelocity(new Vector3f());}
    public Vector3f getAngularVelocity(Vector3f destination){return velocity(destination,2);}
    private Vector3f velocity(Vector3f destination,int type){check();Objects.requireNonNull(destination);Float32Array v=world.bodies.read(id,type);return destination.set(v.get(0),v.get(1),v.get(2));}
    public RigidBody3D setLinearVelocity(Vector3f velocity){return setLinearVelocity(velocity.x,velocity.y,velocity.z);}
    public RigidBody3D setLinearVelocity(float x,float y,float z){moving();PhysicsMath3D.finite(x,"x");PhysicsMath3D.finite(y,"y");PhysicsMath3D.finite(z,"z");world.bodies.change(id,0,x,y,z,0,0,0);return this;}
    public RigidBody3D setAngularVelocity(Vector3f velocity){moving();world.bodies.change(id,1,PhysicsMath3D.check(velocity));return this;}
    public boolean isActive(){check();return world.bodies.isActive(id);}
    public RigidBody3D activate(){check();if(motion!=MotionType3D.STATIC)world.bodies.activateBody(id);return this;}
    public RigidBody3D sleep(){dynamic();world.bodies.deactivateBody(id);return this;}
    public RigidBody3D addImpulse(Vector3f impulse){return change(2,impulse);}
    public RigidBody3D addImpulse(Vector3f impulse,Vector3f worldPoint){dynamic();PhysicsMath3D.check(impulse);PhysicsMath3D.check(worldPoint);world.bodies.change(id,3,impulse.x,impulse.y,impulse.z,worldPoint.x,worldPoint.y,worldPoint.z);return this;}
    public RigidBody3D addAngularImpulse(Vector3f impulse){return change(4,impulse);}
    public RigidBody3D addForce(Vector3f force){return change(5,force);}
    public RigidBody3D addTorque(Vector3f torque){return change(6,torque);}
    private RigidBody3D change(int operation,Vector3f value){dynamic();world.bodies.change(id,operation,PhysicsMath3D.check(value));return this;}
    public RigidBody3D setTransform(Vector3f position,Quaternionf rotation){check();world.bodies.pose(id,PhysicsMath3D.check(position),PhysicsMath3D.normalizeRotation(rotation,new Quaternionf()),0);capture();remember();sync(1);return this;}
    public RigidBody3D moveKinematic(Vector3f position,Quaternionf rotation){check();if(motion!=MotionType3D.KINEMATIC)throw new IllegalStateException("Body is not kinematic");world.bodies.pose(id,PhysicsMath3D.check(position),PhysicsMath3D.normalizeRotation(rotation,new Quaternionf()),world.getFixedTimeStep());return this;}
    public RigidBody3D bind(ModelInstance3D model){check();this.model=Objects.requireNonNull(model);model.setParentTransform(new Matrix4f());sync(1);return this;}
    public void unbind(){check();model=null;}
    void remember(){previousPosition.set(currentPosition);previousRotation.set(currentRotation);}
    void capture(){Float32Array v=world.bodies.read(id,0);currentPosition.set(v.get(0),v.get(1),v.get(2));PhysicsMath3D.normalizeRotation(currentRotation,v.get(3),v.get(4),v.get(5),v.get(6));}
    void sync(float alpha){
        if(model==null)return;
        if(alpha==1){model.setPosition(currentPosition).setRotation(currentRotation);return;}
        if(interpolatedPosition==null){interpolatedPosition=new Vector3f();interpolatedRotation=new Quaternionf();}
        model.setPosition(interpolatedPosition.set(previousPosition).lerp(currentPosition,alpha))
            .setRotation(interpolatedRotation.set(previousRotation).slerp(currentRotation,alpha));
    }
    void invalidate(){destroyed=true;model=null;}
    @Override public void close(){if(!destroyed)world.destroyBody(this);}
}
