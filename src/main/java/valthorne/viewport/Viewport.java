package valthorne.viewport;

import valthorne.camera.Camera2D;
import valthorne.math.Matrix4f;
import valthorne.math.Vector2f;

import static org.lwjgl.opengl.GL11.*;

/**
 * The {@code Viewport} class defines a rendering region inside the OpenGL window.
 * A viewport maps a portion of the screen to a logical coordinate system
 * defined by a world width and height. It also provides projection-management
 * and scoped rendering behavior.
 *
 * <p>This class is abstract—different viewport types may compute layout
 * differently (letterboxing, stretching, pixel-perfect scaling, UI-only regions, etc.).
 * Subclasses implement {@link #update(int, int)} to determine how the viewport's
 * screen-area and world-area relate.</p>
 *
 * <h2>Primary Responsibilities</h2>
 * <ul>
 *     <li>Define a region of the screen where rendering occurs.</li>
 *     <li>Apply world-to-screen projection transforms using either:
 *         <ul>
 *             <li>a user-supplied {@link Camera2D}, or</li>
 *             <li>a built-in {@link Matrix4f} projection matrix.</li>
 *         </ul>
 *     </li>
 *     <li>Preserve and restore OpenGL state during isolated rendering passes.</li>
 *     <li>Allow rendering tasks to be safely executed via {@link #render(Runnable)}.</li>
 * </ul>
 *
 * <h2>Coordinate Definitions</h2>
 * <ul>
 *     <li>{@code x, y} — position of the viewport on the screen</li>
 *     <li>{@code width, height} — the pixel dimensions of the viewport</li>
 *     <li>{@code worldWidth, worldHeight} — logical world units visible inside the viewport</li>
 * </ul>
 *
 * <h2>How Projection Works</h2>
 * <p>If a {@link Camera2D} is assigned:
 * <ul>
 *     <li>Its {@code rebuild()} method is called.</li>
 *     <li>The camera produces a projection matrix.</li>
 *     <li>The matrix is loaded into the OpenGL projection stack.</li>
 * </ul>
 * <p>
 * If no camera is set:
 * <ul>
 *     <li>The internal {@link #projectionMatrix} is used instead.</li>
 * </ul>
 * </p>
 *
 * <h2>Render Isolation</h2>
 * <p>{@link #render(Runnable)} encapsulates drawing code with:
 * <ul>
 *     <li>Viewport push/pop</li>
 *     <li>Projection matrix push/pop</li>
 *     <li>ModelView matrix push/pop</li>
 * </ul>
 * ensuring the viewport does not affect rendering outside its scope.</p>
 *
 * <h2>Subclass Requirements</h2>
 * <p>A subclass <strong>must</strong> compute:</p>
 * <ul>
 *     <li>{@code screenX}</li>
 *     <li>{@code screenY}</li>
 *     <li>{@code screenWidth}</li>
 *     <li>{@code screenHeight}</li>
 * </ul>
 * based on some strategy, such as:
 * <ul>
 *     <li>Scaling while preserving aspect ratio</li>
 *     <li>Stretching</li>
 *     <li>Letterboxing / pillarboxing</li>
 *     <li>Aligning UI viewports</li>
 * </ul>
 *
 * @author Albert Beaupre
 * @since December 1st, 2025
 */
public abstract class Viewport {

    /**
     * The fallback projection matrix used when no camera is assigned.
     * Subclasses may populate this matrix in {@link #update(int, int)}.
     */
    protected final Matrix4f projectionMatrix = new Matrix4f();
    /**
     * A buffer storing the previous OpenGL viewport values so they can be restored
     * after {@link #render(Runnable)}.
     */
    private final int[] oldViewport = new int[4];
    /**
     * X position of this viewport in screen pixels.
     */
    protected int x;
    /**
     * Y position of this viewport in screen pixels.
     */
    protected int y;
    /**
     * Width of this viewport in screen pixels.
     */
    protected int width;
    /**
     * Height of this viewport in screen pixels.
     */
    protected int height;
    /**
     * The logical world width visible inside this viewport.
     */
    protected float worldWidth;
    /**
     * The logical world height visible inside this viewport.
     */
    protected float worldHeight;
    /**
     * Optional camera used to compute a projection transform for rendering.
     * If {@code null}, {@link #projectionMatrix} is used instead.
     */
    protected Camera2D camera;

