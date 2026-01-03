package valthorne.sound;

import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class WaveDecoder implements SoundDecoder {
    @Override
    public SoundData load(byte[] bytes) throws Exception {
        ByteBuffer data = BufferUtils.createByteBuffer(bytes.length);
        data.put(bytes).flip();
        data.order(ByteOrder.LITTLE_ENDIAN);
        if (!readString(data).equals("RIFF")) throw new RuntimeException("Invalid WAV");
        data.position(data.position() + 4);
        if (!readString(data).equals("WAVE")) throw new RuntimeException("Invalid WAV");
        ByteBuffer audioData = null;
        int channels = 0, sampleRate = 0, dataSize = 0;
        int bitsPerSample = 16;
        while (data.hasRemaining()) {
            String chunk = readString(data);
            int chunkSize = data.getInt();
            if (chunk.equals("fmt ")) {
                int fmt = data.getShort() & 0xFFFF;
                channels = data.getShort() & 0xFFFF;
                sampleRate = data.getInt();
                data.position(data.position() + 6);
                int bits = data.getShort() & 0xFFFF;
                if (fmt != 1 || bits != 16) throw new RuntimeException("Only PCM16 WAV supported");
                bitsPerSample = bits;
                if (chunkSize > 16) data.position(data.position() + (chunkSize - 16));
            } else if (chunk.equals("buffer")) {
                dataSize = chunkSize;
                audioData = data.slice();
                audioData.limit(chunkSize);
                break;
            } else if (chunk.equals("data")) {   // <-- FIXED HERE
                dataSize = chunkSize;
                audioData = data.slice();
                audioData.limit(chunkSize);
                break;
            } else {
                data.position(data.position() + chunkSize);
            }
        }
        float duration = dataSize / (float) (channels * sampleRate * 2); // Copy PCM16
        short[] pcm16 = new short[dataSize / 2];
        audioData.asShortBuffer().get(pcm16);
        return new SoundData(audioData, duration, channels, sampleRate, bitsPerSample, pcm16);
    }

    private String readString(ByteBuffer buf) {
        byte[] out = new byte[4];
        buf.get(out);
        return new String(out);
    }
}