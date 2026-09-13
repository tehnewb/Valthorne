package valthorne.graphics.lighting2d;

import org.lwjgl.BufferUtils;
import valthorne.graphics.Color;
import valthorne.graphics.model.RenderStateSnapshot3D;
import valthorne.graphics.shader.Shader;
import valthorne.graphics.shader.ShaderSources;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Objects;

import static org.lwjgl.opengl.GL33.*;

/**
 * Batched XY-world lighting with cached polar shadows and a scaled HDR light map.
 * Capture scene colors between beginScene and endScene; completion composites into
 * the framebuffer and viewport active at capture start. Changed visible lighting
 * uses one instanced draw; unchanged light maps skip that draw while scene
 * composition still occurs. Draw unlit UI after capture completion.
 *
 * <p>Owns GPU resources and borrows light/occluder objects. Their revisions drive
 * cache invalidation, so use their supported mutation APIs. Resource/configuration
 * operations require the creating OpenGL thread. Capture is not nestable on one
 * instance; finish existing batches before capture transitions.</p>
 *
 * <pre>{@code
 * Lighting2D lighting = new Lighting2D();
 * lighting.addLight(new PointLight2D().setPosition(100, 80));
 * lighting.beginScene(0, 0, 800, 600, 800, 600);
 * try {
 *     // Draw and finish scene batches with matching XY-world coordinates.
 *     lighting.endScene();
 * } finally {
 *     lighting.cancelScene(); // No-op after successful completion.
 * }
 * lighting.close(); // At shutdown with the creating context still current.
 * }</pre>
 *
 * @author Albert Beaupre
 */
public final class Lighting2D implements AutoCloseable {
    /**
     * Instanced light-quad vertex shader source.
     */
    private static final String LIGHT_VERTEX = ShaderSources.load("lighting2d/light.vert");
    /**
     * Attenuation and polar-shadow fragment shader source.
     */
    private static final String LIGHT_FRAGMENT = ShaderSources.load("lighting2d/light.frag");
    /**
     * Vertex-ID fullscreen triangle shader source.
     */
    private static final String FULLSCREEN = ShaderSources.load("lighting2d/fullscreen.vert");
    /**
     * Scene/light composition shader source with exposure.
     */
    private static final String COMPOSITE = ShaderSources.load("lighting2d/composite.frag");
    private final Thread owner = Thread.currentThread(); // Creating OpenGL thread required for guarded operations.
    private final int capacity, resolution, atlas, instances, vao, sceneFbo, sceneTexture, lightFbo, lightTexture; // Light/sample limits and owned GPU resource identifiers.
    private final Shader lightShader, compositeShader; // Owned light and composition programs.
    private final ArrayList<Entry> lights = new ArrayList<>(); // Light registrations with atlas slots and shadow caches.
    private final ArrayList<Occluder2D> occluders = new ArrayList<>(); // Borrowed occluders with revision-tracked geometry.
    private final OccluderIndex2D index = new OccluderIndex2D(); // Owned CPU occluder broad-phase index.
    private final BitSet slots = new BitSet(); // Reserved atlas row indices.
    private final FloatBuffer instanceData, shadowData; // Reusable native instance and shadow-row staging buffers.
    private final Color ambient = new Color(.08f, .1f, .16f, 1); // Owned ambient linear RGB clear color.
    private long indexedGeometry = Long.MIN_VALUE; // Geometry fingerprint last indexed.
    private float scale = .5f, exposure = 1, minX, minY, worldWidth, worldHeight; // Map scale, exposure, and active XY-world capture rectangle.
    private int width, height, mapWidth, mapHeight, visibleCount, lastDrawCalls; // Scene/map dimensions and most recent light-pass counts.
    private long mapFingerprint = Long.MIN_VALUE, shadowUploads, mapRenders; // Cached map identity and cumulative work counters.
    private boolean disposed; // Whether owned GPU resources were released.
    private State capture; // Active capture's saved GL state, null when inactive.

    /**
     * Creates capacity for 512 lights with 1024 angular samples per shadow row.
     * Scene/light textures are sized lazily; map resolution initially uses half scale.
     */
    public Lighting2D() {this(512, 1024);}

