package valthorne.camera;

/**
 * The {@code IsometricCamera2D} class provides an isometric-style 2D camera based
 * on classic isometric projection mathematics. It extends {@link Camera2D} and
 * overrides the {@link #rebuild(float, float)} method to construct an orthographic
 * projection and then transform it into an isometric view through rotation and scaling.
 *
 * <p>This camera is ideal for games and visualization systems that require a
 * classic isometric look, where the world is viewed at a 45° angle with a
 * 2:1 pixel ratio.</p>
 *
 * <h2>Projection Behavior</h2>
 * <ul>
 *     <li>Begins with an orthographic projection aligned to the camera center
 *     and zoom level.</li>
 *     <li>Rotates the projection by 45° around the Z-axis.</li>
 *     <li>Applies a Y-axis compression (scale of 0.5) to achieve the standard
 *     isometric "diamond" appearance.</li>
 * </ul>
 *
 * <h2>Mathematics Summary</h2>
 * <ul>
 *     <li>Rotation: 45° about Z (diagonal world orientation)</li>
 *     <li>Scale: 50% on Y-axis (2:1 isometric ratio)</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * This class is used just like any other 2D camera:
 * <pre>
 *     Camera2D camera = new IsometricCamera2D();
 *     camera.setCenter(0, 0);
 *     camera.setZoom(1.0f);
 *     camera.rebuild(worldWidth, worldHeight);
 * </pre>
 *
 * @author Albert Beaupre
 * @since November 16th, 2025
 */
public class IsometricCamera2D extends Camera2D {

    /**
     * Rebuilds the projection matrix to create a classic isometric view.
     *
     * <p>The process involves:</p>
     * <ol>
     *     <li>Calculating a view-aligned orthographic projection based on the
     *     camera center, world size, and zoom level.</li>
     *     <li>Rotating the projection by 45° around the Z-axis.</li>
     *     <li>Compressing the Y-axis by 50% to achieve a proper isometric ratio.</li>
     * </ol>
     *
     * @param worldWidth  the width of the visible world or viewport
     * @param worldHeight the height of the visible world or viewport
     */
    @Override
    public void rebuild(float worldWidth, float worldHeight) {

        float halfW = (worldWidth * 0.5f) / zoom;
        float halfH = (worldHeight * 0.5f) / zoom;

        float left = center.getX() - halfW;
        float right = center.getX() + halfW;
        float bottom = center.getY() - halfH;
        float top = center.getY() + halfH;

        // Start with a standard orthographic projection
        projection.ortho(left, right, bottom, top, -1f, 1f);

        // Apply a 45° world rotation (diamond orientation)
        projection.rotateZ((float) Math.toRadians(45));

        // Apply a 2:1 isometric Y-scale
        projection.scale(1.0f, 0.5f, 1.0f);
    }
}