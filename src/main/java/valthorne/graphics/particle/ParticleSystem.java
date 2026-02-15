package valthorne.graphics.particle;

import org.lwjgl.BufferUtils;
import valthorne.graphics.Color;
import valthorne.graphics.shader.Shader;
import valthorne.graphics.texture.Texture;
import valthorne.io.pool.Pool;
import valthorne.math.MathUtils;

import java.nio.FloatBuffer;
import java.util.Random;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;

/**
 * Particle system optimized for rendering speed using point sprites (one vertex per particle) and a single draw call.
 *
 * <h2>Key features</h2>
 * <ul>
 *     <li><b>Point sprite rendering</b>: uploads 1 vertex per particle and renders with {@code glDrawArrays(GL_POINTS)}.</li>
 *     <li><b>No per-particle {@link Texture} mutation</b> during drawing (unlike quad-based sprite particles).</li>
 *     <li><b>Fixed-function camera compatibility</b>: uses {@code gl_ModelViewProjectionMatrix} so it works with your existing matrix stack pipeline.</li>
 *     <li><b>Frame-rate independent continuous emission</b>: uses an accumulator (rate * delta).</li>
 *     <li><b>Frame-rate independent burst emission</b>: burst is released over time using an accumulator (burstRate * delta).</li>
 *     <li><b>Optional render throttling</b>: draw every N seconds and/or cap particles drawn per draw call.</li>
 * </ul>
 *
 * <h2>Required render state</h2>
 * <ul>
 *     <li>Blending should be enabled for typical particles: {@code glEnable(GL_BLEND)}.</li>
 *     <li>Texture 2D should be enabled for this pipeline: {@code glEnable(GL_TEXTURE_2D)}.</li>
 *     <li>The viewport/camera must load matrices into the fixed-function stack before drawing (same approach as your Texture pipeline).</li>
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
 * @author Albert Beaupre
 * @since February 15th, 2026
 */
public final class ParticleSystem {

    private static final int TRIG_LUT_SIZE = 2048;                                           // Trig LUT size (power of two).
    private static final int TRIG_LUT_MASK = TRIG_LUT_SIZE - 1;                               // Trig LUT index mask.
    private static final float DEG_TO_LUT = TRIG_LUT_SIZE / 360.0f;                           // Degrees to LUT index scale.

    private static final float[] SIN_LUT = new float[TRIG_LUT_SIZE];                          // Precomputed sin values (0..TAU).
    private static final float[] COS_LUT = new float[TRIG_LUT_SIZE];                          // Precomputed cos values (0..TAU).

    static {
        for (int i = 0; i < TRIG_LUT_SIZE; i++) {
            float radians = (i * (MathUtils.TAU_F / TRIG_LUT_SIZE));
            SIN_LUT[i] = (float) Math.sin(radians);
            COS_LUT[i] = (float) Math.cos(radians);
        }
    }

    private static final int FLOATS_PER_PARTICLE = 10;                                       // Interleaved float count per particle vertex.
    private static final int BYTES_PER_FLOAT = 4;                                            // Bytes per float.
    private static final int STRIDE_BYTES = FLOATS_PER_PARTICLE * BYTES_PER_FLOAT;           // VBO stride in bytes.

    private static final int ATTR_POS = 0;                                                   // Attribute index for a_pos.
    private static final int ATTR_SIZE = 1;                                                  // Attribute index for a_size.
    private static final int ATTR_ASPECT = 2;                                                // Attribute index for a_aspect.
    private static final int ATTR_ROT = 3;                                                   // Attribute index for a_rot.
    private static final int ATTR_COL = 4;                                                   // Attribute index for a_col.

    private static final String VERT = """
            #version 120

            attribute vec2 a_pos;
            attribute float a_size;
            attribute vec2 a_aspect;
            attribute float a_rot;
            attribute vec4 a_col;

            varying vec4 v_col;
            varying vec2 v_aspect;
            varying float v_rot;

            void main() {
                gl_Position = gl_ModelViewProjectionMatrix * vec4(a_pos.xy, 0.0, 1.0);
                gl_PointSize = a_size;

                v_col = a_col;
                v_aspect = a_aspect;
                v_rot = a_rot;
            }
            """;                                                                              // Vertex shader source.

