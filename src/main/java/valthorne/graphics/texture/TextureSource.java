package valthorne.graphics.texture;

import java.util.Arrays;

/**
 * Represents a source for a texture, which can either be loaded from a file path or from raw image bytes.
 * This interface is sealed, allowing only specific implementations for texture sources.
 * Sources describe encoded data without decoding it or allocating graphics resources.
 * Byte sources use defensive copies on input and access; path sources validate text
 * only and defer resource existence checks until loading.
 *
 * @author Albert Beaupre
 */
public sealed interface TextureSource permits TextureSource.PathSource, TextureSource.BytesSource {

    /**
     * Immutable description of an encoded image file to be loaded later.
     * Construction validates nonblank path text without checking existence or image
     * format. No decoding or OpenGL allocation occurs until the source is loaded.
     *
     * @param path nonblank image path, retained unchanged
     * @author Albert Beaupre
     */
    record PathSource(String path) implements TextureSource {
        /**
         * Records a nonblank path without resolving or opening it. Whitespace is
         * checked for emptiness but the original path text is retained unchanged.
         *
         * @param path resource path to retain
         * @throws IllegalArgumentException if path is null or blank
         */
        public PathSource {
            if (path == null || path.isBlank()) throw new IllegalArgumentException("path cannot be null/blank");
        }
    }

    /**
     * Immutable encoded-image source that copies bytes on construction and access.
     * The byte array must be nonempty, but image-format validity is deferred to loading.
     * Every bytes() call returns a separate copy; this source owns no decoded pixels
     * or OpenGL texture handle.
     *
     * @param bytes nonempty encoded image data
     * @author Albert Beaupre
     */
    record BytesSource(byte[] bytes) implements TextureSource {
        /**
         * Copies encoded texture bytes so later changes to the input cannot change
         * this source. Format validity is checked by the loader, not this constructor.
         *
         * @param bytes nonempty encoded data
         * @throws IllegalArgumentException if bytes is null or empty
         */
        public BytesSource {
            if (bytes == null || bytes.length == 0) throw new IllegalArgumentException("bytes cannot be null/empty");
            bytes = Arrays.copyOf(bytes, bytes.length);
        }

        /**
         * Returns a fresh copy of the encoded data. Mutating the returned array
         * does not modify this source; every call allocates another array.
         *
         * @return independent copy of the stored bytes
         */
        @Override
        public byte[] bytes() {
            return Arrays.copyOf(bytes, bytes.length);
        }
    }
}
