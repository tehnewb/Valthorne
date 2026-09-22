package valthorne.graphics.map.ldtk;

import valthorne.asset.AssetLoader;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Worker-safe asset loader that parses LDtk JSON and decodes referenced images
 * without creating OpenGL resources. This separates filesystem and CPU work from
 * the render-thread-only construction performed by {@link LdtkMap}.
 *
 * <p>Typical asset-manager code constructs parameters and delegates them to this
 * loader:</p>
 * <pre>{@code
 * LdtkParameters parameters = LdtkParameters.fromPath("maps/world.ldtk");
 * LdtkProject project = new LdtkLoader().load(parameters);
 * }</pre>
 */
public final class LdtkLoader implements AssetLoader<LdtkParameters, LdtkProject> {
    /**
     * Reads and parses a project from the configured path or byte source.
     *
     * <p>Checked I/O and resolver failures are wrapped in a runtime exception;
     * existing runtime exceptions retain their original type. The returned project
     * owns all decoded {@code TextureData} and must eventually be disposed.</p>
     *
     * @param parameters non-null validated LDtk loader parameters
     * @return parsed CPU-side project with decoded referenced images
     * @throws NullPointerException if {@code parameters} is null
     * @throws IllegalArgumentException if the source kind is unsupported
     * @throws RuntimeException if reading, dependency resolution, or parsing fails
     */
    @Override
    public LdtkProject load(LdtkParameters parameters) {
        try {
            LdtkDependencyResolver resolver = LdtkResolvers.from(parameters.dependencies());
            if (parameters.source() instanceof LdtkSource.PathSource(String path)) {
                byte[] bytes = Files.readAllBytes(Path.of(path));
                return LdtkProject.load(bytes, path, resolver);
            }
            if (parameters.source() instanceof LdtkSource.BytesSource(byte[] bytes, String virtualPath))
                return LdtkProject.load(bytes, virtualPath, resolver);
            throw new IllegalArgumentException("Unsupported LDtk source: " + parameters.source());
        } catch (Exception e) {
            throw e instanceof RuntimeException r ? r : new RuntimeException(e);
        }
    }
}
