package valthorne.viewport;

/**
 * A viewport strategy that preserves the logical world aspect ratio while fitting the entire
 * world inside the available screen area.
 *
 * <p>
 * This viewport is commonly used when you want the whole world region to remain visible without
 * distortion. If the window aspect ratio does not match the world aspect ratio, unused space
 * appears as letterboxing or pillarboxing.
 * </p>
 *
 * <h2>Behavior</h2>
 * <ul>
 *     <li>The full world is always visible</li>
 *     <li>Aspect ratio is preserved</li>
 *     <li>No stretching occurs</li>
 *     <li>Black bars or unused margins may appear on one axis</li>
 * </ul>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * FitViewport viewport = new FitViewport(1920f, 1080f);
 * viewport.update(windowWidth, windowHeight);
 *
 * viewport.render(() -> {
 *     game.render();
 * });
 * }</pre>
 *
 * @author Albert Beaupre
 * @since December 1st, 2025
 */
public class FitViewport extends Viewport {

    /**
     * Creates a fit viewport with the specified logical world size.
     *
     * @param worldWidth the logical world width
     * @param worldHeight the logical world height
     */
    public FitViewport(float worldWidth, float worldHeight) {
        super(worldWidth, worldHeight);
    }

    /**
     * Updates this viewport so the full world remains visible while preserving aspect ratio.
     *
     * <p>
     * The screen rectangle is centered on whichever axis has remaining unused space. The fallback
     * orthographic projection is then rebuilt for the current world size.
     * </p>
     *
     * @param screenWidth the window width in pixels
     * @param screenHeight the window height in pixels
     */
    @Override
    public void update(int screenWidth, int screenHeight) {
        float worldAspect = worldWidth / worldHeight;
        float screenAspect = (float) screenWidth / (float) screenHeight;

        if (screenAspect > worldAspect) {
            this.height = screenHeight;
            this.width = (int) (screenHeight * worldAspect);
            this.x = (screenWidth - this.width) / 2;
            this.y = 0;
        } else {
            this.width = screenWidth;
            this.height = (int) (screenWidth / worldAspect);
            this.x = 0;
            this.y = (screenHeight - this.height) / 2;
        }

        projectionMatrix.ortho(0, worldWidth, 0, worldHeight, -1f, 1f);
    }
}