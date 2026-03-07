package valthorne.viewport;

/**
 * A viewport strategy that preserves the logical world aspect ratio while ensuring the screen
 * is completely filled, even if part of the world must be cropped.
 *
 * <p>
 * This viewport is useful when you want to avoid black bars and are willing to let some content
 * fall outside the visible region on one axis. The world aspect ratio is preserved, so content
 * is not stretched, but the viewport may extend beyond the window bounds on either width or
 * height depending on the screen aspect ratio.
 * </p>
 *
 * <h2>Behavior</h2>
 * <ul>
 *     <li>If the screen is wider than the world aspect ratio, the viewport expands width-first</li>
 *     <li>If the screen is taller than the world aspect ratio, the viewport expands height-first</li>
 *     <li>In both cases the viewport is centered on the opposite axis</li>
 * </ul>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * FillViewport viewport = new FillViewport(1280f, 720f);
 * viewport.update(windowWidth, windowHeight);
 *
 * viewport.bind();
 * try {
 *     worldRenderer.render();
 * } finally {
 *     viewport.unbind();
 * }
 * }</pre>
 *
 * @author Albert Beaupre
 * @since December 1st, 2025
 */
public class FillViewport extends Viewport {

    /**
     * Creates a fill viewport with the specified logical world size.
     *
     * @param worldWidth  the logical world width
     * @param worldHeight the logical world height
     */
    public FillViewport(float worldWidth, float worldHeight) {
        super(worldWidth, worldHeight);
    }

    /**
     * Updates this viewport so the screen is fully covered while preserving world aspect ratio.
     *
     * <p>
     * The resulting viewport may become larger than the actual screen on one axis, which causes
     * cropping rather than letterboxing. The fallback orthographic projection is then rebuilt for
     * the current world size.
     * </p>
     *
     * @param screenWidth  the window width in pixels
     * @param screenHeight the window height in pixels
     */
    @Override
    public void update(int screenWidth, int screenHeight) {
        float worldAspect = worldWidth / worldHeight;
        float screenAspect = (float) screenWidth / (float) screenHeight;

        if (screenAspect > worldAspect) {
            this.width = screenWidth;
            this.height = (int) (screenWidth / worldAspect);
            this.x = 0;
            this.y = (screenHeight - this.height) / 2;
        } else {
            this.height = screenHeight;
            this.width = (int) (screenHeight * worldAspect);
            this.y = 0;
            this.x = (screenWidth - this.width) / 2;
        }

        projectionMatrix.ortho(0, worldWidth, 0, worldHeight, -1f, 1f);
    }
}