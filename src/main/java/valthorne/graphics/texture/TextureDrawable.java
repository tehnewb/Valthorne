package valthorne.graphics.texture;

import valthorne.graphics.Drawable;

/**
 * A drawable implementation that renders a texture on the screen.
 * <p>
 * This class allows textures to be positioned and resized before being rendered.
 * It relies on the functionality of the provided {@code Texture} instance to manage
 * its drawing behavior.
 *
 * @author Albert Beaupre
 * @since December 22nd, 2025
 */
public record TextureDrawable(Texture texture) implements Drawable {

    /**
     * Constructs a new {@code TextureDrawable} with the specified texture.
     *
     * @param texture the texture to be rendered by this drawable
     */
    public TextureDrawable {
    }

    @Override
    public void draw(TextureBatch batch, float x, float y, float width, float height) {
        batch.draw(texture, x, y, width, height);
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
     * Returns the texture associated with this drawable.
     *
     * @return the texture used for rendering
     */
    @Override
    public Texture texture() {
        return texture;
    }

}
