package valthorne.ui.nodes.nano;

import valthorne.Keyboard;
import valthorne.event.events.KeyPressEvent;
import valthorne.ui.UINode;
import valthorne.ui.behavior.ChangeSignal;

import java.util.ArrayList;
import java.util.Objects;
import java.util.function.Supplier;

/** NanoVG-backed lazy tab container. */
public class NanoTabbedPane extends NanoPanel {
    private final NanoPanel headers = new NanoPanel();
    private final NanoPanel deck = new NanoPanel();
    private final ArrayList<Tab> tabs = new ArrayList<>();
    private final ChangeSignal changes = new ChangeSignal();
    private int selected = -1;

    public NanoTabbedPane() {
        getLayout().column().gap(8);
        headers.getLayout().row().gap(4).height(36).noShrink();
        deck.getLayout().height(0).minHeight(0).grow();
        super.add(headers); super.add(deck);
    }

    public int getTabCount() { return tabs.size(); }
    public int getSelectedIndex() { return selected; }
    public UINode getPage(int index) { return tabs.get(index).page; }
    public NanoButton getHeader(int index) { return tabs.get(index).button; }
    public AutoCloseable onChange(Runnable listener) { return changes.subscribe(listener); }

    public NanoTabbedPane addTab(String title, Supplier<? extends UINode> factory) {
        Tab tab = new Tab(Objects.requireNonNull(title), Objects.requireNonNull(factory));
        tabs.add(tab); headers.add(tab.button);
        if (selected < 0) select(0);
        return this;
    }

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
            if (getRoot() != null && contains(previous.page, getRoot().getCaptured())) { getRoot().cancelInput(); moveFocus = true; }
            previous.page.setVisible(false); previous.button.setSelected(false);
        }
        selected = index;
        next.page.setVisible(true); next.button.setSelected(true);
        if (moveFocus) getRoot().setFocusTo(next.button);
        changes.fire();
    }

    public void removeTab(int index) {
        Tab removed = tabs.get(index);
        boolean active = selected == index;
        boolean hadFocus = getRoot() != null && (getRoot().getFocused() == removed.button || contains(removed.page, getRoot().getFocused()));
        tabs.remove(index); headers.remove(removed.button);
        if (removed.page != null) deck.remove(removed.page);
        if (active) {
            selected = -1;
            int candidate = Math.min(index, tabs.size() - 1);
            for (int i = 0; i < tabs.size(); i++) {
                int next = (candidate + i) % tabs.size();
                if (!tabs.get(next).button.isDisabled()) { select(next); break; }
            }
            if (selected < 0) changes.fire();
        } else { if (selected > index) selected--; changes.fire(); }
        if (hadFocus && getRoot() != null && selected >= 0) getRoot().setFocusTo(tabs.get(selected).button);
    }

    @Override public void update(float delta) {
        headers.update(delta);
        if (selected >= 0) tabs.get(selected).page.update(delta);
    }

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

    private static boolean contains(UINode parent, UINode node) {
        if (parent == null) return false;
        for (; node != null; node = node.getParent()) if (node == parent) return true;
        return false;
    }

    private final class Tab {
        final Supplier<? extends UINode> factory;
        final NanoButton button;
        UINode page;
        Tab(String title, Supplier<? extends UINode> factory) {
            this.factory = factory;
            button = new NanoButton(title) {
                @Override public void onKeyPress(KeyPressEvent event) {
                    if (isDisabled()) return;
                    int key = event.getKey();
                    if (key == Keyboard.LEFT || key == Keyboard.RIGHT || key == Keyboard.HOME || key == Keyboard.END) {
                        navigate(Tab.this, key); event.consume();
                    } else super.onKeyPress(event);
                }
            }.action(button -> select(tabs.indexOf(this)));
            button.getLayout().padding(8).minWidth(80).heightPercent(100);
        }
    }
}
