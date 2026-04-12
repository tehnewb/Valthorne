package valthorne.graphics.lights;

import valthorne.graphics.Color;
import valthorne.graphics.lighting.*;
import valthorne.math.Vector2f;
import valthorne.math.geometry.Shape;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ConeLight extends Light {

    private static final float EPSILON = 0.0002f;
    private static final float MIN_ANGLE_DELTA = 0.0001f;
    private static final float TWO_PI = (float) (Math.PI * 2.0);

    private final List<Float> angles = new ArrayList<>();

    private float directionRadians;
    private float coneRadians;

    public ConeLight(RayHandler rayHandler, int rays, Color color, float distance, float x, float y, float directionDegrees, float coneDegrees) {
        super(rayHandler, rays, color, distance, x, y);
        setDirectionDegrees(directionDegrees);
        setConeDegrees(coneDegrees);
    }

    @Override
    public void update() {
        if (!active) return;
        if (!dirty) return;
        rebuildFromVertices();
    }

    @Override
    protected void computeRayEnd(int index, float[] output) {
        float start = directionRadians - coneRadians * 0.5f;
        float t = rays <= 1 ? 0.5f : index / (float) (rays - 1);
        float angle = start + coneRadians * t;
        output[0] = x + (float) Math.cos(angle) * distance;
        output[1] = y + (float) Math.sin(angle) * distance;
    }

    private void rebuildFromVertices() {
        angles.clear();

        float start = directionRadians - coneRadians * 0.5f;
        float end = directionRadians + coneRadians * 0.5f;

        addBaseAngles(start, end);
        addBoundaryAngles(start, end);
        addShapeVertexAngles(start, end);

        Collections.sort(angles);
        compactAngles();
        ensureCapacity(angles.size());

        RayCastWorld world = rayHandler.getRayCastWorld();

        for (int i = 0; i < angles.size(); i++) {
            float angle = angles.get(i);
            float targetX = x + (float) Math.cos(angle) * distance;
            float targetY = y + (float) Math.sin(angle) * distance;

            if (xray || world == null) {
                endX[i] = targetX;
                endY[i] = targetY;
                fractions[i] = 1f;
            } else {
                RayCastHit hit = world.rayCast(x, y, targetX, targetY);
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

        buildSegments();
        dirty = false;
    }

    private void addBaseAngles(float start, float end) {
        int baseCount = Math.max(3, rays);
        for (int i = 0; i < baseCount; i++) {
            float t = baseCount == 1 ? 0.5f : i / (float) (baseCount - 1);
            angles.add(start + (end - start) * t);
        }
    }

    private void addBoundaryAngles(float start, float end) {
        angles.add(start);
        angles.add(start + EPSILON);
        angles.add(end - EPSILON);
        angles.add(end);
    }

    private void addShapeVertexAngles(float start, float end) {
        RayCastWorld world = rayHandler.getRayCastWorld();
        if (!(world instanceof DefaultRayCastWorld shapeWorld)) {
            return;
        }

        float maxDist2 = distance * distance;

        for (Shape shape : shapeWorld.getShapes()) {
            if (shape == null) {
                continue;
            }

            Vector2f[] pts = shape.points();
            if (pts == null || pts.length == 0) {
                continue;
            }

            if (!isPotentialOccluder(pts, maxDist2)) {
                continue;
            }

            for (Vector2f p : pts) {
                if (p == null) {
                    continue;
                }

                float dx = p.getX() - x;
                float dy = p.getY() - y;
                float dist2 = dx * dx + dy * dy;
                if (dist2 > maxDist2) {
                    continue;
                }

                float angle = unwrapNear((float) Math.atan2(dy, dx), directionRadians);

                addAngleIfInside(angle - EPSILON, start, end);
                addAngleIfInside(angle, start, end);
                addAngleIfInside(angle + EPSILON, start, end);
            }
        }
    }

    private boolean isPotentialOccluder(Vector2f[] pts, float maxDist2) {
        for (Vector2f p : pts) {
            if (p == null) {
                continue;
            }

            float dx = p.getX() - x;
            float dy = p.getY() - y;
            float dist2 = dx * dx + dy * dy;

            if (dist2 <= maxDist2) {
                return true;
            }
        }

        return false;
    }

    private void addAngleIfInside(float angle, float start, float end) {
        if (angle >= start && angle <= end) {
            angles.add(angle);
        }
    }

    private float unwrapNear(float angle, float reference) {
        while (angle - reference > Math.PI) {
            angle -= TWO_PI;
        }
        while (angle - reference < -Math.PI) {
            angle += TWO_PI;
        }
        return angle;
    }

    private void compactAngles() {
        if (angles.isEmpty()) {
            return;
        }

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
        if (endX.length == count) {
            return;
        }

        endX = new float[count];
        endY = new float[count];
        fractions = new float[count];
        segments = new float[Math.max(18, count * 18)];

        for (int i = 0; i < count; i++) {
            fractions[i] = 1f;
        }
    }

    public float getDirectionRadians() {
        return directionRadians;
    }

    public float getDirectionDegrees() {
        return (float) Math.toDegrees(directionRadians);
    }

    public void setDirectionRadians(float directionRadians) {
        this.directionRadians = normalize(directionRadians);
        dirty = true;
    }

    public void setDirectionDegrees(float directionDegrees) {
        setDirectionRadians((float) Math.toRadians(directionDegrees));
    }

    public void rotateRadians(float deltaRadians) {
        setDirectionRadians(directionRadians + deltaRadians);
    }

    public void rotateDegrees(float deltaDegrees) {
        rotateRadians((float) Math.toRadians(deltaDegrees));
    }

    public float getConeRadians() {
        return coneRadians;
    }

    public float getConeDegrees() {
        return (float) Math.toDegrees(coneRadians);
    }

    public void setConeRadians(float coneRadians) {
        float min = (float) Math.toRadians(1f);
        float max = (float) Math.toRadians(179f);
        this.coneRadians = Math.max(min, Math.min(max, coneRadians));
        dirty = true;
    }

    public void setConeDegrees(float coneDegrees) {
        setConeRadians((float) Math.toRadians(coneDegrees));
    }

    private float normalize(float angle) {
        angle %= TWO_PI;
        if (angle < 0f) {
            angle += TWO_PI;
        }
        return angle;
    }
}