package org.lwjgl.bgfx;

import valthorne.browser.BrowserNative;

import static valthorne.browser.BrowserOperation.CAPABILITIES;

/**
 * Immutable browser device capabilities, refreshed after graphics initialization.
 * The native report determines feature support rather than the backend name.
 */
public final class BGFXCaps {
    /**
     * Most recently captured capability report; cleared during native shutdown.
     */
    private static BGFXCaps current;

    private final long supported; // Native feature mask.
    private final BGFXCapsLimits limits; // Immutable native texture limits.
    private final boolean homogeneousDepth; // Whether clip depth spans minus one through one.
    private final boolean originBottomLeft; // Whether render targets use a bottom-left origin.
    private final int rendererType; // Native bgfx renderer identifier.
    private final short vendor; // Native device vendor identifier.

    /**
     * Captures a newly initialized browser device's immutable report.
     */
    private BGFXCaps() {
        BrowserNative.call(CAPABILITIES);
        supported = Integer.toUnsignedLong(BrowserNative.word(0)) | (Integer.toUnsignedLong(BrowserNative.word(1)) << 32);
        limits = new BGFXCapsLimits(BrowserNative.word(2), BrowserNative.word(3));
        homogeneousDepth = BrowserNative.word(4) != 0;
        originBottomLeft = BrowserNative.word(5) != 0;
        rendererType = BrowserNative.word(6);
        vendor = (short) BrowserNative.word(7);
    }

    /**
     * Returns the cached current report, creating it after native initialization.
     * @return immutable active-device capabilities
     */
    static BGFXCaps current() {
        /*
         * The device is single-threaded, so immutable reports need no synchronization.
         */
        if (current == null) current = new BGFXCaps();
        return current;
    }

    /**
     * Invalidates the report before restoring a browser context.
     */
    public static void invalidate() {
        /*
         * A restored context may expose different limits or optional extensions.
         */
        current = null;
    }

    /**
     * Returns supported native graphics features.
     * @return bgfx feature mask
     */
    public long supported() { return supported; }

    /**
     * Returns immutable native allocation limits.
     * @return texture limits
     */
    public BGFXCapsLimits limits() { return limits; }

    /**
     * Reports the native clip-depth convention.
     * @return whether depth spans minus one through one
     */
    public boolean homogeneousDepth() { return homogeneousDepth; }

    /**
     * Reports native render-target orientation.
     * @return whether the origin is bottom-left
     */
    public boolean originBottomLeft() { return originBottomLeft; }

    /**
     * Returns the native renderer identifier.
     * @return bgfx renderer type
     */
    public int rendererType() { return rendererType; }

    /**
     * Returns the native vendor identifier.
     * @return unsigned vendor code
     */
    public short vendorId() { return vendor; }
}
