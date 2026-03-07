package valthorne.viewport;

import valthorne.camera.Camera2D;
import valthorne.graphics.DrawFunction;
import valthorne.math.Matrix4f;
import valthorne.math.Vector2f;

import static org.lwjgl.opengl.GL11.*;

/**
 * Base 2D viewport abstraction responsible for mapping a logical world area into a screen-space
 * rectangle, applying the proper OpenGL viewport and projection state, converting screen
 * coordinates into world coordinates, and handling viewport-aware scissor rectangles.
 *
 * <p>
 * A viewport controls two separate concepts:
 * </p>
 * <ul>
 *     <li><b>Screen region</b>: the rectangle in actual window pixels where rendering occurs</li>
 *     <li><b>World region</b>: the logical coordinate space visible inside that screen region</li>
 * </ul>
 *
 * <p>
 * Concrete subclasses decide how the screen rectangle is computed during
 * {@link #update(int, int)}. For example:
 * </p>
 * <ul>
 *     <li>{@code FitViewport} preserves aspect ratio and letterboxes when needed</li>
 *     <li>{@code FillViewport} preserves aspect ratio but fills the whole screen, even if that means cropping</li>
 *     <li>{@code StretchViewport} stretches world content to the full screen rectangle</li>
 *     <li>{@code ScreenViewport} maps the world directly to screen pixels</li>
 * </ul>
 *
 * <h2>Rendering flow</h2>
 * <p>
 * The normal render flow is:
 * </p>
 * <ol>
 *     <li>Call {@link #update(int, int)} when the window size changes</li>
 *     <li>Call {@link #bind()} before drawing content for this viewport</li>
 *     <li>Draw your world or UI</li>
 *     <li>Call {@link #unbind()} to restore the previous OpenGL viewport and matrices</li>
 * </ol>
 *
 * <p>
 * If you want a scoped one-call render, you can instead use {@link #render(DrawFunction)}.
 * </p>
 *
 * <h2>Camera behavior</h2>
 * <p>
 * When a {@link Camera2D} is assigned, this viewport delegates projection generation to that
 * camera each time {@link #apply()} is called. If no camera is assigned, the viewport uses its
 * own fallback {@link #projectionMatrix}, which subclasses usually update inside
 * {@link #update(int, int)}.
 * </p>
 *
 * <h2>Scissor behavior</h2>
 * <p>
 * The scissor methods operate in <b>world-space coordinates</b>, not raw screen-space pixel
 * coordinates. The viewport converts the provided world rectangle into screen-space pixels and
 * applies it through OpenGL's scissor test. Nested scissors are handled by intersecting the new
 * rectangle with any existing active scissor box.
 * </p>
 *
 * <h2>Coordinate system</h2>
 * <p>
 * This class assumes a bottom-left world coordinate system, matching the rest of your engine
 * and standard OpenGL-style orthographic rendering.
 * </p>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * Viewport viewport = new FitViewport(1280f, 720f);
 * viewport.update(windowWidth, windowHeight);
 *
 * viewport.bind();
 * try {
 *     batch.begin();
 *
 *     batch.draw(background, 0f, 0f, 1280f, 720f);
 *
 *     if (viewport.beginScissor(100f, 100f, 400f, 200f)) {
 *         try {
 *             batch.draw(panelTexture, 100f, 100f, 400f, 200f);
 *         } finally {
 *             viewport.endScissor();
 *         }
 *     }
 *
 *     batch.end();
 * } finally {
 *     viewport.unbind();
 * }
 *
 * Vector2f world = viewport.screenToWorld(mouseX, mouseY);
 * if (world != null) {
 *     System.out.println("Mouse in world: " + world.getX() + ", " + world.getY());
 * }
 * }</pre>
 *
 * @author Albert Beaupre
 * @since December 1st, 2025
 */
public abstract class Viewport {

    /**
     * Fallback projection matrix used whenever no camera is assigned to this viewport.
     *
     * <p>
     * Subclasses usually rebuild this matrix inside {@link #update(int, int)} so it reflects
     * the current world size and viewport strategy.
     * </p>
     */
    protected final Matrix4f projectionMatrix = new Matrix4f();

