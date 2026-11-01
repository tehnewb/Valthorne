package valthorne.graphics.font.adaptive;

import valthorne.asset.AssetParameters;
import valthorne.graphics.font.FontSource;
import valthorne.graphics.font.FontSource.BytesSource;
import valthorne.graphics.font.FontSource.PathSource;
import valthorne.io.file.ValthorneFiles;

/**
 * Size-independent encoded font request. Only name supplies the shared cache
 * key. Size and hinting are settings on the rendering font, so one loaded asset
 * can create independently configured fonts. Path reads are deferred to loading;
 * bytes are copied and classpath factories read immediately. Prewarm ranges
 * never restrict Unicode shaping; automatic background preparation has bounded admission.
 * @param source encoded font source
 * @param name nonblank shared asset-cache key
 * @param firstCharacterIndex first Unicode prewarm codepoint
 * @param characterCount number of consecutive prewarm codepoints
 */
public record AdaptiveFontParameters(FontSource source, String name, int firstCharacterIndex, int characterCount) implements AssetParameters {
    /**
     * Validates source, cache identity and the bounded Unicode prewarm range.
     * @param source non-null encoded source
     * @param name nonblank shared asset-cache key
     * @param firstCharacterIndex first Unicode prewarm codepoint
     * @param characterCount range length, from 1 through 65536
     * @throws IllegalArgumentException if metadata is invalid
     */
    public AdaptiveFontParameters {
        if (source == null) throw new IllegalArgumentException("source cannot be null");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name cannot be null/blank");
        if (firstCharacterIndex < 0 || characterCount < 1 || characterCount > 65536 || (long) firstCharacterIndex + characterCount > 0x110000)
            throw new IllegalArgumentException("Invalid Unicode prewarm range");
    }

    /**
     * Uses the path as the cache key with printable ASCII prewarming.
     * @param path filesystem source
     * @return immutable loading request
     */
    public static AdaptiveFontParameters fromPath(String path) {
        return fromPath(path, path);
    }

    /**
     * Names a filesystem source with printable ASCII prewarming.
     * @param path filesystem source
     * @param name shared asset-cache key
     * @return immutable loading request
     */
    public static AdaptiveFontParameters fromPath(String path, String name) {
        return fromPath(path, name, 32, 95);
    }

    /**
     * Describes a path with an explicit Unicode prewarm range.
     * @param path filesystem source
     * @param name shared asset-cache key
     * @param firstChar first Unicode prewarm codepoint
     * @param count number of prewarm codepoints
     * @return immutable loading request
     */
    public static AdaptiveFontParameters fromPath(String path, String name, int firstChar, int count) {
        return new AdaptiveFontParameters(new PathSource(path), name, firstChar, count);
    }

    /**
     * Snapshots bytes with printable ASCII prewarming.
     * @param bytes nonempty encoded font
     * @param name shared asset-cache key
     * @return immutable loading request
     */
    public static AdaptiveFontParameters fromBytes(byte[] bytes, String name) {
        return fromBytes(bytes, name, 32, 95);
    }

    /**
     * Snapshots bytes with an explicit Unicode prewarm range.
     * @param bytes nonempty encoded font
     * @param name shared asset-cache key
     * @param firstChar first Unicode prewarm codepoint
     * @param count number of prewarm codepoints
     * @return immutable loading request
     */
    public static AdaptiveFontParameters fromBytes(byte[] bytes, String name, int firstChar, int count) {
        return new AdaptiveFontParameters(new BytesSource(bytes), name, firstChar, count);
    }

    /**
     * Reads classpath bytes immediately with printable ASCII prewarming.
     * @param resourcePath resource name relative to the classpath root
     * @param name shared asset-cache key
     * @return immutable loading request
     */
    public static AdaptiveFontParameters fromClasspath(String resourcePath, String name) {
        return fromClasspath(resourcePath, name, 32, 95);
    }

    /**
     * Reads classpath bytes immediately with an explicit prewarm range.
     * @param resourcePath resource name relative to the classpath root
     * @param name shared asset-cache key
     * @param firstChar first Unicode prewarm codepoint
     * @param count number of prewarm codepoints
     * @return immutable loading request
     */
    public static AdaptiveFontParameters fromClasspath(String resourcePath, String name, int firstChar, int count) {
        return fromBytes(ValthorneFiles.readBytes(resourcePath), name, firstChar, count);
    }

    @Override
    public String key() { return name; }
}
