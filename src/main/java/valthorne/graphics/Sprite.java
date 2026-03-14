package valthorne.graphics;

import org.lwjgl.BufferUtils;
import valthorne.graphics.texture.Texture;
import valthorne.graphics.texture.TextureData;
import valthorne.graphics.texture.TextureRegion;
import valthorne.io.pool.Poolable;
import valthorne.math.Vector2f;
import valthorne.math.geometry.Rectangle;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL11.*;

/**
 * <p>
 * {@code Sprite} represents a textured 2D drawable built from a {@link TextureRegion}.
 * It stores transform state such as position, size, scale, rotation, rotation origin,
 * flip flags, and tint color, while also maintaining cached vertex and UV buffers that
 * can be used for direct OpenGL drawing or by higher-level rendering systems.
 * </p>
 *
 * <p>
 * This class is designed as a lightweight textured quad abstraction. A sprite does not
 * own complex rendering state. Instead, it focuses on describing how a rectangular
 * region of a texture should be drawn in world space. It can be created from a file
 * path, raw image bytes, {@link TextureData}, a {@link Texture}, or a
 * {@link TextureRegion}.
 * </p>
 *
 * <p>
 * Internally, the sprite keeps two important cached buffers:
 * </p>
 *
 * <ul>
 *     <li>a vertex buffer containing the world-space positions of the four corners</li>
 *     <li>a UV buffer containing the texture coordinates for the same four corners</li>
 * </ul>
 *
 * <p>
 * These buffers are updated whenever a relevant property changes:
 * </p>
 *
 * <ul>
 *     <li>region changes update the UV buffer</li>
 *     <li>flip changes update the UV buffer</li>
 *     <li>size or scale changes update the local vertices and world vertices</li>
 *     <li>position or rotation changes update the world vertices</li>
 *     <li>rotation origin changes update both local and world vertices</li>
 * </ul>
 *
 * <p>
 * The sprite supports direct rendering through legacy OpenGL client arrays using
 * {@code glVertexPointer}, {@code glTexCoordPointer}, and {@code glDrawArrays}.
 * It can also be consumed by systems like {@link valthorne.graphics.texture.TextureBatch}
 * which read sprite state and render it in a batched way.
 * </p>
 *
 * <h2>Example Usage</h2>
 *
 * <pre>{@code
 * Sprite sprite = new Sprite("assets/player.png");
 * sprite.setPosition(100f, 80f);
 * sprite.setSize(64f, 64f);
 * sprite.setScale(1.25f, 1.25f);
 * sprite.setRotationOriginCenter();
 * sprite.setRotation(15f);
 * sprite.setFlip(false, true);
 * sprite.setColor(new Color(1f, 1f, 1f, 0.9f));
 *
 * sprite.draw();
 *
 * Texture texture = sprite.getTexture();
 * TextureRegion region = sprite.getRegion();
 * FloatBuffer vertices = sprite.getVertexBuffer();
 * FloatBuffer uvs = sprite.getUVBuffer();
 *
 * sprite.reset();
 * sprite.dispose();
 * }</pre>
 *
 * <p>
 * This example demonstrates the full workflow of the class: construction, transform
 * updates, flipping, tinting, drawing, buffer access, resetting, and disposal.
 * </p>
 *
 * @author Albert Beaupre
 * @since March 9th, 2026
 */
public class Sprite implements Poolable {

    protected TextureRegion region; // Texture region currently used by this sprite
    protected FloatBuffer vertexBuffer = BufferUtils.createFloatBuffer(8); // Cached world-space vertex positions for the four sprite corners
    protected FloatBuffer uvBuffer = BufferUtils.createFloatBuffer(8); // Cached UV coordinates for the four sprite corners
    protected float[] localVertices = new float[8]; // Cached local-space vertices before world transform is applied
    protected Vector2f origin = new Vector2f(0f, 0f); // Rotation origin used for local-to-world transformation
    protected final Rectangle bounds; // World position and unscaled size of the sprite
    protected float rotation; // Current rotation in degrees
    protected float sinRot; // Cached sine of the current rotation
    protected float cosRot = 1f; // Cached cosine of the current rotation
    protected boolean flippedX; // Whether the sprite UVs are flipped horizontally
    protected boolean flippedY; // Whether the sprite UVs are flipped vertically
    protected float scaleX = 1f; // Horizontal scale factor
    protected float scaleY = 1f; // Vertical scale factor
    protected Color color = new Color(1, 1, 1, 1); // Tint color used when drawing the sprite

