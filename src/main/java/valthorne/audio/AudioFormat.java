package valthorne.audio;

import valthorne.audio.sound.Mp3SoundDecoder;
import valthorne.audio.sound.OggSoundDecoder;
import valthorne.audio.sound.SoundDecoder;
import valthorne.audio.sound.WaveSoundDecoder;

/**
 * <p>
 * {@code AudioFormat} enumerates the audio container or codec types currently recognized
 * by Valthorne's sound loading pipeline. Each enum constant stores the signature bytes
 * used for format detection, whether special MP3 frame-sync fallback logic should be
 * applied, and the {@link SoundDecoder} responsible for decoding that format when
 * decoding is supported.
 * </p>
 *
 * <p>
 * The main purpose of this enum is to provide a single place where format detection and
 * decoder selection are defined. Callers typically use {@link #detect(byte[])} to inspect
 * a file header or raw audio byte array and then obtain the appropriate decoder through
 * {@link #getDecoder()}.
 * </p>
 *
 * <h2>Example Usage</h2>
 *
 * <pre>{@code
 * byte[] bytes = Files.readAllBytes(Path.of("music.ogg"));
 * AudioFormat format = AudioFormat.detect(bytes);
 * SoundDecoder decoder = format.getDecoder();
 *
 * if (decoder != null) {
 *     SoundData sound = decoder.decode(bytes);
 * }
 * }</pre>
 *
 * @author Albert Beaupre
 * @since December 5th, 2025
 */
public enum AudioFormat {
    WAV(new WaveSoundDecoder(), new byte[][]{{'R', 'I', 'F', 'F'}, {'W', 'A', 'V', 'E'}}),
    OGG(new OggSoundDecoder(), new byte[][]{{'O', 'g', 'g', 'S'}}),
    MP3(new Mp3SoundDecoder(), new byte[][]{{'I', 'D', '3'}}, true),
    FLAC(null, new byte[][]{{'f', 'L', 'a', 'C'}}),
    AIFF(null, new byte[][]{{'F', 'O', 'R', 'M'}, {'A', 'I', 'F', 'F'}}),
    AAC(null, new byte[][]{{(byte) 0xFF, (byte) 0xF1}, {(byte) 0xFF, (byte) 0xF9}}),
    OPUS(null, new byte[][]{{'O', 'p', 'u', 's', 'H', 'e', 'a', 'd'}}),
    MID(null, new byte[][]{{'M', 'T', 'h', 'd'}}),
    AU(null, new byte[][]{{'.', 's', 'n', 'd'}}),
    UNKNOWN(null, new byte[0][]);

    private final byte[][] magic; // Signature sequences used to identify this audio format
    private final boolean needsMp3Fallback; // Whether MP3 frame-sync fallback detection should be applied
    private final SoundDecoder decoder; // Decoder used to load this format when support exists

    /**
     * Creates an audio format entry with standard signature matching.
     *
     * @param decoder the decoder used for this format, or {@code null} when unsupported
     * @param magic the signature byte sequences used for detection
     */
    AudioFormat(SoundDecoder decoder, byte[][] magic) {
        this(decoder, magic, false);
    }

    /**
     * Creates an audio format entry with full detection configuration.
     *
     * @param decoder the decoder used for this format, or {@code null} when unsupported
     * @param magic the signature byte sequences used for detection
     * @param needsMp3Fallback whether MP3 frame-sync fallback detection should be applied
     */
    AudioFormat(SoundDecoder decoder, byte[][] magic, boolean needsMp3Fallback) {
        this.decoder = decoder;
        this.magic = magic;
        this.needsMp3Fallback = needsMp3Fallback;
    }

    /**
     * Detects the format of the supplied audio bytes.
     *
     * @param data the audio data to inspect
     * @return the detected audio format, or {@link #UNKNOWN} when no match is found
     */
    public static AudioFormat detect(byte[] data) {
        if (data == null || data.length == 0)
            return UNKNOWN;

        for (AudioFormat format : values()) {
            if (format == UNKNOWN) continue;
            if (format.matches(data))
                return format;
        }
        return UNKNOWN;
    }

    /**
     * Returns whether the supplied bytes match this format's signature rules.
     *
     * @param data the bytes to test
     * @return {@code true} when the bytes match this format
     */
    private boolean matches(byte[] data) {
        for (byte[] sig : magic) {
            if (sig.length > data.length)
                continue;

            if (this == WAV) {
                return check(data, magic[0], 0) && check(data, magic[1], 8);
            }
            if (this == AIFF) {
                return check(data, magic[0], 0) && check(data, magic[1], 8);
            }

            if (check(data, sig, 0))
                return true;
        }

        if (needsMp3Fallback && data.length > 2) {
            int b0 = data[0] & 0xFF;
            int b1 = data[1] & 0xFF;
            return b0 == 0xFF && (b1 & 0xE0) == 0xE0;
        }

        return false;
    }

    /**
     * Compares a signature against the supplied data starting at a given offset.
     *
     * @param data the source bytes being inspected
     * @param sig the signature bytes to compare against
     * @param offset the starting offset in {@code data}
     * @return {@code true} when the signature matches at the given offset
     */
    private boolean check(byte[] data, byte[] sig, int offset) {
        if (offset + sig.length > data.length) return false;
        for (int i = 0; i < sig.length; i++) {
            if (data[offset + i] != sig[i])
                return false;
        }
        return true;
    }

    /**
     * Returns the decoder associated with this format.
     *
     * @return the decoder, or {@code null} when decoding is not currently supported
     */
    public SoundDecoder getDecoder() {
        return decoder;
    }
}
