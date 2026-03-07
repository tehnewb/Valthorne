package valthorne.graphics.map.tiled;

import java.util.Arrays;

/**
 * Describes the primary source used to load a Tiled TMX map.
 *
 * <p>
 * A Tiled map can be loaded either from a real file-system path or from an in-memory
 * byte array. This sealed interface provides those two source forms in a type-safe way
 * so higher-level loading code can decide how to acquire the raw TMX bytes.
 * </p>
 *
 * <p>
 * This abstraction is especially useful when integrating with Valthorne's asset system
 * because some projects may load maps from disk while others may load them from classpath
 * resources, cache archives, encrypted containers, or already-preloaded memory blocks.
 * </p>
 *
 * <h2>Implementations</h2>
 * <ul>
 *     <li>{@link PathSource} for file-based TMX loading</li>
 *     <li>{@link BytesSource} for in-memory TMX loading using a virtual path</li>
 * </ul>
 *
 * <h2>Virtual path usage</h2>
 * <p>
 * When loading from bytes, Tiled dependencies such as external TSX files or referenced images
 * may still be resolved relative to a logical parent path. For that reason, {@link BytesSource}
 * stores both the TMX bytes and a virtual path string.
 * </p>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * TiledMapSource sourceA = new TiledMapSource.PathSource("assets/maps/world.tmx");
 *
 * byte[] bytes = Files.readAllBytes(Path.of("assets/maps/world.tmx"));
 * TiledMapSource sourceB = new TiledMapSource.BytesSource(bytes, "assets/maps/world.tmx");
 * }</pre>
 *
 * @author Albert Beaupre
 * @since March 7th, 2026
 */
public sealed interface TiledMapSource permits TiledMapSource.PathSource, TiledMapSource.BytesSource {

    /**
     * Represents a Tiled map source backed by a real file-system path.
     *
     * <p>
     * This form is used when the TMX file should be loaded directly from disk. The path
     * is also used as the logical base path for resolving dependencies such as TSX files
     * and image files.
     * </p>
     *
     * @param path the file-system path of the TMX map
     */
    record PathSource(String path) implements TiledMapSource {

        /**
         * Creates a new path-based map source.
         *
         * @param path the file-system path of the TMX map
         * @throws IllegalArgumentException if {@code path} is null or blank
         */
        public PathSource {
            if (path == null || path.isBlank()) throw new IllegalArgumentException("path cannot be null/blank");
        }
    }

    /**
     * Represents a Tiled map source backed by raw TMX bytes and a logical virtual path.
     *
     * <p>
     * This form is used when map content already exists in memory. The byte array is copied
     * defensively so the stored source remains stable and external mutation cannot alter the
     * asset after construction.
     * </p>
     *
     * <p>
     * The virtual path is still required because Tiled maps often reference other files
     * relative to the parent TMX path.
     * </p>
     *
     * @param bytes       the raw TMX bytes
     * @param virtualPath the logical path used for dependency resolution
     */
    record BytesSource(byte[] bytes, String virtualPath) implements TiledMapSource {

        /**
         * Creates a new byte-based map source.
         *
         * @param bytes       the raw TMX bytes
         * @param virtualPath the logical path used for dependency resolution
         * @throws IllegalArgumentException if {@code bytes} is null or empty
         * @throws IllegalArgumentException if {@code virtualPath} is null or blank
         */
        public BytesSource {
            if (bytes == null || bytes.length == 0) throw new IllegalArgumentException("bytes cannot be null/empty");
            if (virtualPath == null || virtualPath.isBlank())
                throw new IllegalArgumentException("virtualPath cannot be null/blank");
            bytes = Arrays.copyOf(bytes, bytes.length);
        }

        /**
         * Returns a defensive copy of the stored TMX bytes.
         *
         * <p>
         * This prevents callers from mutating the internal byte array held by this source.
         * </p>
         *
         * @return a copy of the TMX byte array
         */
        @Override
        public byte[] bytes() {
            return Arrays.copyOf(bytes, bytes.length);
        }
    }
}