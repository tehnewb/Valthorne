package valthorne.graphics.font;

import valthorne.graphics.Color;
import valthorne.graphics.texture.Texture;
import valthorne.graphics.texture.TextureData;
import valthorne.graphics.texture.TextureFilter;
import valthorne.ui.Dimensional;
import org.lwjgl.stb.STBTTPackedchar;

import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;

/**
 * Represents a font and its associated rendering capabilities,
 * utilizing OpenGL for managing texture data and rendering text
 * onto the GPU.
 * <p>
 * The Font class provides methods for rendering text via a
 * pre-generated glyph atlas and supports efficient text bitmap
 * updates and OpenGL texture uploads.
 *
 * @author Albert Beaupre
 * @since December 6th, 2025
 */
public class Font implements Dimensional {

    /**
     * A single transparent pixel used to form a minimal 1×1 fallback texture
     * when text is empty.
     */
    private static final ByteBuffer ONE_PIXEL = ByteBuffer.allocateDirect(1).put(0, (byte) 0);
    private static final int INITIAL_BUFFER_CAPACITY = 256;

    private final FontData data;                      // Font metrics and glyph atlas data
    private final Texture texture;                    // OpenGL texture for rendering
    private final float[] bounds = new float[4];      // Text bounding box coordinates [minX,minY,width,height]
    private ByteBuffer pixelBuffer = ByteBuffer.allocateDirect(INITIAL_BUFFER_CAPACITY); // CPU-side pixel buffer for text bitmap
    private int bufferCapacity = INITIAL_BUFFER_CAPACITY;  // Current size of pixel buffer in bytes
    private String text = "";                         // Current text content
    private float width;                              // Rendered text width in pixels
    private float height;                             // Rendered text height in pixels

