package valthorne.graphics.map.ldtk;

import valthorne.asset.AssetParameters;

import java.util.Map;

/**
 * Immutable asset-manager parameters for loading an LDtk project. The source
 * selects the primary document, dependencies select referenced-file resolution,
 * and the name becomes the asset cache key.
 *
 * @param source primary LDtk document
 * @param dependencies dependency-resolution strategy
 * @param name nonblank asset-manager key
 */
public record LdtkParameters(LdtkSource source, LdtkDependencySource dependencies, String name) implements AssetParameters {
    /**
     * Validates the required source, dependency strategy, and cache key.
     *
     * @param source non-null primary source
     * @param dependencies non-null dependency source
     * @param name nonblank asset key
     * @throws IllegalArgumentException if any required value is missing or blank
     */
    public LdtkParameters {
        if (source == null || dependencies == null)
            throw new IllegalArgumentException("source and dependencies are required");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name cannot be blank");
    }

    /**
     * Creates filesystem-backed parameters using the path as the asset key.
     *
     * @param path nonblank LDtk project path
     * @return validated loader parameters
     * @throws IllegalArgumentException if {@code path} is null or blank
     */
    public static LdtkParameters fromPath(String path) {
        return fromPath(path, path);
    }

    /**
     * Creates filesystem-backed parameters with an explicit asset key.
     *
     * @param path nonblank LDtk project path
     * @param name nonblank asset key
     * @return validated loader parameters
     * @throws IllegalArgumentException if either value is null or blank
     */
    public static LdtkParameters fromPath(String path, String name) {
        return new LdtkParameters(new LdtkSource.PathSource(path), new LdtkDependencySource.FileSystemSource(), name);
    }

    /**
     * Creates in-memory parameters using the virtual path as the asset key.
     *
     * @param bytes nonempty LDtk project bytes
     * @param virtualPath logical project path used for relative dependencies
     * @param files dependency bytes keyed by logical path
     * @return validated loader parameters containing defensive copies
     * @throws IllegalArgumentException if source or dependency data is invalid
     */
    public static LdtkParameters fromBytes(byte[] bytes, String virtualPath, Map<String, byte[]> files) {
        return fromBytes(bytes, virtualPath, files, virtualPath);
    }

    /**
     * Creates in-memory parameters with an explicit asset key.
     *
     * @param bytes nonempty LDtk project bytes
     * @param virtualPath logical project path used for relative dependencies
     * @param files dependency bytes keyed by logical path
     * @param name nonblank asset key
     * @return validated loader parameters containing defensive copies
     * @throws IllegalArgumentException if source, dependency, or key data is invalid
     */
    public static LdtkParameters fromBytes(byte[] bytes, String virtualPath, Map<String, byte[]> files, String name) {
        return new LdtkParameters(new LdtkSource.BytesSource(bytes, virtualPath), new LdtkDependencySource.MapSource(files), name);
    }

    /**
     * Returns the stable asset-manager cache key.
     *
     * @return the configured nonblank name
     */
    @Override
    public String key() {
        return name;
    }
}
