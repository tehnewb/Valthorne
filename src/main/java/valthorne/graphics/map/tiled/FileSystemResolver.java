package valthorne.graphics.map.tiled;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

/**
 * Reads Tiled dependencies from the filesystem relative to their referring file.
 * Absolute dependency paths are used directly; relative paths use the normalized
 * parent's directory, falling back to the process working directory when unavailable.
 * Resolution performs a fresh synchronous read and does not cache content or restrict
 * paths to the map's directory. Parent bytes are not needed by this implementation.
 *
 * @author Albert Beaupre
 */
public final class FileSystemResolver implements TiledDependencyResolver {

    /**
     * Resolves and normalizes a dependency to an absolute path, then reads all bytes.
     * A blank or null parent path uses the working directory for relative dependencies.
     * The returned array is newly allocated by the file read.
     *
     * @param parentBytes unused parent content; may be null
     * @param parentPath referring file's path, or null when unavailable
     * @param dependencyPath absolute or relative dependency path
     * @return complete file contents
     * @throws NullPointerException if dependencyPath is null
     * @throws Exception if the path cannot be resolved or its bytes cannot be read
     */
    @Override
    public byte[] resolve(byte[] parentBytes, String parentPath, String dependencyPath) throws Exception {
        Objects.requireNonNull(dependencyPath, "dependencyPath");

        Path dep = Paths.get(dependencyPath);

        if (!dep.isAbsolute()) {
            Path baseDir = null;

            if (parentPath != null && !parentPath.isBlank()) {
                Path parent = Paths.get(parentPath).toAbsolutePath().normalize();
                baseDir = parent.getParent();
            }

            dep = Objects.requireNonNullElseGet(baseDir, () -> Paths.get(System.getProperty("user.dir"))).resolve(dependencyPath);
        }

        dep = dep.toAbsolutePath().normalize();
        return Files.readAllBytes(dep);
    }
}
