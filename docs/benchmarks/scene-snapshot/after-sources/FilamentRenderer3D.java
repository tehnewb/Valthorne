package valthorne.graphics.model;

import static io.github.erkko68.filament.ffm.FilamentC.*;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.glfw.GLFWNativeWGL.*;
import static org.lwjgl.opengl.GL43.*;

import static java.lang.foreign.ValueLayout.*;

import io.github.erkko68.filament.ffm.*;
import io.github.erkko68.filament.ffm.FilamentLoader;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.*;

/**
 * Adapts Valthorne triangle scenes to Filament 1.75 through Windows x64 OpenGL
 * texture sharing. Create, render, configure, and close on the GLFW context's
 * owning thread. The adapter owns native renderables, materials, mesh buffers,
 * environment resources, and its shared output texture; source models and albedo
 * textures remain borrowed and must stay alive while referenced.
 * <p>
 * Model identities cache immutable mesh uploads. Scene changes refresh transforms
 * and material parameters, while explicit point lights update independently.
 * Emissive instances create point-light approximations at their world origins by
 * default, using 1000 lumens per emission unit. Disable a material's emission-light
 * flag to retain visible glow without an implicit light. The studio environment
 * provides static indirect illumination; local lights do not rebuild it.
 * </p>
 * <p>
 * Translucent render passes select alpha compositing. Opaque-pass transmission
 * above 0.01 selects the screen-space glass material. Imported albedo textures
 * share the caller's GL objects and receive a full mip chain with trilinear
 * filtering. Call invalidate after changing base pixels to regenerate mip levels.
 * Custom procedural renderables and in-place animated mesh-buffer updates are
 * not represented by this adapter.
 * </p>
 * <pre>{@code
 * try (FilamentRenderer3D renderer = new FilamentRenderer3D()) {
 *     renderer.setQuality(FilamentRenderer3D.Quality.HIGH);
 *     renderer.setEnvironmentIntensity(1000);
 *     renderer.render(scene, camera); // Current destination framebuffer and viewport.
 * }
 * }</pre>
 * See the repository's docs/filament.md for material packages and integration limits.
 * @author Albert Beaupre
 */
public final class FilamentRenderer3D implements AutoCloseable {
    private final float[] projectionValues = new float[16], matrixValues = new float[16]; // Reusable JOML projection and transform component arrays.

    /**
     * Multisample quality presets at full output resolution. All presets retain
     * full-resolution ambient occlusion; INTERACTIVE disables MSAA, HIGH requests
     * four samples, and ULTRA requests eight, subject to backend support.
     * @author Albert Beaupre
     */
    public enum Quality {
        /**
         * Full-resolution ambient occlusion with multisample antialiasing disabled.
         */
        INTERACTIVE,
        /**
         * Full-resolution ambient occlusion with four requested MSAA samples, subject to backend support.
         */
        HIGH,
        /**
         * Full-resolution ambient occlusion with eight requested MSAA samples, subject to backend support.
         */
        ULTRA
    }

    private final Thread owner = Thread.currentThread(); // Thread that owns this renderer and its confined native arena.
    private final Matrix4f inverseView = new Matrix4f(); // Scratch inverse camera view matrix.
    private final int[] viewport = new int[4]; // Reusable caller viewport bounds in pixels.
    private final MemorySegment engine, renderer, view, camera, scene, sky, swap; // Owned core Filament engine, rendering, camera, scene, skybox, and swap-chain handles.
    private MemorySegment target = MemorySegment.NULL, color = MemorySegment.NULL; // Owned output render target and imported color texture wrapper.
    private int texture, framebuffer, width, height; // Owned shared GL output texture/read framebuffer and current pixel dimensions.
    private final Arena arena = Arena.ofConfined(); // Owner-thread native memory retained for the renderer lifetime.
    private final MemorySegment matrix = arena.allocate(16 * 8),
            projection = arena.allocate(16 * 8); // Persistent transform and double-precision projection transfer storage.
    private final Map<String, MemorySegment> names = new HashMap<>(); // Cached native material parameter strings allocated in the arena.
    private final IdentityHashMap<Model3D, Mesh> meshes = new IdentityHashMap<>(); // Owned immutable native meshes keyed by borrowed source identity.
    private final IdentityHashMap<valthorne.graphics.texture.Texture, MemorySegment> textures =
            new IdentityHashMap<>(); // Owned imported texture wrappers keyed by borrowed source texture identity.
    private final List<Entry> entries = new ArrayList<>(); // Reusable native renderable/material slots in collected scene order.
    private final ArrayList<ExplicitLight> pointLights = new ArrayList<>(); // Reusable native slots for active explicit scene point lights.
    private MemorySegment opaque, glass, alpha, white, indirect, environment; // Owned material packages, fallback texture, and static environment-light resources.
    private long signature = Long.MIN_VALUE; // Last synchronized model/material signature; minimum value forces refresh.
    private final PathTracingScene.Collector sceneCollector = new PathTracingScene.Collector(); // Renderer-owned CPU collection scratch.
    private boolean closed, regenerateMipmaps; // Resource lifetime flag and pending imported-texture mip refresh.
    private float exposure = 1; // Positive camera exposure multiplier.

    /**
     * Counts distinct model identities currently cached as native vertex/index
     * buffers. The count changes during scene synchronization and eviction; query
     * on the owner thread. This diagnostic does not itself check closure.
     *
     * @return cached mesh count
     */
    public int getCachedMeshCount() {
        return meshes.size();
    }

    /**
     * Counts active native point lights after the latest render, including emissive
     * model approximations and explicit scene lights. Excludes the environment and
     * explicit lights with zero intensity or black RGB.
     *
     * @return current native scene light count
     * @throws IllegalStateException if called off the owner thread or after closure
     */
    public int getPointLightCount() {
        requireOpen();
        return (int) FilaScene_getLightCount(scene);
    }

