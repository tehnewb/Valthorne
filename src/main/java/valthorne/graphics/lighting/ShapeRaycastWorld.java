package valthorne.graphics.lighting;

import valthorne.math.Vector2f;
import valthorne.math.geometry.Shape;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ShapeRaycastWorld implements RayCastWorld {

    private static final float EPSILON = 0.00001f;
    private static final float DEFAULT_CELL_SIZE = 128f;

    private final List<Shape> shapes = new ArrayList<>();
    private final List<LightOccluder> lightOccluders = new ArrayList<>();
    private final List<Bounds> occluderBounds = new ArrayList<>();
    private final Map<Long, IntBag> spatialIndex = new HashMap<>();

    private float cellSize = DEFAULT_CELL_SIZE;
    private int[] queryStamps = new int[16];
    private int queryStamp = 1;
    private boolean spatialDirty = true;
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
        occluderBounds.add(new Bounds());
        ensureQueryStampCapacity(lightOccluders.size());
        spatialDirty = true;
    }

    public void removeShape(Shape shape) {
        if (shape == null) {
            return;
        }

        for (int i = shapes.size() - 1; i >= 0; i--) {
            if (shapes.get(i) == shape) {
                shapes.remove(i);
                lightOccluders.remove(i);
                occluderBounds.remove(i);
            }
        }

        if (moving == shape) {
            moving = null;
        }

        spatialDirty = true;
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
        occluderBounds.clear();
        spatialIndex.clear();
        moving = null;
        spatialDirty = true;
        queryStamp = 1;
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

    public float getCellSize() {
        return cellSize;
    }

    public void setCellSize(float cellSize) {
        if (cellSize <= 0f) {
            throw new IllegalArgumentException("cellSize must be > 0");
        }
        if (this.cellSize == cellSize) {
            return;
        }
        this.cellSize = cellSize;
        spatialDirty = true;
    }

    @Override
    public void prepare() {
        if (!spatialDirty && moving == null) {
            return;
        }

        rebuildSpatialIndex();
        spatialDirty = moving != null;
    }

    @Override
    public RayCastHit rayCast(float startX, float startY, float endX, float endY) {
        RayCastHit hit = new RayCastHit();
        return rayCast(null, startX, startY, endX, endY, hit) ? hit : null;
    }

    @Override
    public RayCastHit rayCast(Light light, float startX, float startY, float endX, float endY) {
        RayCastHit hit = new RayCastHit();
        return rayCast(light, startX, startY, endX, endY, hit) ? hit : null;
    }

    @Override
    public boolean rayCast(Light light, float startX, float startY, float endX, float endY, RayCastHit outHit) {
        prepare();

        float closestFraction = Float.MAX_VALUE;
        LightOccluder closestOccluder = null;

        int stamp = nextQueryStamp();

        float rayMinX = Math.min(startX, endX);
        float rayMinY = Math.min(startY, endY);
        float rayMaxX = Math.max(startX, endX);
        float rayMaxY = Math.max(startY, endY);

        int minCellX = worldToCell(rayMinX);
        int maxCellX = worldToCell(rayMaxX);
        int minCellY = worldToCell(rayMinY);
        int maxCellY = worldToCell(rayMaxY);

        for (int cellY = minCellY; cellY <= maxCellY; cellY++) {
            for (int cellX = minCellX; cellX <= maxCellX; cellX++) {
                IntBag bucket = spatialIndex.get(cellKey(cellX, cellY));
                if (bucket == null) {
                    continue;
                }

                for (int i = 0; i < bucket.size; i++) {
                    int occluderIndex = bucket.items[i];
                    if (queryStamps[occluderIndex] == stamp) {
                        continue;
                    }
                    queryStamps[occluderIndex] = stamp;

                    LightOccluder occluder = lightOccluders.get(occluderIndex);
                    if (!occluder.blocks(light)) {
                        continue;
                    }

                    Bounds bounds = occluderBounds.get(occluderIndex);
                    if (!bounds.overlaps(rayMinX, rayMinY, rayMaxX, rayMaxY)) {
                        continue;
                    }

                    float hitFraction = rayVsPoints(startX, startY, endX, endY, occluder.points(), closestFraction);
                    if (hitFraction < closestFraction) {
                        closestFraction = hitFraction;
                        closestOccluder = occluder;
                    }
                }
            }
        }

        if (closestOccluder == null) {
            if (outHit != null) {
                outHit.clear();
            }
            return false;
        }

        float dx = endX - startX;
        float dy = endY - startY;
        if (outHit != null) {
            outHit.set(
                    true,
                    startX + dx * closestFraction,
                    startY + dy * closestFraction,
                    closestFraction,
                    closestOccluder.getCollider()
            );
        }
        return true;
    }

    @Override
    public List<LightOccluder> getLightOccluders() {
        return Collections.unmodifiableList(lightOccluders);
    }

    private void rebuildSpatialIndex() {
        spatialIndex.clear();
        ensureQueryStampCapacity(lightOccluders.size());

        for (int i = 0; i < lightOccluders.size(); i++) {
            LightOccluder occluder = lightOccluders.get(i);
            Bounds bounds = occluderBounds.get(i);

            updateBounds(bounds, occluder.points());
            if (!bounds.valid) {
                continue;
            }

            int minCellX = worldToCell(bounds.minX);
            int maxCellX = worldToCell(bounds.maxX);
            int minCellY = worldToCell(bounds.minY);
            int maxCellY = worldToCell(bounds.maxY);

            for (int cellY = minCellY; cellY <= maxCellY; cellY++) {
                for (int cellX = minCellX; cellX <= maxCellX; cellX++) {
                    spatialIndex.computeIfAbsent(cellKey(cellX, cellY), key -> new IntBag()).add(i);
                }
            }
        }
    }

    private void updateBounds(Bounds bounds, Vector2f[] points) {
        bounds.valid = false;
        if (points == null || points.length == 0) {
            return;
        }

        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;

        for (Vector2f point : points) {
            if (point == null) {
                continue;
            }

            float x = point.getX();
            float y = point.getY();

            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
            bounds.valid = true;
        }

        if (bounds.valid) {
            bounds.minX = minX;
            bounds.minY = minY;
            bounds.maxX = maxX;
            bounds.maxY = maxY;
        }
    }

    private int nextQueryStamp() {
        if (queryStamp == Integer.MAX_VALUE) {
            Arrays.fill(queryStamps, 0);
            queryStamp = 1;
        }
        return queryStamp++;
    }

    private void ensureQueryStampCapacity(int count) {
        if (queryStamps.length >= count) {
            return;
        }

        int newSize = queryStamps.length;
        while (newSize < count) {
            newSize <<= 1;
        }
        queryStamps = Arrays.copyOf(queryStamps, newSize);
    }

    private int worldToCell(float coordinate) {
        return (int) Math.floor(coordinate / cellSize);
    }

    private long cellKey(int cellX, int cellY) {
        return (((long) cellX) << 32) ^ (cellY & 0xffffffffL);
    }

    private float rayVsPoints(float x1, float y1, float x2, float y2, Vector2f[] points, float maxFraction) {
        if (points == null || points.length < 2) {
            return Float.MAX_VALUE;
        }

        float closestFraction = maxFraction;

        Vector2f previous = points[points.length - 1];
        for (int i = 0; i < points.length; i++) {
            Vector2f current = points[i];

            if (previous != null && current != null) {
                float hitFraction = intersectSegment(
                        x1, y1, x2, y2,
                        previous.getX(), previous.getY(),
                        current.getX(), current.getY(),
                        closestFraction
                );

                if (hitFraction < closestFraction) {
                    closestFraction = hitFraction;
                }
            }

            previous = current;
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

    private static final class Bounds {
        float minX;
        float minY;
        float maxX;
        float maxY;
        boolean valid;

        boolean overlaps(float otherMinX, float otherMinY, float otherMaxX, float otherMaxY) {
            return valid
                    && otherMaxX >= minX
                    && otherMinX <= maxX
                    && otherMaxY >= minY
                    && otherMinY <= maxY;
        }
    }

    private static final class IntBag {
        private int[] items = new int[8];
        private int size;

        void add(int value) {
            if (size == items.length) {
                items = Arrays.copyOf(items, size << 1);
            }
            items[size++] = value;
        }
    }
}
