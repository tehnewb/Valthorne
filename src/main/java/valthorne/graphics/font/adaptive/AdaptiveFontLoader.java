package valthorne.graphics.font.adaptive;

import valthorne.graphics.font.FontSource;
import valthorne.graphics.font.FontSource.PathSource;
import valthorne.graphics.font.FontSource.BytesSource;

import valthorne.asset.AssetLoader;

/**
 * CPU-only asset loader for immutable encoded adaptive font data. Path reads and
 * defensive byte snapshots may run on the asset executor; this loader allocates
 * neither native faces nor GPU objects. Construct the rendering font separately
 * on its graphics owner thread using {@link AdaptiveFontData#asFont()}.
 *
 * @author Albert Beaupre
 * @see AssetLoader
 * @see AdaptiveFontParameters
 * @see AdaptiveFontData
 */
public final class AdaptiveFontLoader implements AssetLoader<AdaptiveFontParameters, AdaptiveFontData> {

    /**
     * Creates a stateless loader safe for concurrent asset-worker requests.
     */
    public AdaptiveFontLoader() {}

    @Override
    public AdaptiveFontData load(AdaptiveFontParameters parameters) {
        FontSource src = parameters.source();

        if (src instanceof PathSource(String path)) {
            return AdaptiveFontData.load(path, parameters.firstCharacterIndex(), parameters.characterCount());
        }

        if (src instanceof BytesSource(byte[] bytes)) {
            return AdaptiveFontData.load(bytes, parameters.firstCharacterIndex(), parameters.characterCount());
        }

        throw new IllegalStateException("Unknown FontSource: " + src.getClass().getName());
    }
}
