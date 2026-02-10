package valthorne.graphics.particle;

import valthorne.graphics.Color;
import valthorne.graphics.texture.Texture;
import valthorne.io.pool.Pool;
import valthorne.math.MathUtils;

import java.util.Random;

/**
 * A lightweight, LibGDX-style CPU particle system that renders particles using a shared {@link Texture}.
 *
 * <h2>What this class does</h2>
 * <ul>
 *     <li><b>Spawning</b>: Emits particles over time using accumulators (rate * delta).</li>
 *     <li><b>Simulation</b>: Updates age, position, velocity, rotation, scale, and color on the CPU.</li>
 *     <li><b>Rendering</b>: Draws particles by mutating one shared {@link Texture} and calling {@link Texture#draw()}.</li>
 * </ul>
 *
 * <h2>Design constraints</h2>
 * <ul>
 *     <li>One shared {@link Texture} for all particles (no per-particle texture allocations).</li>
 *     <li>No allocations during {@link #update(float)} or {@link #draw()} after construction (assuming your {@link Pool} does not allocate on obtain/free).</li>
 *     <li>Active particles stored in a fixed array and compacted via swap-removal (O(n) update, no shifting).</li>
 * </ul>
 *
 * <h2>Burst behavior (frame-rate independent)</h2>
 * <p>
 * {@link #burst(int)} and {@link #burst(int, float)} queue burst particles. They are released over time using
 * an accumulator (particles/sec * delta), so the burst behaves consistently at different FPS.
 * </p>
 *
 * <h2>Rendering throttle (optional)</h2>
 * <p>
 * You can throttle rendering so you are not drawing every active particle every frame:
 * </p>
 * <ul>
 *     <li>{@link #setRenderInterval(float)}: draw only every N seconds (0 = every frame).</li>
 *     <li>{@link #setMaxDrawPerFrame(int)}: cap how many particles are drawn per {@link #draw()} call.</li>
 * </ul>
 * <p>
 * Simulation always updates <b>all</b> active particles every frame.
 * </p>
 *
 * <h2>Important</h2>
 * <ul>
 *     <li>This class does not manage OpenGL state. The caller must enable whatever {@link Texture#draw()} requires.</li>
 *     <li>The shared {@link Texture} is treated as a temporary “brush” and is mutated per particle before drawing.</li>
 *     <li>This system does not dispose the shared texture.</li>
 * </ul>
 * <h2>Example</h2>
 * <pre>{@code
 * Texture spark = new Texture("assets/particles/spark.png");
 * ParticleSystem ps = new ParticleSystem(spark, 2000);
 *
 * ps.setPosition(640, 360);
 * ps.getEmitter()
 *   .setEmissionRate(120)
 *   .setLifetime(0.2f, 0.6f)
 *   .setVelocity(40f, 180f)
 *   .setAngleDeg(0f, 360f)
 *   .setGravity(0f, -350f)
 *   .setWind(0f, 0f)
 *   .setBaseSize(12f, 12f)
 *   .setStartEndScale(1f, 0f)
 *   .setStartEndColor(new Color(1, 0.9f, 0.2f, 1), new Color(1, 0.2f, 0.0f, 0));
 *
 * // In your loop:
 * ps.update(deltaSeconds);
 * ps.draw();
 *
 * // Optional burst:
 * ps.burst(250);
 * }</pre>
 *
 * @author Albert Beaupre
 * @since February 10th, 2026
 */
public final class ParticleSystem {

    private final Random random = new Random();                 // Random source used for spawn position, life, velocity, rotation, and other randomized properties.
    private final Texture texture;                              // Shared texture used to render every particle (mutated per particle before draw()).
    private final ParticleEmitter emitter;                      // Emitter configuration controlling spawn rate, ranges, and interpolation endpoints.
    private final Pool<Particle> pool;                          // Reuse-only pool supplying Particle instances (should not allocate during obtain/free).
    private final Particle[] active;                            // Fixed-size array holding references to currently active (alive) particles.
    private final float[] spawnTmp = new float[2];              // Scratch array used to receive spawn offsets (dx, dy) from the spawn distributor.

    private int activeCount;                                    // Number of live particles currently stored in active (valid indices: 0..activeCount-1).
    private float x;                                            // World-space emitter X position used as the base spawn origin.
    private float y;                                            // World-space emitter Y position used as the base spawn origin.

    private int burstRemaining;                                 // Number of burst particles still queued to spawn.
    private float burstRatePerSecond;                           // Burst release rate in particles/sec (Infinity = as fast as possible).
    private float burstAccumulator;                             // Fractional accumulator for burst spawning across frames.

    private float renderInterval = 0f;                          // Seconds between renders (0 = render every frame).
    private float renderTimer = 0f;                             // Accumulator used to time render throttling.
    private int maxDrawPerFrame = Integer.MAX_VALUE;            // Maximum particles drawn per draw() call (sampling active[] round-robin).
    private int drawCursor = 0;                                 // Round-robin cursor into active[] for partial drawing across calls.

