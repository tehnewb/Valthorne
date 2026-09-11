package valthorne.graphics.lighting3d;

import valthorne.camera.Camera3D;
import valthorne.graphics.model.PointLight3D;

import java.util.Arrays;
import java.util.List;

/**
 * CPU light packer and conservative screen-tile culler for tiled forward shading.
 * Each active light occupies eight floats: world XYZ, range, linear RGB, and
 * intensity. Each tile occupies STRIDE integers: a count followed by light indices,
 * or a negative count meaning test every active light. Overflow uses that fallback
 * instead of silently dropping contributions. No depth readback is required.
 *
 * <p>Update expects rebuilt camera matrices and frustum. Input values and camera
 * state are cached to avoid rebuilding unchanged data; storage is reused except
 * when tile-grid dimensions change. The mutable arrays and scratch state belong
 * to one coordinated rendering thread. Tile Y follows projected bottom-to-top NDC.</p>
 *
 * @author Albert Beaupre
 */
public final class LightGrid3D {
    /**
     * Maximum input lights, inline indices per tile, and integer tile-record stride.
     */
    public static final int MAX_LIGHTS = 1024, LIGHTS_PER_TILE = 64, STRIDE = LIGHTS_PER_TILE + 1;
    final float[] lights = new float[MAX_LIGHTS * 8]; // Packed active-light records, valid through active times eight floats.
    private final float[] source = new float[MAX_LIGHTS * 8], previous = new float[MAX_LIGHTS * 8]; // Current input packing and prior input snapshot used for change detection.
    private final org.joml.Matrix4f matrix = new org.joml.Matrix4f(); // Copied combined camera matrix from the previous update.
    int[] cells = new int[0]; // Tile headers and packed active-light indices; negative headers use all lights.
    private int columns, rows, tileSize, width, height, active, previousCount = -1, overflow; // Grid dimensions, pixel sizing, active/prior input counts, and overflow statistics.
    private boolean tiled = true, previousTiled; // Requested culling mode and the mode last included in cached data.
    private double average; // Effective light tests per tile averaged over the latest rebuilt grid.

    /**
     * Reads whether the next update assigns lights to individual tiles.
     *
     * @return requested culling mode
     */
    public boolean isTiled() {return tiled;}

    /**
     * Selects tiled culling or all-active-light fallback without rebuilding immediately.
     * The next update detects the mode change and regenerates tile headers.
     *
     * @param enabled true to cull per tile, false for the diagnostic fallback
     * @return this grid
     */
    public LightGrid3D setTiled(boolean enabled) {
        tiled = enabled;
        return this;
    }

    /**
     * Reads the last update's light count after zero-contribution and frustum rejection.
     *
     * @return number of packed active lights
     */
    public int getActiveLightCount() {return active;}

    /**
     * Reads the number of screen-tile columns from the last successful update.
     *
     * @return column count, initially zero
     */
    public int getColumns() {return columns;}

    /**
     * Reads the number of screen-tile rows from the last successful update.
     *
     * @return row count, initially zero
     */
    public int getRows() {return rows;}

    /**
     * Reads the configured square tile extent used by the latest grid update.
     *
     * @return tile size in pixels, initially zero
     */
    public int getTileSize() {return tileSize;}

    /**
     * Reads tiles that exceeded their fixed light-index capacity during tiled culling.
     * Disabling tiled culling uses fallback headers but does not count them as overflow.
     *
     * @return overflow tile count from the latest rebuild
     */
    public int getOverflowTileCount() {return overflow;}

    /**
     * Reads average effective light tests per tile, counting fallback tiles as all
     * active lights rather than a negative header value.
     *
     * @return last rebuild's average, initially zero
     */
    public double getAverageLightsPerTile() {return average;}

    /**
     * Reads a tile's effective light count, expanding a negative fallback header
     * to the complete active-light count. Does not return the raw storage sentinel.
     *
     * @param x zero-based tile column
     * @param y zero-based tile row
     * @return number of lights the shader should test
     * @throws IndexOutOfBoundsException if either tile coordinate is outside the grid
     */
    public int getTileLightCount(int x, int y) {
        if (x < 0 || y < 0 || x >= columns || y >= rows) throw new IndexOutOfBoundsException();
        int count = cells[(y * columns + x) * STRIDE];
        return count < 0 ? active : count;
    }

