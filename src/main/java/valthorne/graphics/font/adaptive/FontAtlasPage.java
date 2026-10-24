package valthorne.graphics.font.adaptive;

import org.lwjgl.system.MemoryStack;
import valthorne.graphics.texture.Texture;
import valthorne.graphics.texture.TextureData;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.*;
import static org.lwjgl.opengl.GL15.glBindBuffer;
import static org.lwjgl.opengl.GL21.*;
import static org.lwjgl.opengl.GL30.GL_R8;
import static org.lwjgl.opengl.GL33.*;
import static org.lwjgl.system.MemoryUtil.memCalloc;
import static org.lwjgl.system.MemoryUtil.memFree;

/**
 * One linear R8 coverage or RGB8 MSDF shelf-packed atlas page. An empty texel gutter keeps
 * linear filtering from sampling neighboring glyphs. Uploads restore texture,
 * pixel-buffer and unpack state; no CPU copy of a page is retained.
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

    private final boolean distanceField; // Whether this page contains RGB distance fields instead of R8 coverage.
    private int cursorX = 1; // Next free shelf column including gutter.
    private int cursorY = 1; // Current shelf bottom including gutter.
    private int rowHeight; // Tallest image in the current shelf.
    private int imageX; // Most recently reserved image column.
    private int imageY; // Most recently reserved image row.

    /**
     * Allocates compact, zero-filled linear storage without retaining CPU pixels.
     * @param distanceField whether to use RGB8 distance fields instead of R8 coverage
     * @param side power-of-two side length sufficient for the requested glyph
     */
    FontAtlasPage(boolean distanceField, int side) {
        super(glGenTextures(), new TextureData(null, side, side));
        this.distanceField = distanceField;
        try {
            upload(null, 0, 0, side, side, true);
        } catch (RuntimeException | Error failure) {
            dispose();
            throw failure;
        }
    }

    @Override
    public float getDistanceFieldRange() { return distanceField ? AdaptiveNativeFace.MSDF_RANGE : 0; }

    /**
     * Returns exact allocated texel storage, excluding driver bookkeeping.
     * @return bytes in this page's R8 or RGB8 image
     */
    long storageBytes() { return (long) getWidth() * getHeight() * (distanceField ? 3 : 1); }

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
        upload(raster.pixels(), imageX, imageY, raster.width(), raster.height(), false);
    }

    /**
     * Performs isolated texture allocation or subimage upload. Resetting every
     * relevant unpack value prevents application PBOs or row skips corrupting data.
     * @param pixels borrowed RGB or coverage bytes, null only for allocation
     * @param x destination column
     * @param y destination row
     * @param width rectangle width
     * @param height rectangle height
     * @param allocate whether to initialize the whole page
     */
    private void upload(ByteBuffer pixels, int x, int y, int width, int height, boolean allocate) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer state = stack.mallocInt(8);
            state.put(0, glGetInteger(GL_TEXTURE_BINDING_2D));
            state.put(1, glGetInteger(GL_PIXEL_UNPACK_BUFFER_BINDING));
            state.put(2, glGetInteger(GL_UNPACK_ALIGNMENT));
            state.put(3, glGetInteger(GL_UNPACK_ROW_LENGTH));
            state.put(4, glGetInteger(GL_UNPACK_SKIP_PIXELS));
            state.put(5, glGetInteger(GL_UNPACK_SKIP_ROWS));
            state.put(6, glGetInteger(GL_UNPACK_SWAP_BYTES));
            state.put(7, glGetInteger(GL_UNPACK_LSB_FIRST));
            glBindBuffer(GL_PIXEL_UNPACK_BUFFER, 0);
            glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
            glPixelStorei(GL_UNPACK_ROW_LENGTH, 0);
            glPixelStorei(GL_UNPACK_SKIP_PIXELS, 0);
            glPixelStorei(GL_UNPACK_SKIP_ROWS, 0);
            glPixelStorei(GL_UNPACK_SWAP_BYTES, 0);
            glPixelStorei(GL_UNPACK_LSB_FIRST, 0);
            glBindTexture(GL_TEXTURE_2D, textureID);
            try {
                int format = distanceField ? GL_RGB : GL_RED;
                if (allocate) {
                    // A cleared gutter is required because uninitialized texture storage is undefined.
                    ByteBuffer zeroes = memCalloc(width * height * (distanceField ? 3 : 1));
                    try {
                        glTexImage2D(GL_TEXTURE_2D, 0, distanceField ? GL_RGB8 : GL_R8, width, height, 0, format, GL_UNSIGNED_BYTE, zeroes);
                    } finally {
                        memFree(zeroes);
                    }
                    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
                    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
                    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
                    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
                    if (!distanceField) {
                        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_SWIZZLE_R, GL_ONE);
                        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_SWIZZLE_G, GL_ONE);
                        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_SWIZZLE_B, GL_ONE);
                        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_SWIZZLE_A, GL_RED);
                    }
                } else glTexSubImage2D(GL_TEXTURE_2D, 0, x, y, width, height, format, GL_UNSIGNED_BYTE, pixels);
            } finally {
                glBindTexture(GL_TEXTURE_2D, state.get(0));
                glBindBuffer(GL_PIXEL_UNPACK_BUFFER, state.get(1));
                glPixelStorei(GL_UNPACK_ALIGNMENT, state.get(2));
                glPixelStorei(GL_UNPACK_ROW_LENGTH, state.get(3));
                glPixelStorei(GL_UNPACK_SKIP_PIXELS, state.get(4));
                glPixelStorei(GL_UNPACK_SKIP_ROWS, state.get(5));
                glPixelStorei(GL_UNPACK_SWAP_BYTES, state.get(6));
                glPixelStorei(GL_UNPACK_LSB_FIRST, state.get(7));
            }
        }
    }
}