    /**
     * Creates a viewport using the specified logical world size.
     *
     * @param worldWidth  the logical width of the viewport
     * @param worldHeight the logical height of the viewport
     */
    public Viewport(float worldWidth, float worldHeight) {
        this.worldWidth = worldWidth;
        this.worldHeight = worldHeight;
    }

    /**
     * Must be implemented by subclasses to compute the viewport's screen region
     * ({@code screenX, screenY, screenWidth, screenHeight}) based on available
     * window dimensions.
     *
     * <p>This method is typically called whenever the window is resized or when
     * the viewport layout strategy changes.</p>
     *
     * @param screenWidth  the actual window width in pixels
     * @param screenHeight the actual window height in pixels
     */
    public abstract void update(int screenWidth, int screenHeight);

    /**
     * Applies this viewport's transform to OpenGL. This includes:
     * <ul>
     *     <li>Setting the OpenGL viewport rectangle</li>
     *     <li>Loading the projection matrix (via camera or internal)</li>
     *     <li>Resetting the model-view matrix</li>
     * </ul>
     *
     * <p>Called automatically inside {@link #render(Runnable)}, but may also be
     * invoked manually for custom rendering flows.</p>
     */
    public void apply() {
        glViewport(x, y, width, height);

        float[] matrixData;

        if (camera != null) {
            camera.rebuild(worldWidth, worldHeight);
            matrixData = camera.getProjection().get();
        } else {
            matrixData = projectionMatrix.get();
        }

        glMatrixMode(GL_PROJECTION);
        glLoadMatrixf(matrixData);

        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();
    }

    /**
     * Executes a rendering task inside this viewport. All OpenGL state affected
     * by the viewport or projection is safely restored once rendering completes.
     *
     * <p>This method performs the following steps:</p>
     * <ol>
     *     <li>Save current OpenGL viewport</li>
     *     <li>Push projection and model-view matrices</li>
     *     <li>Apply this viewport's projection</li>
     *     <li>Run the provided action</li>
     *     <li>Restore all matrices</li>
     *     <li>Restore previous viewport</li>
     * </ol>
     *
     * <p>This allows multiple viewports (UI, game world, minimaps, etc.)
     * to exist in the same frame without interfering with each other.</p>
     *
     * @param action a function containing draw calls to execute inside this viewport
     */
    public void render(Runnable action) {
        glGetIntegerv(GL_VIEWPORT, oldViewport);

        glMatrixMode(GL_PROJECTION);
        glPushMatrix();

        glMatrixMode(GL_MODELVIEW);
        glPushMatrix();

        applyScissor(0, 0, worldWidth, worldHeight, () -> {
            apply();
            action.run();
        });

        glMatrixMode(GL_MODELVIEW);
        glPopMatrix();

        glMatrixMode(GL_PROJECTION);
        glPopMatrix();

        glViewport(oldViewport[0], oldViewport[1], oldViewport[2], oldViewport[3]);
    }

    /**
     * Converts screen coordinates to world coordinates based on the viewport's
     * configuration, including optional camera adjustments.
     *
     * @param screenX the x-coordinate in screen space
     * @param screenY the y-coordinate in screen space
     * @return a {@code Vector2f} representing the corresponding world coordinates,
     * or {@code null} if the screen coordinates lie outside the viewport
     */
    public Vector2f screenToWorld(float screenX, float screenY) {
        if (screenX < x || screenY < y || screenX > x + width || screenY > y + height)
            return null;

        float vx = screenX - x;
        float vy = screenY - y;

        float wx = vx * (worldWidth / width);
        float wy = vy * (worldHeight / height);

        if (camera != null) {
            float halfW = worldWidth * 0.5f;
            float halfH = worldHeight * 0.5f;

            wx = (wx - halfW) / camera.getZoom() + camera.getCenter().getX();
            wy = (wy - halfH) / camera.getZoom() + camera.getCenter().getY();
        }

        return new Vector2f(wx, wy);
    }

