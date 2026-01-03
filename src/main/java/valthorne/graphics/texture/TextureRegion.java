package valthorne.graphics.texture;

/**
 * Represents a rectangular sub-region of a {@link Texture} object, allowing
 * for specific portions of the texture to be defined, manipulated, and drawn.
 * A {@code TextureRegion} can define both the region of the texture to use
 * and the size and position of its rendered output.
 *
 * @author Albert Beaupre
 * @since December 22nd, 2025
 */
public class TextureRegion {

    private final Texture texture; // The backing texture for this region
    private float regionX, regionY; // The x,y coordinates of the region within the texture
    private float regionWidth, regionHeight; // The width and height dimensions of the region
    private float x, y;
    private float width, height; // The rendered output dimensions

    /**
     * Creates a region that spans the entire texture.
     *
     * @param texture the backing texture
     */
    public TextureRegion(Texture texture) {
        this(texture, 0, 0, (int) texture.getWidth(), (int) texture.getHeight());
    }

    /**
     * Constructs a {@code TextureRegion} that represents a specific sub-region
     * of the provided {@code Texture} object, based on the specified parameters.
     *
     * @param texture      the backing texture from which the region is defined
     * @param regionX      the x-coordinate in pixels of the starting point of the region
     * @param regionY      the y-coordinate in pixels of the starting point of the region
     * @param regionWidth  the width in pixels of the defined region
     * @param regionHeight the height in pixels of the defined region
     */
    public TextureRegion(Texture texture, float regionX, float regionY, float regionWidth, float regionHeight) {
        this.texture = texture;
        this.setRegion(regionX, regionY, regionWidth, regionHeight);
    }

    /**
     * Sets the specific region of the texture to be used or drawn by this {@code TextureRegion}.
     *
     * @param regionX      the x-coordinate in pixels of the starting point of the region
     * @param regionY      the y-coordinate in pixels of the starting point of the region
     * @param regionWidth  the width in pixels of the defined region
     * @param regionHeight the height in pixels of the defined region
     */
    public void setRegion(float regionX, float regionY, float regionWidth, float regionHeight) {
        this.regionX = regionX;
        this.regionY = regionY;
        this.regionWidth = regionWidth;
        this.regionHeight = regionHeight;
    }

    /**
     * Draws the configured region of the texture to the screen.
     * <p>
     * This method performs the following actions:
     * 1. Configures the texture to represent the region specified by the region coordinates and dimensions.
     * 2. Sets the size of the texture to the specified width and height.
     * 3. Renders the textured quad to the screen.
     * <p>
     * The texture region is defined by {@code regionX}, {@code regionY}, {@code regionWidth}, and {@code regionHeight},
     * while the output size is determined by {@code width} and {@code height}.
     * <p>
     * Internally, it updates the texture region and size using {@code texture.setRegion} and {@code texture.setSize},
     * then uses {@code texture.draw} to render the texture.
     */
    public void draw() {
        texture.setRegion(regionX, regionY, regionWidth, regionHeight);
        texture.setSize(width, height);
        texture.setPosition(x, y);
        texture.draw();
    }

    /**
     * Sets the width and height dimensions of this {@code TextureRegion}.
     *
     * @param width  the width to set for this {@code TextureRegion}
     * @param height the height to set for this {@code TextureRegion}
     */
    public void setSize(float width, float height) {
        this.width = width;
        this.height = height;
    }

    /**
     * Sets the position of this {@code TextureRegion} by specifying its x and y coordinates.
     *
     * @param x the x-coordinate to set for this {@code TextureRegion}
     * @param y the y-coordinate to set for this {@code TextureRegion}
     */
    public void setPosition(float x, float y) {
        this.x = x;
        this.y = y;
    }

    /**
     * Retrieves the width of this {@code TextureRegion}.
     *
     * @return the width of the texture region in pixels.
     */
    public float getWidth() {
        return width;
    }

    /**
     * Retrieves the height of this {@code TextureRegion}.
     *
     * @return the height of the texture region in pixels.
     */
    public float getHeight() {
        return height;
    }

    /**
     * Retrieves the x-coordinate of this {@code TextureRegion}.
     *
     * @return the x-coordinate of the texture region in pixels.
     */
    public float getX() {
        return x;
    }

    /**
     * Retrieves the y-coordinate of this {@code TextureRegion}.
     *
     * @return the y-coordinate of the texture region in pixels.
     */
    public float getY() {
        return y;
    }

    /**
     * Retrieves the x-coordinate in pixels of the starting point of the texture region.
     *
     * @return the x-coordinate of the texture region in pixels.
     */
    public float getRegionX() {
        return regionX;
    }

    /**
     * Retrieves the y-coordinate in pixels of the starting point of the texture region.
     *
     * @return the y-coordinate of the texture region in pixels.
     */
    public float getRegionY() {
        return regionY;
    }

    /**
     * Retrieves the backing {@code Texture} object associated with this {@code TextureRegion}.
     *
     * @return the {@code Texture} object that this {@code TextureRegion} is using.
     */
    public Texture getTexture() {
        return texture;
    }
}
