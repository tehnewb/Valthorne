package valthorne.graphics.lighting;

import valthorne.graphics.Color;

/**
 * Casts evenly spaced rays around a full circle to form a radial light footprint.
 * Active dirty lights rebuild endpoints; unchanged or inactive lights retain their
 * previous geometry. Occlusion and x-ray behavior follow the shared Light policy.
 *
 * @author Albert Beaupre
 */
public final class PointLight extends Light {

    /**
     * Initializes shared light state and endpoint storage. The constructor retains the
     * handler but does not register this light with its render list; geometry is rebuilt
     * by update when active and dirty.
     *
     * @param rayHandler handler providing the occlusion world
     * @param rays base ray count, at least three
     * @param color color copied into the light
     * @param distance radial extent in world units
     * @param x world-space center X
     * @param y world-space center Y
     * @throws NullPointerException if handler or color is null
     * @throws IllegalArgumentException if rays is below three
     */
    public PointLight(RayHandler rayHandler, int rays, Color color, float distance, float x, float y) {
        super(rayHandler, rays, color, distance, x, y);
    }

    /**
     * Recasts evenly spaced endpoints when active and dirty. Their circular order is
     * already suitable for the fan, so the base rebuild skips endpoint sorting.
     */
    @Override
    public void update() {
        if (!active || !dirty) {
            return;
        }
        rebuild(false);
    }

    /**
     * Writes an unoccluded endpoint at index times one full turn divided by base rays.
     * Does not validate the index, array length, or radial extent.
     *
     * @param index ray index in circular order
     * @param output destination with at least two elements for world X/Y
     */
    @Override
    protected void computeRayEnd(int index, float[] output) {
        float angle = (float) (index * (Math.PI * 2.0) / rays);
        output[0] = x + (float) Math.cos(angle) * distance;
        output[1] = y + (float) Math.sin(angle) * distance;
    }
}
