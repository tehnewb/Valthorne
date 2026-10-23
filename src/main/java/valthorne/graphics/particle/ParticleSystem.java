package valthorne.graphics.particle;

import org.lwjgl.BufferUtils;
import valthorne.Window;
import valthorne.graphics.Color;
import valthorne.graphics.GraphicsViewport;
import valthorne.graphics.bgfx.BgfxBatchRenderer;
import valthorne.graphics.bgfx.BgfxGraphics;
import valthorne.graphics.shader.Shader;
import valthorne.graphics.texture.Texture;
import valthorne.io.pool.Pool;
import valthorne.math.MathUtils;
import valthorne.math.physics.PhysicsWorld2D;
import valthorne.math.physics.RigidBody2D;
import java.util.Objects;

import java.nio.FloatBuffer;
import java.nio.ByteBuffer;
import java.util.Random;

/**
 * Particle system optimized for rendering speed using instanced particle quads (one instance record per particle) and a single draw call.
 *
 * <h2>Key features</h2>
 * <ul>
 *     <li><b>Instanced quad rendering</b>: uploads one instance record per particle and renders with {@code an instanced GPU submission}.</li>
 *     <li><b>No per-particle {@link Texture} mutation</b> during drawing (including rotated, non-square particles).</li>
 *     <li><b>Explicit projection state</b>: uses an engine-managed projection uniform instead of the fixed-function matrix stack.</li>
 *     <li><b>Frame-rate independent continuous emission</b>: uses an accumulator (rate * delta).</li>
 *     <li><b>Frame-rate independent burst emission</b>: burst is released over time using an accumulator (burstRate * delta).</li>
 *     <li><b>Optional render throttling</b>: draw every N seconds and/or cap particles drawn per draw call.</li>
 *     <li><b>Optional individual physics</b>: bounded reusable rigid bodies collide in a caller-owned {@link PhysicsWorld2D}.</li>
 * </ul>
 *
 * <h2>Required render state</h2>
 * <ul>
 *     <li>The particle pass supplies source-over blending for translucent particles.</li>
 *     <li>The active window or viewport projection must be configured through the engine before drawing.</li>
 * </ul>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * Texture smoke = new Texture("./assets/particles.png");
 *
 * ParticleSystem ps = new ParticleSystem(smoke, 20000);
 * ps.setPosition(0, 0);
 *
 * ps.getEmitter()
 *   .setEmissionRate(300)
 *   .setLifetime(0.4f, 1.2f)
 *   .setVelocity(25f, 140f)
 *   .setAngleDeg(70f, 110f)
 *   .setGravity(0f, -220f)
 *   .setWind(30f, 0f)
 *   .setBaseSize(10f, 22f)
 *   .setStartEndScale(1.0f, 0.0f)
 *   .setRotationSpeed(-180f, 180f)
 *   .setSpawnCircle(18f, false);
 *
 * // In your update loop:
 * ps.update(delta);
 *
 * // When you want a burst:
 * ps.burst(1200, 0.15f);
 *
 * // Before draw(), ensure your viewport/camera has loaded matrices (like your normal sprite pipeline).
 * ps.draw();
 *
 * // Cleanup:
 * ps.dispose();
 * }</pre>
 *
 * <h2>Individual rigid-body particles</h2>
 * <pre>{@code
 * ps.enablePhysics(world, new ParticlePhysicsSettings()
 *         .maxBodies(256)
 *         .restitution(0.7f)
 *         .friction(0.3f));
 * // Reserve space for these bodies in the shared world configuration.
 * ps.update(delta);              // Emit, age, and attach particles.
 * world.update(delta);           // Step the shared world exactly once.
 * ps.synchronizePhysics();       // Copy poses before shadows/custom drawing.
 * ps.draw();
 *
 * // Any live particle can opt out independently.
 * ps.getActiveParticle(0).setPhysicsEnabled(false);
 * // Cleanup releases particle bodies; the caller still owns world.
 * ps.dispose();
 * }</pre>
 *
 * @author Albert Beaupre
 * @since February 15th, 2026
 */