    /**
     * Validates atlas dimensions, allocates staging buffers, and creates shaders and
     * GPU resources inside a selected-state restoration scope. Requires a current
     * OpenGL context; scene and map storage are allocated on first capture.
     *
     * @param capacity registered-light limit, one through 4096
     * @param shadowResolution angular samples per light, 64 through 4096
     * @throws IllegalArgumentException if these or device texture-size limits are exceeded
     */
    public Lighting2D(int capacity, int shadowResolution) {
        if (capacity < 1 || capacity > 4096 || shadowResolution < 64 || shadowResolution > 4096)
            throw new IllegalArgumentException("Invalid lighting capacity/resolution");
        if (capacity > glGetInteger(GL_MAX_TEXTURE_SIZE) || shadowResolution > glGetInteger(GL_MAX_TEXTURE_SIZE))
            throw new IllegalArgumentException("Shadow atlas exceeds device limits");
        this.capacity = capacity;
        resolution = shadowResolution;
        instanceData = BufferUtils.createFloatBuffer(capacity * 16);
        shadowData = BufferUtils.createFloatBuffer(resolution);
        try (State ignored = new State()) {
            lightShader = new Shader(LIGHT_VERTEX, LIGHT_FRAGMENT);
            compositeShader = new Shader(FULLSCREEN, COMPOSITE);
            atlas = glGenTextures();
            glActiveTexture(GL_TEXTURE0);
            glBindTexture(GL_TEXTURE_2D, atlas);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_R32F, resolution, capacity, 0, GL_RED, GL_FLOAT, 0L);
            parameters(GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
            sceneTexture = glGenTextures();
            lightTexture = glGenTextures();
            sceneFbo = glGenFramebuffers();
            lightFbo = glGenFramebuffers();
            vao = glGenVertexArrays();
            instances = glGenBuffers();
            glBindVertexArray(vao);
            glBindBuffer(GL_ARRAY_BUFFER, instances);
            glBufferData(GL_ARRAY_BUFFER, (long) capacity * 16 * 4, GL_STREAM_DRAW);
            for (int i = 0; i < 4; i++) {
                glEnableVertexAttribArray(i);
                glVertexAttribPointer(i, 4, GL_FLOAT, false, 64, i * 16L);
                glVertexAttribDivisor(i, 1);
            }
        }
    }

