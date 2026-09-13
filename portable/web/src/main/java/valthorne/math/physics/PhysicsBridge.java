package valthorne.math.physics;

import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.teavm.jso.*;
import org.teavm.jso.typedarrays.Float32Array;

/** Internal bridge; public game code never imports browser types. */
final class PhysicsBridge {
    private final JSObject handle;
    @JSFunctor interface Contact extends JSObject {void accept(int type,int a,int b,int sa,int sb,float x,float y,float z,float depth);}
    PhysicsBridge(PhysicsWorld3D.Settings s,int[] layers,Contact contact){handle=createWorld(new int[]{s.maxBodies(),s.maxBodyPairs(),s.maxContacts()},layers,contact);}
    @JSBody(params={"capacities","layers","contact"},script="return valthorneHost.createPhysicsWorld(capacities,layers,contact);")
    private static native JSObject createWorld(int[] capacities,int[] layers,Contact contact);
    int create(BodySettings3D s){return create(handle,s.shape.type,s.shape.data,new float[]{s.position.x,s.position.y,s.position.z,s.rotation.x,s.rotation.y,s.rotation.z,s.rotation.w,s.velocity.x,s.velocity.y,s.velocity.z,s.motion.ordinal(),s.layer,s.friction,s.restitution,s.linearDamping,s.angularDamping,s.gravityFactor,s.sensor?1:0,s.sleeping?1:0,s.continuous?1:0,s.rotationLocked?1:0,s.mass});}
    @JSBody(params={"h","type","data","values"},script="return h.create(type,data,values);")
    private static native int create(JSObject h,int type,float[] data,float[] values);
    Float32Array read(int id,int kind){return read(handle,id,kind);}
    @JSBody(params={"h","id","kind"},script="return h.read(id,kind);") private static native Float32Array read(JSObject h,int id,int kind);
    void change(int id,int op,Vector3f v){change(id,op,v.x,v.y,v.z,0,0,0);}
    void change(int id,int op,float x,float y,float z,float px,float py,float pz){change(handle,id,op,x,y,z,px,py,pz);}
    @JSBody(params={"h","id","op","x","y","z","px","py","pz"},script="h.change(id,op,x,y,z,px,py,pz);") private static native void change(JSObject h,int id,int op,float x,float y,float z,float px,float py,float pz);
    void pose(int id,Vector3f p,Quaternionf q,float dt){pose(handle,id,p.x,p.y,p.z,q.x,q.y,q.z,q.w,dt);}
    @JSBody(params={"h","id","x","y","z","qx","qy","qz","qw","dt"},script="h.pose(id,x,y,z,qx,qy,qz,qw,dt);") private static native void pose(JSObject h,int id,float x,float y,float z,float qx,float qy,float qz,float qw,float dt);
    boolean isActive(int id){return active(handle,id,0);}
    void activateBody(int id){active(handle,id,1);}
    void deactivateBody(int id){active(handle,id,2);}
    @JSBody(params={"h","id","op"},script="return h.active(id,op);") private static native boolean active(JSObject h,int id,int op);
    void setGravity(Vector3f v){gravity(handle,v.x,v.y,v.z);}
    @JSBody(params={"h","x","y","z"},script="h.gravity(x,y,z);") private static native void gravity(JSObject h,float x,float y,float z);
    Vector3f getGravity(){Float32Array a=gravity(handle);return new Vector3f(a.get(0),a.get(1),a.get(2));}
    @JSBody(params="h",script="return h.getGravity();") private static native Float32Array gravity(JSObject h);
    void step(float dt){step(handle,dt);}
    @JSBody(params={"h","dt"},script="h.step(dt);") private static native void step(JSObject h,float dt);
    void destroyBody(int id){destroy(handle,id);}
    @JSBody(params={"h","id"},script="h.destroy(id);") private static native void destroy(JSObject h,int id);
    void optimize(){optimize(handle);}
    @JSBody(params="h",script="h.system.OptimizeBroadPhase();") private static native void optimize(JSObject h);
    interface Hit extends JSObject {
        @JSProperty int getId(); @JSProperty float getF();
        @JSProperty float getX(); @JSProperty float getY(); @JSProperty float getZ();
        @JSProperty float getNx(); @JSProperty float getNy(); @JSProperty float getNz();
    }
    Hit raycast(float x,float y,float z,float dx,float dy,float dz,RigidBody3D ignore){return ray(handle,x,y,z,dx,dy,dz,ignore!=null,ignore==null?0:ignore.id);}
    @JSBody(params={"h","x","y","z","dx","dy","dz","ignore","id"},script="return h.ray(x,y,z,dx,dy,dz,ignore?id:null);")
    private static native Hit ray(JSObject h,float x,float y,float z,float dx,float dy,float dz,boolean ignore,int id);
    JSObject joint(int a,int b,Vector3f p,Vector3f q,float min,float max){return joint(handle,a,b,p.x,p.y,p.z,q.x,q.y,q.z,min,max);}
    @JSBody(params={"h","a","b","ax","ay","az","bx","by","bz","min","max"},script="return h.joint(a,b,ax,ay,az,bx,by,bz,min,max);")
    private static native JSObject joint(JSObject h,int a,int b,float ax,float ay,float az,float bx,float by,float bz,float min,float max);
    void destroyJoint(JSObject joint){destroyJoint(handle,joint);}
    @JSBody(params={"h","joint"},script="h.destroyJoint(joint);") private static native void destroyJoint(JSObject h,JSObject joint);
    void detachContacts(){detachContacts(handle);}
    @JSBody(params="h",script="h.system.SetContactListener(0);") private static native void detachContacts(JSObject h);
    void close(){close(handle);}
    @JSBody(params="h",script="h.close();") private static native void close(JSObject h);
}