    /**
     * <p>
     * Creates a sprite by loading a texture from a file path.
     * </p>
     *
     * <p>
     * This constructor creates a {@link Texture} from the given path and then
     * delegates to {@link #Sprite(Texture)}.
     * </p>
     *
     * @param path the file path of the texture to load
     */
    public Sprite(String path) {
        this(new Texture(path));
    }

    /**
     * <p>
     * Creates a sprite by loading a texture from encoded image bytes.
     * </p>
     *
     * <p>
     * This constructor creates a {@link Texture} from the provided bytes and then
     * delegates to {@link #Sprite(Texture)}.
     * </p>
     *
     * @param data the encoded image bytes to load
     */
    public Sprite(byte[] data) {
        this(new Texture(data));
    }

    /**
     * <p>
     * Creates a sprite from prepared {@link TextureData}.
     * </p>
     *
     * <p>
     * This constructor creates a {@link Texture} from the provided texture data and
     * then delegates to {@link #Sprite(Texture)}.
     * </p>
     *
     * @param data the prepared texture data
     */
    public Sprite(TextureData data) {
        this(new Texture(data));
    }

    /**
     * <p>
     * Creates a sprite that uses the full area of the given {@link Texture}.
     * </p>
     *
     * <p>
     * This constructor creates a {@link TextureRegion} spanning the entire texture and
     * then delegates to {@link #Sprite(TextureRegion)}.
     * </p>
     *
     * @param texture the texture to use
     */
    public Sprite(Texture texture) {
        this(new TextureRegion(texture));
    }

    /**
     * <p>
     * Creates a sprite from an existing {@link TextureRegion}.
     * </p>
     *
     * <p>
     * The sprite initially uses the region's width and height as its bounds size,
     * starts at position {@code (0, 0)}, uses a zero rotation origin, has no flipping,
     * uses white tinting, and immediately builds its local vertices, UV buffer, and
     * world-space vertex buffer.
     * </p>
     *
     * @param region the texture region used by this sprite
     * @throws NullPointerException if {@code region} is {@code null}
     */
    public Sprite(TextureRegion region) {
        if (region == null) throw new NullPointerException("TextureRegion cannot be null");

        this.region = region;
        this.bounds = new Rectangle(0, 0, region.getRegionWidth(), region.getRegionHeight());
        updateLocalVertices();
        updateUVBuffer();
        updateVertexBuffer();
    }

    /**
     * <p>
     * Returns the texture currently used by this sprite.
     * </p>
     *
     * @return the backing texture
     */
    public Texture getTexture() {
        return region.getTexture();
    }

    /**
     * <p>
     * Returns the texture region currently used by this sprite.
     * </p>
     *
     * @return the current texture region
     */
    public TextureRegion getRegion() {
        return region;
    }

    /**
     * <p>
     * Replaces the sprite's current texture region.
     * </p>
     *
     * <p>
     * This updates the region reference and rebuilds the UV buffer so subsequent
     * rendering uses the new source rectangle.
     * </p>
     *
     * @param region the new texture region
     * @throws NullPointerException if {@code region} is {@code null}
     */
    public void setRegion(TextureRegion region) {
        if (region == null) throw new NullPointerException("TextureRegion cannot be null");

        this.region = region;
        updateUVBuffer();
    }

    /**
     * <p>
     * Updates the current texture region's pixel bounds in place.
     * </p>
     *
     * <p>
     * After changing the region, the UV buffer is rebuilt so rendering uses the
     * updated coordinates.
     * </p>
     *
     * @param regionX      the new region X position
     * @param regionY      the new region Y position
     * @param regionWidth  the new region width
     * @param regionHeight the new region height
     */
    public void setRegion(float regionX, float regionY, float regionWidth, float regionHeight) {
        region.setRegion(regionX, regionY, regionWidth, regionHeight);
        updateUVBuffer();
    }

    /**
     * <p>
     * Returns the X position of the current texture region in pixels.
     * </p>
     *
     * @return the region X position
     */
    public float getRegionX() {
        return region.getRegionX();
    }

    /**
     * <p>
     * Returns the Y position of the current texture region in pixels.
     * </p>
     *
     * @return the region Y position
     */
    public float getRegionY() {
        return region.getRegionY();
    }

