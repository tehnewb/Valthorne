package valthorne.audio;

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
     * Immutable filesystem-path description for deferred sound loading or streaming.
     * Construction validates only that the string is nonblank; it does not open the
     * file or verify its audio format. The original path string is retained unchanged.
     *
     * @param path nonblank filesystem path
     * @author Albert Beaupre
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
     * In-memory encoded audio source that retains the supplied nonempty byte array.
     * Unlike a defensive-copy value object, the array is shared and the generated
     * accessor returns that same array. Callers must keep its contents stable while
     * loaders or streams use it; construction does not validate the audio format.
     *
     * @param bytes nonempty encoded audio array retained by reference
     * @author Albert Beaupre
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
