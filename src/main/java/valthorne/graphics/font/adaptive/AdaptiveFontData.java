package valthorne.graphics.font.adaptive;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * Immutable size-independent encoded font asset. Load on an asset worker, then
 * call asFont() on the graphics owner thread. Size and hinting are runtime font
 * settings. Ranges request automatic background preparation with bounded admission.
 * Scalable TTF, OTF, TTC, OTC and WOFF sources are supported. Collections use the
 * first face and variable fonts use default axes. WOFF2, bitmap-only and legacy
 * Type 1 sources are rejected when constructing the rendering font.
 * @param bytes encoded font snapshot
 * @param firstCharacterIndex first Unicode prewarm codepoint
 * @param characterCount number of consecutive prewarm codepoints
 */
public record AdaptiveFontData(byte[] bytes, int firstCharacterIndex, int characterCount) {
    /**
     * Validates metadata and copies caller-owned bytes without native allocation.
     * @param bytes nonempty encoded font
     * @param firstCharacterIndex first Unicode prewarm codepoint
     * @param characterCount range length, from 1 through 65536
     * @throws IllegalArgumentException if bytes or the range are invalid
     */
    public AdaptiveFontData {
        if (bytes == null || bytes.length == 0) throw new IllegalArgumentException("Font bytes cannot be empty");
        if (firstCharacterIndex < 0 || characterCount < 1 || characterCount > 65536 || (long) firstCharacterIndex + characterCount > 0x110000)
            throw new IllegalArgumentException("Invalid Unicode prewarm range");
        bytes = Arrays.copyOf(bytes, bytes.length);
    }

    /**
     * Snapshots bytes with printable ASCII prewarming.
     * @param bytes nonempty encoded font
     */
    public AdaptiveFontData(byte[] bytes) { this(bytes, 32, 95); }

    @Override
    public byte[] bytes() { return Arrays.copyOf(bytes, bytes.length); }

    /**
     * Reads a size-independent font with printable ASCII prewarming.
     * @param path filesystem source
     * @return immutable encoded asset
     */
    public static AdaptiveFontData load(String path) {
        /*
         * Raster size is chosen during drawing rather than baked into the asset.
         */
        return load(path, 32, 95);
    }

    /**
     * Snapshots bytes with printable ASCII prewarming.
     * @param bytes nonempty encoded font
     * @return immutable encoded asset
     */
    public static AdaptiveFontData load(byte[] bytes) {
        /*
         * The canonical constructor performs the ownership copy.
         */
        return new AdaptiveFontData(bytes);
    }

    /**
     * Reads a font without creating native or graphics resources.
     * @param path filesystem source
     * @param firstChar first Unicode prewarm codepoint
     * @param count number of prewarm codepoints
     * @return immutable encoded asset
     * @throws UncheckedIOException if reading fails
     */
    public static AdaptiveFontData load(String path, int firstChar, int count) {
        /*
         * Preserve the original I/O cause at the asset boundary.
         */
        try {
            return new AdaptiveFontData(Files.readAllBytes(Path.of(path)), firstChar, count);
        } catch (IOException failure) {
            throw new UncheckedIOException("Unable to read font: " + path, failure);
        }
    }

    /**
     * Snapshots bytes with an explicit prewarm range.
     * @param bytes nonempty encoded font
     * @param firstChar first Unicode prewarm codepoint
     * @param count number of prewarm codepoints
     * @return immutable encoded asset
     */
    public static AdaptiveFontData load(byte[] bytes, int firstChar, int count) {
        /*
         * Share validation and copying with direct asset construction.
         */
        return new AdaptiveFontData(bytes, firstChar, count);
    }

    /**
     * Creates an independent font with default size 16 and NORMAL hinting.
     * Change settings at runtime; use and dispose on the constructing thread.
     * No GPU allocation occurs until drawing.
     * @return newly owned font
     */
    public AdaptiveFont asFont() { return new AdaptiveFont(this); }

    /**
     * Creates an independent font with size 16 and mutable initial hinting.
     * @param hinting non-null initial coverage hinting mode
     * @return newly owned font
     */
    public AdaptiveFont asFont(FontHinting hinting) { return new AdaptiveFont(this, hinting); }
}
