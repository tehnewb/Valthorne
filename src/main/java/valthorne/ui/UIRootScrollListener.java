package valthorne.ui;

import valthorne.Mouse;
import valthorne.event.events.*;
import valthorne.event.listeners.MouseScrollListener;

import static org.lwjgl.nanovg.NanoVG.nvgBeginFrame;
import static org.lwjgl.nanovg.NanoVG.nvgCreateFont;
import static org.lwjgl.nanovg.NanoVGGL3.NVG_ANTIALIAS;
import static org.lwjgl.nanovg.NanoVGGL3.NVG_STENCIL_STROKES;
import static org.lwjgl.nanovg.NanoVGGL3.nvgCreate;
import static org.lwjgl.nanovg.NanoVGGL3.nvgDelete;

/**
 * Persistent global scroll adapter forwarding events into the owning root's
 * hit testing and bubbling path.
 * <p>It forwards the same event object so a consuming scroll target can stop further
 * propagation. Scroll offsets belong to controls, while this adapter only connects
 * global delivery to root routing.</p>
 *
 * @author Albert Beaupre
 */
final class UIRootScrollListener implements MouseScrollListener {
    private final UIRoot owner; // Borrowed control coordinating this helper's behavior.

    /**
     * Retains the control whose state this helper presents or routes.
     *
     * @param owner borrowed control; never disposed by this helper
     */
    UIRootScrollListener(UIRoot owner) {
        this.owner = owner;
    }

    /**
     * <p>
     * Forwards a mouse scroll event to {@link UIRoot#handleMouseScrolled(MouseScrollEvent)}.
     * </p>
     *
     * @param event the mouse scroll event
     */
    @Override
    public void mouseScrolled(MouseScrollEvent event) {
        boolean onUi = owner.blockLowerInput && owner.hasUiAt(Mouse.getX(), Mouse.getY());
        owner.handleMouseScrolled(event);
        if (onUi) event.consume();
    }
}
