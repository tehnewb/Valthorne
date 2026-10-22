package valthorne.ui.nodes.nano;

import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.Predicate;

import valthorne.Keyboard;
import valthorne.Mouse;
import valthorne.graphics.Color;
import valthorne.event.events.KeyPressEvent;
import valthorne.event.events.MousePressEvent;
import valthorne.event.events.MouseScrollEvent;
import valthorne.ui.NanoText;
import valthorne.ui.UIInputEvent;
import valthorne.ui.UINode;
import valthorne.ui.UIRoot;
import valthorne.ui.theme.ResolvedStyle;
import valthorne.ui.theme.StyleKey;
import valthorne.ui.theme.UITokens;
import java.util.List;
import java.util.ArrayList;
import java.util.Objects;

/**
 * NanoVG variant. Scrollable command menu hosted in the root's modal overlay.
 * Submenus open beside their parent row inside that same input scope, so the
 * pointer can move between levels without dismissing the tree. Hover preserves
 * the parent's scroll position; flyouts align with the visible row and clamp to
 * the root edges. Scrolling a parent dismisses its old flyouts. Up and Down
 * navigate a level, Right opens a submenu, Left returns to its parent, and
 * Escape closes one level or the entire menu. Commands run after dismissal.
 * <pre>{@code
 * NanoPopupMenu menu = new NanoPopupMenu().items(List.of(
 *     new NanoPopupMenuItem("Save", () -> saveDocument(), true)));
 * menu.showBelow(saveButton);
 * }</pre>
 * Use on the UI thread. Items are immutable snapshots; replacing items closes the
 * menu. The anchor owns its lifetime and should call close when detached.
 * Submenu providers refresh children on each opening and may nest to any depth.
 * @author Albert Beaupre
 */
public class NanoPopupMenu extends NanoContainer {
    /**
     * Shows typical editor context menus without hiding their final commands;
     * larger menus continue to scroll within the root's available height.
     */
    private static final int MAX_VISIBLE_ROWS = 16;

    /**
     * Theme key controlling checkbox corner radius.
     */
    public static final StyleKey<Float> CHECKBOX_CORNER_RADIUS_KEY =
            StyleKey.of("nano.popupmenu.checkboxCornerRadius", Float.class, 0f);
    /**
     * Theme key controlling indicator color.
     */
    public static final StyleKey<Color> INDICATOR_COLOR_KEY =
            StyleKey.of("nano.popupmenu.indicatorColor", Color.class, new Color(0xFFD0D0D0));

    private List<NanoPopupMenuItem> items = List.of(); // Immutable command snapshot in display order.
    private NanoVirtualList options; // Owned transient rows for the current opening.
    final List<List<NanoPopupMenuItem>> pages = new ArrayList<>(); // Visible command snapshots by flyout depth.
    private final List<NanoVirtualList> panels = new ArrayList<>(); // Visible lists sharing this modal shield.
    private final List<Integer> openedBy = new ArrayList<>(); // Parent row index for each flyout; root uses -1.
    int activeDepth; // Flyout level currently receiving keyboard commands.
    private UIRoot owner; // Root hosting this menu, or null while closed.
    int highlighted = -1; // Keyboard target, or -1 when no command is enabled.
    private IntConsumer horizontal; // Optional menu-bar switch callback for Left/Right.
    private Predicate<MousePressEvent> outsidePress; // Optional heading switch on shield presses.
    private Consumer<MousePressEvent> secondaryPress; // Optional owner handling for right presses anywhere on the menu.

    /**
     * Creates an empty transparent input shield; only its option rows are styled.
     * Attach through showBelow rather than adding the menu as normal content.
     */
    public NanoPopupMenu() {
        setClickable(true);
        setScrollable(true);
        setFocusable(true);
    }

    /**
     * Copies commands and dismisses any prior opening. Validation precedes mutation.
     * @param items nonnull list containing no null commands
     * @return this menu
     */
    public NanoPopupMenu items(List<NanoPopupMenuItem> items) {
        List<NanoPopupMenuItem> copy = List.copyOf(items);
        close();
        this.items = copy;
        return this;
    }

    /**
     * Reads the immutable command snapshot without invoking commands.
     * @return commands in display order
     */
    public List<NanoPopupMenuItem> getItems() {
        return items;
    }

    /**
     * Reports actual attachment, including external removal by the root.
     * @return whether the menu is currently hosted by its opening root
     */
    public boolean isOpen() {
        return owner != null && getRoot() == owner;
    }

