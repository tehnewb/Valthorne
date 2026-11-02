package org.lwjgl.nanovg;

import org.lwjgl.system.MemoryStack;

/**
 * Mutable floating-point color storage for the shared NanoVG drawing API. Values
 * are copied into native commands; Java owns this scratch object's lifetime.
 */
public final class NVGColor implements AutoCloseable {
    private float r; // R color component.
    private float g; // G color component.
    private float b; // B color component.
    private float a; // A color component.

    /**
     * Creates zero-filled color storage.
     */
    private NVGColor() {
    }

    /**
     * Creates independent Java-managed color storage.
     * @return mutable zero-filled color
     */
    public static NVGColor create() {
        /*
         * No native allocation is needed until drawing copies the color values.
         */
        return new NVGColor();
    }

    /**
     * Creates independent Java-managed color storage.
     * @return mutable zero-filled color
     */
    public static NVGColor calloc() {
        /*
         * No native allocation is needed until drawing copies the color values.
         */
        return new NVGColor();
    }

    /**
     * Creates scratch color storage for a lexical drawing scope.
     * @param stack scratch scope
     * @return mutable zero-filled color
     */
    public static NVGColor calloc(MemoryStack stack) {
        /*
         * Color scratch contains only Java primitive fields.
         */
        return create();
    }

    /**
     * Returns the r color component.
     * @return floating-point component
     */
    public float r() { return r; }

    /**
     * Replaces the r color component.
     * @param value copied component
     * @return this color
     */
    public NVGColor r(float value) { r = value; return this; }

    /**
     * Returns the g color component.
     * @return floating-point component
     */
    public float g() { return g; }

    /**
     * Replaces the g color component.
     * @param value copied component
     * @return this color
     */
    public NVGColor g(float value) { g = value; return this; }

    /**
     * Returns the b color component.
     * @return floating-point component
     */
    public float b() { return b; }

    /**
     * Replaces the b color component.
     * @param value copied component
     * @return this color
     */
    public NVGColor b(float value) { b = value; return this; }

    /**
     * Returns the a color component.
     * @return floating-point component
     */
    public float a() { return a; }

    /**
     * Replaces the a color component.
     * @param value copied component
     * @return this color
     */
    public NVGColor a(float value) { a = value; return this; }

    /**
     * Copies another color's components.
     * @param source source color
     * @return this color
     */
    public NVGColor set(NVGColor source) { r = source.r; g = source.g; b = source.b; a = source.a; return this; }

    /**
     * Completes scratch ownership; this color has no native allocation.
     */
    public void free() {
        // Component storage is Java-managed and does not reference native memory.
    }

    @Override
    public void close() { free(); }
}
