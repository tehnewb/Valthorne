package valthorne.web.audio;
import java.nio.ByteBuffer;
import org.teavm.jso.JSBody;
import org.teavm.jso.typedarrays.Uint8Array;
public final class BrowserAL {
private BrowserAL(){}
public static final int AL_BUFFER=0x1009;
public static final int AL_BUFFERS_PROCESSED=0x1016;
public static final int AL_BUFFERS_QUEUED=0x1015;
public static final int AL_FALSE=0x0;
public static final int AL_FORMAT_MONO16=0x1101;
public static final int AL_FORMAT_MONO8=0x1100;
public static final int AL_FORMAT_STEREO16=0x1103;
public static final int AL_FORMAT_STEREO8=0x1102;
public static final int AL_GAIN=0x100A;
public static final int AL_INITIAL=0x1011;
public static final int AL_LOOPING=0x1007;
public static final int AL_PAUSED=0x1013;
public static final int AL_PITCH=0x1003;
public static final int AL_PLAYING=0x1012;
public static final int AL_SEC_OFFSET=0x1024;
public static final int AL_SOURCE_STATE=0x1010;
public static final int AL_STOPPED=0x1014;
public static final int AL_TRUE=0x1;
@JSBody(params={},script="return valthorneHost.audioBackend.createSource();") public static native int alGenSources();
@JSBody(params={"id"},script="valthorneHost.audioBackend.deleteSource(id);") public static native void alDeleteSources(int id);
@JSBody(params={},script="return valthorneHost.audioBackend.createBuffer();") public static native int alGenBuffers();
@JSBody(params={"id"},script="valthorneHost.audioBackend.deleteBuffer(id);") public static native void alDeleteBuffers(int id);
@JSBody(params={"id"},script="valthorneHost.audioBackend.command(id,'play');") public static native void alSourcePlay(int id);
@JSBody(params={"id"},script="valthorneHost.audioBackend.command(id,'pause');") public static native void alSourcePause(int id);
@JSBody(params={"id"},script="valthorneHost.audioBackend.command(id,'stop');") public static native void alSourceStop(int id);
@JSBody(params={"id"},script="valthorneHost.audioBackend.command(id,'rewind');") public static native void alSourceRewind(int id);
@JSBody(params={"id","name","value"},script="valthorneHost.audioBackend.integer(id,name,value);") public static native void alSourcei(int id,int name,int value);
@JSBody(params={"id","name"},script="return valthorneHost.audioBackend.query(id,name);") public static native int alGetSourcei(int id,int name);
@JSBody(params={"id","name","value"},script="valthorneHost.audioBackend.scalar(id,name,value);") public static native void alSourcef(int id,int name,float value);
@JSBody(params={"id","name"},script="return valthorneHost.audioBackend.query(id,name);") public static native float alGetSourcef(int id,int name);
@JSBody(params={"id","buffer"},script="valthorneHost.audioBackend.queue(id,buffer);") public static native void alSourceQueueBuffers(int id,int buffer);
@JSBody(params={"id"},script="return valthorneHost.audioBackend.unqueue(id);") public static native int alSourceUnqueueBuffers(int id);
@JSBody(params={"id","format","bytes","rate"},script="valthorneHost.audioBackend.upload(id,format,bytes,rate);") public static native void upload(int id,int format,Uint8Array bytes,int rate);
public static void alBufferData(int id,int format,ByteBuffer data,int rate){Uint8Array bytes=Uint8Array.create(data.remaining());for(int i=0;i<data.remaining();i++)bytes.set(i,(short)(data.get(data.position()+i)&255));upload(id,format,bytes,rate);}
}