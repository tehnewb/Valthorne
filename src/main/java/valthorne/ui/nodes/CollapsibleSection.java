package valthorne.ui.nodes;

import valthorne.Keyboard;
import valthorne.event.events.KeyPressEvent;
import valthorne.ui.UINode;
import valthorne.ui.behavior.ChangeSignal;
import java.util.Objects;

/**
 * Retained disclosure widget with a fixed header and one content subtree.
 * Starts expanded; collapsing hides the body, assigns it zero height, and skips
 * its updates. Content can use either renderer. Focus or pointer capture within
 * a collapsing body is moved/cancelled before the body becomes hidden.
 * The header remains available for pointer activation and Left/Right navigation.
 *
 * @author Albert Beaupre
 */
public class CollapsibleSection extends Panel {
    private final String title; // Persistent title used when rebuilding the header's disclosure text.
    private final Button header; // Owned toggle button retained while the body is collapsed.
    private final Panel body = new Panel(); // Owned container for the supplied content subtree.
    private final ChangeSignal changes = new ChangeSignal(); // Synchronous expansion-change subscriptions.
    private boolean expanded = true; // Whether body visibility, automatic height, and updates are enabled.

    /**
     * Builds an expanded vertical section with a 36-unit header and full-width
     * content. The supplied node becomes a child of the internal body, and its
     * layout width is set to 100 percent.
     *
     * @param title non-null persistent header text
     * @param content non-null unattached content node
     * @throws NullPointerException if title or content is null
     * @throws IllegalArgumentException if content already has a parent
     */
    public CollapsibleSection(String title, UINode content) {
        this.title = Objects.requireNonNull(title);
        Objects.requireNonNull(content);
        if (content.getParent() != null) throw new IllegalArgumentException("Content already has a parent");
        header = new Button("[-] " + title) {
            /**
             * Collapses on Left and expands on Right, consuming either key even if the
             * state was already requested. Disabled headers ignore keys; other keys use
             * normal Button handling.
             *
             * @param event key press delivered to the header
             */
            @Override public void onKeyPress(KeyPressEvent event) {
                if (isDisabled()) return;
                if (event.getKey() == Keyboard.LEFT || event.getKey() == Keyboard.RIGHT) {
                    expanded(event.getKey() == Keyboard.RIGHT); event.consume();
                } else super.onKeyPress(event);
            }
        }.action(b -> expanded(!expanded));
        getLayout().column().noShrink();
        header.getLayout().height(36).widthPercent(100).noShrink();
        body.getLayout().widthPercent(100).minHeight(0).noShrink();
        content.getLayout().widthPercent(100); body.add(content);
        super.add(header); super.add(body);
    }
    /**
     * Reads the requested disclosure state, which also controls body update calls.
     *
     * @return true while the body is expanded
     */
    public boolean isExpanded() { return expanded; }
    /**
     * Exposes the live header for focus, styling, or application configuration.
     * Its text is rewritten by later disclosure changes to include the state marker.
     *
     * @return internally owned header button
     */
    public Button getHeader() { return header; }
    /**
     * Subscribes synchronously to actual expansion changes. No event is emitted
     * when setting the current state; the listener sees updated visibility and text.
     *
     * @param listener non-null callback
     * @return handle whose close removes this subscription
     * @throws NullPointerException if listener is null
     */
    public AutoCloseable onChange(Runnable listener) { return changes.subscribe(listener); }
    /**
     * Applies a new disclosure state and fires change listeners after visibility,
     * height, and header text are updated. On collapse, focus within the body moves
     * to the header; body capture triggers root input cancellation first. Repeating
     * the existing value has no effect and emits no event.
     *
     * @param value true to show the body, false to collapse it
     * @return this section
     */
    public CollapsibleSection expanded(boolean value) {
        if (value == expanded) return this;
        if (!value && getRoot() != null) {
            boolean focus = contains(getRoot().getFocused());
            if (contains(getRoot().getCaptured())) { getRoot().cancelInput(); focus = true; }
            if (focus) getRoot().setFocusTo(header);
        }
        expanded = value;
        body.setVisible(value);
        if (value) body.getLayout().heightAuto(); else body.getLayout().height(0);
        header.text((value ? "[-] " : "[+] ") + title);
        changes.fire(); return this;
    }
    /**
     * Walks parent links to determine whether a node is the internal body or one
     * of its descendants. Null and nodes outside the body return false.
     *
     * @param node possible focus or capture owner
     * @return true for membership in the body subtree
     */
    private boolean contains(UINode node) {
        for (; node != null; node = node.getParent()) if (node == body) return true;
        return false;
    }
    /**
     * Updates the header every call and the body only while expanded. Does not
     * invoke the superclass traversal, so arbitrary extra structural children are
     * not part of this update path.
     *
     * @param delta elapsed update seconds
     */
    @Override public void update(float delta) { header.update(delta); if (expanded) body.update(delta); }
}
