package valthorne.graphics.lighting2d;

import com.sun.management.ThreadMXBean;
import org.lwjgl.BufferUtils;
import valthorne.Application;
import valthorne.JGL;
import valthorne.JGLConfiguration;
import valthorne.SwapInterval;
import valthorne.graphics.Color;
import valthorne.graphics.Sprite;
import valthorne.graphics.texture.Texture;
import valthorne.graphics.texture.TextureBatch;
import valthorne.graphics.texture.TextureData;
import valthorne.graphics.texture.TextureFilter;
import valthorne.Window;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static org.lwjgl.opengl.GL33.*;

/**
 * Reproducible native comparison against the pre-batching implementation from
 * commit 4c5d57b. Uses real alpha textures, animated regions, per-card opacity,
 * rotations, flipped UVs, multiple textures, optional fill/contact passes and
 * batches exceeding staging capacity. RGBA8 and sRGB framebuffer bytes are
 * compared before performance measurements. No production source is copied at run time.
 *
 * <p>CPU measurements cover the renderer call only. GPU elapsed queries cover
 * that same pass and are read after CPU submission timing ends. Both backends
 * alternate order per sample after warmup. Clear, animation, actor drawing,
 * query readback and presentation are excluded. This is a shadow-pass benchmark,
 * not whole-game FPS. Optional --sprite PATH uses an external 512x256 six-frame
 * Cacodaemon sheet without redistributing it. Default alpha fixtures are generated
 * here and need no downloaded assets. Results are written under build/results.</p>
 */
public final class GroundShadowBenchmark implements Application {
    /**
     * Output width and matching world extent.
     */
    private static final int WIDTH = 1000;
    /**
     * Output height and matching world extent.
     */
    private static final int HEIGHT = 680;
    /**
     * Warmup samples per workload; both renderers execute each sample.
     */
    private static final int WARMUP = 80;
    /**
     * Timed samples per workload after warmup.
     */
    private static final int SAMPLES = 160;

    private final String spritePath; // Optional caller-owned external animation sheet.
    private final List<String> results = new ArrayList<>(); // Cold CSV report lines.
    private final PointLight2D light = new PointLight2D()
            .setPosition(500, 340)
            .setRadius(2500)
            .setElevation(220)
            .setSourceRadius(7); // Shared source for identical projection and softness.
    private final PointLight2D[] fills = {new PointLight2D().setPosition(180, 550).setRadius(1500).setElevation(300)}; // Enables volume and contact passes.
    private final ThreadMXBean allocations = (ThreadMXBean) ManagementFactory.getThreadMXBean(); // Owner-thread allocation counter, outside timed samples.
    private final ByteBuffer referencePixels = BufferUtils.createByteBuffer(WIDTH * HEIGHT * 4); // Reused exact reference readback.
    private final ByteBuffer batchedPixels = BufferUtils.createByteBuffer(WIDTH * HEIGHT * 4); // Reused exact candidate readback.
    private final long[][] cpu = new long[2][SAMPLES]; // Per-backend CPU submission nanoseconds.
    private final long[][] gpu = new long[2][SAMPLES]; // Per-backend elapsed GPU nanoseconds.
    private final long[][] allocated = new long[2][SAMPLES]; // Java bytes allocated during renderer calls.
    private TextureData image; // Shared decoded pixels, or JVM-managed generated pixels.
    private Texture first; // Owned texture used by normal batches.
    private Texture second; // Owned distinct texture for ordered texture-run checks.
    private LegacyGroundShadowRenderer2D reference; // Frozen unbatched implementation.
    private GroundShadowRenderer2D batched; // Production renderer being measured.
    private TextureBatch batch; // Active scene batch supplied to both renderers.
    private int framebuffer; // Owned offscreen target.
    private int target; // Owned RGBA8/sRGB8 target texture.
    private int query; // Owned reusable GPU timer query.
    private int sampler; // Owned nondefault sampler used to verify state restoration.
    private int frameCount = 4; // Generated fixture's animation length, or six for supplied art.
    private int comparisons; // Successful full-frame comparisons.
    private int stateChecks; // Successful state-restoration assertions.
    private long comparedBytes; // Exact framebuffer bytes checked across comparisons.
    private boolean done; // Prevents repeated benchmark execution.

    /**
     * Retains only the optional external source location.
     * @param spritePath external sheet, or null for generated alpha fixtures
     */
    public GroundShadowBenchmark(String spritePath) { this.spritePath = spritePath; }