public final class ParticleSystem {
    private ParticlePhysics physics; // Optional reusable per-particle bodies borrowing a shared world.
    private final int[] renderViewport = new int[4]; // Reused physical viewport dimensions for particle projection.
    private float sizeScaleX = 1; // Pixels per particle width unit; standalone drawing defaults to pixels.
    private float sizeScaleY = 1; // Pixels per particle height unit.
    private float discSoftness = -1; // Negative uses the supplied image; nonnegative enables analytic discs.

    /**
     * Power-of-two angular lookup-table length covering one full revolution.
     */
    private static final int TRIG_LUT_SIZE = 2048;
    /**
     * Wrap mask for angular lookup indices.
     */
    private static final int TRIG_LUT_MASK = TRIG_LUT_SIZE - 1;
    /**
     * Multiplier converting degrees to angular lookup-table indices.
     */
    private static final float DEG_TO_LUT = TRIG_LUT_SIZE / 360.0f;
    /**
     * Sine samples spanning a full revolution, initialized once at class loading.
     */
    private static final float[] SIN_LUT = new float[TRIG_LUT_SIZE];
    /**
     * Cosine samples spanning a full revolution, initialized once at class loading.
     */
    private static final float[] COS_LUT = new float[TRIG_LUT_SIZE];
    /**
     * Interleaved particle width: position2, size1, aspect2, rotation1, color4.
     */
    private static final int FLOATS_PER_PARTICLE = 10;
    /**
     * Byte width of each particle attribute float.
     */
    private static final int BYTES_PER_FLOAT = 4;
    /**
     * Byte stride between consecutive packed particle vertices.
     */
    private static final int STRIDE_BYTES = FLOATS_PER_PARTICLE * BYTES_PER_FLOAT;

    static {
        for (int i = 0; i < TRIG_LUT_SIZE; i++) {
            float radians = (i * (MathUtils.TAU_F / TRIG_LUT_SIZE));
            SIN_LUT[i] = (float) Math.sin(radians);
            COS_LUT[i] = (float) Math.cos(radians);
        }
    }

    private final int maxParticles;                                                           // Maximum number of simultaneously active particles.
    private final FloatBuffer batch;                                                          // CPU staging buffer for interleaved particle vertex data.
    private final ByteBuffer batchBytes; // Owned packed particle storage shared with the float view.
    private final BgfxBatchRenderer bgfxRenderer; // Owned native instance renderer, owned by this renderer.

    private final Shader shader; // Owned shader matching the selected rendering backend.
    private final Random random = new Random();                                               // Random source used only for spawn distributor offsets.
    private final float[] spawnTmp = new float[2];                                            // Scratch spawn offset array (dx, dy).

    private final Texture texture;                                                            // Shared texture used for all particles.
    private final ParticleEmitter emitter;                                                    // Emitter configuration for spawning and interpolation endpoints.
    private final Pool<Particle> pool;                                                        // Reuse-only particle pool.
    private final Particle[] active;                                                          // Active particle list (swap-remove compacted).

    private int activeCount;                                                                  // Current number of active particles.
    private boolean externalMotion; // Whether a scene physics owner integrates positions and velocities.
    private float x;                                                                          // Emitter world-space x position.
    private float y;                                                                          // Emitter world-space y position.

    private int burstRemaining;                                                               // Remaining burst particles queued to spawn.
    private float burstRatePerSecond;                                                         // Burst release rate in particles per second.
    private float burstAccumulator;                                                           // Fractional accumulator for burst spawning.

    private float renderInterval = 0f;                                                        // Seconds between draws; 0 means draw every frame.
    private float renderTimer = 0f;                                                           // Accumulator used to enforce renderInterval.

