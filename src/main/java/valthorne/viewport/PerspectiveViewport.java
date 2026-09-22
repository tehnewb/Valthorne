package valthorne.viewport;

import valthorne.Window;
import valthorne.camera.Camera3D;
import valthorne.camera.PerspectiveCamera;
import valthorne.graphics.DrawFunction;
import valthorne.graphics.model.RenderStateSnapshot3D;
import org.joml.Matrix4f;
import org.joml.primitives.Rayf;
import org.joml.Vector3f;

import static org.lwjgl.opengl.GL11.GL_VIEWPORT;
import static org.lwjgl.opengl.GL11.glGetIntegerv;
import static org.lwjgl.opengl.GL11.glViewport;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;

/**
 * Viewport that binds a 3D camera's combined projection-view matrix into the engine.
 *
 * <p>While this viewport is bound, existing 2D shader-based renderers continue to work.
 * Their vertices are rendered on the world XY plane at {@code z = 0}, which makes it
 * easy to mix sprites, shapes, and other 2D content into a 3D camera setup.</p>
 *
 * <pre>{@code
 * PerspectiveViewport viewport = new PerspectiveViewport(800, 600);
 * viewport.renderWithOverlay(() -> {
 *     // Draw world geometry with the viewport's camera.
 * }, () -> {
 *     // Draw UI in bottom-left-origin overlay coordinates.
 * });
 * }</pre>
 *
 * <p>Viewport bounds use OpenGL bottom-left-origin pixel coordinates. Overlay
 * dimensions are independent logical units, initially matching the constructor
 * dimensions. Screen conversion methods use the camera's current matrices and
 * reuse internal result objects; copy results that must survive another call.</p>
 *
 * <p>Binding saves the previous viewport rectangle and engine projection only.
 * Overlay entry additionally captures the state covered by RenderStateSnapshot3D.
 * Rendering requires a current OpenGL context on the owning thread. Flush pending
 * batches before switching projection modes, and close nested scopes in reverse
 * order. The camera is borrowed and mutable; this viewport does not own its lifetime.</p>
 *
 * @author Albert Beaupre
 */
public final class PerspectiveViewport {
    private final float[] matrixUpload = new float[16]; // Reusable 16-float buffer for uploading the projection matrix.


    private final int[] oldViewport = new int[4]; // Viewport rectangle saved at bind time.
    private final float[] oldProjectionMatrix = new float[16]; // Engine projection saved for unbind.
    private final float[] worldProjectionMatrix = new float[16]; // Projection saved before entering the overlay.
    private final Matrix4f overlayProjection = new Matrix4f(); // Reused orthographic overlay matrix.
    private final Rayf screenRay = new Rayf(); // Borrowed result overwritten by successful screen-ray queries.
    private final Vector3f screenPoint = new Vector3f(); // Shared result for projection and unprojection.

    private Camera3D camera; // Borrowed camera rebuilt for the configured pixel dimensions.
    private int x; // Left viewport edge in OpenGL pixels.
    private int y; // Bottom viewport edge in OpenGL pixels.
    private int width; // Positive viewport width in pixels.
    private int height; // Positive viewport height in pixels.
    private float overlayWidth; // Logical horizontal overlay extent.
    private float overlayHeight; // Logical vertical overlay extent.
    private boolean bound; // Whether this instance has an outstanding saved viewport scope.
    private boolean overlayActive; // Whether overlay projection and render state are currently installed.
    private boolean automaticOverlaySize = true; // Whether update follows screen dimensions for overlay sizing.
    private RenderStateSnapshot3D overlayState; // State captured for the active overlay, otherwise null.

    /**
     * Creates an origin-aligned viewport with a new default perspective camera.
     * Overlay dimensions initially match the viewport; camera matrices are rebuilt.
     *
     * @param width  the positive pixel width
     * @param height the positive pixel height
     * @throws IllegalArgumentException if either dimension is not positive
     */
    public PerspectiveViewport(int width, int height) {
        this(width, height, new PerspectiveCamera());
    }

