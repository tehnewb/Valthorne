package valthorne.web.audio;
import java.nio.ByteBuffer;
import org.teavm.interop.*;
import org.teavm.jso.*;
import org.teavm.jso.typedarrays.Uint8Array;
import valthorne.audio.AudioFormat;
import valthorne.audio.sound.*;
public final class AudioDecode {
 private AudioDecode(){}
 @Async public static native SoundData decode(SoundSource source,byte[] bytes,AudioFormat format);
 private static void decode(SoundSource source,byte[] bytes,AudioFormat format,AsyncCallback<SoundData> callback){
  Uint8Array encoded=Uint8Array.create(bytes.length);for(int i=0;i<bytes.length;i++)encoded.set(i,(short)(bytes[i]&255));
  load(encoded,(decoded,error)->{if(error!=null){callback.error(new IllegalArgumentException(error));return;}Uint8Array data=decoded.getPcm();ByteBuffer pcm=ByteBuffer.allocate(data.getLength());for(int i=0;i<data.getLength();i++)pcm.put((byte)data.get(i));pcm.flip();callback.complete(new SoundData(source,pcm,0,pcm.remaining(),decoded.getDuration(),decoded.getChannels(),decoded.getRate(),16,decoded.getDuration()>15||pcm.remaining()>8*1024*1024,true,format));});
 }
 private interface Decoded extends JSObject{@JSProperty Uint8Array getPcm();@JSProperty int getChannels();@JSProperty int getRate();@JSProperty float getDuration();}
 @JSFunctor private interface Loaded extends JSObject{void accept(Decoded decoded,String error);}
 @JSBody(params={"bytes","callback"},script="valthorneHost.audioBackend.decode(bytes).then(data=>callback(data,null),error=>callback(null,String(error)));") private static native void load(Uint8Array bytes,Loaded callback);
}