    /**
     * Validates and packs input values, then rebuilds only when viewport dimensions,
     * tile size, culling mode, camera matrix, or packed inputs change. Rejects black,
     * zero-intensity, and frustum-excluded lights. Projects each remaining sphere's
     * bounding-box corners to screen tiles; crossing the eye plane covers all tiles.
     * Alpha and shadow flags are not part of packed data.
     *
     * @param camera camera with current combined matrix and frustum
     *
     * @param input non-null light list of at most MAX_LIGHTS non-null entries
     * @param width positive viewport width in pixels
     * @param height positive viewport height in pixels
     * @param tileSize positive square tile size in pixels
     * @return true when cached geometry/data was rebuilt and should be uploaded
     * @throws IllegalArgumentException for invalid dimensions, excess lights, non-finite
     * packed values, or negative RGB
     * @throws NullPointerException if camera, input, or a light entry is null
     * @throws ArithmeticException if tile storage size multiplication overflows
     */
    public boolean update(Camera3D camera, List<PointLight3D> input, int width, int height, int tileSize) {
        if (width <= 0 || height <= 0 || tileSize < 1)
            throw new IllegalArgumentException("Positive viewport and tile size required");
        if (input.size() > MAX_LIGHTS) throw new IllegalArgumentException("At most 1024 lights are supported");
        for (int i = 0; i < input.size(); i++) {
            PointLight3D light = input.get(i);
            int p = i * 8;
            source[p] = light.getPosition().x();
            source[p + 1] = light.getPosition().y();
            source[p + 2] = light.getPosition().z();
            source[p + 3] = light.getRange();
            source[p + 4] = light.getColor().r();
            source[p + 5] = light.getColor().g();
            source[p + 6] = light.getColor().b();
            source[p + 7] = light.getIntensity();
            for (int j = 0; j < 8; j++)
                if (!Float.isFinite(source[p + j])) throw new IllegalArgumentException("Light values must be finite");
            if (source[p + 4] < 0 || source[p + 5] < 0 || source[p + 6] < 0)
                throw new IllegalArgumentException("Light colors must be nonnegative");
        }
        org.joml.Matrix4f m = camera.getCombined();
        if (this.width == width && this.height == height && this.tileSize == tileSize && previousCount == input.size()
                && previousTiled == tiled && matrix.equals(m)
                && Arrays.equals(source, 0, input.size() * 8, previous, 0, input.size() * 8)) return false;
        this.width = width;
        this.height = height;
        this.tileSize = tileSize;
        previousCount = input.size();
        previousTiled = tiled;
        System.arraycopy(source, 0, previous, 0, input.size() * 8);
        matrix.set(m);
        columns = (width + tileSize - 1) / tileSize;
        rows = (height + tileSize - 1) / tileSize;
        int size = Math.multiplyExact(Math.multiplyExact(columns, rows), STRIDE);
        if (cells.length != size) cells = new int[size];
        else Arrays.fill(cells, 0);
        active = overflow = 0;
        for (int i = 0; i < input.size(); i++) {
            int p = i * 8;
            float x = source[p], y = source[p + 1], z = source[p + 2], r = source[p + 3];
            if (source[p + 7] == 0 || source[p + 4] + source[p + 5] + source[p + 6] == 0 || !camera.getFrustum().testSphere(x, y, z, r))
                continue;
            int index = active++;
            System.arraycopy(source, p, lights, index * 8, 8);
            if (!tiled) continue;
            float minX = 1, minY = 1, maxX = -1, maxY = -1;
            boolean crossesEye = false;
            for (int corner = 0; corner < 8; corner++) {
                float px = x + ((corner & 1) == 0 ? -r : r), py = y + ((corner & 2) == 0 ? -r : r), pz = z + ((corner & 4) == 0 ? -r : r);
                float w = m.m03() * px + m.m13() * py + m.m23() * pz + m.m33();
                if (w <= .00001f) {
                    crossesEye = true;
                    break;
                }
                float cx = (m.m00() * px + m.m10() * py + m.m20() * pz + m.m30()) / w;
                float cy = (m.m01() * px + m.m11() * py + m.m21() * pz + m.m31()) / w;
                minX = Math.min(minX, cx);
                maxX = Math.max(maxX, cx);
                minY = Math.min(minY, cy);
                maxY = Math.max(maxY, cy);
            }
            // Perspective division is unsafe across the eye plane: conservatively cover the viewport.
            int x0 = crossesEye ? 0 : tile(minX, width, columns), x1 = crossesEye ? columns - 1 : tile(maxX, width, columns);
            int y0 = crossesEye ? 0 : tile(minY, height, rows), y1 = crossesEye ? rows - 1 : tile(maxY, height, rows);
            for (int ty = y0; ty <= y1; ty++)
                for (int tx = x0; tx <= x1; tx++) {
                    int base = (ty * columns + tx) * STRIDE, count = cells[base];
                    if (count < 0) continue;
                    if (count == LIGHTS_PER_TILE) {
                        cells[base] = -1;
                        overflow++;
                    } else {
                        cells[base + 1 + count] = index;
                        cells[base] = count + 1;
                    }
                }
        }
        long total = 0;
        for (int i = 0; i < cells.length; i += STRIDE) {
            if (!tiled) cells[i] = -1;
            total += cells[i] < 0 ? active : cells[i];
        }
        average = total / (double) (columns * rows);
        return true;
    }

    /**
     * Maps a normalized device coordinate to a pixel-derived tile and clamps it
     * to the valid edge tile, including projected bounds outside the viewport.
     *
     * @param ndc projected coordinate, normally between minus one and one
     * @param extent viewport width or height in pixels
     * @param count positive number of tiles on the selected axis
     * @return clamped zero-based tile coordinate
     */
    private int tile(float ndc, int extent, int count) {return Math.max(0, Math.min(count - 1, (int) Math.floor((ndc * .5f + .5f) * extent / tileSize)));}
}
