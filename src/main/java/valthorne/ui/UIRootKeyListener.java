package valthorne.ui;

import valthorne.Keyboard;
import valthorne.event.events.*;
import valthorne.event.listeners.KeyListener;

import static org.lwjgl.nanovg.NanoVG.nvgBeginFrame;
import static org.lwjgl.nanovg.NanoVG.nvgCreateFont;
import static org.lwjgl.nanovg.NanoVGGL3.NVG_ANTIALIAS;
import static org.lwjgl.nanovg.NanoVGGL3.NVG_STENCIL_STROKES;
import static org.lwjgl.nanovg.NanoVGGL3.nvgCreate;
import static org.lwjgl.nanovg.NanoVGGL3.nvgDelete;

/**
 * Persistent global keyboard adapter owned by this root. Forwards press and
 * release events into scoped root routing and is unregistered during disposal.
 * <p>The adapter retains its enclosing root and forwards the original event objects, allowing
 * consumption to remain visible to subsequent routing. Keyboard policy stays in the
 * root handlers rather than in the adapter.</p>
 *
 * @author Albert Beaupre
 */
final class UIRootKeyListener implements KeyListener {
    private final UIRoot owner; // Borrowed control coordinating this helper's behavior.

    /**
     * Retains the control whose state this helper presents or routes.
     *
     * @param owner borrowed control; never disposed by this helper
     */
    UIRootKeyListener(UIRoot owner) {
        this.owner = owner;
    }

    /**
     * <p>
     * Forwards a key press event to {@link UIRoot#handleKeyPressed(KeyPressEvent)}.
     * </p>
     *
     * @param event the key press event
     */
    @Override
    public void keyPressed(KeyPressEvent event) {
        boolean active = owner.blockLowerInput && owner.getFocused() != null;
        owner.handleKeyPressed(event);
        if (active || owner.blockLowerInput && owner.getFocused() != null) event.consume();
    }

    /**
     * <p>
     * Forwards a key release event to {@link UIRoot#handleKeyReleased(KeyReleaseEvent)}.
     * </p>
     *
     * @param event the key release event
     */
    @Override
    public void keyReleased(KeyReleaseEvent event) {
        boolean active = owner.blockLowerInput && owner.getFocused() != null;
        owner.handleKeyReleased(event);
        if (active) event.consume();
    }
}
