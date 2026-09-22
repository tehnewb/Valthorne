package valthorne.graphics.lighting;

import org.joml.Vector2f;
import valthorne.math.geometry.Shape;

import java.util.*;

/**
 * Mutable polygon-boundary occlusion world backed by a uniform spatial grid.
 * Shapes are borrowed, duplicates are allowed, and category masks filter which
 * lights they block. Registration changes invalidate cached bounds; arbitrary
 * mutations of a shape do not. Set a non-null moving marker while geometry
 * changes to force prepare to rebuild all buckets. This mutable query/index
 * state is intended for coordinated use on one thread.
 *
 * <p>Queries test polygon edges rather than filled interiors. The implementation
 * uses segment bounds for broad-phase candidates but does not explicitly cap
 * the edge-intersection fraction at one; see the reusable rayCast overload for
 * that limitation.</p>
 *
 * <pre>{@code
 * ShapeRaycastWorld world = new ShapeRaycastWorld();
 * world.addShape(wall); // Existing Shape in the light coordinate space.
 * world.setMoving(wall);
 * world.prepare();      // Rebuild after geometry changes.
 * RayCastHit hit = world.rayCast(0f, 0f, 100f, 100f);
 * world.setMoving(null);
 * // Also dirty affected lights before reusing their cached endpoints.
 * }</pre>
 *
 * @author Albert Beaupre
 */
public class ShapeRaycastWorld implements RayCastWorld {

    /**
     * Tolerance for near-parallel edges and edge-endpoint parameter comparisons.
     */
    private static final float EPSILON = 0.00001f;
    /**
     * Initial spatial grid cell size in world units.
     */
    private static final float DEFAULT_CELL_SIZE = 128f;

    private final List<Shape> shapes = new ArrayList<>(); // Borrowed shapes in registration order, including duplicates.
    private final List<LightOccluder> lightOccluders = new ArrayList<>(); // Occluder wrappers aligned by index with shapes.
    private final List<Bounds> occluderBounds = new ArrayList<>(); // Reusable cached bounds aligned with registrations.
    private final Map<Long, IntBag> spatialIndex = new HashMap<>(); // Packed cell keys mapped to registered occluder indices.

    private float cellSize = DEFAULT_CELL_SIZE; // Square grid cell size in world units.
    private int[] queryStamps = new int[16]; // Last query stamp seen by each registered occluder.
    private int queryStamp = 1; // Next nonzero query stamp, reset before integer exhaustion.
    private boolean spatialDirty = true; // Whether prepare must rebuild the spatial buckets.
    private Shape moving; // Non-null marker forcing repeated full index rebuilds.

    /**
     * Registers a borrowed shape with every light-occluder category enabled.
     * Null is ignored; repeated registration is allowed and creates another entry.
     *
     * @param shape world-space shape to register
     */
    public void addShape(Shape shape) {
        addShape(shape, Light.ALL_MASK_BITS);
    }

    /**
     * Appends a borrowed shape and matching occluder, growing query storage and
     * invalidating the spatial index. Geometry is read from the live shape.
     *
     * @param shape        shape to register; null is ignored
     * @param categoryBits category mask tested against each light's occlusion mask
     */
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

    /**
     * Removes every registration whose shape is the supplied object by identity.
     * Also clears the moving marker when it refers to that object and invalidates
     * the index. The shape itself is neither modified nor disposed.
     *
     * @param shape registered object to remove; null is ignored
     */
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

    /**
     * Changes category masks for every identity-matching registration. Index
     * geometry is unchanged, and cached light endpoints are not invalidated.
     *
     * @param shape        registered object to match by identity
     * @param categoryBits replacement occluder category mask
     * @return true if at least one registration matched
     */
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

    /**
     * Reads the category mask from the first identity-matching registration.
     *
     * @param shape registered shape object
     * @return first matching mask, or zero when the shape is absent
     */
    public int getShapeCategoryBits(Shape shape) {
        for (int i = 0; i < shapes.size(); i++) {
            if (shapes.get(i) == shape) {
                return lightOccluders.get(i).getCategoryBits();
            }
        }

        return 0;
    }

    /**
     * Drops all registrations, bounds, and spatial buckets without modifying
     * borrowed shapes. Clears the moving marker and restarts query numbering.
     */
    public void clear() {
        shapes.clear();
        lightOccluders.clear();
        occluderBounds.clear();
        spatialIndex.clear();
        moving = null;
        spatialDirty = true;
        queryStamp = 1;
    }

    /**
     * Returns an unmodifiable live view in registration order. Membership cannot
     * be changed through this view, but the shape objects remain mutable.
     *
     * @return live borrowed shapes, including duplicate registrations
     */
    public List<Shape> getShapes() {
        return Collections.unmodifiableList(shapes);
    }

