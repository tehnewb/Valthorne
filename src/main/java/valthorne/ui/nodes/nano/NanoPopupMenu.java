package valthorne.ui.nodes.nano;

import valthorne.Keyboard;
import valthorne.event.events.KeyPressEvent;
import valthorne.event.events.MousePressEvent;
import valthorne.ui.UIInputEvent;
import valthorne.ui.UINode;
import valthorne.ui.UIRoot;
import java.util.List;
import java.util.Objects;

/**
 * NanoVG variant. Scrollable command menu hosted in the root's modal overlay. Outside presses and
 * Escape dismiss it; arrows, Home, End, Enter and Space navigate or activate enabled
 * commands. Closing restores the focus saved by the root before opening. Commands
 * run synchronously after dismissal, so they may safely open another modal.
 * <pre>{@code
 * NanoPopupMenu menu = new NanoPopupMenu().items(List.of(
 *     new NanoPopupMenu.Item("Save", () -> saveDocument(), true)));
 * menu.showBelow(saveButton);
 * }</pre>
 * Use on the UI thread. Items are immutable snapshots; replacing items closes the
 * menu. The anchor owns its lifetime and should call close when detached. This is a
 * flat command list; nested submenus and global shortcut registration are not implied.
 * @author Albert Beaupre
 */
public class NanoPopupMenu extends NanoContainer {
    /**
     * Immutable command description. Disabled items remain visible but cannot activate.
     * The callback is borrowed and is not invoked during construction or formatting.
     * @param text nonnull displayed label
     * @param action nonnull synchronous command
     * @param enabled whether user activation is permitted
     * @author Albert Beaupre
     */
    public record Item(String text, Runnable action, boolean enabled) {
        /**
         * Validates command data before it can enter a menu snapshot.
         * @param text displayed label
         * @param action callback to run after dismissal
         * @param enabled initial availability
         * @throws NullPointerException if text or action is null
         */
        public Item { Objects.requireNonNull(text); Objects.requireNonNull(action); }
    }

    private List<Item> items = List.of(); // Immutable command snapshot in display order.
    private NanoVirtualList options; // Owned transient rows for the current opening.
    private UIRoot owner; // Root hosting this menu, or null while closed.
    private int highlighted = -1; // Keyboard target, or -1 when no command is enabled.
    private java.util.function.IntConsumer horizontal; // Optional menu-bar switch callback for Left/Right.
    private java.util.function.Predicate<MousePressEvent> outsidePress; // Optional heading switch on shield presses.

    /**
     * Creates an empty transparent input shield; only its option rows are styled.
     * Attach through showBelow rather than adding the menu as normal content.
     */
    public NanoPopupMenu() { setClickable(true); setScrollable(true); setFocusable(true); }

    /**
     * Copies commands and dismisses any prior opening. Validation precedes mutation.
     * @param items nonnull list containing no null commands
     * @return this menu
     */
    public NanoPopupMenu items(List<Item> items) {
        List<Item> copy = List.copyOf(items);
        close(); this.items = copy; return this;
    }

    /**
     * Reads the immutable command snapshot without invoking commands.
     * @return commands in display order
     */
    public List<Item> getItems() { return items; }

    /**
     * Reports actual attachment, including external removal by the root.
     * @return whether the menu is currently hosted by its opening root
     */
    public boolean isOpen() { return owner != null && getRoot() == owner; }

    /**
     * Opens below an attached enabled anchor, flipping above when space is limited.
     * Width and height are clamped to the root; long lists scroll and virtualize.
     * Empty menus and detached anchors do nothing. Coordinates account for ancestor scrolling.
     * @param anchor node whose lower edge anchors this menu
     * @throws NullPointerException if anchor is null
     */
    public void showBelow(UINode anchor) {
        Objects.requireNonNull(anchor);
        close();
        if (isDisabled() || items.isEmpty() || anchor.getRoot() == null || anchor.isDisabled()) return;
        UIRoot root = anchor.getRoot();
        clear();
        options = new NanoVirtualList(items.size(), index -> {
            Item item = items.get(index);
            NanoButton row = new CommandButton(index).action(button -> activate(index));
            row.setStyleName("menu-item"); row.leftAligned(true);
            row.setEnabled(item.enabled());
            return row;
        });
        options.rowHeight(32).gap(0);
        options.setStyleName("menu-surface");
        float width = Math.min(root.getWidth(), Math.max(180, anchor.getWidth()));
        float height = Math.min(root.getHeight(), Math.min(8, items.size()) * 32f);
        var content = anchor.screenToContent(0, 0);
        var world = anchor.screenToWorld(0, 0);
        float x = anchor.getAbsoluteX() - content.x() + world.x();
        float y = anchor.getAbsoluteY() + content.y() - world.y() + anchor.getHeight();
        if (y + height > root.getHeight()) y -= anchor.getHeight() + height;
        options.getLayout().absolute().left(Math.clamp(x, 0, Math.max(0, root.getWidth() - width)))
                .top(Math.clamp(y, 0, Math.max(0, root.getHeight() - height))).width(width).height(height);
        getLayout().absolute().left(0).top(0).widthPercent(100).heightPercent(100);
        add(options);
        owner = root;
        root.setFocusTo(anchor);
        root.showModal(this);
        highlighted = -1;
        move(1);
    }

