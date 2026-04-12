package valthorne.graphics.lighting;

import valthorne.math.Vector2f;
import valthorne.math.geometry.Shape;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ShapeRaycastWorld implements RayCastWorld {

    private static final float EPSILON = 0.00001f;

    private final List<Shape> shapes = new ArrayList<>();
    private final List<LightOccluder> lightOccluders = new ArrayList<>();
    private Shape moving;

    public void addShape(Shape shape) {
        addShape(shape, Light.ALL_MASK_BITS);
    }

    public void addShape(Shape shape, int categoryBits) {
        if (shape == null) {
            return;
        }

        shapes.add(shape);
        lightOccluders.add(new LightOccluder(shape, shape, categoryBits));
    }

    public void removeShape(Shape shape) {
        if (shape == null) {
            return;
        }

        for (int i = shapes.size() - 1; i >= 0; i--) {
            if (shapes.get(i) == shape) {
                shapes.remove(i);
                lightOccluders.remove(i);
            }
        }

        if (moving == shape) {
            moving = null;
        }
    }

    public boolean setShapeCategoryBits(Shape shape, int categoryBits) {
        boolean updated = false;

        for (int i = 0; i < shapes.size(); i++) {
            if (shapes.get(i) == shape) {
                lightOccluders.get(i).setCategoryBits(categoryBits);
                updated = true;
            }
        }

        return updated;
    }

    public int getShapeCategoryBits(Shape shape) {
        for (int i = 0; i < shapes.size(); i++) {
            if (shapes.get(i) == shape) {
                return lightOccluders.get(i).getCategoryBits();
            }
        }

        return 0;
    }

    public void clear() {
        shapes.clear();
        lightOccluders.clear();
        moving = null;
    }

    public List<Shape> getShapes() {
        return Collections.unmodifiableList(shapes);
    }

    public Shape getMoving() {
        return moving;
    }

    public void setMoving(Shape moving) {
        this.moving = moving;
    }

    @Override
    public RayCastHit rayCast(float startX, float startY, float endX, float endY) {
        return rayCast(null, startX, startY, endX, endY);
    }

    @Override
    public RayCastHit rayCast(Light light, float startX, float startY, float endX, float endY) {
        float closestFraction = Float.MAX_VALUE;
        LightOccluder closestOccluder = null;

        for (int i = 0, n = lightOccluders.size(); i < n; i++) {
            LightOccluder occluder = lightOccluders.get(i);
            if (!occluder.blocks(light)) {
                continue;
            }

            float hitFraction = rayVsPoints(startX, startY, endX, endY, occluder.points(), closestFraction);
            if (hitFraction < closestFraction) {
                closestFraction = hitFraction;
                closestOccluder = occluder;
            }
        }

        if (closestOccluder == null) {
            return null;
        }

        float dx = endX - startX;
        float dy = endY - startY;

        RayCastHit hit = new RayCastHit(startX + dx * closestFraction, startY + dy * closestFraction);
        hit.setFraction(closestFraction);
        hit.setCollider(closestOccluder.getCollider());
        return hit;
    }

    @Override
    public List<LightOccluder> getLightOccluders() {
        return Collections.unmodifiableList(lightOccluders);
    }

    private float rayVsPoints(float x1, float y1, float x2, float y2, Vector2f[] pts, float maxFraction) {
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
