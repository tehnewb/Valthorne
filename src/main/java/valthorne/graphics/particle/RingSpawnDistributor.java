package valthorne.graphics.particle;

import valthorne.math.MathUtils;

import java.util.Random;

/**
 * Implements the {@link SpawnDistributor} interface to distribute spawn locations
 * within a ring-shaped area. The spawn points are calculated randomly and uniformly
 * between the inner and outer radii of the ring.
 *
 * @author Albert Beaupre
 * @since February 10th, 2026
 */
public final class RingSpawnDistributor implements SpawnDistributor {

    private final float innerRadius; // Inner radius clamped against zero.
    private final float outerRadius; // Outer radius clamped to at least the effective inner radius.

    /**
     * Clamps inner radius to zero and outer radius to at least that result. Equal
     * radii produce circumference sampling; nonfinite inputs are not rejected.
     *
     * @param innerRadius requested inner radius
     * @param outerRadius requested outer radius
     */
    public RingSpawnDistributor(float innerRadius, float outerRadius) {
        this.innerRadius = Math.max(0f, innerRadius);
        this.outerRadius = Math.max(this.innerRadius, outerRadius);
    }

    /**
     * Samples a uniform angle and interpolates squared radii before taking a square
     * root, giving uniform area density across the annulus for valid finite radii.
     *
     * @param random nonnull random source
     * @param out nonnull destination with at least two entries; X then Y
     * @throws NullPointerException if random or out is null
     * @throws ArrayIndexOutOfBoundsException if out has fewer than two entries
     */
    @Override
    public void computeOffset(Random random, float[] out) {
        float a = (float) (random.nextFloat() * Math.PI * 2.0);

        float u = random.nextFloat();
        float r = (float) Math.sqrt(MathUtils.lerp(innerRadius * innerRadius, outerRadius * outerRadius, u));

        out[0] = (float) Math.cos(a) * r;
        out[1] = (float) Math.sin(a) * r;
    }
}
