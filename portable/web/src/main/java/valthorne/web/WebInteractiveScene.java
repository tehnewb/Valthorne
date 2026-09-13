package valthorne.web;

import org.teavm.jso.JSBody;
import org.teavm.jso.JSObject;
import org.teavm.jso.JSProperty;
import valthorne.portable.InteractiveScene;
import valthorne.portable.PhysicsBody;

/** Owned Jolt body handles and first-person camera for the browser backend. */
public final class WebInteractiveScene extends WebSceneBackend implements InteractiveScene {
    public void particles(float x,float y,float z,int rgb,int count,boolean physics,boolean lights){particlesNative(x,y,z,rgb,count,physics,lights);}
    @JSBody(params={"x","y","z","rgb","count","physics","lights"},script="valthorneHost.particles.burst(x,y,z,rgb,count,physics,lights);") private static native void particlesNative(float x,float y,float z,int rgb,int count,boolean physics,boolean lights);
    public PhysicsBody rigidBox(float x,float y,float z,float hx,float hy,float hz,int rgb,boolean dynamic,boolean lockRotation){
        if(!Float.isFinite(x)||!Float.isFinite(y)||!Float.isFinite(z)||!Float.isFinite(hx)||!Float.isFinite(hy)||!Float.isFinite(hz)||hx<=0||hy<=0||hz<=0)throw new IllegalArgumentException("Invalid rigid box");
        return new Body(create(x,y,z,hx,hy,hz,rgb,dynamic,lockRotation));
    }
    public float rayDistance(float x,float y,float z,float dx,float dy,float dz,float range,PhysicsBody ignored){
        return ray(x,y,z,dx,dy,dz,range,ignored==null?0:((Body)ignored).id);
    }
    private interface Hit extends JSObject {@JSProperty int getId();@JSProperty float getDistance();}
    public RayHit castRay(float x,float y,float z,float dx,float dy,float dz,float range,PhysicsBody ignored){
        Hit hit=hitNative(x,y,z,dx,dy,dz,range,ignored==null?0:((Body)ignored).id);
        return hit==null?null:new RayHit(new Body(hit.getId()),hit.getDistance());
    }
    @JSBody(params={"x","y","z","dx","dy","dz","range","ignore"},script="return valthorneHost.castRay(x,y,z,dx,dy,dz,range,ignore);") private static native Hit hitNative(float x,float y,float z,float dx,float dy,float dz,float range,int ignore);
    public void camera(float x,float y,float z,float yaw,float pitch,float fov){cameraNative(x,y,z,yaw,pitch,fov);}
    @JSBody(params={"x","y","z","yaw","pitch","fov"},script="valthorneHost.setCamera(x,y,z,yaw,pitch,fov);") private static native void cameraNative(float x,float y,float z,float yaw,float pitch,float fov);
    private record Body(int id) implements PhysicsBody {
        public float x(){return component(id,0);} public float y(){return component(id,1);} public float z(){return component(id,2);}
        public float velocityY(){return component(id,3);}
        public void velocity(float x,float y,float z){change(id,0,x,y,z);}
        public void impulse(float x,float y,float z){change(id,1,x,y,z);}
        public void position(float x,float y,float z){change(id,2,x,y,z);}
        public void close(){release(id);}
    }
    @JSBody(params={"x","y","z","hx","hy","hz","rgb","dynamic","locked"},script="return valthorneHost.createBox(x,y,z,[hx,hy,hz],rgb,dynamic,locked).id;") private static native int create(float x,float y,float z,float hx,float hy,float hz,int rgb,boolean dynamic,boolean locked);
    @JSBody(params={"id","axis"},script="return valthorneHost.bodyComponent(id,axis);") private static native float component(int id,int axis);
    @JSBody(params={"id","operation","x","y","z"},script="valthorneHost.changeBody(id,operation,x,y,z);") private static native void change(int id,int operation,float x,float y,float z);
    @JSBody(params="id",script="valthorneHost.releaseBody(id);") private static native void release(int id);
    @JSBody(params={"x","y","z","dx","dy","dz","range","ignore"},script="return valthorneHost.rayDistance(x,y,z,dx,dy,dz,range,ignore);") private static native float ray(float x,float y,float z,float dx,float dy,float dz,float range,int ignore);
}