    /**
     * Opens below an attached enabled anchor, flipping above when space is limited.
     * Width and height are clamped to the root; long lists scroll and virtualize.
     * Empty menus and detached anchors do nothing. Coordinates account for ancestor scrolling.
     * @param anchor node whose lower edge anchors this menu
     * @throws NullPointerException if anchor is null
     */
    public void showBelow(UINode anchor) {
        Objects.requireNonNull(anchor);
        if (anchor.getRoot() == null) {
            close();
            return;
        }
        setTheme(anchor.getTheme());
        UIRoot root = anchor.getRoot();
        float width = Math.min(root.getWidth(), Math.max(anchor.getWidth(), preferredWidth(root, items)));
        float height = Math.min(root.getHeight(), Math.min(MAX_VISIBLE_ROWS, items.size()) * rowHeight());
        var content = anchor.screenToContent(0, 0);
        var world = anchor.screenToWorld(0, 0);
        float x = anchor.getAbsoluteX() - content.x() + world.x();
        float y = anchor.getAbsoluteY() + content.y() - world.y() + anchor.getHeight();
        if (y + height > root.getHeight()) y -= anchor.getHeight() + height;
        show(anchor, x, y, width, height);
    }

    /**
     * Opens with its top-left corner at a screen-space pointer, clamped inside the root.
     */
    public void showAt(UINode anchor, float screenX, float screenY, float width) {
        Objects.requireNonNull(anchor);
        if (!Float.isFinite(screenX) || !Float.isFinite(screenY) || !Float.isFinite(width) || width <= 0)
            throw new IllegalArgumentException("Invalid popup position or width");
        if (anchor.getRoot() == null) {
            close();
            return;
        }
        setTheme(anchor.getTheme());
        UIRoot root = anchor.getRoot();
        var point = root.screenToLocal(screenX, screenY);
        show(anchor, point.x(), point.y(), Math.min(root.getWidth(), Math.max(width, preferredWidth(root, items))),
                Math.min(root.getHeight(), Math.min(MAX_VISIBLE_ROWS, items.size()) * rowHeight()));
    }

    /**
     * Opens a command snapshot at the requested screen position within root bounds.
     *
     * @param anchor attached UI node requesting the modal shell
     * @param x horizontal logical or screen coordinate as used by the caller
     * @param y vertical logical or screen coordinate as used by the caller
     * @param width preferred logical popup width
     * @param height preferred logical popup height
     */
    private void show(UINode anchor, float x, float y, float width, float height) {
        setTheme(anchor.getTheme());
        close();
        if (isDisabled() || items.isEmpty() || anchor.getRoot() == null || anchor.isDisabled()) return;
        UIRoot root = anchor.getRoot();
        getLayout()
                .absolute()
                .left(0)
                .top(0)
                .widthPercent(100)
                .heightPercent(100);
        owner = root;
        addPage(items, -1, x, y, width, height);
        root.setFocusTo(anchor);
        root.showModal(this);
        activeDepth = 0;
        highlighted = -1;
        root.setFocusTo(this);
    }

    /**
     * Adds a virtualized command list inside the shared modal shield.
     */
    private void addPage(List<NanoPopupMenuItem> snapshot, int parentIndex, float x, float y, float width, float height) {
        int depth = panels.size();
        pages.add(snapshot);
        openedBy.add(parentIndex);
        NanoVirtualList panel = new NanoVirtualList(snapshot.size(), index -> {
            NanoPopupMenuItem item = snapshot.get(index);
            NanoButton row = new NanoPopupMenuButton(this, depth, index).action(button -> activateAt(depth, index));
            row.setStyleName("menu-item"); row.leftAligned(true);
            if (item.checkable() || item.icon() != null) row.setStyle(NanoButton.PADDING_X_KEY, 36f);
            row.setEnabled(item.enabled());
            return row;
        });
        panel.rowHeight(rowHeight())
                .gap(0);
        panel.setStyleName("menu-surface");
        panel.getLayout()
                .absolute()
                .left(Math.clamp(x, 0, Math.max(0, owner.getWidth() - width)))
                .top(Math.clamp(y, 0, Math.max(0, owner.getHeight() - height)))
                .width(width)
                .height(height);
        panels.add(panel);
        if (depth == 0) options = panel;
        add(panel);
    }

    /**
     * Removes a flyout and all its descendants while retaining its parent.
     */
    void closeFrom(int firstDepth) {
        for (int depth = panels.size() - 1; depth >= firstDepth; depth--) {
            NanoVirtualList panel = panels.remove(depth);
            pages.remove(depth);
            openedBy.remove(depth);
            remove(panel);
        }
        activeDepth = Math.min(activeDepth, Math.max(0, panels.size() - 1));
    }