    /**
     * <p>
     * Returns the width of the current texture region in pixels.
     * </p>
     *
     * @return the region width
     */
    public float getRegionWidth() {
        return region.getRegionWidth();
    }

    /**
     * <p>
     * Returns the height of the current texture region in pixels.
     * </p>
     *
     * @return the region height
     */
    public float getRegionHeight() {
        return region.getRegionHeight();
    }

    /**
     * <p>
     * Sets the horizontal and vertical scale of the sprite.
     * </p>
     *
     * <p>
     * The current rotation origin is adjusted proportionally so that an existing origin
     * remains visually aligned when the sprite is rescaled. After that, local vertices
     * and world vertices are rebuilt.
     * </p>
     *
     * @param sx the new horizontal scale
     * @param sy the new vertical scale
     */
    public void setScale(float sx, float sy) {
        float oldScaleX = this.scaleX;
        float oldScaleY = this.scaleY;

        if (oldScaleX != 0f) origin.setX(origin.getX() * (sx / oldScaleX));
        if (oldScaleY != 0f) origin.setY(origin.getY() * (sy / oldScaleY));

        this.scaleX = sx;
        this.scaleY = sy;

        updateLocalVertices();
        updateVertexBuffer();
    }

    /**
     * <p>
     * Returns the current horizontal scale.
     * </p>
     *
     * @return the horizontal scale
     */
    public float getScaleX() {
        return scaleX;
    }

    /**
     * <p>
     * Returns the current vertical scale.
     * </p>
     *
     * @return the vertical scale
     */
    public float getScaleY() {
        return scaleY;
    }

    /**
     * <p>
     * Sets whether the sprite is horizontally flipped.
     * </p>
     *
     * <p>
     * Flipping only affects the UV buffer, not the actual geometry.
     * </p>
     *
     * @param flip {@code true} to flip horizontally
     */
    public void setFlipX(boolean flip) {
        if (this.flippedX == flip) return;

        this.flippedX = flip;
        updateUVBuffer();
    }

    /**
     * <p>
     * Sets whether the sprite is vertically flipped.
     * </p>
     *
     * <p>
     * Flipping only affects the UV buffer, not the actual geometry.
     * </p>
     *
     * @param flip {@code true} to flip vertically
     */
    public void setFlipY(boolean flip) {
        if (this.flippedY == flip) return;

        this.flippedY = flip;
        updateUVBuffer();
    }

    /**
     * <p>
     * Sets both horizontal and vertical flip state at once.
     * </p>
     *
     * @param flipX {@code true} to flip horizontally
     * @param flipY {@code true} to flip vertically
     */
    public void setFlip(boolean flipX, boolean flipY) {
        this.flippedX = flipX;
        this.flippedY = flipY;
        updateUVBuffer();
    }

    /**
     * <p>
     * Returns whether the sprite is currently flipped horizontally.
     * </p>
     *
     * @return {@code true} if flipped horizontally
     */
    public boolean isFlippedX() {
        return flippedX;
    }

    /**
     * <p>
     * Returns whether the sprite is currently flipped vertically.
     * </p>
     *
     * @return {@code true} if flipped vertically
     */
    public boolean isFlippedY() {
        return flippedY;
    }

    /**
     * <p>
     * Sets the world position of the sprite.
     * </p>
     *
     * <p>
     * If the position is unchanged, the method returns immediately. Otherwise the
     * bounds are updated and the world-space vertex buffer is rebuilt.
     * </p>
     *
     * @param x the new X position
     * @param y the new Y position
     */
    public void setPosition(float x, float y) {
        if (bounds.getX() == x && bounds.getY() == y) return;

        bounds.setX(x);
        bounds.setY(y);
        updateVertexBuffer();
    }

    /**
     * <p>
     * Returns the sprite's world X position.
     * </p>
     *
     * @return the X position
     */
    public float getX() {
        return bounds.getX();
    }

    /**
     * <p>
     * Returns the sprite's world Y position.
     * </p>
     *
     * @return the Y position
     */
    public float getY() {
        return bounds.getY();
    }

    /**
     * <p>
     * Returns the sprite's unscaled width stored in its bounds.
     * </p>
     *
     * @return the width
     */
    public float getWidth() {
        return bounds.getWidth();
    }

