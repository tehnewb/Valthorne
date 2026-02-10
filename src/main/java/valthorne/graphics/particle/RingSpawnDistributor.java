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

    private final float innerRadius;
    private final float outerRadius;

    public RingSpawnDistributor(float innerRadius, float outerRadius) {
        this.innerRadius = Math.max(0f, innerRadius);
        this.outerRadius = Math.max(this.innerRadius, outerRadius);
    }

    @Override
    public void computeOffset(Random random, float[] out) {
        float a = (float) (random.nextFloat() * Math.PI * 2.0);

        float u = random.nextFloat();
        float r = (float) Math.sqrt(MathUtils.lerp(innerRadius * innerRadius, outerRadius * outerRadius, u));

        out[0] = (float) Math.cos(a) * r;
        out[1] = (float) Math.sin(a) * r;
    }
}
