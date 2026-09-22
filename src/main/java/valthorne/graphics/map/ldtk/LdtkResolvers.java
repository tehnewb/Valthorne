package valthorne.graphics.map.ldtk;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Resolver factories and path utilities shared by asset-loader and direct-loading
 * APIs. Filesystem resolution is relative to the referring document; in-memory
 * resolution accepts both fully resolved and raw normalized dependency keys.
 */
public final class LdtkResolvers {
    /**
     * Prevents instantiation of this utility class.
     */
    private LdtkResolvers() {
    }

    /**
     * Builds a resolver for a supported dependency source.
     *
     * <p>Map-backed resolvers return cloned byte arrays so parsing cannot mutate
     * the source snapshot. Filesystem resolvers propagate read failures through
     * the resolver's checked-exception contract.</p>
     *
     * @param source filesystem or in-memory dependency configuration
     * @return resolver implementing the selected strategy
     * @throws IllegalArgumentException if {@code source} is null or unsupported
     */
    public static LdtkDependencyResolver from(LdtkDependencySource source) {
        if (source instanceof LdtkDependencySource.FileSystemSource) {
            return (parentBytes, parentPath, dependencyPath) -> Files.readAllBytes(resolve(parentPath, dependencyPath));
        }
        if (source instanceof LdtkDependencySource.MapSource memory) {
            Map<String, byte[]> files = memory.files();
            return (parentBytes, parentPath, dependencyPath) -> {
                String resolved = normalize(resolve(parentPath, dependencyPath).toString());
                byte[] bytes = files.get(resolved);
                if (bytes == null) bytes = files.get(normalize(dependencyPath));
                if (bytes == null) throw new IllegalArgumentException("Missing LDtk dependency: " + resolved);
                return bytes.clone();
            };
        }
        throw new IllegalArgumentException("Unknown LDtk dependency source: " + source);
    }

    /**
     * Resolves a dependency path against the directory of its parent document.
     * Absolute dependencies are only normalized and are never rebased.
     *
     * @param parentPath logical or filesystem path of the referring document
     * @param dependencyPath referenced child path
     * @return normalized absolute or parent-relative path
     * @throws RuntimeException if either path cannot be converted to a {@link Path}
     */
    static Path resolve(String parentPath, String dependencyPath) {
        Path child = Path.of(dependencyPath);
        if (child.isAbsolute()) return child.normalize();
        Path parent = Path.of(parentPath).normalize().getParent();
        return (parent == null ? child : parent.resolve(child)).normalize();
    }

    /**
     * Normalizes path separators for stable in-memory lookup keys.
     *
     * @param path path text to normalize
     * @return the same text with backslashes replaced by forward slashes
     * @throws NullPointerException if {@code path} is null
     */
    static String normalize(String path) {
        return path.replace('\\', '/');
    }
}
