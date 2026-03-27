package valthorne.audio.sound;

/**
 * <p>
 * {@code SoundSource} describes where raw sound data originates. It is modeled as a
 * sealed interface with compact record implementations for file-path sources and
 * in-memory byte-array sources.
 * </p>
 *
 * <h2>Example Usage</h2>
 *
 * <pre>{@code
 * SoundSource pathSource = new SoundSource.PathSource("music/theme.ogg");
 * SoundSource bytesSource = new SoundSource.BytesSource(bytes);
 * }</pre>
 *
 * @author Albert Beaupre
 * @since March 26th, 2026
 */
public sealed interface SoundSource permits SoundSource.PathSource, SoundSource.BytesSource {

    /**
     * <p>
     * {@code PathSource} stores a filesystem path to the sound data.
     * </p>
     *
     * @param path the non-blank file path
     */
    record PathSource(String path) implements SoundSource {

        /**
         * Validates the stored path.
         *
         * @param path the filesystem path
         */
        public PathSource {
            if (path == null || path.isBlank()) throw new IllegalArgumentException("path cannot be null/blank");
        }
    }

    /**
     * <p>
     * {@code BytesSource} stores sound data entirely in memory.
     * </p>
     *
     * @param bytes the non-empty encoded audio bytes
     */
    record BytesSource(byte[] bytes) implements SoundSource {

        /**
         * Validates the stored bytes.
         *
         * @param bytes the encoded sound bytes
         */
        public BytesSource {
            if (bytes == null || bytes.length == 0) throw new IllegalArgumentException("bytes cannot be null/empty");
        }
    }
}
