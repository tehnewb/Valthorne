package valthorne.sound;

import org.lwjgl.BufferUtils;
import org.lwjgl.stb.STBVorbis;
import org.lwjgl.system.libc.LibCStdlib;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;

public class OggDecoder implements SoundDecoder {

    private static final IntBuffer channelsBuf = BufferUtils.createIntBuffer(1);
    private static final IntBuffer sampleRateBuf = BufferUtils.createIntBuffer(1);

    @Override
    public SoundData load(byte[] data) throws Exception {
        ByteBuffer buffer = BufferUtils.createByteBuffer(data.length);
        buffer.put(data).flip();

        ShortBuffer pcm = STBVorbis.stb_vorbis_decode_memory(buffer, channelsBuf, sampleRateBuf);

        if (pcm == null)
            throw new RuntimeException("Failed to decode OGG file");

        int channels = channelsBuf.get(0);
        int sampleRate = sampleRateBuf.get(0);
        int bitsPerSample = 16;


        ByteBuffer pcmBytes = BufferUtils.createByteBuffer(pcm.remaining() * 2);
        short[] pcm16 = new short[pcm.remaining()];

        for (int i = 0; i < pcm16.length; i++) {
            short s = pcm.get(i);
            pcmBytes.putShort(s);
            pcm16[i] = s;
        }
        pcmBytes.flip();

        float duration = pcm16.length / (float) (channels * sampleRate);
        LibCStdlib.free(pcm);

        return new SoundData(pcmBytes, duration, channels, sampleRate, bitsPerSample, pcm16);
    }
}
