package valthorne.graphics.lighting3d;

import org.lwjgl.BufferUtils;
import valthorne.camera.Camera3D;
import valthorne.graphics.Color;
import valthorne.graphics.model.PointLight3D;
import valthorne.graphics.shader.Shader;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static org.lwjgl.opengl.GL33.*;

/**
 * OpenGL 3.3 tiled forward-lighting resources for linear-space surface shading.
 * Owns texture-buffer objects for packed point lights and tile indices, while
 * borrowing light objects and camera state. ModelBatch3D prepares it through
 * MeshRenderState3D before drawing; unchanged inputs avoid repeated uploads.
 * The diffuse environment blends sky and ground using Z-up surface normals.
 *
 * <p>Create, prepare, bind, mutate, and close on the creating OpenGL thread.
 * Binding uses texture units three and four and leaves their restoration to the
 * batch. The grid's all-visible mode provides a diagnostic reference path with
 * the same shading and no per-tile rejection.</p>
 *
 * <pre>{@code
 * Lighting3D lighting = new Lighting3D();
 * lighting.addLight(new PointLight3D().setPosition(0, 0, 5));
 * // With the intended viewport and rebuilt camera already active:
 * lighting.prepare(camera);
 * // The compatible batch binds lighting when drawing the scene.
 * lighting.close(); // While the creating OpenGL context remains current.
 * }</pre>
 *
 * @author Albert Beaupre
 */
public final class Lighting3D implements AutoCloseable {
    private final Thread owner = Thread.currentThread(); // Creating thread required for resource and mutation operations.
    private final ArrayList<PointLight3D> lights = new ArrayList<>(); // Borrowed lights in registration order; duplicates allowed.
    private final LightGrid3D grid = new LightGrid3D(); // Owned CPU packing and tile-culling cache.
    private final Color sky = new Color(.16f, .21f, .3f, 1), ground = new Color(.035f, .028f, .025f, 1); // Owned linear RGB environment colors for positive/negative Z normals.
    private final int lightBuffer, gridBuffer, lightTexture, gridTexture, maxTexels; // Owned buffer/texture identifiers and device texture-buffer texel limit.
    private final FloatBuffer lightData = BufferUtils.createFloatBuffer(LightGrid3D.MAX_LIGHTS * 8); // Reusable native upload buffer for packed light records.
    private final int[] viewport = new int[4]; // Latest OpenGL viewport origin and dimensions.
    private IntBuffer gridData; // Growable reusable native upload buffer for tile records.
    private float exposure = 1f; // Positive output exposure multiplier supplied at bind time.
    private long uploads; // Completed light/grid upload pairs.
    private boolean disposed; // Whether owned OpenGL resources have been deleted.

    /**
     * Captures the creating thread, queries texture-buffer capacity, and allocates
     * light/grid buffers and texture identifiers. A current OpenGL context is required;
     * data storage is uploaded lazily by prepare.
     */
    public Lighting3D() {
        maxTexels = glGetInteger(GL_MAX_TEXTURE_BUFFER_SIZE);
        lightBuffer = glGenBuffers();
        gridBuffer = glGenBuffers();
        lightTexture = glGenTextures();
        gridTexture = glGenTextures();
    }

    /**
     * Checks a color's RGB sum for finiteness and each RGB component for nonnegativity.
     * Alpha is not validated because environment shading consumes only RGB.
     *
     * @param c non-null candidate linear-space color
     * @throws NullPointerException if c is null
     * @throws IllegalArgumentException if RGB validation fails
     */
    private static void validate(Color c) {
        Objects.requireNonNull(c);
        if (!Float.isFinite(c.r() + c.g() + c.b()) || c.r() < 0 || c.g() < 0 || c.b() < 0)
            throw new IllegalArgumentException("Environment must be finite nonnegative linear RGB");
    }

    /**
     * Rejects operations after disposal or on a thread other than the creating thread.
     * Does not independently verify that the original OpenGL context is current.
     *
     * @throws IllegalStateException if disposed or called on the wrong thread
     */
    private void check() {
        if (disposed) throw new IllegalStateException("Lighting3D is disposed");
        if (Thread.currentThread() != owner)
            throw new IllegalStateException("Lighting3D requires its creating GL thread");
    }

