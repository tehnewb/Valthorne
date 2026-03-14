package valthorne.graphics.texture;

import valthorne.graphics.Sprite;

/**
 * <p>
 * {@code TextureRegion} represents a rectangular subsection of a {@link Texture}.
 * Instead of rendering an entire texture, this class allows a specific portion
 * of the texture to be referenced and used for rendering. This is commonly used
 * with sprite sheets, texture atlases, tile maps, and UI element collections
 * where multiple images are stored within a single texture.
 * </p>
 *
 * <p>
 * The region is defined using pixel coordinates relative to the underlying
 * texture. The class then exposes both pixel-space values and normalized
 * texture coordinates ({@code u, v}) used by OpenGL rendering.
 * </p>
 *
 * <p>
 * A {@code TextureRegion} does not allocate GPU resources and does not perform
 * rendering itself. It simply describes a subsection of a texture that can be
 * used by higher-level rendering types such as:
 * </p>
 *
 * <ul>
 *     <li>{@link Sprite}</li>
 *     <li>{@link NinePatchTexture}</li>
 *     <li>{@link TextureBatch}</li>
 * </ul>
 *
 * <p>
 * Regions can be freely modified after creation. Changing the region updates
 * the pixel bounds used to compute UV coordinates but does not affect the
 * underlying texture.
 * </p>
 *
 * <h2>Example Usage</h2>
 *
 * <pre>{@code
 * Texture texture = new Texture("spritesheet.png");
 *
 * TextureRegion playerIdle = new TextureRegion(texture, 0, 0, 32, 32);
 * TextureRegion playerWalk = new TextureRegion(texture, 32, 0, 32, 32);
 *
 * float u = playerIdle.getU();
 * float v = playerIdle.getV();
 * float u2 = playerIdle.getU2();
 * float v2 = playerIdle.getV2();
 *
 * playerWalk.setRegion(64, 0, 32, 32);
 *
 * TextureBatch batch = new TextureBatch(1000);
 * batch.begin();
 * batch.drawRegion(playerIdle.getTexture(), 100, 100,
 *                  playerIdle.getRegionWidth(),
 *                  playerIdle.getRegionHeight(),
 *                  playerIdle.getRegionX(),
 *                  playerIdle.getRegionY(),
 *                  playerIdle.getRegionWidth(),
 *                  playerIdle.getRegionHeight());
 * batch.end();
 * }</pre>
 *
 * <p>
 * This example demonstrates creating regions from a sprite sheet, modifying
 * their coordinates, retrieving normalized UV values, and using them during
 * rendering.
 * </p>
 *
 * @author Albert Beaupre
 * @since December 22nd, 2025
 */
public final class TextureRegion {

    private final Texture texture; // The underlying texture that contains this region

    private float regionX; // X coordinate of the region within the texture (in pixels)
    private float regionY; // Y coordinate of the region within the texture (in pixels)
    private float regionWidth; // Width of the region (in pixels)
    private float regionHeight; // Height of the region (in pixels)

    /**
     * <p>
     * Creates a {@code TextureRegion} that represents the entire texture.
     * </p>
     *
     * <p>
     * The region bounds are initialized to match the full width and height
     * of the provided texture.
     * </p>
     *
     * @param texture the texture backing this region
     */
    public TextureRegion(Texture texture) {
        this(texture, 0f, 0f, texture.getWidth(), texture.getHeight());
    }

    /**
     * <p>
     * Creates a {@code TextureRegion} describing a subsection of a texture.
     * </p>
     *
     * <p>
     * The supplied values define the rectangular area in pixel coordinates
     * relative to the texture.
     * </p>
     *
     * @param texture      the texture backing this region
     * @param regionX      the X coordinate of the region in pixels
     * @param regionY      the Y coordinate of the region in pixels
     * @param regionWidth  the width of the region in pixels
     * @param regionHeight the height of the region in pixels
     * @throws NullPointerException if {@code texture} is {@code null}
     */
    public TextureRegion(Texture texture, float regionX, float regionY, float regionWidth, float regionHeight) {
        if (texture == null) throw new NullPointerException("Texture cannot be null");

        this.texture = texture;
        this.regionX = regionX;
        this.regionY = regionY;
        this.regionWidth = regionWidth;
        this.regionHeight = regionHeight;
    }

