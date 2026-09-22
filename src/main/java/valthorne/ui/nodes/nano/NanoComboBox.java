package valthorne.ui.nodes.nano;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Themed single-selection dropdown with a virtualized modal option menu. Programmatic
 * item/selection changes are silent; user selection notifies once after the popup closes.
 * Empty selection uses index -1 and displays a placeholder. Items are copied, while
 * individual values and the formatter are borrowed on the UI thread.
 * <pre>{@code
 * NanoComboBox<String> quality = new NanoComboBox<String>().items(List.of("Low", "High"));
 * quality.onChange(value -> applyQuality(value));
 * root.add(quality);
 * }</pre>
 * @param <T> option value type
 * @author Albert Beaupre
 */
public class NanoComboBox<T> extends NanoContainer {
    /** Borrows the Nano caption for custom layout. @return owned trigger caption */
    public NanoLabel getLabel() { return trigger.getLabel(); }

    /** Returns the formatted selection or placeholder. @return displayed caption */
    public String getText() { return trigger.getText(); }

    /** Borrows the button used to paint and activate this control. @return owned trigger */
    public NanoButton getTrigger() { return trigger; }

    /** Keeps named widget chrome on the actual NanoVG button. */
    @Override protected void applyLayout() {
        trigger.setStyleName(getStyleName());
        super.applyLayout();
    }

    /** Mirrors focus for painting while the root retains this control as its focus target. */
    @Override public void setFocused(boolean focused) {
        super.setFocused(focused);
        trigger.setFocused(focused);
    }

    private final NanoButton trigger = new NanoButton("Select...");
    private List<T> items = List.of(); // Immutable option sequence; elements remain borrowed.
    private Function<? super T, String> formatter = String::valueOf; // Option-to-label conversion callback.
    private Consumer<? super T> change = value -> {}; // User-selection listener, invoked after dismissal.
    private final NanoPopupMenu popup = new NanoPopupMenu(); // Owned root-hosted option list.
    private int selected = -1; // Selected option index or -1 for no selection.

    /**
     * Creates an empty dropdown whose standard button action toggles the popup.
     */
    public NanoComboBox() {
        setClickable(true); setFocusable(true);
        trigger.setFocusable(false); trigger.getLayout().widthPercent(100).heightPercent(100); add(trigger);
        trigger.action(button -> { if (popup.isOpen()) popup.close(); else open(); });
        getLayout().minWidth(160).height(36);
    }

    /**
     * Replaces options with a nonnull snapshot and clears selection silently.
     * @param items list without null elements
     * @return this dropdown
     */
    public NanoComboBox<T> items(List<T> items) {
        List<T> copy = List.copyOf(items);
        popup.close(); this.items = copy; selected = -1; trigger.text("Select..."); return this;
    }

    /**
     * Returns the immutable option list; individual values are not deep copied.
     * @return options in menu order
     */
    public List<T> getItems() { return items; }

    /**
     * Reads the selected index without notifying listeners.
     * @return index or -1 when empty
     */
    public int getSelectedIndex() { return selected; }

    /**
     * Borrows the selected value; no selection returns null.
     * @return selected option or null
     */
    public T getSelected() { return selected < 0 ? null : items.get(selected); }

    /**
     * Sets selection silently, dismissing stale options. Formatting is validated before
     * mutation so formatter failures preserve the old selection and label.
     * @param index -1 or a valid option index
     * @return this dropdown
     * @throws IndexOutOfBoundsException if index is outside the supported range
     */
    public NanoComboBox<T> selectedIndex(int index) {
        if (index < -1 || index >= items.size()) throw new IndexOutOfBoundsException(index);
        String label = index < 0 ? "Select..." : Objects.requireNonNull(formatter.apply(items.get(index)));
        popup.close(); selected = index; trigger.text(label); return this;
    }

    /**
     * Applies user selection and notifies only when its index changes. Disabled controls
     * ignore requests; listeners see committed state and their exceptions propagate.
     * @param index valid option index, or -1 to clear
     */
    public void select(int index) {
        if (isDisabled()) return;
        int before = selected; selectedIndex(index);
        if (before != selected) change.accept(getSelected());
    }

    /**
     * Replaces formatting and updates the current label without notifying selection.
     * @param formatter nonnull callback returning nonnull labels
     * @return this dropdown
     */
    public NanoComboBox<T> formatter(Function<? super T, String> formatter) {
        Objects.requireNonNull(formatter);
        String label = selected < 0 ? "Select..." : Objects.requireNonNull(formatter.apply(getSelected()));
        popup.close(); this.formatter = formatter; trigger.text(label); return this;
    }

    /**
     * Replaces the synchronous user-change callback; programmatic setters remain silent.
     * @param listener nonnull callback
     * @return this dropdown
     */
    public NanoComboBox<T> onChange(Consumer<? super T> listener) { change = Objects.requireNonNull(listener); return this; }

    /**
     * Builds labels for a new opening and presents options below this control.
     * Detached, empty, or disabled controls do not open. Formatter errors propagate.
     */
    public void open() {
        if (isDisabled() || getRoot() == null || items.isEmpty()) return;
        var commands = new java.util.ArrayList<NanoPopupMenu.Item>();
        for (int i = 0; i < items.size(); i++) {
            int index = i;
            commands.add(new NanoPopupMenu.Item(Objects.requireNonNull(formatter.apply(items.get(i))), () -> select(index), true));
        }
        popup.items(commands).showBelow(this);
        if (selected >= 0) popup.highlight(selected);
    }

    /**
     * Opens on Up or Down, initially focusing the selected option when available.
     * Enter and Space use the normal button toggle action; disabled controls ignore keys.
     * @param event routed control key press
     */
    @Override public void onKeyPress(valthorne.event.events.KeyPressEvent event) {
        if (isDisabled()) return;
        if (event.getKey() == valthorne.Keyboard.DOWN || event.getKey() == valthorne.Keyboard.UP) {
            open(); event.consume();
        } else if (event.getKey() == valthorne.Keyboard.ENTER || event.getKey() == valthorne.Keyboard.SPACE) {
            if (isOpen()) close(); else open(); event.consume();
        }
    }

    /**
     * Reports whether the dropdown currently owns an attached option overlay.
     * @return true while open
     */
    public boolean isOpen() { return popup.isOpen(); }

    /**
     * Dismisses options without modifying selection or notifying listeners.
     */
    public void close() { popup.close(); }

    /**
     * Removes the option overlay when its anchor leaves the UI tree.
     */
    @Override public void onDestroy() { close(); }
}
