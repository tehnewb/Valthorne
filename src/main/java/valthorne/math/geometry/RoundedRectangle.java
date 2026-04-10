package valthorne.math.geometry;

import valthorne.math.Vector2f;

public class RoundedRectangle extends Shape {

    private static final float EPSILON = 0.0001f;

    private float x, y, width, height;
    private float radius;
    private int segmentsPerCorner;

    private Vector2f[] points;

    public RoundedRectangle(float x, float y, float width, float height, float radius, int segmentsPerCorner) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.radius = Math.max(1f, radius);
        this.segmentsPerCorner = Math.max(1, segmentsPerCorner);
        ensureValidRadius();
        updatePoints();
    }

    public RoundedRectangle(float x, float y, float width, float height) {
        this(x, y, width, height, 0f, 4);
    }

    public float getX() {
        return x;
    }

    public float getY() {
        return y;
    }

    public float getWidth() {
        return width;
    }

    public float getHeight() {
        return height;
    }

    public float getRadius() {
        return radius;
    }

    public int getSegmentsPerCorner() {
        return segmentsPerCorner;
    }

    public void setX(float x) {
        this.x = x;
        updatePoints();
    }

    public void setY(float y) {
        this.y = y;
        updatePoints();
    }

    public void setPosition(float x, float y) {
        this.x = x;
        this.y = y;
        updatePoints();
    }

    public void setWidth(float width) {
        this.width = width;
        ensureValidRadius();
        updatePoints();
    }

    public void setHeight(float height) {
        this.height = height;
        ensureValidRadius();
        updatePoints();
    }

    public void setSize(float width, float height) {
        this.width = width;
        this.height = height;
        ensureValidRadius();
        updatePoints();
    }

    public void setRadius(float radius) {
        this.radius = radius;
        ensureValidRadius();
        updatePoints();
    }

    public void setSegmentsPerCorner(int segmentsPerCorner) {
        this.segmentsPerCorner = Math.max(1, segmentsPerCorner);
        updatePoints();
    }

    @Override
    public void move(Vector2f offset) {
        this.x += offset.getX();
        this.y += offset.getY();
        updatePoints();
    }

    @Override
    public Vector2f[] points() {
        return points;
    }

    private void ensureValidRadius() {
        float maxR = 0.5f * Math.min(Math.max(0f, width), Math.max(0f, height));
        if (radius < 0f) {
            radius = 0f;
        }
        if (radius > maxR) {
            radius = maxR;
        }
    }

    private void ensurePointCount(int count) {
        if (points != null && points.length == count) {
            return;
        }

        points = new Vector2f[count];
        for (int i = 0; i < count; i++) {
            points[i] = new Vector2f();
        }
    }

    private void updatePoints() {
        if (radius <= EPSILON) {
            ensurePointCount(4);
            points[0].set(x, y);
            points[1].set(x + width, y);
            points[2].set(x + width, y + height);
            points[3].set(x, y + height);
            return;
        }

        int arcPointCount = segmentsPerCorner + 1;
        int totalPointCount = arcPointCount * 4;
        ensurePointCount(totalPointCount);

        float tlCx = x + radius;
        float tlCy = y + radius;

        float trCx = x + width - radius;
        float trCy = y + radius;

        float brCx = x + width - radius;
        float brCy = y + height - radius;

        float blCx = x + radius;
        float blCy = y + height - radius;

        int idx = 0;
        idx = writeArc(points, idx, tlCx, tlCy, radius, (float) Math.PI, (float) (1.5 * Math.PI), arcPointCount);
        idx = writeArc(points, idx, trCx, trCy, radius, (float) (1.5 * Math.PI), (float) (2.0 * Math.PI), arcPointCount);
        idx = writeArc(points, idx, brCx, brCy, radius, 0f, (float) (0.5 * Math.PI), arcPointCount);
        writeArc(points, idx, blCx, blCy, radius, (float) (0.5 * Math.PI), (float) Math.PI, arcPointCount);
    }

    private static int writeArc(Vector2f[] dst, int idx, float cx, float cy, float r, float a0, float a1, int pointCount) {
        float da = (a1 - a0) / (pointCount - 1);

        for (int i = 0; i < pointCount; i++) {
            float a = a0 + da * i;
            float px = cx + (float) Math.cos(a) * r;
            float py = cy + (float) Math.sin(a) * r;
            dst[idx++].set(px, py);
        }

        return idx;
    }
}