package org.lwjgl.bgfx;

/**
 * Immutable texture limits captured from the active browser bgfx device.
 */
public final class BGFXCapsLimits {
    private final int textureSize; // Maximum texture extent on either axis.
    private final int textures; // Maximum native texture-handle count.

    /**
     * Stores native texture allocation limits.
     * @param textureSize maximum extent
     * @param textures maximum texture count
     */
    BGFXCapsLimits(int textureSize, int textures) {
        this.textureSize = textureSize;
        this.textures = textures;
    }

    /**
     * Returns the maximum texture extent.
     * @return pixels per axis
     */
    public int maxTextureSize() { return textureSize; }

    /**
     * Returns the maximum native texture count.
     * @return native handle capacity
     */
    public int maxTextures() { return textures; }
    /**
     * Reports no compute bindings for the WebGL 2 device.
     * @return zero compute bindings
     */
    public int maxComputeBindings() { return 0; }

}
