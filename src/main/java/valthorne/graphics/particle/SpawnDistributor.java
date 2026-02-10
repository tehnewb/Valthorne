package valthorne.graphics.particle;

import java.util.Random;

/**
 * An interface representing a mechanism for distributing spawn offsets. Implementations
 * determine how positions are calculated for entities or particles relative to an origin.
 * The offsets are generated stochastically, using a random number generator.
 *
 * @author Albert Beaupre
 * @since February 10th, 2026
 */
public interface SpawnDistributor {

    /**
     * Computes and writes spawn offsets into the provided output array.
     * The computed offsets are based on a provided random number generator,
     * allowing stochastic variations for distributing entities or particles.
     *
     * @param random the random number generator used for stochastic offset calculation
     * @param out    the array to store the computed offset values, must be pre-initialized and of sufficient length
     */
    void computeOffset(Random random, float[] out);
}
