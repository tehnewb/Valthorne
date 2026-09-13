package valthorne.graphics.texture;
import java.nio.ByteBuffer;
import java.util.Objects;
import org.teavm.interop.Async;
import org.teavm.interop.AsyncCallback;
import org.teavm.jso.*;
import org.teavm.jso.typedarrays.Uint8Array;

/** Browser-decoded RGBA pixels, retaining the existing synchronous Java API. */
public record TextureData(ByteBuffer buffer,int width,int height) {
    public static TextureData load(String path){return load(path,true);}
    public static TextureData load(String path,boolean flipVertically){byte[] local=valthorne.web.BrowserIO.readLocal(path);return local!=null?load(local,flipVertically):Decoder.decode(Objects.requireNonNull(path),null,flipVertically);}
    public static TextureData load(byte[] data){return load(data,true);}
    public static TextureData load(byte[] data,boolean flipVertically){
        Objects.requireNonNull(data);Uint8Array encoded=Uint8Array.create(data.length);
        for(int i=0;i<data.length;i++)encoded.set(i,(short)(data[i]&255));
        return Decoder.decode(null,encoded,flipVertically);
    }
    private static final class Decoder {
    @Async private static native TextureData decode(String path,Uint8Array bytes,boolean flip);
    private static void decode(String path,Uint8Array bytes,boolean flip,AsyncCallback<TextureData> callback){
        decodeNative(path,bytes,flip,(image,error)->{
            if(error!=null){callback.error(new IllegalArgumentException("Failed to load image: "+error));return;}
            try{
                Uint8Array pixels=image.getPixels();ByteBuffer buffer=ByteBuffer.allocate(pixels.getLength());
                for(int i=0;i<pixels.getLength();i++)buffer.put((byte)pixels.get(i));buffer.flip();
                callback.complete(new TextureData(buffer,image.getWidth(),image.getHeight()));
            }catch(RuntimeException|Error failure){callback.error(failure);}
        });
    }
    private interface Image extends JSObject {
        @JSProperty int getWidth();
        @JSProperty int getHeight();
        @JSProperty Uint8Array getPixels();
    }
    @JSFunctor private interface Decoded extends JSObject {void accept(Image image,String error);}
    @JSBody(params={"path","bytes","flip","callback"},script="valthorneHost.graphics.decode(path,bytes,flip).then(image=>callback(image,null),error=>callback(null,String(error)));")
    private static native void decodeNative(String path,Uint8Array bytes,boolean flip,Decoded callback);
    }
    public Texture asTexture(){return new Texture(this);}
    public NinePatchTexture asNinePatchTexture(int left,int right,int top,int bottom){return new NinePatchTexture(this,left,right,top,bottom);}
    /** Pixels are managed memory, reclaimed when callers release this record. */
    public void dispose(){}
}
