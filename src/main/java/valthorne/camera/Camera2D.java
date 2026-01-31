package valthorne.camera;

import valthorne.math.Matrix4f;
import valthorne.math.Vector2f;

/**
 * The {@code Camera2D} class serves as the abstract foundation for all 2D camera
 * implementations within the JGL framework. It provides common properties and
 * behavior needed for rendering 2D worlds, such as camera centering, zooming,
 * and projection matrix management.
 *
 * <p>A {@code Camera2D} defines how the world is viewed during rendering. Concrete
 * subclasses provide specific projection types (orthographic, pixel-perfect,
 * screen-space, etc.) by implementing {@link #rebuild(float, float)}.</p>
 *
 * <h2>Core Responsibilities</h2>
 * <ul>
 *     <li>Store and modify the camera's world-space center position.</li>
 *     <li>Maintain camera zoom level, clamped to a minimum safe value.</li>
 *     <li>Provide access to the camera's projection matrix.</li>
 *     <li>Require subclasses to rebuild the projection matrix when needed.</li>
 * </ul>
 *
 * <h2>Usage Notes</h2>
 * <ul>
 *     <li>{@link #rebuild(float, float)} should be called once per frame or whenever
 *     zoom or center changes.</li>
 *     <li>{@link #getProjection()} returns the active projection matrix used in rendering.</li>
 *     <li>Zoom values below {@code 0.001f} are automatically clamped.</li>
 * </ul>
 * <p>
 * This class is intended for extension—use {@link Camera2D} as the base for
 * custom camera types tailored to specific rendering strategies.
 *
 * @author Albert Beaupre
 * @since November 16th, 2025
 */
public abstract class Camera2D {

    /**
     * The world-space center position of the camera. Rendering is typically
     * performed relative to this point.
     */
    protected final Vector2f center = new Vector2f(0, 0);
    /**
     * The projection matrix used by the camera. Concrete implementations rebuild
     * this matrix when camera settings or world dimensions change.
     */
    protected final Matrix4f projection = new Matrix4f();
    /**
     * Current zoom level of the camera. Higher values zoom in; lower values zoom out.
     * Cannot be set below {@code 0.001f}.
     */
    protected float zoom = 1f;

    /**
     * Returns the current center of the camera.
     *
     * @return the camera's world-space center as a {@link Vector2f}
     */
    public Vector2f getCenter() {
        return center;
    }

    /**
     * Sets the camera's center location in world space.
     *
     * @param x the new x-coordinate of the camera center
     * @param y the new y-coordinate of the camera center
     */
    public void setCenter(float x, float y) {
        center.set(x, y);
    }

    /**
     * Returns the current zoom level of the camera.
     *
     * @return the zoom factor
     */
    public float getZoom() {
        return zoom;
    }

    /**
     * Sets the zoom level of the camera. Zoom is clamped to a minimum of {@code 0.001f}
     * to prevent projection matrix instability or division-by-zero calculations.
     *
     * @param z the desired zoom level
     */
    public void setZoom(float z) {
        zoom = Math.max(0.001f, z);
    }

    /**
     * Rebuilds the camera's projection matrix. This method is called whenever
     * the camera changes (zoom, center) or once each frame depending on implementation.
     *
     * <p>Subclasses must define how the projection matrix is constructed based on
     * the world width and height.</p>
     *
     * @param worldWidth  the width of the world or viewport
     * @param worldHeight the height of the world or viewport
     */
    public abstract void rebuild(float worldWidth, float worldHeight);

    /**
     * Returns the active projection matrix used by the camera during rendering.
     *
     * @return the internal {@link Matrix4f} projection matrix
     */
    public Matrix4f getProjection() {
        return projection;
    }
}