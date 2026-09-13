package valthorne.web.audio;

import java.nio.ByteBuffer;
import org.teavm.interop.*;
import org.teavm.jso.*;
import org.teavm.jso.typedarrays.Uint8Array;
import valthorne.audio.AudioFormat;
import valthorne.audio.sound.*;
import valthorne.web.BrowserIO;

/** Suspends the Java caller while a bounded decoder supplies the next PCM block. */
public final class AudioStreaming {
 private AudioStreaming() {}
 public interface Handle extends JSObject {}
 private interface Metadata extends JSObject {
  @JSProperty String getFormat(); @JSProperty int getChannels(); @JSProperty int getRate();
  @JSProperty float getDuration(); @JSProperty double getOffset(); @JSProperty double getLength();
  @JSProperty Uint8Array getPcm();
 }
 private static byte[] memory(SoundSource source) {
  return source instanceof SoundSource.BytesSource bytes ? bytes.bytes() : BrowserIO.readLocal(((SoundSource.PathSource)source).path());
 }
 private static String path(SoundSource source) {return source instanceof SoundSource.PathSource p ? p.path() : null;}
 private static Uint8Array encoded(byte[] bytes) {return bytes==null?null:borrow(bytes);}
 @JSBody(params="bytes",script="return new Uint8Array(bytes.buffer,bytes.byteOffset,bytes.byteLength);") private static native Uint8Array borrow(byte[] bytes);
 @Async public static native SoundData probe(SoundSource source);
 private static void probe(SoundSource source,AsyncCallback<SoundData> callback) {
  probeNative(path(source),encoded(memory(source)),(m,error)->{if(error!=null){callback.error(new IllegalArgumentException(error));return;}
   AudioFormat format=AudioFormat.valueOf(m.getFormat());
   Uint8Array bytes=m.getPcm();ByteBuffer pcm=null;if(bytes!=null){pcm=ByteBuffer.allocate(bytes.getLength());for(int i=0;i<bytes.getLength();i++)pcm.put((byte)bytes.get(i));pcm.flip();}
   callback.complete(new SoundData(source,pcm,pcm==null?(long)m.getOffset():0,pcm==null?(long)m.getLength():pcm.remaining(),m.getDuration(),m.getChannels(),m.getRate(),16,pcm==null,format!=AudioFormat.WAV,format));
  });
 }
 @JSFunctor private interface Probed extends JSObject {void accept(Metadata metadata,String error);}
 @JSBody(params={"path","bytes","callback"},script="valthorneHost.audioBackend.streams.probe(path,bytes).then(m=>callback(m,null),e=>callback(null,String(e)));") private static native void probeNative(String path,Uint8Array bytes,Probed callback);
 @Async public static native Handle open(SoundSource source);
 private static void open(SoundSource source,AsyncCallback<Handle> callback) {
  openNative(path(source),encoded(memory(source)),(handle,error)->{if(error!=null)callback.error(new IllegalArgumentException(error));else callback.complete(handle);});
 }
 @JSFunctor private interface Opened extends JSObject {void accept(Handle handle,String error);}
 @JSBody(params={"path","bytes","callback"},script="valthorneHost.audioBackend.streams.open(path,bytes).then(s=>callback(s,null),e=>callback(null,String(e)));") private static native void openNative(String path,Uint8Array bytes,Opened callback);
 @Async public static native int read(Handle handle,ByteBuffer destination);
 private static void read(Handle handle,ByteBuffer destination,AsyncCallback<Integer> callback) {
  destination.clear();readNative(handle,destination.remaining(),(bytes,error)->{if(error!=null){callback.error(new IllegalStateException(error));return;}
   for(int i=0;i<bytes.getLength();i++)destination.put((byte)bytes.get(i));destination.flip();callback.complete(bytes.getLength());
  });
 }
 @JSFunctor private interface Read extends JSObject {void accept(Uint8Array bytes,String error);}
 @JSBody(params={"handle","size","callback"},script="handle.read(size).then(b=>callback(b,null),e=>callback(null,String(e)));") private static native void readNative(Handle handle,int size,Read callback);
 @Async public static native boolean seek(Handle handle,float seconds);
 private static void seek(Handle handle,float seconds,AsyncCallback<Boolean> callback) {seekNative(handle,seconds,error->{if(error!=null)callback.error(new IllegalStateException(error));else callback.complete(true);});}
 @JSFunctor private interface Sought extends JSObject {void accept(String error);}
 @JSBody(params={"handle","seconds","callback"},script="handle.seek(seconds).then(()=>callback(null),e=>callback(String(e)));") private static native void seekNative(Handle handle,float seconds,Sought callback);
 @JSBody(params="handle",script="handle.close();") public static native void close(Handle handle);
}
