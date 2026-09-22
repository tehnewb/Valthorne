package valthorne.audio.sound;

import org.lwjgl.BufferUtils;
import org.lwjgl.stb.STBVorbis;
import org.lwjgl.stb.STBVorbisInfo;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.libc.LibCStdlib;
import valthorne.audio.AudioFormat;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Decodes complete OGG Vorbis payloads into interleaved 16-bit PCM and probes
 * metadata without retaining a streaming decoder. Probing accepts either a file
 * path or encoded bytes; full decoding accepts bytes and returns independent
 * buffered SoundData. Temporary native decoder and decoded-sample allocations
 * are released after use.
 * <p>For long audio that should not be held fully decoded in memory, use the separate
 * OggSoundStream implementation. This decoder creates no OpenAL playback source.
 *
 * @author Albert Beaupre
 */
public class OggSoundDecoder implements SoundDecoder {

    /**
     * Opens a temporary native decoder over a direct copy of the encoded bytes and
     * reads channels, sample rate, and stream length. No PCM buffer is returned; the
     * decoder is closed even when metadata extraction fails.
     *
     * @param data complete encoded OGG Vorbis payload
     * @return metadata with 16-bit PCM depth, encoded byte length, and duration in
     * seconds, or minus one duration when sample count is unavailable
     * @throws RuntimeException if STB cannot open the encoded stream
     */
    public static SoundMetadata probe(byte[] data) {
        ByteBuffer encoded = BufferUtils.createByteBuffer(data.length);
        encoded.put(data).flip();

        IntBuffer error = BufferUtils.createIntBuffer(1);
        long decoder = STBVorbis.stb_vorbis_open_memory(encoded, error, null);
        if (decoder == MemoryUtil.NULL) {
            throw new RuntimeException("Failed to open OGG file: " + error.get(0));
        }

        try {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                STBVorbisInfo info = STBVorbisInfo.malloc(stack);
                STBVorbis.stb_vorbis_get_info(decoder, info);
                int channels = info.channels();
                int sampleRate = info.sample_rate();
                int sampleCount = STBVorbis.stb_vorbis_stream_length_in_samples(decoder);
                float duration = sampleCount > 0 ? sampleCount / (float) sampleRate : -1f;
                return new SoundMetadata(duration, channels, sampleRate, 16, 0L, data.length);
            }
        } finally {
            STBVorbis.stb_vorbis_close(decoder);
        }
    }

    /**
     * Opens a temporary decoder using an absolute filesystem path, reads metadata,
     * and closes the decoder. A separate file-size failure records minus one encoded
     * bytes without discarding successfully read audio metadata.
     *
     * @param path filesystem path to an OGG Vorbis file
     * @return metadata with 16-bit PCM depth and duration in seconds; unavailable
     * duration or encoded byte count is represented by minus one
     * @throws RuntimeException if STB cannot open the file
     */
    public static SoundMetadata probe(String path) {
        ByteBuffer fileNameBuffer = MemoryUtil.memUTF8(Path.of(path).toAbsolutePath().toString());
        IntBuffer error = BufferUtils.createIntBuffer(1);
        long decoder = STBVorbis.stb_vorbis_open_filename(fileNameBuffer, error, null);
        MemoryUtil.memFree(fileNameBuffer);

        if (decoder == MemoryUtil.NULL) {
            throw new RuntimeException("Failed to open OGG file: " + error.get(0));
        }

        try {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                STBVorbisInfo info = STBVorbisInfo.malloc(stack);
                STBVorbis.stb_vorbis_get_info(decoder, info);
                int channels = info.channels();
                int sampleRate = info.sample_rate();
                int sampleCount = STBVorbis.stb_vorbis_stream_length_in_samples(decoder);
                float duration = sampleCount > 0 ? sampleCount / (float) sampleRate : -1f;
                long encodedBytes;
                try {
                    encodedBytes = Files.size(Path.of(path));
                } catch (Exception e) {
                    encodedBytes = -1L;
                }
                return new SoundMetadata(duration, channels, sampleRate, 16, 0L, encodedBytes);
            }
        } finally {
            STBVorbis.stb_vorbis_close(decoder);
        }
    }

    /**
     * Decodes the complete payload into interleaved signed 16-bit PCM.
     * Copies native STB samples into a separate direct buffer before freeing the STB
     * allocation. Returned SoundData retains this PCM buffer and does not retain the
     * encoded input or create an OpenAL source.
     *
     * @param data complete encoded OGG Vorbis bytes
     * @return buffered PCM data with channel count, sample rate, and duration
     * @throws Exception if decoding fails or the decoded byte count cannot be represented
     */
    @Override
    public SoundData decode(byte[] data) throws Exception {
        ByteBuffer encoded = BufferUtils.createByteBuffer(data.length);
        encoded.put(data).flip();

        IntBuffer channelsBuffer = BufferUtils.createIntBuffer(1);
        IntBuffer sampleRateBuffer = BufferUtils.createIntBuffer(1);
        ShortBuffer pcm = STBVorbis.stb_vorbis_decode_memory(encoded, channelsBuffer, sampleRateBuffer);

        if (pcm == null) {
            throw new RuntimeException("Failed to decode OGG file");
        }

        int channels = channelsBuffer.get(0);
        int sampleRate = sampleRateBuffer.get(0);
        int bitsPerSample = 16;
        int pcmSamples = pcm.remaining();
        try {
            ByteBuffer pcmBytes = BufferUtils.createByteBuffer(Math.multiplyExact(pcmSamples, 2));
            // Do not advance the owning buffer: free() uses its current address.
            pcmBytes.asShortBuffer().put(pcm.duplicate());
            float duration = pcmSamples / (float) (channels * sampleRate);
            return new SoundData(null, pcmBytes, 0L, pcmBytes.remaining(), duration, channels, sampleRate, bitsPerSample, false, true, AudioFormat.OGG);
        } finally {
            LibCStdlib.free(pcm);
        }
    }
}