    /**
     * Owner-thread cache for one reusable explicit native point-light slot. Stores
     * the last uploaded source components so moving lights update only changed
     * parameters without invalidating mesh/material bindings.
     * @author Albert Beaupre
     */
    private static final class ExplicitLight {
        final int entity; // Owned native point-light entity identifier.
        float x = Float.NaN, y, z, r = Float.NaN, g, b; // Last uploaded source position and RGB; NaN forces initial synchronization.
        float intensity = Float.NaN, range = Float.NaN; // Last uploaded lumen intensity and falloff range.
        boolean shadows; // Last uploaded shadow-casting flag.

        /**
         * Associates a cache slot with a newly created native light entity. NaN sentinels
         * force the first position, color, intensity, and range upload.
         *
         * @param entity owned native light entity ID
         */
        ExplicitLight(int entity) {this.entity = entity;}
    }

    /**
     * Owned immutable native mesh buffers and local bounds cached by source model
     * identity. Bounds use center/half-extent representation for Filament culling.
     *
     * <p>The native segments refer to cached vertex and index resources managed by the renderer.
     * Bounds are expressed in model-local coordinates so instances can share geometry while
     * supplying independent transforms; constructing this record does not allocate a mesh.</p>
     *
     * @param vertices native vertex buffer
     * @param indices native index buffer
     * @param cx local bounds center X
     * @param cy local bounds center Y
     * @param cz local bounds center Z
     * @param hx local bounds half-width
     * @param hy local bounds half-height
     * @param hz local bounds half-depth
     * @author Albert Beaupre
     */
    private record Mesh(
            MemorySegment vertices,
            MemorySegment indices,
            float cx,
            float cy,
            float cz,
            float hx,
            float hy,
            float hz) {}

    /**
     * Reusable native renderable/material slot associated with a borrowed source
     * model. The reserved light entity gains a component only when emission lighting
     * is enabled and nonzero.
     *
     * @param model borrowed model identity used to find cached mesh buffers
     * @param glass whether the screen-space transmission material is selected
     * @param alpha whether the alpha-compositing material is selected
     * @param entity owned renderable entity ID
     * @param light owned entity ID reserved for implicit emission lighting
     * @param material owned native material instance
     * @author Albert Beaupre
     */
    private record Entry(
            Model3D model, boolean glass, boolean alpha, int entity, int light, MemorySegment material) {}

