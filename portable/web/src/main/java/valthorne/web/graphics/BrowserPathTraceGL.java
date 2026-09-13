package valthorne.web.graphics;
import org.teavm.jso.*;
/** Internal raster-pass bridge for the unchanged path-tracing scene and algorithms. */
public final class BrowserPathTraceGL {
 private BrowserPathTraceGL(){}
 @JSBody(script="return valthorneHost.graphics.pathTracing.supported();") public static native boolean supported();
 @JSBody(params="source",script="return valthorneHost.graphics.pathTracing.compile(source);") public static native int program(String source);
 @JSBody(params="id",script="valthorneHost.graphics.pathTracing.boundBuffer=id;") public static native void bindBuffer(int id);
 @JSBody(params="data",script="valthorneHost.graphics.pathTracing.upload(data);") public static native void upload(float[] data);
 @JSBody(params={"binding","id"},script="valthorneHost.graphics.pathTracing.buffers.set(binding,id);") public static native void bindStorage(int binding,int id);
 @JSBody(params={"binding","id"},script="valthorneHost.graphics.pathTracing.images.set(binding,id);") public static native void bindImage(int binding,int id);
 @JSBody(script="valthorneHost.graphics.pathTracing.dispatch();") public static native void dispatch();
 @JSBody(script="valthorneHost.graphics.pathTracing.present();") public static native void present();
 @JSBody(script="return valthorneHost.graphics.pathTracing.begin();") public static native JSObject begin();
 @JSBody(params="state",script="valthorneHost.graphics.pathTracing.end(state);") public static native void end(JSObject state);
 @JSBody(params={"width","height"},script="var graphics=valthorneHost.graphics,context=graphics.context(),textureId=graphics.reverse.get(context.getParameter(context.TEXTURE_BINDING_2D));context.texStorage2D(context.TEXTURE_2D,1,context.RGBA32F,width,height);graphics.images.set(textureId,{width:width,height:height,version:1});") public static native void storage(int width,int height);
 @JSBody(params="textures",script="return valthorneHost.graphics.pathTracing.atlas(textures);") public static native int atlas(int[] textures);
}
