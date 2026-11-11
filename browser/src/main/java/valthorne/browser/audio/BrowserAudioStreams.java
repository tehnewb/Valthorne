package valthorne.browser.audio;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import org.teavm.interop.Async;
import org.teavm.interop.AsyncCallback;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSBuffer;
import org.teavm.jso.JSBufferType;
import org.teavm.jso.typedarrays.Uint8Array;
import valthorne.audio.AudioFormat;
import valthorne.audio.SoundData;
import valthorne.audio.SoundSource;
import valthorne.browser.BrowserAssets;

/**
 * Suspends individual Java callers while incremental WAV, MPEG, or Vorbis
 * decoding supplies metadata or PCM. URL sources use bounded response readers;
 * embedded resources borrow their immutable encoded bytes. Each open handle
 * owns its decoder and cursor independently of other players.
 */
public final class BrowserAudioStreams {
    /**
     * Empty nonnull buffer used when a URL supplies the encoded bytes.
     */
    private static final ByteBuffer EMPTY = ByteBuffer.allocateDirect(0);

    /**
     * Prevents constructing stateless decoder bindings.
     */
    private BrowserAudioStreams() {
    }

    /**
     * Finds embedded bytes without fetching a URL into an unbounded allocation.
     * @param source immutable sound origin
     * @return encoded memory, or an empty marker for HTTP streaming
     */
    private static ByteBuffer memory(SoundSource source) {
        /*
         * Only classpath resources and explicit memory sources are retained in full.
         */
        if (source instanceof SoundSource.BytesSource bytes) return ByteBuffer.wrap(bytes.bytes());
        try {
            byte[] local = BrowserAssets.local(((SoundSource.PathSource) source).path());
            return local == null ? EMPTY : ByteBuffer.wrap(local);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    /**
     * Reads the URL token used when no encoded memory is supplied.
     * @param source sound origin
     * @return asset path, or null for encoded memory
     */
    private static String path(SoundSource source) {
        /*
         * Java source variants retain the same public API on desktop and browser.
         */
        return source instanceof SoundSource.PathSource path ? path.path() : null;
    }

    /**
     * Probes a complete source while retaining PCM only within engine buffering limits.
     * @param source immutable encoded source
     * @return buffered or incremental sound descriptor
     */
    @Async
    public static native SoundData probe(SoundSource source);

    /**
     * Builds the existing engine descriptor from a bounded native probe result.
     * @param source sound origin
     * @param callback suspended Java continuation
     */
    private static void probe(SoundSource source, AsyncCallback<SoundData> callback) {
        /*
         * Copy retained small PCM once into Java ownership; long audio retains metadata only.
         */
        probeRequest(path(source), memory(source), (metadata, error) -> {
            if (error != null) {
                callback.error(new IllegalArgumentException(error));
                return;
            }
            Uint8Array bytes = metadata.getPcm();
            ByteBuffer pcm = null;
            if (bytes != null) {
                pcm = ByteBuffer.allocateDirect(bytes.getLength());
                for (int i = 0; i < bytes.getLength(); i++) pcm.put((byte) bytes.get(i));
                pcm.flip();
            }
            AudioFormat format = AudioFormat.valueOf(metadata.getFormat());
            callback.complete(new SoundData(pcm == null ? source : null, pcm, pcm == null ? (long) metadata.getOffset() : 0, pcm == null ? (long) metadata.getLength() : pcm.remaining(), metadata.getDuration(), metadata.getChannels(), metadata.getRate(), 16, pcm == null, format != AudioFormat.WAV, format));
        });
    }

    /**
     * Starts one bounded decoder probe.
     * @param path URL source token
     * @param bytes encoded memory or an empty marker
     * @param callback one-shot metadata outcome
     */
    @JSBody(params = {"path", "bytes", "callback"}, script = "globalThis.valthorneHost.audioBackend.streams.probe(path,bytes.length?bytes:null).then(value=>callback(value,null),error=>callback(null,String(error)));")
    private static native void probeRequest(String path, @JSBuffer(JSBufferType.UINT8) ByteBuffer bytes, BrowserSoundProbed callback);

    /**
     * Opens an independent incremental cursor.
     * @param source immutable encoded source
     * @return owned decoder token
     */
    @Async
    public static native BrowserSoundHandle open(SoundSource source);

    /**
     * Connects asynchronous decoder creation to its owning Java coroutine.
     * @param source sound origin
     * @param callback suspended Java continuation
     */
    private static void open(SoundSource source, AsyncCallback<BrowserSoundHandle> callback) {
        /*
         * Every cursor owns its own network reader and codec so seeking is isolated.
         */
        openRequest(path(source), memory(source), (handle, error) -> {
            if (error == null) callback.complete(handle);
            else callback.error(new IllegalArgumentException(error));
        });
    }

    /**
     * Begins incremental decoder creation.
     * @param path URL source token
     * @param bytes encoded memory or an empty marker
     * @param callback one-shot decoder outcome
     */
    @JSBody(params = {"path", "bytes", "callback"}, script = "globalThis.valthorneHost.audioBackend.streams.open(path,bytes.length?bytes:null).then(value=>callback(value,null),error=>callback(null,String(error)));")
    private static native void openRequest(String path, @JSBuffer(JSBufferType.UINT8) ByteBuffer bytes, BrowserSoundOpened callback);

    /**
     * Fills and flips a caller-owned PCM staging buffer.
     * @param handle open cursor
     * @param destination PCM scratch storage
     * @return copied byte count, zero at end of stream
     */
    @Async
    public static native int read(BrowserSoundHandle handle, ByteBuffer destination);

    /**
     * Copies the next bounded decode block into the shared player's staging buffer.
     * @param handle open cursor
     * @param destination owned PCM scratch
     * @param callback suspended Java continuation
     */
    private static void read(BrowserSoundHandle handle, ByteBuffer destination, AsyncCallback<Integer> callback) {
        /*
         * Preserve SoundStream's clear/read/flip contract across the asynchronous boundary.
         */
        destination.clear();
        readRequest(handle, destination.remaining(), (bytes, error) -> {
            if (error != null) {
                callback.error(new IllegalStateException(error));
                return;
            }
            for (int i = 0; i < bytes.getLength(); i++) destination.put((byte) bytes.get(i));
            destination.flip();
            callback.complete(bytes.getLength());
        });
    }

    /**
     * Requests a bounded PCM block from an open decoder.
     * @param handle open cursor
     * @param size requested maximum byte count
     * @param callback one-shot PCM outcome
     */
    @JSBody(params = {"handle", "size", "callback"}, script = "handle.read(size).then(bytes=>callback(bytes,null),error=>callback(null,String(error)));")
    private static native void readRequest(BrowserSoundHandle handle, int size, BrowserSoundRead callback);

    /**
     * Moves one decoder cursor to a finite nonnegative playback time.
     * @param handle open cursor
     * @param seconds desired time in seconds
     * @return successful seek
     */
    @Async
    public static native boolean seek(BrowserSoundHandle handle, float seconds);

    /**
     * Waits for incremental decoding to reach the requested cursor.
     * @param handle open cursor
     * @param seconds desired time
     * @param callback suspended Java continuation
     */
    private static void seek(BrowserSoundHandle handle, float seconds, AsyncCallback<Boolean> callback) {
        /*
         * Seeking restarts only this decoder and retains the same bounded working set.
         */
        seekRequest(handle, seconds, (success, error) -> {
            if (success) callback.complete(true);
            else callback.error(new IllegalStateException(error));
        });
    }

    /**
     * Starts a decoder seek.
     * @param handle open cursor
     * @param seconds desired time
     * @param callback one-shot seek outcome
     */
    @JSBody(params = {"handle", "seconds", "callback"}, script = "handle.seek(seconds).then(()=>callback(true,null),error=>callback(false,String(error)));")
    private static native void seekRequest(BrowserSoundHandle handle, float seconds, BrowserAudioResult callback);

    /**
     * Releases a decoder and aborts any pending network read.
     * @param handle cursor to release
     */
    @JSBody(params = "handle", script = "handle.close();")
    public static native void close(BrowserSoundHandle handle);
}
