package valthorne.graphics.lighting;

import valthorne.graphics.Color;
import org.joml.Vector2f;

/**
 * Refines a radial light footprint by adding rays at and around nearby occluder
 * vertices. Base rays maintain circular coverage while extra angles improve corner
 * boundaries. Only vertices within the light radius contribute; an occluder with no
 * nearby vertex is omitted from this refinement even if an edge crosses the radius.
 *
 * @author Albert Beaupre
 */
public final class PolygonLight extends VertexCastLight {

    /**
     * Initializes shared light state and endpoint storage. The constructor retains the
     * handler but does not register this light with its render list; geometry is rebuilt
     * by update when active and dirty.
     *
     * @param rayHandler handler providing the occlusion world
     * @param rays       base ray count, at least three
     * @param color      color copied into the light
     * @param distance   radial extent in world units
     * @param x          world-space center X
     * @param y          world-space center Y
     * @throws NullPointerException     if handler or color is null
     * @throws IllegalArgumentException if rays is below three
     */
    public PolygonLight(RayHandler rayHandler, int rays, Color color, float distance, float x, float y) {
        super(rayHandler, rays, color, distance, x, y);
    }

    /**
     * When active and dirty, collects base circle angles plus three rays around each
     * eligible nearby occluder vertex. Filters category masks, sorts and compacts the
     * angles, casts endpoints, and clears dirty state. Skips inactive or clean lights.
     */
    @Override
    public void update() {
        if (!active || !dirty) {
            return;
        }

        resetAngles();

        for (int i = 0; i < rays; i++) {
            addAngle((float) (i * (Math.PI * 2.0) / rays));
        }

        float maxDistanceSquared = distance * distance;

        for (LightOccluder occluder : getLightOccluders()) {
            if (occluder == null || !occluder.blocks(this)) {
                continue;
            }

            Vector2f[] points = occluder.points();
            if (points == null || points.length == 0 || !isPotentialOccluder(points, maxDistanceSquared)) {
                continue;
            }

            for (Vector2f point : points) {
                if (point == null) {
                    continue;
                }

                float dx = point.x() - x;
                float dy = point.y() - y;
                float pointDistanceSquared = dx * dx + dy * dy;
                if (pointDistanceSquared > maxDistanceSquared) {
                    continue;
                }

                float angle = (float) Math.atan2(dy, dx);
                addAngle(angle - EPSILON);
                addAngle(angle);
                addAngle(angle + EPSILON);
            }
        }

        rebuildFromAngles();
    }

    /**
     * Writes an unoccluded endpoint at index times one full turn divided by base rays.
     * Does not validate the index, array length, or radial extent.
     *
     * @param index  ray index in circular order
     * @param output destination with at least two elements for world X/Y
     */
    @Override
    protected void computeRayEnd(int index, float[] output) {
        float angle = (float) (index * (Math.PI * 2.0) / rays);
        output[0] = x + (float) Math.cos(angle) * distance;
        output[1] = y + (float) Math.sin(angle) * distance;
    }
}
