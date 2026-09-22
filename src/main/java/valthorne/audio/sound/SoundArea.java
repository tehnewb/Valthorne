package valthorne.audio.sound;

/**
 * Immutable ambient sound zone. Full gain inside, smoothstep attenuation outside,
 * zero gain at and beyond the fade distance. Two-dimensional zones ignore Z.
 * Evaluation allocates no objects. Coordinates and dimensions must be finite.
 * This models ambient coverage, not directional panning, occlusion or reverb.
 *
 * @author Albert Beaupre
 */
public final class SoundArea {
    private final double x, y, z, a, b, c, fade, outerSquared; // Center XYZ, radius/half-extents ABC, fade distance, and squared outer round radius.
    private final boolean round, twoDimensional; // Radial-versus-box distance mode and whether geometry ignores Z.

    /**
     * Validates and stores immutable geometry in double precision, including the
     * round zone's squared outer radius. Zero dimensions and zero fade are allowed.
     *
     * @param x              center X
     * @param y              center Y
     * @param z              center Z
     * @param a              radius for round zones, otherwise half-width
     * @param b              half-height for rectangular zones
     * @param c              half-depth for box zones
     * @param fade           nonnegative distance outside the full-gain boundary
     * @param round          whether to use radial rather than box distance
     * @param twoDimensional whether listener Z is ignored after validation
     * @throws IllegalArgumentException if coordinates are non-finite or dimensions are negative/non-finite
     */
    private SoundArea(float x, float y, float z, float a, float b, float c, float fade, boolean round, boolean twoDimensional) {
        finite(x);
        finite(y);
        finite(z);
        positive(a);
        positive(b);
        positive(c);
        positive(fade);
        this.x = x;
        this.y = y;
        this.z = z;
        this.a = a;
        this.b = b;
        this.c = c;
        this.fade = fade;
        this.round = round;
        this.twoDimensional = twoDimensional;
        double outer = a + (double) fade;
        outerSquared = outer * outer;
    }

    /**
     * Creates a circular full-gain zone in the XY plane with smooth attenuation
     * beyond its radius. A zero fade makes the boundary abrupt; listener Z is
     * ignored geometrically but must still be finite during evaluation.
     *
     * @param x      finite center X
     * @param y      finite center Y
     * @param radius finite nonnegative full-gain radius
     * @param fade   finite nonnegative outer fade distance
     * @return immutable circular zone
     * @throws IllegalArgumentException if inputs violate the stated ranges
     */
    public static SoundArea circle(float x, float y, float radius, float fade) {
        return new SoundArea(x, y, 0, radius, 0, 0, fade, true, true);
    }

    /**
     * Creates a spherical full-gain zone with smooth attenuation outside its
     * surface. At a zero fade distance, gain drops immediately outside the sphere.
     *
     * @param x      finite center X
     * @param y      finite center Y
     * @param z      finite center Z
     * @param radius finite nonnegative full-gain radius
     * @param fade   finite nonnegative outer fade distance
     * @return immutable spherical zone
     * @throws IllegalArgumentException if inputs violate the stated ranges
     */
    public static SoundArea sphere(float x, float y, float z, float radius, float fade) {
        return new SoundArea(x, y, z, radius, 0, 0, fade, true, false);
    }

    /**
     * Creates an axis-aligned XY rectangle specified by center and half-extents.
     * Outside attenuation uses Euclidean distance to the rectangle, producing
     * rounded fade corners rather than a larger rectangular border.
     *
     * @param x          finite center X
     * @param y          finite center Y
     * @param halfWidth  finite nonnegative X half-extent
     * @param halfHeight finite nonnegative Y half-extent
     * @param fade       finite nonnegative distance outside the rectangle
     * @return immutable two-dimensional zone
     * @throws IllegalArgumentException if inputs violate the stated ranges
     */
    public static SoundArea rectangle(float x, float y, float halfWidth, float halfHeight, float fade) {
        return new SoundArea(x, y, 0, halfWidth, halfHeight, 0, fade, false, true);
    }

    /**
     * Creates an axis-aligned full-gain box with Euclidean-distance attenuation
     * outside its surface. Half-extents and fade may be zero; no rotation is stored.
     *
     * @param x          finite center X
     * @param y          finite center Y
     * @param z          finite center Z
     * @param halfWidth  finite nonnegative X half-extent
     * @param halfHeight finite nonnegative Y half-extent
     * @param halfDepth  finite nonnegative Z half-extent
     * @param fade       finite nonnegative distance outside the box
     * @return immutable three-dimensional zone
     * @throws IllegalArgumentException if inputs violate the stated ranges
     */
    public static SoundArea box(float x, float y, float z, float halfWidth, float halfHeight, float halfDepth, float fade) {
        return new SoundArea(x, y, z, halfWidth, halfHeight, halfDepth, fade, false, false);
    }

    /**
     * Evaluates full gain inside or on the boundary and smoothstep falloff outside.
     * Returns zero once outside distance reaches fade. With zero fade, boundary
     * points still receive full gain and every outside point receives zero. Uses
     * no per-call objects and validates all coordinates, including ignored Z.
     *
     * @param listenerX finite listener world X
     * @param listenerY finite listener world Y
     * @param listenerZ finite listener world Z
     * @return gain multiplier between zero and one inclusive
     * @throws IllegalArgumentException if any listener coordinate is non-finite
     */
    public float gainAt(float listenerX, float listenerY, float listenerZ) {
        finite(listenerX);
        finite(listenerY);
        finite(listenerZ);
        double dx = listenerX - x, dy = listenerY - y, dz = twoDimensional ? 0 : listenerZ - z;
        double distance;
        if (round) {
            double squared = dx * dx + dy * dy + dz * dz;
            if (squared <= a * a) return 1;
            if (squared >= outerSquared) return 0;
            distance = Math.sqrt(squared) - a;
        } else {
            dx = Math.max(0, Math.abs(dx) - a);
            dy = Math.max(0, Math.abs(dy) - b);
            dz = Math.max(0, Math.abs(dz) - c);
            double squared = dx * dx + dy * dy + dz * dz;
            if (squared == 0) return 1;
            if (squared >= fade * fade) return 0;
            distance = Math.sqrt(squared);
        }
        double t = distance / fade;
        return (float) (1 - t * t * (3 - 2 * t));
    }

    /**
     * Checks a dimension or fade distance for finiteness and nonnegativity.
     * Despite the helper name, zero is accepted for degenerate zones and hard edges.
     *
     * @param value dimension to validate
     * @throws IllegalArgumentException if value is negative or non-finite
     */
    private static void positive(float value) {
        finite(value);
        if (value < 0) throw new IllegalArgumentException("Dimensions and fade must be nonnegative");
    }

    /**
     * Rejects NaN and either infinity before geometry is stored or evaluated.
     *
     * @param value coordinate or dimension to validate
     * @throws IllegalArgumentException if value is non-finite
     */
    private static void finite(float value) {
        if (!Float.isFinite(value)) throw new IllegalArgumentException("Coordinates must be finite");
    }
}
