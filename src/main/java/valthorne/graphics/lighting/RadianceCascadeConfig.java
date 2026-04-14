package valthorne.graphics.lighting;

/**
 * Immutable configuration for standalone radiance-cascade lighting.
 *
 * <p>{@code baseRays} is the actual cascade-0 ray count before pre-averaging.
 * The current renderer stores four angularly adjacent rays per texel, so
 * {@code baseRays} must be a multiple of 4.
 */
public final class RadianceCascadeConfig {

    public static final int DEFAULT_CASCADES = 5;
    public static final int DEFAULT_BASE_RAYS = 4;
    public static final float DEFAULT_BASE_SPACING = 8f;
    public static final float DEFAULT_BASE_RANGE = 8f;
    public static final int DEFAULT_MARCH_STEPS = 32;
    public static final float DEFAULT_EXPOSURE = 1f;

    public final int cascades;
    public final int baseRays;
    public final float baseSpacing;
    public final float baseRange;
    public final int marchSteps;
    public final float exposure;

    public RadianceCascadeConfig(int cascades, int baseRays, float baseSpacing, float baseRange,
                                 int marchSteps, float exposure) {
        if (cascades < 1) throw new IllegalArgumentException("cascades must be >= 1");
        if (baseRays < 4) throw new IllegalArgumentException("baseRays must be >= 4");
        if ((baseRays & (CascadeLevel.PREAVERAGE_RAY_COUNT - 1)) != 0) {
            throw new IllegalArgumentException("baseRays must be a multiple of 4");
        }
        if (baseSpacing <= 0f) throw new IllegalArgumentException("baseSpacing must be > 0");
        if (baseRange <= 0f) throw new IllegalArgumentException("baseRange must be > 0");
        if (marchSteps < 1) throw new IllegalArgumentException("marchSteps must be >= 1");
        if (exposure <= 0f) throw new IllegalArgumentException("exposure must be > 0");

        this.cascades = cascades;
        this.baseRays = baseRays;
        this.baseSpacing = baseSpacing;
        this.baseRange = baseRange;
        this.marchSteps = marchSteps;
        this.exposure = exposure;
    }

    public static RadianceCascadeConfig defaults() {
        return new RadianceCascadeConfig(
                DEFAULT_CASCADES,
                DEFAULT_BASE_RAYS,
                DEFAULT_BASE_SPACING,
                DEFAULT_BASE_RANGE,
                DEFAULT_MARCH_STEPS,
                DEFAULT_EXPOSURE
        );
    }

    public RadianceCascadeConfig withCascades(int value) {
        return new RadianceCascadeConfig(value, baseRays, baseSpacing, baseRange, marchSteps, exposure);
    }

    public RadianceCascadeConfig withBaseRays(int value) {
        return new RadianceCascadeConfig(cascades, value, baseSpacing, baseRange, marchSteps, exposure);
    }

    public RadianceCascadeConfig withBaseSpacing(float value) {
        return new RadianceCascadeConfig(cascades, baseRays, value, baseRange, marchSteps, exposure);
    }

    public RadianceCascadeConfig withBaseRange(float value) {
        return new RadianceCascadeConfig(cascades, baseRays, baseSpacing, value, marchSteps, exposure);
    }

    public RadianceCascadeConfig withMarchSteps(int value) {
        return new RadianceCascadeConfig(cascades, baseRays, baseSpacing, baseRange, value, exposure);
    }

    public RadianceCascadeConfig withExposure(float value) {
        return new RadianceCascadeConfig(cascades, baseRays, baseSpacing, baseRange, marchSteps, value);
    }
}
