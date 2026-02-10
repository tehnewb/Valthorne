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

    private float x0, y0;
    private float x1, y1;

    public LineSpawnDistributor(float x0, float y0, float x1, float y1) {
        this.x0 = x0;
        this.y0 = y0;
        this.x1 = x1;
        this.y1 = y1;
    }

    @Override
    public void computeOffset(Random random, float[] out) {
        float t = random.nextFloat();
        out[0] = MathUtils.lerp(x0, x1, t);
        out[1] = MathUtils.lerp(y0, y1, t);
    }
}
