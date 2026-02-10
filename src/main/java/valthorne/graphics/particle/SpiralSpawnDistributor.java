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

    private final float maxRadius;
    private final float turns;

    public SpiralSpawnDistributor(float maxRadius, float turns) {
        this.maxRadius = Math.max(0f, maxRadius);
        this.turns = Math.max(0f, turns);
    }

    @Override
    public void computeOffset(Random random, float[] out) {
        float t = random.nextFloat();
        float r = maxRadius * t;
        float a = (float) (t * turns * Math.PI * 2.0f);

        out[0] = (float) Math.cos(a) * r;
        out[1] = (float) Math.sin(a) * r;
    }
}
