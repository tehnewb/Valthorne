package valthorne.graphics.map.tiled;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

/**
 * Factory utilities for creating {@link TiledDependencyResolver} instances from higher-level
 * dependency source descriptions.
 *
 * <p>
 * Tiled maps may resolve dependencies either from the file system or from an in-memory
 * file map. This class converts the user-facing {@link TiledDependencySource} into a
 * runtime resolver implementation that the TMX parser can use uniformly.
 * </p>
 *
 * <h2>Supported dependency sources</h2>
 * <ul>
 *     <li>{@link TiledDependencySource.FileSystemSource}</li>
 *     <li>{@link TiledDependencySource.MapSource}</li>
 * </ul>
 *
 * <h2>In-memory resolution</h2>
 * <p>
 * The {@link InMemoryResolver} resolves paths relative to the parent TMX or TSX file path,
 * normalizes them, and then performs a lookup in the provided in-memory file map.
 * </p>
 *
 * @author Albert Beaupre
 * @since March 7th, 2026
 */
public final class TiledResolvers {

    /**
     * Prevents instantiation of this utility class.
     */
    private TiledResolvers() {
    }

    /**
     * Creates a dependency resolver from the provided dependency source.
     *
     * @param source the dependency source description
     * @return a runtime resolver for the provided source
     * @throws IllegalStateException if the dependency source type is unsupported
     */
    public static TiledDependencyResolver from(TiledDependencySource source) {
        if (source instanceof TiledDependencySource.FileSystemSource) {
            return new FileSystemResolver();
        }

        if (source instanceof TiledDependencySource.MapSource mapSource) {
            return new InMemoryResolver(mapSource.files());
        }

        throw new IllegalStateException("Unknown TiledDependencySource: " + source.getClass().getName());
    }

    /**
     * Dependency resolver that loads Tiled dependencies from an in-memory file map.
     *
     * <p>
     * This resolver is useful for cache-backed maps, classpath-preloaded resource packs,
     * custom archives, or test setups where all files already exist in memory.
     * </p>
     *
     * <p>
     * Dependency paths are resolved relative to the parent path, normalized into slash-separated
     * form, and then looked up directly in the stored file map.
     * </p>
     */
    public static final class InMemoryResolver implements TiledDependencyResolver {

        private final Map<String, byte[]> files; // The normalized in-memory file map used for dependency lookup.

        /**
         * Creates a new in-memory dependency resolver.
         *
         * @param files the in-memory file map keyed by normalized path
         */
        public InMemoryResolver(Map<String, byte[]> files) {
            this.files = files;
        }

        /**
         * Resolves a dependency path using the in-memory file map.
         *
         * <p>
         * The parent bytes are unused by this implementation, but the parent path is used
         * to resolve the dependency path relative to the referencing file.
         * </p>
         *
         * @param parentBytes    the raw bytes of the parent file
         * @param parentPath     the logical path of the parent file
         * @param dependencyPath the dependency path to resolve
         * @return the resolved dependency bytes
         * @throws RuntimeException if the dependency is not present in the in-memory file map
         */
        @Override
        public byte[] resolve(byte[] parentBytes, String parentPath, String dependencyPath) {
            String resolved = normalize(resolvePath(parentPath, dependencyPath));
            byte[] bytes = files.get(resolved);
            if (bytes == null) throw new RuntimeException("Missing tiled dependency: " + resolved);
            return bytes;
        }

        /**
         * Resolves a dependency path relative to a parent file path.
         *
         * @param basePath       the parent file path
         * @param dependencyPath the dependency path as written in TMX or TSX data
         * @return the resolved path string
         */
        private static String resolvePath(String basePath, String dependencyPath) {
            Path dep = Paths.get(dependencyPath);
            if (dep.isAbsolute()) return dep.normalize().toString();

            Path base = Paths.get(basePath).normalize();
            Path parent = base.getParent();
            if (parent == null) return dep.normalize().toString();
            return parent.resolve(dependencyPath).normalize().toString();
        }

        /**
         * Normalizes a path into slash-separated form.
         *
         * @param path the input path
         * @return the normalized slash-separated path
         */
        private static String normalize(String path) {
            return path.replace('\\', '/');
        }
    }
}