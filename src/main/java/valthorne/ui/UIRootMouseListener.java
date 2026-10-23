package valthorne.ui;

import valthorne.event.events.*;
import valthorne.event.listeners.MouseListener;

import static org.lwjgl.nanovg.NanoVG.nvgBeginFrame;
import static org.lwjgl.nanovg.NanoVG.nvgCreateFont;
import static org.lwjgl.nanovg.NanoVGGL3.NVG_ANTIALIAS;
import static org.lwjgl.nanovg.NanoVGGL3.NVG_STENCIL_STROKES;
import static org.lwjgl.nanovg.NanoVGGL3.nvgCreate;
import static org.lwjgl.nanovg.NanoVGGL3.nvgDelete;

/**
 * Persistent pointer adapter forwarding presses, releases, drags, and movement
 * to the owning root's capture and hover logic.
 * <p>The original event object is passed through so capture, hit testing, and consumption
 * share one routing decision. Registration and removal follow the enclosing root's
 * lifecycle; the adapter owns no native cursor resources.</p>
 *
 * @author Albert Beaupre
 */
final class UIRootMouseListener implements MouseListener {
    private final UIRoot owner; // Borrowed control coordinating this helper's behavior.

    /**
     * Retains the control whose state this helper presents or routes.
     *
     * @param owner borrowed control; never disposed by this helper
     */
    UIRootMouseListener(UIRoot owner) {
        this.owner = owner;
    }

    /**
     * <p>
     * Forwards a mouse press event to {@link UIRoot#handleMousePressed(MousePressEvent)}.
     * </p>
     *
     * @param event the mouse press event
     */
    @Override
    public void mousePressed(MousePressEvent event) {
        boolean onUi = owner.blockLowerInput && owner.hasUiAt(event.getX(), event.getY());
        owner.handleMousePressed(event);
        if (onUi) {
            owner.pointerOwned = true;
            event.consume();
        }
    }

    /**
     * <p>
     * Forwards a mouse release event to {@link UIRoot#handleMouseReleased(MouseReleaseEvent)}.
     * </p>
     *
     * @param event the mouse release event
     */
    @Override
    public void mouseReleased(MouseReleaseEvent event) {
        boolean owned = owner.pointerOwned && event.getButton() == owner.pressedButton;
        if (owner.pointerOwned && owner.pressedButton < 0) owned = true;
        owner.handleMouseReleased(event);
        if (owned) {
            owner.pointerOwned = false;
            event.consume();
        }
    }

    /**
     * <p>
     * Forwards a mouse drag event to {@link UIRoot#handleMouseDragged(MouseDragEvent)}.
     * </p>
     *
     * @param event the mouse drag event
     */
    @Override
    public void mouseDragged(MouseDragEvent event) {
        owner.handleMouseDragged(event);
        if (owner.pointerOwned) event.consume();
    }

    /**
     * <p>
     * Forwards a mouse move event to {@link UIRoot#handleMouseMoved(MouseMoveEvent)}.
     * </p>
     *
     * @param event the mouse move event
     */
    @Override
    public void mouseMoved(MouseMoveEvent event) {
        owner.handleMouseMoved(event);
        if (owner.blockLowerInput && owner.hasUiAt(event.getToX(), event.getToY())) event.consume();
    }
}
