package valthorne.graphics.map.tiled;

/**
 * Defines an interface for resolving dependencies in Tiled map structures or related assets
 * such as external tileset or image files. This allows implementations to provide functionality
 * for locating and loading resources needed by Tiled map components.
 */
public interface TiledDependencyResolver {

    /**
     * Resolves a dependency file path relative to the given parent resource, and retrieves the
     * content of the dependency as a byte array.
     *
     * @param parentBytes    The byte content of the parent resource that references the dependency.
     * @param parentPath     The path to the parent resource, used as the base for resolving the dependency path.
     * @param dependencyPath The path to the dependency resource to be resolved, relative to the parent path.
     * @return A byte array containing the content of the resolved dependency resource.
     * @throws Exception If the resolution or retrieval of the dependency fails.
     */
    byte[] resolve(byte[] parentBytes, String parentPath, String dependencyPath) throws Exception;
}