package valthorne.graphics.font.slug;

import valthorne.asset.AssetParameters;
import valthorne.asset.Assets;
import valthorne.io.file.ValthorneFiles;

import java.util.Arrays;

/**
 * Immutable asset request for encoded Slug font data and its compiled character range.
 * Loading through {@link Assets} performs file I/O only; create the GPU
 * font later with {@link SlugData#asFont()} on the graphics-context thread.
 *
 * Exactly one source is present: path is nonnull for deferred file loading, or
 * bytes is nonnull for a copied in-memory source. Factories cover filesystem,
 * classpath and encoded-byte inputs without a separate source type.
 *
 * @param path           nonblank font path, or null for an encoded-byte request
 * @param bytes          copied encoded data, or null for a filesystem request
 * @param name           nonblank shared asset-cache key
 * @param firstCodepoint first codepoint to compile
 * @param characterCount number of consecutive codepoints, from 1 through 256
 * @author Albert Beaupre
 */
public record SlugParameters(String path, byte[] bytes, String name, int firstCodepoint, int characterCount) implements AssetParameters {

    /**
     * Validates a complete Slug asset request.
     */
    public SlugParameters {
        if ((path == null) == (bytes == null)) throw new IllegalArgumentException("Exactly one of path or bytes must be supplied.");
        if (path != null && path.isBlank()) throw new IllegalArgumentException("path cannot be blank");
        if (bytes != null) {
            if (bytes.length == 0) throw new IllegalArgumentException("bytes cannot be empty");
            bytes = Arrays.copyOf(bytes, bytes.length);
        }
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name cannot be null/blank");
        if (characterCount <= 0 || characterCount > 256)
            throw new IllegalArgumentException("characterCount must be in the range [1, 256]");
    }

    /**
     * Creates a printable-ASCII request from a filesystem path.
     *
     * @param path nonblank source path, also used as the cache name
     * @return validated deferred-file request
     */
    public static SlugParameters fromPath(String path) {
        /*
         * Use the path as the default asset key without performing file I/O.
         */
        return fromPath(path, path, 32, 95);
    }

    /**
     * Creates a custom-range request from a filesystem path.
     *
     * @param path nonblank source path; file reading is deferred
     * @param name nonblank shared asset-cache key
     * @param firstCodepoint first character requested for compilation
     * @param characterCount consecutive character count, from 1 through 256
     * @return validated deferred-file request
     */
    public static SlugParameters fromPath(String path, String name, int firstCodepoint, int characterCount) {
        /*
         * Retain the path until the asset worker loads it; encoded bytes stay absent.
         */
        return new SlugParameters(path, null, name, firstCodepoint, characterCount);
    }

    /**
     * Creates a printable-ASCII request from encoded bytes.
     *
     * @param bytes nonempty encoded font data copied by the request
     * @param name nonblank shared asset-cache key
     * @return validated in-memory request
     */
    public static SlugParameters fromBytes(byte[] bytes, String name) {
        /*
         * Share the same validated byte-source path for the printable ASCII range.
         */
        return fromBytes(bytes, name, 32, 95);
    }

    /**
     * Creates a custom-range request from encoded bytes.
     *
     * @param bytes nonempty encoded font data copied by the request
     * @param name nonblank shared asset-cache key
     * @param firstCodepoint first character requested for compilation
     * @param characterCount consecutive character count, from 1 through 256
     * @return validated in-memory request
     */
    public static SlugParameters fromBytes(byte[] bytes, String name, int firstCodepoint, int characterCount) {
        /*
         * The record constructor snapshots caller-owned bytes exactly once.
         */
        return new SlugParameters(null, bytes, name, firstCodepoint, characterCount);
    }

    /**
     * Creates a printable-ASCII request from a classpath resource.
     *
     * @param resourcePath classpath resource resolved immediately
     * @param name nonblank shared asset-cache key
     * @return validated encoded-byte request
     */
    public static SlugParameters fromClasspath(String resourcePath, String name) {
        /*
         * Classpath requests resolve to encoded bytes rather than filesystem paths.
         */
        return fromBytes(ValthorneFiles.readBytes(resourcePath), name);
    }

    /**
     * Creates a custom-range request from a classpath resource.
     *
     * @param resourcePath classpath resource resolved immediately
     * @param name nonblank shared asset-cache key
     * @param firstCodepoint first character requested for compilation
     * @param characterCount consecutive character count, from 1 through 256
     * @return validated encoded-byte request
     */
    public static SlugParameters fromClasspath(String resourcePath, String name, int firstCodepoint, int characterCount) {
        /*
         * Resolve the resource before handing a CPU-only request to the asset service.
         */
        return fromBytes(ValthorneFiles.readBytes(resourcePath), name, firstCodepoint, characterCount);
    }

    /**
     * @return the nonblank cache name
     */
    @Override
    public String key() {
        return name;
    }

    @Override
    public byte[] bytes() {
        return bytes == null ? null : Arrays.copyOf(bytes, bytes.length);
    }
}
