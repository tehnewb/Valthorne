package valthorne.graphics.particle;

import java.util.Random;

/**
 * The SpiralSpawnDistributor is an implementation of the SpawnDistributor interface.
 * This class generates stochastic spawn offsets based on a spiral pattern.
 * The spiral is defined by a maximum radius and a number of turns, both of which
 * influence the distribution of offsets.
 * <p>
 * The spawn offsets are computed by mapping a random value onto a spiral equation,
 * resulting in offsets distributed along the spiral's path in 2D space.
 *
 * @author Albert Beaupre
 * @since February 10th, 2026
 */
public final class SpiralSpawnDistributor implements SpawnDistributor {

    private final float maxRadius; // Maximum radial extent, clamped against zero.
    private final float turns; // Number of spiral revolutions, clamped against zero.

    /**
     * Creates an Archimedean spiral parameterization, clamping negative radius and
     * turn count to zero. Values are not checked for finiteness.
     *
     * @param maxRadius final spiral radius
     * @param turns revolutions from the origin to the outer end
     */
    public SpiralSpawnDistributor(float maxRadius, float turns) {
        this.maxRadius = Math.max(0f, maxRadius);
        this.turns = Math.max(0f, turns);
    }

    /**
     * Samples one uniform parameter t and evaluates radius maxRadius*t and angle
     * 2*pi*turns*t. Sampling is uniform in the parameter, not arc length or area.
     *
     * @param random nonnull random source
     * @param out nonnull destination with at least two entries; X then Y
     * @throws NullPointerException if random or out is null
     * @throws ArrayIndexOutOfBoundsException if out has fewer than two entries
     */
    @Override
    public void computeOffset(Random random, float[] out) {
        float t = random.nextFloat();
        float r = maxRadius * t;
        float a = (float) (t * turns * Math.PI * 2.0f);

        out[0] = (float) Math.cos(a) * r;
        out[1] = (float) Math.sin(a) * r;
    }
}
