package valthorne.graphics.texture;

import org.lwjgl.BufferUtils;
import valthorne.graphics.Color;
import valthorne.math.Vector2f;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL30.glGenerateMipmap;

/**
 * A 2D textured quad renderer built on the legacy OpenGL fixed-function pipeline.
 *
 * <p>This class wraps an OpenGL texture object and provides sprite-like rendering features:</p>
 * <ul>
 *     <li><b>Transform</b>: position ({@link #x},{@link #y}), size ({@link #width},{@link #height}),
 *         scale ({@link #scaleX},{@link #scaleY}), rotation ({@link #rotation}) around an origin ({@link #origin}).</li>
 *     <li><b>Region rendering</b>: draw a sub-rectangle of the underlying image (sprite sheets) using UVs.</li>
 *     <li><b>Low-allocation rendering</b>: pre-allocated {@link #vertexBuffer} and {@link #uvBuffer}, updated only when needed.</li>
 *     <li><b>Mirroring</b>: horizontal/vertical UV flips via {@link #setFlipX(boolean)} and {@link #setFlipY(boolean)}.</li>
 * </ul>
 *
 * <h2>Rendering model</h2>
 * <p>The quad is drawn using:</p>
 * <ul>
 *     <li>{@code glBindTexture(GL_TEXTURE_2D, textureID)}</li>
 *     <li>{@code glVertexPointer(..., vertexBuffer)}</li>
 *     <li>{@code glTexCoordPointer(..., uvBuffer)}</li>
 *     <li>{@code glDrawArrays(GL_QUADS, 0, 4)}</li>
 * </ul>
 *
 * <p>This means:</p>
 * <ul>
 *     <li>Your projection/view transforms must already be configured before calling {@link #draw()}.</li>
 *     <li>Client states (vertex array / texcoord array / texture 2D) must be enabled by the caller (or elsewhere).</li>
 * </ul>
 *
 * <h2>Vertex generation</h2>
 * <p>Vertices are stored in two stages:</p>
 * <ol>
 *     <li>{@link #localVertices}: local-space quad corners relative to the origin (no rotation or translation).</li>
 *     <li>{@link #vertexBuffer}: final world-space vertices after applying rotation and translation.</li>
 * </ol>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * Texture tex = new Texture("assets/sprites/player.png");
 * tex.setRegion(0, 0, 32, 32);          // draw first frame from a sprite sheet
 * tex.setPosition(100, 200);
 * tex.setSize(64, 64);
 * tex.setRotationOriginCenter();
 * tex.setRotation(15);
 * tex.setColor(new Color(1f, 1f, 1f, 1f));
 *
 * // In your render loop:
 * tex.draw();
 *
 * tex.dispose();
 * }</pre>
 *
 * @author Albert Beaupre
 * @see TextureData
 * @since November 26th, 2025
 */
public class Texture {

    private TextureData data;                                     // Decoded image data used to upload pixels and resolve region UVs.
    private FloatBuffer vertexBuffer = BufferUtils.createFloatBuffer(8); // World-space vertex positions (4 corners, x/y pairs).
    private FloatBuffer uvBuffer = BufferUtils.createFloatBuffer(8);     // Normalized UV coordinates (4 corners, u/v pairs).
    private float[] localVertices = new float[8];                 // Local-space quad corners relative to the rotation origin.
    private Vector2f origin = new Vector2f(0f, 0f);                // OpenGL texture object id returned by glGenTextures().
    private TextureFilter filter = TextureFilter.NEAREST;               // Current sampling filter used for MIN/MAG scaling.          // Rotation/pivot point relative to the quad’s top-left.
    private final int textureID;
    private float width;                                                // Base quad width (before scaleX is applied).
    private float height;                                               // Base quad height (before scaleY is applied).
    private float x;                                                    // World-space X position for the quad (top-left reference).
    private float y;                                                    // World-space Y position for the quad (top-left reference).
    private float leftRegion;                                           // Normalized U coordinate for the left edge of the region.
    private float topRegion;                                            // Normalized V coordinate for the top edge of the region.
    private float rightRegion = 1f;                                     // Normalized U coordinate for the right edge of the region.
    private float bottomRegion = 1f;                                    // Normalized V coordinate for the bottom edge of the region.
    private float rotation;                                             // Rotation angle in degrees (clockwise by convention here).
    private float sinRot, cosRot = 1f;                                  // Cached sine/cosine for the current rotation angle.
    private boolean flippedX;                                           // Whether UVs are currently flipped horizontally (left/right swapped).
    private boolean flippedY;                                           // Whether UVs are currently flipped vertically (top/bottom swapped).
    private float scaleX = 1f;                                          // Horizontal scale factor applied during local vertex generation.
    private float scaleY = 1f;                                          // Vertical scale factor applied during local vertex generation.
    private Color color = Color.WHITE;                                  // Tint color multiplied in fixed-function pipeline via glColor4f().


