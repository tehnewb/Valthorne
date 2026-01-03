package valthorne.graphics.texture;

import valthorne.graphics.Drawable;

/**
 * A drawable implementation that supports rendering a nine-patch texture.
 * This class is useful for drawing resizable images with stretchable areas, often used
 * in UI components.
 * <p>
 * The {@code NinePatchDrawable} wraps a {@code NinePatchTexture} to handle the
 * rendering details of the nine-patch image.
 * <p>
 * The nine-patch technique allows specific portions of an image
 * to stretch or repeat, enabling dynamic resizing while maintaining
 * visual integrity of non-stretchable regions.
 * <p>
 * The dimensions and position for drawing the nine-patch texture can
 * be adjusted dynamically via the {@code draw(float x, float y, float width, float height)}
 * method, in accordance with the {@code Drawable} interface.
 *
 * @author Albert Beaupre
 * @since December 22nd, 2025
 */
public class NinePatchDrawable implements Drawable {

    private final NinePatchTexture texture;

    /**
     * Constructs a new {@code NinePatchDrawable} instance using the specified nine-patch texture.
     *
     * @param texture the {@code NinePatchTexture} to be used for rendering. This texture
     *                defines the stretchable and non-stretchable regions of the image
     *                and is required for the {@code NinePatchDrawable}.
     */
    public NinePatchDrawable(NinePatchTexture texture) {
        this.texture = texture;
    }

    @Override
    public void draw(float x, float y, float width, float height) {
        texture.setPosition(x, y);
        texture.setSize(width, height);
        texture.draw();
    }

    @Override
    public float getWidth() {
        return texture.getWidth();
    }

    @Override
    public float getHeight() {
        return texture.getHeight();
    }

    /**
     * Retrieves the nine-patch texture associated with this drawable.
     *
     * @return the {@code NinePatchTexture} used by this {@code NinePatchDrawable}.
     */
    public NinePatchTexture getTexture() {
        return texture;
    }

}
