package valthorne.camera;

import org.joml.Matrix4f;
import org.joml.Vector2f;

/**
 * The {@code Camera} class serves as the abstract foundation for all 2D camera
 * implementations within the JGL framework. It provides common properties and
 * behavior needed for rendering 2D worlds, such as camera centering, zooming,
 * and projection matrix management.
 *
 * <p>A {@code Camera} defines how the world is viewed during rendering. Concrete
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
 * This class is intended for extension—use {@link Camera} as the base for
 * custom camera types tailored to specific rendering strategies.
 *
 * @author Albert Beaupre
 * @since November 16th, 2025
 */
public abstract class Camera {

    protected final Vector2f center = new Vector2f(0, 0); // Live world-space camera center used by concrete projection implementations.
    protected final Matrix4f projection = new Matrix4f(); // Reusable projection matrix rebuilt by concrete camera implementations.
    protected float zoom = 1f; // Camera zoom factor, initially one; setters enforce a minimum of 0.001.

    /**
     * Returns the current center of the camera.
     *
     * The returned vector is live. Built-in cameras validate it again during rebuild.
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
     * @throws IllegalArgumentException if either coordinate is non-finite; state is unchanged
     */
    public void setCenter(float x, float y) {
        if (!Float.isFinite(x) || !Float.isFinite(y)) throw new IllegalArgumentException("Camera center must be finite");
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
     * @param z the desired finite zoom level
     * @throws IllegalArgumentException if zoom is non-finite; state is unchanged
     */
    public void setZoom(float z) {
        if (!Float.isFinite(z)) throw new IllegalArgumentException("Camera zoom must be finite");
        zoom = Math.max(0.001f, z);
    }

    /**
     * Rebuilds the camera's projection matrix. This method is called whenever
     * the camera changes (zoom, center) or once each frame depending on implementation.
     *
     * <p>Subclasses must define how the projection matrix is constructed based on
     * the world width and height. Built-in cameras reject non-finite/nonpositive dimensions
     * and unrepresentable bounds before mutating the previous projection. Skip rebuild while
     * minimized if dimensions are zero.</p>
     *
     * @param worldWidth  the width of the world or viewport
     * @param worldHeight the height of the world or viewport
     */
    public abstract void rebuild(float worldWidth, float worldHeight);

    /**
     * Validates dimensions and live camera state before a built-in projection rebuild.
     *
     * @param width positive finite world width
     * @param height positive finite world height
     * @throws IllegalArgumentException if dimensions or live camera state are invalid
     */
    protected final void validateProjectionDimensions(float width, float height) {
        if (!Float.isFinite(width) || !Float.isFinite(height) || width <= 0f || height <= 0f)
            throw new IllegalArgumentException("Projection dimensions must be positive and finite");
        if (!Float.isFinite(zoom) || zoom <= 0f || !Float.isFinite(center.x) || !Float.isFinite(center.y))
            throw new IllegalArgumentException("Camera zoom and center must be finite with positive zoom");
    }

    /**
     * Commits finite orthographic bounds only when their float coefficients are representable.
     * Preserves the previous matrix if extreme dimensions, zoom, or center collapse bounds.
     *
     * @param left horizontal lower bound
     * @param right horizontal upper bound
     * @param bottom first vertical bound, which may exceed top for UI projections
     * @param top second vertical bound
     * @throws IllegalArgumentException if bounds generate non-finite projection coefficients
     */
    protected final void setOrthographicProjection(float left, float right, float bottom, float top) {
        float width = right - left;
        float height = top - bottom;
        if (!Float.isFinite(left) || !Float.isFinite(right) || !Float.isFinite(bottom) || !Float.isFinite(top)
                || !Float.isFinite(width) || !Float.isFinite(height) || width == 0f || height == 0f
                || !Float.isFinite(2f / width) || !Float.isFinite(2f / height)
                || !Float.isFinite((right + left) / width) || !Float.isFinite((top + bottom) / height))
            throw new IllegalArgumentException("Camera bounds do not produce a finite orthographic projection");
        projection.setOrtho(left, right, bottom, top, -1f, 1f);
    }

    /**
     * Returns the active projection matrix used by the camera during rendering.
     *
     * @return the internal {@link Matrix4f} projection matrix
     */
    public Matrix4f getProjection() {
        return projection;
    }
}