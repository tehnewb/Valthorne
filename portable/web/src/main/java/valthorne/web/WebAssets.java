package valthorne.web;

import org.teavm.jso.JSBody;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;
import valthorne.portable.AssetService;
import valthorne.portable.ModelAsset;
import java.util.Objects;

/** Cancellable same-origin GLB loading through the initialized browser asset service. */
public final class WebAssets implements AssetService {
    @JSFunctor public interface Success extends JSObject {void accept(int id);}
    @JSFunctor public interface Failure extends JSObject {void accept(String reason);}

    @Override public Request loadGlb(String uri, Listener listener) {
        Objects.requireNonNull(uri); Objects.requireNonNull(listener);
        int id = load(uri, model -> listener.loaded(new Handle(model)), listener::failed);
        return () -> cancel(id);
    }

    private static final class Handle implements ModelAsset {
        private final int id;
        private boolean closed;
        private Handle(int id) {this.id=id;}
        public void transform(float x,float y,float z,float yaw,float scale) {
            check();
            if (!Float.isFinite(x)||!Float.isFinite(y)||!Float.isFinite(z)||!Float.isFinite(yaw)
                    ||!Float.isFinite(scale)||scale<=0) throw new IllegalArgumentException("Finite transform and positive scale required");
            setTransform(id,x,y,z,yaw,scale);
        }
        public int animationCount(){check();return count(id);}
        public void animate(int clip,float seconds) {
            check();
            if(clip<0||clip>=count(id)||!Float.isFinite(seconds)||seconds<0)
                throw new IllegalArgumentException("Valid clip and finite nonnegative animation time required");
            applyAnimation(id,clip,seconds);
        }
        private void check(){if(closed)throw new IllegalStateException("Model is closed");}
        public void close(){if(!closed){closed=true;release(id);}}
    }

    @JSBody(params={"uri","success","failure"},script="return valthorneHost.assets.load(uri,success,failure);")
    private static native int load(String uri,Success success,Failure failure);
    @JSBody(params="id",script="valthorneHost.assets.cancel(id);") private static native void cancel(int id);
    @JSBody(params="id",script="valthorneHost.assets.release(id);") private static native void release(int id);
    @JSBody(params="id",script="return valthorneHost.assets.animationCount(id);") private static native int count(int id);
    @JSBody(params={"id","clip","time"},script="valthorneHost.assets.animate(id,clip,time);")
    private static native void applyAnimation(int id,int clip,float time);
    @JSBody(params={"id","x","y","z","yaw","scale"},script="valthorneHost.assets.transform(id,x,y,z,yaw,scale);")
    private static native void setTransform(int id,float x,float y,float z,float yaw,float scale);
}
