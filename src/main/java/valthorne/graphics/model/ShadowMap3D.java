package valthorne.graphics.model;

import org.joml.Matrix4f;
import valthorne.camera.OrthographicCamera3D;

import static org.lwjgl.opengl.GL33.*;
import java.util.Arrays;
import java.util.Objects;

/**
 * Owns a directional-light depth map and the batch used to render opaque and
 * alpha-cutout scene geometry into it. A square orthographic camera defines the
 * light's coverage; configure that camera to include every relevant caster and
 * receiver before rendering. The receiving mesh shader samples the depth map
 * with comparison filtering and a 3-by-3 PCF kernel.
 * <p>
 * Construction, rendering, and disposal require a current compatible OpenGL
 * context. The scene and returned camera are borrowed; this object owns its
 * depth texture, framebuffer, comparison sampler, and batch. A successful depth
 * pass makes the matrix and texture ready for the main pass. Cached rendering
 * requires the caller to advance a caster revision whenever geometry, pose,
 * visibility, or alpha coverage changes.
 * </p>
 * <pre>{@code
 * try (ShadowMap3D shadows = new ShadowMap3D(2048)) {
 *     shadows.getCamera().setWorldHeight(40);
 *     shadows.renderIfChanged(scene, casterRevision);
 *     // Supply this shadow map to the main pass's MeshRenderState3D.
 * }
 * }</pre>
 *
 * @author Albert Beaupre
 */
public final class ShadowMap3D implements AutoCloseable {
    private final float[] matrixUpload = new float[16]; // Reusable column-major camera matrix used for cache comparisons and copies.

    private final int resolution, framebuffer, texture, comparisonSampler; // Square map size and owned OpenGL framebuffer, depth texture, and comparison sampler names.
    private final OrthographicCamera3D camera = new OrthographicCamera3D(); // Mutable light camera defining shadow coverage.
    private final Matrix4f matrix = new Matrix4f(); // Combined camera matrix captured by the last successful depth pass.
    private final ModelBatch3D batch; // Owned batch used exclusively for caster rendering.
    private final MeshRenderState3D state = new MeshRenderState3D().setCamera(camera).setShadowPass(true); // Submission state selecting the light camera and shadow shader path.
    private final float[] cachedCamera = new float[16]; // Combined matrix associated with the last revision-cached render.
    private float bias = .001f, strength = .85f; // Receiver depth bias and shadow darkening strength.
    private float softness = 2f; // PCF sampling radius measured in depth-texture texels.
    private boolean disposed, rendered; // Resource disposal flag and validity of the last depth image.
    private Scene3D cachedScene; // Scene identity used by the optional revision cache.
    private long cachedRevision; // Caster revision associated with the cached scene and camera.
    private long renderCount; // Number of depth passes that completed successfully.