    /**
     * Opens a child beside its visible parent row, flipping left at the root edge.
     * Hover leaves the scroll offset unchanged; keyboard activation reveals only
     * rows outside the parent's current viewport.
     *
     * @param depth parent page depth
     * @param index parent command index
     * @param focusChild whether to transfer keyboard focus into the child
     */
    void openSubmenu(int depth, int index, boolean focusChild) {
        if (!isOpen() || depth >= pages.size()) return;
        NanoPopupMenuItem item = pages.get(depth)
                .get(index);
        if (!item.enabled() || item.submenu() == null) return;
        if (panels.size() > depth + 1 && openedBy.get(depth + 1) == index) {
            if (focusChild) {
                activeDepth = depth + 1;
                highlighted = -1;
                move(1);
            }
            return;
        }
        List<NanoPopupMenuItem> children = List.copyOf(item.submenu().get());
        closeFrom(depth + 1);
        if (children.isEmpty()) return;
        NanoVirtualList parent = panels.get(depth);
        if (focusChild) parent.revealIndex(index);
        owner.layout();
        UINode row = parent.getItemNode(index);
        if (row == null) return;
        float width = Math.min(owner.getWidth(), preferredWidth(owner, children));
        float height = Math.min(owner.getHeight(), Math.min(MAX_VISIBLE_ROWS, children.size()) * rowHeight());
        float parentX = parent.getAbsoluteX() - owner.getAbsoluteX();
        float x = parentX + parent.getWidth();
        if (x + width > owner.getWidth()) x = parentX - width;
        float y = row.getAbsoluteY() - parent.getScrollY() - owner.getAbsoluteY();
        addPage(children, index, x, y, width, height);
        owner.layout();
        if (focusChild) {
            activeDepth = depth + 1;
            highlighted = -1;
            move(1);
        }
    }

    /**
     * Sizes a popup to its longest command, including space for an indicator
     * and the submenu arrow. This is calculated only when a menu opens.
     *
     * @param root root supplying the fallback NanoVG context
     * @param commands entries that will appear in this popup page
     * @return preferred width in UI units
     */
    private float preferredWidth(UIRoot root, List<NanoPopupMenuItem> commands) {
        float width = 180f;
        ResolvedStyle style = getStyle();
        Float size = style == null ? null : style.get(UITokens.FONT_SIZE);
        float fontSize = size == null ? 14f : size;
        for (NanoPopupMenuItem item : commands) {
            float label = NanoText.measureTextWidth(this, root.getNanoVGHandle(), "default", fontSize, item.text());
            width = Math.max(width, label + 80f);
        }
        return width;
    }

    /**
     * Reads the opening theme's compact control height for every popup page.
     *
     * @return finite positive row height in UI units
     */
    private float rowHeight() {
        ResolvedStyle style = getStyle();
        Float height = style == null ? null : style.get(UITokens.CONTROL_HEIGHT);
        return height != null && Float.isFinite(height) && height > 0 ? height : 26f;
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
        panels.clear();
        pages.clear();
        openedBy.clear();
        activeDepth = 0;
        highlighted = -1;
        clear();
    }

    /**
     * Executes one enabled command after closing. Disabled menus/items do nothing;
     * invalid indices throw before any state changes. Callback failures propagate.
     * @param index zero-based command index
     */
    public void activate(int index) {
        activateAt(0, index);
    }

    /**
     * Executes a command or reveals its submenu at the specified depth.
     */
    private void activateAt(int depth, int index) {
        NanoPopupMenuItem item = depth < pages.size() ? pages.get(depth).get(index) : items.get(index);
        if (isDisabled() || !item.enabled()) return;
        if (item.submenu() != null) {
            openSubmenu(depth, index, true);
            return;
        }
        close();
        item.action()
                .run();
    }

    /**
     * Installs owner-provided Left/Right menu switching; standalone popups leave it null.
     * @param listener callback receiving -1 or 1, or null to leave arrows unhandled
     */
    void horizontalNavigation(IntConsumer listener) {
        horizontal = listener;
    }

    /**
     * Lets an owning menu bar switch headings with a single pointer press.
     */
    void outsidePress(Predicate<MousePressEvent> listener) {
        outsidePress = listener;
    }

    /**
     * Handles right presses on the menu or shield before normal menu routing.
     */
    public void onSecondaryPress(Consumer<MousePressEvent> listener) {
        secondaryPress = listener;
    }