    /**
     * <p>
     * Sets the sprite width.
     * </p>
     *
     * <p>
     * Changing the width requires the local quad geometry and world-space vertices to
     * be rebuilt.
     * </p>
     *
     * @param width the new width
     */
    public void setWidth(float width) {
        if (bounds.getWidth() == width) return;

        bounds.setWidth(width);
        updateLocalVertices();
        updateVertexBuffer();
    }

    /**
     * <p>
     * Returns the sprite's unscaled height stored in its bounds.
     * </p>
     *
     * @return the height
     */
    public float getHeight() {
        return bounds.getHeight();
    }

    /**
     * <p>
     * Sets the sprite height.
     * </p>
     *
     * <p>
     * Changing the height requires the local quad geometry and world-space vertices to
     * be rebuilt.
     * </p>
     *
     * @param height the new height
     */
    public void setHeight(float height) {
        if (bounds.getHeight() == height) return;

        bounds.setHeight(height);
        updateLocalVertices();
        updateVertexBuffer();
    }

    /**
     * <p>
     * Sets the sprite size in one call.
     * </p>
     *
     * <p>
     * If either dimension changes, local geometry and world-space vertices are rebuilt.
     * </p>
     *
     * @param width  the new width
     * @param height the new height
     */
    public void setSize(float width, float height) {
        if (bounds.getWidth() == width && bounds.getHeight() == height) return;

        bounds.setWidth(width);
        bounds.setHeight(height);
        updateLocalVertices();
        updateVertexBuffer();
    }

    /**
     * <p>
     * Returns the current rotation in degrees.
     * </p>
     *
     * @return the rotation angle in degrees
     */
    public float getRotation() {
        return rotation;
    }

    /**
     * <p>
     * Sets the sprite rotation in degrees.
     * </p>
     *
     * <p>
     * The angle is converted into cached sine and cosine values immediately so
     * repeated vertex updates do not need to recompute the trigonometric functions.
     * The world-space vertex buffer is rebuilt after the rotation changes.
     * </p>
     *
     * @param degrees the new rotation angle in degrees
     */
    public void setRotation(float degrees) {
        this.rotation = degrees;
        float rad = (float) Math.toRadians(-degrees);
        sinRot = (float) Math.sin(rad);
        cosRot = (float) Math.cos(rad);
        updateVertexBuffer();
    }

    /**
     * <p>
     * Sets the rotation origin used when transforming the sprite.
     * </p>
     *
     * <p>
     * Since the local quad is defined relative to the origin, changing the origin
     * requires both the local vertices and world-space vertices to be rebuilt.
     * </p>
     *
     * @param ox the new origin X
     * @param oy the new origin Y
     */
    public void setRotationOrigin(float ox, float oy) {
        this.origin.set(ox, oy);
        updateLocalVertices();
        updateVertexBuffer();
    }

    /**
     * <p>
     * Sets the rotation origin to the center of the scaled sprite.
     * </p>
     *
     * <p>
     * The center is computed using the current bounds size multiplied by the current
     * scale values.
     * </p>
     */
    public void setRotationOriginCenter() {
        origin.set(bounds.getWidth() * scaleX / 2f, bounds.getHeight() * scaleY / 2f);
        updateLocalVertices();
        updateVertexBuffer();
    }

    /**
     * <p>
     * Returns the current rotation origin vector.
     * </p>
     *
     * @return the rotation origin
     */
    public Vector2f getRotationOrigin() {
        return origin;
    }

    /**
     * <p>
     * Returns the sprite tint color.
     * </p>
     *
     * @return the tint color
     */
    public Color getColor() {
        return color;
    }

    /**
     * <p>
     * Sets the tint color used when drawing the sprite.
     * </p>
     *
     * <p>
     * The provided color reference is stored directly.
     * </p>
     *
     * @param color the new tint color
     * @throws NullPointerException if {@code color} is {@code null}
     */
    public void setColor(Color color) {
        if (color == null) throw new NullPointerException("Color cannot be null");

        this.color = color;
    }

    /**
     * <p>
     * Returns the sprite bounds rectangle.
     * </p>
     *
     * @return the bounds rectangle
     */
    public Rectangle getBounds() {
        return bounds;
    }

    /**
     * <p>
     * Returns the cached world-space vertex buffer.
     * </p>
     *
     * @return the vertex buffer
     */
    public FloatBuffer getVertexBuffer() {
        return vertexBuffer;
    }

