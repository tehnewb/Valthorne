package valthorne.graphics.font.adaptive;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * Immutable CPU-only encoded font asset following the FontData/asFont lifecycle.
 * Load on an asset worker, then call {@link #asFont()} on the graphics thread.
 * Each resulting font owns independent native faces, layouts and GPU caches.
 * The initial size is logical units per em; rendering can use any positive size.
 * Character ranges describe prewarming rather than restricting Unicode shaping;
 * first draw prewarms at most 256 nominal characters to bound startup work.
 * Supported sources are scalable SFNT outlines (TTF, OTF, TTC and OTC) and WOFF.
 * Collections select the first face and variable fonts use their default axes.
 * WOFF2, bitmap-only fonts and legacy Type 1 sources are rejected by asFont().
 *
 * @param bytes encoded TrueType/OpenType snapshot
 * @param fontSize initial logical size
 * @param firstCharacterIndex first codepoint to prewarm
 * @param characterCount number of consecutive codepoints to prewarm
 */
public record AdaptiveFontData(byte[] bytes, int fontSize, int firstCharacterIndex, int characterCount) {
    /**
     * Validates metadata and copies caller-owned bytes without native allocation.
     * @param bytes nonempty encoded OpenType source
     * @param fontSize initial logical em size, from 1 through 4096
     * @param firstCharacterIndex first Unicode prewarm codepoint
     * @param characterCount prewarm range length, from 1 through 65536
     * @throws IllegalArgumentException if bytes or metadata are outside supported bounds
     */
    public AdaptiveFontData {
        if (bytes == null || bytes.length == 0) throw new IllegalArgumentException("Font bytes cannot be empty");
        if (fontSize < 1 || fontSize > 4096) throw new IllegalArgumentException("Font size must be between 1 and 4096");
        if (firstCharacterIndex < 0 || characterCount < 1 || characterCount > 65536 || (long) firstCharacterIndex + characterCount > 0x110000)
            throw new IllegalArgumentException("Invalid Unicode prewarm range");
        bytes = Arrays.copyOf(bytes, bytes.length);
    }

    @Override
    public byte[] bytes() { return Arrays.copyOf(bytes, bytes.length); }

    /**
     * Reads a font with the standard printable ASCII prewarm range.
     * @param path filesystem source
     * @param fontSize initial logical em size
     * @return immutable encoded font asset
     */
    public static AdaptiveFontData load(String path, int fontSize) {
        /*
         * Keep the short factory useful for game UI without eagerly caching all Unicode.
         */
        return load(path, fontSize, 32, 95);
    }

    /**
     * Snapshots encoded bytes with the printable ASCII prewarm range.
     * @param bytes encoded OpenType source
     * @param fontSize initial logical em size
     * @return immutable CPU asset
     */
    public static AdaptiveFontData load(byte[] bytes, int fontSize) {
        /*
         * Delegate snapshot ownership and range validation to the canonical constructor.
         */
        return new AdaptiveFontData(bytes, fontSize, 32, 95);
    }

    /**
     * Reads encoded data without creating a native face or requiring OpenGL.
     * @param path filesystem source
     * @param fontSize initial logical em size
     * @param firstChar first prewarm codepoint
     * @param count prewarm count
     * @return immutable encoded font asset
     * @throws UncheckedIOException if reading fails
     */
    public static AdaptiveFontData load(String path, int fontSize, int firstChar, int count) {
        /*
         * Preserve the original I/O cause at the file boundary.
         */
        try {
            return load(Files.readAllBytes(Path.of(path)), fontSize, firstChar, count);
        } catch (IOException failure) {
            throw new UncheckedIOException("Unable to read font: " + path, failure);
        }
    }

    /**
     * Snapshots an encoded font with explicit initial size and prewarm range.
     * @param bytes encoded source
     * @param fontSize initial logical em size
     * @param firstChar first prewarm codepoint
     * @param count prewarm count
     * @return immutable encoded font asset
     */
    public static AdaptiveFontData load(byte[] bytes, int fontSize, int firstChar, int count) {
        /*
         * The canonical constructor performs the sole source snapshot and validation.
         */
        return new AdaptiveFontData(bytes, fontSize, firstChar, count);
    }

    /**
     * Creates an independent adaptive font; no OpenGL allocation occurs until drawing.
     * Dispose the font on its owning thread after all borrowed UI references end.
     * @return newly owned font
     */
    public AdaptiveFont asFont() { return new AdaptiveFont(this); }

    /**
     * Creates an independently owned rendering font with an immutable small-text
     * hinting policy. HarfBuzz spacing and the large-text MSDF path are unchanged.
     * No OpenGL allocation occurs until drawing; use and dispose on the owner thread.
     * @param hinting non-null grayscale hinting policy
     * @return newly owned adaptive font
     * @throws NullPointerException if hinting is null
     */
    public AdaptiveFont asFont(FontHinting hinting) { return new AdaptiveFont(this, hinting); }
}
