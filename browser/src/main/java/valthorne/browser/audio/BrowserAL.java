package valthorne.browser.audio;

import java.nio.ByteBuffer;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSBuffer;
import org.teavm.jso.JSBufferType;

/**
 * Maps the shared player's source and buffer operations to Web Audio. Handles
 * belong to one AudioRuntime session. All calls execute on its cooperative Java
 * owner; PCM uploads copy into AudioBuffer storage before the scratch is reused.
 */
public final class BrowserAL {
    /**
     * Shared player protocol value for buffer.
     */
    public static final int AL_BUFFER = 0x1009;

    /**
     * Shared player protocol value for buffers processed.
     */
    public static final int AL_BUFFERS_PROCESSED = 0x1016;

    /**
     * Shared player protocol value for buffers queued.
     */
    public static final int AL_BUFFERS_QUEUED = 0x1015;

    /**
     * Shared player protocol value for false.
     */
    public static final int AL_FALSE = 0x0;

    /**
     * Shared player protocol value for format mono16.
     */
    public static final int AL_FORMAT_MONO16 = 0x1101;

    /**
     * Shared player protocol value for format mono8.
     */
    public static final int AL_FORMAT_MONO8 = 0x1100;

    /**
     * Shared player protocol value for format stereo16.
     */
    public static final int AL_FORMAT_STEREO16 = 0x1103;

    /**
     * Shared player protocol value for format stereo8.
     */
    public static final int AL_FORMAT_STEREO8 = 0x1102;

    /**
     * Shared player protocol value for gain.
     */
    public static final int AL_GAIN = 0x100A;

    /**
     * Shared player protocol value for initial.
     */
    public static final int AL_INITIAL = 0x1011;

    /**
     * Shared player protocol value for looping.
     */
    public static final int AL_LOOPING = 0x1007;

    /**
     * Shared player protocol value for paused.
     */
    public static final int AL_PAUSED = 0x1013;

    /**
     * Shared player protocol value for pitch.
     */
    public static final int AL_PITCH = 0x1003;

    /**
     * Shared player protocol value for playing.
     */
    public static final int AL_PLAYING = 0x1012;

    /**
     * Shared player protocol value for sec offset.
     */
    public static final int AL_SEC_OFFSET = 0x1024;

    /**
     * Shared player protocol value for source state.
     */
    public static final int AL_SOURCE_STATE = 0x1010;

    /**
     * Shared player protocol value for stopped.
     */
    public static final int AL_STOPPED = 0x1014;

    /**
     * Shared player protocol value for true.
     */
    public static final int AL_TRUE = 0x1;

    /**
     * Prevents construction of stateless playback bindings.
     */
    private BrowserAL() {
    }

    /**
     * Allocates one session-owned playback source.
     * @return playback operation result
     */
    @JSBody(params = {}, script = "return valthorneHost.audioBackend.createSource();")
    public static native int alGenSources();

    /**
     * Releases a playback source and disconnects its scheduled nodes.
     * @param id playback operation parameter
     */
    @JSBody(params = {"id"}, script = "valthorneHost.audioBackend.deleteSource(id);")
    public static native void alDeleteSources(int id);

    /**
     * Allocates one session-owned PCM buffer identifier.
     * @return playback operation result
     */
    @JSBody(params = {}, script = "return valthorneHost.audioBackend.createBuffer();")
    public static native int alGenBuffers();

    /**
     * Releases a PCM buffer after it has been detached from sources.
     * @param id playback operation parameter
     */
    @JSBody(params = {"id"}, script = "valthorneHost.audioBackend.deleteBuffer(id);")
    public static native void alDeleteBuffers(int id);

    /**
     * Plays the source while preserving the shared playback state machine.
     * @param id playback operation parameter
     */
    @JSBody(params = {"id"}, script = "valthorneHost.audioBackend.command(id,'play');")
    public static native void alSourcePlay(int id);

    /**
     * Pauses the source while preserving the shared playback state machine.
     * @param id playback operation parameter
     */
    @JSBody(params = {"id"}, script = "valthorneHost.audioBackend.command(id,'pause');")
    public static native void alSourcePause(int id);

    /**
     * Stops the source while preserving the shared playback state machine.
     * @param id playback operation parameter
     */
    @JSBody(params = {"id"}, script = "valthorneHost.audioBackend.command(id,'stop');")
    public static native void alSourceStop(int id);

    /**
     * Rewinds the source while preserving the shared playback state machine.
     * @param id playback operation parameter
     */
    @JSBody(params = {"id"}, script = "valthorneHost.audioBackend.command(id,'rewind');")
    public static native void alSourceRewind(int id);

    /**
     * Updates source buffer binding or looping policy.
     * @param id playback operation parameter
     * @param name playback operation parameter
     * @param value playback operation parameter
     */
    @JSBody(params = {"id", "name", "value"}, script = "valthorneHost.audioBackend.integer(id,name,value);")
    public static native void alSourcei(int id, int name, int value);

    /**
     * Reads source state or queue progress at the current audio clock.
     * @param id playback operation parameter
     * @param name playback operation parameter
     * @return playback operation result
     */
    @JSBody(params = {"id", "name"}, script = "return valthorneHost.audioBackend.query(id,name);")
    public static native int alGetSourcei(int id, int name);

    /**
     * Updates gain, pitch, or playback offset.
     * @param id playback operation parameter
     * @param name playback operation parameter
     * @param value playback operation parameter
     */
    @JSBody(params = {"id", "name", "value"}, script = "valthorneHost.audioBackend.scalar(id,name,value);")
    public static native void alSourcef(int id, int name, float value);

    /**
     * Reads gain, pitch, or playback offset.
     * @param id playback operation parameter
     * @param name playback operation parameter
     * @return playback operation result
     */
    @JSBody(params = {"id", "name"}, script = "return valthorneHost.audioBackend.query(id,name);")
    public static native float alGetSourcef(int id, int name);

    /**
     * Appends a decoded PCM block to a streaming source.
     * @param id playback operation parameter
     * @param buffer playback operation parameter
     */
    @JSBody(params = {"id", "buffer"}, script = "valthorneHost.audioBackend.queue(id,buffer);")
    public static native void alSourceQueueBuffers(int id, int buffer);

    /**
     * Returns the oldest processed streaming buffer.
     * @param id playback operation parameter
     * @return playback operation result
     */
    @JSBody(params = {"id"}, script = "return valthorneHost.audioBackend.unqueue(id);")
    public static native int alSourceUnqueueBuffers(int id);

    /**
     * Copies the readable PCM range without changing the source cursor.
     * @param id destination buffer
     * @param format mono/stereo and signed PCM16 or unsigned PCM8
     * @param bytes readable interleaved PCM
     * @param rate samples per second
     */
    public static void alBufferData(int id, int format, ByteBuffer bytes, int rate) {
        /*
         * A sliced view keeps positions correct without copying the Java staging buffer.
         */
        upload(id, format, bytes.slice(), rate);
    }

    /**
     * Borrows a zero-based readable range for immediate native conversion.
     * @param id destination buffer
     * @param format shared PCM format
     * @param bytes zero-based PCM range
     * @param rate sample rate
     */
    @JSBody(params = {"id", "format", "bytes", "rate"}, script = "valthorneHost.audioBackend.upload(id,format,bytes,rate);")
    private static native void upload(int id, int format, @JSBuffer(JSBufferType.UINT8) ByteBuffer bytes, int rate);

}
