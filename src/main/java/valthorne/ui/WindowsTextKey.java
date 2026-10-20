package valthorne.ui;

import valthorne.graphics.font.directwrite.DirectWriteFace;

/**
 * Exact raster cache identity. One mutable lookup key is reused for hot draws;
 * inserted keys are independent immutable snapshots so steady text allocates nothing.
 */
final class WindowsTextKey {
    private DirectWriteFace font; // Native collection identity avoids retaining disposed Slug font source/outline buffers in raster entries.
    private String text; // Exact shaped text, retained only for bounded cache entries.
    private float size; // Layout em size in DIPs.
    private float scaleX; // Horizontal physical pixels per DIP.
    private float scaleY; // Vertical physical pixels per DIP.
    private float phaseX; // Exact subpixel physical origin fraction horizontally.
    private float phaseY; // Exact subpixel physical origin fraction vertically.
    private int color; // Native foreground in packed RRGGBBAA order.
    private int revision; // Native rendering-settings generation.
    private int background; // Explicit final opaque RGB background, or -1 for real transparent grayscale.
    private int left; // Cropped physical X offset in the full padded raster.
    private int top; // Cropped physical Y offset in the full padded raster.
    private int width; // Visible physical raster width.
    private int height; // Visible physical raster height.
    private int hash; // Precomputed hash reused for hot cache lookups.

    /**
     * Creates a reusable lookup key; it must never itself be inserted into a map.
     */
    WindowsTextKey() {}

    /**
     * Copies one lookup into a stable cache entry key.
     *
     * @param source completed reusable lookup key
     */
    WindowsTextKey(WindowsTextKey source) {
        set(source.font, source.text, source.size, source.scaleX, source.scaleY, source.phaseX, source.phaseY, source.color, source.revision, source.background, source.left, source.top, source.width, source.height);
    }

    /**
     * Updates the reusable lookup without allocating transient key objects.
     *
     * @param font native collection identity used for this raster
     * @param text exact shaped contents
     * @param size logical em size
     * @param scaleX horizontal DPI scale
     * @param scaleY vertical DPI scale
     * @param phaseX horizontal physical fraction
     * @param phaseY vertical physical fraction
     * @param color native RRGGBBAA foreground
     * @param revision rendering-settings generation
     * @param background explicit final opaque RGB background, or -1 for transparency
     * @param left physical crop X
     * @param top physical crop Y
     * @param width physical crop width
     * @param height physical crop height
     */
    void set(DirectWriteFace font, String text, float size, float scaleX, float scaleY, float phaseX, float phaseY, int color, int revision, int background, int left, int top, int width, int height) {
        this.font = font;
        this.text = text;
        this.size = size;
        this.scaleX = scaleX;
        this.scaleY = scaleY;
        this.phaseX = phaseX;
        this.phaseY = phaseY;
        this.color = color;
        this.revision = revision;
        this.background = background;
        this.left = left;
        this.top = top;
        this.width = width;
        this.height = height;
        int value = System.identityHashCode(font);
        value = value * 31 + text.hashCode();
        value = value * 31 + Float.floatToIntBits(size);
        value = value * 31 + Float.floatToIntBits(scaleX);
        value = value * 31 + Float.floatToIntBits(scaleY);
        value = value * 31 + Float.floatToIntBits(phaseX);
        value = value * 31 + Float.floatToIntBits(phaseY);
        value = (((value * 31 + color) * 31 + revision) * 31 + background) * 31 + left;
        hash = ((value * 31 + top) * 31 + width) * 31 + height;
    }

    @Override
    public int hashCode() { return hash; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof WindowsTextKey key)) return false;
        return font == key.font && size == key.size && scaleX == key.scaleX && scaleY == key.scaleY
                && phaseX == key.phaseX && phaseY == key.phaseY && color == key.color && revision == key.revision && background == key.background
                && left == key.left && top == key.top && width == key.width && height == key.height && text.equals(key.text);
    }
}