    /**
     * Loads a texture from an image file path using {@link TextureData}.
     *
     * <p>This constructor loads the image into a {@link TextureData} and then uploads it to the GPU.</p>
     *
     * @param path path to the image file
     */
    public Texture(String path) {
        this(TextureData.load(path));
    }

    /**
     * Loads a texture from an encoded image buffer (PNG/JPEG/etc.) using {@link TextureData}.
     *
     * <p>This constructor decodes the image data into a {@link TextureData} and uploads it to the GPU.</p>
     *
     * @param data PNG/JPEG/etc. byte array
     */
    public Texture(byte[] data) {
        this(TextureData.load(data));
    }

    /**
     * Creates a texture from already-loaded {@link TextureData}.
     *
     * <p>Upload steps performed:</p>
     * <ol>
     *     <li>Generate and bind the OpenGL texture object</li>
     *     <li>Apply filter parameters (MIN/MAG)</li>
     *     <li>Apply clamping to edges to reduce bleeding artifacts</li>
     *     <li>Upload RGBA8 pixel data with {@code glTexImage2D}</li>
     *     <li>Initialize quad state: default size = image size, full region, buffers updated</li>
     * </ol>
     *
     * <p>Side effects:</p>
     * <ul>
     *     <li>Leaves this texture bound to {@code GL_TEXTURE_2D} after construction.</li>
     * </ul>
     *
     * @param data the decoded image container
     * @throws NullPointerException if {@code data} is null
     */
    public Texture(TextureData data) {
        if (data == null) throw new NullPointerException("TextureData cannot be null");

        // Generate an OpenGL texture object.
        this.textureID = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, textureID);

