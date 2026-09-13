package valthorne.web;

import org.teavm.jso.JSBody;
import valthorne.portable.SceneBackend;

/** Browser implementation backed by the initialized Filament/Jolt host. */
public class WebSceneBackend implements SceneBackend {
        public void close() {closeNative();}

        public void clear() {clearNative();}

        public void light(float x, float y, float z, int rgb, float intensity) {lightNative(x,y,z,rgb,intensity);}

        public void render() {renderNative();}

        public void step(float seconds) {stepNative(seconds);}

        public void box(float x, float y, float z, float halfX, float halfY, float halfZ, int rgb, boolean dynamic) {
            if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)
                    || !Float.isFinite(halfX) || !Float.isFinite(halfY) || !Float.isFinite(halfZ)
                    || halfX <= 0 || halfY <= 0 || halfZ <= 0)
                throw new IllegalArgumentException("Finite positions and positive finite extents required");
            boxNative(x,y,z,halfX,halfY,halfZ,rgb,dynamic);
        }

        @JSBody(params = {"x", "y", "z", "hx", "hy", "hz", "rgb", "dynamic"}, script = "globalThis.valthorneHost.box(x,y,z,hx,hy,hz,rgb,dynamic);")
        private static native void boxNative(float x, float y, float z, float hx, float hy, float hz, int rgb, boolean dynamic);
        @JSBody(params = "seconds", script = "globalThis.valthorneHost.step(seconds);") private static native void stepNative(float seconds);
        @JSBody(script = "globalThis.valthorneHost.render();") private static native void renderNative();
        @JSBody(params = {"x", "y", "z", "rgb", "intensity"}, script = "globalThis.valthorneHost.light(x,y,z,rgb,intensity);")
        private static native void lightNative(float x, float y, float z, int rgb, float intensity);
        @JSBody(script = "globalThis.valthorneHost.clear();") private static native void clearNative();
        @JSBody(script = "globalThis.valthorneHost.close();") private static native void closeNative();
    }
