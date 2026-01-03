package valthorne.sound;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Represents audio buffer for use in sound processing and playback systems.
 * This immutable record encapsulates details about the raw audio buffer and its format.
 *
 * @param data          A ByteBuffer containing the raw PCM audio buffer in the specified format.
 * @param duration      The length of the audio in seconds.
 * @param channels      The number of audio channels (e.g., 1 for mono, 2 for stereo).
 * @param sampleRate    The sample rate of the audio in Hz, representing samples per second.
 * @param bitsPerSample The number of bits used to represent each sample (e.g., 16, 24).
 * @param pcm16         An array of PCM audio samples decoded as signed 16-bit values.
 */
public record SoundData(ByteBuffer data, float duration, int channels, int sampleRate, int bitsPerSample,
                        short[] pcm16) {

    /**
     * Loads a sound file into a {@code SoundData} object from the provided byte array.
     * The format of the sound file is detected automatically, and the appropriate loader
     * is used to process and decode the sound data.
     *
     * @param data A byte array representing the raw binary content of the sound file.
     *             It should contain sufficient data to allow format detection and processing.
     * @return A {@code SoundData} object containing the decoded audio data and associated metadata.
     * @throws RuntimeException If the sound file format is unsupported or if an error occurs during decoding.
     */
    public static SoundData load(byte[] data) {
        try {
            return SoundFileFormat.detect(data).getLoader().load(data);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Loads a sound file into a {@code SoundData} object from the provided file path.
     * The file's format is automatically detected, and the sound data is processed
     * and decoded accordingly.
     *
     * @param path The path to the sound file to be loaded. It should point to a valid file
     *             on the filesystem containing compatible audio data.
     * @return A {@code SoundData} object containing the decoded audio data and associated metadata.
     * @throws RuntimeException If an I/O error occurs or the sound file cannot be processed.
     */
    public static SoundData load(String path) {
        try {
            return load(Files.readAllBytes(Paths.get(path)));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}