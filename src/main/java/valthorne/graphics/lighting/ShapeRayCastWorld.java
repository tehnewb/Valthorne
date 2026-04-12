package valthorne.graphics.lighting;

import valthorne.math.Vector2f;
import valthorne.math.geometry.Area;

import java.util.ArrayList;
import java.util.List;

public final class ShapeRayCastWorld implements RayCastWorld {

    private static final float EPSILON = 0.00001f;

    private final List<Area> occluders = new ArrayList<>();

    public void add(Area area) {
        if (area == null) throw new NullPointerException("area cannot be null");
        occluders.add(area);
    }

    public void remove(Area area) {
        occluders.remove(area);
    }

    public List<Area> getOccluders() {
        return occluders;
    }

    @Override
    public RayCastHit rayCast(float startX, float startY, float endX, float endY) {
        float closestFraction = Float.MAX_VALUE;
        RayCastHit closest = null;

        for (Area area : occluders) {
            Vector2f[] pts = area.points();
            if (pts == null || pts.length < 2) continue;

            for (int i = 0; i < pts.length; i++) {
                Vector2f a = pts[i];
                Vector2f b = pts[(i + 1) % pts.length];
                if (a == null || b == null) continue;

                RayCastHit hit = intersectSegment(
                        startX, startY, endX, endY,
                        a.getX(), a.getY(),
                        b.getX(), b.getY()
                );

                if (hit == null) continue;

                if (hit.getFraction() < closestFraction) {
                    closestFraction = hit.getFraction();
                    closest = hit;
                    closest.setCollider(area);
                }
            }
        }

        return closest;
    }

    private RayCastHit intersectSegment(
            float x1, float y1,
            float x2, float y2,
            float x3, float y3,
            float x4, float y4) {

        float rX = x2 - x1;
        float rY = y2 - y1;
        float sX = x4 - x3;
        float sY = y4 - y3;

        float denom = cross(rX, rY, sX, sY);
        if (Math.abs(denom) < EPSILON) return null;

        float qpx = x3 - x1;
        float qpy = y3 - y1;

        float t = cross(qpx, qpy, sX, sY) / denom;
        float u = cross(qpx, qpy, rX, rY) / denom;

        if (t <= EPSILON || t >= 1f - EPSILON) return null;
        if (u < 0f || u > 1f) return null;

        float hitX = x1 + t * rX;
        float hitY = y1 + t * rY;

        RayCastHit hit = new RayCastHit(hitX, hitY);
        hit.setFraction(t);
        return hit;
    }

    private float cross(float ax, float ay, float bx, float by) {
        return ax * by - ay * bx;
    }
}