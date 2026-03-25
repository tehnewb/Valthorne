package valthorne.graphics.texture;

import valthorne.graphics.Color;
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
     * Creates a new TextureDrawable instance using a file path to a texture.
     *
     * @param path the file path to the texture to be used for this drawable
     */
    public TextureDrawable(String path) {
        this(new Texture(path));
    }

    /**
     * Creates a new TextureDrawable instance using the specified texture data.
     *
     * @param data the {@link TextureData} object containing the decoded texture and its metadata
     */
    public TextureDrawable(TextureData data) {
        this(new Texture(data));
    }

    @Override
    public void draw(TextureBatch batch, float x, float y, float width, float height, float regionX, float regionY, float regionWidth, float regionHeight, float originX, float originY, float rotation, Color tint) {
        if (texture == null) return;
        batch.draw(texture, x, y, width, height, regionX, regionY, regionWidth, regionHeight, originX, originY, rotation, tint);
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