    /**
     * Creates a new {@code ParticleSystem} that renders particles using the provided shared texture.
     *
     * <p>This constructor allocates:</p>
     * <ul>
     *     <li>A fixed-size {@link #active} array of {@code maxParticles}.</li>
     *     <li>A {@link Pool} sized to {@code maxParticles}.</li>
     * </ul>
     *
     * <p>The shared {@link Texture} is not owned by this system and is not disposed here.</p>
     *
     * @param particleTexture the shared texture used to draw each particle (must not be null)
     * @param maxParticles    maximum number of simultaneously active particles supported by this system (must be > 0)
     * @throws NullPointerException     if {@code particleTexture} is null
     * @throws IllegalArgumentException if {@code maxParticles <= 0}
     */
    public ParticleSystem(Texture particleTexture, int maxParticles) {
        if (particleTexture == null) throw new NullPointerException("particleTexture cannot be null");
        if (maxParticles <= 0) throw new IllegalArgumentException("maxParticles must be > 0");

        this.texture = particleTexture;
        this.emitter = new ParticleEmitter();

        this.pool = new Pool<>(Particle::new, maxParticles);
        this.pool.initialize(maxParticles);

        this.active = new Particle[maxParticles];
        this.activeCount = 0;

        this.burstRemaining = 0;
        this.burstRatePerSecond = 0f;
        this.burstAccumulator = 0f;
    }

    /**
     * Sets the base world-space spawn position for this system.
     *
     * <p>New particles spawn at this origin plus the offset computed by the emitter's spawn distributor.</p>
     *
     * @param x world-space X position
     * @param y world-space Y position
     */
    public void setPosition(float x, float y) {
        this.x = x;
        this.y = y;
    }

    /**
     * Returns the emitter configuration used by this system.
     *
     * <p>The returned instance is owned by this particle system and is expected to be mutated by the caller.</p>
     *
     * @return emitter configuration instance
     */
    public ParticleEmitter getEmitter() {
        return emitter;
    }

    /**
     * Requests a burst of particles and spreads the release across a default time window.
     *
     * <p>This does not spawn immediately. It queues the burst and releases it over time (frame-rate independent).
     * The default spread time is 0.10 seconds.</p>
     *
     * @param count number of burst particles to queue; ignored if {@code count <= 0}
     */
    public void burst(int count) {
        burst(count, 0.10f);
    }

    /**
     * Requests a burst of particles and spreads the release across {@code spreadSeconds}.
     *
     * <p>Release is time-based and frame-rate independent:</p>
     * <ul>
     *     <li>{@code burst(1000, 0.10f)} releases ~1000 particles over ~0.10 seconds.</li>
     *     <li>{@code burst(1000, 0f)} releases as fast as possible (may still complete in one update if delta is large).</li>
     * </ul>
     *
     * <p>Note: If multiple bursts are queued, the last call determines the current burst release rate.</p>
     *
     * @param count         number of burst particles to queue; ignored if {@code count <= 0}
     * @param spreadSeconds how long (in seconds) to spread the burst across; {@code <= 0} = as fast as possible
     */
    public void burst(int count, float spreadSeconds) {
        if (count <= 0) return;

        burstRemaining += count;

        if (spreadSeconds <= 0f) {
            burstRatePerSecond = Float.POSITIVE_INFINITY;
        } else {
            burstRatePerSecond = count / spreadSeconds;
        }
    }

    /**
     * Sets how quickly queued burst particles are released (particles/sec).
     *
     * <p>This affects the time-based burst release accumulator used by {@link #update(float)}.
     * Setting 0 disables burst release until a burst() call sets a rate again.</p>
     *
     * @param particlesPerSecond burst release rate (clamped to {@code >= 0})
     */
    public void setBurstRatePerSecond(float particlesPerSecond) {
        this.burstRatePerSecond = Math.max(0f, particlesPerSecond);
    }

    /**
     * Returns how many burst particles are still queued to spawn.
     *
     * @return queued burst particles remaining
     */
    public int getBurstRemaining() {
        return burstRemaining;
    }

    /**
     * Sets the render interval used by {@link #draw()}.
     *
     * <p>If {@code seconds == 0}, rendering occurs every frame.
     * If {@code seconds > 0}, rendering occurs only when an internal timer reaches that interval.</p>
     *
     * <p>This does not affect simulation. {@link #update(float)} still processes all particles every frame.</p>
     *
     * @param seconds interval between renders (clamped to {@code >= 0})
     */
    public void setRenderInterval(float seconds) {
        this.renderInterval = Math.max(0f, seconds);
        this.renderTimer = 0f;
    }

