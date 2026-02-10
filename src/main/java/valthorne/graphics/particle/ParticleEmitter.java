package valthorne.graphics.particle;

import valthorne.graphics.Color;

/**
 * Configuration object that controls how {@link ParticleSystem} spawns and initializes particles.
 *
 * <p>This class is intentionally a <b>mutable</b> parameter bag with a fluent API, designed to be edited
 * at runtime (e.g., changing emission rate, colors, gravity) without reallocating or rebuilding the
 * {@link ParticleSystem}.</p>
 *
 * <h2>Responsibilities</h2>
 * <ul>
 *     <li><b>Spawn rate</b>: Controls continuous emission via {@link #emissionRate} and {@link #spawnAccumulator}.</li>
 *     <li><b>Initialization ranges</b>: Life, speed, angle, starting rotation, rotation speed, scale endpoints.</li>
 *     <li><b>Forces</b>: Simple constant acceleration inputs used by {@link ParticleSystem} (gravity Y and wind X).</li>
 *     <li><b>Rendering defaults</b>: Base quad size and optional texture atlas region.</li>
 *     <li><b>Spawn distribution</b>: Delegates spawn offsets to a {@link SpawnDistributor} strategy.</li>
 * </ul>
 *
 * <h2>Notes</h2>
 * <ul>
 *     <li>{@link #spawnAccumulator} is owned/advanced by {@link ParticleSystem}; treat it as internal state.</li>
 *     <li>{@link #startColor} and {@link #endColor} are stored as mutable {@link Color} instances to avoid allocations.</li>
 *     <li>The region values are stored in the same coordinate units expected by your {@code Texture.setRegion(...)} usage.</li>
 * </ul>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * ParticleEmitter e = new ParticleEmitter()
 *     .setEmissionRate(120f)
 *     .setLifetime(0.15f, 0.5f)
 *     .setVelocity(40f, 180f)
 *     .setAngleDeg(0f, 360f)
 *     .setGravity(0f, -350f)
 *     .setWind(25f, 0f)
 *     .setBaseSize(12f, 12f)
 *     .setStartEndScale(1f, 0f)
 *     .setRotationSpeed(-90f, 90f)
 *     .setStartEndColor(new Color(1f, 0.9f, 0.2f, 1f), new Color(1f, 0.2f, 0f, 0f))
 *     .setSpawnCircle(32f, false)
 *     .setRegion(0, 0, 16, 16);
 * }</pre>
 *
 * @author Albert Beaupre
 * @since February 10th, 2026
 */
public class ParticleEmitter {

    private float emissionRate = 60f;                      // Continuous emission rate in particles per second.
    private float spawnAccumulator;                        // Accumulator used to convert fractional spawns into integer spawns over time.

    private float lifeMin = 0.3f;                          // Minimum particle lifetime in seconds (inclusive).
    private float lifeMax = 0.8f;                          // Maximum particle lifetime in seconds (inclusive-ish; depends on random).

    private float speedMin = 20f;                          // Minimum initial speed magnitude in units/second.
    private float speedMax = 120f;                         // Maximum initial speed magnitude in units/second.

    private float angleMinDeg = 0f;                        // Minimum initial velocity angle in degrees.
    private float angleMaxDeg = 360f;                      // Maximum initial velocity angle in degrees.

    private float gravityY = -200f;                        // Constant Y acceleration applied each update (units/second^2).
    private float windX = 0f;                              // Constant X acceleration applied each update (units/second^2).

    private float baseWidth = 16f;                         // Base particle quad width before per-particle scaling.
    private float baseHeight = 16f;                        // Base particle quad height before per-particle scaling.

    private float startScale = 1f;                         // Starting scale value assigned to newly spawned particles.
    private float endScale = 0f;                           // Ending scale value used during scale interpolation over lifetime.

    private float startRotMinDeg = 0f;                     // Minimum initial rotation in degrees.
    private float startRotMaxDeg = 0f;                     // Maximum initial rotation in degrees.

