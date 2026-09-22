package valthorne.graphics.lighting2d;

import valthorne.graphics.Sprite;

import java.util.Objects;

/**
 * Upright sprite card projected onto XY ground by an elevated light.
 * Borrows the sprite and its live texture; no pixel extraction or GPU ownership.
 * Set the foot anchor and the top of the visible artwork in normalized frame coordinates
 * (bottom-up), excluding transparent canvas padding. Does not register a polar occluder.
 */
public final class SpriteGroundShadow2D {
    final Sprite sprite;
    float anchorU = .5f, anchorV = 0, topV = 1, height = 50, maxLength = 150, opacity = .42f;
    boolean enabled = true;

    public SpriteGroundShadow2D(Sprite sprite) {
        this.sprite = Objects.requireNonNull(sprite);
    }

    public Sprite getSprite() {
        return sprite;
    }

    public SpriteGroundShadow2D setHeight(float value) {
        PointLight2D.nonnegative(value);
        height = value;
        return this;
    }

    public SpriteGroundShadow2D setMaxLength(float value) {
        PointLight2D.nonnegative(value);
        maxLength = value;
        return this;
    }

    public SpriteGroundShadow2D setOpacity(float value) {
        PointLight2D.nonnegative(value);
        if (value > 1) throw new IllegalArgumentException("Opacity exceeds one");
        opacity = value;
        return this;
    }

    public SpriteGroundShadow2D setEnabled(boolean value) {
        enabled = value;
        return this;
    }

    /**
     * Physical foot position and artwork top; stays fixed throughout an animation.
     */
    public SpriteGroundShadow2D setFrameAnchors(float footU, float footV, float artworkTopV) {
        PointLight2D.finite(footU);
        PointLight2D.finite(footV);
        PointLight2D.finite(artworkTopV);
        if (footU < 0 || footU > 1 || footV < 0 || artworkTopV > 1 || footV >= artworkTopV)
            throw new IllegalArgumentException("Invalid normalized sprite anchors");
        anchorU = footU;
        anchorV = footV;
        topV = artworkTopV;
        return this;
    }

    /**
     * Bounded projection height prevents horizons/screen-length shadows near low lights.
     */
    static float boundedHeight(float objectHeight, float lightHeight, float distance, float limit) {
        if (lightHeight <= 0 || limit <= 0) return 0;
        return Math.min(objectHeight, Math.min(lightHeight * .8f, lightHeight * (limit / (distance + limit))));
    }
}