    /**
     * Caps how many particles are drawn each {@link #draw()} call.
     *
     * <p>When this cap is smaller than {@link #activeCount}, drawing uses round-robin sampling
     * across the active particle array so all particles are eventually drawn over subsequent frames.</p>
     *
     * @param max maximum particles to draw per call (clamped to {@code >= 1})
     */
    public void setMaxDrawPerFrame(int max) {
        this.maxDrawPerFrame = Math.max(1, max);
    }

    /**
     * Updates spawning and advances all active particles.
     *
     * <p>This method performs:</p>
     * <ol>
     *     <li>Continuous emission spawning using emitter emissionRate and spawnAccumulator.</li>
     *     <li>Burst spawning using a time-based accumulator (burstRatePerSecond * delta).</li>
     *     <li>Particle simulation update for all live particles.</li>
     *     <li>Swap-removal of dead particles, returning them to the pool.</li>
     * </ol>
     *
     * <p>All spawning is clamped by remaining capacity in {@link #active}.</p>
     *
     * @param delta seconds since last frame (ignored if {@code delta <= 0})
     */
    public void update(float delta) {
        if (delta <= 0f) return;

        // ---- Spawning (continuous) ----
        emitter.setSpawnAccumulator(emitter.getSpawnAccumulator() + delta * emitter.getEmissionRate());
        int continuousToSpawn = (int) emitter.getSpawnAccumulator();
        if (continuousToSpawn > 0) emitter.setSpawnAccumulator(emitter.getSpawnAccumulator() - continuousToSpawn);

        // ---- Spawning (burst queued, rate-based) ----
        int burstToSpawn = 0;
        if (burstRemaining > 0) {
            float add = burstRatePerSecond * delta;

            if (Float.isInfinite(add)) {
                burstToSpawn = burstRemaining; // as fast as possible
            } else if (add > 0f) {
                burstAccumulator += add;
                burstToSpawn = (int) burstAccumulator;
                if (burstToSpawn > 0) burstAccumulator -= burstToSpawn;
                if (burstToSpawn > burstRemaining) burstToSpawn = burstRemaining;
            }

            burstRemaining -= burstToSpawn;

            // Finished burst: reset accumulator so next burst starts clean.
            if (burstRemaining == 0) burstAccumulator = 0f;
        }

        // ---- Total to spawn, clamped to remaining capacity ----
        int toSpawn = continuousToSpawn + burstToSpawn;
        int capacityLeft = active.length - activeCount;
        if (toSpawn > capacityLeft) toSpawn = capacityLeft;

        // Spawn.
        for (int i = 0; i < toSpawn; i++) {
            Particle p = pool.obtain();
            if (p == null) break; // pool exhausted (depends on Pool.obtain() behavior)

            initParticle(p);
            active[activeCount++] = p;
        }

        // ---- Update + compact active list in-place ----
        for (int i = 0; i < activeCount; ) {
            Particle p = active[i];

            p.setAge(p.getAge() + delta);
            if (p.getAge() >= p.getLife()) {
                // Kill: swap-remove and return to pool.
                activeCount--;
                active[i] = active[activeCount];
                active[activeCount] = null;

                // Keep draw cursor in range when we shrink.
                if (drawCursor > activeCount) drawCursor = 0;

                p.setActive(false);
                pool.free(p);
                continue; // reprocess swapped-in particle at index i
            }

            float t = p.getAge() / p.getLife(); // 0..1

            // Integrate velocity with constant acceleration (gravity + wind).
            p.setVelX(p.getVelX() + emitter.getWindX() * delta);
            p.setVelY(p.getVelY() + emitter.getGravityY() * delta);

            // Update position.
            p.setX(p.getX() + p.getVelX() * delta);
            p.setY(p.getY() + p.getVelY() * delta);

            // Rotation.
            p.setRotation(p.getRotation() + p.getRotationSpeed() * delta);

            // Scale.
            p.setScale(MathUtils.lerp(p.getStartScale(), p.getEndScale(), t));

            // Color.
            Color c = p.getColor();
            Color sc = p.getStartColor();
            Color ec = p.getEndColor();
            c.r(MathUtils.lerp(sc.r(), ec.r(), t));
            c.g(MathUtils.lerp(sc.g(), ec.g(), t));
            c.b(MathUtils.lerp(sc.b(), ec.b(), t));
            c.a(MathUtils.lerp(sc.a(), ec.a(), t));

            i++;
        }

        // Render throttle timer is time-based.
        if (renderInterval > 0f) {
            renderTimer += delta;
        }
    }

