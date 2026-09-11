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

    private float radius; // Radius about the origin, clamped against zero.
    private boolean edgeOnly; // Whether sampling uses only the circumference.

    /**
     * Creates a circular distribution with negative radius clamped to zero.
     *
     * @param radius circle radius
     * @param edgeOnly true for circumference sampling, false for uniform area
     */
    public CircleSpawnDistributor(float radius, boolean edgeOnly) {
        this.radius = Math.max(0f, radius);
        this.edgeOnly = edgeOnly;
    }

    /**
     * Returns the configured maximum radius.
     *
     * @return radius in emitter coordinate units
     */
    public float getRadius() {
        return radius;
    }

    /**
     * Stores a radius clamped against zero; nonfinite values are not rejected.
     *
     * @param radius requested radius
     * @return this distributor
     */
    public CircleSpawnDistributor setRadius(float radius) {
        this.radius = Math.max(0f, radius);
        return this;
    }

    /**
     * Reports whether samples lie only on the circumference.
     *
     * @return true for edge-only sampling
     */
    public boolean isEdgeOnly() {
        return edgeOnly;
    }

    /**
     * Switches between circumference and uniform-area sampling without changing radius.
     *
     * @param edgeOnly true for circumference samples
     * @return this distributor
     */
    public CircleSpawnDistributor setEdgeOnly(boolean edgeOnly) {
        this.edgeOnly = edgeOnly;
        return this;
    }

    /**
     * Samples a uniform angle and either the fixed radius or a square-root-scaled
     * radius for uniform area density. Consumes one random float in edge mode and
     * two in area mode.
     *
     * @param random nonnull random source
     * @param out nonnull destination with at least two entries; X then Y
     * @throws NullPointerException if random or out is null
     * @throws ArrayIndexOutOfBoundsException if out has fewer than two entries
     */
    @Override
    public void computeOffset(Random random, float[] out) {
        float r = radius;
        if (!edgeOnly) r *= (float) Math.sqrt(random.nextFloat()); // uniform area
        float a = (float) (random.nextFloat() * Math.PI * 2.0f);

        out[0] = (float) Math.cos(a) * r;
        out[1] = (float) Math.sin(a) * r;
    }
}
