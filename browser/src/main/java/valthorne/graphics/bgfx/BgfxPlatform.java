package valthorne.graphics.bgfx;

import valthorne.graphics.GraphicsBackend;

/**
 * Validates explicit browser backend selection without desktop fallback.
 */
final class BgfxPlatform {
    /**
     * Prevents creating platform validators.
     */
    private BgfxPlatform() {
    }

    /**
     * Rejects APIs unavailable to the browser canvas runtime.
     * @param backend explicitly selected graphics backend
     */
    static void validate(GraphicsBackend backend) {
        /*
         * Browser WebGL is a distinct platform target, not desktop OpenGL ES.
         */
        if (backend == null) throw new NullPointerException("backend");
        if (backend != GraphicsBackend.WEBGL) throw new UnsupportedOperationException("The browser runtime requires WEBGL; desktop APIs require the desktop runtime");
    }
}