    private final int[] oldViewport = new int[4]; // Previously active OpenGL viewport restored by unbind or render.
    private final int[] previousScissor = new int[4]; // Previously active OpenGL scissor rectangle restored by endScissor.
    protected int x; // X position of this viewport in actual screen pixels.
    protected int y; // Y position of this viewport in actual screen pixels.
    protected int width; // Width of this viewport in actual screen pixels.
    protected int height; // Height of this viewport in actual screen pixels.
    protected float worldWidth; // Logical world width visible through this viewport.
    protected float worldHeight; // Logical world height visible through this viewport.
    protected Camera2D camera; // Optional camera used to build the active projection transform.
    private boolean scissorWasEnabled; // True when a scissor test was already active before beginScissor was called.
    private final Vector2f screenToWorldCoordinates = new Vector2f(); // Reused return vector for screen-to-world conversion.

    /**
     * Creates a viewport with the specified logical world size.
     *
     * <p>
     * The provided world size represents the logical coordinate space that this viewport exposes
     * to rendering and input conversion. The actual screen rectangle is not defined here and must
     * later be computed by a concrete subclass inside {@link #update(int, int)}.
     * </p>
     *
     * @param worldWidth  the logical width visible through this viewport
     * @param worldHeight the logical height visible through this viewport
     */
    public Viewport(float worldWidth, float worldHeight) {
        this.worldWidth = worldWidth;
        this.worldHeight = worldHeight;
    }

    /**
     * Recomputes this viewport's screen rectangle and fallback projection using the current
     * window dimensions.
     *
     * <p>
     * Every concrete viewport strategy must implement this method. Typical responsibilities are:
     * </p>
     * <ul>
     *     <li>Choosing the screen-space x and y position of the viewport</li>
     *     <li>Choosing the screen-space width and height of the viewport</li>
     *     <li>Updating {@link #projectionMatrix} to match the intended world-space projection</li>
     * </ul>
     *
     * @param screenWidth  the current window width in pixels
     * @param screenHeight the current window height in pixels
     */
    public abstract void update(int screenWidth, int screenHeight);

    /**
     * Applies this viewport's OpenGL viewport rectangle and projection state.
     *
     * <p>
     * This method updates the current OpenGL viewport to this viewport's screen rectangle and
     * then loads the active projection matrix. If a camera is present, the camera is rebuilt
     * using the current world size and its projection matrix is loaded. Otherwise the fallback
     * viewport projection is loaded.
     * </p>
     *
     * <p>
     * The model-view matrix is reset to identity after the projection matrix is applied.
     * </p>
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
     * Binds this viewport for scoped rendering.
     *
     * <p>
     * This method captures the currently active OpenGL viewport, pushes both the projection and
     * model-view matrices, and then applies this viewport. It is intended to be paired with
     * {@link #unbind()}.
     * </p>
     *
     * <p>
     * Use this when you want full control over when rendering begins and ends inside the viewport.
     * </p>
     */
    public void bind() {
        glGetIntegerv(GL_VIEWPORT, oldViewport);

        glMatrixMode(GL_PROJECTION);
        glPushMatrix();

        glMatrixMode(GL_MODELVIEW);
        glPushMatrix();

        apply();
    }

    /**
     * Restores the OpenGL state captured by {@link #bind()}.
     *
     * <p>
     * This method pops the model-view matrix, pops the projection matrix, and restores the
     * previously active OpenGL viewport rectangle.
     * </p>
     */
    public void unbind() {
        glMatrixMode(GL_MODELVIEW);
        glPopMatrix();

        glMatrixMode(GL_PROJECTION);
        glPopMatrix();

        glViewport(oldViewport[0], oldViewport[1], oldViewport[2], oldViewport[3]);
    }

    /**
     * Renders a drawing function inside this viewport while automatically preserving and restoring
     * the previous OpenGL state.
     *
     * <p>
     * This is a convenience wrapper around the same bind/apply/unbind flow used by
     * {@link #bind()} and {@link #unbind()}, but scoped to a single callback.
     * </p>
     *
     * @param function the drawing function to execute inside this viewport
     * @throws NullPointerException if {@code function} is null
     */
    public void render(DrawFunction function) {
        if (function == null) {
            throw new NullPointerException("Draw function cannot be null");
        }

        glGetIntegerv(GL_VIEWPORT, oldViewport);

        glMatrixMode(GL_PROJECTION);
        glPushMatrix();

        glMatrixMode(GL_MODELVIEW);
        glPushMatrix();

        apply();
        function.draw();

        glMatrixMode(GL_MODELVIEW);
        glPopMatrix();

        glMatrixMode(GL_PROJECTION);
        glPopMatrix();

        glViewport(oldViewport[0], oldViewport[1], oldViewport[2], oldViewport[3]);
    }

