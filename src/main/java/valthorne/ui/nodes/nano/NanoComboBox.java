package valthorne.ui.nodes.nano;

import valthorne.Keyboard;
import valthorne.event.events.KeyPressEvent;
import valthorne.event.events.MousePressEvent;
import valthorne.ui.nodes.VirtualList;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Keyboard-accessible selection control with a clipped, virtualized modal overlay.
 * Item membership is copied on assignment, while item objects remain shared. The
 * popup creates rows on demand, shows at most eight row slots, and moves keyboard
 * focus to the highlighted option. Use on the owning UI thread.
 *
 * <pre>{@code
 * NanoComboBox<String> choice = new NanoComboBox<String>()
 *         .items(List.of("Low", "Medium", "High"))
 *         .selectedIndex(1)
 *         .onChange(value -> System.out.println(value));
 * }</pre>
 *
 * <p>Programmatic selection updates the label without invoking the change listener.
 * User selection closes the popup and notifies only when the selected index changes.
 * An attached, enabled control with nonempty items is required to open the popup.</p>
 *
 * @param <T> item value type
 * @author Albert Beaupre
 */
public class NanoComboBox<T> extends NanoContainer {
    private final NanoButton trigger = new NanoButton("Select..."); // Owned full-size trigger button.
    private List<T> items = List.of(); // Immutable membership snapshot retaining item references.
    private Function<? super T, String> formatter = String::valueOf; // Shared item-to-label formatter.
    private Consumer<? super T> change = value -> {}; // Synchronous user-selection callback.
    private int selectedIndex = -1, highlighted; // Selected index and current popup highlight.
    private Popup popup; // Active modal overlay, or null when closed.
    private VirtualList options; // Active virtualized option rows, or null when closed.

    /**
     * Creates a focusable 200-by-38 control with a non-focusable trigger that fills its
     * bounds. Trigger activation opens the popup; the initial item list is empty.
     */
    public NanoComboBox() {
        setClickable(true);
        setFocusable(true);
        getLayout().width(200).height(38).noShrink();
        trigger.getLayout().widthPercent(100).heightPercent(100);
        trigger.setFocusable(false);
        trigger.action(button -> open());
        add(trigger);
    }

    /**
     * Closes any popup, copies list membership, clears selection, and refreshes the label.
     * Does not notify the change listener. Null validation happens after popup closure.
     *
     * @param items replacement values; list and elements must be non-null
     * @return this control
     * @throws NullPointerException if the list or any item is null
     */
    public NanoComboBox<T> items(List<T> items) {
        close();
        this.items = List.copyOf(items);
        selectedIndex = -1;
        updateText();
        return this;
    }

    /**
     * Returns the unmodifiable membership snapshot. Item objects themselves are not
     * copied and may remain mutable.
     *
     * @return current item list
     */
    public List<T> getItems() {return items;}

    /**
     * Returns the current selected index independently of the popup highlight.
     *
     * @return selected index, or -1 when no item is selected
     */
    public int getSelectedIndex() {return selectedIndex;}

    /**
     * Returns the shared selected item without applying the formatter.
     *
     * @return selected item, or null when selection is empty
     */
    public T getSelected() {return selectedIndex < 0 ? null : items.get(selectedIndex);}

    /**
     * Sets selection and refreshes the trigger label without closing an open popup or
     * calling the change listener. The existing popup highlight is not synchronized here.
     *
     * @param index item index, or -1 to clear selection
     * @return this control
     * @throws IndexOutOfBoundsException if index is outside -1 through the last item
     */
    public NanoComboBox<T> selectedIndex(int index) {
        if (index < -1 || index >= items.size()) throw new IndexOutOfBoundsException(index);
        selectedIndex = index;
        updateText();
        return this;
    }

    /**
     * Replaces the item formatter, closes the popup, and refreshes the selected label.
     * The formatter runs synchronously for selected text and newly created option rows.
     *
     * @param formatter item-to-label function
     * @return this control
     * @throws NullPointerException if formatter is null
     */
    public NanoComboBox<T> formatter(Function<? super T, String> formatter) {
        this.formatter = Objects.requireNonNull(formatter);
        close();
        updateText();
        return this;
    }

    /**
     * Replaces the callback for user selection changes. Programmatic item or index
     * changes do not invoke it, and callback exceptions propagate after popup closure.
     *
     * @param listener callback receiving the selected item
     * @return this control
     * @throws NullPointerException if listener is null
     */
    public NanoComboBox<T> onChange(Consumer<? super T> listener) {
        change = Objects.requireNonNull(listener);
        return this;
    }

    /**
     * Tests whether an overlay reference is retained. This reflects local popup state
     * rather than independently querying the root's modal stack.
     *
     * @return true while this control retains a popup
     */
    public boolean isOpen() {return popup != null;}

