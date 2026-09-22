package valthorne.graphics.font.slug;

import java.util.Arrays;

/**
 * CPU-only encoded data loaded by {@link SlugLoader}. It is safe to create on an
 * asset worker; {@link SlugFont#load(SlugData)} consumes it later on the graphics
 * thread to compile outlines and allocate textures.
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
     * @return an independent copy of the encoded bytes
     */
    @Override
    public byte[] bytes() {
        return Arrays.copyOf(bytes, bytes.length);
    }
}