    /**
     * Converts a screen-space coordinate into a world-space coordinate using this viewport's
     * current layout and optional camera.
     *
     * <p>
     * If the given screen coordinate lies outside the viewport's screen rectangle, this method
     * returns {@code null}. Otherwise it converts the point into viewport-local coordinates,
     * maps it into the logical world dimensions, and then optionally applies the inverse camera
     * transform if a camera is assigned.
     * </p>
     *
     * <p>
     * The returned vector is reused internally, so it should be used immediately and not stored
     * long-term if more conversions may happen later.
     * </p>
     *
     * @param screenX the x position in actual screen pixels
     * @param screenY the y position in actual screen pixels
     * @return a reused vector containing the corresponding world coordinate, or {@code null} if the point is outside the viewport
     */
    public Vector2f screenToWorld(float screenX, float screenY) {
        if (screenX < x || screenY < y || screenX > x + width || screenY > y + height) {
            return null;
        }

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

        return screenToWorldCoordinates.set(wx, wy);
    }

    /**
     * Begins a viewport-aware scissor rectangle using world-space coordinates.
     *
     * <p>
     * The provided world-space rectangle is converted into the viewport's actual screen-space
     * pixel rectangle. The result is then clamped to the viewport bounds. If another scissor
     * rectangle is already active, the new rectangle is intersected with the existing scissor
     * region so nested scissoring behaves correctly.
     * </p>
     *
     * <p>
     * If the resulting scissor rectangle has no visible area, this method returns {@code false}
     * and no new scissor state is applied.
     * </p>
     *
     * @param wx the world-space x coordinate of the scissor rectangle
     * @param wy the world-space y coordinate of the scissor rectangle
     * @param ww the world-space width of the scissor rectangle
     * @param wh the world-space height of the scissor rectangle
     * @return true if a valid scissor rectangle was applied, false if the rectangle clipped to nothing
     */
    public boolean beginScissor(float wx, float wy, float ww, float wh) {
        float nx = wx / worldWidth;
        float ny = wy / worldHeight;
        float nw = ww / worldWidth;
        float nh = wh / worldHeight;

        int sx = (int) (x + nx * width);
        int sy = (int) (y + ny * height);
        int sw = (int) (nw * width);
        int sh = (int) (nh * height);

        if (sx < x) {
            sw -= x - sx;
            sx = x;
        }
        if (sy < y) {
            sh -= y - sy;
            sy = y;
        }
        if (sx + sw > x + width) {
            sw = x + width - sx;
        }
        if (sy + sh > y + height) {
            sh = y + height - sy;
        }

        if (sw <= 0 || sh <= 0) {
            return false;
        }

        scissorWasEnabled = glIsEnabled(GL_SCISSOR_TEST);

        glGetIntegerv(GL_SCISSOR_BOX, previousScissor);
        int prevX = previousScissor[0];
        int prevY = previousScissor[1];
        int prevW = previousScissor[2];
        int prevH = previousScissor[3];

        int fx = sx;
        int fy = sy;
        int fw = sw;
        int fh = sh;

        if (scissorWasEnabled) {
            int prevRight = prevX + prevW;
            int prevTop = prevY + prevH;
            int newRight = sx + sw;
            int newTop = sy + sh;

            int ix0 = Math.max(prevX, sx);
            int iy0 = Math.max(prevY, sy);
            int ix1 = Math.min(prevRight, newRight);
            int iy1 = Math.min(prevTop, newTop);

            fw = ix1 - ix0;
            fh = iy1 - iy0;
            fx = ix0;
            fy = iy0;

            if (fw <= 0 || fh <= 0) {
                return false;
            }
        }

        glEnable(GL_SCISSOR_TEST);
        glScissor(fx, fy, fw, fh);
        return true;
    }

    /**
     * Ends the most recently applied scissor scope started by {@link #beginScissor(float, float, float, float)}.
     *
     * <p>
     * If a scissor test was already active before this viewport began its scissor scope, the
     * previous scissor rectangle is restored. Otherwise the OpenGL scissor test is disabled.
     * </p>
     */
    public void endScissor() {
        if (scissorWasEnabled) {
            glScissor(previousScissor[0], previousScissor[1], previousScissor[2], previousScissor[3]);
        } else {
            glDisable(GL_SCISSOR_TEST);
        }
    }

