package valthorne.ui.nodes;

import valthorne.Keyboard;
import valthorne.event.events.KeyPressEvent;
import valthorne.ui.UINode;
import valthorne.ui.behavior.ChangeSignal;
import java.util.ArrayList;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Retains lazily constructed tab pages with headers that support pointer and keyboard
 * selection. Pages may use either rendering backend. Only the selected page is visible
 * and updated; instantiated inactive pages retain state until removed.
 *
 * <pre>{@code
 * TabbedPane tabs = new TabbedPane();
 * tabs.addTab("Overview", () -> new Panel());
 * tabs.addTab("Details", () -> new Panel());
 * tabs.select(1);
 * }</pre>
 *
 * <p>Use addTab and removeTab to maintain the header/page bookkeeping, rather than
 * inherited structural methods. Factories must return fresh unattached nodes. The
 * first added tab is selected immediately, which invokes its factory during addTab.
 * Changes and factories run synchronously on the UI thread. Selection moves focus to
 * the next header when necessary and cancels capture held by the outgoing page.</p>
 *
 * @author Albert Beaupre
 */
public class TabbedPane extends Panel {
    private final Panel headers = new Panel(); // Owned horizontal strip of tab buttons.
    private final Panel deck = new Panel(); // Owned container retaining every instantiated page.
    private final ArrayList<Tab> tabs = new ArrayList<>(); // Ordered tab metadata matching header order.
    private final ChangeSignal changes = new ChangeSignal(); // Synchronous selection and removal notifications.
    private int selected = -1; // Active tab index, or -1 when no tab is selected.

