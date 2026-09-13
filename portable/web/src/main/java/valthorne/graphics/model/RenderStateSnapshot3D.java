package valthorne.graphics.model;
import org.teavm.jso.*;
/** Restores the corresponding WebGL 2 state. WebGL has no framebuffer-sRGB toggle or buffer textures. */
public final class RenderStateSnapshot3D implements AutoCloseable {
 private final JSObject state;
 public RenderStateSnapshot3D(){state=capture();}
 public void close(){restore(state);}
 @JSBody(script="return valthorneHost.graphics.snapshot();") private static native JSObject capture();
 @JSBody(params="state",script="valthorneHost.graphics.restore(state);") private static native void restore(JSObject state);
}