    /**
     * Executes a draw function inside a temporary world-space scissor rectangle.
     *
     * <p>
     * This is a convenience wrapper around {@link #beginScissor(float, float, float, float)} and
     * {@link #endScissor()}. If the requested scissor rectangle clips to nothing, the function is
     * not called.
     * </p>
     *
     * @param wx       the world-space x coordinate of the scissor rectangle
     * @param wy       the world-space y coordinate of the scissor rectangle
     * @param ww       the world-space width of the scissor rectangle
     * @param wh       the world-space height of the scissor rectangle
     * @param function the drawing code to execute while the scissor is active
     * @throws NullPointerException if {@code function} is null
     */
    public void applyScissor(float wx, float wy, float ww, float wh, DrawFunction function) {
        if (function == null) {
            throw new NullPointerException("Scissor function cannot be null");
        }

        if (!beginScissor(wx, wy, ww, wh)) {
            return;
        }

        try {
            function.draw();
        } finally {
            endScissor();
        }
    }

    /**
     * Sets the logical world size visible through this viewport.
     *
     * <p>
     * This changes the world-space dimensions used for projection and input conversion. Concrete
     * subclasses may need {@link #update(int, int)} to be called afterward so their projection
     * matrix and screen rectangle stay in sync with the new world dimensions.
     * </p>
     *
     * @param worldWidth  the new logical world width
     * @param worldHeight the new logical world height
     */
    public void setWorldSize(float worldWidth, float worldHeight) {
        this.worldWidth = worldWidth;
        this.worldHeight = worldHeight;
    }

    /**
     * Sets the screen-space size of this viewport in pixels.
     *
     * <p>
     * This directly updates the viewport rectangle dimensions. Most subclasses normally calculate
     * these values inside {@link #update(int, int)}.
     * </p>
     *
     * @param width  the new viewport width in pixels
     * @param height the new viewport height in pixels
     */
    public void setSize(int width, int height) {
        this.width = width;
        this.height = height;
    }

    /**
     * Sets the screen-space position of this viewport in pixels.
     *
     * @param x the new screen-space x position
     * @param y the new screen-space y position
     */
    public void setPosition(int x, int y) {
        this.x = x;
        this.y = y;
    }

    /**
     * Returns the screen-space x position of this viewport.
     *
     * @return the viewport x position in pixels
     */
    public int getX() {
        return x;
    }

    /**
     * Sets the screen-space x position of this viewport.
     *
     * @param x the new viewport x position in pixels
     */
    public void setX(int x) {
        this.x = x;
    }

    /**
     * Returns the screen-space y position of this viewport.
     *
     * @return the viewport y position in pixels
     */
    public int getY() {
        return y;
    }

    /**
     * Sets the screen-space y position of this viewport.
     *
     * @param y the new viewport y position in pixels
     */
    public void setY(int y) {
        this.y = y;
    }

    /**
     * Returns the screen-space width of this viewport.
     *
     * @return the viewport width in pixels
     */
    public int getWidth() {
        return width;
    }

    /**
     * Returns the screen-space height of this viewport.
     *
     * @return the viewport height in pixels
     */
    public int getHeight() {
        return height;
    }

    /**
     * Returns the logical world width visible through this viewport.
     *
     * @return the logical world width
     */
    public float getWorldWidth() {
        return worldWidth;
    }

    /**
     * Returns the logical world height visible through this viewport.
     *
     * @return the logical world height
     */
    public float getWorldHeight() {
        return worldHeight;
    }

    /**
     * Returns the camera currently assigned to this viewport.
     *
     * @return the current camera, or {@code null} if none is assigned
     */
    public Camera2D getCamera() {
        return camera;
    }

    /**
     * Assigns a camera to this viewport.
     *
     * <p>
     * When a camera is assigned, {@link #apply()} rebuilds the camera using the current world
     * size and loads the camera projection instead of the fallback viewport projection matrix.
     * Passing {@code null} restores fallback projection behavior.
     * </p>
     *
     * @param camera the camera to assign, or {@code null} to disable camera-based projection
     */
    public void setCamera(Camera2D camera) {
        this.camera = camera;
    }
}