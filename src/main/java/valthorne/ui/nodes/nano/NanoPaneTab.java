package valthorne.ui.nodes.nano;

import valthorne.Keyboard;
import valthorne.event.events.KeyPressEvent;
import valthorne.ui.UINode;

import java.util.function.Supplier;

/**
 * Retained tab definition linking an owned header, a deferred page factory,
 * and its optional instantiated page. The pane controls attachment and selection;
 * this definition borrows the pane and never closes application resources.
 */
final class NanoPaneTab {
    private final NanoTabbedPane owner; // Borrowed control coordinating this helper's behavior.

    final Supplier<? extends UINode> factory; // Retained application supplier used to create the page on first selection.
    final NanoButton button; // Owned header button selecting and navigating this tab.
    UINode page; // Owned instantiated page, or null until first successful selection.
    /**
     * Retains the owner and initializes this transient UI helper without transferring external resources.
     *
     * @param owner borrowed control coordinating this helper
     * @param title displayed header caption
     * @param factory nonnull supplier returning a fresh unattached page
     */
    NanoPaneTab(NanoTabbedPane owner, String title, Supplier<? extends UINode> factory) {
        this.owner = owner;
        this.factory = factory;
        button = new NanoButton(title) {
            @Override
            public void onKeyPress(KeyPressEvent event) {
                if (isDisabled()) return;
                int key = event.getKey();
                if (key == Keyboard.LEFT || key == Keyboard.RIGHT || key == Keyboard.HOME || key == Keyboard.END) {
                    owner.navigate(NanoPaneTab.this, key);
                    event.consume();
                } else super.onKeyPress(event);
            }
        }.action(button -> owner.select(owner.tabs.indexOf(this)));
        button.setStyleName("tab-header");
        button.getLayout()
                .paddingHorizontal(8)
                .minWidth(64)
                .heightPercent(100);
    }
}
