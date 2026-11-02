package valthorne.audio;

import java.nio.ByteBuffer;
import valthorne.browser.audio.BrowserAudioStreams;
import valthorne.browser.audio.BrowserSoundHandle;

/**
 * Owns one independently seekable incremental decoder. Only a bounded PCM block
 * is retained; pending reads suspend the audio coroutine, leaving rendering free.
 * Closing releases the decoder, encoded source, and outstanding network reader.
 */
final class BrowserPcmStream implements SoundStream {
    private final SoundData sound; // Immutable format and sample metadata.
    private BrowserSoundHandle handle; // Owned incremental decoder, null after close.

    /**
     * Opens a fresh cursor over a sound's immutable encoded source.
     * @param sound stream-backed sound metadata
     */
    BrowserPcmStream(SoundData sound) {
        this.sound = sound;
        handle = BrowserAudioStreams.open(sound.source());
    }

    @Override
    public int channels() { return sound.channels(); }

    @Override
    public int sampleRate() { return sound.sampleRate(); }

    @Override
    public int bitsPerSample() { return sound.bitsPerSample(); }

    @Override
    public float duration() { return sound.duration(); }

    @Override
    public boolean seek(float seconds) {
        checkOpen();
        if (!Float.isFinite(seconds)) throw new IllegalArgumentException("Seek time must be finite");
        return BrowserAudioStreams.seek(handle, seconds);
    }

    @Override
    public int read(ByteBuffer destination) {
        checkOpen();
        return BrowserAudioStreams.read(handle, destination);
    }

    @Override
    public void close() {
        if (handle == null) return;
        BrowserAudioStreams.close(handle);
        handle = null;
    }

    /**
     * Rejects cursor operations after native decoder ownership has ended.
     */
    private void checkOpen() {
        if (handle == null) throw new IllegalStateException("Sound stream is closed");
    }
}
