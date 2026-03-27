package valthorne.audio.sound;

import java.io.RandomAccessFile;
import java.nio.ByteBuffer;

/**
 * <p>
 * {@code WaveSoundStream} provides pull-based chunked PCM reading for WAV audio that
 * is being played through Valthorne's streaming pipeline. It can stream either from a
 * filesystem-backed source or directly from an in-memory byte array, depending on the
 * {@link SoundSource} stored inside the owning {@link SoundData}.
 * </p>
 *
 * <p>
 * Because WAV audio is already stored as PCM in the supported formats, the stream does
 * not need to transcode sample data on the fly. Instead, it reads byte ranges directly
 * from the WAV payload described by {@link SoundData#streamOffset()} and
 * {@link SoundData#streamLength()} and copies them into caller-supplied buffers.
 * </p>
 *
 * <p>
 * Seeking is implemented by moving to a byte position derived from the requested time,
 * aligned to the current frame size so channel/sample boundaries are preserved.
 * </p>
 *
 * <h2>Example Usage</h2>
 *
 * <pre>{@code
 * SoundData sound = SoundData.load("assets/music/theme.wav");
 *
 * try (WaveSoundStream stream = new WaveSoundStream(sound)) {
 *     ByteBuffer pcm = ByteBuffer.allocateDirect(64 * 1024);
 *
 *     stream.seek(3.5f);
 *     int bytesRead = stream.read(pcm);
 *
 *     int channels = stream.channels();
 *     int sampleRate = stream.sampleRate();
 *     int bitsPerSample = stream.bitsPerSample();
 *     float duration = stream.duration();
 * }
 * }</pre>
 *
 * <p>
 * This example demonstrates the complete usage of the class: construction, seeking,
 * PCM reading, metadata access, and resource cleanup.
 * </p>
 *
 * @author Albert Beaupre
 * @since March 26th, 2026
 */
public class WaveSoundStream implements SoundStream {

    private final SoundData data; // Stream metadata and source description for this WAV stream
    private final byte[] memoryBytes; // In-memory source bytes when streaming from a byte-backed source
    private final RandomAccessFile file; // Random-access file handle when streaming from disk
    private final byte[] scratch; // Temporary transfer buffer used for file-backed reads
    private final int frameSize; // Size in bytes of one PCM frame for this stream
    private long position; // Current byte position inside the WAV payload portion of the stream

