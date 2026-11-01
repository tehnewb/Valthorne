package org.lwjgl.glfw;

import java.nio.ByteBuffer;
import org.lwjgl.system.MemoryStack;

/**
 * Borrows decoded image data for canvas icons and custom browser cursors.
 */
public final class GLFWImage implements AutoCloseable {
    private int width; // Logical image width in pixels.
    private int height; // Logical image height in pixels.
    private ByteBuffer pixels; // Borrowed RGBA pixels.

    /**
     * Creates an empty image descriptor.
     */
    private GLFWImage() {
    }

    /**
     * Allocates a Java-managed image descriptor.
     * @return new descriptor
     */
    public static GLFWImage malloc() {
        /*
         * Pixel data remains borrowed; no native image struct is needed.
         */
        return new GLFWImage();
    }

    /**
     * Allocates a scope-local image descriptor.
     * @param stack scratch scope
     * @return new descriptor
     */
    public static GLFWImage malloc(MemoryStack stack) {
        /*
         * The descriptor contains only Java fields and needs no native cleanup.
         */
        return malloc();
    }

    /**
     * Allocates image-descriptor slots for a shared icon call.
     * @param count number of slots
     * @param stack scratch scope
     * @return independent descriptor storage
     */
    public static GLFWImageBuffer malloc(int count, MemoryStack stack) {
        /*
         * Browser icon selection consumes the first available descriptor.
         */
        return new GLFWImageBuffer(count);
    }

    /**
     * Sets image dimensions and borrowed RGBA data.
     * @param width pixel width
     * @param height pixel height
     * @param pixels borrowed RGBA buffer
     * @return this descriptor
     */
    public GLFWImage set(int width, int height, ByteBuffer pixels) { this.width = width; this.height = height; this.pixels = pixels; return this; }

    /**
     * Updates pixel width.
     * @param width pixel width
     * @return this descriptor
     */
    public GLFWImage width(int width) { this.width = width; return this; }

    /**
     * Updates pixel height.
     * @param height pixel height
     * @return this descriptor
     */
    public GLFWImage height(int height) { this.height = height; return this; }

    /**
     * Updates borrowed pixel storage.
     * @param pixels RGBA pixels
     * @return this descriptor
     */
    public GLFWImage pixels(ByteBuffer pixels) { this.pixels = pixels; return this; }

    /**
     * Returns image width.
     * @return pixel width
     */
    public int width() { return width; }

    /**
     * Returns image height.
     * @return pixel height
     */
    public int height() { return height; }

    /**
     * Borrows image pixel storage.
     * @return RGBA buffer
     */
    public ByteBuffer pixels() { return pixels; }

    /**
     * Drops the descriptor's borrowed pixel reference.
     */
    public void free() { pixels = null; }

    @Override
    public void close() { free(); }
}
