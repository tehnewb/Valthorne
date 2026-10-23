package valthorne.browser.audio;

import org.teavm.jso.JSObject;
import org.teavm.jso.JSProperty;
import org.teavm.jso.typedarrays.Uint8Array;

/**
 * Immutable decoder probe result with optional bounded interleaved PCM16 data.
 */
public interface BrowserSoundMetadata extends JSObject {
    /**
     * Returns the detected engine audio format name.
     * @return WAV, OGG, or MP3
     */
    @JSProperty
    String getFormat();

    /**
     * Returns the mono or stereo channel count.
     * @return channel count
     */
    @JSProperty
    int getChannels();

    /**
     * Returns the decoded sampling frequency.
     * @return samples per second
     */
    @JSProperty
    int getRate();

    /**
     * Returns the complete duration found by probing the source.
     * @return duration in seconds
     */
    @JSProperty
    float getDuration();

    /**
     * Returns the encoded payload offset.
     * @return byte offset
     */
    @JSProperty
    double getOffset();

    /**
     * Returns the encoded payload size.
     * @return byte count
     */
    @JSProperty
    double getLength();

    /**
     * Returns buffered PCM only when the engine's size and duration limits permit it.
     * @return PCM bytes, or null for incremental playback
     */
    @JSProperty
    Uint8Array getPcm();
}