    /**
     * Displays the placeholder when selection is empty or formats the selected item.
     * Does not notify listeners or rebuild popup rows.
     */
    private void updateText() {trigger.text(selectedIndex < 0 ? "Select..." : formatter.apply(items.get(selectedIndex)));}

    /**
     * Applies a user-selected index, closes the popup, and invokes the listener only
     * when the index changed. Index validation and label updates precede closure.
     *
     * @param index selected option index
     */
    private void select(int index) {
        boolean changed = selectedIndex != index;
        selectedIndex(index);
        close();
        if (changed) change.accept(getSelected());
    }

    /**
     * Creates a root-sized modal overlay and virtualized option list if attached, enabled,
     * nonempty, and currently closed. Positions the list below the control or above when
     * space is insufficient, bounds its height to the root, and focuses the selected or
     * first option. Formatting and layout occur synchronously.
     */
    public void open() {
        if (popup != null || items.isEmpty() || getRoot() == null || isDisabled()) return;
        var root = getRoot();
        root.setFocusTo(this);
        popup = new Popup();
        popup.getLayout().absolute().left(0).top(0).widthPercent(100).heightPercent(100);
        options = new VirtualList(items.size(), index ->
                new NanoButton(formatter.apply(items.get(index))).action(button -> select(index)));
        options.rowHeight(36).gap(2);
        var content = screenToContent(0, 0);
        var world = screenToWorld(0, 0);
        float x = getAbsoluteX() - (content.x() - world.x());
        float y = getAbsoluteY() + (content.y() - world.y()) + getHeight();
        float height = Math.min(8 * 38, items.size() * 38);
        height = Math.min(height, root.getHeight());
        if (y + height > root.getHeight()) y = Math.max(0, y - getHeight() - height);
        options.getLayout().absolute().left(Math.clamp(x, 0, Math.max(0, root.getWidth() - getWidth())))
                .top(y).width(Math.min(root.getWidth(), Math.max(160, getWidth()))).height(height);
        popup.add(options);
        root.showModal(popup);
        highlighted = Math.max(0, selectedIndex);
        focusOption();
    }

    /**
     * Scrolls the highlighted item into view, performs root layout to materialize its row,
     * and requests focus for that row. Requires an active popup attached to a root.
     */
    private void focusOption() {
        options.scrollToIndex(highlighted);
        getRoot().layout();
        getRoot().setFocusTo(options.getItemNode(highlighted));
    }

    /**
     * Clears local popup references and asks the owning root to hide the prior modal
     * when still attached. Repeated calls are harmless and do not change selection.
     */
    public void close() {
        Popup previous = popup;
        popup = null;
        options = null;
        if (previous != null && previous.getRoot() != null) previous.getRoot().hideModal(previous);
    }

    /**
     * Closes the modal overlay during node destruction so it does not remain attached
     * to the root after the control is removed.
     */
    @Override
    public void onDestroy() {close();}

    /**
     * Attempts to open on Space, Enter, or Down and consumes those keys even if opening
     * is prevented by attachment, disabled state, or an empty list.
     *
     * @param event routed control key press
     */
    @Override
    public void onKeyPress(KeyPressEvent event) {
        int key = event.getKey();
        if (key == Keyboard.SPACE || key == Keyboard.ENTER || key == Keyboard.DOWN) {
            open();
            event.consume();
        }
    }

    /**
     * Root-sized modal receiver around the virtual option list. Handles dismissal and
     * navigation keys while option buttons perform selection. Its lifetime is controlled
     * by the enclosing combo box.
     *
     * @author Albert Beaupre
     */
    private final class Popup extends NanoContainer {
        /**
         * Creates a clickable, scrollable overlay so modal routing can intercept input
         * outside the option rows.
         */
        Popup() {
            setClickable(true);
            setScrollable(true);
        }

        /**
         * Closes the popup and consumes a mouse press routed to the overlay. There is no
         * button filter in this callback; option rows have their own input routing.
         *
         * @param event overlay mouse press
         */
        @Override
        public void onMousePress(MousePressEvent event) {
            close();
            event.consume();
        }

        /**
         * Handles Escape dismissal and bounded Up, Down, Home, and End highlighting.
         * Navigation materializes and focuses the option row; recognized keys are consumed.
         * Other keys are left for option button activation or normal routing.
         *
         * @param event routed popup key press
         */
        @Override
        public void onKeyPress(KeyPressEvent event) {
            switch (event.getKey()) {
                case Keyboard.ESCAPE -> close();
                case Keyboard.UP -> {
                    highlighted = Math.max(0, highlighted - 1);
                    focusOption();
                }
                case Keyboard.DOWN -> {
                    highlighted = Math.min(items.size() - 1, highlighted + 1);
                    focusOption();
                }
                case Keyboard.HOME -> {
                    highlighted = 0;
                    focusOption();
                }
                case Keyboard.END -> {
                    highlighted = items.size() - 1;
                    focusOption();
                }
                default -> {return;}
            }
            event.consume();
        }
    }
}
