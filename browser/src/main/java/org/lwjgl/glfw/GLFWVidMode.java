package org.lwjgl.glfw;

/**
 * Captures browser screen dimensions for shared window layout code. Browsers
 * schedule presentation through animation frames rather than a selected mode.
 */
public final class GLFWVidMode {
    private final int width; // Available display width in logical pixels.
    private final int height; // Available display height in logical pixels.

    /**
     * Captures available browser display dimensions.
     * @param width logical width
     * @param height logical height
     */
    GLFWVidMode(int width, int height) { this.width = width; this.height = height; }

    /**
     * Returns the available logical display width.
     * @return logical pixels
     */
    public int width() { return width; }

    /**
     * Returns the available logical display height.
     * @return logical pixels
     */
    public int height() { return height; }

    /**
     * Returns no explicit refresh-rate selection for browser-controlled presentation.
     * @return zero, requesting the browser default
     */
    public int refreshRate() { return 0; }
}