    private float rotSpeedMinDegPerSec = 0f;               // Minimum rotation speed in degrees/second.
    private float rotSpeedMaxDegPerSec = 0f;               // Maximum rotation speed in degrees/second.

    private final Color startColor = new Color(1f, 1f, 1f, 1f); // Starting color used when initializing a particle (copied into Particle.startColor).
    private final Color endColor = new Color(1f, 1f, 1f, 0f);   // Ending color used for lifetime interpolation (copied into Particle.endColor).

    private boolean useRegion;                              // Whether the particle renderer should apply a texture sub-region (atlas) to the shared Texture.
    private float regionLeft, regionTop, regionRight, regionBottom; // Region rectangle coordinates used by ParticleSystem when calling Texture.setRegion(...).

    private SpawnDistributor shape = new PointSpawnDistributor(); // Spawn offset strategy (defaults to point/zero offset).

    /**
     * Sets continuous emission rate.
     *
     * @param particlesPerSecond particles spawned per second (clamped to {@code >= 0})
     * @return this emitter for chaining
     */
    public ParticleEmitter setEmissionRate(float particlesPerSecond) {
        this.emissionRate = Math.max(0f, particlesPerSecond);
        return this;
    }

    /**
     * Sets lifetime range for newly spawned particles.
     *
     * @param minSeconds minimum lifetime in seconds (clamped to {@code >= 0})
     * @param maxSeconds maximum lifetime in seconds (clamped to {@code >= minSeconds})
     * @return this emitter for chaining
     */
    public ParticleEmitter setLifetime(float minSeconds, float maxSeconds) {
        this.lifeMin = Math.max(0f, minSeconds);
        this.lifeMax = Math.max(this.lifeMin, maxSeconds);
        return this;
    }

    /**
     * Sets initial speed range for newly spawned particles.
     *
     * @param minSpeed minimum speed magnitude (clamped to {@code >= 0})
     * @param maxSpeed maximum speed magnitude (clamped to {@code >= minSpeed})
     * @return this emitter for chaining
     */
    public ParticleEmitter setVelocity(float minSpeed, float maxSpeed) {
        this.speedMin = Math.max(0f, minSpeed);
        this.speedMax = Math.max(this.speedMin, maxSpeed);
        return this;
    }

    /**
     * Sets initial direction range (in degrees) for newly spawned particles.
     *
     * <p>This range is sampled uniformly and used with the sampled speed to produce initial velocity.</p>
     *
     * @param minDeg minimum angle in degrees
     * @param maxDeg maximum angle in degrees
     * @return this emitter for chaining
     */
    public ParticleEmitter setAngleDeg(float minDeg, float maxDeg) {
        this.angleMinDeg = minDeg;
        this.angleMaxDeg = maxDeg;
        return this;
    }

    /**
     * Sets gravity acceleration.
     *
     * <p>This API keeps an X parameter for symmetry, but this implementation currently uses only Y.</p>
     *
     * @param x gravity X (ignored)
     * @param y gravity Y acceleration in units/second^2
     * @return this emitter for chaining
     */
    public ParticleEmitter setGravity(float x, float y) {
        this.gravityY = y;
        return this;
    }

    /**
     * Sets wind acceleration.
     *
     * <p>This API keeps a Y parameter for symmetry, but this implementation currently uses only X.</p>
     *
     * @param x wind X acceleration in units/second^2
     * @param y wind Y (ignored)
     * @return this emitter for chaining
     */
    public ParticleEmitter setWind(float x, float y) {
        this.windX = x;
        return this;
    }

    /**
     * Sets the base quad size applied to every particle before per-particle scaling.
     *
     * @param w base width (clamped to {@code >= 0})
     * @param h base height (clamped to {@code >= 0})
     * @return this emitter for chaining
     */
    public ParticleEmitter setBaseSize(float w, float h) {
        this.baseWidth = Math.max(0f, w);
        this.baseHeight = Math.max(0f, h);
        return this;
    }

