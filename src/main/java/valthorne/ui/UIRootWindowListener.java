package valthorne.ui;

import valthorne.event.events.*;
import valthorne.event.listeners.WindowResizeListener;

import static org.lwjgl.nanovg.NanoVG.nvgBeginFrame;
import static org.lwjgl.nanovg.NanoVG.nvgCreateFont;
import static org.lwjgl.nanovg.NanoVGGL3.NVG_ANTIALIAS;
import static org.lwjgl.nanovg.NanoVGGL3.NVG_STENCIL_STROKES;
import static org.lwjgl.nanovg.NanoVGGL3.nvgCreate;
import static org.lwjgl.nanovg.NanoVGGL3.nvgDelete;

/**
 * Persistent resize adapter updating the root and optional viewport when the
 * window dimensions change. Unregistered with the root lifecycle.
 * <p>The resize event is forwarded to the root's sizing policy instead of directly mutating
 * individual children. Layout and viewport decisions remain centralized in the owning
 * root, and this listener allocates no window resources.</p>
 *
 * @author Albert Beaupre
 */
final class UIRootWindowListener implements WindowResizeListener {
    private final UIRoot owner; // Borrowed control coordinating this helper's behavior.

    /**
     * Retains the control whose state this helper presents or routes.
     *
     * @param owner borrowed control; never disposed by this helper
     */
    UIRootWindowListener(UIRoot owner) {
        this.owner = owner;
    }

    /**
     * <p>
     * Forwards a window resize event to {@link UIRoot#handleWindowResized(WindowResizeEvent)}.
     * </p>
     *
     * @param event the resize event
     */
    @Override
    public void windowResized(WindowResizeEvent event) {
        owner.handleWindowResized(event);
    }
}
