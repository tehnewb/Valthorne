package valthorne.viewport;

/**
 * A viewport strategy that stretches the logical world to fill the entire screen rectangle.
 *
 * <p>
 * This viewport never letterboxes and never crops, but it does not preserve aspect ratio.
 * If the screen aspect ratio differs from the world aspect ratio, content is stretched on one
 * axis.
 * </p>
 *
 * <h2>Behavior</h2>
 * <ul>
 *     <li>The viewport always fills the full screen</li>
 *     <li>The whole world remains visible</li>
 *     <li>Aspect ratio is not preserved</li>
 *     <li>Content may appear wider or taller depending on the screen shape</li>
 * </ul>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * StretchViewport viewport = new StretchViewport(800f, 600f);
 * viewport.update(windowWidth, windowHeight);
 *
 * viewport.render(() -> {
 *     renderer.drawScene();
 * });
 * }</pre>
 *
 * @author Albert Beaupre
 * @since December 1st, 2025
 */
public class StretchViewport extends Viewport {

    /**
     * Creates a stretch viewport with the specified logical world size.
     *
     * @param worldWidth the logical world width
     * @param worldHeight the logical world height
     */
    public StretchViewport(float worldWidth, float worldHeight) {
        super(worldWidth, worldHeight);
    }

    /**
     * Updates this viewport so it fills the entire screen without preserving aspect ratio.
     *
     * <p>
     * The screen rectangle is set to the full window size and the fallback orthographic
     * projection is rebuilt for the current world size.
     * </p>
     *
     * @param screenWidth the window width in pixels
     * @param screenHeight the window height in pixels
     */
    @Override
    public void update(int screenWidth, int screenHeight) {
        this.x = 0;
        this.y = 0;
        this.width = screenWidth;
        this.height = screenHeight;

        projectionMatrix.ortho(0, worldWidth, 0, worldHeight, -1f, 1f);
    }
}