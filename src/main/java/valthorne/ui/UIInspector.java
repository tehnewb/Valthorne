package valthorne.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Collects opt-in inspection data from nodes dispatched during a UI root draw.
 * Entries describe translated layout bounds, the effective batch clip, focus
 * and pointer-capture ownership, and resolved style values at recording time.
 * Recording follows actual dispatch order rather than traversing the tree again.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * UIInspector inspector = root.getInspector();
 * inspector.setEnabled(true);
 * inspector.setOutlines(true);
 * root.draw();
 * for (UIInspector.Entry entry : inspector.entries()) {
 *     System.out.println(entry.type() + " " + entry.bounds());
 * }
 * }</pre>
 *
 * <h2>Coordinates and Lifetime</h2>
 * <p>Rectangles use top-left render-space layout units, with accumulated batch
 * translation reflected in node bounds. Camera projection and zoom are not
 * baked into these values. A clip describes the current scissor state rather
 * than the intersection of that scissor with an individual node's bounds.</p>
 *
 * <p>The root clears entries at the start of each draw. Visible nodes are recorded
 * before their callbacks execute, so repeated draws create repeated entries and
 * a callback failure can leave a partial frame's data. Disabled inspection skips
 * recording entirely; disabling also clears retained entries immediately.</p>
 *
 * <p>List snapshots preserve membership, but retain live node references and
 * shallow style values. Avoid keeping them longer than needed, especially after
 * nodes are removed or disposed. Use this unsynchronized inspector on the UI's
 * drawing thread. Outlines require both inspection to be enabled and an available
 * NanoVG context in the root's render context.</p>
 *
 * @author Albert Beaupre
 * @see UIRoot#getInspector()
 */
public final class UIInspector {
    private final List<Entry> entries = new ArrayList<>(); // Current draw's observations in dispatch order.
    private boolean enabled, outlines; // Recording enablement and the independently retained outline preference.

    /**
     * Reports whether node dispatches are currently eligible for recording.
     * This flag starts false and does not imply that any entries have been collected.
     *
     * @return whether inspection recording is enabled
     */
    public boolean isEnabled() {return enabled;}

    /**
     * Enables or disables collection. Disabling immediately clears current entries;
     * enabling leaves the collection as it is and does not retroactively inspect
     * previously drawn nodes. The stored outline preference is preserved.
     *
     * @param enabled whether subsequent dispatches should be recorded
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) entries.clear();
    }

    /**
     * Returns the effective outline setting, requiring both recording and the
     * outline preference. This does not test whether a native drawing context exists.
     *
     * @return true when inspection and outline drawing are both requested
     */
    public boolean isOutlines() {return enabled && outlines;}

    /**
     * Changes the retained outline preference without enabling recording or
     * modifying existing entries. Setting true while disabled takes effect when
     * inspection is subsequently enabled and the root draws its inspection layer.
     *
     * @param outlines whether recorded bounds should be outlined during root drawing
     */
    public void setOutlines(boolean outlines) {this.outlines = outlines;}

    /**
     * Returns an unmodifiable snapshot of the current entry sequence. Later
     * recording or clearing does not change that sequence. Entry objects, node
     * references and nested style values are not deep-copied.
     *
     * @return a list snapshot in node-dispatch order, possibly empty
     */
    public List<Entry> entries() {return List.copyOf(entries);}

    /**
     * Clears observations at the beginning of a root draw, preserving recording
     * and outline settings. Previously returned list snapshots remain intact.
     */
    void beginFrame() {entries.clear();}

    /**
     * Appends an observation for a dispatched node when inspection is enabled.
     * Uses absolute layout position plus batch translation for bounds, converts
     * the effective batch clip to top-left coordinates, and snapshots resolved
     * styles by key name. A missing style produces an empty map.
     *
     * <p>This method does not test visibility, perform hit testing, or draw
     * outlines; its caller determines which dispatches are worth recording.
     * Focus and capture flags compare the root's current owners by identity.</p>
     *
     * @param node the visible node about to be drawn by the caller
     * @param root the owning root supplying batch state, focus and capture
     * @throws NullPointerException if inspection is enabled and node or root is null
     */
    void record(UINode node, UIRoot root) {
        if (!enabled) return;
        var batch = root.getBatch();
        var style = node.getStyle();
        entries.add(new Entry(node, node.getClass().getSimpleName(), new Bounds(node.getAbsoluteX() + batch.getTranslationX(), node.getAbsoluteY() - batch.getTranslationY(), node.getWidth(), node.getHeight()), batch.isClipEnabled() ? new Bounds(batch.getClipX(), root.getRenderSpaceHeight() - batch.getClipY() - batch.getClipHeight(), batch.getClipWidth(), batch.getClipHeight()) : null, root.getFocused() == node, root.getCaptured() == node, style == null ? Map.of() : style.asMap().snapshot()));
    }

    /**
     * Immutable rectangle captured in top-left render-space layout units.
     * Values describe a node box or a scissor rectangle before camera projection.
     * The record stores supplied values without normalization or validation.
     *
     * @param x      the left edge in layout units
     * @param y      the top edge in layout units
     * @param width  the captured horizontal extent
     * @param height the captured vertical extent
     * @author Albert Beaupre
     */
    public record Bounds(float x, float y, float width, float height) {
    }

    /**
     * Captures one node-dispatch observation. Rectangle and boolean values are
     * snapshots; the node remains a live reference. Internally recorded style maps
     * are unmodifiable shallow snapshots whose contained values can still be mutable.
     * Constructing an entry directly performs no copying or null validation.
     *
     * @param node     the live node observed before its draw callback
     * @param type     the node class's simple name, which can be empty for anonymous classes
     * @param bounds   the translated node rectangle in top-left layout coordinates
     * @param clip     the effective scissor rectangle, or null when clipping is disabled
     * @param focused  whether this node owned keyboard focus when recorded
     * @param captured whether this node owned pointer capture when recorded
     * @param style    the resolved style values indexed by registered key name
     * @author Albert Beaupre
     */
    public record Entry(UINode node, String type, Bounds bounds, Bounds clip, boolean focused, boolean captured, Map<String, Object> style) {
    }
}
