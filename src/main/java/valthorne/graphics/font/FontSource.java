package valthorne.graphics.font;

import java.util.Arrays;

/**
 * Represents the source of font data. It is a sealed interface that has two specific implementations:
 * one for file path-based font sources and another for raw byte array-based font sources.
 */
public sealed interface FontSource permits FontSource.PathSource, FontSource.BytesSource {

    /**
     * Represents a font source identified by a file path.
     * This source provides a path from which a font can be loaded.
     *
     * @param path the file path pointing to the font resource; must not be null or blank
     * @throws IllegalArgumentException if the provided path is null or blank
     */
    record PathSource(String path) implements FontSource {
        public PathSource {
            if (path == null || path.isBlank()) throw new IllegalArgumentException("path cannot be null/blank");
        }
    }

    /**
     * A record that represents a font source defined by raw byte data.
     * This source allows a font to be initialized with a byte array of the font's encoded data.
     * <p>
     * Instances of this record ensure the encapsulated byte array is safely copied for immutability.
     */
    record BytesSource(byte[] bytes) implements FontSource {
        public BytesSource {
            if (bytes == null || bytes.length == 0) throw new IllegalArgumentException("bytes cannot be null/empty");
            bytes = Arrays.copyOf(bytes, bytes.length);
        }

        @Override
        public byte[] bytes() {
            return Arrays.copyOf(bytes, bytes.length);
        }
    }
}