    private int maxDrawPerFrame = Integer.MAX_VALUE;                                          // Cap particles drawn per draw() call.
    private int drawCursor = 0;                                                               // Round-robin cursor for partial drawing.

    /**
     * Creates a new quad particle system.
     *
     * <p>This allocates a fixed-size CPU staging buffer and a GPU VBO sized to {@code maxParticles}.
     * It also initializes a {@link Pool} and an {@code active[]} list of the same capacity.</p>
     *
     * <p>Attribute bindings are set explicitly (0..4) and the shader is re-linked.</p>
     *
     * @param particleTexture shared texture used for all particles (not owned by this system)
     * @param maxParticles    maximum number of particles that may be alive at once
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

        this.maxParticles = maxParticles;

        this.batchBytes = BufferUtils.createByteBuffer(Math.multiplyExact(maxParticles, STRIDE_BYTES));
        this.batch = batchBytes.asFloatBuffer();
        shader = new Shader("bgfx:engine/particle.vert", "bgfx:engine/particle.frag");
        bgfxRenderer = new BgfxBatchRenderer(maxParticles, 48);
    }

    /**
     * Returns the current number of live particles without copying storage.
     *
     * @return live count
     */
    public int getActiveCount() {
        return activeCount;
    }

    /**
     * Transfers motion integration to an external owner while retaining emission,
     * lifetime, color and scale updates. The owner must synchronize particle poses.
     *
     * @param externalMotion whether CPU motion integration is disabled
     */
    public void setExternalMotion(boolean externalMotion) {
        if (externalMotion && physics != null) throw new IllegalStateException("Detach particle physics before transferring motion ownership");
        this.externalMotion = externalMotion;
    }

    /**
     * Gives each participating particle its own reusable rigid body. Call update
     * before stepping the shared world, then synchronizePhysics before shadow or
     * custom rendering. draw also synchronizes automatically. The caller owns the
     * world and must reserve settings.maxBodies worth of native capacity.
     * Physical particles use native world gravity and collision response; emitter
     * gravity/wind affect only particles without a body. Native collider dimensions
     * are captured when a slot begins a particle lifetime.
     * @param world open shared physics world, never owned by this system
     * @param settings configuration copied at attachment time
     * @return this particle system
     */
    public ParticleSystem enablePhysics(PhysicsWorld2D world, ParticlePhysicsSettings settings) {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(settings, "settings");
        if (externalMotion) throw new IllegalStateException("External motion and particle physics cannot both own motion");
        ParticlePhysics next = new ParticlePhysics(world, settings, maxParticles);
        disablePhysics();
        physics = next;
        return this;
    }

    /**
     * Releases every owned particle body while retaining the caller's world.
     * Existing particles continue ordinary integration from their current state.
     * @return this particle system
     */
    public ParticleSystem disablePhysics() {
        if (physics != null) {
            physics.close();
            physics = null;
        }
        return this;
    }

    /**
     * Copies current native body positions and velocities into live particles.
     * Call after the shared world update and before external shadow rendering.
     */
    public void synchronizePhysics() {
        if (physics != null) physics.synchronize();
    }

    /**
     * Returns a particle's borrowed body for applying impulses or customizing
     * native friction/restitution. A pooled lifetime must not retain this handle.
     * @param particle live particle belonging to this system
     * @return assigned body, or null without a native slot
     */
    public RigidBody2D getPhysicsBody(Particle particle) {
        Objects.requireNonNull(particle, "particle");
        return physics == null ? null : physics.body(particle);
    }

    /**
     * Reports actual native particle participation, excluding inactive reuse slots.
     * @return live physics particle count
     */
    public int getPhysicsParticleCount() {
        return physics == null ? 0 : physics.activeCount();
    }