    /**
     * Appends a borrowed non-null light under the fixed input capacity. Duplicate
     * objects are allowed and contribute separate entries. Changes upload on prepare.
     *
     * @param light light to retain
     * @return this lighting system
     * @throws NullPointerException if light is null
     * @throws IllegalStateException if full, disposed, or used from the wrong thread
     */
    public Lighting3D addLight(PointLight3D light) {
        check();
        Objects.requireNonNull(light);
        if (lights.size() == LightGrid3D.MAX_LIGHTS)
            throw new IllegalStateException("At most 1024 lights are supported");
        lights.add(light);
        return this;
    }

    /**
     * Removes the first matching borrowed light without disposing it. The next
     * prepare detects changed membership and updates buffers.
     *
     * @param light entry to remove
     * @return true if an entry was removed
     * @throws IllegalStateException if disposed or used from the wrong thread
     */
    public boolean removeLight(PointLight3D light) {
        check();
        return lights.remove(light);
    }

    /**
     * Clears light membership without changing the environment or disposing light
     * objects. A subsequent prepare uploads the empty grid/light-count state.
     *
     * @throws IllegalStateException if disposed or used from the wrong thread
     */
    public void clearLights() {
        check();
        lights.clear();
    }

    /**
     * Returns an unmodifiable live membership view. Contained light objects remain
     * mutable, and later updates read their current values. No owner check occurs here.
     *
     * @return live borrowed lights in registration order
     */
    public List<PointLight3D> getLights() {return Collections.unmodifiableList(lights);}

    /**
     * Exposes the live CPU grid for diagnostics and culling configuration. Coordinate
     * direct mutations with the normal prepare lifecycle on the owning thread.
     *
     * @return owned mutable grid
     */
    public LightGrid3D getGrid() {return grid;}

    /**
     * Reads the count of completed light/grid upload pairs, excluding cached prepares.
     *
     * @return cumulative upload count
     */
    public long getUploadCount() {return uploads;}

    /**
     * Reads the positive exposure multiplier supplied at the next bind.
     *
     * @return configured exposure, initially one
     */
    public float getExposure() {return exposure;}

    /**
     * Validates and stores exposure without rebuilding light buffers. Bind forwards
     * the latest value independently of the grid upload cache.
     *
     * @param exposure positive finite multiplier
     * @return this lighting system
     * @throws IllegalArgumentException if exposure is nonpositive or non-finite
     * @throws IllegalStateException if disposed or used from the wrong thread
     */
    public Lighting3D setExposure(float exposure) {
        check();
        if (!Float.isFinite(exposure) || exposure <= 0)
            throw new IllegalArgumentException("Exposure must be positive and finite");
        this.exposure = exposure;
        return this;
    }

    /**
     * Validates both linear RGB colors, then copies them into owned sky/ground state.
     * Z-up normals select their blend during shading; alpha does not affect shading.
     * The input color objects are not retained.
     *
     * @param sky non-null upper-hemisphere diffuse color
     * @param ground non-null lower-hemisphere diffuse color
     * @return this lighting system
     * @throws NullPointerException if either color is null
     * @throws IllegalArgumentException if RGB components are negative or their sum is non-finite
     */
    public Lighting3D setEnvironment(Color sky, Color ground) {
        check();
        validate(sky);
        validate(ground);
        this.sky.set(sky);
        this.ground.set(ground);
        return this;
    }

    /**
     * Selects normal tiled culling or a diagnostic mode testing every frustum-visible
     * light in each fragment. The next prepare rebuilds/upload data if the mode changed.
     *
     * @param enabled true for tiled culling
     * @return this lighting system
     */
    public Lighting3D setTiledCullingEnabled(boolean enabled) {
        check();
        grid.setTiled(enabled);
        return this;
    }

