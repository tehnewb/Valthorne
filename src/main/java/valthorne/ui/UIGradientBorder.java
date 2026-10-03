package valthorne.ui;

import java.util.Objects;
import valthorne.graphics.Color;
import valthorne.graphics.Drawable;
import valthorne.graphics.texture.TextureBatch;

/**
 * Fixed-width rectangular outline backed by a shared {@link UIGradient}.
 * Assign this drawable to a regular control's surface style key to paint a
 * gradient border without rasterizing a new texture for each control size.
 * The gradient remains owned by the caller and may be shared with fills.
 */
public final class UIGradientBorder implements Drawable {
    private final UIGradient gradient; // Shared gradient used by all four sides.
    private final float thickness; // Inset border width in UI units.

    /**
     * Creates a drawable outline around its eventual bounds.
     *
     * @param gradient retained gradient; the caller owns its lifetime
     * @param thickness positive border width in UI units
     */
    public UIGradientBorder(UIGradient gradient, float thickness) {
        this.gradient = Objects.requireNonNull(gradient);
        if (!Float.isFinite(thickness) || thickness <= 0f)
            throw new IllegalArgumentException("Border thickness must be positive and finite.");
        this.thickness = thickness;
    }

    /**
     * Paints the border in the requested bounds. Region, rotation and tint are
     * unused because this outline samples the complete target-space gradient.
     */
    @Override
    public void draw(TextureBatch batch, float x, float y, float width, float height, float regionX, float regionY, float regionWidth, float regionHeight, float originX, float originY, float rotation, Color tint) {
        gradient.drawBorder(batch, x, y, width, height, thickness);
    }

    /**
     * Returns the unit intrinsic width required by the Drawable contract.
     *
     * @return one UI unit
     */
    @Override
    public float getWidth() {
        return 1f;
    }

    /**
     * Returns the unit intrinsic height required by the Drawable contract.
     *
     * @return one UI unit
     */
    @Override
    public float getHeight() {
        return 1f;
    }
}
