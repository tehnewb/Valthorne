package valthorne.graphics.lighting;

import valthorne.math.Vector2f;
import valthorne.math.geometry.Area;

public final class LightOccluder {

    private final Area area;
    private final Object collider;
    private int categoryBits;

    public LightOccluder(Area area) {
        this(area, area, Light.ALL_MASK_BITS);
    }

    public LightOccluder(Area area, Object collider) {
        this(area, collider, Light.ALL_MASK_BITS);
    }

    public LightOccluder(Area area, Object collider, int categoryBits) {
        if (area == null) throw new NullPointerException("area cannot be null");
        this.area = area;
        this.collider = (collider == null) ? area : collider;
        this.categoryBits = categoryBits;
    }

    public Area getArea() {
        return area;
    }

    public Object getCollider() {
        return collider;
    }

    public Vector2f[] points() {
        return area.points();
    }

    public int getCategoryBits() {
        return categoryBits;
    }

    public void setCategoryBits(int categoryBits) {
        this.categoryBits = categoryBits;
    }

    public boolean blocks(Light light) {
        return light == null || (categoryBits & light.getOcclusionMaskBits()) != 0;
    }
}
