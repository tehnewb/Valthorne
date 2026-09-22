package valthorne.graphics.font.slug;

import java.util.Arrays;

/**
 * Describes encoded font input for a Slug asset without reading or decoding it.
 * Path sources defer I/O to {@link SlugLoader}; byte sources defensively copy their
 * contents so asset requests remain stable while queued or cached.
 *
 * @author Albert Beaupre
 */
public sealed interface SlugSource permits SlugSource.PathSource, SlugSource.BytesSource {

    /**
     * Deferred filesystem source.
     *
     * @param path nonblank font path
     */
    record PathSource(String path) implements SlugSource {
        /**
         * Validates the path text without touching the filesystem.
         */
        public PathSource {
            if (path == null || path.isBlank()) throw new IllegalArgumentException("path cannot be null/blank");
        }
    }

    /**
     * Immutable encoded-font source.
     *
     * @param bytes nonempty font bytes, copied on construction and access
     */
    record BytesSource(byte[] bytes) implements SlugSource {
        /**
         * Copies and validates the supplied encoded data.
         */
        public BytesSource {
            if (bytes == null || bytes.length == 0) throw new IllegalArgumentException("bytes cannot be null/empty");
            bytes = Arrays.copyOf(bytes, bytes.length);
        }

        /**
         * @return an independent copy of the encoded font bytes
         */
        @Override
        public byte[] bytes() {
            return Arrays.copyOf(bytes, bytes.length);
        }
    }
}
