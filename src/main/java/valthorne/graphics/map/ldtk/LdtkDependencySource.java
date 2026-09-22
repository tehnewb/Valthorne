package valthorne.graphics.map.ldtk;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Selects how referenced LDtk files are resolved. Filesystem sources resolve
 * relative paths beside the referring document; map sources provide an immutable,
 * normalized snapshot suitable for archives, tests, and virtual filesystems.
 */
public sealed interface LdtkDependencySource permits LdtkDependencySource.FileSystemSource, LdtkDependencySource.MapSource {
    /**
     * Requests dependency reads from the local filesystem.
     */
    record FileSystemSource() implements LdtkDependencySource {
    }

    /**
     * Supplies dependency bytes by normalized forward-slash path.
     *
     * @param files path-to-content entries, defensively copied on input and access
     */
    record MapSource(Map<String, byte[]> files) implements LdtkDependencySource {
        /**
         * Validates, normalizes, and deeply copies every dependency entry.
         *
         * @param files non-null map with nonblank keys and non-null byte arrays
         * @throws IllegalArgumentException if the map or any entry is invalid
         */
        public MapSource {
            if (files == null) throw new IllegalArgumentException("files cannot be null");
            Map<String, byte[]> copy = new LinkedHashMap<>();
            files.forEach((key, value) -> {
                if (key == null || key.isBlank() || value == null)
                    throw new IllegalArgumentException("invalid dependency entry");
                copy.put(key.replace('\\', '/'), Arrays.copyOf(value, value.length));
            });
            files = Map.copyOf(copy);
        }

        /**
         * Returns a deep copy so callers cannot mutate stored dependency bytes.
         *
         * @return immutable map whose byte-array values are fresh copies
         */
        @Override
        public Map<String, byte[]> files() {
            Map<String, byte[]> copy = new LinkedHashMap<>();
            files.forEach((key, value) -> copy.put(key, Arrays.copyOf(value, value.length)));
            return copy;
        }
    }
}