    /**
     * Creates an origin-aligned viewport retaining the supplied camera and rebuilding
     * its matrices for these dimensions. Construction does not bind OpenGL state.
     *
     * @param width  the positive pixel width
     * @param height the positive pixel height
     * @param camera the nonnull borrowed camera
     * @throws NullPointerException     if camera is null
     * @throws IllegalArgumentException if either dimension is not positive
     */
    public PerspectiveViewport(int width, int height, Camera3D camera) {
        if (camera == null) throw new NullPointerException("camera");
        setBounds(0, 0, width, height);
        this.camera = camera;
        this.overlayWidth = width;
        this.overlayHeight = height;
        camera.rebuild(width, height);
    }

    /**
     * Resets bounds to the origin and rebuilds the camera for a new screen size.
     * Overlay dimensions follow this size until setOverlaySize has been called.
     * This configures future rendering without issuing glViewport.
     *
     * @param screenWidth  the positive screen width in pixels
     * @param screenHeight the positive screen height in pixels
     * @throws IllegalArgumentException if either dimension is not positive
     */
    public void update(int screenWidth, int screenHeight) {
        setBounds(0, 0, screenWidth, screenHeight);
        if (automaticOverlaySize) {
            overlayWidth = screenWidth;
            overlayHeight = screenHeight;
        }
    }

    /**
     * Stores pixel bounds and rebuilds an attached camera. Overlay dimensions are
     * unchanged, even in automatic mode; update is the resize operation that also
     * adjusts them. The new rectangle is applied to OpenGL by apply or bind.
     *
     * @param x      the left edge in bottom-left-origin pixels
     * @param y      the bottom edge in bottom-left-origin pixels
     * @param width  the positive pixel width
     * @param height the positive pixel height
     * @throws IllegalArgumentException if width or height is not positive
     */
    public void setBounds(int x, int y, int width, int height) {
        if (width <= 0) throw new IllegalArgumentException("width must be > 0");
        if (height <= 0) throw new IllegalArgumentException("height must be > 0");
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        if (camera != null) camera.rebuild(width, height);
    }

    /**
     * Sets logical overlay extents and disables automatic sizing on later updates.
     * The next overlay entry builds the new projection. Validation rejects values
     * at or below zero but does not explicitly reject NaN or positive infinity;
     * callers should supply finite positive dimensions.
     *
     * @param overlayWidth  the logical horizontal extent
     * @param overlayHeight the logical vertical extent
     * @throws IllegalArgumentException if either extent compares at or below zero
     */
    public void setOverlaySize(float overlayWidth, float overlayHeight) {
        if (overlayWidth <= 0f) throw new IllegalArgumentException("overlayWidth must be > 0");
        if (overlayHeight <= 0f) throw new IllegalArgumentException("overlayHeight must be > 0");
        this.overlayWidth = overlayWidth;
        this.overlayHeight = overlayHeight;
        automaticOverlaySize = false;
    }

    /**
     * Installs the viewport rectangle, rebuilds camera matrices, and publishes
     * the camera's combined matrix as the engine projection. This saves no prior
     * state and does not mark the viewport bound. Requires a current GL context.
     */
    public void apply() {
        glViewport(x, y, width, height);
        camera.rebuild(width, height);
        Window.setProjectionMatrix(camera.getCombined().get(matrixUpload));
    }

    /**
     * Saves the current GL viewport and engine projection, then applies this
     * viewport. Pair a successful bind with unbind on the same rendering thread.
     * The same instance cannot be nested within itself.
     *
     * @throws IllegalStateException if this viewport is already bound
     */
    public void bind() {
        if (bound) throw new IllegalStateException("PerspectiveViewport is already bound");
        glGetIntegerv(GL_VIEWPORT, oldViewport);
        Window.copyProjectionMatrix(oldProjectionMatrix);
        apply();
        bound = true;
    }

