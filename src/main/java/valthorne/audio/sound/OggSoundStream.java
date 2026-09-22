package valthorne.audio.sound;

import org.lwjgl.BufferUtils;
import org.lwjgl.stb.STBVorbis;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ReadOnlyBufferException;
import java.nio.ShortBuffer;
import java.nio.file.Path;

/**
 * Decodes file-backed or memory-backed OGG Vorbis audio into interleaved signed
 * 16-bit PCM through an owned native STB decoder. Reads and seeks share one decoder
 * position and must be externally serialized; instances are not thread-safe.
 * <p>Close the stream to release the native decoder. Closing is idempotent and leaves
 * metadata accessible, but further reads and seeks fail. Memory-backed encoded data
 * is retained for the decoder's lifetime.
 *
 * @author Albert Beaupre
 */
public class OggSoundStream implements SoundStream {

    private final SoundData data; // Stream description and metadata used by this decoder
    private final ByteBuffer encodedData; // Encoded OGG data retained for memory-backed sources
    private final long decoder; // Native STB Vorbis decoder handle
    private ByteBuffer lastDestination; // Last caller-owned PCM buffer, retained only to reuse its short view.
    private ShortBuffer sampleView; // Cached signed-sample view of the last destination buffer.
    private boolean closed; // Whether the native decoder has already been released.

    /**
     * Creates a new OGG stream for the supplied sound data.
     *
     * @param data the stream-backed sound data
     */
    public OggSoundStream(SoundData data) {
        this.data = data;

        try {
            IntBuffer error = BufferUtils.createIntBuffer(1);

            if (data.source() instanceof SoundSource.PathSource(String path)) {
                this.encodedData = null;
                ByteBuffer fileNameBuffer = MemoryUtil.memUTF8(Path.of(path).toAbsolutePath().toString());
                this.decoder = STBVorbis.stb_vorbis_open_filename(fileNameBuffer, error, null);
                MemoryUtil.memFree(fileNameBuffer);
            } else if (data.source() instanceof SoundSource.BytesSource(byte[] bytes)) {
                this.encodedData = BufferUtils.createByteBuffer(bytes.length);
                this.encodedData.put(bytes).flip();
                this.decoder = STBVorbis.stb_vorbis_open_memory(encodedData, error, null);
            } else {
                throw new IllegalStateException("OGG stream requires a path or byte source");
            }

            if (decoder == MemoryUtil.NULL) {
                throw new RuntimeException("Failed to open OGG stream: " + error.get(0));
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Reads the decoded PCM channel count without advancing the stream cursor.
     *
     * @return the channel count produced by this stream
     */
    @Override
    public int channels() {
        return data.channels();
    }

    /**
     * Reads the decoded PCM sampling frequency in frames per second (Hz), independent of playback position.
     *
     * @return the sample rate produced by this stream
     */
    @Override
    public int sampleRate() {
        return data.sampleRate();
    }

    /**
     * Reads the decoded PCM bit depth per channel sample, rather than the compressed file bitrate.
     *
     * @return the bits per sample produced by this stream
     */
    @Override
    public int bitsPerSample() {
        return data.bitsPerSample();
    }

    /**
     * Reads total decoded-content duration in seconds, not remaining time or the current playback position.
     *
     * @return the total stream duration in seconds
     */
    @Override
    public float duration() {
        return data.duration();
    }

    /**
     * Seeks to a target playback time.
     *
     * @param seconds the target time in seconds
     * @return {@code true} if seeking succeeded
     */
    @Override
    public boolean seek(float seconds) {
        ensureOpen();
        int sample = Math.max(0, Math.round(clamp(seconds) * sampleRate()));
        return STBVorbis.stb_vorbis_seek(decoder, sample);
    }

    /**
     * Decodes from the current stream position into a direct PCM destination.
     * The destination is cleared first, so its incoming position and limit are ignored.
     * On return its position is zero and its limit is the number of decoded bytes.
     * Samples are interleaved signed 16-bit values; use native byte order when reading
     * them. A cached short view is reused while the same destination object is supplied.
     *
     * @param pcmBuffer writable direct buffer for complete interleaved PCM frames
     * @return bytes produced, or zero when no more frames can be decoded
     * @throws IllegalStateException if this stream has been closed
     */
    @Override
    public int read(ByteBuffer pcmBuffer) {
        ensureOpen();
        if (!pcmBuffer.isDirect()) throw new IllegalArgumentException("OGG decoding requires a direct PCM buffer");
        if (pcmBuffer.isReadOnly()) throw new ReadOnlyBufferException();
        pcmBuffer.clear();

        if (lastDestination != pcmBuffer) {
            lastDestination = pcmBuffer;
            sampleView = pcmBuffer.asShortBuffer();
        }
        ShortBuffer shortBuffer = sampleView;
        shortBuffer.clear();
        int totalShorts = 0;
        int maxShorts = shortBuffer.remaining();

        while (totalShorts < maxShorts) {
            shortBuffer.position(totalShorts);
            int frames = STBVorbis.stb_vorbis_get_samples_short_interleaved(decoder, channels(), shortBuffer);
            if (frames <= 0) {
                break;
            }
            totalShorts += frames * channels();
        }

        int bytesRead = totalShorts * 2;
        pcmBuffer.position(0);
        pcmBuffer.limit(bytesRead);
        return bytesRead;
    }

    /**
     * Closes the native decoder handle.
     */
    @Override
    public void close() {
        if (closed) return;
        closed = true;
        STBVorbis.stb_vorbis_close(decoder);
        lastDestination = null;
        sampleView = null;
    }

    /**
     * Guards operations that dereference the native decoder. Metadata access does not
     * require an open decoder, but reads and seeks call this before entering STB.
     *
     * @throws IllegalStateException if close has already released the decoder
     */
    private void ensureOpen() {
        if (closed) throw new IllegalStateException("OGG stream is closed");
    }

    /**
     * Clamps a time value into the valid stream range.
     *
     * @param seconds the time in seconds to clamp
     * @return the clamped time
     */
    private float clamp(float seconds) {
        if (data.duration() <= 0f) {
            return Math.max(0f, seconds);
        }
        return Math.max(0f, Math.min(seconds, data.duration()));
    }
}