    /**
     * Opens a hidden audio-disabled GL context and executes the complete experiment.
     * @param args optional --sprite followed by a compatible image path
     */
    public static void main(String[] args) {
        /*
         * Real driver execution avoids mock draw-count or simulated pixel comparisons.
         */
        String sprite = args.length == 1 && args[0].startsWith("--sprite=") ? args[0].substring(9) : args.length == 2 && args[0].equals("--sprite") ? args[1] : null;
        JGL.init(new GroundShadowBenchmark(sprite), JGLConfiguration.defaults().size(WIDTH, HEIGHT).visible(false).audioEnabled(false).swapInterval(SwapInterval.OFF));
    }

    @Override
    public void init() {
        if (!allocations.isThreadAllocatedMemorySupported()) throw new IllegalStateException("Allocation counter unavailable");
        allocations.setThreadAllocatedMemoryEnabled(true);
        image = spritePath == null ? generatedImage() : TextureData.load(spritePath);
        if (spritePath != null) {
            if (image.width() != 512 || image.height() != 256) throw new IllegalArgumentException("Expected 512x256 Cacodaemon sheet");
            frameCount = 6;
        }
        first = new Texture(image);
        second = new Texture(image);
        first.setFilter(TextureFilter.NEAREST);
        second.setFilter(TextureFilter.NEAREST);
        reference = new LegacyGroundShadowRenderer2D();
        batched = new GroundShadowRenderer2D();
        batch = new TextureBatch(16);
        framebuffer = glGenFramebuffers();
        query = glGenQueries();
        sampler = glGenSamplers();
        glSamplerParameteri(sampler, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glSamplerParameteri(sampler, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        results.add("# GPU: " + glGetString(GL_RENDERER));
        results.add("# Vendor: " + glGetString(GL_VENDOR));
        results.add("# OpenGL: " + glGetString(GL_VERSION));
        results.add("# Java: " + System.getProperty("java.version") + "; OS: " + System.getProperty("os.name"));
        results.add("# Fixture: " + (spritePath == null ? "generated four-frame alpha atlas" : "external Cacodaemon first-row animation"));
        results.add("# Warmup=" + WARMUP + "; samples=" + SAMPLES + "; alternating backend order; RGBA8; shadow pass only");
        results.add("casters,mode,textures,reference_draws,batched_draws,reference_cpu_us,batched_cpu_us,cpu_speedup,reference_gpu_us,batched_gpu_us,reference_allocated_bytes,batched_allocated_bytes");
    }

    /**
     * Creates an authored four-frame alpha atlas with thin edges, holes and padding.
     * @return JVM-owned direct pixel data, never passed to STB's free function
     */
    private static TextureData generatedImage() {
        /*
         * The synthetic fixture makes visual validation reproducible without third-party art.
         */
        ByteBuffer bytes = BufferUtils.createByteBuffer(64 * 16 * 4);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 64; x++) {
                int frame = x / 16;
                float dx = x % 16 - 7.5f;
                float dy = y - 7.5f;
                float radius = (float) Math.hypot(dx, dy);
                int alpha = Math.round(Math.clamp(6.8f - radius, 0, 1) * 255);
                if (frame == 1 && Math.abs(dx) < 2 && Math.abs(dy) < 2) alpha = 0;
                if (frame == 2 && dx < -2 && dy > 0) alpha = 0;
                if (frame == 3 && dy < -2 && ((int) dx & 1) == 0) alpha /= 2;
                bytes.put((byte) 240)
                        .put((byte) (100 + frame * 30))
                        .put((byte) 60)
                        .put((byte) alpha);
            }
        }
        bytes.flip();
        return new TextureData(bytes, 64, 16);
    }

    @Override
    public void update(float delta) {
    }

    @Override
    public void render() {
        if (done) return;
        System.out.println("GPU: " + glGetString(GL_RENDERER));
        verifyPixels(GL_RGBA8);
        verifyPixels(GL_SRGB8_ALPHA8);
        target(GL_RGBA8);
        for (int count : new int[]{64, 256, 512, 1000}) {
            measure(count, false, 0);
            measure(count, true, 0);
        }
        measure(512, true, 1);
        measure(512, true, 2);
        measure(512, false, 2);
        verifyEarlyReturns();
        results.add("# Exact comparisons=" + comparisons + "; compared bytes=" + comparedBytes + "; state assertions=" + stateChecks);
        try {
            Path output = Path.of("build/results");
            Files.createDirectories(output);
            Files.write(output.resolve(spritePath == null ? "generated.csv" : "cacodaemon.csv"), results);
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot save benchmark report", failure);
        }
        done = true;
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        Window.requestClose();
        System.out.println("GROUND_SHADOW_BATCH_OK " + comparisons + " exact RGBA comparisons, " + comparedBytes + " bytes, " + stateChecks + " state assertions");
    }