        // Configure texture filtering for minification and magnification.
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, filter.minFilter);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, filter.magFilter);

        // Clamp texture edges to prevent bleeding when sampling near borders.
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

        // Upload the pixel buffer to the GPU using 8-bit RGBA format.
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, data.width(), data.height(), 0, GL_RGBA, GL_UNSIGNED_BYTE, data.buffer());

        this.data = data;
        this.width = data.width();
        this.height = data.height();

        updateLocalVertices();
        updateUVBuffer();
        updateVertexBuffer();
    }

    /**
     * Sets the texture filtering mode used when OpenGL samples/scales this texture.
     *
     * <p>This updates both MIN and MAG filters. The texture is bound before updating GL state.</p>
     *
     * @param filter the filtering mode to apply
     * @throws NullPointerException if {@code filter} is null
     */
    public void setFilter(TextureFilter filter) {
        if (filter == null) throw new NullPointerException("TextureFilter cannot be null");
        this.filter = filter;
        glBindTexture(GL_TEXTURE_2D, textureID);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, filter.minFilter);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, filter.magFilter);

        if (filter.usesMipmaps())
            glGenerateMipmap(GL_TEXTURE_2D);

    }

    /**
     * Sets the scale factors applied to the quad during vertex generation.
     *
     * <p>Important behavior:</p>
     * <ul>
     *     <li>This scales the rendered quad without changing the stored base {@link #width}/{@link #height}.</li>
     *     <li>The origin is adjusted proportionally so the pivot remains visually consistent under scaling.</li>
     * </ul>
     *
     * <p>After updating scale, this recalculates local vertices and rebuilds the final vertex buffer.</p>
     *
     * @param sx horizontal scale factor
     * @param sy vertical scale factor
     */
    public void setScale(float sx, float sy) {
        // Adjust origin proportionally to new scale.
        float oldScaleX = this.scaleX;
        float oldScaleY = this.scaleY;

        // Prevent divide-by-zero and invalid first-scale.
        if (oldScaleX != 0) origin.setX(origin.getX() * (sx / oldScaleX));
        if (oldScaleY != 0) origin.setY(origin.getY() * (sy / oldScaleY));

        this.scaleX = sx;
        this.scaleY = sy;

        updateLocalVertices();
        updateVertexBuffer();
    }

    /**
     * @return the current horizontal scale factor applied to this quad
     */
    public float getScaleX() {
        return scaleX;
    }

    /**
     * @return the current vertical scale factor applied to this quad
     */
    public float getScaleY() {
        return scaleY;
    }

    /**
     * Flips the texture mapping horizontally by swapping the left and right UV values.
     *
     * <p>This is a raw UV swap. It does not update {@link #flippedX}; prefer {@link #setFlipX(boolean)}
     * if you want stateful flipping.</p>
     */
    public void flipX() {
        float temp = leftRegion;
        leftRegion = rightRegion;
        rightRegion = temp;
        updateUVBuffer();
    }

    /**
     * Flips the texture mapping vertically by swapping the top and bottom UV values.
     *
     * <p>This is a raw UV swap. It does not update {@link #flippedY}; prefer {@link #setFlipY(boolean)}
     * if you want stateful flipping.</p>
     */
    public void flipY() {
        float temp = topRegion;
        topRegion = bottomRegion;
        bottomRegion = temp;
        updateUVBuffer();
    }

    /**
     * Enables or disables horizontal UV flipping.
     *
     * <p>This method swaps UVs only when the requested state differs from the current {@link #flippedX} state,
     * preventing repeated swaps.</p>
     *
     * @param flip true to flip horizontally, false to restore original orientation
     */
    public void setFlipX(boolean flip) {
        if (flip != flippedX) {
            flipX();
            flippedX = flip;
        }
    }

    /**
     * Enables or disables vertical UV flipping.
     *
     * <p>This method swaps UVs only when the requested state differs from the current {@link #flippedY} state,
     * preventing repeated swaps.</p>
     *
     * @param flip true to flip vertically, false to restore original orientation
     */
    public void setFlipY(boolean flip) {
        if (flip != flippedY) {
            flipY();
            flippedY = flip;
        }
    }

    /**
     * Sets horizontal and vertical flip states in one call.
     *
     * <p>This delegates to {@link #setFlipX(boolean)} and {@link #setFlipY(boolean)} so UV updates
     * occur only when needed.</p>
     *
     * @param flipX true to flip horizontally
     * @param flipY true to flip vertically
     */
    public void setFlip(boolean flipX, boolean flipY) {
        setFlipX(flipX);
        setFlipY(flipY);
    }

    /**
     * Sets the world-space position of the quad.
     *
     * <p>If the position is unchanged, this method exits early to avoid unnecessary buffer writes.</p>
     *
     * @param x world X coordinate
     * @param y world Y coordinate
     */
    public void setPosition(float x, float y) {
        if (this.x == x && this.y == y) return;
        this.x = x;
        this.y = y;
        updateVertexBuffer();
    }

    /**
     * @return current world-space X position
     */
    public float getX() {
        return x;
    }

    /**
     * @return current world-space Y position
     */
    public float getY() {
        return y;
    }

    /**
     * @return current base width of the quad (before scale)
     */
    public float getWidth() {
        return width;
    }

    /**
     * Sets the quad width (base, before scaling), then rebuilds local and final vertices.
     *
     * @param w new width
     */
    public void setWidth(float w) {
        this.width = w;
        updateLocalVertices();
        updateVertexBuffer();
    }

    /**
     * @return current base height of the quad (before scale)
     */
    public float getHeight() {
        return height;
    }

    /**
     * Sets the quad height (base, before scaling), then rebuilds local and final vertices.
     *
     * @param h new height
     */
    public void setHeight(float h) {
        this.height = h;
        updateLocalVertices();
        updateVertexBuffer();
    }

    /**
     * Sets both width and height (base, before scaling), then rebuilds local and final vertices.
     *
     * @param w new width
     * @param h new height
     */
    public void setSize(float w, float h) {
        this.width = w;
        this.height = h;
        updateLocalVertices();
        updateVertexBuffer();
    }

    /**
     * Defines a sub-rectangle of the texture to render, expressed in pixel coordinates.
     *
     * <p>This converts pixel coordinates into normalized UVs (0..1) using the underlying image size.</p>
     *
     * <p>Important:</p>
     * <ul>
     *     <li>This method does not modify {@link #flippedX}/{@link #flippedY} state.</li>
     *     <li>If you call this after flipping, the region values are replaced and you may want to reapply flips.</li>
     * </ul>
     *
     * @param left   left edge in pixels
     * @param top    top edge in pixels
     * @param right  right edge in pixels
     * @param bottom bottom edge in pixels
     */
    public void setRegion(float left, float top, float right, float bottom) {
        this.leftRegion = left / data.width();
        this.topRegion = top / data.height();
        this.rightRegion = right / data.width();
        this.bottomRegion = bottom / data.height();
        updateUVBuffer();
    }

    /**
     * Updates the UV buffer with the current normalized region values.
     *
     * <p>Corner order:</p>
     * <ol>
     *     <li>Top-left</li>
     *     <li>Top-right</li>
     *     <li>Bottom-right</li>
     *     <li>Bottom-left</li>
     * </ol>
     *
     * <p>This ordering must match the vertex ordering in {@link #updateLocalVertices()} and {@link #updateVertexBuffer()}.</p>
     */
    private void updateUVBuffer() {
        uvBuffer.put(0, leftRegion);
        uvBuffer.put(1, topRegion);
        uvBuffer.put(2, rightRegion);
        uvBuffer.put(3, topRegion);
        uvBuffer.put(4, rightRegion);
        uvBuffer.put(5, bottomRegion);
        uvBuffer.put(6, leftRegion);
        uvBuffer.put(7, bottomRegion);
    }

    /**
     * @return current rotation angle in degrees
     */
    public float getRotation() {
        return rotation;
    }

    /**
     * Sets rotation (in degrees) around {@link #origin} and rebuilds the final vertex buffer.
     *
     * <p>Implementation details:</p>
     * <ul>
     *     <li>Degrees are converted to radians.</li>
     *     <li>Radians are negated ({@code -degrees}) to match your clockwise rotation convention.</li>
     *     <li>Sine and cosine are cached into {@link #sinRot} and {@link #cosRot}.</li>
     * </ul>
     *
     * @param degrees clockwise rotation angle
     */
    public void setRotation(float degrees) {
        this.rotation = degrees;
        float rad = (float) Math.toRadians(-degrees);
        sinRot = (float) Math.sin(rad);
        cosRot = (float) Math.cos(rad);
        updateVertexBuffer();
    }

    /**
     * Sets the rotation origin/pivot relative to the quad.
     *
     * <p>After changing the origin, local and final vertices are rebuilt.</p>
     *
     * @param ox origin X relative to the quad's top-left
     * @param oy origin Y relative to the quad's top-left
     */
    public void setRotationOrigin(float ox, float oy) {
        this.origin.set(ox, oy);
        updateLocalVertices();
        updateVertexBuffer();
    }

    /**
     * Sets the rotation origin to the geometric center of the quad (after scaling).
     *
     * <p>This uses {@code width * scaleX / 2} and {@code height * scaleY / 2} to ensure the pivot is centered
     * in rendered space rather than base-size space.</p>
     */
    public void setRotationOriginCenter() {
        origin.set(width * scaleX / 2f, height * scaleY / 2f);
        updateLocalVertices();
        updateVertexBuffer();
    }

    /**
     * @return the current rotation origin vector (mutable)
     */
    public Vector2f getRotationOrigin() {
        return origin;
    }

    /**
     * Returns the decoded image data backing this texture.
     *
     * @return texture data container
     */
    public TextureData getData() {
        return data;
    }

    /**
     * @return current tint color used for rendering
     */
    public Color getColor() {
        return color;
    }

    /**
     * Sets the tint color used for rendering.
     *
     * <p>The color is applied via {@code glColor4f} inside {@link #draw()} and therefore multiplies the sampled
     * texture color.</p>
     *
     * @param color new color
     * @throws NullPointerException if {@code color} is null
     */
    public void setColor(Color color) {
        if (color == null) throw new NullPointerException("Color cannot be null");
        this.color = color;
    }

    /**
     * Computes local-space quad corners relative to {@link #origin}.
     *
     * <p>This method applies scaling to {@link #width} and {@link #height} to build the local rectangle,
     * but does not apply rotation or world translation.</p>
     *
     * <p>Corner order is consistent with {@link #updateUVBuffer()}:</p>
     * <ol>
     *     <li>Top-left</li>
     *     <li>Top-right</li>
     *     <li>Bottom-right</li>
     *     <li>Bottom-left</li>
     * </ol>
     */
    private void updateLocalVertices() {
        float ox = origin.getX();
        float oy = origin.getY();

        float scaledW = width * scaleX;
        float scaledH = height * scaleY;

        localVertices[0] = -ox;
        localVertices[1] = -oy;

        localVertices[2] = scaledW - ox;
        localVertices[3] = -oy;

        localVertices[4] = scaledW - ox;
        localVertices[5] = scaledH - oy;

        localVertices[6] = -ox;
        localVertices[7] = scaledH - oy;
    }

    /**
     * Rebuilds the final world-space {@link #vertexBuffer} by applying rotation and translation.
     *
     * <p>Computation:</p>
     * <ul>
     *     <li>Translate to pivot space: {@code px = x + origin.x}, {@code py = y + origin.y}</li>
     *     <li>Rotate each local corner using cached {@link #cosRot}/{@link #sinRot}</li>
     *     <li>Translate rotated corners into world space and write them into {@link #vertexBuffer}</li>
     * </ul>
     *
     * <p>This method performs no allocations and writes the buffer using absolute puts.</p>
     */
    private void updateVertexBuffer() {
        float px = x + origin.getX();
        float py = y + origin.getY();

        for (int i = 0; i < 4; i++) {
            float lx = localVertices[i * 2];
            float ly = localVertices[i * 2 + 1];

            float rx = lx * cosRot - ly * sinRot;
            float ry = lx * sinRot + ly * cosRot;

            vertexBuffer.put(i * 2, rx + px);
            vertexBuffer.put(i * 2 + 1, ry + py);
        }
    }

    /**
     * Renders the textured quad with the current transform, UV region, and color.
     *
     * <p>Preconditions (caller responsibility):</p>
     * <ul>
     *     <li>{@code glEnable(GL_TEXTURE_2D)} is enabled</li>
     *     <li>{@code glEnableClientState(GL_VERTEX_ARRAY)} is enabled</li>
     *     <li>{@code glEnableClientState(GL_TEXTURE_COORD_ARRAY)} is enabled</li>
     *     <li>Projection/model-view matrices are already configured</li>
     * </ul>
     *
     * <p>This method does not modify matrices or GL client state beyond binding the texture and issuing the draw.</p>
     */
    public void draw() {
        glColor4f(color.r(), color.g(), color.b(), color.a());
        glBindTexture(GL_TEXTURE_2D, textureID);
        glVertexPointer(2, GL_FLOAT, 0, vertexBuffer);
        glTexCoordPointer(2, GL_FLOAT, 0, uvBuffer);
        glDrawArrays(GL_QUADS, 0, 4);
    }

    /**
     * Exposes the internal vertex buffer used for rendering.
     *
     * <p>This is primarily useful for debugging or for batching systems that want to read the computed vertices.
     * The buffer contents are updated by {@link #updateVertexBuffer()}.</p>
     *
     * @return the world-space vertex buffer (length 8 floats)
     */
    public FloatBuffer getVertexBuffer() {
        return vertexBuffer;
    }

    /**
     * Exposes the internal UV buffer used for rendering.
     *
     * <p>The buffer contents are updated by {@link #updateUVBuffer()}.</p>
     *
     * @return the UV buffer (length 8 floats)
     */
    public FloatBuffer getUVBuffer() {
        return uvBuffer;
    }

    /**
     * Deletes the underlying OpenGL texture object.
     *
     * <p>After calling this method, {@link #textureID} is no longer valid and this instance should not be used.</p>
     */
    public void dispose() {
        glDeleteTextures(textureID);
        this.data = null;
        this.vertexBuffer = null;
        this.uvBuffer = null;
        this.color = null;
        this.origin = null;
        this.localVertices = null;
        this.filter = null;
    }

    /**
     * Returns the OpenGL texture handle for this instance.
     *
     * @return OpenGL texture id
     */
    public int getID() {
        return textureID;
    }
}
