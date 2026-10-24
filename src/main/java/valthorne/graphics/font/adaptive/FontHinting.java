package valthorne.graphics.font.adaptive;

import static org.lwjgl.util.freetype.FreeType.*;

/**
 * Selects FreeType outline hinting for an adaptive font's small-text coverage
 * images. All modes retain grayscale antialiasing and natural HarfBuzz advances.
 * Large, rotated and otherwise transformed text still uses unhinted MSDF images.
 * Set the mode when constructing the rendering font; it remains immutable so
 * cached glyphs cannot mix hinting policies or require runtime invalidation.
 * Font format, embedded instructions and FreeType's driver determine how much
 * the modes differ visually. LCD subpixel and monochrome rendering are separate
 * pixel formats and are not provided by this grayscale renderer.
 */
public enum FontHinting {
    /**
     * Disables outline grid fitting while retaining grayscale antialiasing.
     */
    NONE(FT_LOAD_NO_HINTING | FT_LOAD_NO_AUTOHINT),
    /**
     * Default grayscale hinting, using the native hinter when available and
     * FreeType's automatic fallback when the font driver requires it.
     */
    NORMAL(FT_LOAD_TARGET_NORMAL),
    /**
     * Requests lighter hinting favoring vertical alignment and natural shapes;
     * the font driver's implementation determines the exact grid-fitting behavior.
     */
    LIGHT(FT_LOAD_TARGET_LIGHT),
    /**
     * Forces FreeType's auto-hinter with the normal grayscale hinting target,
     * bypassing the font driver's native hinting instructions.
     */
    AUTO(FT_LOAD_FORCE_AUTOHINT | FT_LOAD_TARGET_NORMAL);

    private final int loadFlags; // Precomputed FreeType hinting flags, independent of glyph and size.

    /**
     * Stores native raster flags without initializing native font resources.
     * @param loadFlags hinting policy combined with NO_BITMAP when loading glyphs
     */
    FontHinting(int loadFlags) { this.loadFlags = loadFlags; }

    /**
     * Supplies trusted native hinting flags during a glyph cache miss.
     * @return FreeType load flags excluding the renderer's NO_BITMAP flag
     */
    final int loadFlags() { return loadFlags; }
}
