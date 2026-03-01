package valthorne.sound;

import java.util.Arrays;

/**
 * Represents a sound source that can be loaded either from a file system path
 * or raw byte data. The `SoundSource` interface is sealed and permits specific
 * implementations for defining these two types of sources.
 */
public sealed interface SoundSource permits SoundSource.PathSource, SoundSource.BytesSource {

    /**
     * A record that represents a sound source specified by a file path.
     * Implements the SoundSource interface as a subtype to provide the
     * path-based source for loading or processing sound data.
     * <p>
     * The path provided must be non-null and cannot be blank. If the path
     * fails these requirements, an IllegalArgumentException is thrown during
     * instantiation.
     *
     * @param path The file path representing the location of the sound source.
     *             This path is expected to point to a valid file containing
     *             audio data in a supported format.
     */
    record PathSource(String path) implements SoundSource {
        public PathSource {
            if (path == null || path.isBlank()) throw new IllegalArgumentException("path cannot be null/blank");
        }
    }

    /**
     * A record that represents a sound source containing raw byte data.
     * Implements the {@code SoundSource} interface as a subtype to provide
     * a byte array-based source for loading or processing sound data.
     * <p>
     * The byte array provided must be non-null and cannot be empty. This ensures
     * the integrity of the sound source. If the validation fails, an IllegalArgumentException
     * is thrown during instantiation.
     * <p>
     * The {@code bytes()} method ensures immutability by returning a defensive
     * copy of the internal byte array to prevent external modifications.
     *
     * @param bytes A byte array representing the raw binary content of the sound source.
     */
    record BytesSource(byte[] bytes) implements SoundSource {
        public BytesSource {
            if (bytes == null || bytes.length == 0) throw new IllegalArgumentException("bytes cannot be null/empty");
            // Defensive copy to keep immutability (records are meant to be immutable)
            bytes = Arrays.copyOf(bytes, bytes.length);
        }

        @Override
        public byte[] bytes() {
            // Return a copy to prevent callers from mutating internal state
            return Arrays.copyOf(bytes, bytes.length);
        }
    }
}