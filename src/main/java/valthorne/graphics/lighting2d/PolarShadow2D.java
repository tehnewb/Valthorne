package valthorne.graphics.lighting2d;

import java.util.Arrays;
import java.util.List;

/**
 * CPU cache of nearest occluder distances around a 2D point light. The full
 * circle is divided into equally sized angular bins, sampled at their centers
 * from zero through 2 * PI in the XY plane. Stored distances are divided by
 * the light's influence radius: one means no nearer blocker, and zero is used
 * for every bin when the light is inside a participating polygon.
 *
 * <pre>{@code
 * PolarShadow2D shadow = new PolarShadow2D(512);
 * PointLight2D light = new PointLight2D().setRadius(100);
 * Occluder2D wall = Occluder2D.rectangle(20, -10, 4, 20);
 * boolean rebuilt = shadow.update(light, java.util.List.of(wall));
 * float normalizedDistance = shadow.getDistance(0);
 * }</pre>
 *
 * <p>Each segment updates only the angular bins it spans, including wraparound.
 * An endpoint-distance fallback conservatively covers narrow obstacles when a
 * bin-center ray misses their segment. This is a discretized shadow representation,
 * not an exact continuous visibility query.</p>
 *
 * <p>Update fingerprints include light identity and shadow revision plus ordered
 * identities and revisions of relevant occluders. Unchanged fingerprints reuse
 * existing data. Color, intensity and other appearance-only light edits do not
 * rebuild geometry. Use configuration setters so revision changes remain visible
 * to this cache. The object is mutable, unsynchronized and owns no GPU resource.</p>
 *
 * @author Albert Beaupre
 */
public final class PolarShadow2D {
    /**
     * Full-circle angle in radians used for bin mapping and seam unwrapping.
     */
    private static final double TAU = Math.PI * 2;
    private final float[] distances, cos, sin; // Normalized depths and precomputed bin-center unit directions.
    private long fingerprint = Long.MIN_VALUE, rebuilds; // Last geometry fingerprint and count of completed rebuilds.

    /**
     * Allocates fixed-size depth and direction arrays and precomputes bin-center
     * rays. Depth storage initially contains zeros; call update before interpreting
     * it as a shadow map. Resolution need not be a power of two.
     *
     * @param resolution the angular bin count, inclusive range 64 through 4096
     * @throws IllegalArgumentException if resolution is outside the supported range
     */
    public PolarShadow2D(int resolution) {
        if (resolution < 64 || resolution > 4096)
            throw new IllegalArgumentException("Shadow resolution must be in [64,4096]");
        distances = new float[resolution];
        cos = new float[resolution];
        sin = new float[resolution];
        for (int i = 0; i < resolution; i++) {
            cos[i] = (float) Math.cos((i + .5) * TAU / resolution);
            sin[i] = (float) Math.sin((i + .5) * TAU / resolution);
        }
    }

    /**
     * Reports completed geometry rebuilds since construction. Cached updates do
     * not increment this diagnostic counter, even though they scan candidate geometry.
     *
     * @return the number of updates that rebuilt distance data
     */
    public long getRebuildCount() {return rebuilds;}

    /**
     * Reads a normalized radial depth from current storage. Multiply by the light
     * radius used in the last rebuild to recover world distance. Before the first
     * update, this returns the array's initial zero value rather than a valid map.
     *
     * @param bin the zero-based angular sample index
     * @return the cached distance relative to the last light radius
     * @throws ArrayIndexOutOfBoundsException if bin is outside the allocated samples
     */
    public float getDistance(int bin) {return distances[bin];}

    /**
     * Exposes the live depth array for rendering uploads without making a copy.
     * The caller must treat it as borrowed read-only data: modifications would
     * corrupt cached results without changing the geometry fingerprint.
     *
     * @return the owned normalized-distance array, overwritten by later rebuilds
     */
    float[] data() {return distances;}