    /**
     * <p>
     * Returns the texture associated with this region.
     * </p>
     *
     * @return the underlying texture
     */
    public Texture getTexture() {
        return texture;
    }

    /**
     * <p>
     * Returns the region's X coordinate in pixels.
     * </p>
     *
     * @return the region X position
     */
    public float getRegionX() {
        return regionX;
    }

    /**
     * <p>
     * Sets the region's X coordinate.
     * </p>
     *
     * @param regionX the new region X position in pixels
     */
    public void setRegionX(float regionX) {
        this.regionX = regionX;
    }

    /**
     * <p>
     * Returns the region's Y coordinate in pixels.
     * </p>
     *
     * @return the region Y position
     */
    public float getRegionY() {
        return regionY;
    }

    /**
     * <p>
     * Sets the region's Y coordinate.
     * </p>
     *
     * @param regionY the new region Y position in pixels
     */
    public void setRegionY(float regionY) {
        this.regionY = regionY;
    }

    /**
     * <p>
     * Returns the region width in pixels.
     * </p>
     *
     * @return the region width
     */
    public float getRegionWidth() {
        return regionWidth;
    }

    /**
     * <p>
     * Sets the region width.
     * </p>
     *
     * @param regionWidth the new region width in pixels
     */
    public void setRegionWidth(float regionWidth) {
        this.regionWidth = regionWidth;
    }

    /**
     * <p>
     * Returns the region height in pixels.
     * </p>
     *
     * @return the region height
     */
    public float getRegionHeight() {
        return regionHeight;
    }

    /**
     * <p>
     * Sets the region height.
     * </p>
     *
     * @param regionHeight the new region height in pixels
     */
    public void setRegionHeight(float regionHeight) {
        this.regionHeight = regionHeight;
    }

    /**
     * <p>
     * Updates the full region bounds at once.
     * </p>
     *
     * <p>
     * This method replaces the X, Y, width, and height values in a single call.
     * </p>
     *
     * @param regionX      the new region X coordinate
     * @param regionY      the new region Y coordinate
     * @param regionWidth  the new region width
     * @param regionHeight the new region height
     */
    public void setRegion(float regionX, float regionY, float regionWidth, float regionHeight) {
        this.regionX = regionX;
        this.regionY = regionY;
        this.regionWidth = regionWidth;
        this.regionHeight = regionHeight;
    }

    /**
     * <p>
     * Returns the right edge of the region in pixel space.
     * </p>
     *
     * @return the right coordinate
     */
    public float getRight() {
        return regionX + regionWidth;
    }

    /**
     * <p>
     * Returns the bottom edge of the region in pixel space.
     * </p>
     *
     * @return the bottom coordinate
     */
    public float getBottom() {
        return regionY + regionHeight;
    }

    /**
     * <p>
     * Returns the normalized U coordinate for the left side of the region.
     * </p>
     *
     * <p>
     * This value is calculated by dividing the region's X position by the
     * full texture width.
     * </p>
     *
     * @return the normalized U coordinate
     */
    public float getU() {
        return regionX / texture.getWidth();
    }

    /**
     * <p>
     * Returns the normalized V coordinate for the top side of the region.
     * </p>
     *
     * <p>
     * This value is calculated by dividing the region's Y position by the
     * full texture height.
     * </p>
     *
     * @return the normalized V coordinate
     */
    public float getV() {
        return regionY / texture.getHeight();
    }

    /**
     * <p>
     * Returns the normalized U coordinate for the right side of the region.
     * </p>
     *
     * @return the normalized U2 coordinate
     */
    public float getU2() {
        return (regionX + regionWidth) / texture.getWidth();
    }

    /**
     * <p>
     * Returns the normalized V coordinate for the bottom side of the region.
     * </p>
     *
     * @return the normalized V2 coordinate
     */
    public float getV2() {
        return (regionY + regionHeight) / texture.getHeight();
    }
}