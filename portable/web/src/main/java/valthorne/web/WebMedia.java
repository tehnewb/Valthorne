package valthorne.web;

import org.teavm.jso.*;
import valthorne.portable.AssetService;
import valthorne.portable.MediaService;
import java.util.Objects;

/** Browser image and audio decoding with cancellable requests and owned asset handles. */
public final class WebMedia implements MediaService {
    @JSFunctor private interface Success extends JSObject {void accept(int id);}
    @JSFunctor private interface Failure extends JSObject {void accept(String reason);}
    public AssetService.Request loadImage(String uri,Listener<Image> listener){Objects.requireNonNull(uri);Objects.requireNonNull(listener);int id=load(uri,false,value->listener.loaded(new ImageHandle(value)),listener::failed);return ()->cancel(id);}
    public AssetService.Request loadSound(String uri,Listener<Sound> listener){Objects.requireNonNull(uri);Objects.requireNonNull(listener);int id=load(uri,true,value->listener.loaded(new SoundHandle(value)),listener::failed);return ()->cancel(id);}
    private record ImageHandle(int id) implements Image {
        public int width(){return dimension(id,false);}public int height(){return dimension(id,true);}
        public void draw(float x,float y,float width,float height,float alpha){drawNative(id,x,y,width,height,alpha);}
        public void close(){release(id);}
    }
    private record SoundHandle(int id) implements Sound {public boolean play(float volume,float pan){return playNative(id,volume,pan);}public void close(){release(id);}}
    @JSBody(params={"uri","sound","success","failure"},script="return valthorneHost.media.load(uri,sound,success,failure);") private static native int load(String uri,boolean sound,Success success,Failure failure);
    @JSBody(params="id",script="valthorneHost.media.cancel(id);") private static native void cancel(int id);
    @JSBody(params="id",script="valthorneHost.media.release(id);") private static native void release(int id);
    @JSBody(params={"id","height"},script="const image=valthorneHost.media.get(id).image;return height?image.height:image.width;") private static native int dimension(int id,boolean height);
    @JSBody(params={"id","x","y","width","height","alpha"},script="valthorneHost.media.draw(id,x,y,width,height,alpha);") private static native void drawNative(int id,float x,float y,float width,float height,float alpha);
    @JSBody(params={"id","volume","pan"},script="return valthorneHost.media.play(id,volume,pan);") private static native boolean playNative(int id,float volume,float pan);
}
