package valthorne.graphics.font.adaptive;

import valthorne.graphics.texture.TextureFilter;
import valthorne.graphics.texture.Texture;
import valthorne.graphics.texture.TextureData;

import java.nio.ByteBuffer;

import static org.lwjgl.bgfx.BGFX.*;
import static org.lwjgl.system.MemoryUtil.memFree;
import static org.lwjgl.system.MemoryUtil.memAlloc;

/**
 * One linear R8 coverage or RGB8/RGBA8 MSDF shelf-packed atlas page. An empty
 * texel gutter prevents neighboring glyphs from bleeding through linear filtering.
 * Managed texture updates retain base pixels for backend changes and recovery.
 */
final class FontAtlasPage extends Texture {
    /**
     * Standard R8 coverage page side, keeping a small ASCII face at 64 KiB.
     */
    static final int COVERAGE_SIDE = 256;
    /**
     * Standard RGB8 distance-field page side, allocating 768 KiB per page.
     */
    static final int DISTANCE_SIDE = 512;
    /**
     * Maximum page side supported for unusually large glyph outlines.
     */
    static final int MAX_SIDE = 1024;

    private final int pixelBytes; // Actual compact storage width, with RGBA fallback when RGB8 is unsupported.
    private final boolean distanceField; // Whether this page contains RGB distance fields instead of R8 coverage.
    private int cursorX = 1; // Next free shelf column including gutter.
    private int cursorY = 1; // Current shelf bottom including gutter.
    private int rowHeight; // Tallest image in the current shelf.
    private int imageX; // Most recently reserved image column.
    private int imageY; // Most recently reserved image row.

    /**
     * Allocates compact, zero-filled linear storage with retained recovery pixels.
     * @param distanceField whether to use RGB8 distance fields instead of R8 coverage
     * @param side power-of-two side length sufficient for the requested glyph
     */
    FontAtlasPage(boolean distanceField, int side) {
        super(new TextureData(null, side, side), distanceField ? distanceFormat() : BGFX_TEXTURE_FORMAT_R8);
        this.distanceField = distanceField;
        pixelBytes = distanceField ? (distanceFormat() == BGFX_TEXTURE_FORMAT_RGB8 ? 3 : 4) : 1;
        try {
            setFilter(TextureFilter.LINEAR);
        } catch (RuntimeException | Error failure) {
            dispose();
            throw failure;
        }
    }

    @Override
    public float getDistanceFieldRange() { return distanceField ? AdaptiveNativeFace.MSDF_RANGE : -1; }

    /**
     * Returns exact allocated texel storage, excluding driver bookkeeping.
     * @return bytes in this page's actual R8, RGB8 or RGBA8 image
     */
    long storageBytes() { return (long) getWidth() * getHeight() * pixelBytes; }

    /**
     * Reserves a shelf rectangle without modifying the page on failure.
     * @param width image width
     * @param height image height
     * @return whether the rectangle fits
     */
    boolean reserve(int width, int height) {
        int x = cursorX;
        int y = cursorY;
        int row = rowHeight;
        int side = getWidth();
        if (width > side - 2 || height > side - 2) return false;
        if (x + width + 1 > side) {
            x = 1;
            y += row + 2;
            row = 0;
        }
        if (y + height + 1 > side) return false;
        imageX = x;
        imageY = y;
        cursorX = x + width + 2;
        cursorY = y;
        rowHeight = Math.max(row, height);
        return true;
    }

    /**
     * Returns the last successful reservation's horizontal coordinate.
     * @return atlas texel column
     */
    int imageX() { return imageX; }

    /**
     * Returns the last successful reservation's vertical coordinate.
     * @return atlas texel row
     */
    int imageY() { return imageY; }

    /**
     * Uploads one raster to the last reserved rectangle.
     * @param raster borrowed pixels, released by caller after this operation
     */
    void upload(FontRaster raster) {
        if (pixelBytes != 4) {
            updatePixels(imageX, imageY, raster.width(), raster.height(), raster.pixels());
            return;
        }
        ByteBuffer expanded = memAlloc(raster.width() * raster.height() * 4);
        try {
            ByteBuffer source = raster.pixels();
            for (int i = source.position(); i < source.limit(); i += 3)
                expanded.put(source.get(i)).put(source.get(i + 1)).put(source.get(i + 2)).put((byte) 255);
            expanded.flip();
            updatePixels(imageX, imageY, raster.width(), raster.height(), expanded);
        } finally {
            memFree(expanded);
        }
    }

    /**
     * Selects compact RGB8 where supported, retaining full RGB precision otherwise.
     * @return RGB8 or universal RGBA8 sampling format
     */
    private static int distanceFormat() {
        /*
         * Vulkan devices may omit three-channel sampled images. The fallback
         * adds only an unused alpha channel; distance samples retain their RGB.
         */
        return bgfx_is_texture_valid(0, false, 1, BGFX_TEXTURE_FORMAT_RGB8, BGFX_TEXTURE_NONE) ? BGFX_TEXTURE_FORMAT_RGB8 : BGFX_TEXTURE_FORMAT_RGBA8;
    }
}
