package valthorne.graphics.texture;

import valthorne.graphics.Color;
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
 * be adjusted dynamically via the {@code draw(TextureBatch, float x, float y, float width, float height)}
 * method, in accordance with the {@code Drawable} interface.
 *
 * @author Albert Beaupre
 * @since December 22nd, 2025
 */
public record NinePatchDrawable(NinePatchTexture texture) implements Drawable {

    @Override
    public void draw(TextureBatch batch, float x, float y, float width, float height) {
        if (batch == null) throw new NullPointerException("batch");

        TextureData data = texture.getData();
        if (data == null) return;

        float texW = data.width();
        float texH = data.height();

        if (texW <= 0f || texH <= 0f) return;

        float left = texture.getLeft();
        float right = texture.getRight();
        float top = texture.getTop();
        float bottom = texture.getBottom();

        float centerW = Math.max(0f, width - left - right);
        float centerH = Math.max(0f, height - top - bottom);

        float x1 = x + left;
        float x2 = x + left + centerW;
        float y1 = y + bottom;
        float y2 = y + bottom + centerH;
        float srcX2 = texW - right;
        float srcY2 = texH - top;

        Color tint = texture.getColor();

        batch.drawRegion(texture, x, y, left, bottom, 0, 0, left, bottom, tint);
        batch.drawRegion(texture, x1, y, centerW, bottom, left, 0, srcX2 - left, bottom, tint);
        batch.drawRegion(texture, x2, y, right, bottom, srcX2, 0, right, bottom, tint);

        batch.drawRegion(texture, x, y1, left, centerH, 0, bottom, left, srcY2 - bottom, tint);
        batch.drawRegion(texture, x1, y1, centerW, centerH, left, bottom, srcX2 - left, srcY2 - bottom, tint);
        batch.drawRegion(texture, x2, y1, right, centerH, srcX2, bottom, right, srcY2 - bottom, tint);

        batch.drawRegion(texture, x, y2, left, top, 0, srcY2, left, top, tint);
        batch.drawRegion(texture, x1, y2, centerW, top, left, srcY2, srcX2 - left, top, tint);
        batch.drawRegion(texture, x2, y2, right, top, srcX2, srcY2, right, top, tint);
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
    @Override
    public NinePatchTexture texture() {
        return texture;
    }

}
