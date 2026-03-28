package valthorne.graphics.map.tiled;

import valthorne.asset.AssetParameters;
import valthorne.io.file.ValthorneFiles;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.ByteArrayInputStream;
import java.util.*;

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
     * Creates in-memory Tiled map parameters using a resource located on the classpath.
     * Resolves all dependencies recursively and stores them in-memory.
     *
     * @param tmxResourcePath the path of the TMX resource file on the classpath
     * @param name            the custom asset key used to identify the map
     * @return a TiledMapParameters instance representing the TMX map and its dependencies
     */
    public static TiledMapParameters fromClasspath(String tmxResourcePath, String name) {
        String virtualPath = normalize(tmxResourcePath);
        Map<String, byte[]> files = new HashMap<>();
        Set<String> visited = new HashSet<>();

        collectClasspathDependencies(virtualPath, null, files, visited);

        return fromBytes(files.get(virtualPath), virtualPath, files, name);
    }

    /**
     * Creates in-memory Tiled map parameters using a resource located on the classpath.
     * Resolves all dependencies recursively and stores them in-memory.
     *
     * @param tmxResourcePath the path of the TMX resource file on the classpath
     * @return a TiledMapParameters instance representing the TMX map and its dependencies
     */
    public static TiledMapParameters fromClasspath(String tmxResourcePath) {
        return fromClasspath(tmxResourcePath, normalize(tmxResourcePath));
    }

    private static void collectClasspathDependencies(String resourcePath, String rawReference, Map<String, byte[]> files, Set<String> visited) {
        String normalizedPath = normalize(resourcePath);

        if (!visited.add(normalizedPath)) {
            byte[] existing = files.get(normalizedPath);
            if (existing != null) {
                addAliases(files, normalizedPath, rawReference, existing);
            }
            return;
        }

        byte[] bytes = ValthorneFiles.readBytes(normalizedPath);
        addAliases(files, normalizedPath, rawReference, bytes);

        if (!isXmlDependency(normalizedPath)) {
            return;
        }

        for (String dependency : readDependencyPaths(bytes)) {
            String resolved = resolveRelative(normalizedPath, dependency);
            collectClasspathDependencies(resolved, dependency, files, visited);
        }
    }

    private static void addAliases(Map<String, byte[]> files, String normalizedPath, String rawReference, byte[] bytes) {
        files.put(normalizedPath, bytes);
        files.put(toAbsoluteAlias(normalizedPath), bytes);

        if (rawReference != null && !rawReference.isBlank()) {
            files.putIfAbsent(normalize(rawReference), bytes);
        }
    }

    private static boolean isXmlDependency(String path) {
        String lower = path.toLowerCase();
        return lower.endsWith(".tmx") || lower.endsWith(".tsx");
    }

    private static List<String> readDependencyPaths(byte[] bytes) {
        List<String> dependencies = new ArrayList<>();

        try {
            XMLInputFactory factory = XMLInputFactory.newFactory();
            XMLStreamReader reader = factory.createXMLStreamReader(new ByteArrayInputStream(bytes));

            while (reader.hasNext()) {
                int event = reader.next();

                if (event != XMLStreamConstants.START_ELEMENT) {
                    continue;
                }

                String element = reader.getLocalName();
                if (!"tileset".equals(element) && !"image".equals(element)) {
                    continue;
                }

                String source = reader.getAttributeValue(null, "source");
                if (source != null && !source.isBlank()) {
                    dependencies.add(source);
                }
            }

            reader.close();
            return dependencies;
        } catch (XMLStreamException e) {
            throw new RuntimeException("Failed to parse tiled classpath dependency tree: " + bytes.length + " bytes", e);
        }
    }

    private static String resolveRelative(String parentPath, String childPath) {
        String normalizedChild = normalize(childPath);

        if (normalizedChild.contains(":")) {
            return normalizedChild;
        }

        if (normalizedChild.startsWith("/")) {
            return normalizedChild.substring(1);
        }

        String parentDir = directoryOf(parentPath);
        String joined = parentDir.isEmpty() ? normalizedChild : parentDir + "/" + normalizedChild;

        Deque<String> parts = new ArrayDeque<>();
        for (String part : joined.split("/")) {
            if (part.isEmpty() || ".".equals(part)) {
                continue;
            }

            if ("..".equals(part)) {
                if (!parts.isEmpty()) {
                    parts.removeLast();
                }
                continue;
            }

            parts.addLast(part);
        }

        return String.join("/", parts);
    }

    private static String directoryOf(String path) {
        String normalized = normalize(path);
        int index = normalized.lastIndexOf('/');
        return index == -1 ? "" : normalized.substring(0, index);
    }

    private static String toAbsoluteAlias(String path) {
        return normalize(System.getProperty("user.dir")) + "/" + normalize(path);
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