    /**
     * Creates a shared OpenGL Filament engine, headless rendering objects, compiled
     * surface/glass/alpha materials, a white fallback texture, and the bundled studio
     * environment. Temporarily releases the GLFW context while creating Filament's
     * shared driver context, then restores it. Enables temporal/FXAA smoothing and
     * ambient occlusion, with screen-space reflections disabled.
     *
     * @throws UnsupportedOperationException if the platform is not the supported Windows sharing path
     * @throws IllegalStateException if no GLFW context is current, engine creation fails, or a required material/environment cannot be loaded
     */
    public FilamentRenderer3D() {
        if (org.lwjgl.system.Platform.get() != org.lwjgl.system.Platform.WINDOWS)
            throw new UnsupportedOperationException(
                    "Filament texture sharing currently requires Windows x64 and an OpenGL"
                            + " context");
        if (glfwGetCurrentContext() == 0)
            throw new IllegalStateException(
                    "Create FilamentRenderer3D with the caller's OpenGL context current");
        FilamentLoader.load();
        var builder = FilaEngineBuilder_create();
        FilaEngineBuilder_backend(builder, FILA_ENGINE_BACKEND_OPENGL());
        long window = glfwGetCurrentContext();
        FilaEngineBuilder_sharedContext(
                builder, MemorySegment.ofAddress(glfwGetWGLContext(window)));
        glfwMakeContextCurrent(0);
        try {
            engine = FilaEngineBuilder_build(builder);
        } finally {
            glfwMakeContextCurrent(window);
            FilaEngineBuilder_destroy(builder);
        }
        if (engine.equals(MemorySegment.NULL))
            throw new IllegalStateException("Filament engine creation failed");
        renderer = FilaEngine_createRenderer(engine);
        view = FilaEngine_createView(engine);
        scene = FilaEngine_createScene(engine);
        camera = FilaEngine_createCameraAuto(engine);
        swap = FilaEngine_createSwapChainHeadless(engine, 1, 1, 0);
        FilaView_setScene(view, scene);
        FilaView_setCamera(view, camera);
        var sb = FilaSkyboxBuilder_create();
        FilaSkyboxBuilder_color(sb, .15f, .2f, .3f, 1);
        sky = FilaSkyboxBuilder_build(sb, engine);
        FilaSkyboxBuilder_destroy(sb);
        FilaScene_setSkybox(scene, sky);
        opaque = material("surface");
        glass = material("glass");
        alpha = material("alpha");
        var tb = FilaTextureBuilder_create();
        FilaTextureBuilder_width(tb, 1);
        FilaTextureBuilder_height(tb, 1);
        FilaTextureBuilder_format(tb, FILA_TEXTURE_INTERNAL_FORMAT_RGBA8());
        white = FilaTextureBuilder_build(tb, engine);
        FilaTextureBuilder_destroy(tb);
        var pixel = arena.allocate(4);
        pixel.set(JAVA_INT, 0, -1);
        FilaTexture_setImage(
                white,
                engine,
                0,
                0,
                0,
                0,
                1,
                1,
                1,
                pixel,
                4,
                FILA_PIXEL_DATA_FORMAT_RGBA(),
                FILA_PIXEL_DATA_TYPE_UBYTE(),
                (byte) 1,
                0,
                0,
                0,
                MemorySegment.NULL,
                MemorySegment.NULL,
                MemorySegment.NULL);
        try (var input =
                FilamentRenderer3D.class.getResourceAsStream(
                        "/valthorne/filament/studio-ibl.ktx")) {
            byte[] data =
                    Objects.requireNonNull(input, "Missing studio environment").readAllBytes();
            try (var upload = Arena.ofConfined()) {
                var bytes = upload.allocateFrom(JAVA_BYTE, data);
                var sh = upload.allocate(27 * 4);
                environment = FilaKTX1Loader_createTexture(engine, bytes, data.length, false);
                if (!FilaKTX1Loader_getSphericalHarmonics(bytes, data.length, sh))
                    throw new IllegalStateException("Missing environment irradiance");
                indirect = FilaKTX1Loader_createIndirectLight(engine, environment, sh);
                FilaIndirectLight_setIntensity(indirect, 1000);
                FilaScene_setIndirectLight(scene, indirect);
                FilaEngine_flushAndWait(engine, -1L);
            }
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
        FilaView_setAntiAliasing(view, 1);
        FilaView_setShadowType(view, FILA_VIEW_SHADOW_TYPE_PCF());
        var ao = FilaViewAmbientOcclusionOptions.allocate(arena);
        FilaView_getAmbientOcclusionOptions(view, ao);
        FilaViewAmbientOcclusionOptions.enabled(ao, true);
        FilaViewAmbientOcclusionOptions.quality(ao, 3);
        FilaViewAmbientOcclusionOptions.resolution(ao, 1);
        FilaViewAmbientOcclusionOptions.radius(ao, .3f);
        FilaView_setAmbientOcclusionOptions(view, ao);
        var ssr = FilaViewScreenSpaceReflectionsOptions.allocate(arena);
        FilaView_getScreenSpaceReflectionsOptions(view, ssr);
        FilaViewScreenSpaceReflectionsOptions.enabled(ssr, false);
        FilaView_setScreenSpaceReflectionsOptions(view, ssr);
        var taa = FilaViewTemporalAntiAliasingOptions.allocate(arena);
        FilaView_getTemporalAntiAliasingOptions(view, taa);
        FilaViewTemporalAntiAliasingOptions.enabled(taa, true);
        FilaView_setTemporalAntiAliasingOptions(view, taa);
    }

    /**
     * Sets the positive multiplier applied through Filament camera sensitivity on
     * subsequent renders. Does not rebuild mesh or material caches.
     *
     * @param value finite positive exposure multiplier
     * @throws IllegalArgumentException if value is nonpositive or nonfinite
     * @throws IllegalStateException if called off the owner thread or after closure
     */
    public void setExposure(float value) {
        requireOpen();
        if (!(value > 0) || !Float.isFinite(value))
            throw new IllegalArgumentException("Exposure must be positive and finite");
        exposure = value;
    }

    /**
     * Updates the bundled static indirect-light intensity immediately. This scales
     * environment illumination and reflections without recomputing the environment.
     *
     * @param value finite nonnegative native indirect-light intensity
     * @throws IllegalArgumentException if value is negative or nonfinite
     * @throws IllegalStateException if called off the owner thread or after closure
     */
    public void setEnvironmentIntensity(float value) {
        requireOpen();
        if (value < 0 || !Float.isFinite(value))
            throw new IllegalArgumentException(
                    "Environment intensity must be finite and nonnegative");
        FilaIndirectLight_setIntensity(indirect, value);
    }

    /**
     * Toggles temporal antialiasing and the view's post-process antialiasing setting.
     * Does not alter MSAA, which is controlled by setQuality.
     *
     * @param enabled whether temporal and post-process smoothing are enabled
     * @throws IllegalStateException if called off the owner thread or after closure
     */
    public void setAntiAliasing(boolean enabled) {
        requireOpen();
        try (var temporary = Arena.ofConfined()) {
            var taa = FilaViewTemporalAntiAliasingOptions.allocate(temporary);
            FilaView_getTemporalAntiAliasingOptions(view, taa);
            FilaViewTemporalAntiAliasingOptions.enabled(taa, enabled);
            FilaView_setTemporalAntiAliasingOptions(view, taa);
        }
        FilaView_setAntiAliasing(view, enabled ? 1 : 0);
    }

    /**
     * Applies full-resolution ambient occlusion settings and configures MSAA:
     * disabled for INTERACTIVE, four requested samples for HIGH, or eight for ULTRA.
     * Does not change temporal antialiasing or output resolution.
     *
     * @param quality nonnull preset
     * @throws NullPointerException if quality is null
     * @throws IllegalStateException if called off the owner thread or after closure
     */
    public void setQuality(Quality quality) {
        requireOpen();
        Objects.requireNonNull(quality);
        try (var temporary = Arena.ofConfined()) {
            var ao = FilaViewAmbientOcclusionOptions.allocate(temporary);
            FilaView_getAmbientOcclusionOptions(view, ao);
            FilaViewAmbientOcclusionOptions.quality(ao, 3);
            FilaViewAmbientOcclusionOptions.resolution(ao, 1);
            FilaView_setAmbientOcclusionOptions(view, ao);
            var msaa = FilaViewMultiSampleAntiAliasingOptions.allocate(temporary);
            FilaView_getMultiSampleAntiAliasingOptions(view, msaa);
            FilaViewMultiSampleAntiAliasingOptions.enabled(msaa, quality != Quality.INTERACTIVE);
            FilaViewMultiSampleAntiAliasingOptions.sampleCount(
                    msaa, (byte) (quality == Quality.ULTRA ? 8 : 4));
            FilaView_setMultiSampleAntiAliasingOptions(view, msaa);
        }
    }

    /**
     * Forces scene binding refresh and imported texture mip regeneration on the
     * next render. Use after externally editing borrowed texture pixels. Cached
     * mesh buffers remain keyed by model identity, so this does not reupload mutated
     * geometry from an already cached model.
     *
     * @throws IllegalStateException if called off the owner thread or after closure
     */
    public void invalidate() {
        requireOpen();
        signature = Long.MIN_VALUE;
        regenerateMipmaps = true;
    }

    /**
     * Caches a native UTF-8 parameter name in the renderer's confined arena.
     * The returned segment remains valid until renderer closure.
     *
     * @param value parameter name
     * @return borrowed arena-backed native string
     */
    private MemorySegment name(String value) {
        return names.computeIfAbsent(value, arena::allocateFrom);
    }

    /**
     * Loads one bundled compiled material package and creates an owned native
     * material, retaining package bytes in the renderer arena.
     *
     * @param file resource basename without the filamat extension
     * @return owned native material handle
     * @throws IllegalStateException if the package is missing or cannot be read
     */
    private MemorySegment material(String file) {
        try (var input =
                FilamentRenderer3D.class.getResourceAsStream(
                        "/valthorne/filament/" + file + ".filamat")) {
            if (input == null)
                throw new IllegalStateException("Missing Filament material: " + file);
            byte[] data = input.readAllBytes();
            var bytes = arena.allocateFrom(JAVA_BYTE, data);
            var b = FilaMaterial_Builder_create();
            try {
                FilaMaterial_Builder_package(b, bytes, data.length);
                return FilaMaterial_Builder_build(b, engine);
            } finally {
                FilaMaterial_Builder_destroy(b);
            }
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Synchronizes supported scene models and lights, renders at the current viewport
     * size into the shared output texture, waits for Filament completion, and blits
     * color into the caller's current draw framebuffer. Rebuilds the source camera
     * for that size; zero-sized viewports return without rendering.
     * <p>
     * A caller GL completion wait protects reuse of the shared output texture.
     * The final blit restores the read-framebuffer binding and sRGB enablement on
     * normal return and preserves destination depth. This method does not install
     * a general try/finally GL-state guard around all native operations.
     * </p>
     *
     * @param source supported triangle-model scene
     * @param sourceCamera camera whose projection and view are copied to Filament
     * @throws IllegalStateException if called off the owner thread or after closure
     * @throws IllegalArgumentException if scene renderables or explicit light values are unsupported
     */
    public void render(Scene3D source, valthorne.camera.Camera3D sourceCamera) {
        checkOwner();
        // Complete the caller's previous reads before the shared driver writes
        // the output texture again. Benchmark-only waits must not hide this edge.
        glFinish();
        if (closed) throw new IllegalStateException("Renderer is closed");
        int[] vp = viewport;
        glGetIntegerv(GL_VIEWPORT, vp);
        if (vp[2] <= 0 || vp[3] <= 0) return;
        if (vp[2] != width || vp[3] != height) resize(vp[2], vp[3]);
        sourceCamera.rebuild(width, height);
        float[] p = sourceCamera.getProjection().get(projectionValues),
                m = inverseView.set(sourceCamera.getView()).invert().get(matrixValues);
        for (int i = 0; i < 16; i++) {
            projection.setAtIndex(JAVA_DOUBLE, i, p[i]);
            matrix.setAtIndex(JAVA_FLOAT, i, m[i]);
        }
        FilaCamera_setCustomProjection(
                camera, projection, projection, sourceCamera.getNear(), sourceCamera.getFar());
        FilaCamera_setModelMatrix(camera, matrix);
        FilaCamera_setExposure(camera, 4, 1f / 60, 100 * exposure);
        sync(source);
        FilaView_setViewport(view, 0, 0, width, height);
        FilaRenderer_renderStandaloneView(renderer, view);
        FilaEngine_flushAndWait(engine, -1L);
        int old = glGetInteger(GL_READ_FRAMEBUFFER_BINDING);
        boolean srgb = glIsEnabled(GL_FRAMEBUFFER_SRGB);
        glDisable(GL_FRAMEBUFFER_SRGB);
        glBindFramebuffer(GL_READ_FRAMEBUFFER, framebuffer);
        glBlitFramebuffer(
                0,
                0,
                width,
                height,
                vp[0],
                vp[1],
                vp[0] + width,
                vp[1] + height,
                GL_COLOR_BUFFER_BIT,
                GL_NEAREST);
        glBindFramebuffer(GL_READ_FRAMEBUFFER, old);
        if (srgb) glEnable(GL_FRAMEBUFFER_SRGB);
    }

    /**
     * Updates explicit lights, then compares a collected model/material signature.
     * When changed, reuses or replaces compatible renderable slots, uploads transforms
     * and material parameters, adjusts implicit emission lights and shadow flags,
     * and evicts mesh/texture wrappers no longer referenced. Regenerates imported
     * mips when invalidation requested it.
     *
     * @param source current source scene
     */
    private void sync(Scene3D source) {
        syncPointLights(source.getLights());
        var snapshot = sceneCollector.capture(source);
        if (signature == snapshot.signature()) return;
        snapshot.instances.removeIf(instance -> instance.model().triangles().length == 0);
        // Entries represent native geometry/material slots, not persistent source identities.
        // Matching slots can be reused even when particles leave or enter the source list:
        // the loop below refreshes every transform, material parameter and light component.
        for (int i = 0; i < snapshot.instances.size(); i++) {
            var instance = snapshot.instances.get(i);
            boolean blended = instance.material().getRenderPass() == RenderPass3D.TRANSLUCENT;
            boolean transparent = !blended && instance.material().getTransmission() > .01f;
            if (i == entries.size()) {
                entries.add(createEntry(instance.model(), transparent, blended));
            } else {
                var entry = entries.get(i);
                if (entry.model() != instance.model() || entry.glass() != transparent || entry.alpha() != blended) {
                    Entry replacement = createEntry(instance.model(), transparent, blended);
                    entries.set(i, replacement);
                    destroyEntry(entry);
                }
            }
        }
        while (entries.size() > snapshot.instances.size())
            destroyEntry(entries.remove(entries.size() - 1));
        for (int i = 0; i < entries.size(); i++) {
            var e = entries.get(i);
            var instance = snapshot.instances.get(i);
            var material = instance.material();
            var tint = material.getTint();
            var emissive = material.getEmissive();
            for (int j = 0; j < 16; j++)
                matrix.setAtIndex(JAVA_FLOAT, j, instance.transform().get(j / 4, j % 4));
            var tm = FilaEngine_getTransformManager(engine);
            FilaTransformManager_setTransform(
                    tm, FilaTransformManager_getInstance(tm, e.entity()), matrix);
            FilaMaterialInstance_setParameterFloat4(
                    e.material(),
                    name("tint"),
                    linear(tint.r()),
                    linear(tint.g()),
                    linear(tint.b()),
                    tint.a());
            FilaMaterialInstance_setParameterFloat(
                    e.material(), name("roughness"), material.getRoughness());
            FilaMaterialInstance_setParameterFloat(
                    e.material(), name("metallic"), material.getMetallic());
            FilaMaterialInstance_setParameterFloat(
                    e.material(), name("cutoff"), material.getAlphaCutoff());
            float power = material.getEmissionStrength() * emissive.a();
            FilaMaterialInstance_setParameterFloat3(
                    e.material(),
                    name("emission"),
                    linear(emissive.r()) * power * 200,
                    linear(emissive.g()) * power * 200,
                    linear(emissive.b()) * power * 200);
            FilaMaterialInstance_setParameterTexture(
                    e.material(),
                    name("albedo"),
                    material.getTexture() == null
                            ? white
                            : textures.computeIfAbsent(material.getTexture(), this::importTexture),
                    FilaTextureSampler_create(
                            FILA_TEXTURE_SAMPLER_MIN_FILTER_LINEAR_MIPMAP_LINEAR(),
                            FILA_TEXTURE_SAMPLER_MAG_FILTER_LINEAR(),
                            FILA_TEXTURE_SAMPLER_WRAP_MODE_REPEAT(),
                            FILA_TEXTURE_SAMPLER_WRAP_MODE_REPEAT(),
                            FILA_TEXTURE_SAMPLER_WRAP_MODE_REPEAT()));
            if (e.glass()) {
                FilaMaterialInstance_setParameterFloat(
                        e.material(), name("ior"), material.getIndexOfRefraction());
                FilaMaterialInstance_setParameterFloat(
                        e.material(), name("transmission"), material.getTransmission());
                FilaMaterialInstance_setParameterFloat(
                        e.material(), name("thickness"), meshes.get(e.model()).hz() * 2);
            }
            boolean emitting =
                    power > 0 && (emissive.r() > 0 || emissive.g() > 0 || emissive.b() > 0);
            var lm = FilaEngine_getLightManager(engine);
            boolean hasLight = FilaLightManager_hasComponent(lm, e.light());
            if (emitting && material.isEmissionLightEnabled()) {
                if (!hasLight) {
                    var lb = FilaLightManagerBuilder_create(FILA_LIGHT_MANAGER_TYPE_POINT());
                    try {
                        FilaLightManagerBuilder_intensity(lb, power * 1000);
                        FilaLightManagerBuilder_falloff(lb, 30);
                        FilaLightManagerBuilder_castShadows(lb, true);
                        if (!FilaLightManagerBuilder_build(lb, engine, e.light()))
                            throw new IllegalStateException("Filament light creation failed");
                    } finally {
                        FilaLightManagerBuilder_destroy(lb);
                    }
                    FilaScene_addEntity(scene, e.light());
                }
                int light = FilaLightManager_getInstance(lm, e.light());
                float[] transform = instance.transform().get(matrixValues);
                FilaLightManager_setPosition(
                        lm, light, transform[12], transform[13], transform[14]);
                FilaLightManager_setColor(
                        lm,
                        light,
                        linear(emissive.r()),
                        linear(emissive.g()),
                        linear(emissive.b()));
                FilaLightManager_setIntensity(lm, light, power * 1000);
            } else if (hasLight) {
                FilaScene_remove(scene, e.light());
                FilaLightManager_destroy(lm, e.light());
            }
            FilaRenderableManager_setCastShadows(
                    FilaEngine_getRenderableManager(engine),
                    FilaRenderableManager_getInstance(
                            FilaEngine_getRenderableManager(engine), e.entity()),
                    material.isCastsShadow() && !emitting && !e.glass() && !e.alpha());
            FilaRenderableManager_setReceiveShadows(
                    FilaEngine_getRenderableManager(engine),
                    FilaRenderableManager_getInstance(FilaEngine_getRenderableManager(engine), e.entity()),
                    material.isReceivesShadow());
        }
        signature = snapshot.signature();
        var usedModels = Collections.newSetFromMap(new IdentityHashMap<Model3D, Boolean>());
        var usedTextures =
                Collections.newSetFromMap(
                        new IdentityHashMap<valthorne.graphics.texture.Texture, Boolean>());
        for (var instance : snapshot.instances) {
            usedModels.add(instance.model());
            if (instance.material().getTexture() != null)
                usedTextures.add(instance.material().getTexture());
        }
        meshes.entrySet()
                .removeIf(
                        e -> {
                            if (usedModels.contains(e.getKey())) return false;
                            FilaEngine_destroyVertexBuffer(engine, e.getValue().vertices());
                            FilaEngine_destroyIndexBuffer(engine, e.getValue().indices());
                            return true;
                        });
        textures.entrySet()
                .removeIf(
                        e -> {
                            if (usedTextures.contains(e.getKey())) return false;
                            FilaEngine_destroyTexture(engine, e.getValue());
                            return true;
                        });
        if (regenerateMipmaps) {
            for (var entry : textures.entrySet()) {
                if (Math.max(entry.getKey().getWidth(), entry.getKey().getHeight()) > 1)
                    FilaTexture_generateMipmaps(entry.getValue(), engine);
            }
            regenerateMipmaps = false;
        }
    }

    /**
     * Updates compact reusable native light slots independently of mesh signatures.
     * Skips zero-intensity or black lights, converts source RGB to linear values,
     * scales intensity by 1000 lumens, and uploads changed position, color, range,
     * and shadow settings. Destroys surplus slots after source removal.
     *
     * @param source current explicit scene lights
     * @throws IllegalArgumentException if source positions, colors, or converted intensity/color are invalid
     */
    private void syncPointLights(List<PointLight3D> source) {
        if (source.isEmpty() && pointLights.isEmpty()) return;
        var manager = FilaEngine_getLightManager(engine);
        int used = 0;
        for (int i = 0; i < source.size(); i++) {
            var light = source.get(i);
            var position = light.getPosition();
            var color = light.getColor();
            float lumens = light.getIntensity() * 1000f;
            if (!position.isFinite() || !Float.isFinite(lumens)
                    || !Float.isFinite(color.r()) || color.r() < 0
                    || !Float.isFinite(color.g()) || color.g() < 0
                    || !Float.isFinite(color.b()) || color.b() < 0)
                throw new IllegalArgumentException("Point light position, RGB and lumen intensity must be finite; RGB must be nonnegative");
            if (lumens == 0 || (color.r() == 0 && color.g() == 0 && color.b() == 0)) continue;
            ExplicitLight entry;
            if (used == pointLights.size()) {
                entry = createPointLight();
                pointLights.add(entry);
            } else entry = pointLights.get(used);
            used++;
            int instance = FilaLightManager_getInstance(manager, entry.entity);
            if (entry.x != position.x || entry.y != position.y || entry.z != position.z) {
                FilaLightManager_setPosition(manager, instance, position.x, position.y, position.z);
                entry.x = position.x;
                entry.y = position.y;
                entry.z = position.z;
            }
            if (entry.r != color.r() || entry.g != color.g() || entry.b != color.b()) {
                float r = linear(color.r()), g = linear(color.g()), b = linear(color.b());
                if (!Float.isFinite(r) || !Float.isFinite(g) || !Float.isFinite(b))
                    throw new IllegalArgumentException("Point light RGB exceeds the renderer's finite range");
                FilaLightManager_setColor(manager, instance, r, g, b);
                entry.r = color.r();
                entry.g = color.g();
                entry.b = color.b();
            }
            if (entry.intensity != lumens) {
                FilaLightManager_setIntensity(manager, instance, lumens);
                entry.intensity = lumens;
            }
            if (entry.range != light.getRange()) {
                FilaLightManager_setFalloff(manager, instance, light.getRange());
                entry.range = light.getRange();
            }
            if (entry.shadows != light.isCastsShadows()) {
                FilaLightManager_setShadowCaster(manager, instance, light.isCastsShadows());
                entry.shadows = light.isCastsShadows();
            }
        }
        while (pointLights.size() > used) destroyPointLight(pointLights.removeLast());
    }

    /**
     * Creates a native point-light entity with zero intensity, range ten, and
     * shadows disabled, then adds it to the scene. Releases the entity on failure
     * and always destroys its temporary builder.
     *
     * @return owned cached light slot
     * @throws IllegalStateException if native light construction fails
     */
    private ExplicitLight createPointLight() {
        int entity = FilaEntityManager_create(FilaEntityManager_get());
        var builder = FilaLightManagerBuilder_create(FILA_LIGHT_MANAGER_TYPE_POINT());
        try {
            FilaLightManagerBuilder_intensity(builder, 0);
            FilaLightManagerBuilder_falloff(builder, 10);
            FilaLightManagerBuilder_castShadows(builder, false);
            if (!FilaLightManagerBuilder_build(builder, engine, entity))
                throw new IllegalStateException("Filament point light creation failed");
            FilaScene_addEntity(scene, entity);
            return new ExplicitLight(entity);
        } catch (RuntimeException | Error failure) {
            FilaEngine_destroyEntity(engine, entity);
            FilaEntityManager_destroy(FilaEntityManager_get(), entity);
            throw failure;
        } finally {
            FilaLightManagerBuilder_destroy(builder);
        }
    }

    /**
     * Removes a light from the native scene and destroys its engine components
     * and entity-manager ID.
     *
     * @param entry owned light slot to release
     */
    private void destroyPointLight(ExplicitLight entry) {
        FilaScene_remove(scene, entry.entity);
        FilaEngine_destroyEntity(engine, entry.entity);
        FilaEntityManager_destroy(FilaEntityManager_get(), entry.entity);
    }

    /**
     * Imports a borrowed OpenGL albedo texture into Filament after synchronizing
     * caller writes. Requests a complete mip chain and generates it immediately
     * unless a pending invalidation will regenerate all imported textures together.
     * The wrapper is owned, while the underlying source texture remains caller-owned.
     *
     * @param source live borrowed albedo texture
     * @return owned native texture wrapper
     */
    private MemorySegment importTexture(valthorne.graphics.texture.Texture source) {
        glFinish();
        var b = FilaTextureBuilder_create();
        FilaTextureBuilder_width(b, source.getWidth());
        FilaTextureBuilder_height(b, source.getHeight());
        int levels = 32 - Integer.numberOfLeadingZeros(Math.max(source.getWidth(), source.getHeight()));
        FilaTextureBuilder_levels(b, (byte) levels);
        FilaTextureBuilder_format(b, FILA_TEXTURE_INTERNAL_FORMAT_RGBA8());
        FilaTextureBuilder_usage(b, FILA_TEXTURE_USAGE_SAMPLEABLE()
                | FILA_TEXTURE_USAGE_BLIT_SRC() | FILA_TEXTURE_USAGE_BLIT_DST()
                | FILA_TEXTURE_USAGE_GEN_MIPMAPPABLE());
        FilaTextureBuilder_importTexture(b, source.getTextureID());
        try {
            MemorySegment imported = FilaTextureBuilder_build(b, engine);
            if (levels > 1 && !regenerateMipmaps) FilaTexture_generateMipmaps(imported, engine);
            return imported;
        } finally {
            FilaTextureBuilder_destroy(b);
        }
    }

    /**
     * Converts an sRGB component to linear light using the standard piecewise
     * transfer function. Does not clamp inputs or validate the result.
     *
     * @param value sRGB component
     * @return linear-light component
     */
    private static float linear(float value) {
        return value <= .04045f ? value / 12.92f : (float) Math.pow((value + .055f) / 1.055f, 2.4);
    }

    /**
     * Uploads a model's triangles into immutable native buffers with thirteen floats
     * per vertex: position, normal-frame quaternion, UV, and linearized RGBA color.
     * Uses sequential indices and derives local center/half-extent bounds, clamping
     * each half-extent to at least 0.001. Source normals must be finite and nonzero.
     * Waits for upload completion before releasing temporary native memory.
     *
     * @param model borrowed nonempty triangle model
     * @return newly owned native mesh resources
     */
    private Mesh mesh(Model3D model) {
        var triangles = model.triangles();
        int count = triangles.length * 3;
        float[] data = new float[count * 13];
        int[] indices = new int[count];
        int vertex = 0;
        float minX = Float.POSITIVE_INFINITY,
                minY = minX,
                minZ = minX,
                maxX = -minX,
                maxY = -minX,
                maxZ = -minX;
        for (var t : triangles) {
            var positions = new Vector3f[] {t.a, t.b, t.c};
            var normals = new Vector3f[] {t.normalA, t.normalB, t.normalC};
            var uvs = new org.joml.Vector2f[] {t.uvA, t.uvB, t.uvC};
            for (int j = 0; j < 3; j++) {
                var p = positions[j];
                var n = normals[j];
                int k = vertex * 13;
                data[k] = p.x();
                data[k + 1] = p.y();
                data[k + 2] = p.z();
                minX = Math.min(minX, p.x());
                minY = Math.min(minY, p.y());
                minZ = Math.min(minZ, p.z());
                maxX = Math.max(maxX, p.x());
                maxY = Math.max(maxY, p.y());
                maxZ = Math.max(maxZ, p.z());
                // Unit quaternion rotating +Z to the smooth normal; no tangent-space normal map is
                // used.
                float nx = n.x(),
                        ny = n.y(),
                        nz = n.z(),
                        length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
                nx /= length;
                ny /= length;
                nz /= length;
                float qw = (float) Math.sqrt(Math.max(0, (1 + nz) * .5f));
                if (qw < .0001f) {
                    data[k + 3] = 1;
                    data[k + 6] = .00001f;
                } else {
                    data[k + 3] = -ny / (2 * qw);
                    data[k + 4] = nx / (2 * qw);
                    data[k + 6] = qw;
                }
                data[k + 7] = uvs[j].x();
                data[k + 8] = uvs[j].y();
                data[k + 9] = linear(t.color.r());
                data[k + 10] = linear(t.color.g());
                data[k + 11] = linear(t.color.b());
                data[k + 12] = t.color.a();
                indices[vertex] = vertex;
                vertex++;
            }
        }
        var vb = FilaVertexBufferBuilder_create();
        FilaVertexBufferBuilder_vertexCount(vb, count);
        FilaVertexBufferBuilder_bufferCount(vb, (byte) 1);
        FilaVertexBufferBuilder_attribute(vb, 0, (byte) 0, 20, 0, (byte) 52);
        FilaVertexBufferBuilder_attribute(vb, 1, (byte) 0, 21, 12, (byte) 52);
        FilaVertexBufferBuilder_attribute(vb, 3, (byte) 0, 19, 28, (byte) 52);
        FilaVertexBufferBuilder_attribute(vb, 2, (byte) 0, 21, 36, (byte) 52);
        var vertices = FilaVertexBufferBuilder_build(vb, engine);
        FilaVertexBufferBuilder_destroy(vb);
        var ib = FilaIndexBufferBuilder_create();
        FilaIndexBufferBuilder_indexCount(ib, count);
        FilaIndexBufferBuilder_bufferType(ib, 1);
        var indexBuffer = FilaIndexBufferBuilder_build(ib, engine);
        FilaIndexBufferBuilder_destroy(ib);
        try (var upload = Arena.ofConfined()) {
            FilaVertexBuffer_setBufferAt(
                    vertices,
                    engine,
                    (byte) 0,
                    upload.allocateFrom(JAVA_FLOAT, data),
                    (long) data.length * 4,
                    0,
                    MemorySegment.NULL,
                    MemorySegment.NULL,
                    MemorySegment.NULL);
            FilaIndexBuffer_setBuffer(
                    indexBuffer,
                    engine,
                    upload.allocateFrom(JAVA_INT, indices),
                    (long) indices.length * 4,
                    0,
                    MemorySegment.NULL,
                    MemorySegment.NULL,
                    MemorySegment.NULL);
            FilaEngine_flushAndWait(engine, -1L);
        }
        return new Mesh(
                vertices,
                indexBuffer,
                (minX + maxX) / 2,
                (minY + maxY) / 2,
                (minZ + maxZ) / 2,
                Math.max(.001f, (maxX - minX) / 2),
                Math.max(.001f, (maxY - minY) / 2),
                Math.max(.001f, (maxZ - minZ) / 2));
    }

    /**
     * Creates a renderable slot using cached or newly uploaded model buffers and
     * a material instance selected for alpha, glass, or opaque rendering. Allocates
     * a second entity ID for optional emission lighting. Releases the slot if native
     * renderable construction fails.
     *
     * @param model borrowed model identity
     * @param transparent whether glass transmission is selected
     * @param blended whether alpha compositing is selected
     * @return owned scene entry
     * @throws IllegalStateException if native renderable construction fails
     */
    private Entry createEntry(Model3D model, boolean transparent, boolean blended) {
        Mesh mesh = meshes.computeIfAbsent(model, this::mesh);
        var entry = new Entry(model, transparent, blended,
                FilaEntityManager_create(FilaEntityManager_get()),
                FilaEntityManager_create(FilaEntityManager_get()),
                FilaMaterial_createInstance(blended ? alpha : transparent ? glass : opaque));
        var builder = FilaRenderableManagerBuilder_create(1);
        try {
            FilaRenderableManagerBuilder_geometry(builder, 0, 4, mesh.vertices(), mesh.indices());
            FilaRenderableManagerBuilder_material(builder, 0, entry.material());
            FilaRenderableManagerBuilder_boundingBox(
                    builder, mesh.cx(), mesh.cy(), mesh.cz(), mesh.hx(), mesh.hy(), mesh.hz());
            FilaRenderableManagerBuilder_castShadows(builder, !transparent && !blended);
            FilaRenderableManagerBuilder_receiveShadows(builder, true);
            if (!FilaRenderableManagerBuilder_build(builder, engine, entry.entity()))
                throw new IllegalStateException("Filament mesh creation failed");
            FilaTransformManager_create(FilaEngine_getTransformManager(engine), entry.entity());
            FilaScene_addEntity(scene, entry.entity());
            return entry;
        } catch (RuntimeException | Error failure) {
            destroyEntry(entry);
            throw failure;
        } finally {
            FilaRenderableManagerBuilder_destroy(builder);
        }
    }

    /**
     * Removes renderable and light entities from the scene, destroys their components
     * and IDs, and releases the material instance. Shared mesh buffers remain cached.
     *
     * @param entry owned slot to release
     */
    private void destroyEntry(Entry entry) {
        FilaScene_remove(scene, entry.entity());
        FilaScene_remove(scene, entry.light());
        FilaEngine_destroyEntity(engine, entry.entity());
        FilaEngine_destroyEntity(engine, entry.light());
        FilaEntityManager_destroy(FilaEntityManager_get(), entry.entity());
        FilaEntityManager_destroy(FilaEntityManager_get(), entry.light());
        FilaEngine_destroyMaterialInstance(engine, entry.material());
    }

    /**
     * Destroys every native renderable/material slot and clears the entry list.
     * Shared meshes and imported textures are released separately by their owner.
     */
    private void clearEntries() {
        for (var entry : entries) destroyEntry(entry);
        entries.clear();
    }

    /**
     * Waits for pending Filament work, detaches and destroys the old target, then
     * allocates a shared RGBA8 output texture and read framebuffer at the new size.
     * Imports the texture into a new Filament render target and updates the view.
     * Restores caller texture/read-framebuffer bindings on normal completion.
     *
     * @param w positive output width in pixels
     * @param h positive output height in pixels
     */
    private void resize(int w, int h) {
        FilaEngine_flushAndWait(engine, -1L);
        if (!target.equals(MemorySegment.NULL)) {
            FilaView_setRenderTarget(view, MemorySegment.NULL);
            FilaEngine_destroyRenderTarget(engine, target);
            FilaEngine_destroyTexture(engine, color);
            glDeleteFramebuffers(framebuffer);
            glDeleteTextures(texture);
        }
        width = w;
        height = h;
        int old = glGetInteger(GL_TEXTURE_BINDING_2D),
                fb = glGetInteger(GL_READ_FRAMEBUFFER_BINDING);
        texture = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, texture);
        glTexStorage2D(GL_TEXTURE_2D, 1, GL_RGBA8, w, h);
        glBindTexture(GL_TEXTURE_2D, old);
        glFinish();
        var tb = FilaTextureBuilder_create();
        FilaTextureBuilder_width(tb, w);
        FilaTextureBuilder_height(tb, h);
        FilaTextureBuilder_format(tb, FILA_TEXTURE_INTERNAL_FORMAT_RGBA8());
        FilaTextureBuilder_usage(
                tb, FILA_TEXTURE_USAGE_COLOR_ATTACHMENT() | FILA_TEXTURE_USAGE_SAMPLEABLE());
        FilaTextureBuilder_importTexture(tb, texture);
        color = FilaTextureBuilder_build(tb, engine);
        FilaTextureBuilder_destroy(tb);
        var rb = FilaRenderTargetBuilder_create();
        FilaRenderTargetBuilder_texture(rb, 0, color);
        target = FilaRenderTargetBuilder_build(rb, engine);
        FilaRenderTargetBuilder_destroy(rb);
        FilaView_setRenderTarget(view, target);
        framebuffer = glGenFramebuffers();
        glBindFramebuffer(GL_READ_FRAMEBUFFER, framebuffer);
        glFramebufferTexture2D(
                GL_READ_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, texture, 0);
        glBindFramebuffer(GL_READ_FRAMEBUFFER, fb);
    }

    /**
     * Releases explicit and implicit lights, renderable slots, mesh buffers, imported
     * wrappers, materials, environment, rendering objects, shared output storage,
     * and the confined arena. Source models and textures remain caller-owned.
     * Repeated calls on the owner thread have no effect.
     *
     * @throws IllegalStateException if called off the creating thread
     */
    @Override
    public void close() {
        checkOwner();
        if (closed) return;
        closed = true;
        sceneCollector.clear();
        for (var light : pointLights) destroyPointLight(light);
        pointLights.clear();
        clearEntries();
        for (var mesh : meshes.values()) {
            FilaEngine_destroyVertexBuffer(engine, mesh.vertices());
            FilaEngine_destroyIndexBuffer(engine, mesh.indices());
        }
        for (var t : textures.values()) FilaEngine_destroyTexture(engine, t);
        FilaEngine_destroyTexture(engine, white);
        FilaEngine_destroyMaterial(engine, opaque);
        FilaEngine_destroyMaterial(engine, glass);
        FilaEngine_destroyMaterial(engine, alpha);
        FilaEngine_destroyIndirectLight(engine, indirect);
        FilaEngine_destroyTexture(engine, environment);
        FilaEngine_flushAndWait(engine, -1L);
        FilaEngine_destroyView(engine, view);
        FilaEngine_destroyRenderer(engine, renderer);
        if (!target.equals(MemorySegment.NULL)) {
            FilaEngine_destroyRenderTarget(engine, target);
            FilaEngine_destroyTexture(engine, color);
        }
        FilaEngine_destroySkybox(engine, sky);
        FilaEngine_destroyScene(engine, scene);
        FilaEngine_destroyCamera(engine, camera);
        FilaEngine_destroySwapChain(engine, swap);
        FilaEngine_destroy(engine);
        glDeleteFramebuffers(framebuffer);
        glDeleteTextures(texture);
        arena.close();
    }

    /**
     * Requires the thread that created this renderer. A compatible current context
     * remains the caller's responsibility.
     *
     * @throws IllegalStateException if called from another thread
     */
    private void checkOwner() {
        if (Thread.currentThread() != owner)
            throw new IllegalStateException("Use FilamentRenderer3D on its creating thread");
    }

    /**
     * Checks owner-thread access and rejects operations after resource closure.
     *
     * @throws IllegalStateException if called off the owner thread or after closure
     */
    private void requireOpen() {
        checkOwner();
        if (closed) throw new IllegalStateException("Renderer is closed");
    }
}
