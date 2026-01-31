package valthorne.graphics.texture;

import valthorne.graphics.Drawable;

/**
 * A drawable implementation that allows rendering a {@link TextureRegion} to a specified
 * position and size. This class wraps a {@code TextureRegion} and provides a mechanism
 * to draw it with given dimensions and coordinates.
 * <p>
 * The primary usage involves setting the position and size of the texture region
 * based on the provided parameters, then delegating the actual drawing process
 * to the underlying {@code TextureRegion}.
 *
 * @author Albert Beaupre
 * @since December 22nd, 2025
 */
public record TextureRegionDrawable(TextureRegion region) implements Drawable {

    /**
     * Creates a new {@code TextureRegionDrawable} that wraps the specified
     * {@link TextureRegion}, allowing it to be drawn with specified dimensions
     * and coordinates.
     *
     * @param region the {@code TextureRegion} to be wrapped and rendered by this
     *               drawable; must not be null
     */
    public TextureRegionDrawable {
    }

    @Override
    public void draw(float x, float y, float width, float height) {
        region.setPosition(x, y);
        region.setSize(width, height);
        region.draw();
    }

    @Override
    public float getWidth() {
        return region.getWidth();
    }

    @Override
    public float getHeight() {
        return region.getHeight();
    }

    /**
     * Returns the {@link TextureRegion} wrapped by this drawable. The {@code TextureRegion}
     * represents the rectangular portion of a texture being drawn.
     *
     * @return the {@code TextureRegion} associated with this drawable
     */
    @Override
    public TextureRegion region() {
        return region;
    }
}