    /**
     * Ends an active overlay, restores the projection and rectangle saved by bind,
     * and clears the bound flag. Calling while unbound does nothing. World drawing
     * state beyond those saved values is not restored by the viewport scope.
     */
    public void unbind() {
        if (!bound) return;
        if (overlayActive) {
            endOverlay2D();
        }
        Window.setProjectionMatrix(oldProjectionMatrix);
        glViewport(oldViewport[0], oldViewport[1], oldViewport[2], oldViewport[3]);
        bound = false;
    }

    /**
     * Runs a drawing callback inside a bind/unbind scope. After a successful bind,
     * unbind runs even when drawing throws; the callback's failure propagates.
     *
     * @param function the nonnull world drawing callback
     * @throws NullPointerException  if function is null
     * @throws IllegalStateException if this viewport is already bound
     */
    public void render(DrawFunction function) {
        if (function == null) throw new NullPointerException("function");
        bind();
        try {
            function.draw();
        } finally {
            unbind();
        }
    }

    /**
     * Draws world content followed by an orthographic overlay in nested restoration
     * scopes. If world drawing fails, the overlay is skipped. Successful overlay
     * entry is paired with exit even on a callback failure, followed by unbind.
     *
     * @param worldFunction   the nonnull world drawing callback
     * @param overlayFunction the nonnull overlay drawing callback
     * @throws NullPointerException  if either callback is null
     * @throws IllegalStateException if the viewport is already bound or overlay entry is invalid
     */
    public void renderWithOverlay(DrawFunction worldFunction, DrawFunction overlayFunction) {
        if (worldFunction == null) throw new NullPointerException("worldFunction");
        if (overlayFunction == null) throw new NullPointerException("overlayFunction");

        bind();
        try {
            worldFunction.draw();
            beginOverlay2D();
            try {
                overlayFunction.draw();
            } finally {
                endOverlay2D();
            }
        } finally {
            unbind();
        }
    }

    /**
     * Captures world projection and selected render state, then installs a
     * bottom-left-origin orthographic projection spanning the overlay dimensions.
     * Depth testing, depth writes and culling are disabled; standard source-alpha
     * blending with additive blend equations is enabled. The viewport rectangle
     * stays unchanged. Finish outstanding batches before this transition.
     *
     * @throws IllegalStateException if unbound or an overlay is already active
     */
    public void beginOverlay2D() {
        if (!bound) throw new IllegalStateException("PerspectiveViewport must be bound before beginning an overlay");
        if (overlayActive) throw new IllegalStateException("2D overlay is already active");
        Window.copyProjectionMatrix(worldProjectionMatrix);
        overlayState = new RenderStateSnapshot3D();
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(false);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL20.glBlendEquationSeparate(GL14.GL_FUNC_ADD, GL14.GL_FUNC_ADD);
        overlayProjection.setOrtho(0f, overlayWidth, 0f, overlayHeight, -1f, 1f);
        Window.setProjectionMatrix(overlayProjection.get(matrixUpload));
        overlayActive = true;
    }

    /**
     * Restores projection and the render-state snapshot captured at overlay entry,
     * then releases the snapshot reference. An inactive overlay is a no-op. Finish
     * overlay batches before leaving so they use the intended projection and state.
     */
    public void endOverlay2D() {
        if (!overlayActive) return;
        Window.setProjectionMatrix(worldProjectionMatrix);
        overlayState.close();
        overlayState = null;
        overlayActive = false;
    }

    /**
     * Tests the stored rectangle with inclusive edges, without consulting camera
     * state. Coordinates use the same bottom-left-origin pixels as the GL viewport.
     *
     * @param screenX the horizontal screen coordinate
     * @param screenY the vertical screen coordinate
     * @return whether both coordinates lie inside or on the rectangle edges
     */
    public boolean containsScreenPoint(float screenX, float screenY) {
        return screenX >= x && screenX <= x + width && screenY >= y && screenY <= y + height;
    }