    /**
     * Borrows one live particle for editor visualization on the rendering thread.
     * The handle may be reused after the next update. Per-particle physics can be
     * toggled while it is live, but the handle must not be retained after recycling.
     *
     * @param index live particle index
     * @return borrowed particle
     */
    public Particle getActiveParticle(int index) {
        if (index < 0 || index >= activeCount) throw new IndexOutOfBoundsException(index);
        return active[index];
    }

    /**
     * Sets the world-space emitter position.
     *
     * <p>New particles spawn at this position plus an offset computed by {@link ParticleEmitter#getShape()}.</p>
     *
     * @param x world-space x position
     * @param y world-space y position
     */
    public void setPosition(float x, float y) {
        this.x = x;
        this.y = y;
    }

    /**
     * Returns the emitter configuration object used by this system.
     *
     * @return emitter configuration instance
     */
    public ParticleEmitter getEmitter() {
        return emitter;
    }

    /**
     * Queues a burst of particles and spreads it across a default duration of 0.10 seconds.
     *
     * @param count number of particles to queue; ignored if {@code count <= 0}
     */
    public void burst(int count) {
        burst(count, 0.10f);
    }

    /**
     * Queues a burst of particles and releases it over {@code spreadSeconds} (frame-rate independent).
     *
     * <p>If {@code spreadSeconds <= 0}, the burst is released as fast as possible (subject to capacity and pool).</p>
     *
     * @param count         number of particles to queue; ignored if {@code count <= 0}
     * @param spreadSeconds burst release duration in seconds; {@code <= 0} means immediate release
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
     * Spawns a one-shot burst immediately at an independent position using the
     * emitter's current configuration. Unlike {@link #burst(int)}, repeated calls
     * in one frame retain their own origins and appearance. Existing particles
     * continue to use the colors and sizes captured when they spawned. The
     * emitter's configured continuous-emission position is restored afterward.
     *
     * @param x finite horizontal spawn position in world units
     * @param y finite vertical spawn position in world units
     * @param count requested number of particles; nonpositive values do nothing
     * @return actual number spawned before the fixed capacity was reached
     * @throws IllegalArgumentException if either coordinate is not finite
     */
    public int emitAt(float x, float y, int count) {
        if (!Float.isFinite(x) || !Float.isFinite(y))
            throw new IllegalArgumentException("Particle position must be finite");
        if (count <= 0 || activeCount == active.length) return 0;

        float previousX = this.x;
        float previousY = this.y;
        int previousCount = activeCount;
        this.x = x;
        this.y = y;
        try {
            spawn(Math.min(count, active.length - activeCount));
        } finally {
            this.x = previousX;
            this.y = previousY;
        }
        return activeCount - previousCount;
    }

    /**
     * Sets the burst release rate in particles per second (frame-rate independent).
     *
     * @param particlesPerSecond burst release rate; values &lt; 0 are clamped to 0
     */
    public void setBurstRatePerSecond(float particlesPerSecond) {
        this.burstRatePerSecond = Math.max(0f, particlesPerSecond);
    }

    /**
     * Returns how many burst particles are still queued.
     *
     * @return remaining burst particles
     */
    public int getBurstRemaining() {
        return burstRemaining;
    }

    /**
     * Sets the minimum interval (seconds) between successful draws.
     *
     * <p>If set to 0, rendering occurs every frame. Simulation still runs every update.</p>
     *
     * @param seconds draw interval in seconds; values &lt; 0 are clamped to 0
     */
    public void setRenderInterval(float seconds) {
        this.renderInterval = Math.max(0f, seconds);
        this.renderTimer = 0f;
    }

    /**
     * Caps how many particles are drawn each time {@link #draw()} runs.
     *
     * <p>This uses round-robin sampling via {@link #drawCursor} so all particles eventually render
     * even when capped.</p>
     *
     * @param max maximum particles to draw per call; values &lt; 1 are clamped to 1
     */
    public void setMaxDrawPerFrame(int max) {
        this.maxDrawPerFrame = Math.max(1, max);
    }

