package valthorne.viewport;

/**
 * A viewport strategy where the viewport fills the entire screen and, by default, the logical
 * world size matches the actual screen size.
 *
 * <p>
 * This viewport is useful for UI rendering or screen-space rendering where you want one world
 * unit to correspond directly to one screen pixel after each resize. It can also be constructed
 * with a custom initial world size, but {@link #update(int, int)} will overwrite the world size
 * so that it matches the latest screen dimensions.
 * </p>
 *
 * <h2>Behavior</h2>
 * <ul>
 *     <li>The viewport always covers the full screen</li>
 *     <li>The world size is updated to match the current screen size</li>
 *     <li>No aspect preservation logic is needed because the world tracks the screen directly</li>
 * </ul>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * ScreenViewport viewport = new ScreenViewport(1280, 720);
 * viewport.update(windowWidth, windowHeight);
 *
 * viewport.bind();
 * try {
 *     ui.draw(batch);
 * } finally {
 *     viewport.unbind();
 * }
 * }</pre>
 *
 * @author Albert Beaupre
 * @since December 1st, 2025
 */
public class ScreenViewport extends Viewport {

    /**
     * Creates a screen viewport whose initial logical world size matches the supplied pixel size.
     *
     * @param width the initial screen-like world width
     * @param height the initial screen-like world height
     */
    public ScreenViewport(int width, int height) {
        super(width, height);
    }

    /**
     * Creates a screen viewport with a custom initial world size.
     *
     * <p>
     * Note that once {@link #update(int, int)} is called, the world size is overwritten so it
     * matches the current screen size.
     * </p>
     *
     * @param worldWidth the initial logical world width
     * @param worldHeight the initial logical world height
     */
    public ScreenViewport(float worldWidth, float worldHeight) {
        super(worldWidth, worldHeight);
    }

    /**
     * Updates this viewport so it fully matches the current screen dimensions.
     *
     * <p>
     * Both the screen rectangle and the logical world size are updated to the provided dimensions.
     * The fallback orthographic projection is then rebuilt so world units match screen pixels.
     * </p>
     *
     * @param screenWidth the window width in pixels
     * @param screenHeight the window height in pixels
     */
    @Override
    public void update(int screenWidth, int screenHeight) {
        this.width = screenWidth;
        this.height = screenHeight;
        this.x = 0;
        this.y = 0;
        this.worldWidth = screenWidth;
        this.worldHeight = screenHeight;

        projectionMatrix.ortho(0, worldWidth, 0, worldHeight, -1f, 1f);
    }
}