package valthorne.sound;

import org.lwjgl.BufferUtils;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;

public class Mp3Decoder implements SoundDecoder {
    @Override
    public SoundData load(byte[] data) throws Exception {
        AudioInputStream in = AudioSystem.getAudioInputStream(new ByteArrayInputStream(data));
        AudioFormat f = in.getFormat();
        AudioFormat pcmFmt = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, f.getSampleRate(), 16, f.getChannels(), f.getChannels() * 2, f.getSampleRate(), false);
        byte[] b = AudioSystem.getAudioInputStream(pcmFmt, in).readAllBytes();
        ByteBuffer buf = BufferUtils.createByteBuffer(b.length).put(b).flip();

        int ch = pcmFmt.getChannels();
        int rate = (int) pcmFmt.getSampleRate();
        float dur = (b.length / 2f) / (ch * rate);

        short[] s = new short[b.length / 2];
        for (int i = 0; i < s.length; i++)
            s[i] = (short) ((b[i * 2 + 1] << 8) | (b[i * 2] & 0xFF));

        return new SoundData(buf, dur, ch, rate, 16, s);
    }
}
