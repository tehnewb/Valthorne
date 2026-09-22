package valthorne.graphics.font.slug;

import valthorne.asset.AssetParameters;
import valthorne.io.file.ValthorneFiles;

/**
 * Immutable asset request for encoded Slug font data and its compiled character range.
 * Loading through {@link valthorne.asset.Assets} performs file I/O only; create the GPU
 * font later with {@link SlugFont#load(SlugData)} on the graphics-context thread.
 *
 * @param source         encoded font source
 * @param name           nonblank shared asset-cache key
 * @param firstCodepoint first codepoint to compile
 * @param characterCount number of consecutive codepoints, from 1 through 256
 * @author Albert Beaupre
 */
public record SlugParameters(SlugSource source, String name, int firstCodepoint, int characterCount) implements AssetParameters {

    /**
     * Validates a complete Slug asset request.
     */
    public SlugParameters {
        if (source == null) throw new IllegalArgumentException("source cannot be null");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name cannot be null/blank");
        if (characterCount <= 0 || characterCount > 256)
            throw new IllegalArgumentException("characterCount must be in the range [1, 256]");
    }

    /**
     * Creates a printable-ASCII request from a filesystem path.
     */
    public static SlugParameters fromPath(String path) {
        return fromPath(path, path, 32, 95);
    }

    /**
     * Creates a custom-range request from a filesystem path.
     */
    public static SlugParameters fromPath(String path, String name, int firstCodepoint, int characterCount) {
        return new SlugParameters(new SlugSource.PathSource(path), name, firstCodepoint, characterCount);
    }

    /**
     * Creates a printable-ASCII request from encoded bytes.
     */
    public static SlugParameters fromBytes(byte[] bytes, String name) {
        return fromBytes(bytes, name, 32, 95);
    }

    /**
     * Creates a custom-range request from encoded bytes.
     */
    public static SlugParameters fromBytes(byte[] bytes, String name, int firstCodepoint, int characterCount) {
        return new SlugParameters(new SlugSource.BytesSource(bytes), name, firstCodepoint, characterCount);
    }

    /**
     * Creates a printable-ASCII request from a classpath resource.
     */
    public static SlugParameters fromClasspath(String resourcePath, String name) {
        return fromBytes(ValthorneFiles.readBytes(resourcePath), name);
    }

    /**
     * Creates a custom-range request from a classpath resource.
     */
    public static SlugParameters fromClasspath(String resourcePath, String name, int firstCodepoint, int characterCount) {
        return fromBytes(ValthorneFiles.readBytes(resourcePath), name, firstCodepoint, characterCount);
    }

    /**
     * @return the nonblank cache name
     */
    @Override
    public String key() {
        return name;
    }
}