    /**
     * Creates a picking ray from the current camera matrices for an in-bounds
     * screen point. The ray starts on the near plane and has a normalized direction
     * toward the far plane. This does not rebuild a camera modified since the last
     * rebuild; the returned ray is reused by the next successful call.
     *
     * @param screenX the bottom-left-origin screen X coordinate
     * @param screenY the bottom-left-origin screen Y coordinate
     * @return the borrowed ray, or null outside the rectangle
     */
    public Rayf screenToRay(float screenX, float screenY) {
        if (!containsScreenPoint(screenX, screenY)) {
            return null;
        }
        return camera.screenPointToRay(screenX, screenY, x, y, width, height, screenRay);
    }

    /**
     * Projects a world point using current camera matrices without clipping or
     * rebuilding them. Result X/Y are bottom-left-origin screen pixels and Z is
     * normalized depth, with near/far planes at zero/one. Off-screen results are
     * permitted. The vector is shared with unproject and overwritten on later calls.
     *
     * @param world the nonnull world-space point
     * @return the borrowed screen-position and depth vector
     * @throws NullPointerException if world is null
     */
    public Vector3f project(Vector3f world) {
        if (world == null) throw new NullPointerException("world");
        return camera.project(world, x, y, width, height, screenPoint);
    }

    /**
     * Converts an in-bounds screen point and normalized depth through the current
     * inverse camera matrix. Depth zero/one represents the near/far plane; depth
     * is not clamped or validated here. The result shares storage with project.
     *
     * @param screenX the bottom-left-origin screen X coordinate
     * @param screenY the bottom-left-origin screen Y coordinate
     * @param depth   the normalized depth to unproject
     * @return the borrowed world point, or null outside the rectangle
     */
    public Vector3f unproject(float screenX, float screenY, float depth) {
        if (!containsScreenPoint(screenX, screenY)) {
            return null;
        }
        return camera.unproject(screenX, screenY, depth, x, y, width, height, screenPoint);
    }

    /**
     * Returns the retained camera directly. After mutating it, rebuild its matrices
     * before coordinate queries, or use apply/bind to rebuild for this viewport.
     *
     * @return the live nonnull camera
     */
    public Camera3D getCamera() {
        return camera;
    }

    /**
     * Replaces the borrowed camera and rebuilds it for current pixel dimensions.
     * This does not install its projection in Window until apply or bind is used.
     *
     * @param camera the nonnull replacement camera
     * @throws NullPointerException if camera is null
     */
    public void setCamera(Camera3D camera) {
        if (camera == null) throw new NullPointerException("camera");
        this.camera = camera;
        camera.rebuild(width, height);
    }

    /**
     * Reads the configured left edge; pending bounds need not yet be applied to GL.
     *
     * @return the left edge in screen pixels
     */
    public int getX() {
        return x;
    }

    /**
     * Reads the configured bottom edge in OpenGL's screen-coordinate convention.
     *
     * @return the bottom edge in screen pixels
     */
    public int getY() {
        return y;
    }

    /**
     * Reads the pixel width used to rebuild the camera, independent of overlay units.
     *
     * @return the positive configured viewport width
     */
    public int getWidth() {
        return width;
    }

    /**
     * Reads the pixel height used to rebuild the camera, independent of overlay units.
     *
     * @return the positive configured viewport height
     */
    public int getHeight() {
        return height;
    }

    /**
     * Reads the logical horizontal extent used on the next overlay entry.
     * It can differ from the viewport's pixel width after explicit sizing.
     *
     * @return the configured overlay width in logical units
     */
    public float getOverlayWidth() {
        return overlayWidth;
    }

    /**
     * Reads the logical vertical extent used on the next overlay entry.
     * It can differ from the viewport's pixel height after explicit sizing.
     *
     * @return the configured overlay height in logical units
     */
    public float getOverlayHeight() {
        return overlayHeight;
    }
}
