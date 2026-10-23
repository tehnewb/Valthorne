package valthorne.ui.nodes.nano;

import valthorne.Keyboard;
import valthorne.ui.UINode;
import valthorne.ui.behavior.ChangeSignal;
import valthorne.ui.theme.ResolvedStyle;
import valthorne.ui.theme.StyleKey;

import java.util.ArrayList;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * UI-thread tab container with an owned header strip and a lazy page deck.
 * Page factories run once on first successful selection and must return fresh,
 * unattached nodes. Created pages remain until removal or disposal; switching
 * tabs preserves their state. Applications own resources their pages borrow.
 *
 * <pre>{@code
 * NanoTabbedPane tabs = new NanoTabbedPane();
 * tabs.addTab("Assets", () -> createAssetBrowser());
 * tabs.addTab("Settings", () -> createSettingsPage());
 * root.add(tabs);
 * }</pre>
 */
public class NanoTabbedPane extends NanoPanel {
    /**
     * Height of the tab header strip in UI units.
     */
    public static final StyleKey<Float> HEADER_HEIGHT_KEY = StyleKey.of("nano.tabbedPane.headerHeight", Float.class, 26f);
    private final NanoPanel headers = new NanoPanel(); // Owned container holding the fixed header controls.
    private final NanoPanel deck = new NanoPanel(); // Owned page container displaying the selected tab.
    final ArrayList<NanoPaneTab> tabs = new ArrayList<>(); // Ordered retained tab definitions, headers, and optional pages.
    private final ChangeSignal changes = new ChangeSignal(); // Synchronous listeners notified after user-visible state changes.
    private int selected = -1; // Selected tab index, or minus one while the pane is empty.

    /**
     * Builds the control and its owned children from the supplied configuration.
     */
    public NanoTabbedPane() {
        getLayout()
                .column()
                .gap(0);
        headers.setStyleName("tab-header-strip");
        headers.getLayout()
                .row()
                .gap(1)
                .height(26)
                .noShrink();
        deck.setStyleName("tab-body");
        deck.getLayout()
                .height(0)
                .minHeight(0)
                .grow();
        super.add(headers);
        super.add(deck);
    }

    @Override
    protected void applyLayout() {
        ResolvedStyle style = getStyle();
        Float height = style == null ? null : style.get(HEADER_HEIGHT_KEY);
        if (height != null && Float.isFinite(height) && height > 0) headers.getLayout()
                .height(height);
        super.applyLayout();
    }

    /**
     * Returns the current number of registered tabs without instantiating pages.
     *
     * @return the current number of registered tabs without instantiating pages
     */
    public int getTabCount() {
        return tabs.size();
    }
    /**
     * Returns the active tab index, or minus one when no page is selected.
     *
     * @return the active tab index, or minus one when no page is selected
     */
    public int getSelectedIndex() {
        return selected;
    }
    /**
     * Borrows an instantiated page, or returns null before its first successful selection.
     *
     * @param index zero-based index in the current visible or retained membership
     * @return an instantiated page, or returns null before its first successful selection
     */
    public UINode getPage(int index) {
        return tabs.get(index).page;
    }
    /**
     * Borrows the owned header control for styling or interaction configuration.
     *
     * @param index zero-based index in the current visible or retained membership
     * @return the owned header control for styling or interaction configuration
     */
    public NanoButton getHeader(int index) {
        return tabs.get(index).button;
    }
    /**
     * Replaces the synchronous user-change callback without emitting an initial notification.
     *
     * @param listener nonnull synchronous application callback
     * @return removal action detaching this listener from subsequent changes
     */
    public Runnable onChange(Runnable listener) {
        return changes.subscribe(listener);
    }

    /**
     * Retains a tab factory and header; the page is created only when first selected.
     *
     * @param title displayed header caption
     * @param factory nonnull supplier returning a fresh unattached page
     * @return this control for further configuration
     */
    public NanoTabbedPane addTab(String title, Supplier<? extends UINode> factory) {
        NanoPaneTab tab = new NanoPaneTab(this, Objects.requireNonNull(title), Objects.requireNonNull(factory));
        tabs.add(tab);
        headers.add(tab.button);
        if (selected < 0) select(0);
        return this;
    }

    /**
     * Selects one enabled tab, creating its page on demand and transferring focus as needed.
     *
     * @param index zero-based index in the current visible or retained membership
     */
    public void select(int index) {
        NanoPaneTab next = tabs.get(index);
        if (next.button.isDisabled() || index == selected) return;
        if (next.page == null) {
            UINode page = Objects.requireNonNull(next.factory.get(), "Page factory returned null");
            if (page.getParent() != null || page == this) throw new IllegalArgumentException("Page must be fresh and unattached");
            page.getLayout()
                    .absolute()
                    .left(0)
                    .top(0)
                    .widthPercent(100)
                    .heightPercent(100);
            deck.add(page);
            next.page = page;
        }
        NanoPaneTab previous = selected < 0 ? null : tabs.get(selected);
        boolean moveFocus = previous != null && getRoot() != null && contains(previous.page, getRoot().getFocused());
        if (previous != null) {
            if (getRoot() != null && contains(previous.page, getRoot().getCaptured())) {
                getRoot()
                        .cancelInput();
                moveFocus = true;
            }
            previous.page.setVisible(false);
            previous.button.setSelected(false);
        }
        selected = index;
        next.page.setVisible(true);
        next.button.setSelected(true);
        if (moveFocus) getRoot()
                .setFocusTo(next.button);
        changes.fire();
    }

    /**
     * Removes one tab and its instantiated page, selecting a neighboring tab when necessary.
     *
     * @param index zero-based index in the current visible or retained membership
     */
    public void removeTab(int index) {
        NanoPaneTab removed = tabs.get(index);
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
                if (!tabs.get(next).button.isDisabled()) {
                    select(next);
                    break;
                }
            }
            if (selected < 0) changes.fire();
        } else {
            if (selected > index) selected--;
            changes.fire();
        }
        if (hadFocus && getRoot() != null && selected >= 0) getRoot()
                .setFocusTo(tabs.get(selected).button);
    }

    @Override
    public void update(float delta) {
        headers.update(delta);
        if (selected >= 0) tabs.get(selected)
                .page.update(delta);
    }

    /**
     * Moves tab focus and selection using the supported horizontal navigation keys.
     *
     * @param from header definition from which keyboard navigation begins
     * @param key keyboard navigation key code
     */
    void navigate(NanoPaneTab from, int key) {
        int direction = key == Keyboard.LEFT || key == Keyboard.END ? -1 : 1;
        int index = key == Keyboard.HOME ? -1 : key == Keyboard.END ? tabs.size() : tabs.indexOf(from);
        for (int i = 0; i < tabs.size(); i++) {
            index = Math.floorMod(index + direction, tabs.size());
            if (tabs.get(index).button.isDisabled()) continue;
            select(index);
            if (getRoot() != null) getRoot()
                    .setFocusTo(tabs.get(index).button);
            return;
        }
    }

    /**
     * Checks ancestry by identity without allocating a temporary path.
     *
     * @param parent candidate ancestor identity
     * @param node tree or UI node whose state is queried or changed
     * @return checks ancestry by identity without allocating a temporary path
     */
    private static boolean contains(UINode parent, UINode node) {
        /*
         * Walk parent identities directly, avoiding allocation of an ancestor collection.
         */
        if (parent == null) return false;
        for (; node != null; node = node.getParent()) if (node == parent) return true;
        return false;
    }

}
