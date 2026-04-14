package valthorne.graphics.lighting;

import valthorne.graphics.Color;
import valthorne.math.Vector2f;

public final class PolygonLight extends VertexCastLight {

    public PolygonLight(RayHandler rayHandler, int rays, Color color, float distance, float x, float y) {
        super(rayHandler, rays, color, distance, x, y);
    }

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

                float dx = point.getX() - x;
                float dy = point.getY() - y;
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

    @Override
    protected void computeRayEnd(int index, float[] output) {
        float angle = (float) (index * (Math.PI * 2.0) / rays);
        output[0] = x + (float) Math.cos(angle) * distance;
        output[1] = y + (float) Math.sin(angle) * distance;
    }
}