    /**
     * Reads the current viewport, selects tiles starting at 64 pixels and enlarging
     * them to fit device texture-buffer capacity, then updates/uploads changed grid
     * data. Zero-sized viewports return without an upload. Camera matrices/frustum
     * must already be rebuilt. Saves and restores the affected texture-buffer binding,
     * unit-three/four texture bindings, and active texture selector around uploads.
     *
     * @param camera current scene camera
     * @throws IllegalStateException if disposed or used from the wrong thread
     * @throws IllegalArgumentException if the grid rejects light or viewport data
     */
    public void prepare(Camera3D camera) {
        check();
        glGetIntegerv(GL_VIEWPORT, viewport);
        int width = viewport[2], height = viewport[3];
        if (width <= 0 || height <= 0) return;
        int tile = 64;
        while ((long) ((width + tile - 1) / tile) * ((height + tile - 1) / tile) * LightGrid3D.STRIDE > maxTexels)
            tile *= 2;
        if (!grid.update(camera, lights, width, height, tile)) return;
        int binding = glGetInteger(GL_TEXTURE_BUFFER), active = glGetInteger(GL_ACTIVE_TEXTURE);
        glActiveTexture(GL_TEXTURE3);
        int oldLight = glGetInteger(GL_TEXTURE_BINDING_BUFFER);
        glActiveTexture(GL_TEXTURE4);
        int oldGrid = glGetInteger(GL_TEXTURE_BINDING_BUFFER);
        try {
            lightData.clear();
            lightData.put(grid.lights, 0, Math.max(8, grid.getActiveLightCount() * 8)).flip();
            glBindBuffer(GL_TEXTURE_BUFFER, lightBuffer);
            glBufferData(GL_TEXTURE_BUFFER, lightData, GL_STREAM_DRAW);
            glActiveTexture(GL_TEXTURE3);
            glBindTexture(GL_TEXTURE_BUFFER, lightTexture);
            glTexBuffer(GL_TEXTURE_BUFFER, GL_RGBA32F, lightBuffer);
            if (gridData == null || gridData.capacity() < grid.cells.length)
                gridData = BufferUtils.createIntBuffer(grid.cells.length);
            gridData.clear();
            gridData.put(grid.cells).flip();
            glBindBuffer(GL_TEXTURE_BUFFER, gridBuffer);
            glBufferData(GL_TEXTURE_BUFFER, gridData, GL_STREAM_DRAW);
            glActiveTexture(GL_TEXTURE4);
            glBindTexture(GL_TEXTURE_BUFFER, gridTexture);
            glTexBuffer(GL_TEXTURE_BUFFER, GL_R32I, gridBuffer);
            uploads++;
        } finally {
            glBindBuffer(GL_TEXTURE_BUFFER, binding);
            glActiveTexture(GL_TEXTURE3);
            glBindTexture(GL_TEXTURE_BUFFER, oldLight);
            glActiveTexture(GL_TEXTURE4);
            glBindTexture(GL_TEXTURE_BUFFER, oldGrid);
            glActiveTexture(active);
        }
    }

    /**
     * Binds light/grid texture buffers on units three/four and writes the compatible
     * shader's lighting, viewport, environment, and exposure uniforms. The shader
     * must already be bound. Leaves unit four active; the caller restores bindings.
     * At least one successful prepare upload is required.
     *
     * @param shader bound shader implementing the tiled-lighting uniform contract
     * @throws IllegalStateException if unprepared, disposed, or used from the wrong thread
     */
    public void bind(Shader shader) {
        check();
        if (uploads == 0) throw new IllegalStateException("Prepare lighting with an active viewport before drawing");
        glActiveTexture(GL_TEXTURE3);
        glBindTexture(GL_TEXTURE_BUFFER, lightTexture);
        glActiveTexture(GL_TEXTURE4);
        glBindTexture(GL_TEXTURE_BUFFER, gridTexture);
        shader.setUniform1i("u_lights", 3);
        shader.setUniform1i("u_grid", 4);
        shader.setUniform1i("u_lightCount", grid.getActiveLightCount());
        shader.setUniform1i("u_tileColumns", grid.getColumns());
        shader.setUniform1i("u_tileRows", grid.getRows());
        shader.setUniform1i("u_tileSize", grid.getTileSize());
        shader.setUniform2f("u_viewportOrigin", viewport[0], viewport[1]);
        shader.setUniform3f("u_sky", sky.r(), sky.g(), sky.b());
        shader.setUniform3f("u_ground", ground.r(), ground.g(), ground.b());
        shader.setUniform1f("u_exposure", exposure);
    }

    /**
     * Deletes the two owned textures and buffers once. Borrowed lights are not
     * disposed, and diagnostic Java state remains readable. Requires the creating
     * thread before first disposal; subsequent calls return immediately.
     *
     * @throws IllegalStateException if first disposal is attempted on the wrong thread
     */
    @Override
    public void close() {
        if (disposed) return;
        check();
        glDeleteTextures(lightTexture);
        glDeleteTextures(gridTexture);
        glDeleteBuffers(lightBuffer);
        glDeleteBuffers(gridBuffer);
        disposed = true;
    }
}
