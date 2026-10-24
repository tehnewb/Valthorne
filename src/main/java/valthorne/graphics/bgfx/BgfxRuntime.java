package valthorne.graphics.bgfx;

/**
 * Prevents competing device wrappers from initializing bgfx's
 * process-wide runtime simultaneously. Acquisition and release are confined to
 * the application render thread, just like both rendering paths.
 */
final class BgfxRuntime {
    /**
     * Current runtime owner, or null before acquisition and after release.
     */
    private static Object owner;

    /**
     * Prevents constructing a runtime ownership registry.
     */
    private BgfxRuntime() {
    }

    /**
     * Reserves the native singleton before any initialization calls.
     *
     * @param candidate renderer taking ownership
     * @throws IllegalStateException if a renderer already owns bgfx
     */
    static void claim(Object candidate) {
        /*
         * A shared guard covers both integrations without querying native state
         * or adding synchronization to thread-confined rendering.
         */
        if (owner != null) throw new IllegalStateException("Another Valthorne renderer already owns bgfx");
        owner = candidate;
    }

    /**
     * Releases a reservation after native shutdown or failed initialization.
     *
     * @param candidate renderer relinquishing ownership
     */
    static void release(Object candidate) {
        /*
         * Failed constructors must never release another renderer's reservation.
         */
        if (owner == candidate) owner = null;
    }
}
