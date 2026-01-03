package valthorne.camera;

/**
 * The {@code OrthographicCamera2D} class provides a standard 2D orthographic camera.
 * It extends {@link Camera2D} and constructs an axis-aligned orthographic projection
 * based on the camera's center, zoom level, and the world dimensions provided.
 *
 * <p>This is the most common camera type for 2D rendering and is ideal for:</p>
 * <ul>
 *     <li>Tile-based games</li>
 *     <li>UI systems</li>
 *     <li>Side-scrollers and top-down views</li>
 *     <li>Pixel-perfect rendering setups (when paired with appropriate world units)</li>
 * </ul>
 *
 * <h2>Projection Behavior</h2>
 * <ul>
 *     <li>Computes a world-aligned orthographic projection.</li>
 *     <li>Zooming reduces or expands the viewed region.</li>
 *     <li>The projection always remains unrotated and non-skewed.</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>
 *     Camera2D camera = new OrthographicCamera2D();
 *     camera.setCenter(0, 0);
 *     camera.setZoom(1f);
 *     camera.rebuild(worldWidth, worldHeight);
 * </pre>
 *
 * @author Albert Beaupre
 * @since November 16th, 2025
 */
public class OrthographicCamera2D extends Camera2D {

    /**
     * Rebuilds the camera's projection matrix using a standard axis-aligned
     * orthographic projection. The visible region is determined by the world
     * dimensions and the current zoom factor.
     *
     * <p>The projection boundaries are computed so that the camera centers on
     * {@link #center} and zooms uniformly in both X and Y directions.</p>
     *
     * @param worldWidth  the width of the viewport or world units visible
     * @param worldHeight the height of the viewport or world units visible
     */
    @Override
    public void rebuild(float worldWidth, float worldHeight) {
        float halfW = (worldWidth * 0.5f) / zoom;
        float halfH = (worldHeight * 0.5f) / zoom;

        float left = center.getX() - halfW;
        float right = center.getX() + halfW;
        float bottom = center.getY() - halfH;
        float top = center.getY() + halfH;

        // Construct a classic orthographic projection
        projection.ortho(left, right, bottom, top, -1f, 1f);
    }
}
