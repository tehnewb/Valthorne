package valthorne.graphics.particle;

import java.util.Random;

/**
 * A concrete implementation of the {@link SpawnDistributor} interface. The
 * {@code PointSpawnDistributor} provides a fixed point spawn distribution,
 * where all offsets are consistently set to zero. This creates a static
 * positioning system with no variation, ensuring all entities or particles
 * spawn at the origin.
 *
 * @author Albert Beaupre
 * @since February 10th, 2026
 */
public final class PointSpawnDistributor implements SpawnDistributor {

    /**
     * Writes a zero offset, leaving the particle at the emitter origin. Does not
     * read or advance the supplied random generator.
     *
     * @param random unused generator, possibly null
     * @param out nonnull destination with at least two entries; X then Y
     * @throws NullPointerException if out is null
     * @throws ArrayIndexOutOfBoundsException if out has fewer than two entries
     */
    @Override
    public void computeOffset(Random random, float[] out) {
        out[0] = 0f;
        out[1] = 0f;
    }
}
