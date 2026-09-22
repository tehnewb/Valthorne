package valthorne.graphics.font.slug;

import valthorne.asset.AssetLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Loads immutable, CPU-only {@link SlugData} for the shared asset service. GPU
 * compilation is intentionally deferred to {@link SlugFont#load(SlugData)} because
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
        SlugSource source = parameters.source();
        byte[] bytes;
        if (source instanceof SlugSource.PathSource(String path)) {
            try {
                bytes = Files.readAllBytes(Path.of(path));
            } catch (IOException exception) {
                throw new IllegalStateException("Unable to read Slug font: " + path, exception);
            }
        } else if (source instanceof SlugSource.BytesSource(byte[] sourceBytes)) {
            bytes = sourceBytes;
        } else {
            throw new IllegalStateException("Unknown SlugSource: " + source.getClass().getName());
        }
        return new SlugData(bytes, parameters.firstCodepoint(), parameters.characterCount());
    }
}
