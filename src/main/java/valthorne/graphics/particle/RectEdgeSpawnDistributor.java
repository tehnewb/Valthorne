package valthorne.graphics.particle;

import valthorne.math.MathUtils;

import java.util.Random;

/**
 * A {@code RectEdgeSpawnDistributor} is responsible for distributing spawn offsets along the edges
 * of a rectangle. The rectangle is defined by its half-width and half-height. Spawn positions
 * are randomly chosen along one of the four edges: top, right, bottom, or left.
 * The output offsets are computed based on a random choice of edge and a linear interpolation
 * along the chosen edge.
 * <p>
 * This implementation ensures that the computed offsets always lie on the perimeter of the rectangle.
 * The {@link MathUtils#lerp} method is used for interpolating positions along the edges.
 *
 * @author Albert Beaupre
 * @since February 10th, 2026
 */
public final class RectEdgeSpawnDistributor implements SpawnDistributor {

    private final float halfWidth; // Horizontal rectangle half-extent about the origin.
    private final float halfHeight; // Vertical rectangle half-extent about the origin.

    /**
     * Creates a centered rectangular edge distribution, clamping each negative
     * half-extent to zero.
     *
     * @param halfWidth horizontal half-extent
     * @param halfHeight vertical half-extent
     */
    public RectEdgeSpawnDistributor(float halfWidth, float halfHeight) {
        this.halfWidth = Math.max(0f, halfWidth);
        this.halfHeight = Math.max(0f, halfHeight);
    }

    /**
     * Chooses each of four edges with equal probability, then samples uniformly
     * along that edge. This is not uniform per unit perimeter length when width
     * and height differ.
     *
     * @param random nonnull random source
     * @param out nonnull destination with at least two entries; X then Y
     * @throws NullPointerException if random or out is null
     * @throws ArrayIndexOutOfBoundsException if out has fewer than two entries
     */
    @Override
    public void computeOffset(Random random, float[] out) {
        float t = random.nextFloat();
        switch (random.nextInt(4)) {
            case 0 -> { // top
                out[0] = MathUtils.lerp(-halfWidth, halfWidth, t);
                out[1] = halfHeight;
            }
            case 1 -> { // right
                out[0] = halfWidth;
                out[1] = MathUtils.lerp(-halfHeight, halfHeight, t);
            }
            case 2 -> { // bottom
                out[0] = MathUtils.lerp(-halfWidth, halfWidth, t);
                out[1] = -halfHeight;
            }
            default -> { // left
                out[0] = -halfWidth;
                out[1] = MathUtils.lerp(-halfHeight, halfHeight, t);
            }
        }
    }
}
