package valthorne.graphics.font.slug;

/**
 * Metadata needed to draw one Slug glyph.
 *
 * <p>The glyph stores layout metrics in normalized em-space and packed locations into the Slug
 * band texture. The curve data itself is stored globally by {@link SlugFont} so many glyphs can
 * share one GPU upload.</p>
 *
 * @author Albert Beaupre
 * @since July 7th, 2026
 */
public final class SlugGlyph {

    final int codepoint; // Unicode codepoint represented by this glyph.
    final float advance; // Horizontal advance in em units.
    final float leftSideBearing; // Left side bearing in em units.
    final float x0; // Minimum glyph bound x in em-space.
    final float y0; // Minimum glyph bound y in em-space.
    final float x1; // Maximum glyph bound x in em-space.
    final float y1; // Maximum glyph bound y in em-space.
    final int glyphPack; // Packed band texture location used by the vertex shader.
    final int glyphInfoPack; // Packed band max values and fill flags used by the vertex shader.
    final float bandScaleX; // Multiplier converting em x to vertical-band index.
    final float bandScaleY; // Multiplier converting em y to horizontal-band index.
    final float bandOffsetX; // Offset converting em x to vertical-band index.
    final float bandOffsetY; // Offset converting em y to horizontal-band index.
    final boolean drawable; // True when this glyph owns outline curves.

    SlugGlyph(int codepoint, float advance, float leftSideBearing, float x0, float y0, float x1, float y1,
              int glyphPack, int glyphInfoPack, float bandScaleX, float bandScaleY, float bandOffsetX, float bandOffsetY,
              boolean drawable) {
        this.codepoint = codepoint;
        this.advance = advance;
        this.leftSideBearing = leftSideBearing;
        this.x0 = x0;
        this.y0 = y0;
        this.x1 = x1;
        this.y1 = y1;
        this.glyphPack = glyphPack;
        this.glyphInfoPack = glyphInfoPack;
        this.bandScaleX = bandScaleX;
        this.bandScaleY = bandScaleY;
        this.bandOffsetX = bandOffsetX;
        this.bandOffsetY = bandOffsetY;
        this.drawable = drawable;
    }

    /**
     * Returns the Unicode codepoint represented by this glyph.
     *
     * @return glyph codepoint
     */
    public int codepoint() {
        return codepoint;
    }

    /**
     * Returns the horizontal advance in em units.
     *
     * @return advance in em units
     */
    public float advance() {
        return advance;
    }

    /**
     * Returns whether this glyph has visible outline data.
     *
     * @return true if drawable
     */
    public boolean isDrawable() {
        return drawable;
    }

    /**
     * Returns this glyph's width in em units.
     *
     * @return glyph bounds width
     */
    public float width() {
        return x1 - x0;
    }

    /**
     * Returns this glyph's height in em units.
     *
     * @return glyph bounds height
     */
    public float height() {
        return y1 - y0;
    }
}
