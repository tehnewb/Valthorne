package valthorne.ui.nodes;

import valthorne.Keyboard;
import valthorne.event.events.KeyPressEvent;
import valthorne.ui.UINode;
import java.util.function.Supplier;

/**
 * Retains one header, its deferred factory, and its instantiated page reference.
 * The enclosing pane controls attachment, selection, and disposal through its
 * header and deck containers.
 *
 * @author Albert Beaupre
 */
final class PaneTab {
    private final TabbedPane owner; // Borrowed control coordinating this helper's behavior.

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
    PaneTab(TabbedPane owner, String title, Supplier<? extends UINode> factory) {
        this.owner = owner;
        this.factory = factory;
        button = new Button(title) {
            /**
             * Handles enabled header navigation using Left, Right, Home, and End, consuming
             * those events. Other keys use normal button activation; disabled headers do nothing.
             *
             * @param event routed header key press
             */
            @Override
            public void onKeyPress(KeyPressEvent event) {
                if (isDisabled()) return;
                int key = event.getKey();
                if (key == Keyboard.LEFT || key == Keyboard.RIGHT || key == Keyboard.HOME || key == Keyboard.END) {
                    owner.navigate(PaneTab.this, key);
                    event.consume();
                } else super.onKeyPress(event);
            }
        }.action(b -> owner.select(owner.tabs.indexOf(this)));
        button.setStyleName("tab-header");
        button.getLayout()
                .paddingHorizontal(8)
                .minWidth(64)
                .heightPercent(100);
    }
}