    /**
     * <p>
     * Creates a new WAV stream for the supplied {@link SoundData}.
     * </p>
     *
     * <p>
     * The constructor determines whether streaming should pull bytes from a file path or
     * an in-memory byte array. It also computes the PCM frame size so future seeks and
     * reads can remain frame aligned.
     * </p>
     *
     * @param data the stream-backed sound data describing the WAV payload
     */
    public WaveSoundStream(SoundData data) {
        this.data = data;
        this.frameSize = Math.max(1, data.channels() * (data.bitsPerSample() / 8));
        this.scratch = new byte[64 * 1024];

        try {
            if (data.source() instanceof SoundSource.PathSource(String path)) {
                this.file = new RandomAccessFile(path, "r");
                this.file.seek(data.streamOffset());
                this.memoryBytes = null;
            } else if (data.source() instanceof SoundSource.BytesSource(byte[] bytes)) {
                this.memoryBytes = bytes;
                this.file = null;
            } else {
                throw new IllegalStateException("Wave stream requires a path or byte source");
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * <p>
     * Returns the channel count produced by this stream.
     * </p>
     *
     * @return the PCM channel count
     */
    @Override
    public int channels() {
        return data.channels();
    }

    /**
     * <p>
     * Returns the sample rate produced by this stream.
     * </p>
     *
     * @return the PCM sample rate in hertz
     */
    @Override
    public int sampleRate() {
        return data.sampleRate();
    }

    /**
     * <p>
     * Returns the bits per sample produced by this stream.
     * </p>
     *
     * @return the PCM bit depth
     */
    @Override
    public int bitsPerSample() {
        return data.bitsPerSample();
    }

    /**
     * <p>
     * Returns the total duration of the underlying sound.
     * </p>
     *
     * @return the duration in seconds
     */
    @Override
    public float duration() {
        return data.duration();
    }

    /**
     * <p>
     * Seeks to a target playback time.
     * </p>
     *
     * <p>
     * The requested time is clamped into the valid duration range, converted into a byte
     * offset using the stream's sample rate and frame size, then aligned down to the
     * nearest full frame. The internal byte position is updated, and file-backed streams
     * also move their random-access pointer to the new payload location.
     * </p>
     *
     * @param seconds the desired seek time in seconds
     * @return {@code true} when the seek completed successfully
     */
    @Override
    public boolean seek(float seconds) {
        try {
            long target = Math.round(clamp(seconds) * data.sampleRate() * frameSize);
            target -= target % frameSize;
            target = Math.max(0L, Math.min(target, data.streamLength()));
            position = target;

            if (file != null) {
                file.seek(data.streamOffset() + position);
            }

            return true;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * <p>
     * Reads the next chunk of PCM data into the supplied buffer.
     * </p>
     *
     * <p>
     * The method respects the remaining stream length, clamps the requested read size to
     * the destination buffer capacity, and keeps the read aligned to whole PCM frames.
     * File-backed sources copy through a reusable scratch array, while memory-backed
     * sources copy directly from the in-memory byte array.
     * </p>
     *
     * <p>
     * The destination buffer is cleared before writing and flipped before returning.
     * When no data remains, the buffer is returned empty and {@code 0} is reported.
     * </p>
     *
     * @param pcmBuffer the destination PCM buffer
     * @return the number of bytes written into the buffer
     */
    @Override
    public int read(ByteBuffer pcmBuffer) {
        try {
            long remaining = data.streamLength() - position;
            if (remaining <= 0L) {
                pcmBuffer.clear();
                pcmBuffer.flip();
                return 0;
            }

            int maxBytes = Math.min(pcmBuffer.capacity(), (int) Math.min(Integer.MAX_VALUE, remaining));
            maxBytes -= maxBytes % frameSize;
            if (maxBytes <= 0) {
                pcmBuffer.clear();
                pcmBuffer.flip();
                return 0;
            }

            pcmBuffer.clear();

            if (file != null) {
                int bytesRead = file.read(scratch, 0, Math.min(scratch.length, maxBytes));
                if (bytesRead <= 0) {
                    pcmBuffer.flip();
                    return 0;
                }
                bytesRead -= bytesRead % frameSize;
                if (bytesRead <= 0) {
                    pcmBuffer.flip();
                    return 0;
                }
                pcmBuffer.put(scratch, 0, bytesRead);
                pcmBuffer.flip();
                position += bytesRead;
                return bytesRead;
            }

            pcmBuffer.put(memoryBytes, (int) (data.streamOffset() + position), maxBytes);
            pcmBuffer.flip();
            position += maxBytes;
            return maxBytes;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * <p>
     * Closes this stream and releases any file handle it owns.
     * </p>
     *
     * <p>
     * Memory-backed streams do not own a file handle, so closing them only matters for
     * consistency. File-backed streams silently ignore secondary close failures.
     * </p>
     */
    @Override
    public void close() {
        if (file != null) {
            try {
                file.close();
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * <p>
     * Clamps a requested time into the valid playback range.
     * </p>
     *
     * <p>
     * If the stream duration is not known or not positive, only a lower bound of zero is
     * enforced. Otherwise the time is clamped into {@code [0, duration]}.
     * </p>
     *
     * @param seconds the requested time in seconds
     * @return the clamped time in seconds
     */
    private float clamp(float seconds) {
        if (data.duration() <= 0f) {
            return Math.max(0f, seconds);
        }
        return Math.max(0f, Math.min(seconds, data.duration()));
    }
}
