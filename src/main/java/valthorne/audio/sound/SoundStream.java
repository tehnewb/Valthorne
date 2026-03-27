package valthorne.audio.sound;

import java.nio.ByteBuffer;

/**
 * <p>
 * {@code SoundStream} represents a pull-based decoded PCM stream used for long or
 * compressed audio playback. Implementations expose stream metadata, seeking, chunked
 * reading, and resource cleanup.
 * </p>
 *
 * <h2>Example Usage</h2>
 *
 * <pre>{@code
 * try (SoundStream stream = soundData.openStream()) {
 *     ByteBuffer chunk = BufferUtils.createByteBuffer(64 * 1024);
 *     stream.seek(5f);
 *     int bytesRead = stream.read(chunk);
 * }
 * }</pre>
 *
 * @author Albert Beaupre
 * @since March 26th, 2026
 */
public interface SoundStream extends AutoCloseable {

    /**
     * Returns the number of channels produced by this stream.
     *
     * @return the channel count
     */
    int channels();

    /**
     * Returns the sample rate produced by this stream.
     *
     * @return the sample rate in hertz
     */
    int sampleRate();

    /**
     * Returns the number of bits per sample produced by this stream.
     *
     * @return the bits per sample
     */
    int bitsPerSample();

    /**
     * Returns the duration of the underlying audio.
     *
     * @return the duration in seconds
     */
    float duration();

    /**
     * Seeks to a target playback time.
     *
     * @param seconds the time in seconds to seek to
     * @return {@code true} when the seek succeeded
     */
    boolean seek(float seconds);

    /**
     * Reads decoded PCM data into the supplied buffer.
     *
     * @param pcmBuffer the destination PCM buffer
     * @return the number of bytes read
     */
    int read(ByteBuffer pcmBuffer);

    /**
     * Closes this stream and releases any underlying resources.
     */
    @Override
    void close();
}
