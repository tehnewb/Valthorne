package valthorne.graphics.particle;

import valthorne.math.MathUtils;

import java.util.Random;

/**
 * A {@code ConeSpawnDistributor} is a specific implementation of the {@code SpawnDistributor} interface
 * that calculates spawn offsets within a conical region. The cone is defined by a direction, a spread
 * angle, a radius, and an optional restriction to the cone's edge.
 * <p>
 * This distributor allows for randomized distribution of offsets that simulate spawning within a
 * cone-shaped area, as determined by the given parameters.
 *
 * @author Albert Beaupre
 * @since February 10th, 2026
 */
public final class ConeSpawnDistributor implements SpawnDistributor {

    private final float directionDeg; // Sector center direction in degrees from positive X.
    private final float spreadDeg; // Full angular sector width in degrees.
    private final float radius; // Maximum sector radius, clamped against zero.
    private final boolean edgeOnly; // Whether offsets lie on the outer arc instead of throughout the sector.

    /**
     * Creates a 2D circular sector distribution. Negative radius is clamped to zero;
     * angles are stored without normalization or range validation. Edge mode selects
     * the outer arc, not the sector's radial sides.
     *
     * @param directionDeg center angle in degrees
     * @param spreadDeg full angular width in degrees
     * @param radius maximum radial distance
     * @param edgeOnly true for the outer arc
     */
    public ConeSpawnDistributor(float directionDeg, float spreadDeg, float radius, boolean edgeOnly) {
        this.directionDeg = directionDeg;
        this.spreadDeg = spreadDeg;
        this.radius = Math.max(0f, radius);
        this.edgeOnly = edgeOnly;
    }

    /**
     * Samples an angle across the configured full spread and uses a fixed or
     * square-root-distributed radius. Conventional positive sector spans give uniform
     * area density when edgeOnly is false.
     *
     * @param random nonnull random source
     * @param out nonnull destination with at least two entries; X then Y
     * @throws NullPointerException if random or out is null
     * @throws ArrayIndexOutOfBoundsException if out has fewer than two entries
     */
    @Override
    public void computeOffset(Random random, float[] out) {
        float minA = directionDeg - spreadDeg * 0.5f;
        float maxA = directionDeg + spreadDeg * 0.5f;

        float a = (float) Math.toRadians(MathUtils.lerp(minA, maxA, random.nextFloat()));

        float r = radius;
        if (!edgeOnly) r *= (float) Math.sqrt(random.nextFloat());

        out[0] = (float) Math.cos(a) * r;
        out[1] = (float) Math.sin(a) * r;
    }
}
