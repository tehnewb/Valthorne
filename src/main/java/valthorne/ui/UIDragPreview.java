package valthorne.ui;

import org.joml.Vector2f;

/**
 * Keeps one dragged UI node visually attached to the pointer until its drag ends.
 *
 * <p>The node remains owned and laid out by its original container. Normal tree
 * traversal omits that node while a root draw paints it again above overlays,
 * outside any scroll-panel clip. Only the pointer position changes during a
 * drag, so no Yoga layout work or temporary node is needed per movement.</p>
 *
 * <p>The root owns this state and clears it when input is cancelled or when the
 * initiating list or tree completes the drag. Callers must update it on the UI
 * thread with screen-space pointer coordinates.</p>
 */
final class UIDragPreview {
    private UINode source; // Node temporarily painted above the normal UI tree.
    private float grabX; // Horizontal pointer offset within the source at press time.
    private float grabY; // Vertical pointer offset within the source at press time.
    private float x; // Desired top-left X of the lifted node in root layout space.
    private float y; // Desired top-left Y of the lifted node in root layout space.
    private boolean painting; // Allows the source through normal render dispatch for its preview pass.

    /** Records the pointer's original grab point before the node moves visually. */
    void begin(UIRoot root, UINode node, float pressX, float pressY) {
        if (node == null || node.getRoot() != root) return;
        Vector2f local = node.screenToLayout(pressX, pressY);
        source = node;
        grabX = local.x() - node.getAbsoluteX();
        grabY = local.y() - node.getAbsoluteY();
        move(root, pressX, pressY);
    }

    /**
     * Moves the preview without changing layout or item order. The common
     * window-space path uses primitive coordinates to avoid per-drag allocation;
     * viewport-backed roots use their coordinate conversion API.
     */
    void move(UIRoot root, float pointerX, float pointerY) {
        if (source == null) return;
        if (root.getViewport() == null) {
            x = pointerX - grabX;
            y = root.getRenderSpaceHeight() - pointerY - grabY;
            return;
        }
        Vector2f pointer = root.screenToLayout(pointerX, pointerY);
        x = pointer.x() - grabX;
        y = pointer.y() - grabY;
    }

    /** Clears the preview only when the caller still owns its source node. */
    void end(UINode node) {
        if (source == node) source = null;
    }

    /** Clears a preview abandoned by pointer or window focus cancellation. */
    void clear() { source = null; }

    /** Indicates that normal child traversal should leave a gap for the lifted node. */
    boolean skips(UINode node) { return source == node && !painting; }

    /** Returns whether the current source belongs to a subtree being detached. */
    boolean belongsTo(UINode ancestor) {
        for (UINode node = source; node != null; node = node.getParent())
            if (node == ancestor) return true;
        return false;
    }

    /**
     * Paints the existing node once at its translated pointer position. The
     * preview has no backdrop, and its content is slightly translucent so the
     * destination remains visible through the carried item. Translation and
     * opacity are restored even when a widget's paint callback fails.
     */
    void draw(UIRenderContext context) {
        UINode node = source;
        if (node == null || node.getRoot() == null || !node.isVisible()) return;
        var batch = context.getBatch();
        batch.pushTranslation(x - node.getAbsoluteX(), node.getAbsoluteY() - y);
        painting = true;
        try {
            context.drawDragPreview(node);
        } finally {
            painting = false;
            batch.popTranslation();
        }
    }
}
