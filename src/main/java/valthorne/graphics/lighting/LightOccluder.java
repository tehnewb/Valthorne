package valthorne.graphics.lighting;

import org.joml.Vector2f;
import valthorne.math.geometry.Area;

/**
 * Associates a borrowed geometric area with a collider identity and light-blocking
 * category mask. Geometry queries delegate directly to the area, so changes to the
 * area affect future queries and point-array ownership follows that implementation.
 * This wrapper does not register itself with a world or manage collider lifetime.
 *
 * @author Albert Beaupre
 */
public final class LightOccluder {

    private final Area area; // Borrowed area defining occluder geometry.
    private final Object collider; // Borrowed application hit identity.
    private int categoryBits; // Membership mask used for light filtering.

    /**
     * Uses the area itself as collider identity and enables all category bits.
     *
     * @param area non-null borrowed geometry
     */
    public LightOccluder(Area area) {
        this(area, area, Light.ALL_MASK_BITS);
    }

    /**
     * Retains geometry and optional collider identity with all category bits enabled.
     * A null collider falls back to the area itself.
     *
     * @param area non-null borrowed geometry
     * @param collider application collision identity, or null
     */
    public LightOccluder(Area area, Object collider) {
        this(area, collider, Light.ALL_MASK_BITS);
    }

    /**
     * Retains the area and category bits without copying geometry. Null collider identity
     * is replaced by the area; zero category bits blocks no non-null light masks.
     *
     * @param area borrowed occlusion geometry
     * @param collider application identity, or null to use area
     * @param categoryBits occluder membership mask
     * @throws NullPointerException if area is null
     */
    public LightOccluder(Area area, Object collider, int categoryBits) {
        if (area == null) throw new NullPointerException("area cannot be null");
        this.area = area;
        this.collider = (collider == null) ? area : collider;
        this.categoryBits = categoryBits;
    }

    /**
     * Returns the live borrowed area. Geometry ownership stays with its original owner.
     *
     * @return occlusion area
     */
    public Area getArea() {
        return area;
    }

    /**
     * Returns the retained collider identity used to associate hits with application data.
     *
     * @return supplied collider or the area fallback
     */
    public Object getCollider() {
        return collider;
    }

    /**
     * Delegates to the area's points method without an additional copy. Point ordering,
     * allocation, and mutability follow the concrete Area implementation.
     *
     * @return area-provided vertices
     */
    public Vector2f[] points() {
        return area.points();
    }

    /**
     * Returns this occluder's membership mask without testing any light.
     *
     * @return category bit mask
     */
    public int getCategoryBits() {
        return categoryBits;
    }

    /**
     * Replaces category membership for subsequent blocking tests. Does not notify the
     * world or mark existing light geometry dirty.
     *
     * @param categoryBits replacement membership mask
     */
    public void setCategoryBits(int categoryBits) {
        this.categoryBits = categoryBits;
    }

    /**
     * Tests for any shared bit between this category mask and the light's occlusion mask.
     * A null light is treated as an unfiltered query and always returns true.
     *
     * @param light light supplying a mask, or null
     * @return whether this occluder is eligible to block the query
     */
    public boolean blocks(Light light) {
        return light == null || (categoryBits & light.getOcclusionMaskBits()) != 0;
    }
}