    /**
     * Creates a vertical tab layout with an eight-unit gap, a 36-unit header strip,
     * and a growing page deck. No tab or page is initially selected.
     */
    public TabbedPane() {
        getLayout().column().gap(8);
        headers.getLayout().row().gap(4).height(36).noShrink();
        deck.getLayout().height(0).minHeight(0).grow();
        super.add(headers); super.add(deck);
    }
    /**
     * Returns the number of registered tabs, including tabs whose pages have not
     * yet been instantiated.
     *
     * @return current tab count
     */
    public int getTabCount() { return tabs.size(); }
    /**
     * Returns the selected tab's current index. Removing an earlier tab can change
     * this index while leaving the same page selected.
     *
     * @return selected index, or -1 when no tab is selected
     */
    public int getSelectedIndex() { return selected; }
    /**
     * Returns an existing page without invoking its factory. The returned node belongs
     * to the deck; callers should not reparent it independently.
     *
     * @param index zero-based tab index
     * @return live page, or null if never successfully selected
     * @throws IndexOutOfBoundsException if index is outside the tab list
     */
    public UINode getPage(int index) { return tabs.get(index).page; }
    /**
     * Returns the live header for label, style, or disabled-state customization.
     * Ownership stays with the header strip.
     *
     * @param index zero-based tab index
     * @return tab header button
     * @throws IndexOutOfBoundsException if index is outside the tab list
     */
    public Button getHeader(int index) { return tabs.get(index).button; }
    /**
     * Subscribes to synchronous selection and removal notifications. Registration does
     * not immediately report the current selection; close the subscription to detach.
     *
     * @param listener callback invoked after state changes
     * @return subscription handle
     */
    public AutoCloseable onChange(Runnable listener) { return changes.subscribe(listener); }
    /**
     * Adds a header and retains its page factory. If no tab is selected, selects index
     * zero immediately. Other page factories are deferred until selection; failures
     * propagate, and adding a tab is not rolled back if automatic selection fails.
     *
     * @param title header label
     * @param factory supplier of a fresh unattached page
     * @return this pane
     * @throws NullPointerException if title or factory is null
     */
    public TabbedPane addTab(String title, Supplier<? extends UINode> factory) {
        Tab tab = new Tab(Objects.requireNonNull(title), Objects.requireNonNull(factory));
        tabs.add(tab); headers.add(tab.button);
        if (selected < 0) select(0);
        return this;
    }
    /**
     * Selects an enabled tab, creating and attaching its page if needed. The page is
     * absolutely sized to fill the deck. A disabled or already-selected tab is ignored.
     * Hides the previous page, updates selected header state, cancels outgoing page
     * capture if present, and notifies listeners. Factory failures propagate before the
     * selection changes; a later retry may invoke the factory again.
     *
     * @param index zero-based tab to select
     * @throws IndexOutOfBoundsException if index is invalid
     * @throws NullPointerException if the factory returns null
     * @throws IllegalArgumentException if the page is attached or is this pane
     */
    public void select(int index) {
        Tab next = tabs.get(index);
        if (next.button.isDisabled() || index == selected) return;
        if (next.page == null) {
            UINode page = Objects.requireNonNull(next.factory.get(), "Page factory returned null");
            if (page.getParent() != null || page == this) throw new IllegalArgumentException("Page must be fresh and unattached");
            page.getLayout().absolute().left(0).top(0).widthPercent(100).heightPercent(100);
            deck.add(page); next.page = page;
        }
        Tab previous = selected < 0 ? null : tabs.get(selected);
        boolean moveFocus = previous != null && getRoot() != null && contains(previous.page, getRoot().getFocused());
        if (previous != null) {
            if (getRoot() != null && contains(previous.page, getRoot().getCaptured())) {
                getRoot().cancelInput(); moveFocus = true;
            }
            previous.page.setVisible(false); previous.button.setSelected(false);
        }
        selected = index;
        next.page.setVisible(true); next.button.setSelected(true);
        if (moveFocus) getRoot().setFocusTo(next.button);
        changes.fire();
    }
    /**
     * Removes a header and any instantiated page through normal node removal. If the
     * active tab is removed, searches cyclically from a nearby index for an enabled
     * replacement; otherwise adjusts the selected index as needed. Fires a change
     * notification and restores header focus when focus belonged to removed content.
     *
     * @param index zero-based tab to remove
     * @throws IndexOutOfBoundsException if index is invalid
     */
    public void removeTab(int index) {
        Tab removed = tabs.get(index);
        boolean active = selected == index;
        boolean hadFocus = getRoot() != null && (getRoot().getFocused() == removed.button || contains(removed.page, getRoot().getFocused()));
        tabs.remove(index);
        headers.remove(removed.button);
        if (removed.page != null) deck.remove(removed.page);
        if (active) {
            selected = -1;
            int candidate = Math.min(index, tabs.size() - 1);
            for (int i = 0; i < tabs.size(); i++) {
                int next = (candidate + i) % tabs.size();
                if (!tabs.get(next).button.isDisabled()) { select(next); break; }
            }
            if (selected < 0) changes.fire();
        } else {
            if (selected > index) selected--;
            changes.fire();
        }
        if (hadFocus && getRoot() != null && selected >= 0) getRoot().setFocusTo(tabs.get(selected).button);
    }
    /**
     * Updates the header strip and only the selected page. Inactive pages retain state
     * without receiving updates; the deck's generic update traversal is bypassed.
     *
     * @param delta elapsed time in seconds
     */
    @Override public void update(float delta) {
        headers.update(delta);
        if (selected >= 0) tabs.get(selected).page.update(delta);
    }
    /**
     * Finds the next enabled header for Left, Right, Home, or End, wrapping when needed.
     * Selects that tab and requests header focus when attached to a root. If every
     * header is disabled, leaves selection and focus unchanged.
     *
     * @param from header's owning tab
     * @param key supported navigation key
     */
    private void navigate(Tab from, int key) {
        int direction = key == Keyboard.LEFT || key == Keyboard.END ? -1 : 1;
        int index = key == Keyboard.HOME ? -1 : key == Keyboard.END ? tabs.size() : tabs.indexOf(from);
        for (int i = 0; i < tabs.size(); i++) {
            index = Math.floorMod(index + direction, tabs.size());
            if (tabs.get(index).button.isDisabled()) continue;
            select(index);
            if (getRoot() != null) getRoot().setFocusTo(tabs.get(index).button);
            return;
        }
    }
    /**
     * Walks the node's parent chain to test containment, including identity with the
     * parent itself. Null parent or node yields false.
     *
     * @param parent candidate ancestor
     * @param node candidate descendant
     * @return whether node belongs to parent's subtree
     */
    private static boolean contains(UINode parent, UINode node) {
        if (parent == null) return false;
        for (; node != null; node = node.getParent()) if (node == parent) return true;
        return false;
    }
    /**
     * Retains one header, its deferred factory, and its instantiated page reference.
     * The enclosing pane controls attachment, selection, and disposal through its
     * header and deck containers.
     *
     * @author Albert Beaupre
     */
    private final class Tab {
        final Supplier<? extends UINode> factory; // Retained factory used when this tab is first selected.
        final Button button; // Owned header button used to select this tab.
        UINode page; // Lazily attached page, or null before successful creation.
        /**
         * Creates a header whose activation selects this tab and whose navigation keys
         * are handled by the enclosing pane. Page construction remains deferred.
         *
         * @param title header label
         * @param factory retained page supplier
         */
        Tab(String title, Supplier<? extends UINode> factory) {
            this.factory = factory;
            button = new Button(title) {
                /**
                 * Handles enabled header navigation using Left, Right, Home, and End, consuming
                 * those events. Other keys use normal button activation; disabled headers do nothing.
                 *
                 * @param event routed header key press
                 */
                @Override public void onKeyPress(KeyPressEvent event) {
                    if (isDisabled()) return;
                    int key = event.getKey();
                    if (key == Keyboard.LEFT || key == Keyboard.RIGHT || key == Keyboard.HOME || key == Keyboard.END) {
                        navigate(Tab.this, key); event.consume();
                    } else super.onKeyPress(event);
                }
            }.action(b -> select(tabs.indexOf(this)));
            button.getLayout().padding(8).minWidth(80).heightPercent(100);
        }
    }
}
