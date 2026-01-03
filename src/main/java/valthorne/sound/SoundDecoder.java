package valthorne.sound;

/**
 * Represents a contract for loading and decoding audio buffer to be used within a sound processing system.
 * Implementations of this interface are responsible for handling specific audio formats,
 * transforming raw byte arrays into a {@code SoundData} object suitable for playback or processing.
 *
 * @author Albert Beaupre
 * @since December 5th, 2025
 */
public interface SoundDecoder {

    /**
     * Loads and decodes raw audio buffer from a byte array into a {@code SoundData} object.
     * The method is responsible for processing the byte array according to the specific
     * sound file format and converting it to a usable audio buffer representation.
     *
     * @param data the byte array containing raw sound file buffer that needs to be loaded and decoded
     * @return a {@code SoundData} object containing the decoded audio buffer and format details
     * @throws Exception if an error occurs during the loading or decoding process
     */
    SoundData load(byte[] data) throws Exception;

}