    /**
     * Allocates a 24-bit depth texture, depth-only framebuffer, comparison sampler,
     * and private model batch. Initializes a Z-up light camera at (5, -5, 10),
     * looking at the origin with a world height of 20 and clip planes 0.1 to 100.
     * Framebuffer bindings and the state covered by the render snapshot are restored.
     *
     * @param resolution width and height in pixels
     * @throws IllegalArgumentException if resolution is nonpositive or exceeds the GL texture limit
     * @throws IllegalStateException    if the depth framebuffer is incomplete
     */
    public ShadowMap3D(int resolution) {
        if (resolution <= 0 || resolution > glGetInteger(GL_MAX_TEXTURE_SIZE))
            throw new IllegalArgumentException("Invalid shadow resolution");
        this.resolution = resolution;
        int oldDraw = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING), oldRead = glGetInteger(GL_READ_FRAMEBUFFER_BINDING);
        int newFramebuffer = 0, newTexture = 0;
        ModelBatch3D newBatch = null;
        try (RenderStateSnapshot3D ignored = new RenderStateSnapshot3D()) {
            glActiveTexture(GL_TEXTURE0);
            newTexture = glGenTextures();
            glBindTexture(GL_TEXTURE_2D, newTexture);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_DEPTH_COMPONENT24, resolution, resolution, 0, GL_DEPTH_COMPONENT, GL_FLOAT, 0L);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
            newFramebuffer = glGenFramebuffers();
            glBindFramebuffer(GL_FRAMEBUFFER, newFramebuffer);
            glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, newTexture, 0);
            glDrawBuffer(GL_NONE);
            glReadBuffer(GL_NONE);
            if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE)
                throw new IllegalStateException("Incomplete shadow framebuffer");
            newBatch = new ModelBatch3D();
        } catch (RuntimeException | Error e) {
            if (newBatch != null) newBatch.dispose();
            glDeleteTextures(newTexture);
            glDeleteFramebuffers(newFramebuffer);
            throw e;
        } finally {
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, oldDraw);
            glBindFramebuffer(GL_READ_FRAMEBUFFER, oldRead);
        }
        framebuffer = newFramebuffer;
        texture = newTexture;
        batch = newBatch;
        comparisonSampler = glGenSamplers();
        glSamplerParameteri(comparisonSampler, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glSamplerParameteri(comparisonSampler, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glSamplerParameteri(comparisonSampler, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glSamplerParameteri(comparisonSampler, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glSamplerParameteri(comparisonSampler, GL_TEXTURE_COMPARE_MODE, GL_COMPARE_REF_TO_TEXTURE);
        glSamplerParameteri(comparisonSampler, GL_TEXTURE_COMPARE_FUNC, GL_LEQUAL);
        camera.setPosition(5, -5, 10);
        camera.lookAt(0, 0, 0, 0, 0, 1);
        camera.setWorldHeight(20);
        camera.setClipPlanes(.1f, 100f);
    }

    /**
     * Returns the sampling radius used by the receiving shader's PCF kernel.
     * Changing this value affects sampling rather than the stored depth image.
     *
     * @return softness in shadow-map texels; initially 2
     */
    public float getSoftness() {
        return softness;
    }

    /**
     * Sets the PCF sampling radius without invalidating the cached depth pass.
     * Zero collapses the sampling offsets; larger values spread the
     * filter over more shadow-map texels.
     *
     * @param texels finite radius from 0 through 8
     * @return this map
     * @throws IllegalArgumentException if the radius is nonfinite or outside the supported range
     */
    public ShadowMap3D setSoftness(float texels) {
        if (!Float.isFinite(texels) || texels < 0 || texels > 8)
            throw new IllegalArgumentException("Shadow softness must be in [0,8] texels");
        softness = texels;
        return this;
    }

    /**
     * Returns the owned linear-filtered, clamp-to-edge depth-comparison sampler.
     * Its comparison function is {@code GL_LEQUAL}; callers may bind it but must
     * not delete it or retain it after disposal.
     *
     * @return OpenGL sampler name
     * @throws IllegalStateException if this map has been disposed
     */
    public int getComparisonSampler() {
        if (disposed) throw new IllegalStateException("Shadow map disposed");
        return comparisonSampler;
    }

    /**
     * Returns the number of successfully completed depth passes. Cache hits and
     * failed passes do not increment this counter.
     *
     * @return cumulative successful render count
     */
    public long getRenderCount() {
        return renderCount;
    }

    /**
     * Renders only when the scene identity, caller-supplied caster revision, or
     * rebuilt light-camera matrix differs from the last cached pass. An unready map
     * always renders. The scene's internal mutations are not inspected: advance
     * the revision for every shadow-affecting change, or use {@link #render(Scene3D)}
     * for procedural geometry whose changes cannot be tracked.
     *
     * @param scene          scene whose shadow casters are drawn
     * @param casterRevision application-managed revision of caster content
     * @return true if a depth pass completed; false if the cached image was reused
     * @throws IllegalStateException if this map has been disposed
     * @throws NullPointerException  if the scene is null
     */
    public boolean renderIfChanged(Scene3D scene, long casterRevision) {
        if (disposed) throw new IllegalStateException("Shadow map disposed");
        camera.rebuild(resolution, resolution);
        if (isReady() && cachedScene == scene && cachedRevision == casterRevision && Arrays.equals(cachedCamera, camera.getCombined().get(matrixUpload)))
            return false;
        render(scene);
        cachedScene = scene;
        cachedRevision = casterRevision;
        System.arraycopy(camera.getCombined().get(matrixUpload), 0, cachedCamera, 0, 16);
        return true;
    }

    /**
     * Returns the live light camera used by subsequent shadow passes. Mutating it
     * changes coverage on the next render; cached rendering detects changes to its
     * rebuilt combined matrix.
     *
     * @return borrowed mutable light camera
     */
    public OrthographicCamera3D getCamera() {
        return camera;
    }

    /**
     * Returns the owned depth texture name. Check {@link #isReady()} before using
     * its contents; construction alone does not render a valid shadow image.
     * The caller must not delete the texture.
     *
     * @return OpenGL depth texture name
     * @throws IllegalStateException if this map has been disposed
     */
    public int getTextureId() {
        if (disposed) throw new IllegalStateException("Shadow map disposed");
        return texture;
    }

    /**
     * Copies the combined light-camera matrix captured by the last successful
     * depth pass. Later edits to the live camera do not affect this snapshot.
     * Before any successful render the stored matrix is identity.
     *
     * @return independent world-to-light-clip matrix
     */
    public Matrix4f getMatrix() {
        return new Matrix4f(matrix);
    }

    /**
     * Reports whether a successful depth pass is available and the resources remain
     * alive. Starting a new pass clears readiness until that pass succeeds.
     *
     * @return true when the depth texture and captured matrix can be sampled
     */
    public boolean isReady() {
        return rendered && !disposed;
    }

    /**
     * Returns the receiver-side depth bias used during shadow comparison.
     *
     * @return nonnegative depth bias; initially 0.001
     */
    public float getBias() {
        return bias;
    }

    /**
     * Sets the receiving shader's depth-comparison bias. This sampling adjustment
     * does not require another depth pass and is independent of the polygon offset
     * applied while rendering casters.
     *
     * @param bias finite, nonnegative bias in depth-comparison units
     * @return this map
     * @throws IllegalArgumentException if bias is negative or nonfinite
     */
    public ShadowMap3D setBias(float bias) {
        if (!Float.isFinite(bias) || bias < 0f)
            throw new IllegalArgumentException("Bias must be finite and nonnegative");
        this.bias = bias;
        return this;
    }

    /**
     * Returns the fraction by which a fully shadowed receiver is darkened.
     *
     * @return shadow strength from 0 through 1; initially 0.85
     */
    public float getStrength() {
        return strength;
    }

    /**
     * Sets receiver shadow strength without invalidating the depth image. Zero
     * disables shadow darkening; one applies the full sampled shadow contribution.
     *
     * @param strength finite shadow strength from 0 through 1
     * @return this map
     * @throws IllegalArgumentException if strength is nonfinite or outside the unit interval
     */
    public ShadowMap3D setStrength(float strength) {
        if (!Float.isFinite(strength) || strength < 0f || strength > 1f)
            throw new IllegalArgumentException("Strength must be in [0,1]");
        this.strength = strength;
        return this;
    }

    /**
     * Unconditionally clears and renders the scene into the owned depth target.
     * Rebuilds the light camera for the square target, disables scissoring, enables
     * depth writes and polygon offset, and submits the scene in shadow-pass mode.
     * On success it captures the camera matrix, marks the image ready, and increments
     * the render counter. This invalidates the revision cache even for the same scene.
     * <p>
     * Restores framebuffer bindings, viewport, clear depth, scissor enablement,
     * polygon-offset settings, and the state covered by the render snapshot,
     * including when scene rendering fails. A failed pass leaves the map unready.
     * </p>
     *
     * @param scene nonnull scene to render; ownership remains with the caller
     * @throws IllegalStateException if this map has been disposed
     * @throws NullPointerException  if scene is null
     */
    public void render(Scene3D scene) {
        if (disposed) throw new IllegalStateException("Shadow map disposed");
        Objects.requireNonNull(scene, "scene");
        cachedScene = null;
        int oldDraw = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING), oldRead = glGetInteger(GL_READ_FRAMEBUFFER_BINDING);
        int[] viewport = new int[4];
        glGetIntegerv(GL_VIEWPORT, viewport);
        double depth = glGetDouble(GL_DEPTH_CLEAR_VALUE);
        boolean scissor = glIsEnabled(GL_SCISSOR_TEST);
        boolean polygonOffset = glIsEnabled(GL_POLYGON_OFFSET_FILL);
        float offsetFactor = glGetFloat(GL_POLYGON_OFFSET_FACTOR), offsetUnits = glGetFloat(GL_POLYGON_OFFSET_UNITS);
        rendered = false;
        try (RenderStateSnapshot3D ignored = new RenderStateSnapshot3D()) {
            glBindFramebuffer(GL_FRAMEBUFFER, framebuffer);
            glViewport(0, 0, resolution, resolution);
            glDisable(GL_SCISSOR_TEST);
            glDepthMask(true);
            glClearDepth(1);
            glClear(GL_DEPTH_BUFFER_BIT);
            glEnable(GL_POLYGON_OFFSET_FILL);
            glPolygonOffset(2f, 4f);
            camera.rebuild(resolution, resolution);
            scene.render(batch, state);
            matrix.set(camera.getCombined());
            rendered = true;
            renderCount++;
        } finally {
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, oldDraw);
            glBindFramebuffer(GL_READ_FRAMEBUFFER, oldRead);
            glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
            glClearDepth(depth);
            if (scissor) glEnable(GL_SCISSOR_TEST);
            else glDisable(GL_SCISSOR_TEST);
            glPolygonOffset(offsetFactor, offsetUnits);
            if (polygonOffset) glEnable(GL_POLYGON_OFFSET_FILL);
            else glDisable(GL_POLYGON_OFFSET_FILL);
        }
    }

    /**
     * Releases the private batch and all owned OpenGL objects. Call with the
     * appropriate context current. Repeated calls after successful disposal have
     * no effect; the borrowed scene and camera are not disposed.
     */
    public void dispose() {
        if (disposed) return;
        batch.dispose();
        glDeleteFramebuffers(framebuffer);
        glDeleteTextures(texture);
        glDeleteSamplers(comparisonSampler);
        disposed = true;
    }

    /**
     * Releases this map's GPU resources by delegating to {@link #dispose()}.
     * Supports try-with-resources and is harmless after successful disposal.
     */
    @Override
    public void close() {
        dispose();
    }
}
