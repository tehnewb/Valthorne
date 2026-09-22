package valthorne.graphics.map.ldtk;

/**
 * Resolves an external level, tileset image, or other file referenced by LDtk.
 * Implementations may read a filesystem, archive, classpath, or in-memory store.
 * The parent content is supplied for resolvers whose addressing scheme depends on
 * the referring document rather than only its path.
 */
@FunctionalInterface
public interface LdtkDependencyResolver {
    /**
     * Loads one dependency referenced by an LDtk document.
     *
     * @param parentBytes bytes of the document containing the reference
     * @param parentPath logical or filesystem path of that document
     * @param dependencyPath path written in the LDtk reference
     * @return complete dependency bytes; never {@code null}
     * @throws Exception if the path cannot be resolved or the bytes cannot be read
     */
    byte[] resolve(byte[] parentBytes, String parentPath, String dependencyPath) throws Exception;
}