    /**
     * Sets scale endpoints used for interpolation over particle lifetime.
     *
     * @param start starting scale value
     * @param end ending scale value
     * @return this emitter for chaining
     */
    public ParticleEmitter setStartEndScale(float start, float end) {
        this.startScale = start;
        this.endScale = end;
        return this;
    }

    /**
     * Sets initial rotation range (in degrees) for newly spawned particles.
     *
     * @param startMinDeg minimum initial rotation in degrees
     * @param startMaxDeg maximum initial rotation in degrees
     * @return this emitter for chaining
     */
    public ParticleEmitter setStartEndRotation(float startMinDeg, float startMaxDeg) {
        this.startRotMinDeg = startMinDeg;
        this.startRotMaxDeg = startMaxDeg;
        return this;
    }

    /**
     * Sets rotation speed range (in degrees/second) for newly spawned particles.
     *
     * @param minDegPerSec minimum rotation speed
     * @param maxDegPerSec maximum rotation speed
     * @return this emitter for chaining
     */
    public ParticleEmitter setRotationSpeed(float minDegPerSec, float maxDegPerSec) {
        this.rotSpeedMinDegPerSec = minDegPerSec;
        this.rotSpeedMaxDegPerSec = maxDegPerSec;
        return this;
    }

    /**
     * Sets color endpoints used for interpolation over particle lifetime.
     *
     * <p>This copies the provided colors into internal reusable {@link Color} instances.</p>
     *
     * @param start start color (non-null)
     * @param end end color (non-null)
     * @return this emitter for chaining
     * @throws NullPointerException if start or end is null
     */
    public ParticleEmitter setStartEndColor(Color start, Color end) {
        if (start == null) throw new NullPointerException("start color cannot be null");
        if (end == null) throw new NullPointerException("end color cannot be null");
        this.startColor.set(start);
        this.endColor.set(end);
        return this;
    }

    /**
     * Enables region usage and sets the region rectangle.
     *
     * <p>The {@link ParticleSystem} will call {@code Texture.setRegion(left, top, right, bottom)} every draw.</p>
     *
     * @param left left region coordinate
     * @param top top region coordinate
     * @param right right region coordinate
     * @param bottom bottom region coordinate
     * @return this emitter for chaining
     */
    public ParticleEmitter setRegion(float left, float top, float right, float bottom) {
        this.useRegion = true;
        this.regionLeft = left;
        this.regionTop = top;
        this.regionRight = right;
        this.regionBottom = bottom;
        return this;
    }

    /**
     * Disables region usage.
     *
     * <p>The region rectangle values are preserved but ignored until {@link #setRegion(float, float, float, float)} is called again.</p>
     *
     * @return this emitter for chaining
     */
    public ParticleEmitter clearRegion() {
        this.useRegion = false;
        return this;
    }

    /**
     * Sets the spawn distribution strategy.
     *
     * <p>The {@link SpawnDistributor} computes an offset (dx, dy) from the system's base position each spawn.</p>
     *
     * @param shape spawn distribution strategy (non-null)
     * @return this emitter for chaining
     * @throws NullPointerException if shape is null
     */
    public ParticleEmitter setShape(SpawnDistributor shape) {
        if (shape == null) throw new NullPointerException("shape cannot be null");
        this.shape = shape;
        return this;
    }

    /**
     * Convenience helper that sets point spawning (zero offset).
     *
     * @return this emitter for chaining
     */
    public ParticleEmitter setSpawnPoint() {
        this.shape = new PointSpawnDistributor();
        return this;
    }

    /**
     * Convenience helper that sets circle spawning.
     *
     * @param radius circle radius (passed to distributor)
     * @param edgeOnly if true, spawns on the circle edge; otherwise spawns over the area
     * @return this emitter for chaining
     */
    public ParticleEmitter setSpawnCircle(float radius, boolean edgeOnly) {
        this.shape = new CircleSpawnDistributor(radius, edgeOnly);
        return this;
    }

