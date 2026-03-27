package valthorne.audio.sound;

import org.lwjgl.BufferUtils;
import valthorne.audio.AudioFormat;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.ByteBuffer;
import java.util.Map;

/**
 * <p>
 * {@code Mp3SoundDecoder} decodes MP3 audio into 16-bit signed PCM using the Java
 * sound system. It also exposes probing helpers that extract lightweight metadata
 * without building a full {@link SoundData} instance.
 * </p>
 *
 * @author Albert Beaupre
 * @since March 26th, 2026
 */
public class Mp3SoundDecoder implements SoundDecoder {

    /**
     * Decodes MP3 bytes into buffered PCM sound data.
     *
     * @param data the encoded MP3 bytes
     * @return the decoded sound data
     * @throws Exception if decoding fails
     */
    @Override
    public SoundData decode(byte[] data) throws Exception {
        try (AudioInputStream input = AudioSystem.getAudioInputStream(new ByteArrayInputStream(data))) {
            javax.sound.sampled.AudioFormat sourceFormat = input.getFormat();
            javax.sound.sampled.AudioFormat pcmFormat = new javax.sound.sampled.AudioFormat(javax.sound.sampled.AudioFormat.Encoding.PCM_SIGNED, sourceFormat.getSampleRate(), 16, sourceFormat.getChannels(), sourceFormat.getChannels() * 2, sourceFormat.getSampleRate(), false);

            try (AudioInputStream pcmInput = AudioSystem.getAudioInputStream(pcmFormat, input)) {
                byte[] pcmBytes = pcmInput.readAllBytes();
                ByteBuffer pcm = BufferUtils.createByteBuffer(pcmBytes.length);
                pcm.put(pcmBytes).flip();
                float duration = pcmBytes.length / (pcmFormat.getChannels() * pcmFormat.getSampleRate() * 2);
                return new SoundData(null, pcm, 0L, pcmBytes.length, duration, pcmFormat.getChannels(), (int) pcmFormat.getSampleRate(), 16, false, true, AudioFormat.MP3);
            }
        }
    }

    /**
     * Probes MP3 metadata from an in-memory byte array.
     *
     * @param data the encoded MP3 bytes
     * @return the probed metadata
     * @throws Exception if probing fails
     */
    public static SoundMetadata probe(byte[] data) throws Exception {
        AudioFileFormat fileFormat = AudioSystem.getAudioFileFormat(new ByteArrayInputStream(data));

        try (AudioInputStream input = AudioSystem.getAudioInputStream(new ByteArrayInputStream(data))) {
            javax.sound.sampled.AudioFormat format = input.getFormat();
            float duration = extractDurationSeconds(fileFormat.properties(), fileFormat.getFrameLength(), format.getFrameRate());
            return new SoundMetadata(duration, format.getChannels(), (int) format.getSampleRate(), 16, 0L, data.length);
        }
    }

    /**
     * Probes MP3 metadata from a file path.
     *
     * @param path the file path to inspect
     * @return the probed metadata
     * @throws Exception if probing fails
     */
    public static SoundMetadata probe(String path) throws Exception {
        File file = new File(path);
        AudioFileFormat fileFormat = AudioSystem.getAudioFileFormat(file);

        try (AudioInputStream input = AudioSystem.getAudioInputStream(file)) {
            javax.sound.sampled.AudioFormat format = input.getFormat();
            float duration = extractDurationSeconds(fileFormat.properties(), fileFormat.getFrameLength(), format.getFrameRate());
            return new SoundMetadata(duration, format.getChannels(), (int) format.getSampleRate(), 16, 0L, file.length());
        }
    }

    /**
     * Extracts duration information from Java Sound metadata.
     *
     * @param properties  the audio file property map
     * @param frameLength the frame length reported by the file
     * @param frameRate   the frame rate reported by the format
     * @return the duration in seconds, or {@code -1f} when unavailable
     */
    private static float extractDurationSeconds(Map<String, Object> properties, long frameLength, float frameRate) {
        Object duration = properties.get("duration");
        if (duration instanceof Number number) {
            return number.longValue() / 1_000_000f;
        }

        if (frameLength > 0 && frameRate > 0f) {
            return frameLength / frameRate;
        }

        return -1f;
    }
}
