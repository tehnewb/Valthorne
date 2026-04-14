package valthorne.graphics.lighting;

import valthorne.graphics.Color;
import valthorne.math.Vector2f;

public final class ConeLight extends VertexCastLight {

    private static final float TWO_PI = (float) (Math.PI * 2.0);

    private float directionRadians;
    private float coneRadians;

    public ConeLight(RayHandler rayHandler, int rays, Color color, float distance, float x, float y, float directionDegrees, float coneDegrees) {
        super(rayHandler, rays, color, distance, x, y);
        setDirectionDegrees(directionDegrees);
        setConeDegrees(coneDegrees);
    }

    @Override
    public void update() {
        if (!active || !dirty) {
            return;
        }

        float start = directionRadians - coneRadians * 0.5f;
        float end = directionRadians + coneRadians * 0.5f;

        resetAngles();
        addBaseAngles(start, end);
        addBoundaryAngles(start, end);
        addOccluderVertexAngles(start, end);
        rebuildFromAngles();
    }

    @Override
    protected void computeRayEnd(int index, float[] output) {
        float start = directionRadians - coneRadians * 0.5f;
        float t = rays <= 1 ? 0.5f : index / (float) (rays - 1);
        float angle = start + coneRadians * t;
        output[0] = x + (float) Math.cos(angle) * distance;
        output[1] = y + (float) Math.sin(angle) * distance;
    }

    private void addBaseAngles(float start, float end) {
        int baseCount = Math.max(3, rays);
        for (int i = 0; i < baseCount; i++) {
            float t = baseCount == 1 ? 0.5f : i / (float) (baseCount - 1);
            addAngle(start + (end - start) * t);
        }
    }

    private void addBoundaryAngles(float start, float end) {
        addAngle(start);
        addAngle(start + EPSILON);
        addAngle(end - EPSILON);
        addAngle(end);
    }

    private void addOccluderVertexAngles(float start, float end) {
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

                float angle = unwrapNear((float) Math.atan2(dy, dx), directionRadians);
                addAngleIfInside(angle - EPSILON, start, end);
                addAngleIfInside(angle, start, end);
                addAngleIfInside(angle + EPSILON, start, end);
            }
        }
    }

    private void addAngleIfInside(float angle, float start, float end) {
        if (angle >= start && angle <= end) {
            addAngle(angle);
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
