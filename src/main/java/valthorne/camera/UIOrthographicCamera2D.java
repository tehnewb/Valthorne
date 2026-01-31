package valthorne.camera;

/**
 * The {@code UIOrthographicCamera2D} class provides a specialized orthographic camera
 * designed for UI and screen-space rendering. Unlike a world-space camera, this camera
 * aligns its projection to a top-left origin system (0,0 → top-left, width/height → bottom-right),
 * which is the common coordinate system used for user interfaces.
 *
 * <p>This camera does <strong>not</strong> center its projection. Instead, it treats the
 * {@link #center} vector as the top-left corner of the visible region, making UI layout
 * logic intuitive and consistent with screen-space coordinates.</p>
 *
 * <h2>Key Differences from {@link OrthographicCamera2D}</h2>
 * <ul>
 *     <li>Origin aligns to the top-left of the UI.</li>
 *     <li>No world-centered projection—uses {@code center} as the top-left.</li>
 *     <li>Projection Y-axis is inverted to match screen-space coords
 *         (top → 0, bottom → +height).</li>
 *     <li>Zoom scales UI uniformly without shifting the camera’s origin.</li>
 * </ul>
 *
 * <h2>Use Cases</h2>
 * <ul>
 *     <li>User interface rendering</li>
 *     <li>HUDs, menus, overlays, tooltips</li>
 *     <li>Pixel-based or resolution-independent UI layouts</li>
 * </ul>
 *
 * <h2>Example</h2>
 * <pre>
 *     Camera2D uiCamera = new UIOrthographicCamera2D();
 *     uiCamera.setCenter(0, 0);   // Top-left of screen
 *     uiCamera.setZoom(1f);
 *     uiCamera.rebuild(windowWidth, windowHeight);
 * </pre>
 *
 * @author Albert Beaupre
 * @since November 16th, 2025
 */
public class UIOrthographicCamera2D extends Camera2D {

    /**
     * Rebuilds the projection matrix using a UI-friendly coordinate system.
     * The visible region begins at {@link #center} (treated as the top-left corner)
     * and extends rightward and downward according to the scaled world dimensions.
     *
     * <p>The projection Y-axis is inverted by supplying {@code bottom} above
     * {@code top} when calling {@code ortho}, matching typical UI coordinate
     * conventions.</p>
     *
     * @param worldWidth  the width of the screen or UI layout region
     * @param worldHeight the height of the screen or UI layout region
     */
    @Override
    public void rebuild(float worldWidth, float worldHeight) {
        float w = worldWidth / zoom;
        float h = worldHeight / zoom;

        float left = center.getX();
        float right = center.getX() + w;
        float top = center.getY();
        float bottom = center.getY() + h;

        // Build a top-left aligned orthographic projection for UI rendering
        projection.ortho(left, right, bottom, top, -1f, 1f);
    }
}
