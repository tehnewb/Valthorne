package valthorne.sound;

/**
 * Represents various sound file formats along with associated metadata
 * like magic bytes for signature verification and loader implementations.
 * Each enum constant defines specific attributes needed to detect and process
 * a sound file of that format.
 *
 * @author Albert Beaupre
 * @since December 5th, 2025
 */
public enum SoundFileFormat {
    WAV(new WaveDecoder(), new byte[][]{{'R', 'I', 'F', 'F'}, {'W', 'A', 'V', 'E'}}),
    OGG(new OggDecoder(), new byte[][]{{'O', 'g', 'g', 'S'}}),
    MP3(new Mp3Decoder(), new byte[][]{{'I', 'D', '3'}}, true),   // uses fallback frame sync check
    FLAC(null, new byte[][]{{'f', 'L', 'a', 'C'}}),
    AIFF(null, new byte[][]{{'F', 'O', 'R', 'M'}, {'A', 'I', 'F', 'F'}}),
    AAC(null, new byte[][]{{(byte) 0xFF, (byte) 0xF1}, {(byte) 0xFF, (byte) 0xF9}}), // ADTS
    OPUS(null, new byte[][]{{'O', 'p', 'u', 's', 'H', 'e', 'a', 'd'}}),
    MID(null, new byte[][]{{'M', 'T', 'h', 'd'}}),
    AU(null, new byte[][]{{'.', 's', 'n', 'd'}}),
    UNKNOWN(null, new byte[0][]);

    private final byte[][] magic;
    private final boolean needsMp3Fallback;
    private final SoundDecoder loader;

    SoundFileFormat(SoundDecoder loader, byte[][] magic) {
        this(loader, magic, false);
    }

    SoundFileFormat(SoundDecoder loader, byte[][] magic, boolean needsMp3Fallback) {
        this.loader = loader;
        this.magic = magic;
        this.needsMp3Fallback = needsMp3Fallback;
    }

    /**
     * Detects and identifies the sound file format based on the provided byte array.
     * It checks the file signature in the byte array against well-known signatures
     * for various sound formats and returns the corresponding {@code SoundFileFormat}.
     * If no matching format is found, {@code UNKNOWN} is returned.
     *
     * @param data A byte array representing the sound file buffer to be analyzed.
     *             It should contain sufficient bytes to match identifiable signatures
     *             of sound file formats.
     * @return The detected {@code SoundFileFormat} if a match is found; otherwise returns {@code UNKNOWN}.
     */
    public static SoundFileFormat detect(byte[] data) {
        if (data == null || data.length == 0)
            return UNKNOWN;

        for (SoundFileFormat format : values()) {
            if (format == UNKNOWN) continue;
            if (format.matches(data))
                return format;
        }
        return UNKNOWN;
    }

    /**
     * Checks whether the provided byte array matches the expected magic signature(s)
     * associated with the sound file format.
     *
     * @param data A byte array representing the file buffer to be checked. It must
     *             contain enough bytes to compare against the required signature(s).
     * @return {@code true} if the byte array matches the magic signature(s) for the
     * corresponding sound file format; otherwise, {@code false}.
     */
    private boolean matches(byte[] data) {
        for (byte[] sig : magic) {
            if (sig.length > data.length)
                continue;

            // Special case for WAV/AIFF where multiple signatures must be matched
            if (this == WAV) {
                return check(data, magic[0], 0) && check(data, magic[1], 8);
            }
            if (this == AIFF) {
                return check(data, magic[0], 0) && check(data, magic[1], 8);
            }

            if (check(data, sig, 0))
                return true;
        }

        // MP3 fallback: check frame sync header (0xFFE0 mask)
        if (needsMp3Fallback && data.length > 2) {
            int b0 = data[0] & 0xFF;
            int b1 = data[1] & 0xFF;
            return b0 == 0xFF && (b1 & 0xE0) == 0xE0;
        }

        return false;
    }

    /**
     * Validates whether a specific byte sequence (signature) matches within a given byte array
     * starting at a specified offset.
     *
     * @param data   The byte array to be checked. It represents the source buffer where the signature
     *               will be searched for a match.
     * @param sig    The byte array containing the signature to match. This represents the fixed
     *               sequence of bytes to look for within the source buffer.
     * @param offset The starting position in the buffer array from where the comparison with the
     *               signature will begin.
     * @return {@code true} if the signature is found at the specified offset in the buffer array;
     * otherwise, {@code false}.
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
     * Retrieves the current {@code SoundLoader} instance associated with this {@code SoundFileFormat}.
     * The {@code SoundLoader} is responsible for processing and loading audio buffer
     * related to this file format.
     *
     * @return the {@code SoundLoader} instance used for loading and decoding audio buffer
     */
    public SoundDecoder getLoader() {
        return loader;
    }
}
