package valthorne.audio.sound;

/**
 * <p>
 * {@code SoundDecoder} defines the contract used by Valthorne to convert raw encoded
 * audio bytes into a fully decoded {@link SoundData} instance suitable for playback
 * or buffering.
 * </p>
 *
 * <p>
 * Implementations are format-specific. For example, WAV, OGG, and MP3 decoders each
 * interpret the incoming bytes differently but all expose the same decode operation.
 * </p>
 *
 * <h2>Example Usage</h2>
 *
 * <pre>{@code
 * SoundDecoder decoder = new WaveSoundDecoder();
 * SoundData data = decoder.decode(bytes);
 * }</pre>
 *
 * @author Albert Beaupre
 * @since December 5th, 2025
 */
public interface SoundDecoder {

    /**
     * Decodes the supplied encoded audio bytes into {@link SoundData}.
     *
     * @param data the encoded audio bytes to decode
     * @return the decoded sound data
     * @throws Exception if decoding fails for any reason
     */
    SoundData decode(byte[] data) throws Exception;

}
