package valthorne.web.graphics;
import org.teavm.jso.*;
import org.teavm.jso.typedarrays.Uint8Array;
import java.nio.ByteBuffer;
import valthorne.graphics.texture.TextureData;
/** Builds the existing debug overlay's fixed ASCII atlas once. */
public final class BrowserPerformanceAtlas {
 private BrowserPerformanceAtlas(){}
 public static TextureData create(){Uint8Array pixels=paint();ByteBuffer data=ByteBuffer.allocate(pixels.getLength());for(int i=0;i<pixels.getLength();i++)data.put((byte)pixels.get(i));data.flip();return new TextureData(data,288,168);}
 @JSBody(script="var c=document.createElement('canvas');c.width=288;c.height=168;var g=c.getContext('2d');g.font='700 22px monospace';for(var i=0;i<95;i++){var x=i%16*18,y=Math.floor(i/16)*28;g.fillStyle='rgba(0,0,0,0.7843137255)';g.fillText(String.fromCharCode(i+32),x+2,y+24);g.fillStyle='rgb(150,255,213)';g.fillText(String.fromCharCode(i+32),x,y+22);}var source=g.getImageData(0,0,288,168).data,result=new Uint8Array(source.length),row=288*4;for(var y=0;y<168;y++)result.set(source.subarray(y*row,(y+1)*row),(167-y)*row);return result;") private static native Uint8Array paint();
}
