package valthorne.graphics.lighting2d;

import valthorne.graphics.bgfx.BgfxRenderState;

/**
 * Captures the engine's render target, viewport, scissor, program and texture
 * bindings around a lighting capture. Does not own the referenced GPU resources;
 * they must remain alive until restoration on the creating rendering thread.
 */
final class Lighting2DState {
    private final BgfxRenderState state = new BgfxRenderState(); // Reusable captured pass state.

    /**
     * Captures the active engine state immediately without querying a driver.
     */
    Lighting2DState() {
        state.store();
    }

    /**
     * Restores the captured state and starts a separate ordered rendering pass.
     */
    void restore() {
        state.restore();
    }
}
