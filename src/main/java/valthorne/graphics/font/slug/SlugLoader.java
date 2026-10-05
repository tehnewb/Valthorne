package valthorne.graphics.font.slug;

import valthorne.asset.AssetLoader;

/**
 * Loads immutable, CPU-only {@link SlugData} for the shared asset service. GPU
 * compilation is intentionally deferred to {@link SlugData#asFont()} because
 * asset workers do not own Valthorne's OpenGL context.
 *
 * @author Albert Beaupre
 */
public final class SlugLoader implements AssetLoader<SlugParameters, SlugData> {

    /**
     * Reads or copies the encoded source while preserving the requested range.
     *
     * @param parameters validated Slug asset request
     * @return independently owned encoded font data
     */
    @Override
    public SlugData load(SlugParameters parameters) {
        if (parameters == null) throw new NullPointerException("parameters");
        String path = parameters.path();
        if (path != null) return SlugData.load(path, parameters.firstCodepoint(), parameters.characterCount());
        return SlugData.load(parameters.bytes(), parameters.firstCodepoint(), parameters.characterCount());
    }
}