    /**
     * Convenience helper that sets box spawning.
     *
     * @param halfWidth half-width of the box in X
     * @param halfHeight half-height of the box in Y
     * @return this emitter for chaining
     */
    public ParticleEmitter setSpawnBox(float halfWidth, float halfHeight) {
        this.shape = new BoxSpawnDistributor(halfWidth, halfHeight);
        return this;
    }

    // ---- Getters used by ParticleSystem ----

    /**
     * @return continuous emission rate in particles per second
     */
    public float getEmissionRate() { return emissionRate; }

    /**
     * @return spawn accumulator used to convert fractional spawns into whole spawns
     */
    public float getSpawnAccumulator() { return spawnAccumulator; }

    /**
     * Sets the spawn accumulator value.
     *
     * <p>This is typically updated by {@link ParticleSystem} and not manually by gameplay code.</p>
     *
     * @param spawnAccumulator new accumulator value
     */
    public void setSpawnAccumulator(float spawnAccumulator) { this.spawnAccumulator = spawnAccumulator; }

    /**
     * @return minimum lifetime in seconds
     */
    public float getLifeMin() { return lifeMin; }

    /**
     * @return maximum lifetime in seconds
     */
    public float getLifeMax() { return lifeMax; }

    /**
     * @return minimum initial speed magnitude
     */
    public float getSpeedMin() { return speedMin; }

    /**
     * @return maximum initial speed magnitude
     */
    public float getSpeedMax() { return speedMax; }

    /**
     * @return minimum initial velocity angle in degrees
     */
    public float getAngleMinDeg() { return angleMinDeg; }

    /**
     * @return maximum initial velocity angle in degrees
     */
    public float getAngleMaxDeg() { return angleMaxDeg; }

    /**
     * @return constant Y acceleration (gravity) in units/second^2
     */
    public float getGravityY() { return gravityY; }

    /**
     * @return constant X acceleration (wind) in units/second^2
     */
    public float getWindX() { return windX; }

    /**
     * @return base particle width before per-particle scaling
     */
    public float getBaseWidth() { return baseWidth; }

    /**
     * @return base particle height before per-particle scaling
     */
    public float getBaseHeight() { return baseHeight; }

    /**
     * @return starting scale used when initializing particles
     */
    public float getStartScale() { return startScale; }

    /**
     * @return ending scale used during lifetime interpolation
     */
    public float getEndScale() { return endScale; }

    /**
     * @return minimum initial rotation in degrees
     */
    public float getStartRotMinDeg() { return startRotMinDeg; }

    /**
     * @return maximum initial rotation in degrees
     */
    public float getStartRotMaxDeg() { return startRotMaxDeg; }

    /**
     * @return minimum rotation speed in degrees/second
     */
    public float getRotSpeedMinDegPerSec() { return rotSpeedMinDegPerSec; }

    /**
     * @return maximum rotation speed in degrees/second
     */
    public float getRotSpeedMaxDegPerSec() { return rotSpeedMaxDegPerSec; }

    /**
     * @return reusable start color instance (do not replace; you may mutate its channels if desired)
     */
    public Color getStartColor() { return startColor; }

    /**
     * @return reusable end color instance (do not replace; you may mutate its channels if desired)
     */
    public Color getEndColor() { return endColor; }

    /**
     * @return true if region rendering is enabled
     */
    public boolean isUseRegion() { return useRegion; }

    /**
     * @return region left coordinate
     */
    public float getRegionLeft() { return regionLeft; }

    /**
     * @return region top coordinate
     */
    public float getRegionTop() { return regionTop; }

    /**
     * @return region right coordinate
     */
    public float getRegionRight() { return regionRight; }

    /**
     * @return region bottom coordinate
     */
    public float getRegionBottom() { return regionBottom; }

    /**
     * @return current spawn distribution strategy
     */
    public SpawnDistributor getShape() { return shape; }
}