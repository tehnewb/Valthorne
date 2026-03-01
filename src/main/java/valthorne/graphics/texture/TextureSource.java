package valthorne.graphics.texture;

import java.util.Arrays;

/**
 * Represents a source for a texture, which can either be loaded from a file path or from raw image bytes.
 * This interface is sealed, allowing only specific implementations for texture sources.
 */
public sealed interface TextureSource permits TextureSource.PathSource, TextureSource.BytesSource {

    /**
     * A record that represents a texture source defined by a file system path.
     * This implementation of {@link TextureSource} ensures the validity of the provided path.
     *
     * @param path the file system path to the texture, must not be null or blank
     * @throws IllegalArgumentException if the provided path is null or blank
     */
    record PathSource(String path) implements TextureSource {
        public PathSource {
            if (path == null || path.isBlank()) throw new IllegalArgumentException("path cannot be null/blank");
        }
    }

    /**
     * Represents a texture source that is defined by raw image bytes.
     * This implementation of the {@link TextureSource} interface validates the provided byte array
     * to ensure non-null and non-empty data and creates a defensive copy for immutability.
     *
     * @param bytes the byte array containing the texture data, must not be null or empty
     * @throws IllegalArgumentException if the provided byte array is null or empty
     * @implSpec This class ensures that the byte array provided is immutable by copying it in the constructor.
     * Additionally, it enforces that the data is non-null and non-empty at the time of instantiation.
     * @see TextureSource
     */
    record BytesSource(byte[] bytes) implements TextureSource {
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