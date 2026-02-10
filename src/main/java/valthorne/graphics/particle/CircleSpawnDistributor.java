package valthorne.graphics.particle;

import java.util.Random;

/**
 * A {@code CircleSpawnDistributor} is an implementation of the {@code SpawnDistributor} interface
 * that calculates spawn offsets within a circular region. Spawn positions can either be uniformly
 * distributed within the circle's area or restricted to its edge.
 * <p>
 * This class utilizes a radius to define the circle's size and a boolean flag to determine
 * whether the spawn offsets are restricted to the perimeter of the circle.
 *
 * @author Albert Beaupre
 * @since February 10th, 2026
 */
public final class CircleSpawnDistributor implements SpawnDistributor {

    private float radius;
    private boolean edgeOnly;

    public CircleSpawnDistributor(float radius, boolean edgeOnly) {
        this.radius = Math.max(0f, radius);
        this.edgeOnly = edgeOnly;
    }

    public float getRadius() {
        return radius;
    }

    public CircleSpawnDistributor setRadius(float radius) {
        this.radius = Math.max(0f, radius);
        return this;
    }

    public boolean isEdgeOnly() {
        return edgeOnly;
    }

    public CircleSpawnDistributor setEdgeOnly(boolean edgeOnly) {
        this.edgeOnly = edgeOnly;
        return this;
    }

    @Override
    public void computeOffset(Random random, float[] out) {
        float r = radius;
        if (!edgeOnly) r *= (float) Math.sqrt(random.nextFloat()); // uniform area
        float a = (float) (random.nextFloat() * Math.PI * 2.0f);

        out[0] = (float) Math.cos(a) * r;
        out[1] = (float) Math.sin(a) * r;
    }
}
