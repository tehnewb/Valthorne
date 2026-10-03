package valthorne.viewport;

import static org.lwjgl.opengl.GL11.glViewport;

/**
 * Fits a fixed logical world into a window with an optional whole-number pixel
 * scale. When the window can hold at least one full-sized copy of the world,
 * integer mode centers the largest whole-number enlargement. Smaller windows
 * fall back to the ordinary aspect-preserving fit so the full world remains
 * visible. Disabling integer mode uses {@link FitViewport} directly.
 *
 * <p>This viewport controls both rendering and pointer conversion through the
 * normal {@link Viewport} API. Update it after changing the integer mode or the
 * window size, then share it with any UI root drawing over the same world.</p>
 */
public final class IntegerFitViewport extends FitViewport {
    private boolean integerScalingEnabled = true; // Whether whole-number enlargement is preferred when it fits.
    private float framebufferScaleX = 1f; // Physical framebuffer pixels per window unit on the horizontal axis.
    private float framebufferScaleY = 1f; // Physical framebuffer pixels per window unit on the vertical axis.

    /**
     * Creates a centered viewport for the specified positive logical dimensions.
     *
     * @param worldWidth  logical width in world units
     * @param worldHeight logical height in world units
     */
    public IntegerFitViewport(float worldWidth, float worldHeight) {
        super(worldWidth, worldHeight);
    }

    /**
     * Selects integer enlargement or continuous aspect-preserving fitting.
     * Call {@link #update(int, int)} after changing the setting.
     *
     * @param enabled whether to prefer a whole-number scale when it fits
     * @return this viewport
     */
    public IntegerFitViewport integerScalingEnabled(boolean enabled) {
        integerScalingEnabled = enabled;
        return this;
    }

    /**
     * Reports whether whole-number enlargement is selected.
     *
     * @return true when integer mode is enabled
     */
    public boolean isIntegerScalingEnabled() {
        return integerScalingEnabled;
    }

    /**
     * Sets the display's physical-to-logical scale without changing pointer
     * conversion, which continues to use window coordinates. This lets one
     * viewport serve both a framebuffer render and logical UI input on HiDPI
     * displays. The scale is normally the framebuffer size divided by the
     * corresponding window size.
     *
     * @param horizontal finite positive horizontal pixel scale
     * @param vertical   finite positive vertical pixel scale
     * @return this viewport
     * @throws IllegalArgumentException if either scale is nonpositive or nonfinite
     */
    public IntegerFitViewport framebufferScale(float horizontal, float vertical) {
        if (!Float.isFinite(horizontal) || horizontal <= 0 || !Float.isFinite(vertical) || vertical <= 0)
            throw new IllegalArgumentException("Framebuffer scale must be finite and positive");
        framebufferScaleX = horizontal;
        framebufferScaleY = vertical;
        return this;
    }

    @Override
    public void apply() {
        super.apply();
        glViewport(Math.round(x * framebufferScaleX), Math.round(y * framebufferScaleY), Math.round(width * framebufferScaleX), Math.round(height * framebufferScaleY));
    }

    @Override
    public void update(int screenWidth, int screenHeight) {
        if (screenWidth <= 0 || screenHeight <= 0) {
            x = 0;
            y = 0;
            width = 0;
            height = 0;
            return;
        }

        super.update(screenWidth, screenHeight);
        if (!integerScalingEnabled) return;

        int scale = (int) Math.floor(Math.min(screenWidth / worldWidth, screenHeight / worldHeight));
        if (scale < 1) return;

        width = Math.round(worldWidth * scale);
        height = Math.round(worldHeight * scale);
        x = (screenWidth - width) / 2;
        y = (screenHeight - height) / 2;
    }
}