    /**
     * <p>
     * Returns the cached UV buffer.
     * </p>
     *
     * @return the UV buffer
     */
    public FloatBuffer getUVBuffer() {
        return uvBuffer;
    }

    /**
     * <p>
     * Rebuilds the UV buffer from the current region and flip state.
     * </p>
     *
     * <p>
     * The region UVs are read from the current {@link TextureRegion}. If either flip
     * flag is active, the corresponding UV pair is swapped before being written into
     * the buffer in quad order.
     * </p>
     */
    protected void updateUVBuffer() {
        float left = region.getU();
        float top = region.getV();
        float right = region.getU2();
        float bottom = region.getV2();

        if (flippedX) {
            float temp = left;
            left = right;
            right = temp;
        }

        if (flippedY) {
            float temp = top;
            top = bottom;
            bottom = temp;
        }

        uvBuffer.put(0, left);
        uvBuffer.put(1, top);
        uvBuffer.put(2, right);
        uvBuffer.put(3, top);
        uvBuffer.put(4, right);
        uvBuffer.put(5, bottom);
        uvBuffer.put(6, left);
        uvBuffer.put(7, bottom);
    }

    /**
     * <p>
     * Rebuilds the local vertex positions for the sprite quad.
     * </p>
     *
     * <p>
     * The local quad is defined relative to the current rotation origin and scaled
     * bounds size. These local coordinates are later transformed into world-space by
     * {@link #updateVertexBuffer()}.
     * </p>
     */
    protected void updateLocalVertices() {
        float ox = origin.getX();
        float oy = origin.getY();

        float scaledW = bounds.getWidth() * scaleX;
        float scaledH = bounds.getHeight() * scaleY;

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
     * <p>
     * Rebuilds the world-space vertex buffer from the local vertex data.
     * </p>
     *
     * <p>
     * Each local point is rotated using the cached sine and cosine values, then
     * translated by the sprite position plus its origin. The resulting four points
     * are written into the vertex buffer in quad order.
     * </p>
     */
    protected void updateVertexBuffer() {
        float px = bounds.getX() + origin.getX();
        float py = bounds.getY() + origin.getY();

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
     * <p>
     * Draws the sprite immediately using legacy OpenGL client arrays.
     * </p>
     *
     * <p>
     * The current tint color is applied, the backing texture is bound, the cached
     * vertex and UV buffers are supplied to OpenGL, and the quad is rendered with
     * {@code glDrawArrays(GL_QUADS, 0, 4)}.
     * </p>
     */
    public void draw() {
        glColor4f(color.r(), color.g(), color.b(), color.a());
        glBindTexture(GL_TEXTURE_2D, region.getTexture().getTextureID());
        glVertexPointer(2, GL_FLOAT, 0, vertexBuffer);
        glTexCoordPointer(2, GL_FLOAT, 0, uvBuffer);
        glDrawArrays(GL_QUADS, 0, 4);
    }

    /**
     * <p>
     * Disposes internal references held by this sprite.
     * </p>
     *
     * <p>
     * This method does not dispose the underlying texture. It only clears this sprite's
     * references and buffers. After this call, the sprite should no longer be used.
     * </p>
     */
    public void dispose() {
        this.region = null;
        this.vertexBuffer = null;
        this.uvBuffer = null;
        this.localVertices = null;
        this.origin = null;
        this.color = null;
    }

    /**
     * <p>
     * Resets this sprite to its default pooled state.
     * </p>
     *
     * <p>
     * The position is reset to zero, the size is restored to the region size, rotation
     * is cleared, cached sine and cosine are reset, flip flags are disabled, scale is
     * reset to one, the origin is reset to zero, the tint color is set to
     * {@link Color#WHITE}, and all cached geometry is rebuilt.
     * </p>
     */
    @Override
    public void reset() {
        bounds.setX(0f);
        bounds.setY(0f);
        bounds.setWidth(region.getRegionWidth());
        bounds.setHeight(region.getRegionHeight());
        rotation = 0f;
        sinRot = 0f;
        cosRot = 1f;
        flippedX = false;
        flippedY = false;
        scaleX = 1f;
        scaleY = 1f;
        origin.set(0f, 0f);
        color = Color.WHITE;
        updateLocalVertices();
        updateUVBuffer();
        updateVertexBuffer();
    }
}