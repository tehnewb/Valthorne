package valthorne.graphics.font;

import java.util.Arrays;

/**
 * Represents the source of font data. It is a sealed interface that has two specific implementations:
 * one for file path-based font sources and another for raw byte array-based font sources.
 * Sources describe encoded data without decoding it or allocating graphics resources.
 * Byte sources use defensive copies on input and access; path sources validate text
 * only and defer resource existence checks until loading.
 *
 * @author Albert Beaupre
 */
public sealed interface FontSource permits FontSource.PathSource, FontSource.BytesSource {

    /**
     * Immutable description of a font path whose contents are loaded later.
     * Only nonblank path text is validated; construction does not check existence,
     * read bytes, or allocate a font. The original path string is retained unchanged.
     *
     * @param path nonblank font path
     * @author Albert Beaupre
     */
    record PathSource(String path) implements FontSource {
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
     * A record that represents a font source defined by raw byte data.
     * This source allows a font to be initialized with a byte array of the font's encoded data.
     * <p>
     * Instances of this record ensure the encapsulated byte array is safely copied for immutability.
     *
     * @author Albert Beaupre
     */
    record BytesSource(byte[] bytes) implements FontSource {
        /**
         * Copies encoded font bytes so later changes to the input cannot change
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
