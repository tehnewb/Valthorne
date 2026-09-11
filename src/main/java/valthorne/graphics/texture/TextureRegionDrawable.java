package valthorne.graphics.texture;

import valthorne.graphics.Color;
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
     * Creates a region wrapper over the supplied texture without allocating a new
     * GPU texture. The region uses source pixel coordinates; the caller remains
     * responsible for the backing texture's lifetime.
     *
     * @param texture backing texture
     * @param regionX horizontal source offset in pixels
     * @param regionY vertical source offset in pixels
     * @param regionWidth source width in pixels
     * @param regionHeight source height in pixels
     */
    public TextureRegionDrawable(Texture texture, float regionX, float regionY, float regionWidth, float regionHeight) {
        this(new TextureRegion(texture, regionX, regionY, regionWidth, regionHeight));
    }

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

    /**
     * Delegates a transformed region-relative subsection to the active batch.
     * A null region skips drawing; no resource ownership changes and no batch
     * lifecycle operations occur in this method.
     *
     * @param batch active destination texture batch
     * @param x horizontal destination position
     * @param y vertical destination position
     * @param width destination width in batch units
     * @param height destination height in batch units
     * @param regionX horizontal source offset in pixels
     * @param regionY vertical source offset in pixels
     * @param regionWidth source width in pixels
     * @param regionHeight source height in pixels
     * @param originX horizontal rotation-origin offset in destination units
     * @param originY vertical rotation-origin offset in destination units
     * @param rotation clockwise rotation in degrees
     * @param tint optional tint multiplier, or null for the drawable's default tint
     */
    @Override
    public void draw(TextureBatch batch, float x, float y, float width, float height, float regionX, float regionY, float regionWidth, float regionHeight, float originX, float originY, float rotation, Color tint) {
        if (region == null) return;

        batch.draw(region, x, y, width, height, regionX, regionY, regionWidth, regionHeight, originX, originY, rotation, tint);
    }

    /**
     * Returns the wrapped region's current pixel width without applying draw scaling.
     *
     * @return source width in pixels
     * @throws NullPointerException if the wrapped region is null
     */
    @Override
    public float getWidth() {
        return region.getRegionWidth();
    }

    /**
     * Returns the wrapped region's current pixel height without applying draw scaling.
     *
     * @return source height in pixels
     * @throws NullPointerException if the wrapped region is null
     */
    @Override
    public float getHeight() {
        return region.getRegionHeight();
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