    /**
     * Reveals and focuses a specified enabled row after opening, without activation.
     * Closed menus and disabled entries retain their current highlight.
     * @param index valid command position
     */
    void highlight(int index) {
        highlightAt(0, index);
    }

    /**
     * Focuses an enabled row, scrolling only enough to make the entire row visible.
     * Changing the highlighted parent dismisses a child belonging to the old row.
     *
     * @param depth page receiving keyboard focus
     * @param index enabled command index
     */
    private void highlightAt(int depth, int index) {
        if (!isOpen() || depth >= pages.size() || !pages.get(depth).get(index).enabled()) return;
        activeDepth = depth;
        highlighted = index;
        if (panels.size() > depth + 1 && openedBy.get(depth + 1) != index) closeFrom(depth + 1);
        NanoVirtualList panel = panels.get(depth);
        panel.revealIndex(index);
        owner.layout();
        owner.setFocusTo(panel.getItemNode(index));
    }

    /**
     * Wraps keyboard selection through enabled items, materializing only the chosen row.
     * @param direction positive for next, negative for previous
     */
    private void move(int direction) {
        if (panels.isEmpty()) return;
        List<NanoPopupMenuItem> page = pages.get(activeDepth);
        if (page.isEmpty()) return;
        int start = highlighted < 0 ? (direction > 0 ? -1 : 0) : highlighted;
        for (int n = 0; n < page.size(); n++) {
            int index = Math.floorMod(start + direction * (n + 1), page.size());
            if (page.get(index).enabled()) {
                highlightAt(activeDepth, index);
                return;
            }
        }
        highlighted = -1;
        owner.setFocusTo(this);
    }

    /**
     * Closes the active flyout and restores focus to its parent entry.
     */
    private void returnToParent() {
        int depth = activeDepth;
        int parentIndex = openedBy.get(depth);
        closeFrom(depth);
        highlightAt(depth - 1, parentIndex);
    }

    /**
     * Intercepts dismissal and menu navigation before row buttons receive input.
     * Presses targeting actual rows are preserved so normal button activation works.
     * @param context current routed preview event
     */
    @Override
    public void onInputPreview(UIInputEvent context) {
        if (context.event() instanceof MouseScrollEvent) {
            for (UINode node = context.target(); node != null && node != this; node = node.getParent()) {
                int depth = panels.indexOf(node);
                if (depth < 0) continue;
                closeFrom(depth + 1);
                break;
            }
        }
        if (context.event() instanceof MousePressEvent press && press.getButton() == Mouse.RIGHT && secondaryPress != null) {
            secondaryPress.accept(press);
            context.consume();
            return;
        }
        if (context.event() instanceof MousePressEvent press && context.target() == this) {
            if (outsidePress != null && outsidePress.test(press)) {
                context.consume();
                return;
            }
            close();
            context.consume();
        } else if (context.event() instanceof KeyPressEvent key) {
            for (UINode node = context.target(); node != null && node != this; node = node.getParent()) {
                if (node instanceof NanoPopupMenuButton row) {
                    activeDepth = row.depth;
                    highlighted = row.index;
                    break;
                }
            }
            switch (key.getKey()) {
                case Keyboard.ESCAPE -> {
                    if (activeDepth > 0) returnToParent();
                    else close();
                }
                case Keyboard.DOWN -> move(1);
                case Keyboard.UP -> move(-1);
                case Keyboard.HOME -> {
                    highlighted = -1;
                    move(1);
                }
                case Keyboard.END -> {
                    highlighted = -1;
                    move(-1);
                }
                case Keyboard.ENTER, Keyboard.SPACE -> {
                    if (highlighted >= 0) activateAt(activeDepth, highlighted);
                }
                case Keyboard.LEFT -> {
                    if (activeDepth > 0) returnToParent();
                    else if (horizontal != null) horizontal.accept(-1);
                    else return;
                }
                case Keyboard.RIGHT -> {
                    if (highlighted >= 0 && pages.get(activeDepth).get(highlighted).submenu() != null)
                        openSubmenu(activeDepth, highlighted, true);
                    else if (activeDepth == 0 && horizontal != null) horizontal.accept(1);
                    else return;
                }
                default -> {
                    return;
                }
            }
            context.consume();
        }
    }

    /**
     * Clears opening state when the root removes the overlay externally. Child
     * destruction is handled by the container's normal detach traversal.
     */
    @Override
    public void onDestroy() {
        owner = null;
        activeDepth = 0;
        highlighted = -1;
    }

}