    /**
     * Updates emission and advances all active particles.
     *
     * <p>This method is frame-rate independent for both continuous emission and burst emission:
     * both use accumulators that scale by {@code delta}.</p>
     *
     * @param delta seconds since last frame; ignored if {@code delta <= 0}
     */
    public void update(float delta) {
        if (delta <= 0f) return;
        synchronizePhysics();

        int continuousToSpawn = computeContinuousSpawn(delta);
        int burstToSpawn = computeBurstSpawn(delta);

        int toSpawn = continuousToSpawn + burstToSpawn;
        int capacityLeft = active.length - activeCount;
        if (toSpawn > capacityLeft) toSpawn = capacityLeft;

        spawn(toSpawn);
        updateAndCompact(delta);

        if (renderInterval > 0f) {
            renderTimer += delta;
        }
    }

    /**
     * Draws particles using instanced particle quads.
     *
     * <p>This performs one upload to the VBO and one {@code an instanced GPU submission} call.
     * If render throttling is enabled, this may skip drawing until enough time has elapsed.</p>
     */
    public void draw() {
        if (activeCount <= 0) return;
        synchronizePhysics();

        if (renderInterval > 0f) {
            if (renderTimer < renderInterval) return;
            renderTimer -= renderInterval;
        }

        int toDraw = Math.min(activeCount, maxDrawPerFrame);
        if (toDraw <= 0) return;

        float u0, v0, u1, v1;
        if (emitter.isUseRegion()) {
            float invW = 1f / texture.getData().width();
            float invH = 1f / texture.getData().height();
            u0 = emitter.getRegionLeft() * invW;
            v0 = emitter.getRegionTop() * invH;
            u1 = emitter.getRegionRight() * invW;
            v1 = emitter.getRegionBottom() * invH;
        } else {
            u0 = 0f;
            v0 = 0f;
            u1 = 1f;
            v1 = 1f;
        }

        buildBatch(toDraw);
        renderQuads(toDraw, u0, v0, u1, v1);
    }

