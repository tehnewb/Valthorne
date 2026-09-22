package valthorne.graphics.map.ldtk;

import java.util.Arrays;

/**
 * Identifies the primary LDtk project JSON document. A source is either a path
 * that the loader reads on demand or an immutable byte snapshot with a virtual
 * path used to resolve relative dependencies.
 */
public sealed interface LdtkSource permits LdtkSource.PathSource, LdtkSource.BytesSource {
    /**
     * Names a project document on the local filesystem.
     *
     * @param path nonblank path to an LDtk project JSON file
     */
    record PathSource(String path) implements LdtkSource {
        /**
         * Validates the project path.
         *
         * @param path nonblank filesystem path
         * @throws IllegalArgumentException if the path is null or blank
         */
        public PathSource {
            if (path == null || path.isBlank()) throw new IllegalArgumentException("path cannot be blank");
        }
    }

    /**
     * Stores project JSON bytes together with a dependency-resolution base path.
     *
     * @param bytes nonempty project bytes, defensively copied
     * @param virtualPath nonblank logical path used for relative references
     */
    record BytesSource(byte[] bytes, String virtualPath) implements LdtkSource {
        /**
         * Validates and snapshots the supplied source bytes.
         *
         * @param bytes nonempty project bytes
         * @param virtualPath nonblank logical project path
         * @throws IllegalArgumentException if either argument is invalid
         */
        public BytesSource {
            if (bytes == null || bytes.length == 0) throw new IllegalArgumentException("bytes cannot be empty");
            if (virtualPath == null || virtualPath.isBlank())
                throw new IllegalArgumentException("virtualPath cannot be blank");
            bytes = Arrays.copyOf(bytes, bytes.length);
        }

        /**
         * Returns an isolated copy of the stored project document.
         *
         * @return a fresh byte array
         */
        @Override
        public byte[] bytes() {
            return Arrays.copyOf(bytes, bytes.length);
        }
    }
}
