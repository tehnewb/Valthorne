package valthorne.graphics.lighting;

import valthorne.graphics.Color;
import valthorne.math.Vector2f;
import valthorne.math.geometry.Area;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class PolygonLight extends Light {

    private static final float EPSILON = 0.0002f;
    private static final float MIN_ANGLE_DELTA = 0.0001f;

    private final List<Float> angles = new ArrayList<>();

    public PolygonLight(RayHandler rayHandler, int rays, Color color, float distance, float x, float y) {
        super(rayHandler, rays, color, distance, x, y);
    }

    @Override
    public void update() {
        if (!active) return;
        if (!dirty) return;

        rebuildFromVertices();
        dirty = false;
    }

    @Override
    protected void computeRayEnd(int index, float[] output) {
        float angle = (float) (index * (Math.PI * 2.0) / rays);
        output[0] = x + (float) Math.cos(angle) * distance;
        output[1] = y + (float) Math.sin(angle) * distance;
    }

    private void rebuildFromVertices() {
        angles.clear();

        for (int i = 0; i < rays; i++) {
            angles.add((float) (i * (Math.PI * 2.0) / rays));
        }

        RayCastWorld world = rayHandler.getRayCastWorld();
        if (world instanceof ShapeRayCastWorld shapeWorld) {
            float maxDist2 = distance * distance;

            for (Area area : shapeWorld.getOccluders()) {
                Vector2f[] pts = area.points();
                if (pts == null || pts.length == 0) continue;
                if (!isPotentialOccluder(pts, maxDist2)) continue;

                for (Vector2f p : pts) {
                    if (p == null) continue;

                    float dx = p.getX() - x;
                    float dy = p.getY() - y;
                    float dist2 = dx * dx + dy * dy;
                    if (dist2 > maxDist2) continue;

                    float angle = (float) Math.atan2(dy, dx);
                    angles.add(angle - EPSILON);
                    angles.add(angle);
                    angles.add(angle + EPSILON);
                }
            }
        }

        Collections.sort(angles);
        compactAngles();

        ensureCapacity(angles.size());

        for (int i = 0; i < angles.size(); i++) {
            float angle = angles.get(i);

            float targetX = x + (float) Math.cos(angle) * distance;
            float targetY = y + (float) Math.sin(angle) * distance;

            if (xray || rayHandler.getRayCastWorld() == null) {
                endX[i] = targetX;
                endY[i] = targetY;
                fractions[i] = 1f;
            } else {
                RayCastHit hit = rayHandler.getRayCastWorld().rayCast(x, y, targetX, targetY);
                if (hit != null && hit.isHit()) {
                    endX[i] = hit.getX();
                    endY[i] = hit.getY();
                    fractions[i] = hit.getFraction();
                } else {
                    endX[i] = targetX;
                    endY[i] = targetY;
                    fractions[i] = 1f;
                }
            }
        }
    }

    private boolean isPotentialOccluder(Vector2f[] pts, float maxDist2) {
        for (Vector2f p : pts) {
            if (p == null) continue;
            float dx = p.getX() - x;
            float dy = p.getY() - y;
            float dist2 = dx * dx + dy * dy;
            if (dist2 <= maxDist2) {
                return true;
            }
        }
        return false;
    }

    private void compactAngles() {
        if (angles.isEmpty()) return;

        int write = 1;
        float last = angles.get(0);

        for (int read = 1; read < angles.size(); read++) {
            float current = angles.get(read);
            if (Math.abs(current - last) >= MIN_ANGLE_DELTA) {
                angles.set(write++, current);
                last = current;
            }
        }

        while (angles.size() > write) {
            angles.remove(angles.size() - 1);
        }
    }

    private void ensureCapacity(int count) {
        if (endX.length == count) return;

        endX = new float[count];
        endY = new float[count];
        fractions = new float[count];
        segments = new float[Math.max(18, count * 18)];

        for (int i = 0; i < count; i++) {
            fractions[i] = 1f;
        }
    }
}