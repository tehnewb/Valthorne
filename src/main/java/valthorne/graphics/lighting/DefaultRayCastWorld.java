package valthorne.graphics.lighting;

import valthorne.math.Vector2f;
import valthorne.math.geometry.Shape;

import java.util.ArrayList;
import java.util.List;

public class DefaultRayCastWorld implements RayCastWorld {

    private static final float EPSILON = 0.00001f;

    private final List<Shape> shapes = new ArrayList<>();
    private Shape moving;

    public void addShape(Shape shape) {
        if (shape != null) {
            shapes.add(shape);
        }
    }

    public void clear() {
        shapes.clear();
        moving = null;
    }

    public List<Shape> getShapes() {
        return shapes;
    }

    public Shape getMoving() {
        return moving;
    }

    public void setMoving(Shape moving) {
        this.moving = moving;
    }

    @Override
    public RayCastHit rayCast(float startX, float startY, float endX, float endY) {
        float closestFraction = Float.MAX_VALUE;
        Shape closestShape = null;

        for (int i = 0, n = shapes.size(); i < n; i++) {
            Shape shape = shapes.get(i);

            float hitFraction = rayVsShape(startX, startY, endX, endY, shape, closestFraction);
            if (hitFraction < closestFraction) {
                closestFraction = hitFraction;
                closestShape = shape;
            }
        }

        if (closestShape == null) {
            return null;
        }

        float dx = endX - startX;
        float dy = endY - startY;

        RayCastHit hit = new RayCastHit(startX + dx * closestFraction, startY + dy * closestFraction);
        hit.setFraction(closestFraction);
        hit.setCollider(closestShape);
        return hit;
    }

    private float rayVsShape(float x1, float y1, float x2, float y2, Shape shape, float maxFraction) {
        Vector2f[] pts = shape.points();
        if (pts == null || pts.length < 2) {
            return Float.MAX_VALUE;
        }

        float closestFraction = maxFraction;

        Vector2f prev = pts[pts.length - 1];
        for (int i = 0; i < pts.length; i++) {
            Vector2f curr = pts[i];

            if (prev != null && curr != null) {
                float hitFraction = intersectSegment(
                        x1, y1, x2, y2,
                        prev.getX(), prev.getY(),
                        curr.getX(), curr.getY(),
                        closestFraction
                );

                if (hitFraction < closestFraction) {
                    closestFraction = hitFraction;
                }
            }

            prev = curr;
        }

        return closestFraction;
    }

    private float intersectSegment(
            float x1, float y1, float x2, float y2,
            float x3, float y3, float x4, float y4,
            float maxFraction
    ) {
        float rX = x2 - x1;
        float rY = y2 - y1;
        float sX = x4 - x3;
        float sY = y4 - y3;

        float denom = rX * sY - rY * sX;
        if (Math.abs(denom) < EPSILON) {
            return Float.MAX_VALUE;
        }

        float qpx = x3 - x1;
        float qpy = y3 - y1;

        float t = (qpx * sY - qpy * sX) / denom;
        if (t < 0f || t > maxFraction) {
            return Float.MAX_VALUE;
        }

        float u = (qpx * rY - qpy * rX) / denom;
        if (u < -EPSILON || u > 1f + EPSILON) {
            return Float.MAX_VALUE;
        }

        return t;
    }
}