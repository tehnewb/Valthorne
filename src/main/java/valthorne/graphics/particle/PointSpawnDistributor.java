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

    @Override
    public void computeOffset(Random random, float[] out) {
        out[0] = 0f;
        out[1] = 0f;
    }
}