    /**
     * Replaces the offscreen target for the requested color encoding.
     * @param format RGBA8 or SRGB8_ALPHA8
     */
    private void target(int format) {
        if (target != 0) glDeleteTextures(target);
        target = glGenTextures();
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, target);
        glTexImage2D(GL_TEXTURE_2D, 0, format, WIDTH, HEIGHT, 0, GL_RGBA, GL_UNSIGNED_BYTE, 0L);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glBindFramebuffer(GL_FRAMEBUFFER, framebuffer);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, target, 0);
        require(glCheckFramebufferStatus(GL_FRAMEBUFFER) == GL_FRAMEBUFFER_COMPLETE, "Offscreen framebuffer complete");
        glViewport(0, 0, WIDTH, HEIGHT);
        if (format == GL_SRGB8_ALPHA8) glEnable(GL_FRAMEBUFFER_SRGB);
        else glDisable(GL_FRAMEBUFFER_SRGB);
    }

    /**
     * Creates cold scene state with per-caster transforms and differing alpha values.
     * @param count number of requested cards
     * @param textures zero for one texture, one for long runs, two for alternating runs
     * @return independently mutable cards retaining borrowed textures
     */
    private List<SpriteGroundShadow2D> scene(int count, int textures) {
        ArrayList<SpriteGroundShadow2D> cards = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            boolean alternate = textures == 1 ? i / 32 % 2 != 0 : textures == 2 && (i & 1) != 0;
            Sprite sprite = new Sprite(alternate ? second : first);
            sprite.setSize(18 + i % 9, 25 + i % 7);
            sprite.setPosition(26 + i % 32 * 29, 30 + i / 32 * 19);
            if (count == 1) sprite.setPosition(450, 300);
            sprite.setRotationOriginCenter();
            sprite.setRotation(i % 5 * 7 - 14);
            sprite.setFlip(i % 7 == 0, i % 11 == 0);
            sprite.setColor(new Color(1f, 1f, 1f, 0.35f + i % 6 * 0.1f));
            cards.add(new SpriteGroundShadow2D(sprite).setHeight(20 + i % 14).setMaxLength(25 + i % 11).setOpacity(0.17f + i % 5 * 0.08f).setFrameAnchors(0.25f + i % 3 * 0.2f, 0.08f, 0.94f));
        }
        animate(cards, 0);
        return cards;
    }

    /**
     * Changes atlas frame coordinates in place outside measured submission time.
     * @param cards reused scene
     * @param tick animation step
     */
    private void animate(List<SpriteGroundShadow2D> cards, int tick) {
        for (int i = 0; i < cards.size(); i++) {
            int frame = (i + tick) % frameCount;
            if (spritePath == null) cards.get(i).sprite.setRegion(frame * 16, 0, 16, 16);
            else cards.get(i).sprite.setRegion(frame * 64 + 4, 196, 56, 48);
        }
    }

    /**
     * Clears all pixels before selecting an optional nondefault scissor rectangle.
     * @param scissor whether the comparison also tests clipping preservation
     */
    private void clear(boolean scissor) {
        glDisable(GL_SCISSOR_TEST);
        glColorMask(true, true, true, true);
        glClearColor(0.31f, 0.44f, 0.53f, 0.37f);
        glClear(GL_COLOR_BUFFER_BIT);
        if (scissor) {
            glScissor(53, 47, WIDTH - 107, HEIGHT - 91);
            glEnable(GL_SCISSOR_TEST);
        }
    }

    /**
     * Compares every output byte against the unchanged reference in edge workloads.
     * @param format target color encoding
     */
    private void verifyPixels(int format) {
        target(format);
        for (int count : new int[]{0, 1, 64, 512, 513, 1023, 1024, 1025}) {
            for (int mode = 0; mode < 2; mode++) {
                for (int textures = 0; textures < 3; textures++) {
                    List<SpriteGroundShadow2D> cards = scene(count, textures);
                    if (count == 64) {
                        cards.get(0).setEnabled(false);
                        cards.get(1).setOpacity(0);
                        cards.get(2).sprite.setPosition(10000, 10000);
                        cards.get(3).sprite.getColor().set(1f, 1f, 1f, 0f);
                    }
                    for (int frame = 0; frame < 2; frame++) {
                        animate(cards, frame);
                        clear(true);
                        batch.begin();
                        configureState();
                        reference.draw(batch, light, cards, mode == 0 ? null : fills);
                        verifyState();
                        batch.end();
                        glReadPixels(0, 0, WIDTH, HEIGHT, GL_RGBA, GL_UNSIGNED_BYTE, referencePixels);
                        clear(true);
                        batch.begin();
                        configureState();
                        batched.draw(batch, light, cards, mode == 0 ? null : fills);
                        verifyState();
                        batch.end();
                        glReadPixels(0, 0, WIDTH, HEIGHT, GL_RGBA, GL_UNSIGNED_BYTE, batchedPixels);
                        require(reference.getDrawCalls() == batched.getShadowCount(), "Logical quad counts match");
                        boolean visible = false;
                        for (int pixel = 0; pixel < referencePixels.capacity(); pixel++) {
                            if (referencePixels.get(pixel) != batchedPixels.get(pixel))
                                throw new AssertionError("Pixel mismatch: format=" + format + " count=" + count + " volume=" + mode + " textures=" + textures + " byte=" + pixel + " old=" + (referencePixels.get(pixel) & 255) + " new=" + (batchedPixels.get(pixel) & 255));
                            if ((pixel & 3) != 3 && referencePixels.get(pixel) != referencePixels.get(pixel & 3)) visible = true;
                        }
                        if (batched.getShadowCount() > 0) require(visible, "Visible shadow pixels: count=" + count + " volume=" + mode + " textures=" + textures);
                        comparisons++;
                        comparedBytes += referencePixels.capacity();
                    }
                }
            }
        }
        glDisable(GL_SCISSOR_TEST);
        System.out.println("Exact pixels passed: " + (format == GL_RGBA8 ? "RGBA8" : "sRGB8"));
    }

    /**
     * Installs nondefault state whose restoration is part of the pass contract.
     */
    private void configureState() {
        glEnable(GL_SCISSOR_TEST);
        glEnable(GL_DEPTH_TEST);
        glDepthMask(true);
        glEnable(GL_CULL_FACE);
        glCullFace(GL_FRONT);
        glBlendEquationSeparate(GL_FUNC_REVERSE_SUBTRACT, GL_FUNC_SUBTRACT);
        glActiveTexture(GL_TEXTURE2);
        glBindSampler(2, sampler);
        glActiveTexture(GL_TEXTURE1);
    }

    /**
     * Checks restored capabilities, blending, sampler, texture selector and scissor.
     */
    private void verifyState() {
        require(glIsEnabled(GL_DEPTH_TEST), "Depth test restored");
        require(glGetBoolean(GL_DEPTH_WRITEMASK), "Depth writes restored");
        require(glIsEnabled(GL_CULL_FACE) && glGetInteger(GL_CULL_FACE_MODE) == GL_FRONT, "Culling restored");
        require(glGetInteger(GL_BLEND_EQUATION_RGB) == GL_FUNC_REVERSE_SUBTRACT, "RGB blend equation restored");
        require(glGetInteger(GL_BLEND_EQUATION_ALPHA) == GL_FUNC_SUBTRACT, "Alpha blend equation restored");
        require(glGetInteger(GL_ACTIVE_TEXTURE) == GL_TEXTURE1, "Active texture restored");
        glActiveTexture(GL_TEXTURE2);
        require(glGetInteger(GL_SAMPLER_BINDING) == sampler, "Sampler restored");
        glActiveTexture(GL_TEXTURE1);
        require(glIsEnabled(GL_SCISSOR_TEST), "Scissor preserved");
        require(glGetError() == GL_NO_ERROR, "No GL errors");
        stateChecks += 9;
    }

    /**
     * Times both renderers against the same animated cards and active batch.
     * @param count casters in the workload
     * @param volume whether fills also create contact shadows
     * @param textures texture-run arrangement
     */
    private void measure(int count, boolean volume, int textures) {
        List<SpriteGroundShadow2D> cards = scene(count, textures);
        for (int sample = -WARMUP; sample < SAMPLES; sample++) {
            animate(cards, sample + WARMUP);
            for (int turn = 0; turn < 2; turn++) {
                int backend = (sample + WARMUP + turn) & 1;
                clear(false);
                batch.begin();
                glFinish();
                glBeginQuery(GL_TIME_ELAPSED, query);
                long beforeBytes = allocations.getThreadAllocatedBytes(Thread.currentThread().threadId());
                long start = System.nanoTime();
                if (backend == 0) reference.draw(batch, light, cards, volume ? fills : null);
                else batched.draw(batch, light, cards, volume ? fills : null);
                long duration = System.nanoTime() - start;
                long bytes = allocations.getThreadAllocatedBytes(Thread.currentThread().threadId()) - beforeBytes;
                glEndQuery(GL_TIME_ELAPSED);
                batch.end();
                long elapsed = glGetQueryObjectui64(query, GL_QUERY_RESULT);
                if (sample >= 0) {
                    cpu[backend][sample] = duration;
                    gpu[backend][sample] = elapsed;
                    allocated[backend][sample] = bytes;
                }
            }
        }
        require(batched.getShadowCount() == count * (volume ? 2 : 1), "Timed workload emits every quad");
        if (textures == 0) require(batched.getDrawCalls() == (batched.getShadowCount() + 1023) / 1024, "Capacity flush count");
        double oldCpu = median(cpu[0]) / 1000.0;
        double newCpu = median(cpu[1]) / 1000.0;
        String line = String.format(Locale.ROOT, "%d,%s,%s,%d,%d,%.3f,%.3f,%.2f,%.3f,%.3f,%d,%d", count, volume ? "volume+contact" : "silhouette", textures == 0 ? "one atlas" : textures == 1 ? "32-card runs" : "alternating", reference.getDrawCalls(), batched.getDrawCalls(), oldCpu, newCpu, oldCpu / newCpu, median(gpu[0]) / 1000.0, median(gpu[1]) / 1000.0, median(allocated[0]), median(allocated[1]));
        results.add(line);
        System.out.println(line);
    }

    /**
     * Checks zero-work paths and lifecycle failures without changing input semantics.
     */
    private void verifyEarlyReturns() {
        List<SpriteGroundShadow2D> cards = scene(1, 0);
        batch.begin();
        light.setEnabled(false);
        batched.draw(batch, light, cards);
        require(batched.getDrawCalls() == 0 && batched.getShadowCount() == 0, "Disabled light skips work");
        light.setEnabled(true);
        light.setElevation(0);
        batched.draw(batch, light, cards);
        require(batched.getDrawCalls() == 0 && batched.getShadowCount() == 0, "Zero elevation skips work");
        light.setElevation(220);
        batched.draw(batch, light, cards, new PointLight2D[0]);
        require(batched.getDrawCalls() == 1 && batched.getShadowCount() == 2, "Empty fills retain contact behavior");
        batch.end();
        batched.close();
        batched.close();
        try {
            batched.draw(batch, light, cards);
            throw new AssertionError("Closed renderer accepted a draw");
        } catch (IllegalStateException expected) {
            require(true, "Closed renderer rejected");
        }
    }

    /**
     * Returns the middle sample after timing has finished.
     * @param values collected samples; sorted in place
     * @return median sample
     */
    private static long median(long[] values) {
        /*
         * Sorting stays outside measurement to keep the render timings isolated.
         */
        Arrays.sort(values);
        return values[values.length / 2];
    }

    /**
     * Keeps regression assertions active without requiring the JVM -ea switch.
     * @param condition expected invariant
     * @param message failure description
     */
    private static void require(boolean condition, String message) {
        /*
         * A failed comparison must stop the experiment before performance claims are reported.
         */
        if (!condition) throw new AssertionError(message);
    }

    @Override
    public void dispose() {
        if (batched != null) batched.close();
        if (reference != null) reference.close();
        if (batch != null) batch.dispose();
        if (first != null) first.dispose();
        if (second != null) second.dispose();
        if (image != null && spritePath != null) image.dispose();
        if (target != 0) glDeleteTextures(target);
        if (framebuffer != 0) glDeleteFramebuffers(framebuffer);
        if (query != 0) glDeleteQueries(query);
        if (sampler != 0) glDeleteSamplers(sampler);
        if (!done) throw new AssertionError("Experiment did not finish");
    }
}
