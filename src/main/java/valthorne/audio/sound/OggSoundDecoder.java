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
import java.nio.file.Path;

/**
 * <p>
 * {@code OggSoundDecoder} decodes OGG Vorbis audio using STB Vorbis and also exposes
 * metadata probing helpers for both memory and file-path sources.
 * </p>
 *
 * @author Albert Beaupre
 * @since March 26th, 2026
 */
public class OggSoundDecoder implements SoundDecoder {

    /**
     * Decodes OGG Vorbis bytes into buffered PCM sound data.
     *
     * @param data the encoded OGG bytes
     * @return the decoded sound data
     * @throws Exception if decoding fails
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
        ByteBuffer pcmBytes = BufferUtils.createByteBuffer(pcmSamples * 2);
        pcmBytes.asShortBuffer().put(pcm);
        pcmBytes.limit(pcmSamples * 2);

        float duration = pcmSamples / (float) (channels * sampleRate);
        LibCStdlib.free(pcm);

        return new SoundData(null, pcmBytes, 0L, pcmBytes.remaining(), duration, channels, sampleRate, bitsPerSample, false, true, AudioFormat.OGG);
    }

    /**
     * Probes OGG metadata from encoded bytes.
     *
     * @param data the encoded OGG bytes
     * @return the probed metadata
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
     * Probes OGG metadata from a file path.
     *
     * @param path the file path to inspect
     * @return the probed metadata
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
                    encodedBytes = java.nio.file.Files.size(Path.of(path));
                } catch (Exception e) {
                    encodedBytes = -1L;
                }
                return new SoundMetadata(duration, channels, sampleRate, 16, 0L, encodedBytes);
            }
        } finally {
            STBVorbis.stb_vorbis_close(decoder);
        }
    }
}
