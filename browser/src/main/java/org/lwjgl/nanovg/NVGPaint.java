package org.lwjgl.nanovg;

import org.lwjgl.system.MemoryStack;
import valthorne.browser.BrowserNative;

/**
 * Reusable NanoVG paint descriptor. Native gradients copy their complete value
 * into this Java scratch record; drawing serializes it without native allocation
 * or per-draw temporary paint objects.
 */
public final class NVGPaint implements AutoCloseable {
    private final float[] values = new float[18]; // Transform, extent, radius, feather and two colors.
    private int image; // Borrowed NanoVG image identifier.

    /**
     * Creates zero-filled paint storage.
     */
    private NVGPaint() {
    }

    /**
     * Allocates independent Java-managed paint scratch.
     * @return mutable paint descriptor
     */
    public static NVGPaint calloc() {
        /*
         * Paints are copied values rather than native pointers in browser bindings.
         */
        return new NVGPaint();
    }

    /**
     * Allocates paint scratch for a lexical drawing scope.
     * @param stack scratch scope
     * @return mutable paint descriptor
     */
    public static NVGPaint calloc(MemoryStack stack) {
        /*
         * Java owns the descriptor; the scope has no native allocation to release.
         */
        return calloc();
    }

    /**
     * Copies a gradient or image-pattern result from native scratch.
     * @return this paint
     */
    NVGPaint read() {
        for (int i = 0; i < values.length; i++) values[i] = Float.intBitsToFloat(BrowserNative.word(i));
        image = BrowserNative.word(18);
        return this;
    }

    /**
     * Writes this paint's copied value into the next native command.
     */
    void write() {
        for (int i = 0; i < values.length; i++) BrowserNative.value(i, values[i]);
        BrowserNative.word(1, image);
    }

    /**
     * Completes scratch ownership; this descriptor owns no native allocation.
     */
    public void free() {
        // All paint components are retained as ordinary Java values.
    }

    @Override
    public void close() { free(); }
}
