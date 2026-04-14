package valthorne.graphics.lighting;

import valthorne.graphics.Color;
import valthorne.math.Vector2f;

import java.util.Arrays;

abstract class VertexCastLight extends Light {

    protected static final float EPSILON = 0.0002f;
    protected static final float MIN_ANGLE_DELTA = 0.0001f;

    private float[] angles = new float[64];
    private int angleCount;

    protected VertexCastLight(RayHandler rayHandler, int rays, Color color, float distance, float x, float y) {
        super(rayHandler, rays, color, distance, x, y);
    }

    protected final void resetAngles() {
        angleCount = 0;
    }

    protected final void addAngle(float angle) {
        ensureAngleCapacity(angleCount + 1);
        angles[angleCount++] = angle;
    }

    protected final float angleAt(int index) {
        return angles[index];
    }

    protected final int getAngleCount() {
        return angleCount;
    }

    protected final void sortAndCompactAngles() {
        if (angleCount <= 1) {
            return;
        }

        Arrays.sort(angles, 0, angleCount);

        int write = 1;
        float last = angles[0];

        for (int read = 1; read < angleCount; read++) {
            float current = angles[read];
            if (Math.abs(current - last) >= MIN_ANGLE_DELTA) {
                angles[write++] = current;
                last = current;
            }
        }

        angleCount = write;
    }

    protected final void rebuildFromAngles() {
        sortAndCompactAngles();
        ensureRayCapacity(angleCount);

        for (int i = 0; i < angleCount; i++) {
            float angle = angles[i];
            float targetX = x + (float) Math.cos(angle) * distance;
            float targetY = y + (float) Math.sin(angle) * distance;
            applyRayResult(i, targetX, targetY);
        }

        dirty = false;
    }

    protected final boolean isPotentialOccluder(Vector2f[] points, float maxDistanceSquared) {
        if (points == null) {
            return false;
        }

        for (Vector2f point : points) {
            if (point == null) {
                continue;
            }

            float dx = point.getX() - x;
            float dy = point.getY() - y;
            float distanceSquared = dx * dx + dy * dy;
            if (distanceSquared <= maxDistanceSquared) {
                return true;
            }
        }

        return false;
    }

    private void ensureAngleCapacity(int count) {
        if (angles.length >= count) {
            return;
        }

        int newSize = angles.length;
        while (newSize < count) {
            newSize <<= 1;
        }
        angles = Arrays.copyOf(angles, newSize);
    }
}
