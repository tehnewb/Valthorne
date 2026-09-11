package valthorne.graphics.particle;

import valthorne.math.MathUtils;

import java.util.Random;

/**
 * The LineSpawnDistributor class provides an implementation of the SpawnDistributor interface
 * where spawn positions are distributed along a straight line defined by two endpoints.
 * The positions are computed by interpolating between the start and end points using
 * randomly generated weights.
 *
 * @author Albert Beaupre
 * @since February 10th, 2026
 */
public final class LineSpawnDistributor implements SpawnDistributor {

    private float x0, y0; // First endpoint in emitter-local coordinates.
    private float x1, y1; // Second endpoint in emitter-local coordinates.

    /**
     * Stores the endpoints of a line distribution without numeric validation.
     *
     * @param x0 first endpoint X
     * @param y0 first endpoint Y
     * @param x1 second endpoint X
     * @param y1 second endpoint Y
     */
    public LineSpawnDistributor(float x0, float y0, float x1, float y1) {
        this.x0 = x0;
        this.y0 = y0;
        this.x1 = x1;
        this.y1 = y1;
    }

    /**
     * Uses one uniform interpolation parameter for both coordinates, producing
     * uniform distance sampling along the segment. The first endpoint is possible;
     * the second is excluded except for degenerate segments.
     *
     * @param random nonnull random source
     * @param out nonnull destination with at least two entries; X then Y
     * @throws NullPointerException if random or out is null
     * @throws ArrayIndexOutOfBoundsException if out has fewer than two entries
     */
    @Override
    public void computeOffset(Random random, float[] out) {
        float t = random.nextFloat();
        out[0] = MathUtils.lerp(x0, x1, t);
        out[1] = MathUtils.lerp(y0, y1, t);
    }
}
