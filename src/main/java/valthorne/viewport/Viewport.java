package valthorne.viewport;

import valthorne.camera.Camera2D;
import valthorne.graphics.DrawFunction;
import valthorne.math.Matrix4f;
import valthorne.math.Vector2f;

import static org.lwjgl.opengl.GL11.*;

/**
 * Represents a viewport in a 2D rendering context, handling projection, view transformations,
 * and OpenGL state management. The {@code Viewport} class provides functionality for managing
 * screen-to-world coordinate mappings, OpenGL scissor operations, and projection matrix updates.
 * Subclasses are required to define their own viewport resizing logic and projection setups.
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
     * Stores the previous OpenGL viewport configuration as an array of four integers.
     * This includes the x and y position, width, and height of the last OpenGL viewport.
     */
    private final int[] oldViewport = new int[4];

    /**
     * Stores the previous OpenGL scissor rectangle's coordinates and dimensions.
     * Used to restore the scissor state after applying transformations or nested
     * scissor rectangles.
     */
    private final int[] previousScissor = new int[4];

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
     * A vector used internally to store temporary calculations or transformations
     * related to the viewport. This vector is managed internally by the {@code Viewport}
     * and is not directly exposed or modifiable by external code.
     */
    private final Vector2f screenToWorldCoordinates = new Vector2f();

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
     * Applies the current viewport configuration to the OpenGL state.
     * <p>
     * The method adjusts the OpenGL viewport to match the viewport's screen position
     * and size, sets the projection matrix based on the assigned camera or fallback
     * projection matrix, and resets the model-view matrix.
     * <p>
     * - The OpenGL viewport is configured to the dimensions defined by {@code x},
     * {@code y}, {@code width}, and {@code height}.
     * - If a camera is set, its projection matrix is rebuilt using the logical world
     * size and applied; otherwise, the default projection matrix is used.
     * - The OpenGL projection matrix is updated to align with the selected matrix.
     * - The OpenGL model-view matrix is reset to the identity matrix.
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
     * Renders a drawing operation inside the viewport's projection and model-view matrix scope.
     * This method ensures that the OpenGL state is preserved before and restored after rendering.
     *
     * @param function a {@link DrawFunction} representing the drawing operation to be rendered
     */
    public void render(DrawFunction function) {
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
     * Converts screen coordinates to world coordinates based on the viewport's
     * configuration, including optional camera adjustments.
     *
     * @param screenX the x-coordinate in screen space
     * @param screenY the y-coordinate in screen space
     * @return a {@code Vector2f} representing the corresponding world coordinates,
     * or {@code null} if the screen coordinates lie outside the viewport
     */
    public Vector2f screenToWorld(float screenX, float screenY) {
        if (screenX < x || screenY < y || screenX > x + width || screenY > y + height) return null;

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
     * Applies a scissor operation to restrict rendering within a specified region
     * of the viewport. This operation ensures that any draw calls are limited to
     * the defined rectangular area. The scissor rectangle is defined in terms of
     * the viewport's logical world coordinates.
     *
     * @param wx       the x-coordinate of the lower-left corner of the scissor
     *                 rectangle in world coordinates
     * @param wy       the y-coordinate of the lower-left corner of the scissor
     *                 rectangle in world coordinates
     * @param ww       the width of the scissor rectangle in world coordinates
     * @param wh       the height of the scissor rectangle in world coordinates
     * @param function a {@code DrawFunction} representing the drawing operation
     *                 to execute within the scissor region
     * @throws NullPointerException if the {@code function} parameter is null
     */
    public void applyScissor(float wx, float wy, float ww, float wh, DrawFunction function) {
        if (function == null)
            throw new NullPointerException("Scissor function cannot be null");

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
        if (sx + sw > x + width) sw = (x + width) - sx;
        if (sy + sh > y + height) sh = (y + height) - sy;

        if (sw <= 0 || sh <= 0) return;

        boolean wasEnabled = glIsEnabled(GL_SCISSOR_TEST);

        glGetIntegerv(GL_SCISSOR_BOX, previousScissor);
        int prevX = previousScissor[0];
        int prevY = previousScissor[1];
        int prevW = previousScissor[2];
        int prevH = previousScissor[3];

        int fx = sx;
        int fy = sy;
        int fw = sw;
        int fh = sh;

        if (wasEnabled) {
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

            if (fw <= 0 || fh <= 0) return;
        }

        glEnable(GL_SCISSOR_TEST);
        glScissor(fx, fy, fw, fh);

        try {
            function.draw();
        } finally {
            if (wasEnabled)
                glScissor(prevX, prevY, prevW, prevH);
            if (!wasEnabled)
                glDisable(GL_SCISSOR_TEST);
        }
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
