package valthorne.graphics;

import valthorne.graphics.bgfx.BgfxGraphics;

/**
 * Accesses physical viewport and scissor state for the selected rendering API.
 * Rectangles use bottom-left framebuffer pixels, matching existing Viewport
 * scopes. bgfx stores explicit pass state instead of querying an graphics device.
 * Caller-owned arrays and primitive parameters keep viewport changes allocation-free.
 */
public final class GraphicsViewport {
    /**
     * Prevents constructing a raster viewport-state accessor.
     */
    private GraphicsViewport() {
    }

    /**
     * Copies the active physical viewport.
     *
     * @param rectangle destination with at least four integers
     */
    public static void get(int[] rectangle) {
        /*
         * Reuse scope-owned storage on both backends instead of allocating snapshots.
         */
        BgfxGraphics.getViewport(rectangle);
    }

    /**
     * Changes the active physical viewport.
     *
     * @param x left edge
     * @param y bottom edge
     * @param width viewport width
     * @param height viewport height
     */
    public static void set(int x, int y, int width, int height) {
        /*
         * Viewport changes start an ordered pass without altering earlier submissions.
         */
        BgfxGraphics.setViewport(x, y, width, height);
    }

    /**
     * Reports whether a physical scissor scope is enabled.
     *
     * @return current physical clipping flag
     */
    public static boolean isScissorEnabled() {
        /*
         * Read engine state without querying an API context.
         */
        return BgfxGraphics.isScissorEnabled();
    }

    /**
     * Copies the current physical scissor rectangle.
     *
     * @param rectangle destination with at least four integers
     */
    public static void getScissor(int[] rectangle) {
        /*
         * Save exactly the state restored by a nested viewport scissor scope.
         */
        BgfxGraphics.getScissor(rectangle);
    }

    /**
     * Enables or changes a physical scissor rectangle.
     *
     * @param x left edge
     * @param y bottom edge
     * @param width nonnegative width
     * @param height nonnegative height
     */
    public static void scissor(int x, int y, int width, int height) {
        /*
         * A shared view scissor constrains all engine renderers, including UI.
         */
        BgfxGraphics.setScissor(x, y, width, height, true);
    }

    /**
     * Disables physical scissoring for subsequent draws.
     */
    public static void disableScissor() {
        /*
         * Logical per-instance clipping remains independent from physical scissoring.
         */
        BgfxGraphics.setScissor(0, 0, 0, 0, false);
    }
}