    /**
     * Kills all particles immediately and returns them to the pool.
     *
     * <p>This also clears all accumulators and resets draw throttling state.</p>
     */
    public void clear() {
        for (int i = 0; i < activeCount; i++) {
            Particle p = active[i];
            if (p == null) continue;

            p.setActive(false);
            if (physics != null) physics.release(p);
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
     * Kills live particles and disposes GPU resources and owned particle bodies.
     *
     * <p>This disposes the internal shader, vertex buffers and optional particle
     * bodies. It does not dispose the shared {@link Texture} or physics world.</p>
     */
    public void dispose() {
        clear();
        disablePhysics();
        shader.dispose();
        bgfxRenderer.close();
    }

    /**
     * Computes how many particles to spawn from continuous emission this frame.
     *
     * @param delta seconds since last frame
     * @return integer number of particles to spawn due to continuous emission
     */
    private int computeContinuousSpawn(float delta) {
        emitter.setSpawnAccumulator(emitter.getSpawnAccumulator() + delta * emitter.getEmissionRate());
        int spawn = (int) emitter.getSpawnAccumulator();
        if (spawn > 0) emitter.setSpawnAccumulator(emitter.getSpawnAccumulator() - spawn);
        return spawn;
    }

    /**
     * Computes how many particles to spawn from the queued burst this frame.
     *
     * <p>Burst spawning is driven by {@link #burstRatePerSecond} and {@link #burstAccumulator} so it behaves consistently across FPS.</p>
     *
     * @param delta seconds since last frame
     * @return integer number of burst particles to spawn this frame
     */
    private int computeBurstSpawn(float delta) {
        if (burstRemaining <= 0) return 0;

        int burstToSpawn = 0;
        float add = burstRatePerSecond * delta;

        if (Float.isInfinite(add)) {
            burstToSpawn = burstRemaining;
        } else if (add > 0f) {
            burstAccumulator += add;
            burstToSpawn = (int) burstAccumulator;
            if (burstToSpawn > 0) burstAccumulator -= burstToSpawn;
            if (burstToSpawn > burstRemaining) burstToSpawn = burstRemaining;
        }

        burstRemaining -= burstToSpawn;
        if (burstRemaining == 0) burstAccumulator = 0f;

        return burstToSpawn;
    }

    /**
     * Spawns up to {@code toSpawn} particles, limited by pool availability.
     *
     * @param toSpawn desired number of particles to spawn this frame
     */
    private void spawn(int toSpawn) {
        for (int i = 0; i < toSpawn; i++) {
            Particle p = pool.obtain();
            if (p == null) break;

            initParticle(p);
            active[activeCount++] = p;
        }
    }

    /**
     * Updates all active particles and compacts the active list via swap-removal.
     *
     * @param delta seconds since last frame
     */
    private void updateAndCompact(float delta) {
        for (int i = 0; i < activeCount; ) {
            Particle p = active[i];

            p.setAge(p.getAge() + delta);
            if (!p.isActive() || p.getAge() >= p.getLife()) {
                activeCount--;
                active[i] = active[activeCount];
                active[activeCount] = null;

                if (drawCursor > activeCount) drawCursor = 0;

                p.setActive(false);
                if (physics != null) physics.release(p);
                pool.free(p);
                continue;
            }

            float t = p.getAge() / p.getLife();

            boolean physical = physics != null && physics.attach(p);
            if (!externalMotion && !physical) {
                p.setVelX(p.getVelX() + emitter.getWindX() * delta);
                p.setVelY(p.getVelY() + emitter.getGravityY() * delta);

                p.setX(p.getX() + p.getVelX() * delta);
                p.setY(p.getY() + p.getVelY() * delta);

                p.setRotation(p.getRotation() + p.getRotationSpeed() * delta);
            }

            p.setScale(MathUtils.lerp(p.getStartScale(), p.getEndScale(), t));

            Color c = p.getColor();
            Color sc = p.getStartColor();
            Color ec = p.getEndColor();
            c.r(MathUtils.lerp(sc.r(), ec.r(), t));
            c.g(MathUtils.lerp(sc.g(), ec.g(), t));
            c.b(MathUtils.lerp(sc.b(), ec.b(), t));
            c.a(MathUtils.lerp(sc.a(), ec.a(), t));

            i++;
        }
    }

    /**
     * Builds an interleaved CPU batch for {@code toDraw} particles using round-robin selection.
     *
     * <p>Each particle contributes one vertex with attributes:
     * position, base size, aspect, rotation, and color.</p>
     *
     * @param toDraw number of particles to include in the batch
     */
    private void buildBatch(int toDraw) {
        batch.clear();

        int idx = drawCursor;

        for (int drawn = 0; drawn < toDraw; drawn++) {
            if (idx >= activeCount) idx = 0;

            Particle p = active[idx++];

            float w = p.getBaseWidth() * p.getScale();
            float h = p.getBaseHeight() * p.getScale();

            float size = Math.max(w, h);
            float ax = (size == 0f) ? 1f : (w / size);
            float ay = (size == 0f) ? 1f : (h / size);

            Color c = p.getColor();

            batch.put(p.getX())
                    .put(p.getY());
            batch.put(size);
            batch.put(ax)
                    .put(ay);
            batch.put(p.getRotation());
            batch.put(c.r())
                    .put(c.g())
                    .put(c.b())
                    .put(c.a());
        }

        drawCursor = idx;
        batch.flip();
    }

    /**
     * Renders the uploaded batch as instanced particle quads using the internal shader.
     *
     * @param toDraw number of particles (vertices) to draw
     * @param u0     atlas u0 (left)
     * @param v0     atlas v0 (top)
     * @param u1     atlas u1 (right)
     * @param v1     atlas v1 (bottom)
     */
    private void renderQuads(int toDraw, float u0, float v0, float u1, float v1) {
        GraphicsViewport.get(renderViewport);
        shader.bind();
        shader.setUniformMatrix4("u_mvp", Window.getProjectionMatrix());
        shader.setUniform1i("u_texture", 0);
        shader.setUniform4f("u_uvRect", u0, v0, u1, v1);
        shader.setUniform2f("u_pixelScale", 2f * sizeScaleX / Math.max(1, renderViewport[2]), 2f * sizeScaleY / Math.max(1, renderViewport[3]));
        shader.setUniform1f("u_discSoftness", discSoftness);
        BgfxGraphics.bindTexture(0, texture.getTextureID());
        batchBytes.position(0).limit(toDraw * STRIDE_BYTES);
        bgfxRenderer.submit(batchBytes, toDraw, STRIDE_BYTES, shader, BgfxGraphics.textures(), 1, false);
        shader.unbind();
    }

    /**
     * Initializes a particle instance using the current emitter configuration.
     *
     * <p>This uses {@link MathUtils#randomFloat(float, float)} for value ranges and a trig LUT for angle velocity.</p>
     *
     * @param p particle instance to initialize
     * @throws NullPointerException if {@code p} is null
     */
    private void initParticle(Particle p) {
        if (p == null) throw new NullPointerException("p");

        p.reset();

        p.setLife(MathUtils.randomFloat(emitter.getLifeMin(), emitter.getLifeMax()));
        p.setAge(0f);
        p.setActive(true);

        float sx = x;
        float sy = y;

        emitter.getShape()
                .computeOffset(random, spawnTmp);
        sx += spawnTmp[0];
        sy += spawnTmp[1];

        p.setX(sx);
        p.setY(sy);

        float speed = MathUtils.randomFloat(emitter.getSpeedMin(), emitter.getSpeedMax());
        float angleDeg = MathUtils.randomFloat(emitter.getAngleMinDeg(), emitter.getAngleMaxDeg());

        int lut = (int) (angleDeg * DEG_TO_LUT);
        float cx = COS_LUT[lut & TRIG_LUT_MASK];
        float syy = SIN_LUT[lut & TRIG_LUT_MASK];

        p.setVelX(cx * speed);
        p.setVelY(syy * speed);

        p.setRotation(MathUtils.randomFloat(emitter.getStartRotMinDeg(), emitter.getStartRotMaxDeg()));
        p.setRotationSpeed(MathUtils.randomFloat(emitter.getRotSpeedMinDegPerSec(), emitter.getRotSpeedMaxDegPerSec()));

        p.setStartScale(emitter.getStartScale());
        p.setEndScale(emitter.getEndScale());
        p.setScale(p.getStartScale());
        p.setBaseWidth(emitter.getBaseWidth());
        p.setBaseHeight(emitter.getBaseHeight());

        p.getStartColor()
                .set(emitter.getStartColor());
        p.getEndColor()
                .set(emitter.getEndColor());
        p.getColor()
                .set(p.getStartColor());
    }

    /**
     * Configures projected dimensions independently of particle simulation.
     *
     * @param horizontal pixels per width unit
     * @param vertical pixels per height unit
     */
    public void setSizeScale(float horizontal, float vertical) {
        if (!Float.isFinite(horizontal) || !Float.isFinite(vertical) || horizontal <= 0 || vertical <= 0)
            throw new IllegalArgumentException("Particle projection scales must be positive and finite");
        sizeScaleX = horizontal;
        sizeScaleY = vertical;
    }

    /**
     * Chooses a resolution-independent disc or the original image renderer.
     *
     * @param softness negative for image sampling; zero for crisp discs, one for soft discs
     */
    public void setDiscSoftness(float softness) {
        if (!Float.isFinite(softness) || softness < -1 || softness > 1)
            throw new IllegalArgumentException("Disc softness must be between -1 and 1");
        discSoftness = softness;
    }

}
