package valthorne;
import org.teavm.jso.JSBody;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
/** Browser implementation of rendering and input diagnostics. */
public final class PlatformTools {
 private PlatformTools(){}
 public static final int RELEASE=0,PRESS=1,REPEAT=2;
 @JSBody(params={"x","y","w","h"},script="valthorneHost.graphics.context().viewport(x,y,w,h);") public static native void viewport(int x,int y,int w,int h);
 @JSBody(script="if(valthorneHost.graphics.gl)valthorneHost.graphics.gl.finish();") public static native void finish();
 @JSBody(script="return valthorneHost.graphics.gl?valthorneHost.graphics.gl.getError():0;") public static native int graphicsError();
 @JSBody(script="return 'Filament / WebGL2';") public static native String rendererName();
 @JSBody(params="unit",script="var graphicsContext=valthorneHost.graphics.context();graphicsContext.activeTexture(graphicsContext.TEXTURE0+unit);") public static native void textureUnit(int unit);
 public static void capture(String path,int width,int height){try{var file=Path.of(path);if(file.getParent()!=null)Files.createDirectories(file.getParent());Files.write(file,pixels(width,height));}catch(IOException error){throw new UncheckedIOException(error);}}
 @JSBody(params={"width","height"},script="var snapshotCanvas=document.createElement('canvas');snapshotCanvas.width=width;snapshotCanvas.height=height;var snapshotContext=snapshotCanvas.getContext('2d');var captureLayers=valthorneHost.platform.window.stage.querySelectorAll('canvas');for(var captureIndex=0;captureIndex<captureLayers.length;captureIndex++)snapshotContext.drawImage(captureLayers[captureIndex],0,0,width,height);var encodedImage=atob(snapshotCanvas.toDataURL('image/png').split(',')[1]);return Int8Array.from(encodedImage,c=>c.charCodeAt(0));") private static native byte[] pixels(int width,int height);
 @JSBody(params={"code","action"},script="valthorneHost.platform.legacyKeyEvent(code,0,action!==0);") public static native void injectKey(int code,int action);
 @JSBody(params={"x","y","button","action"},script="var replayPlatform=valthorneHost.platform;replayPlatform.legacyMouseEvent(2,-1,0,replayPlatform.mouseX,replayPlatform.window.height-replayPlatform.mouseY,x,y);replayPlatform.mouseX=x;replayPlatform.mouseY=replayPlatform.window.height-y;if(button>=0)replayPlatform.legacyMouseEvent(action===0?1:0,button,0,0,0,x,y);") public static native void injectPointer(float x,float y,int button,int action);
}