    /**
     * Applies an OpenGL scissor rectangle using world coordinates.
     * The rectangle is converted into framebuffer (screen) space
     * so it works correctly with all viewport types.
     *
     * @param wx world-space x
     * @param wy world-space y
     * @param ww world-space width
     * @param wh world-space height
     */
    public void applyScissor(float wx, float wy, float ww, float wh, Runnable action) {
        float nx = wx / worldWidth;
        float ny = wy / worldHeight;
        float nw = ww / worldWidth;
        float nh = wh / worldHeight;

        int sx = (int) (x + nx * width);
        int sy = (int) (y + ny * height);
        int sw = (int) (nw * width);
        int sh = (int) (nh * height);

        if (sx < x) {
            sw -= (x - sx);
            sx = x;
        }
        if (sy < y) {
            sh -= (y - sy);
            sy = y;
        }
        if (sx + sw > x + width)
            sw = x + width - sx;
        if (sy + sh > y + height)
            sh = y + height - sy;

        if (sw <= 0 || sh <= 0)
            return;

        glEnable(GL_SCISSOR_TEST);
        glScissor(sx, sy, sw, sh);
        action.run();
        glDisable(GL_SCISSOR_TEST);
    }

    /**
     * Sets the logical world size for this viewport.
     *
     * @param worldWidth  the new logical width of the world
     * @param worldHeight the new logical height of the world
     */
    public void setWorldSize(float worldWidth, float worldHeight) {
        this.worldWidth = worldWidth;
        this.worldHeight = worldHeight;
    }

    /**
     * Sets the dimensions of the viewport in pixels.
     *
     * @param width  the new width of the viewport in pixels
     * @param height the new height of the viewport in pixels
     */
    public void setSize(int width, int height) {
        this.width = width;
        this.height = height;
    }

    /**
     * Updates the screen position of this viewport.
     *
     * @param x the x-coordinate of the new screen position
     * @param y the y-coordinate of the new screen position
     */
    public void setPosition(int x, int y) {
        this.x = x;
        this.y = y;
    }

    /**
     * Retrieves the x-coordinate of the screen position for this viewport.
     *
     * @return the x-coordinate of the screen position
     */
    public int getX() {
        return x;
    }

    /**
     * Sets the x-coordinate of the screen position for this viewport.
     *
     * @param x the x-coordinate of the screen position
     */
    public void setX(int x) {
        this.x = x;
    }

    /**
     * Retrieves the y-coordinate of the screen position for this viewport.
     *
     * @return the y-coordinate of the screen position
     */
    public int getY() {
        return y;
    }

    /**
     * Sets the y-coordinate of the screen position for this viewport.
     *
     * @param y the y-coordinate of the screen position
     */
    public void setY(int y) {
        this.y = y;
    }

    /**
     * Retrieves the width of the viewport in pixels.
     *
     * @return the width of the viewport
     */
    public int getWidth() {
        return width;
    }

    /**
     * Retrieves the height of the viewport in pixels.
     *
     * @return the height of the viewport
     */
    public int getHeight() {
        return height;
    }

    /**
     * @return the logical world width of this viewport
     */
    public float getWorldWidth() {
        return worldWidth;
    }

    /**
     * @return the logical world height of this viewport
     */
    public float getWorldHeight() {
        return worldHeight;
    }

    /**
     * @return the currently assigned camera, or {@code null} if none
     */
    public Camera2D getCamera() {
        return camera;
    }

    /**
     * Assigns a camera to this viewport. When applied, the camera will be used
     * to compute the projection matrix instead of the internal fallback matrix.
     *
     * @param camera the camera to use for rendering (may be null)
     */
    public void setCamera(Camera2D camera) {
        this.camera = camera;
    }
}
