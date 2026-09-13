package valthorne.graphics.model;

import valthorne.camera.Camera3D;
import valthorne.graphics.GraphicsCapabilities;
import valthorne.graphics.lighting3d.Lighting3D;

import java.util.Objects;

/**
 * Selects a supported desktop renderer once, on construction. AUTO prefers the
 * existing Filament adapter when its prerequisites exist, otherwise tiled raster
 * lighting. It does not silently recover from shader, asset or native-load failures.
 *
 * <p>The raster path retains scene lights, material emission and transparency, but
 * is visually different: no Filament IBL, glass refraction or automatic point-light
 * shadows. Configure a borrowed directional shadow map through rasterState if needed.
 * Neither path changes physics or owns source models/textures. This is not an ES port.
 * Construct, render and close with the creating GLFW context current.</p>
 */
public final class SceneRenderer3D implements AutoCloseable {
    /** Explicit preferences never silently downgrade. */
    public enum Backend { AUTO, FILAMENT, RASTER }

    private final Backend backend;
    private final long context;
    private final Thread owner = Thread.currentThread();
    private FilamentRenderer3D filament;
    private ModelBatch3D batch;
    private Lighting3D lighting;
    private final MeshRenderState3D state = new MeshRenderState3D();
    private final int[] viewport = new int[4];
    private boolean closed;

    /** Creates the automatically selected renderer. */
    public SceneRenderer3D() {this(Backend.AUTO);}

    /** @param preference requested backend; AUTO permits the documented raster fallback */
    public SceneRenderer3D(Backend preference) {
        context = org.lwjgl.glfw.GLFW.glfwGetCurrentContext();
        if (context == 0) throw new IllegalStateException("Create SceneRenderer3D with a GLFW context current");
        backend = select(GraphicsCapabilities.current(), preference);
        try {
            if (backend == Backend.FILAMENT) filament = new FilamentRenderer3D();
            else {
                batch = new ModelBatch3D();
                lighting = new Lighting3D();
                state.setLighting(lighting);
            }
        } catch (RuntimeException | Error failure) {
            try {close();} catch (RuntimeException | Error cleanup) {failure.addSuppressed(cleanup);}
            throw failure;
        }
    }

    /** Pure selection for capability reports/tests; does not allocate graphics resources. */
    public static Backend select(GraphicsCapabilities capabilities, Backend preference) {
        Objects.requireNonNull(capabilities).requireRaster();
        Objects.requireNonNull(preference);
        if (preference == Backend.FILAMENT && !capabilities.filament())
            throw new UnsupportedOperationException("Filament is unavailable on this platform/context; choose AUTO or RASTER");
        return preference == Backend.AUTO ? (capabilities.filament() ? Backend.FILAMENT : Backend.RASTER) : preference;
    }

    /** @return selected backend; never AUTO */
    public Backend getBackend() {return backend;}

    /** @return active Filament renderer for backend-specific configuration */
    public FilamentRenderer3D filament() {
        check();
        if (filament == null) throw new IllegalStateException("Raster backend is active");
        return filament;
    }

    /** @return live raster state for lighting, fog and borrowed shadow-map configuration */
    public MeshRenderState3D rasterState() {
        check();
        if (batch == null) throw new IllegalStateException("Filament backend is active");
        return state;
    }

    /** Draws to the current framebuffer/viewport; source lights replace raster light membership. */
    public void render(Scene3D scene, Camera3D camera) {
        check();
        Objects.requireNonNull(scene);
        Objects.requireNonNull(camera);
        if (filament != null) filament.render(scene, camera);
        else {
            org.lwjgl.opengl.GL11.glGetIntegerv(org.lwjgl.opengl.GL11.GL_VIEWPORT, viewport);
            if (viewport[2] <= 0 || viewport[3] <= 0) return;
            camera.rebuild(viewport[2], viewport[3]);
            lighting.clearLights();
            var lights = scene.getLights();
            for (int i = 0; i < lights.size(); i++) lighting.addLight(lights.get(i));
            state.setLighting(lighting);
            state.setCamera(camera);
            scene.render(batch, state);
        }
    }

    private void check() {
        if (closed) throw new IllegalStateException("SceneRenderer3D is closed");
        if (Thread.currentThread() != owner || org.lwjgl.glfw.GLFW.glfwGetCurrentContext() != context)
            throw new IllegalStateException("Use SceneRenderer3D with its creating context current on its owner thread");
    }

    /** Releases owned resources; source models, physics and borrowed shadow maps remain owned by the caller. */
    @Override public void close() {
        if (closed) return;
        check();
        try {
            if (filament != null) filament.close();
        } finally {
            try {if (batch != null) batch.close();}
            finally {
                try {if (lighting != null) lighting.close();}
                finally {closed = true;}
            }
        }
    }
}
