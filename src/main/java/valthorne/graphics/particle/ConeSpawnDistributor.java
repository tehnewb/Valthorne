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

    private final float directionDeg;
    private final float spreadDeg;
    private final float radius;
    private final boolean edgeOnly;

    public ConeSpawnDistributor(float directionDeg, float spreadDeg, float radius, boolean edgeOnly) {
        this.directionDeg = directionDeg;
        this.spreadDeg = spreadDeg;
        this.radius = Math.max(0f, radius);
        this.edgeOnly = edgeOnly;
    }

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