    private static final String FRAG = """
            #version 120

            uniform sampler2D u_texture;
            uniform vec4 u_uvRect; // (u0, v0, u1, v1)

            varying vec4 v_col;
            varying vec2 v_aspect;
            varying float v_rot;

            vec2 rot2(vec2 p, float radians) {
                float c = cos(radians);
                float s = sin(radians);
                p -= vec2(0.5);
                vec2 r = vec2(p.x * c - p.y * s, p.x * s + p.y * c);
                return r + vec2(0.5);
            }

            void main() {
                vec2 uv = gl_PointCoord;

                vec2 centered = uv - vec2(0.5);
                centered /= max(v_aspect, vec2(0.0001));
                uv = centered + vec2(0.5);

                if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) discard;

                uv = rot2(uv, radians(-v_rot));

                vec2 atlasUV = vec2(
                    mix(u_uvRect.x, u_uvRect.z, uv.x),
                    mix(u_uvRect.y, u_uvRect.w, uv.y)
                );

                vec4 tex = texture2D(u_texture, atlasUV);
                gl_FragColor = tex * v_col;
            }
            """;                                                                              // Fragment shader source.

    private final int maxParticles;                                                           // Maximum number of simultaneously active particles.
    private final FloatBuffer batch;                                                          // CPU staging buffer for interleaved particle vertex data.
    private final int vbo;                                                                    // OpenGL buffer object storing particle vertex data.

    private final Shader shader = new Shader(VERT, FRAG);                                     // Shader used to render point sprites.
    private final Random random = new Random();                                               // Random source used only for spawn distributor offsets.
    private final float[] spawnTmp = new float[2];                                            // Scratch spawn offset array (dx, dy).

    private final Texture texture;                                                            // Shared texture used for all particles.
    private final ParticleEmitter emitter;                                                    // Emitter configuration for spawning and interpolation endpoints.
    private final Pool<Particle> pool;                                                        // Reuse-only particle pool.
    private final Particle[] active;                                                          // Active particle list (swap-remove compacted).

    private int activeCount;                                                                  // Current number of active particles.
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
     * Creates a new point-sprite particle system.
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

        this.batch = BufferUtils.createFloatBuffer(maxParticles * FLOATS_PER_PARTICLE);

