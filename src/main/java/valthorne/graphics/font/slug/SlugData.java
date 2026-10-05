package valthorne.graphics.font.slug;

import java.util.Arrays;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Immutable CPU-only encoded data loaded from a file, byte array or
 * {@link SlugLoader}. Loading is safe on an asset worker and creates no GPU
 * resources. Call {@link #asFont()} later on the graphics thread to compile
 * outlines and allocate a separately owned font's textures.
 *
 * @param bytes          encoded TrueType or OpenType data
 * @param firstCodepoint first codepoint requested for compilation
 * @param characterCount consecutive codepoint count
 * @author Albert Beaupre
 */
public record SlugData(byte[] bytes, int firstCodepoint, int characterCount) {
    /**
     * Validates metadata and takes an immutable snapshot of the encoded bytes.
     */
    public SlugData {
        if (bytes == null || bytes.length == 0) throw new IllegalArgumentException("bytes cannot be null/empty");
        if (characterCount <= 0 || characterCount > 256)
            throw new IllegalArgumentException("characterCount must be in the range [1, 256]");
        bytes = Arrays.copyOf(bytes, bytes.length);
    }

    /**
     * Reads the 95 printable ASCII characters beginning at codepoint 32.
     * No OpenGL context is required until {@link #asFont()} is called.
     *
     * @param path TrueType/OpenType filesystem path
     * @return immutable encoded data for the printable range
     * @throws IllegalStateException if the file cannot be read
     */
    public static SlugData load(String path) {
        /*
         * Share the printable range with the explicit-range loading path.
         */
        return load(path, 32, 95);
    }

    /**
     * Reads encoded font bytes and records the requested contiguous range.
     * Font decoding and GPU compilation are deferred to {@link #asFont()}.
     *
     * @param path TrueType/OpenType filesystem path
     * @param firstCodepoint first character to compile
     * @param characterCount range length from 1 through 256
     * @return immutable encoded data with the requested range
     * @throws IllegalStateException if the file cannot be read
     * @throws IllegalArgumentException if bytes are empty or the count is invalid
     */
    public static SlugData load(String path, int firstCodepoint, int characterCount) {
        /*
         * Preserve the I/O cause while keeping file access outside GPU objects.
         */
        try {
            return load(Files.readAllBytes(Path.of(path)), firstCodepoint, characterCount);
        } catch (IOException failure) {
            throw new IllegalStateException("Unable to read Slug font: " + path, failure);
        }
    }

    /**
     * Snapshots encoded bytes for a requested contiguous character range.
     * The caller may reuse its array; no font decoding or GPU allocation occurs.
     *
     * @param bytes nonnull, nonempty TrueType/OpenType data
     * @param firstCodepoint first character to compile
     * @param characterCount range length from 1 through 256
     * @return immutable encoded data with the requested range
     * @throws IllegalArgumentException if bytes are absent or the count is invalid
     */
    public static SlugData load(byte[] bytes, int firstCodepoint, int characterCount) {
        /*
         * The canonical constructor performs validation and the sole source snapshot.
         */
        return new SlugData(bytes, firstCodepoint, characterCount);
    }

    /**
     * @return an independent copy of the encoded bytes
     */
    @Override
    public byte[] bytes() {
        return Arrays.copyOf(bytes, bytes.length);
    }

    /**
     * Compiles this data into a new font and uploads its textures. Call on the
     * thread owning the current OpenGL context; dispose the returned font when
     * it is no longer needed. This data remains reusable for additional fonts.
     *
     * @return newly owned font using this data's configured character range
     * @throws RuntimeException if STB cannot initialize the encoded font
     */
    public SlugFont asFont() {
        return new SlugFont(this);
    }
}
