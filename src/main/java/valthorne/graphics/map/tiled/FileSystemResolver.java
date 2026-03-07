package valthorne.graphics.map.tiled;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

public final class FileSystemResolver implements TiledDependencyResolver {

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