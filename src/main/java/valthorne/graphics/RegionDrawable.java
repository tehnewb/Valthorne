package valthorne.graphics;

import valthorne.graphics.texture.TextureBatch;

/**
 * A rectangular view of a borrowed drawable. Source coordinates are relative to
 * the wrapped drawable; nested views add their offsets when drawing. The source
 * must honor Drawable's source-rectangle arguments. No textures are uploaded,
 * no resources are owned, and drawing allocates nothing.
 *
 * @param drawable borrowed source, which must outlive this view
 * @param regionX nonnegative horizontal source offset
 * @param regionY nonnegative vertical source offset
 * @param regionWidth positive source width
 * @param regionHeight positive source height
 */
public record RegionDrawable(Drawable drawable, float regionX, float regionY, float regionWidth, float regionHeight) implements Drawable {
    /**
     * Validates a finite rectangle contained in the source drawable.
     *
     * @param drawable non-null borrowed source
     * @param regionX horizontal source offset
     * @param regionY vertical source offset
     * @param regionWidth source width
     * @param regionHeight source height
     */
    public RegionDrawable {
        if (drawable == null) throw new IllegalArgumentException("Source drawable cannot be null");
        float sourceWidth = drawable.getWidth();
        float sourceHeight = drawable.getHeight();
        if (!Float.isFinite(regionX) || !Float.isFinite(regionY) || !Float.isFinite(regionWidth) || !Float.isFinite(regionHeight)
                || regionX < 0 || regionY < 0 || regionWidth <= 0 || regionHeight <= 0
                || !Float.isFinite(sourceWidth) || !Float.isFinite(sourceHeight)
                || (double) regionX + regionWidth > sourceWidth || (double) regionY + regionHeight > sourceHeight)
            throw new IllegalArgumentException("Region must be finite, positive-sized and inside the drawable");
    }

    @Override
    public void draw(TextureBatch batch, float x, float y, float width, float height, float regionX, float regionY, float regionWidth, float regionHeight, float originX, float originY, float rotation, Color tint) {
        drawable.draw(batch, x, y, width, height, this.regionX + regionX, this.regionY + regionY, regionWidth, regionHeight, originX, originY, rotation, tint);
    }

    @Override
    public float getWidth() {
        return regionWidth;
    }

    @Override
    public float getHeight() {
        return regionHeight;
    }
}
