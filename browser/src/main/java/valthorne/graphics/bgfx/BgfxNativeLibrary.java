package valthorne.graphics.bgfx;

/**
 * Keeps shared renderer initialization independent of desktop native loading.
 * The browser host owns its WebAssembly module and has no DLL selection policy.
 */
final class BgfxNativeLibrary {
    /**
     * Prevents constructing the browser's native loading boundary.
     */
    private BgfxNativeLibrary() {
    }

    /**
     * Leaves native module selection with the initialized browser host.
     */
    static void configure() {
        /*
         * Shared shape renderer initialization may precede canvas creation.
         * Desktop library configuration must never enter the browser bundle.
         */
    }
}
