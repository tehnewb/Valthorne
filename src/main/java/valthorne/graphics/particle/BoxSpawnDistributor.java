package valthorne.graphics.particle;

import java.util.Random;

/**
 * A concrete implementation of the {@link SpawnDistributor} interface that distributes
 * spawn locations within the bounds of a rectangular area. The rectangle is defined
 * by the half-width and half-height, with the center of the rectangle at the origin.
 * <p>
 * The spawn offsets are generated stochastically, ensuring that the resulting positions
 * are uniformly distributed within the rectangle.
 *
 * @author Albert Beaupre
 * @since February 10th, 2026
 */
public final class BoxSpawnDistributor implements SpawnDistributor {

    private float halfWidth;
    private float halfHeight;

    public BoxSpawnDistributor(float halfWidth, float halfHeight) {
        this.halfWidth = Math.max(0f, halfWidth);
        this.halfHeight = Math.max(0f, halfHeight);
    }

    public float getHalfWidth() {
        return halfWidth;
    }

    public BoxSpawnDistributor setHalfWidth(float halfWidth) {
        this.halfWidth = Math.max(0f, halfWidth);
        return this;
    }

    public float getHalfHeight() {
        return halfHeight;
    }

    public BoxSpawnDistributor setHalfHeight(float halfHeight) {
        this.halfHeight = Math.max(0f, halfHeight);
        return this;
    }

    @Override
    public void computeOffset(Random random, float[] out) {
        out[0] = -halfWidth + random.nextFloat() * (halfWidth * 2f);
        out[1] = -halfHeight + random.nextFloat() * (halfHeight * 2f);
    }
}