    /**
     * Allocates RGBA texture storage with linear/clamped sampling, attaches it to
     * its framebuffer, and checks completeness. Enclosing capture owns state restoration.
     *
     * @param texture owned texture identifier
     * @param fbo owned framebuffer identifier
     * @param w positive pixel width
     * @param h positive pixel height
     * @param format internal texture format
     * @throws IllegalStateException if the framebuffer is incomplete
     */
    private static void allocate(int texture, int fbo, int w, int h, int format) {
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, texture);
        glTexImage2D(GL_TEXTURE_2D, 0, format, w, h, 0, GL_RGBA, GL_FLOAT, 0L);
        parameters(GL_LINEAR);
        glBindFramebuffer(GL_FRAMEBUFFER, fbo);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, texture, 0);
        if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE)
            throw new IllegalStateException("Incomplete lighting framebuffer");
    }

    /**
     * Sets both filters and clamp-to-edge wrapping on the bound two-dimensional
     * texture. Atlas creation subsequently overrides S wrapping to repeat.
     *
     * @param filter OpenGL minification/magnification filter
     */
    private static void parameters(int filter) {
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, filter);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, filter);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    }

    /**
     * Rejects calls after disposal or off the creating thread. Context correctness
     * remains the caller's responsibility beyond this identity check.
     *
     * @throws IllegalStateException if disposed or on the wrong thread
     */
    private void check() {
        if (disposed) throw new IllegalStateException("Lighting2D is disposed");
        if (Thread.currentThread() != owner)
            throw new IllegalStateException("Use Lighting2D on its creating GL thread");
    }

    /**
     * Registers a borrowed light once by identity and reserves the first free atlas
     * row with its own CPU shadow cache. Invalidates the map; work waits until visible.
     *
     * @param light non-null light to register
     * @return this system
     * @throws NullPointerException if light is null
     * @throws IllegalStateException if capacity is full or lifecycle/thread checks fail
     */
    public Lighting2D addLight(PointLight2D light) {
        check();
        Objects.requireNonNull(light);
        if (lights.stream().anyMatch(e -> e.light == light)) return this;
        if (lights.size() == capacity) throw new IllegalStateException("Light capacity reached");
        int slot = slots.nextClearBit(0);
        slots.set(slot);
        lights.add(new Entry(light, slot, new PolarShadow2D(resolution)));
        mapFingerprint = Long.MIN_VALUE;
        return this;
    }

    /**
     * Removes the identity-matching registration, frees its atlas slot, and invalidates
     * the map. Does not modify or dispose the light object.
     *
     * @param light light to remove
     * @return true if a registration was removed
     */
    public boolean removeLight(PointLight2D light) {
        check();
        for (int i = 0; i < lights.size(); i++)
            if (lights.get(i).light == light) {
                slots.clear(lights.remove(i).slot);
                mapFingerprint = Long.MIN_VALUE;
                return true;
            }
        return false;
    }

    /**
     * Retains a non-null occluder unless already contained. The next lighting pass
     * detects membership/revision changes through its geometry fingerprint.
     *
     * @param o occluder to register
     * @return this system
     * @throws NullPointerException if o is null
     */
    public Lighting2D addOccluder(Occluder2D o) {
        check();
        Objects.requireNonNull(o);
        if (!occluders.contains(o)) occluders.add(o);
        return this;
    }

    /**
     * Removes the first matching occluder without modifying it. The next geometry
     * fingerprint check determines index/shadow invalidation.
     *
     * @param o occluder to remove
     * @return true when membership changed
     */
    public boolean removeOccluder(Occluder2D o) {
        check();
        return occluders.remove(o);
    }

    /**
     * Validates finite nonnegative RGB, copies the color, and invalidates the map.
     * Map clear alpha is always one, regardless of the copied input alpha.
     *
     * @param color non-null linear-space ambient color
     * @return this system
     * @throws NullPointerException if color is null
     * @throws IllegalArgumentException if RGB is negative or non-finite
     */
    public Lighting2D setAmbient(Color color) {
        check();
        PointLight2D.nonnegative(color.r());
        PointLight2D.nonnegative(color.g());
        PointLight2D.nonnegative(color.b());
        ambient.set(color);
        mapFingerprint = Long.MIN_VALUE;
        return this;
    }

    /**
     * Sets the map-size fraction applied during the next capture resize. Scene color
     * remains full resolution; map dimensions are rounded with a one-pixel minimum.
     *
     * @param scale finite fraction from 0.25 through one
     * @return this system
     * @throws IllegalArgumentException if scale is outside the supported range
     */
    public Lighting2D setResolutionScale(float scale) {
        check();
        if (!Float.isFinite(scale) || scale < .25f || scale > 1)
            throw new IllegalArgumentException("Resolution scale must be in [.25,1]");
        this.scale = scale;
        return this;
    }

    /**
     * Sets a positive finite composition multiplier without invalidating lighting
     * caches; exposure is applied after light-map generation.
     *
     * @param value requested exposure
     * @return this system
     * @throws IllegalArgumentException if value is nonpositive or non-finite
     */
    public Lighting2D setExposure(float value) {
        check();
        PointLight2D.positive(value);
        exposure = value;
        return this;
    }

    /**
     * Reads lights passing enabled, contribution, and XY view-bounds checks in the
     * most recent pass. Does not count only unshadowed or actually visible pixels.
     *
     * @return last visible light count
     */
    public int getVisibleLightCount() {return visibleCount;}

    /**
     * Reads light-only instanced draws: zero for cache reuse or no visible lights,
     * one for a refreshed map containing lights. Excludes the fullscreen composite.
     *
     * @return last light draw count
     */
    public int getLastLightDrawCalls() {return lastDrawCalls;}

    /**
     * Reads cumulative atlas-row uploads, excluding unchanged and shadow-disabled rows.
     *
     * @return completed shadow upload count
     */
    public long getShadowUploadCount() {return shadowUploads;}

    /**
     * Reads cumulative map refreshes, including ambient-only clears. Cache reuse
     * leaves this count unchanged.
     *
     * @return map refresh count
     */
    public long getLightMapRenderCount() {return mapRenders;}

    /**
     * Exposes the owned HDR texture as a borrowed identifier. Storage appears on first
     * capture and can be resized later; closing the system invalidates this resource.
     *
     * @return light-map texture identifier; do not delete it
     */
    public int getLightTextureId() {
        check();
        return lightTexture;
    }

    /**
     * Saves selected GL state, sizes capture targets, and binds/clears the scene-color
     * framebuffer. Defines XY lighting bounds, sets a full capture viewport, disables
     * scissor/sRGB, and enables all color writes. Finish existing batches first.
     * Failure cancels capture and restores covered state; success requires end/cancel.
     *
     * @param minX finite lower world X
     * @param minY finite lower world Y
     * @param worldWidth positive finite world extent X
     * @param worldHeight positive finite world extent Y
     * @param pixelWidth positive capture width within device texture limits
     * @param pixelHeight positive capture height within device texture limits
     * @throws IllegalStateException if already capturing or lifecycle/thread checks fail
     * @throws IllegalArgumentException if world or pixel dimensions are invalid
     */
    public void beginScene(float minX, float minY, float worldWidth, float worldHeight, int pixelWidth, int pixelHeight) {
        check();
        if (capture != null) throw new IllegalStateException("Scene capture is already active");
        PointLight2D.finite(minX);
        PointLight2D.finite(minY);
        PointLight2D.positive(worldWidth);
        PointLight2D.positive(worldHeight);
        if (pixelWidth < 1 || pixelHeight < 1 || pixelWidth > glGetInteger(GL_MAX_TEXTURE_SIZE) || pixelHeight > glGetInteger(GL_MAX_TEXTURE_SIZE))
            throw new IllegalArgumentException("Invalid viewport dimensions");
        capture = new State();
        try {
            if (this.minX != minX || this.minY != minY || this.worldWidth != worldWidth || this.worldHeight != worldHeight)
                mapFingerprint = Long.MIN_VALUE;
            this.minX = minX;
            this.minY = minY;
            this.worldWidth = worldWidth;
            this.worldHeight = worldHeight;
            resize(pixelWidth, pixelHeight);
            glBindFramebuffer(GL_FRAMEBUFFER, sceneFbo);
            glViewport(0, 0, width, height);
            glDisable(GL_SCISSOR_TEST);
            glDisable(GL_FRAMEBUFFER_SRGB);
            glColorMask(true, true, true, true);
            glClearColor(0, 0, 0, 0);
            glClear(GL_COLOR_BUFFER_BIT);
        } catch (RuntimeException | Error e) {
            cancelScene();
            throw e;
        }
    }

    /**
     * Finishes capture, refreshes changed lighting, and composites into the saved
     * framebuffer/viewport. Scene batches must already be finished. Capture state is
     * cleared before rendering; covered GL state is restored in finally on success
     * or failure. The saved destination receives one fullscreen composition draw.
     *
     * @throws IllegalStateException if no capture is active or lifecycle/thread checks fail
     */
    public void endScene() {
        check();
        if (capture == null) throw new IllegalStateException("Call beginScene first");
        State saved = capture;
        capture = null;
        try {
            glDisable(GL_SCISSOR_TEST);
            glDisable(GL_DEPTH_TEST);
            glDisable(GL_CULL_FACE);
            glDisable(GL_FRAMEBUFFER_SRGB);
            glColorMask(true, true, true, true);
            glBindSampler(0, 0);
            glBindSampler(1, 0);
            renderLights();
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, saved.draw);
            glBindFramebuffer(GL_READ_FRAMEBUFFER, saved.read);
            glViewport(saved.viewport[0], saved.viewport[1], saved.viewport[2], saved.viewport[3]);
            glDisable(GL_BLEND);
            glBindVertexArray(vao);
            compositeShader.bind();
            glActiveTexture(GL_TEXTURE0);
            glBindTexture(GL_TEXTURE_2D, sceneTexture);
            glActiveTexture(GL_TEXTURE1);
            glBindTexture(GL_TEXTURE_2D, lightTexture);
            compositeShader.setUniform1i("u_scene", 0);
            compositeShader.setUniform1i("u_light", 1);
            compositeShader.setUniform1f("u_exposure", exposure);
            glDrawArrays(GL_TRIANGLES, 0, 3);
        } finally {saved.close();}
    }

    /**
     * Restores active capture state without drawing or deleting textures. An inactive
     * capture is a no-op after lifecycle/thread validation.
     */
    public void cancelScene() {
        check();
        if (capture != null) {
            capture.close();
            capture = null;
        }
    }

    /**
     * Reallocates scene RGBA8 and scaled light RGBA16F targets only when required sizes
     * change. Scaled dimensions are rounded and at least one pixel; resize invalidates
     * the map fingerprint. The caller supplies enclosing state restoration.
     *
     * @param w validated scene pixel width
     * @param h validated scene pixel height
     */
    private void resize(int w, int h) {
        int mw = Math.max(1, Math.round(w * scale)), mh = Math.max(1, Math.round(h * scale));
        if (width == w && height == h && mapWidth == mw && mapHeight == mh) return;
        allocate(sceneTexture, sceneFbo, w, h, GL_RGBA8);
        allocate(lightTexture, lightFbo, mw, mh, GL_RGBA16F);
        width = w;
        height = h;
        mapWidth = mw;
        mapHeight = mh;
        mapFingerprint = Long.MIN_VALUE;
    }

    /**
     * Rebuilds the occluder index after geometry-fingerprint changes, updates visible
     * polar shadow caches, and packs instance records. Reuses equal map fingerprints;
     * otherwise clears ambient and issues at most one instanced light draw. Updates
     * statistics and leaves GL state for capture completion to restore.
     */
    private void renderLights() {
        lastDrawCalls = 0;
        visibleCount = 0;
        long fingerprint = 17;
        instanceData.clear();
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, atlas);
        long geometry = 17;
        for (Occluder2D o : occluders) {
            o.synchronize();
            geometry = geometry * 31 + System.identityHashCode(o);
            geometry = geometry * 31 + o.revision;
        }
        if (geometry != indexedGeometry) {
            index.rebuild(occluders);
            indexedGeometry = geometry;
        }
        for (Entry entry : lights) {
            PointLight2D l = entry.light;
            if (!l.enabled || l.intensity == 0 || l.r + l.g + l.b == 0 || l.x + l.radius < minX || l.y + l.radius < minY || l.x - l.radius > minX + worldWidth || l.y - l.radius > minY + worldHeight)
                continue;
            boolean dirty = false;
            if (entry.geometry != geometry || entry.lightRevision != l.shadowRevision) {
                dirty = entry.shadow.update(l, l.shadows ? index.query(l, occluders) : List.of());
                entry.geometry = geometry;
                entry.lightRevision = l.shadowRevision;
            }
            if (dirty && l.shadows) {
                shadowData.clear();
                shadowData.put(entry.shadow.data()).flip();
                glTexSubImage2D(GL_TEXTURE_2D, 0, 0, entry.slot, resolution, 1, GL_RED, GL_FLOAT, shadowData);
                shadowUploads++;
            }
            fingerprint = fingerprint * 31 + System.identityHashCode(l);
            fingerprint = fingerprint * 31 + l.revision;
            fingerprint = fingerprint * 31 + entry.shadow.getRebuildCount();
            instanceData.put(l.x).put(l.y).put(l.radius).put(l.shadows ? entry.slot : -1);
            instanceData.put(l.r).put(l.g).put(l.b).put(l.intensity);
            instanceData.put(l.sourceRadius).put((float) Math.cos(l.inner)).put((float) Math.cos(l.outer)).put(l.direction);
            instanceData.put(l.elevation).put(0).put(0).put(0);
            visibleCount++;
        }
        if (fingerprint == mapFingerprint) return;
        glBindFramebuffer(GL_FRAMEBUFFER, lightFbo);
        glViewport(0, 0, mapWidth, mapHeight);
        glClearColor(ambient.r(), ambient.g(), ambient.b(), 1);
        glClear(GL_COLOR_BUFFER_BIT);
        if (visibleCount > 0) {
            glEnable(GL_BLEND);
            glBlendEquation(GL_FUNC_ADD);
            glBlendFunc(GL_ONE, GL_ONE);
            glBindVertexArray(vao);
            glBindBuffer(GL_ARRAY_BUFFER, instances);
            instanceData.flip();
            glBufferData(GL_ARRAY_BUFFER, instanceData, GL_STREAM_DRAW);
            lightShader.bind();
            lightShader.setUniform1i("u_shadows", 0);
            lightShader.setUniform1f("u_rows", capacity);
            lightShader.setUniform1f("u_resolution", resolution);
            lightShader.setUniform4f("u_world", minX, minY, worldWidth, worldHeight);
            glDrawArraysInstanced(GL_TRIANGLE_STRIP, 0, 4, visibleCount);
            lastDrawCalls = 1;
        }
        mapRenders++;
        mapFingerprint = fingerprint;
    }

    /**
     * Cancels capture, releases owned shaders/textures/framebuffers/buffers/VAO, clears
     * registrations, and marks disposed. Borrowed lights and occluders remain alive.
     * After successful disposal, repeated calls return immediately.
     */
    @Override
    public void close() {
        if (disposed) return;
        check();
        cancelScene();
        lightShader.dispose();
        compositeShader.dispose();
        glDeleteTextures(atlas);
        glDeleteTextures(sceneTexture);
        glDeleteTextures(lightTexture);
        glDeleteFramebuffers(sceneFbo);
        glDeleteFramebuffers(lightFbo);
        glDeleteBuffers(instances);
        glDeleteVertexArrays(vao);
        lights.clear();
        occluders.clear();
        disposed = true;
    }

    /**
     * Registration tying one borrowed light to a reserved atlas row and owned polar
     * shadow cache. Revision markers control reconsideration when the light is visible.
     * Removal releases the row for a subsequent registration.
     *
     * @author Albert Beaupre
     */
    private static final class Entry {
        final PointLight2D light; // Borrowed registered light.
        final int slot; // Reserved atlas row.
        final PolarShadow2D shadow; // Owned reusable polar-shadow cache.
        long geometry = Long.MIN_VALUE, lightRevision = Long.MIN_VALUE; // Last geometry and light-shadow revisions considered.

        /**
         * Retains registration state with initially invalid revision markers so a visible
         * pass considers the light's shadow geometry.
         *
         * @param light borrowed light
         * @param slot reserved atlas row
         * @param shadow owned CPU shadow cache
         */
        Entry(PointLight2D light, int slot, PolarShadow2D shadow) {
            this.light = light;
            this.slot = slot;
            this.shadow = shadow;
        }
    }

    /**
     * Selected OpenGL snapshot extending shared render state with framebuffer bindings,
     * viewport, clear color, write mask, scissor/sRGB enablement, and samplers zero/one.
     * It owns no GPU objects and does not preserve arbitrary unlisted context state.
     * Keep captured object identifiers alive until restoration.
     *
     * @author Albert Beaupre
     */
    private static final class State implements AutoCloseable {
        final RenderStateSnapshot3D base = new RenderStateSnapshot3D(); // Captured shared render bindings and capabilities.
        final int draw = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING), read = glGetInteger(GL_READ_FRAMEBUFFER_BINDING); // Captured draw/read framebuffer identifiers.
        final int[] viewport = new int[4]; // Captured viewport origin and size.
        final float[] clear = new float[4]; // Captured RGBA clear color.
        final boolean[] mask = new boolean[4]; // Captured per-channel color-write mask.
        final boolean scissor = glIsEnabled(GL_SCISSOR_TEST), srgb = glIsEnabled(GL_FRAMEBUFFER_SRGB); // Captured scissor and sRGB enablement.
        final int sampler0, sampler1; // Captured sampler bindings on units zero and one.

        /**
         * Captures supplemental context state immediately and restores the active texture
         * selector after querying sampler bindings. Use the same current context on close.
         */
        State() {
            glGetIntegerv(GL_VIEWPORT, viewport);
            glGetFloatv(GL_COLOR_CLEAR_VALUE, clear);
            ByteBuffer data = BufferUtils.createByteBuffer(4);
            glGetBooleanv(GL_COLOR_WRITEMASK, data);
            for (int i = 0; i < 4; i++) mask[i] = data.get(i) != 0;
            int active = glGetInteger(GL_ACTIVE_TEXTURE);
            glActiveTexture(GL_TEXTURE0);
            sampler0 = glGetInteger(GL_SAMPLER_BINDING);
            glActiveTexture(GL_TEXTURE1);
            sampler1 = glGetInteger(GL_SAMPLER_BINDING);
            glActiveTexture(active);
        }

        /**
         * Restores captured supplemental state and then the shared render snapshot.
         * Repeated calls reapply original values; no closed flag is maintained.
         * Captured GPU objects must remain alive in the same current context.
         */
        public void close() {
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, draw);
            glBindFramebuffer(GL_READ_FRAMEBUFFER, read);
            glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
            glClearColor(clear[0], clear[1], clear[2], clear[3]);
            glColorMask(mask[0], mask[1], mask[2], mask[3]);
            if (scissor) glEnable(GL_SCISSOR_TEST);
            else glDisable(GL_SCISSOR_TEST);
            if (srgb) glEnable(GL_FRAMEBUFFER_SRGB);
            else glDisable(GL_FRAMEBUFFER_SRGB);
            glBindSampler(0, sampler0);
            glBindSampler(1, sampler1);
            base.close();
        }
    }
}