        this.vbo = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, (long) maxParticles * FLOATS_PER_PARTICLE * BYTES_PER_FLOAT, GL_STREAM_DRAW);
        glBindBuffer(GL_ARRAY_BUFFER, 0);

        shader.bindAttribLocation(ATTR_POS, "a_pos");
        shader.bindAttribLocation(ATTR_SIZE, "a_size");
        shader.bindAttribLocation(ATTR_ASPECT, "a_aspect");
        shader.bindAttribLocation(ATTR_ROT, "a_rot");
        shader.bindAttribLocation(ATTR_COL, "a_col");
        shader.reload();
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
     * Draws particles using point sprites.
     *
     * <p>This performs one upload to the VBO and one {@code glDrawArrays(GL_POINTS)} call.
     * If render throttling is enabled, this may skip drawing until enough time has elapsed.</p>
     */
    public void draw() {
        if (activeCount <= 0) return;

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
        uploadBatch();
        renderPoints(toDraw, u0, v0, u1, v1);
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
     * Disposes GPU resources created by this particle system.
     *
     * <p>This disposes the internal shader and deletes the internal VBO. It does not dispose the shared {@link Texture}.</p>
     */
    public void dispose() {
        shader.dispose();
        glDeleteBuffers(vbo);
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
            if (p.getAge() >= p.getLife()) {
                activeCount--;
                active[i] = active[activeCount];
                active[activeCount] = null;

                if (drawCursor > activeCount) drawCursor = 0;

                p.setActive(false);
                pool.free(p);
                continue;
            }

            float t = p.getAge() / p.getLife();

            p.setVelX(p.getVelX() + emitter.getWindX() * delta);
            p.setVelY(p.getVelY() + emitter.getGravityY() * delta);

            p.setX(p.getX() + p.getVelX() * delta);
            p.setY(p.getY() + p.getVelY() * delta);

            p.setRotation(p.getRotation() + p.getRotationSpeed() * delta);

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
     * position, point-size, aspect, rotation, and color.</p>
     *
     * @param toDraw number of particles to include in the batch
     */
    private void buildBatch(int toDraw) {
        batch.clear();

        int idx = drawCursor;

        for (int drawn = 0; drawn < toDraw; drawn++) {
            if (idx >= activeCount) idx = 0;

            Particle p = active[idx++];

            float w = emitter.getBaseWidth() * p.getScale();
            float h = emitter.getBaseHeight() * p.getScale();

            float size = Math.max(w, h);
            float ax = (size == 0f) ? 1f : (w / size);
            float ay = (size == 0f) ? 1f : (h / size);

            Color c = p.getColor();

            batch.put(p.getX()).put(p.getY());
            batch.put(size);
            batch.put(ax).put(ay);
            batch.put(p.getRotation());
            batch.put(c.r()).put(c.g()).put(c.b()).put(c.a());
        }

        drawCursor = idx;
        batch.flip();
    }

    /**
     * Uploads the current CPU batch to the GPU VBO using {@code glBufferSubData}.
     */
    private void uploadBatch() {
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferSubData(GL_ARRAY_BUFFER, 0, batch);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
    }

    /**
     * Renders the uploaded batch as point sprites using the internal shader.
     *
     * @param toDraw number of particles (vertices) to draw
     * @param u0     atlas u0 (left)
     * @param v0     atlas v0 (top)
     * @param u1     atlas u1 (right)
     * @param v1     atlas v1 (bottom)
     */
    private void renderPoints(int toDraw, float u0, float v0, float u1, float v1) {
        glEnable(GL_POINT_SPRITE);
        glEnable(GL_VERTEX_PROGRAM_POINT_SIZE);

        shader.bind();
        shader.setUniform1i("u_texture", 0);
        shader.setUniform4f("u_uvRect", u0, v0, u1, v1);

        glBindTexture(GL_TEXTURE_2D, texture.getTextureID());
        glBindBuffer(GL_ARRAY_BUFFER, vbo);

        glEnableVertexAttribArray(ATTR_POS);
        glEnableVertexAttribArray(ATTR_SIZE);
        glEnableVertexAttribArray(ATTR_ASPECT);
        glEnableVertexAttribArray(ATTR_ROT);
        glEnableVertexAttribArray(ATTR_COL);

        long off = 0L;

        glVertexAttribPointer(ATTR_POS, 2, GL_FLOAT, false, STRIDE_BYTES, off);
        off += 2L * BYTES_PER_FLOAT;

        glVertexAttribPointer(ATTR_SIZE, 1, GL_FLOAT, false, STRIDE_BYTES, off);
        off += BYTES_PER_FLOAT;

        glVertexAttribPointer(ATTR_ASPECT, 2, GL_FLOAT, false, STRIDE_BYTES, off);
        off += 2L * BYTES_PER_FLOAT;

        glVertexAttribPointer(ATTR_ROT, 1, GL_FLOAT, false, STRIDE_BYTES, off);
        off += BYTES_PER_FLOAT;

        glVertexAttribPointer(ATTR_COL, 4, GL_FLOAT, false, STRIDE_BYTES, off);

        glDrawArrays(GL_POINTS, 0, toDraw);

        glDisableVertexAttribArray(ATTR_POS);
        glDisableVertexAttribArray(ATTR_SIZE);
        glDisableVertexAttribArray(ATTR_ASPECT);
        glDisableVertexAttribArray(ATTR_ROT);
        glDisableVertexAttribArray(ATTR_COL);

        glBindBuffer(GL_ARRAY_BUFFER, 0);
        shader.unbind();

        glDisable(GL_VERTEX_PROGRAM_POINT_SIZE);
        glDisable(GL_POINT_SPRITE);
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

        emitter.getShape().computeOffset(random, spawnTmp);
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

        p.getStartColor().set(emitter.getStartColor());
        p.getEndColor().set(emitter.getEndColor());
        p.getColor().set(p.getStartColor());
    }
}