    /**
     * Rebuilds the map only when the computed geometry fingerprint changes.
     * Candidate occluders participate when their categories intersect the light
     * mask and their bounds overlap its influence square. Changes to excluded
     * occluders do not affect the fingerprint; reordering included ones can.
     *
     * <p>A rebuild first resets all depths to one. If shadows are enabled, edges
     * reduce those depths, or an enclosing occluder sets the entire map to zero.
     * Disabled shadows retain ones. The light's enabled flag and cone settings
     * are not applied here; the rendering layer handles their contribution.</p>
     *
     * <p>The supplied list and objects are read directly, not retained as a copied
     * geometry snapshot. Do not mutate them during this call. Fingerprinting is
     * hash-based rather than a full equality comparison.</p>
     *
     * @param light     the nonnull light with valid geometry settings
     * @param occluders the nonnull candidate list containing no null entries
     * @return true if distance data was rebuilt; false if its fingerprint matched
     * @throws NullPointerException if light, the list or a list entry is null
     */
    public boolean update(PointLight2D light, List<Occluder2D> occluders) {
        long hash = light.shadowRevision * 31 + System.identityHashCode(light);
        for (Occluder2D o : occluders)
            if ((o.category & light.mask) != 0 && o.overlaps(light))
                hash = (hash * 1099511628211L) ^ ((long) System.identityHashCode(o) * 31 + o.revision);
        if (hash == fingerprint) return false;
        Arrays.fill(distances, 1);
        if (light.shadows) for (Occluder2D o : occluders) {
            if ((o.category & light.mask) == 0 || !o.overlaps(light)) continue;
            if (o.contains(light.x, light.y)) {
                Arrays.fill(distances, 0);
                break;
            }
            for (int i = 0; i < o.vertices.length; i += 2) {
                int j = (i + 2) % o.vertices.length;
                edge(o.x + o.vertices[i] - light.x, o.y + o.vertices[i + 1] - light.y,
                        o.x + o.vertices[j] - light.x, o.y + o.vertices[j + 1] - light.y, light.radius);
            }
        }
        fingerprint = hash;
        rebuilds++;
        return true;
    }

    /**
     * Accumulates one segment into the bins covered by its shorter angular span.
     * End angles are unwrapped across the circle seam, and bin indices wrap into
     * array range. Valid forward ray/segment intersections supply depth; near-parallel
     * or missed intersections use the nearer endpoint distance so sub-bin obstacles
     * remain represented. Each depth is normalized and minimized with existing data.
     *
     * @param ax     the first endpoint X relative to the light center
     * @param ay     the first endpoint Y relative to the light center
     * @param bx     the second endpoint X relative to the light center
     * @param by     the second endpoint Y relative to the light center
     * @param radius the positive light influence radius used for normalization
     */
    private void edge(float ax, float ay, float bx, float by, float radius) {
        double a = Math.atan2(ay, ax), b = Math.atan2(by, bx), span = b - a;
        if (span > Math.PI) b -= TAU;
        else if (span < -Math.PI) b += TAU;
        double lo = Math.min(a, b), hi = Math.max(a, b);
        int start = (int) Math.floor(lo / TAU * distances.length), end = (int) Math.floor(hi / TAU * distances.length);
        float sx = bx - ax, sy = by - ay;
        float endpoint = Math.min((float) Math.hypot(ax, ay), (float) Math.hypot(bx, by));
        for (int bin = start; bin <= end; bin++) {
            int i = Math.floorMod(bin, distances.length);
            float dx = cos[i], dy = sin[i], denom = dx * sy - dy * sx;
            float distance = endpoint;
            if (Math.abs(denom) > 1e-8) {
                float t = (ax * sy - ay * sx) / denom, u = (ax * dy - ay * dx) / denom;
                if (t >= 0 && u >= 0 && u <= 1) distance = t;
            }
            distances[i] = Math.min(distances[i], distance / radius);
        }
    }
}
