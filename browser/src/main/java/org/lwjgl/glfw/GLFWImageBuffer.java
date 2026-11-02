package org.lwjgl.glfw;

/**
 * Stores a bounded list of image descriptors for shared window-icon calls.
 */
public final class GLFWImageBuffer {
    private final GLFWImage[] images; // Caller-visible image slots.

    /**
     * Allocates image descriptor slots.
     * @param count number of slots
     */
    GLFWImageBuffer(int count) { images = new GLFWImage[count]; }

    /**
     * Replaces one image descriptor.
     * @param index destination slot
     * @param image borrowed descriptor
     * @return this buffer
     */
    public GLFWImageBuffer put(int index, GLFWImage image) { images[index] = image; return this; }

    /**
     * Returns a borrowed image descriptor.
     * @param index requested slot
     * @return stored descriptor, or null when unset
     */
    public GLFWImage get(int index) { return images[index]; }
}