    /**
     * Returns the marker that keeps the spatial index rebuilding while non-null.
     * The marker need not identify a registered shape.
     *
     * @return movement marker, or null
     */
    public Shape getMoving() {
        return moving;
    }

    /**
     * Stores the movement marker. While non-null, each prepare call rebuilds the
     * entire index; this is not a single-shape incremental update. Assigning null
     * does not itself dirty an otherwise clean index.
     *
     * @param moving any non-null shape to force repeated rebuilds, or null to stop
     */
    public void setMoving(Shape moving) {
        this.moving = moving;
    }

    /**
     * Reads the spatial grid's cell width and height in world units.
     *
     * @return configured cell size
     */
    public float getCellSize() {
        return cellSize;
    }

    /**
     * Sets the grid scale and invalidates the index unless the value is unchanged.
     * Use a finite positive size; the check rejects zero and negatives but does
     * not explicitly reject NaN or positive infinity.
     *
     * @param cellSize desired square-cell size in world units
     * @throws IllegalArgumentException if cellSize is zero or negative
     */
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

    /**
     * Rebuilds all spatial buckets when dirty or when the moving marker is set.
     * A non-null marker keeps the index dirty for the next call. Live shape
     * mutations are not detected automatically while the index remains clean.
     */
    @Override
    public void prepare() {
        if (!spatialDirty && moving == null) {
            return;
        }

        rebuildSpatialIndex();
        spatialDirty = moving != null;
    }

    /**
     * Allocates a hit record and queries without light-category filtering.
     * Uses the same bounds and intersection behavior as the reusable-output overload.
     *
     * @param startX world-space ray origin X
     * @param startY world-space ray origin Y
     * @param endX   world-space target X
     * @param endY   world-space target Y
     * @return nearest accepted boundary hit, or null
     */
    @Override
    public RayCastHit rayCast(float startX, float startY, float endX, float endY) {
        RayCastHit hit = new RayCastHit();
        return rayCast(null, startX, startY, endX, endY, hit) ? hit : null;
    }

    /**
     * Allocates a hit record and queries category-compatible occluders.
     * The temporary record is discarded on a miss.
     *
     * @param light  filtering light, or null to accept all categories
     * @param startX world-space ray origin X
     * @param startY world-space ray origin Y
     * @param endX   world-space target X
     * @param endY   world-space target Y
     * @return nearest accepted boundary hit, or null
     */
    @Override
    public RayCastHit rayCast(Light light, float startX, float startY, float endX, float endY) {
        RayCastHit hit = new RayCastHit();
        return rayCast(light, startX, startY, endX, endY, hit) ? hit : null;
    }

    /**
     * Prepares the index, scans cells overlapping the origin-to-target bounding
     * rectangle, and tests each compatible occluder at most once. Writes the
     * closest accepted edge intersection, or clears output on a miss. Polygon
     * interiors are not treated as immediate hits; parallel edges are skipped.
     *
     * <p>The edge solver caps its parameter by the closest hit so far, initially
     * Float.MAX_VALUE, rather than explicitly by one. Thus an accepted hit can
     * lie beyond the target even though candidates use the segment bounds.</p>
     *
     * @param light  filtering light, or null to accept all categories
     * @param startX world-space origin X
     * @param startY world-space origin Y
     * @param endX   world-space target X
     * @param endY   world-space target Y
     * @param outHit reusable destination, or null for an existence-only query
     * @return true if an eligible edge intersection was found
     */
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

    /**
     * Exposes an unmodifiable live list of occluder wrappers in registration order.
     * The wrappers themselves remain mutable and refer to borrowed shapes.
     *
     * @return live occluder view
     */
    @Override
    public List<LightOccluder> getLightOccluders() {
        return Collections.unmodifiableList(lightOccluders);
    }

    /**
     * Clears buckets, refreshes all shape bounds, and inserts each valid occluder
     * index into every overlapped grid cell. Shapes with no non-null points are
     * excluded. Does not change the dirty flag; prepare owns that transition.
     */
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

    /**
     * Recomputes a bounds object from non-null points. Null or empty input, or
     * an array containing only nulls, marks it invalid and leaves old extents
     * unusable. Coordinates are expected to be finite.
     *
     * @param bounds mutable destination
     * @param points world-space vertices, possibly null
     */
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

            float x = point.x();
            float y = point.y();

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

