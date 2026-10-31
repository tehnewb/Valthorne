package valthorne.graphics.shader;

import valthorne.asset.AssetParameters;
import valthorne.io.file.ValthorneFiles;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.function.IntFunction;

/**
 * Deferred UTF-8 PropertySet shader configuration and its source dependencies.
 * Path/classpath reads happen on the asset worker. In-memory source maps use
 * the exact names from vertex/fragment/compute properties. Cache IDs use the
 * optional source resolver; its callback runs on the loading thread and must
 * support that access. Only name identifies this request in Assets.
 */
public final class ShaderParameters implements AssetParameters {
    private final String location; // Config path, or empty for an in-memory request.
    private final String name; // Shared asset-cache identity.
    private final boolean classpath; // Whether deferred paths are classpath resources.
    private final byte[] configBytes; // Owned configuration snapshot, or null for deferred reads.
    private final Map<String, String> sources; // Immutable named GLSL snapshots for in-memory dependencies.
    private final IntFunction<String> sourceResolver; // Optional borrowed source-ID lookup.

    /**
     * Stores validated sources, copying bytes and the dependency map.
     * @param location config location, empty only for byte requests
     * @param name nonblank cache key
     * @param classpath whether to use classpath lookup
     * @param configBytes optional UTF-8 config snapshot
     * @param sources named source snapshots
     * @param sourceResolver optional cache-ID lookup
     */
    private ShaderParameters(String location, String name, boolean classpath, byte[] configBytes, Map<String, String> sources, IntFunction<String> sourceResolver) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name cannot be blank");
        if (configBytes == null && (location == null || location.isBlank())) throw new IllegalArgumentException("Config path cannot be blank");
        if (configBytes != null && configBytes.length == 0) throw new IllegalArgumentException("Config bytes cannot be empty");
        this.location = location;
        this.name = name;
        this.classpath = classpath;
        this.configBytes = configBytes == null ? null : Arrays.copyOf(configBytes, configBytes.length);
        this.sources = Map.copyOf(sources);
        this.sourceResolver = sourceResolver;
    }

    /**
     * Defers a filesystem config, using its path as cache identity.
     * @param path PropertySet text file
     * @return immutable request
     */
    public static ShaderParameters fromPath(String path) {
        return fromPath(path, path);
    }

    /**
     * Defers a filesystem config and relative GLSL files.
     * @param path config file
     * @param name cache key
     * @return immutable request
     */
    public static ShaderParameters fromPath(String path, String name) {
        return new ShaderParameters(path, name, false, null, Map.of(), null);
    }

    /**
     * Defers a classpath config and relative GLSL resources.
     * @param resourcePath config resource
     * @param name cache key
     * @return immutable request
     */
    public static ShaderParameters fromClasspath(String resourcePath, String name) {
        return new ShaderParameters(resourcePath, name, true, null, Map.of(), null);
    }

    /**
     * Snapshots a config using inline sources or a later attached source resolver.
     * @param configBytes UTF-8 PropertySet text
     * @param name cache key
     * @return immutable request
     */
    public static ShaderParameters fromBytes(byte[] configBytes, String name) {
        return fromBytes(configBytes, Map.of(), name);
    }

    /**
     * Snapshots a config and its named GLSL dependencies without filesystem I/O.
     * @param configBytes UTF-8 text
     * @param sources names matching stage path properties mapped to GLSL strings
     * @param name cache key
     * @return immutable byte request
     */
    public static ShaderParameters fromBytes(byte[] configBytes, Map<String, String> sources, String name) {
        if (configBytes == null || sources == null) throw new IllegalArgumentException("Config and sources cannot be null");
        return new ShaderParameters("", name, false, configBytes, sources, null);
    }

    /**
     * Returns a request with the same sources and a caller-supplied source-ID lookup.
     * @param sourceResolver non-null lookup returning GLSL for vertexID/fragmentID/computeID
     * @return new request; does not mutate this instance
     */
    public ShaderParameters withSourceResolver(IntFunction<String> sourceResolver) {
        if (sourceResolver == null) throw new IllegalArgumentException("Source resolver cannot be null");
        return new ShaderParameters(location, name, classpath, configBytes, sources, sourceResolver);
    }

    /**
     * Returns the shared cache identity.
     * @return nonblank name
     */
    public String name() {
        return name;
    }

    @Override
    public String key() {
        return name;
    }

    /**
     * Reads deferred config bytes or returns a private snapshot to the loader.
     * @return UTF-8 configuration
     * @throws IOException if filesystem reading fails
     */
    byte[] readConfig() throws IOException {
        if (configBytes != null) return configBytes.clone();
        return classpath ? ValthorneFiles.readBytes(location) : Files.readAllBytes(Path.of(location));
    }

    /**
     * Resolves one stage file relative to the config, or from the in-memory map.
     * @param path configured dependency name
     * @return GLSL source
     */
    String readSource(String path) {
        if (sources.containsKey(path)) return sources.get(path);
        if (configBytes != null) throw new IllegalArgumentException("No shader source supplied for " + path);
        Path parent = Path.of(location).getParent();
        Path resolved = (parent == null ? Path.of(path) : parent.resolve(path)).normalize();
        if (classpath) return ValthorneFiles.readString(resolved.toString().replace('\\', '/'));
        try {
            return Files.readString(resolved);
        } catch (IOException failure) {
            throw new UncheckedIOException("Unable to read shader source: " + resolved, failure);
        }
    }

    /**
     * Supplies the optional cache lookup to the trusted loader.
     * @return borrowed lookup or null
     */
    IntFunction<String> sourceResolver() {
        return sourceResolver;
    }
}