    /**
     * Draws particles using the shared {@link Texture}.
     *
     * <p>This method mutates the shared texture for each drawn particle:</p>
     * <ul>
     *     <li>{@link Texture#setSize(float, float)}</li>
     *     <li>{@link Texture#setRotationOriginCenter()}</li>
     *     <li>{@link Texture#setRotation(float)}</li>
     *     <li>{@link Texture#setColor(Color)}</li>
     *     <li>{@link Texture#setPosition(float, float)}</li>
     *     <li>{@link Texture#setRegion(float, float, float, float)} (optional)</li>
     * </ul>
     *
     * <p>Optional throttles:</p>
     * <ul>
     *     <li>If {@link #renderInterval} &gt; 0, this only draws when the interval elapses.</li>
     *     <li>If {@link #maxDrawPerFrame} is less than {@link #activeCount}, this draws only a subset using round-robin sampling.</li>
     * </ul>
     *
     * <p>The caller must have whatever GL state your {@link Texture#draw()} requires enabled.</p>
     */
    public void draw() {
        if (activeCount <= 0) return;

        // Throttle by interval.
        if (renderInterval > 0f) {
            if (renderTimer < renderInterval) return;
            renderTimer -= renderInterval; // keep fractional remainder
        }

        // Draw up to maxDrawPerFrame particles, round-robin across active[].
        int toDraw = Math.min(activeCount, maxDrawPerFrame);
        int idx = drawCursor;

        for (int drawn = 0; drawn < toDraw; drawn++) {
            if (idx >= activeCount) idx = 0;

            Particle p = active[idx++];

            float w = emitter.getBaseWidth() * p.getScale();
            float h = emitter.getBaseHeight() * p.getScale();

            texture.setSize(w, h);
            texture.setRotationOriginCenter();
            texture.setRotation(p.getRotation());
            texture.setColor(p.getColor());
            texture.setPosition(p.getX() - w * 0.5f, p.getY() - h * 0.5f);

            if (emitter.isUseRegion()) {
                texture.setRegion(
                        emitter.getRegionLeft(),
                        emitter.getRegionTop(),
                        emitter.getRegionRight(),
                        emitter.getRegionBottom()
                );
            }

            texture.draw();
        }

        drawCursor = idx;
    }

    /**
     * Kills all particles immediately and returns them to the pool.
     *
     * <p>This clears the active list, resets counters, resets the emitter spawn accumulator,
     * and clears burst + render throttling accumulators.</p>
     */
    public void clear() {
        for (int i = 0; i < activeCount; i++) {
            Particle p = active[i];
            if (p == null) continue;

            p.setActive(false);
            pool.free(p);
            active[i] = null;
        }

        activeCount = 0;

        emitter.setSpawnAccumulator(0f);

        burstRemaining = 0;
        burstAccumulator = 0f;
        burstRatePerSecond = 0f;

        renderTimer = 0f;
        drawCursor = 0;
    }

    /**
     * Initializes a particle using the current emitter configuration.
     *
     * <p>This method sets:</p>
     * <ul>
     *     <li>Life (random in emitter min/max)</li>
     *     <li>Spawn position (system position + spawn distributor offset)</li>
     *     <li>Velocity (random speed + random angle)</li>
     *     <li>Rotation and rotation speed</li>
     *     <li>Scale endpoints and initial scale</li>
     *     <li>Color endpoints and initial color</li>
     * </ul>
     *
     * @param p particle instance to initialize (must not be null)
     */
    private void initParticle(Particle p) {
        p.reset();

        // Life.
        p.setLife(MathUtils.randomFloat(emitter.getLifeMin(), emitter.getLifeMax()));
        p.setAge(0f);
        p.setActive(true);

        // Spawn position.
        float sx = x;
        float sy = y;

        // Distributor offset (dx, dy).
        emitter.getShape().computeOffset(random, spawnTmp);
        sx += spawnTmp[0];
        sy += spawnTmp[1];

        p.setX(sx);
        p.setY(sy);

        // Initial velocity from angle + speed.
        float speed = MathUtils.randomFloat(emitter.getSpeedMin(), emitter.getSpeedMax());
        float angleDeg = MathUtils.randomFloat(emitter.getAngleMinDeg(), emitter.getAngleMaxDeg());
        float ang = (float) Math.toRadians(angleDeg);

        p.setVelX((float) Math.cos(ang) * speed);
        p.setVelY((float) Math.sin(ang) * speed);

        // Rotation.
        p.setRotation(MathUtils.randomFloat(emitter.getStartRotMinDeg(), emitter.getStartRotMaxDeg()));
        p.setRotationSpeed(MathUtils.randomFloat(emitter.getRotSpeedMinDegPerSec(), emitter.getRotSpeedMaxDegPerSec()));

        // Scale.
        p.setStartScale(emitter.getStartScale());
        p.setEndScale(emitter.getEndScale());
        p.setScale(p.getStartScale());

        // Color.
        p.getStartColor().set(emitter.getStartColor());
        p.getEndColor().set(emitter.getEndColor());
        p.getColor().set(p.getStartColor());
    }
}