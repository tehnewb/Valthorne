package valthorne.graphics.map.tiled;

import valthorne.asset.AssetLoader;

import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Asset loader responsible for converting {@link TiledMapParameters} into {@link TiledMapData}.
 *
 * <p>
 * This loader is the bridge between Valthorne's asset system and the Tiled map parser.
 * It reads the primary TMX source, creates the appropriate dependency resolver from the
 * provided dependency source, and then loads the map into CPU-side {@link TiledMapData}.
 * </p>
 *
 * <p>
 * The result of this loader is intentionally {@code TiledMapData} instead of {@link TiledMap}
 * because map loading may occur on a worker thread. The returned object contains decoded
 * CPU-side texture data but does not create any OpenGL resources.
 * </p>
 *
 * <h2>Supported source types</h2>
 * <ul>
 *     <li>{@link TiledMapSource.PathSource}</li>
 *     <li>{@link TiledMapSource.BytesSource}</li>
 * </ul>
 *
 * @author Albert Beaupre
 * @since March 7th, 2026
 */
public class TiledMapLoader implements AssetLoader<TiledMapParameters, TiledMapData> {

    /**
     * Loads a Tiled map into CPU-side {@link TiledMapData}.
     *
     * <p>
     * This method creates a {@link TiledDependencyResolver} from the supplied dependency
     * source and then dispatches loading based on whether the primary TMX map was supplied
     * as a file path or as raw bytes.
     * </p>
     *
     * @param parameters the Tiled map asset parameters
     * @return the loaded CPU-side map data
     * @throws RuntimeException if loading fails or if an unsupported source type is supplied
     */
    @Override
    public TiledMapData load(TiledMapParameters parameters) {
        TiledDependencyResolver resolver = TiledResolvers.from(parameters.dependencies());
        TiledMapSource source = parameters.source();

        try {
            if (source instanceof TiledMapSource.PathSource(String path)) {
                byte[] bytes = Files.readAllBytes(Paths.get(path));
                return TiledMapData.load(bytes, path, resolver);
            }

            if (source instanceof TiledMapSource.BytesSource(byte[] bytes, String virtualPath)) {
                return TiledMapData.load(bytes, virtualPath, resolver);
            }

            throw new IllegalStateException("Unknown TiledMapSource: " + source.getClass().getName());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}