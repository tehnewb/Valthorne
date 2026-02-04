package valthorne.graphics.texture;

/**
 * Represents a rectangular sub-region of a {@link Texture}, allowing you to draw only a portion of an
 * atlas/spritesheet while also controlling the output position and size.
 *
 * <p>This class stores two separate concepts:</p>
 * <ul>
 *     <li><b>Region (source rectangle)</b>: {@link #regionX}, {@link #regionY}, {@link #regionWidth}, {@link #regionHeight}
 *     define the pixel rectangle inside the backing {@link Texture}.</li>
 *     <li><b>Output (destination rectangle)</b>: {@link #x}, {@link #y}, {@link #width}, {@link #height}
 *     define where and how large the region is drawn in world space.</li>
 * </ul>
 *
 * <p>When {@link #draw()} is called, the region and output settings are pushed into the backing {@link Texture}
 * via {@code setRegion()}, {@code setSize()}, and {@code setPosition()}, then the texture is rendered.</p>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * Texture atlas = new Texture("assets/sprites/ui_atlas.png");
 *
 * // Use a 16x16 icon starting at (32, 48) inside the atlas.
 * TextureRegion icon = new TextureRegion(atlas, 32, 48, 16, 16);
 * icon.setPosition(100, 200);
 * icon.setSize(64, 64); // scale up the icon on-screen
 *
 * // In your render loop:
 * icon.draw();
 * }</pre>
 *
 * <p><b>Note:</b> Because {@link #draw()} mutates the backing {@link Texture}'s region/size/position, multiple
 * {@code TextureRegion}s that share the same {@link Texture} should not be drawn concurrently without reapplying
 * their settings each time (which {@link #draw()} does). If you interleave draws of regions sharing one texture,
 * the last draw call wins for that texture's state.</p>
 *
 * @author Albert Beaupre
 * @since December 22nd, 2025
 */
public class TextureRegion {

    private final Texture texture;          // The backing texture that this region references and draws from.
    private float regionX, regionY;         // Source-region top-left coordinates inside the texture (pixels).
    private float regionWidth, regionHeight; // Source-region size inside the texture (pixels).
    private float x, y;                     // Destination position in world space where the region is drawn.
    private float width, height;            // Destination size in world space for the rendered region.

    /**
     * Creates a {@code TextureRegion} that spans the entire backing texture.
     *
     * <p>The created region uses:</p>
     * <ul>
     *     <li>Region: (0,0) to (texture width, texture height)</li>
     *     <li>Output: defaults to the region size unless changed via {@link #setSize(float, float)}</li>
     * </ul>
     *
     * @param texture the backing texture
     * @throws NullPointerException if {@code texture} is null
     */
    public TextureRegion(Texture texture) {
        this(texture, 0, 0, (int) texture.getWidth(), (int) texture.getHeight());
    }

    /**
     * Creates a {@code TextureRegion} that references a specific sub-rectangle of a backing {@link Texture}.
     *
     * <p>This constructor sets the source region only. Output position and size can be configured independently
     * via {@link #setPosition(float, float)} and {@link #setSize(float, float)}.</p>
     *
     * @param texture      the backing texture from which the region is defined
     * @param regionX      source x-coordinate in pixels inside the texture
     * @param regionY      source y-coordinate in pixels inside the texture
     * @param regionWidth  source width in pixels inside the texture
     * @param regionHeight source height in pixels inside the texture
     * @throws NullPointerException if {@code texture} is null
     */
    public TextureRegion(Texture texture, float regionX, float regionY, float regionWidth, float regionHeight) {
        if (texture == null) throw new NullPointerException("Texture cannot be null");
        this.texture = texture;
        this.setRegion(regionX, regionY, regionWidth, regionHeight);
    }

    /**
     * Updates the source rectangle inside the backing texture.
     *
     * <p>This does not automatically change the output size. If you want the output size to match the new region,
     * call {@link #setSize(float, float)} as well.</p>
     *
     * @param regionX      source x-coordinate in pixels inside the texture
     * @param regionY      source y-coordinate in pixels inside the texture
     * @param regionWidth  source width in pixels inside the texture
     * @param regionHeight source height in pixels inside the texture
     */
    public void setRegion(float regionX, float regionY, float regionWidth, float regionHeight) {
        this.regionX = regionX;
        this.regionY = regionY;
        this.regionWidth = regionWidth;
        this.regionHeight = regionHeight;
    }

    /**
     * Draws this region using the backing {@link Texture}.
     *
     * <p>This method:</p>
     * <ol>
     *     <li>Configures the backing texture to sample from this region via {@link Texture#setRegion(float, float, float, float)}.</li>
     *     <li>Sets the backing texture's quad size via {@link Texture#setSize(float, float)}.</li>
     *     <li>Sets the backing texture's position via {@link Texture#setPosition(float, float)}.</li>
     *     <li>Issues the draw call via {@link Texture#draw()}.</li>
     * </ol>
     *
     * <p><b>Important:</b> This mutates the backing {@link Texture}'s state. If multiple regions share the same
     * texture, make sure you call {@link #draw()} for each region (which re-applies state) and avoid relying on
     * the texture retaining prior region values between unrelated draws.</p>
     */
    public void draw() {
        texture.setRegion(regionX, regionY, regionWidth, regionHeight);
        texture.setSize(width, height);
        texture.setPosition(x, y);
        texture.draw();
    }

    /**
     * Sets the destination size (rendered output dimensions) for this region.
     *
     * <p>This does not change the source region size. It only controls how large the region is drawn.</p>
     *
     * @param width  output width in world units
     * @param height output height in world units
     */
    public void setSize(float width, float height) {
        this.width = width;
        this.height = height;
    }

    /**
     * Sets the destination position (rendered output position) for this region.
     *
     * <p>This does not change the source region position inside the texture.</p>
     *
     * @param x destination x in world space
     * @param y destination y in world space
     */
    public void setPosition(float x, float y) {
        this.x = x;
        this.y = y;
    }

    /**
     * Returns the configured output width.
     *
     * @return output width in world units
     */
    public float getWidth() {
        return width;
    }

    /**
     * Returns the configured output height.
     *
     * @return output height in world units
     */
    public float getHeight() {
        return height;
    }

    /**
     * Returns the configured output x position.
     *
     * @return destination x in world space
     */
    public float getX() {
        return x;
    }

    /**
     * Returns the configured output y position.
     *
     * @return destination y in world space
     */
    public float getY() {
        return y;
    }

    /**
     * Returns the source-region X coordinate (in pixels) inside the backing texture.
     *
     * @return source x in pixels
     */
    public float getRegionX() {
        return regionX;
    }

    /**
     * Returns the source-region Y coordinate (in pixels) inside the backing texture.
     *
     * @return source y in pixels
     */
    public float getRegionY() {
        return regionY;
    }

    /**
     * Returns the backing texture used by this region.
     *
     * @return backing {@link Texture}
     */
    public Texture getTexture() {
        return texture;
    }
}
