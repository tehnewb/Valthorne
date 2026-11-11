package org.lwjgl.nanovg;

import valthorne.browser.BrowserNative;

import static valthorne.browser.BrowserOperation.*;

/**
 * Owns native NanoVG bgfx contexts sharing the engine's WebGL device and views.
 */
public final class NanoVGBGFX {
    /**
     * Prevents constructing static vector-context bindings.
     */
    private NanoVGBGFX() {
    }

    /**
     * Creates a context on the active browser bgfx device.
     * @param antialias whether to enable edge antialiasing
     * @param view painter-ordered bgfx view
     * @param allocator reserved native allocator, which must be zero
     * @return owned native context, or zero on failure
     */
    public static long nvgCreate(boolean antialias, int view, long allocator) {
        /*
         * The module supplies its own allocator and shares the existing graphics device.
         */
        if (allocator != 0) throw new UnsupportedOperationException("External native allocators are unavailable in the browser");
        BrowserNative.word(0, antialias ? 1 : 0);
        BrowserNative.word(1, view);
        return Integer.toUnsignedLong(BrowserNative.call(NVG_CREATE));
    }

    /**
     * Releases a context and its owned font and image resources.
     * @param context owned native context
     */
    public static void nvgDelete(long context) {
        /*
         * Context teardown precedes the device lifecycle in the resource registry.
         */
        BrowserNative.word(0, (int) context);
        BrowserNative.call(NVG_DELETE);
    }

    /**
     * Selects the next painter-ordered engine view for vector submissions.
     * @param context active native context
     * @param view bgfx view identifier
     */
    public static void nvgSetViewId(long context, int view) {
        /*
         * Vector draws retain the same pass ordering as the desktop NanoVG backend.
         */
        BrowserNative.word(0, (int) context);
        BrowserNative.word(1, view);
        BrowserNative.call(NVG_VIEW);
    }
}
