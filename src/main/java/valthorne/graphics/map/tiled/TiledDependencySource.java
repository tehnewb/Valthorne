package valthorne.graphics.map.tiled;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Describes how Tiled map dependencies should be resolved.
 *
 * <p>
 * A TMX map often references other files such as TSX tilesets and image files. This sealed
 * interface provides the higher-level source description used to create runtime
 * {@link TiledDependencyResolver} instances.
 * </p>
 *
 * <h2>Implementations</h2>
 * <ul>
 *     <li>{@link FileSystemSource} for file-based dependency resolution</li>
 *     <li>{@link MapSource} for in-memory dependency resolution</li>
 * </ul>
 *
 * <p>
 * This abstraction keeps Tiled loading flexible so maps can be loaded from disk, memory,
 * cache archives, classpath resource bundles, or other custom asset systems.
 * </p>
 *
 * @author Albert Beaupre
 * @since March 7th, 2026
 */
public sealed interface TiledDependencySource permits TiledDependencySource.FileSystemSource, TiledDependencySource.MapSource {

    /**
     * Represents dependency resolution through the file system.
     *
     * <p>
     * When this source is used, relative dependency paths are resolved against the parent
     * TMX or TSX path using normal file-system rules.
     * </p>
     */
    record FileSystemSource() implements TiledDependencySource {
    }

    /**
     * Represents dependency resolution through an in-memory file map.
     *
     * <p>
     * The provided file map is normalized and defensively copied so the dependency source
     * remains stable after construction.
     * </p>
     *
     * @param files the in-memory file map keyed by normalized path
     */
    record MapSource(Map<String, byte[]> files) implements TiledDependencySource {

        /**
         * Creates a new in-memory dependency source.
         *
         * @param files the in-memory file map keyed by path
         * @throws IllegalArgumentException if {@code files} is null or empty
         * @throws IllegalArgumentException if any path key is null or blank
         * @throws IllegalArgumentException if any file byte array is null or empty
         */
        public MapSource {
            if (files == null || files.isEmpty()) throw new IllegalArgumentException("files cannot be null/empty");

            Map<String, byte[]> copy = new HashMap<>();
            for (Map.Entry<String, byte[]> entry : files.entrySet()) {
                String key = normalize(entry.getKey());
                byte[] value = entry.getValue();

                if (key == null || key.isBlank()) throw new IllegalArgumentException("file key cannot be null/blank");
                if (value == null || value.length == 0)
                    throw new IllegalArgumentException("file bytes cannot be null/empty");

                copy.put(key, Arrays.copyOf(value, value.length));
            }
            files = copy;
        }

        /**
         * Returns a defensive copy of the in-memory file map.
         *
         * <p>
         * Both the map structure and the stored byte arrays are copied so callers cannot
         * mutate the internal state of this record.
         * </p>
         *
         * @return a deep copy of the in-memory file map
         */
        @Override
        public Map<String, byte[]> files() {
            Map<String, byte[]> copy = new HashMap<>();
            for (Map.Entry<String, byte[]> entry : files.entrySet()) {
                copy.put(entry.getKey(), Arrays.copyOf(entry.getValue(), entry.getValue().length));
            }
            return copy;
        }

        /**
         * Normalizes a path into slash-separated form.
         *
         * @param path the input path
         * @return the normalized slash-separated path, or null if the input was null
         */
        private static String normalize(String path) {
            return path == null ? null : path.replace('\\', '/');
        }
    }
}