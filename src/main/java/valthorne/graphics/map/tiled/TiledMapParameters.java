package valthorne.graphics.map.tiled;

import valthorne.asset.AssetParameters;
import valthorne.io.file.ValthorneFiles;

import java.util.HashMap;
import java.util.Map;

/**
 * Asset parameters used for loading a Tiled TMX map through Valthorne's asset system.
 *
 * <p>
 * This record combines three key pieces of information required to load a map:
 * </p>
 *
 * <ul>
 *     <li>The primary TMX source via {@link TiledMapSource}</li>
 *     <li>The strategy used to resolve map dependencies via {@link TiledDependencySource}</li>
 *     <li>A stable asset key name used for caching and retrieval</li>
 * </ul>
 *
 * <p>
 * These parameters are consumed by {@link TiledMapLoader}, which converts them into
 * {@link TiledMapData} objects that can later be turned into runtime {@link TiledMap}
 * instances on the OpenGL thread.
 * </p>
 *
 * <h2>Convenience creation methods</h2>
 * <p>
 * This type provides helpers for common use cases:
 * </p>
 *
 * <ul>
 *     <li>Load from a file path using file-system dependency resolution</li>
 *     <li>Load from raw bytes using an in-memory dependency map</li>
 *     <li>Load from classpath resources using an in-memory dependency map</li>
 * </ul>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * TiledMapParameters paramsA = TiledMapParameters.fromPath("assets/map/TestMap.tmx", "test-map");
 *
 * Map<String, byte[]> files = new HashMap<>();
 * files.put("assets/map/TestMap.tmx", ValthorneFiles.readBytes("assets/map/TestMap.tmx"));
 * files.put("assets/map/Tiles.tsx", ValthorneFiles.readBytes("assets/map/Tiles.tsx"));
 * files.put("assets/map/Tiles.png", ValthorneFiles.readBytes("assets/map/Tiles.png"));
 *
 * TiledMapParameters paramsB = TiledMapParameters.fromBytes(
 *         files.get("assets/map/TestMap.tmx"),
 *         "assets/map/TestMap.tmx",
 *         files,
 *         "test-map"
 * );
 * }</pre>
 *
 * @param source       the source of the TMX data
 * @param dependencies the dependency-resolution source for TSX and image files
 * @param name         the asset key used by the asset manager
 * @author Albert Beaupre
 * @since March 7th, 2026
 */
public record TiledMapParameters(TiledMapSource source, TiledDependencySource dependencies, String name) implements AssetParameters {

    /**
     * Creates a new set of Tiled map asset parameters.
     *
     * @param source       the source of the TMX map
     * @param dependencies the dependency source used to resolve TSX and image files
     * @param name         the cache key used by the asset manager
     * @throws IllegalArgumentException if {@code source} is null
     * @throws IllegalArgumentException if {@code dependencies} is null
     * @throws IllegalArgumentException if {@code name} is null or blank
     */
    public TiledMapParameters {
        if (source == null) throw new IllegalArgumentException("source cannot be null");
        if (dependencies == null) throw new IllegalArgumentException("dependencies cannot be null");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name cannot be null/blank");
    }

    /**
     * Creates path-based map parameters using the same path as the asset key.
     *
     * <p>
     * Dependency resolution is configured to use the file system.
     * </p>
     *
     * @param path the TMX file path
     * @return a new path-based Tiled map parameter set
     */
    public static TiledMapParameters fromPath(String path) {
        return new TiledMapParameters(new TiledMapSource.PathSource(path), new TiledDependencySource.FileSystemSource(), path);
    }

    /**
     * Creates path-based map parameters using a custom asset key.
     *
     * <p>
     * Dependency resolution is configured to use the file system.
     * </p>
     *
     * @param path the TMX file path
     * @param name the custom asset key
     * @return a new path-based Tiled map parameter set
     */
    public static TiledMapParameters fromPath(String path, String name) {
        return new TiledMapParameters(new TiledMapSource.PathSource(path), new TiledDependencySource.FileSystemSource(), name);
    }

    /**
     * Creates byte-based map parameters using the virtual path as the asset key.
     *
     * <p>
     * Dependency resolution is configured to use the supplied in-memory file map.
     * </p>
     *
     * @param tmxBytes    the raw TMX bytes
     * @param virtualPath the logical TMX path used for relative dependency resolution
     * @param files       the in-memory dependency file map
     * @return a new byte-based Tiled map parameter set
     */
    public static TiledMapParameters fromBytes(byte[] tmxBytes, String virtualPath, Map<String, byte[]> files) {
        return new TiledMapParameters(new TiledMapSource.BytesSource(tmxBytes, virtualPath), new TiledDependencySource.MapSource(files), virtualPath);
    }

    /**
     * Creates byte-based map parameters using a custom asset key.
     *
     * @param tmxBytes    the raw TMX bytes
     * @param virtualPath the logical TMX path used for relative dependency resolution
     * @param files       the in-memory dependency file map
     * @param name        the custom asset key
     * @return a new byte-based Tiled map parameter set
     */
    public static TiledMapParameters fromBytes(byte[] tmxBytes, String virtualPath, Map<String, byte[]> files, String name) {
        return new TiledMapParameters(new TiledMapSource.BytesSource(tmxBytes, virtualPath), new TiledDependencySource.MapSource(files), name);
    }

    /**
     * Creates byte-based map parameters from classpath resources.
     *
     * <p>
     * The main TMX resource and all dependency resources are loaded immediately into an
     * in-memory file map using {@link ValthorneFiles#readBytes(String)}.
     * </p>
     *
     * @param tmxResourcePath         the classpath path to the TMX file bytes
     * @param virtualPath             the logical TMX path used inside the dependency map
     * @param name                    the custom asset key
     * @param dependencyResourcePaths the classpath paths of dependency files to preload
     * @return a new byte-based Tiled map parameter set backed by classpath resource bytes
     */
    public static TiledMapParameters fromClasspath(String tmxResourcePath, String virtualPath, String name, String... dependencyResourcePaths) {
        Map<String, byte[]> files = new HashMap<>();
        files.put(normalize(virtualPath), ValthorneFiles.readBytes(tmxResourcePath));

        for (String dependency : dependencyResourcePaths) {
            files.put(normalize(dependency), ValthorneFiles.readBytes(dependency));
        }

        return fromBytes(files.get(normalize(virtualPath)), virtualPath, files, name);
    }

    /**
     * Returns the asset-manager cache key for this map.
     *
     * @return the asset key
     */
    @Override
    public String key() {
        return name;
    }

    /**
     * Normalizes a path into slash-separated form.
     *
     * <p>
     * This is used so in-memory dependency maps use consistent keys regardless of
     * platform-specific path separators.
     * </p>
     *
     * @param path the input path
     * @return the normalized slash-separated path
     */
    private static String normalize(String path) {
        return path.replace('\\', '/');
    }
}