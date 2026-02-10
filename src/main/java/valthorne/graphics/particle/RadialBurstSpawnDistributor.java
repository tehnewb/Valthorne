package valthorne.graphics.particle;

import java.util.Random;

/**
 * A radial burst spawn distributor that calculates offsets for positioning entities or particles
 * in a circular burst pattern around an origin point. The spawn positions are distributed
 * along the circumference of a circle with the specified radius.
 * <p>
 * This implementation uses a provided random number generator to generate random angles
 * (in radians), and calculates the corresponding (x, y) offsets for positions
 * around the circle.
 * <p>
 * The computed offsets are written into the provided output array, indexed as follows:
 * - Index 0: X-offset
 * - Index 1: Y-offset
 * <p>
 * The output values reflect positions on a circle defined by the radius of this distributor.
 *
 * @author Albert Beaupre
 * @since February 10th, 2026
 */
public final class RadialBurstSpawnDistributor implements SpawnDistributor {

    private final float radius;

    public RadialBurstSpawnDistributor(float radius) {
        this.radius = Math.max(0f, radius);
    }

    @Override
    public void computeOffset(Random random, float[] out) {
        float a = (float) (random.nextFloat() * Math.PI * 2.0f);
        out[0] = (float) Math.cos(a) * radius;
        out[1] = (float) Math.sin(a) * radius;
    }
}
