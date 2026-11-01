package valthorne.graphics.texture;

/**
 * Backend-independent sampling choices. Mipmap filters require a complete mip
 * chain, which Texture creates when the filter changes. Pixel alignment remains
 * the caller's responsibility when using nearest magnification.
 */
public enum TextureFilter {
    /**
     * Nearest source pixels without mipmaps.
     */
    NEAREST(false, true),
    /**
     * Interpolated source pixels without mipmaps.
     */
    LINEAR(false, false),
    /**
     * Nearest pixels from the nearest mip level.
     */
    NEAREST_MIPMAP_NEAREST(true, true),
    /**
     * Nearest pixels interpolated between mip levels.
     */
    NEAREST_MIPMAP_LINEAR(true, true),
    /**
     * Interpolated pixels from the nearest mip level.
     */
    LINEAR_MIPMAP_NEAREST(true, false),
    /**
     * Interpolation between pixels and mip levels.
     */
    LINEAR_MIPMAP_LINEAR(true, false);

    private final boolean mipmaps; // Whether minification requires mip storage.
    private final boolean nearest; // Whether magnification samples nearest pixels.

    /**
     * Records portable sampling properties.
     *
     * @param mipmaps whether mip storage is required
     * @param nearest whether magnification uses nearest samples
     */
    TextureFilter(boolean mipmaps, boolean nearest) {
        this.mipmaps = mipmaps;
        this.nearest = nearest;
    }

    /**
     * Reports whether this filter requires a complete mip chain.
     *
     * @return whether mipmaps are required
     */
    public boolean usesMipmaps() {
        return mipmaps;
    }

    /**
     * Reports whether magnification samples the nearest source pixel.
     *
     * @return whether nearest magnification is selected
     */
    public boolean isPixelPerfect() {
        return nearest;
    }
}