    /**
     * Constructs a Font instance using the provided font data.
     * <p>
     * Populates a texture with the glyph atlas from the font data and prepares it
     * for rendering text. Initializes GPU resources and configures texture
     * parameters.
     *
     * @param data the {@link FontData} containing font metrics, glyph atlas,
     *             and layout information used to initialize this Font.
     */
    public Font(FontData data) {
        this.data = data;

        int texID = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, texID);
        glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_ALPHA, data.atlasSize(), data.atlasSize(), 0, GL_ALPHA, GL_UNSIGNED_BYTE, data.bitmap());

        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

        this.texture = new Texture(createEmptyTextureData());
        this.texture.setFlipY(true);
        this.texture.setFilter(TextureFilter.NEAREST);
        this.setText(text);
    }

    /**
     * Creates a 1×1 GL_ALPHA texture used as the initial texture before any real
     * text is rendered.
     *
     * @return newly created {@link TextureData}
     */
    private TextureData createEmptyTextureData() {
        int textureID = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, textureID);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

        ONE_PIXEL.position(0);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_ALPHA, 1, 1, 0, GL_ALPHA, GL_UNSIGNED_BYTE, ONE_PIXEL);

        return new TextureData(ONE_PIXEL, (short) 1, (short) 1);
    }

    /**
     * Updates the rendered text. This triggers complete regeneration of the bitmap:
     * <ul>
     *     <li>Measure bounds</li>
     *     <li>Resize buffer if needed</li>
     *     <li>Clear pixel buffer</li>
     *     <li>Rasterize glyphs and composite into buffer</li>
     *     <li>Upload result to OpenGL</li>
     * </ul>
     *
     * @param text the new string content
     */
    public void setText(String text) {
        if (text == null) text = "";
        if (this.text.equals(text)) return;

        this.text = text;

        // If empty, upload a 1x1 texture
        if (text.isEmpty()) {
            upload1x1Texture();
            width = height = 0;
            return;
        }

        // Compute bounds in the same coordinate system we use to rasterize
        computeBounds(text);
        float minX = bounds[0], minY = bounds[1];
        float w = bounds[2], h = bounds[3];

        int texW = Math.max(1, (int) Math.ceil(w));
        int texH = Math.max(1, (int) Math.ceil(h));

        this.width = w;
        this.height = h;

        int requiredPixels = texW * texH;
        ensureBufferCapacity(requiredPixels);
        clearBuffer(requiredPixels);

        renderTextToBuffer(text, minX, minY, texW, texH);
        uploadTexture(texW, texH);
    }

    /**
     * Retrieves the current text content of this Font instance.
     *
     * @return the current text content as a String
     */
    public String getText() {
        return text;
    }

    /**
     * Ensures the pixel buffer is large enough to store the rendered bitmap.
     * Grows exponentially up to 4MB increments.
     *
     * @param required required number of bytes
     */
    private void ensureBufferCapacity(int required) {
        if (required <= bufferCapacity) return;

        // Grow ONCE to the required size
        ByteBuffer newBuffer = ByteBuffer.allocateDirect(required);
        bufferCapacity = required;

        // Replace old buffer
        pixelBuffer = newBuffer;
    }


    /**
     * Fills the pixel buffer with fully transparent bytes.
     *
     * @param size number of bytes to clear
     */
    private void clearBuffer(int size) {
        pixelBuffer.position(0);
        pixelBuffer.limit(size);
        while (pixelBuffer.hasRemaining()) pixelBuffer.put((byte) 0);
        pixelBuffer.position(0);
    }

    /**
     * Rasterizes the text into the CPU-side pixel buffer.
     *
     * <p>For each glyph:</p>
     * <ol>
     *     <li>Fetch glyph alpha mask from font atlas</li>
     *     <li>Compute glyph placement using STB metrics (xoff/yoff/yoff2)</li>
     *     <li>Copy non-zero alpha pixels into buffer</li>
     * </ol>
     *
     * @param text content to rasterize
     * @param minX left text bound offset
     * @param minY top text bound offset
     * @param texW output texture width
     * @param texH output texture height
     */
    private void renderTextToBuffer(String text, float minX, float minY, int texW, int texH) {
        byte[] atlas = data.atlasPixels();
        int atlasW = data.atlasSize();

        // Offsets that move the min bounds to (0,0)
        float offsetX = -minX;
        float offsetY = -minY;

        float penX = 0f;
        float penY = 0f; // baseline for first line in our local space

        int first = data.firstChar();
        int count = data.numChars();

        for (int i = 0, n = text.length(); i < n; i++) {
            char c = text.charAt(i);

            if (c == '\n') {
                penX = 0;
                penY += data.lineHeight();
                continue;
            }

            int idx = c - first;
            if ((idx | (count - 1 - idx)) < 0) continue;

            STBTTPackedchar ch = data.charData()[idx];
            int gw = ch.x1() - ch.x0();
            int gh = ch.y1() - ch.y0();

            // Skip empty glyphs
            if (gw <= 0 || gh <= 0) {
                penX += ch.xadvance();
                continue;
            }

            // Compute glyph placement in output buffer (same formulas as stbtt_GetPackedQuad)
            float gx0 = penX + ch.xoff() + offsetX;
            float gy0 = penY + ch.yoff() + offsetY;

            int dx0 = (int) Math.floor(gx0 + 0.5f);
            int dy0 = (int) Math.floor(gy0 + 0.5f);

            // Off-screen skip optimization
            if (dx0 >= texW || dy0 >= texH || dx0 + gw <= 0 || dy0 + gh <= 0) {
                penX += ch.xadvance();
                continue;
            }

            // Compute atlas and buffer subregion boundaries
            int sx0 = ch.x0();
            int sy0 = ch.y0();
            int x0 = Math.max(0, -dx0);
            int y0 = Math.max(0, -dy0);
            int x1 = Math.min(gw, texW - dx0);
            int y1 = Math.min(gh, texH - dy0);

            // Copy alpha values
            for (int y = y0; y < y1; y++) {
                int srcBase = (sy0 + y) * atlasW + sx0;
                int dstBase = (dy0 + y) * texW + dx0;
                for (int x = x0; x < x1; x++) {
                    byte alpha = atlas[srcBase + x];
                    if (alpha != 0) {
                        pixelBuffer.put(dstBase + x, alpha);
                    }
                }
            }

            penX += ch.xadvance();
        }
    }

    /**
     * Uploads the rendered bitmap to the GPU. Uses {@code glTexSubImage2D} when possible
     * to avoid reallocating GPU memory.
     *
     * @param w texture width
     * @param h texture height
     */
    private void uploadTexture(int w, int h) {
        glBindTexture(GL_TEXTURE_2D, texture.getID());
        glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
        pixelBuffer.position(0).limit(w * h);

        TextureData data = texture.getData();
        if (data.width() == w && data.height() == h) {
            glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, w, h, GL_ALPHA, GL_UNSIGNED_BYTE, pixelBuffer);
        } else {
            glTexImage2D(GL_TEXTURE_2D, 0, GL_ALPHA, w, h, 0, GL_ALPHA, GL_UNSIGNED_BYTE, pixelBuffer);
            texture.setSize(w, h);
        }
    }

    /**
     * Uploads a single transparent pixel to reset the texture when the text is empty.
     */
    private void upload1x1Texture() {
        glBindTexture(GL_TEXTURE_2D, texture.getID());
        ONE_PIXEL.position(0);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_ALPHA, 1, 1, 0, GL_ALPHA, GL_UNSIGNED_BYTE, ONE_PIXEL);
        texture.setSize(1, 1);
    }

    /**
     * Draws the rendered text using the underlying {@link Texture}.
     */
    public void draw() {
        texture.draw();
    }

    /**
     * Sets the screen position of the rendered text.
     */
    public void setPosition(float x, float y) {
        texture.setPosition(x, y);
    }

    /**
     * Retrieves the current screen-space X position of the rendered text.
     *
     * @return the X coordinate of the rendered text in screen-space.
     */
    public float getX() {
        return texture.getX();
    }

    /**
     * Retrieves the current screen-space Y position of the rendered text.
     *
     * @return the Y coordinate of the rendered text in screen-space.
     */
    public float getY() {
        return texture.getY();
    }

    /**
     * Applies a color tint to the rendered text.
     */
    public void setColor(Color color) {
        texture.setColor(color);
    }

    /**
     * @return rendered text width in pixels
     */
    public float getWidth() {
        return width;
    }

    /**
     * @return rendered text height in pixels
     */
    public float getHeight() {
        return height;
    }

    /**
     * Frees GPU resources and clears the pixel buffer.
     * After calling this, the instance must no longer be used.
     */
    public void dispose() {
        texture.dispose();
        pixelBuffer = null;
        bufferCapacity = 0;
    }

    /**
     * Computes tight bounds of the given text (including multiple lines)
     * in the same coordinate system used by {@link #renderTextToBuffer}.
     */
    private void computeBounds(String text) {
        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;

        float penX = 0f;
        float penY = 0f; // baseline for first line
        float lineH = data.lineHeight();

        int first = data.firstChar();
        int count = data.numChars();

        for (int i = 0, n = text.length(); i < n; i++) {
            char c = text.charAt(i);

            if (c == '\n') {
                penX = 0f;
                penY += lineH;
                continue;
            }

            int idx = c - first;
            if ((idx | (count - 1 - idx)) < 0) continue;

            STBTTPackedchar g = data.charData()[idx];

            float x0 = penX + g.xoff();
            float y0 = penY + g.yoff();
            float x1 = penX + g.xoff2();
            float y1 = penY + g.yoff2();

            if (x0 <= minX) minX = x0;
            if (y0 <= minY) minY = y0;
            if (x1 >= maxX) maxX = x1;
            if (y1 >= maxY) maxY = y1;

            penX += g.xadvance();
        }

        if (maxX < minX || maxY < minY) {
            bounds[0] = 0;
            bounds[1] = 0;
            bounds[2] = 0;
            bounds[3] = 0;
            return;
        }

        bounds[0] = minX;
        bounds[1] = minY;
        bounds[2] = maxX - minX;
        bounds[3] = maxY - minY;
    }

    /**
     * Computes the width of the given text using the same rules as computeBounds.
     *
     * @param text the text to measure
     * @return width in pixels
     */
    public float computeWidth(String text) {
        if (text == null || text.isEmpty()) return 0f;

        float minX = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;

        float penX = 0f;

        int first = data.firstChar();
        int count = data.numChars();

        for (int i = 0, n = text.length(); i < n; i++) {
            char c = text.charAt(i);

            if (c == '\n') {
                penX = 0f;
                continue;
            }

            int idx = c - first;
            if ((idx | (count - 1 - idx)) < 0) continue;

            STBTTPackedchar g = data.charData()[idx];

            float x0 = penX + g.xoff();
            float x1 = penX + g.xoff2();

            if (x0 < minX) minX = x0;
            if (x1 > maxX) maxX = x1;

            penX += g.xadvance();
        }

        if (maxX < minX) return 0f;
        return maxX - minX;
    }

    /**
     * Computes the height of the given text using the same rules as computeBounds.
     *
     * @param text the text to measure
     * @return height in pixels
     */
    public float computeHeight(String text) {
        if (text == null || text.isEmpty()) return 0f;

        float minY = Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;

        float penY = 0f;

        int first = data.firstChar();
        int count = data.numChars();

        for (int i = 0, n = text.length(); i < n; i++) {
            char c = text.charAt(i);

            if (c == '\n') {
                penY += data.lineHeight();
                continue;
            }

            int idx = c - first;
            if ((idx | (count - 1 - idx)) < 0) continue;

            STBTTPackedchar g = data.charData()[idx];

            float y0 = penY + g.yoff();
            float y1 = penY + g.yoff2();

            if (y0 < minY) minY = y0;
            if (y1 > maxY) maxY = y1;
        }

        if (maxY < minY) return 0f;
        return maxY - minY;
    }

    /**
     * Retrieves the font data associated with this Font instance.
     *
     * @return the FontData containing font metrics, glyph atlas, and layout information.
     */
    public FontData getData() {
        return data;
    }
}