    /**
     * Returns a new deduplication stamp. Before integer exhaustion, clears all
     * per-occluder stamps and restarts at one so old queries cannot alias.
     *
     * @return nonzero stamp for one query
     */
    private int nextQueryStamp() {
        if (queryStamp == Integer.MAX_VALUE) {
            Arrays.fill(queryStamps, 0);
            queryStamp = 1;
        }
        return queryStamp++;
    }

    /**
     * Doubles deduplication storage until it can address the requested number
     * of registered occluders, preserving existing entries.
     *
     * @param count required registration capacity
     */
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

    /**
     * Maps a finite world coordinate to a grid cell using floor division, so
     * negative fractional coordinates belong to the cell below zero.
     *
     * @param coordinate world-space coordinate
     * @return integer cell coordinate
     */
    private int worldToCell(float coordinate) {
        return (int) Math.floor(coordinate / cellSize);
    }

    /**
     * Packs signed X and Y cell coordinates into distinct halves of a long.
     * No hashing or truncation is performed beyond the map's own key handling.
     *
     * @param cellX grid X
     * @param cellY grid Y
     * @return unique key for the integer coordinate pair
     */
    private long cellKey(int cellX, int cellY) {
        return (((long) cellX) << 32) ^ (cellY & 0xffffffffL);
    }

    /**
     * Finds a nearer intersection along the closed vertex boundary, including
     * the last-to-first edge. Edges adjoining null vertices are skipped.
     *
     * @param x1          ray origin X
     * @param y1          ray origin Y
     * @param x2          target X defining ray direction and fraction scale
     * @param y2          target Y defining ray direction and fraction scale
     * @param points      cyclic boundary vertices
     * @param maxFraction incumbent nearest-hit parameter
     * @return nearer parameter or maxFraction; Float.MAX_VALUE for insufficient points
     */
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
                        previous.x(), previous.y(),
                        current.x(), current.y(),
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

    /**
     * Solves the ray/edge cross-product equations. Rejects near-parallel edges,
     * negative ray parameters, parameters beyond the incumbent, and edge
     * parameters outside the epsilon-expanded [0, 1] interval. The ray parameter
     * is not independently clamped to one, and collinear overlap is ignored.
     *
     * @param x1          ray origin X
     * @param y1          ray origin Y
     * @param x2          target X defining ray direction
     * @param y2          target Y defining ray direction
     * @param x3          edge start X
     * @param y3          edge start Y
     * @param x4          edge end X
     * @param y4          edge end Y
     * @param maxFraction largest accepted ray parameter
     * @return intersection parameter, or Float.MAX_VALUE when rejected
     */
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

    /**
     * Reusable axis-aligned extent for one registered occluder. Validity is
     * separate from coordinates so empty geometry can reuse existing storage.
     *
     * <p>Coordinates describe world-space occluder geometry and are used only for broad-phase
     * rejection. An overlapping bound does not establish an exact ray intersection.</p>
     *
     * @author Albert Beaupre
     */
    private static final class Bounds {
        float minX; // Cached minimum world X, meaningful only when valid.
        float minY; // Cached minimum world Y, meaningful only when valid.
        float maxX; // Cached maximum world X, meaningful only when valid.
        float maxY; // Cached maximum world Y, meaningful only when valid.
        boolean valid; // Whether at least one non-null point supplied the extent.

        /**
         * Tests inclusive rectangle overlap only when this cached extent is valid.
         * Touching edges count as overlap; input bounds are assumed ordered.
         *
         * @param otherMinX candidate minimum X
         * @param otherMinY candidate minimum Y
         * @param otherMaxX candidate maximum X
         * @param otherMaxY candidate maximum Y
         * @return true when both axis intervals overlap this valid extent
         */
        boolean overlaps(float otherMinX, float otherMinY, float otherMaxX, float otherMaxY) {
            return valid
                    && otherMaxX >= minX
                    && otherMinX <= maxX
                    && otherMaxY >= minY
                    && otherMinY <= maxY;
        }
    }

    /**
     * Growable primitive index list for one spatial bucket. Only entries before
     * size are valid; capacity grows without boxing occluder indices.
     *
     * <p>Bucket contents can include duplicate indices. The enclosing spatial index controls
     * which entries are meaningful and rebuilds buckets when occluder placement changes.</p>
     *
     * @author Albert Beaupre
     */
    private static final class IntBag {
        private int[] items = new int[8]; // Primitive occluder indices, valid before size.
        private int size; // Number of stored indices.

        /**
         * Appends an occluder index, doubling the backing array when full. Existing
         * indices are preserved and duplicate values are allowed.
         *
         * @param value registered occluder index
         */
        void add(int value) {
            if (size == items.length) {
                items = Arrays.copyOf(items, size << 1);
            }
            items[size++] = value;
        }
    }
}
