package valthorne.graphics.texture;

/**
 * Represents a rectangular region inside an existing {@link Texture}.
 *
 * <h2>Example</h2>
 * <pre>{@code
 * // Load a sprite sheet texture.
 * Texture sheet = new Texture("assets/player_sprites.png");
 *
 * // Create a region that represents a 32x32 sprite located at (64, 0).
 * TextureRegion idleFrame = new TextureRegion(sheet, 64, 0, 32, 32);
 *
 * // Draw the region like a normal texture.
 * idleFrame.setPosition(200, 100);
 * idleFrame.draw();
 *
 * // Resize it on screen (region sampling does not change).
 * idleFrame.setSize(96, 96);
 * idleFrame.draw();
 *
 * // Change the sampled sprite region dynamically.
 * idleFrame.setRegion(96, 0, 32, 32);
 * idleFrame.draw();
 *
 * // IMPORTANT:
 * // Disposing a TextureRegion does nothing because it does not own the texture.
 * // Always dispose the original Texture instead.
 * sheet.dispose();
 * }</pre>
 *
 * <h2>What this class does</h2>
 * <p>
 * {@code TextureRegion} is a lightweight view into a portion of an existing {@link Texture}.
 * Instead of creating a new OpenGL texture, this class reuses the parent texture's OpenGL ID
 * and {@link TextureData}. The region defines which part of the parent texture should be
 * sampled during rendering.
 * </p>
 *
 * <h2>How regions work</h2>
 * <p>
 * Internally, the base {@link Texture} class stores normalized UV coordinates
 * ({@code leftRegion}, {@code rightRegion}, {@code topRegion}, {@code bottomRegion}).
 * This class converts pixel coordinates into those normalized UV values by calling
 * {@link Texture#setRegion(float, float, float, float)}.
 * </p>
 *
 * <h2>Ownership and disposal</h2>
 * <p>
 * A {@code TextureRegion} does not own any GPU resources. It simply references the
 * OpenGL texture ID of the parent texture. Calling {@link #dispose()} on this class
 * intentionally does nothing. The parent {@link Texture} must be disposed instead.
 * </p>
 *
 * @author Albert Beaupre
 * @since December 22nd, 2025
 */
public final class TextureRegion extends Texture {

    /**
     * Creates a {@code TextureRegion} that covers the entire parent texture.
     *
     * <p>
     * This constructor is equivalent to creating a region starting at (0,0)
     * with the full width and height of the parent texture.
     * </p>
     *
     * @param parent the parent texture whose pixels will be sampled
     */
    public TextureRegion(Texture parent) {
        this(parent, 0f, 0f, parent.getData().width(), parent.getData().height());
    }

    /**
     * Creates a {@code TextureRegion} representing a rectangular portion of the parent texture.
     *
     * <p>
     * The region is defined in source pixel coordinates. The provided rectangle is converted
     * into normalized UV coordinates internally by forwarding the values to
     * {@link Texture#setRegion(float, float, float, float)}.
     * </p>
     *
     * <p>
     * The region size is also applied as the initial render size using {@link #setSize(float, float)}.
     * This means the region will draw at its native pixel dimensions unless resized later.
     * </p>
     *
     * @param parent       the parent texture whose OpenGL ID and data will be reused
     * @param regionX      the left pixel coordinate of the region
     * @param regionY      the top pixel coordinate of the region
     * @param regionWidth  the width of the region in pixels
     * @param regionHeight the height of the region in pixels
     */
    public TextureRegion(Texture parent, float regionX, float regionY, float regionWidth, float regionHeight) {
        super(parent.getTextureID(), parent.getData());
        setRegion(regionX, regionY, regionWidth, regionHeight);
        setSize(regionWidth, regionHeight);
    }

    /**
     * Updates the sampled region of the parent texture.
     *
     * <p>
     * This method defines a rectangle in source pixel coordinates. Internally,
     * the right and bottom edges are computed by adding the width and height
     * to the provided starting position.
     * </p>
     *
     * <p>
     * The resulting rectangle is forwarded to the base {@link Texture#setRegion}
     * method which converts the coordinates into normalized UV values used during rendering.
     * </p>
     *
     * @param regionX      the left pixel coordinate of the region
     * @param regionY      the top pixel coordinate of the region
     * @param regionWidth  the width of the region in pixels
     * @param regionHeight the height of the region in pixels
     */
    public void setRegion(float regionX, float regionY, float regionWidth, float regionHeight) {
        super.setRegion(regionX, regionY, regionX + regionWidth, regionY + regionHeight);
    }

    /**
     * Returns the left pixel coordinate of the current region.
     *
     * <p>
     * The base texture stores normalized UV coordinates. This method converts
     * the normalized value back into pixel space using the texture width.
     * </p>
     *
     * @return the region's left pixel coordinate
     */
    public float getRegionX() {
        return leftRegion * data.width();
    }

    /**
     * Returns the top pixel coordinate of the current region.
     *
     * <p>
     * This value is calculated by converting the normalized UV coordinate
     * into pixel space using the texture height.
     * </p>
     *
     * @return the region's top pixel coordinate
     */
    public float getRegionY() {
        return topRegion * data.height();
    }

    /**
     * Returns the width of the region in pixels.
     *
     * <p>
     * The width is derived from the difference between the normalized
     * right and left UV coordinates multiplied by the texture width.
     * </p>
     *
     * @return region width in pixels
     */
    public float getRegionWidth() {
        return (rightRegion - leftRegion) * data.width();
    }

    /**
     * Returns the height of the region in pixels.
     *
     * <p>
     * The height is derived from the difference between the normalized
     * bottom and top UV coordinates multiplied by the texture height.
     * </p>
     *
     * @return region height in pixels
     */
    public float getRegionHeight() {
        return (bottomRegion - topRegion) * data.height();
    }

    /**
     * Disposes the region.
     *
     * <p>
     * This method intentionally performs no action. The region does not
     * own the OpenGL texture resource. The parent {@link Texture} must
     * be disposed instead.
     * </p>
     */
    @Override
    public void dispose() {
    }
}