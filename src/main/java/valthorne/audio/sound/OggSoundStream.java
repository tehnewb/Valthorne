package valthorne.audio.sound;

import org.lwjgl.BufferUtils;
import org.lwjgl.stb.STBVorbis;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.nio.file.Path;

/**
 * <p>
 * {@code OggSoundStream} provides chunked PCM decoding for OGG Vorbis audio using
 * STB Vorbis. It supports both file-path-backed and in-memory sources.
 * </p>
 *
 * @author Albert Beaupre
 * @since March 26th, 2026
 */
public class OggSoundStream implements SoundStream {

    private final SoundData data; // Stream description and metadata used by this decoder
    private final ByteBuffer encodedData; // Encoded OGG data retained for memory-backed sources
    private final long decoder; // Native STB Vorbis decoder handle

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
     * @return the channel count produced by this stream
     */
    @Override
    public int channels() {return data.channels();}

    /**
     * @return the sample rate produced by this stream
     */
    @Override
    public int sampleRate() {return data.sampleRate();}

    /**
     * @return the bits per sample produced by this stream
     */
    @Override
    public int bitsPerSample() {return data.bitsPerSample();}

    /**
     * @return the total stream duration in seconds
     */
    @Override
    public float duration() {return data.duration();}

    /**
     * Seeks to a target playback time.
     *
     * @param seconds the target time in seconds
     * @return {@code true} if seeking succeeded
     */
    @Override
    public boolean seek(float seconds) {
        int sample = Math.max(0, Math.round(clamp(seconds) * sampleRate()));
        return STBVorbis.stb_vorbis_seek(decoder, sample);
    }

    /**
     * Reads decoded PCM data into the supplied buffer.
     *
     * @param pcmBuffer the destination PCM buffer
     * @return the number of bytes read
     */
    @Override
    public int read(ByteBuffer pcmBuffer) {
        pcmBuffer.clear();

        ShortBuffer shortBuffer = pcmBuffer.asShortBuffer();
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
        STBVorbis.stb_vorbis_close(decoder);
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