    /**
     * Dismisses this opening and releases transient rows. Repeated calls are harmless.
     * Root modal removal restores the previous eligible focus target.
     */
    public void close() {
        UIRoot previous = owner;
        owner = null;
        if (previous != null && getRoot() == previous) previous.hideModal(this);
        options = null;
        highlighted = -1;
        clear();
    }

    /**
     * Executes one enabled command after closing. Disabled menus/items do nothing;
     * invalid indices throw before any state changes. Callback failures propagate.
     * @param index zero-based command index
     */
    public void activate(int index) {
        Item item = items.get(index);
        if (isDisabled() || !item.enabled()) return;
        close(); item.action().run();
    }

    /**
     * Installs owner-provided Left/Right menu switching; standalone popups leave it null.
     * @param listener callback receiving -1 or 1, or null to leave arrows unhandled
     */
    void horizontalNavigation(java.util.function.IntConsumer listener) { horizontal = listener; }

    /** Lets an owning menu bar switch headings with a single pointer press. */
    void outsidePress(java.util.function.Predicate<MousePressEvent> listener) { outsidePress = listener; }

    /**
     * Reveals and focuses a specified enabled row after opening, without activation.
     * Closed menus and disabled entries retain their current highlight.
     * @param index valid command position
     */
    void highlight(int index) {
        if (!isOpen() || !items.get(index).enabled()) return;
        highlighted = index; options.scrollToIndex(index); owner.layout();
        owner.setFocusTo(options.getItemNode(index));
    }

    /**
     * Wraps keyboard selection through enabled items, materializing only the chosen row.
     * @param direction positive for next, negative for previous
     */
    private void move(int direction) {
        if (options == null || items.isEmpty()) return;
        for (int n = 0; n < items.size(); n++) {
            highlighted = Math.floorMod(highlighted + direction, items.size());
            if (items.get(highlighted).enabled()) {
                options.scrollToIndex(highlighted); owner.layout();
                owner.setFocusTo(options.getItemNode(highlighted)); return;
            }
        }
        highlighted = -1; owner.setFocusTo(this);
    }

    /**
     * Intercepts dismissal and menu navigation before row buttons receive input.
     * Presses targeting actual rows are preserved so normal button activation works.
     * @param context current routed preview event
     */
    @Override public void onInputPreview(UIInputEvent context) {
        if (context.event() instanceof MousePressEvent press && context.target() == this) {
            if (outsidePress != null && outsidePress.test(press)) { context.consume(); return; }
            close(); context.consume();
        } else if (context.event() instanceof KeyPressEvent key) {
            for (UINode node = context.target(); node != null && node != this; node = node.getParent()) {
                if (node instanceof CommandButton row) { highlighted = row.index; break; }
            }
            switch (key.getKey()) {
                case Keyboard.ESCAPE -> close();
                case Keyboard.DOWN -> move(1);
                case Keyboard.UP -> move(-1);
                case Keyboard.HOME -> { highlighted = -1; move(1); }
                case Keyboard.END -> { highlighted = items.size(); move(-1); }
                case Keyboard.ENTER, Keyboard.SPACE -> { if (highlighted >= 0) activate(highlighted); }
                case Keyboard.LEFT, Keyboard.RIGHT -> {
                    if (horizontal == null) return;
                    horizontal.accept(key.getKey() == Keyboard.LEFT ? -1 : 1);
                }
                default -> { return; }
            }
            context.consume();
        }
    }

    /**
     * Clears opening state when the root removes the overlay externally. Child
     * destruction is handled by the container's normal detach traversal.
     */
    @Override public void onDestroy() { owner = null; highlighted = -1; }

    /**
     * Transient row retaining its command index so Tab-driven focus and arrow-driven
     * highlighting agree. Rows are owned and recycled by the virtualized option list.
     * The immutable index belongs to one opening and is discarded when rows are cleared.
     * @author Albert Beaupre
     */
    private final class CommandButton extends NanoButton {
        private final int index; // Command position represented by this transient row.

        /**
         * Creates a themed command label without executing its callback.
         * @param index valid position in the current command snapshot
         */
        private CommandButton(int index) { super(items.get(index).text()); this.index = index; }
    }
}
