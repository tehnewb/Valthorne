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

    private float halfWidth; // Horizontal half-extent about the origin.
    private float halfHeight; // Vertical half-extent about the origin.

    /**
     * Creates a centered rectangular distribution, clamping negative half-extents
     * to zero. Nonfinite values are not rejected.
     *
     * @param halfWidth horizontal half-extent
     * @param halfHeight vertical half-extent
     */
    public BoxSpawnDistributor(float halfWidth, float halfHeight) {
        this.halfWidth = Math.max(0f, halfWidth);
        this.halfHeight = Math.max(0f, halfHeight);
    }

    /**
     * Returns the stored width half-extent.
     *
     * @return half-extent in emitter coordinate units
     */
    public float getHalfWidth() {
        return halfWidth;
    }

    /**
     * Sets the width half-extent, clamping negative values to zero.
     * Nonfinite values are not rejected.
     *
     * @param halfWidth requested half-extent
     * @return this distributor
     */
    public BoxSpawnDistributor setHalfWidth(float halfWidth) {
        this.halfWidth = Math.max(0f, halfWidth);
        return this;
    }

    /**
     * Returns the stored height half-extent.
     *
     * @return half-extent in emitter coordinate units
     */
    public float getHalfHeight() {
        return halfHeight;
    }

    /**
     * Sets the height half-extent, clamping negative values to zero.
     * Nonfinite values are not rejected.
     *
     * @param halfHeight requested half-extent
     * @return this distributor
     */
    public BoxSpawnDistributor setHalfHeight(float halfHeight) {
        this.halfHeight = Math.max(0f, halfHeight);
        return this;
    }

    /**
     * Samples X and Y independently and uniformly inside the centered rectangle.
     * The positive edges are excluded by Random.nextFloat's half-open interval.
     *
     * @param random nonnull random source
     * @param out nonnull destination with at least two entries; X then Y
     * @throws NullPointerException if random or out is null
     * @throws ArrayIndexOutOfBoundsException if out has fewer than two entries
     */
    @Override
    public void computeOffset(Random random, float[] out) {
        out[0] = -halfWidth + random.nextFloat() * (halfWidth * 2f);
        out[1] = -halfHeight + random.nextFloat() * (halfHeight * 2f);
    }
}
