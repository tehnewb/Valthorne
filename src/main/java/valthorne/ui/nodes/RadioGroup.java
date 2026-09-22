package valthorne.ui.nodes;

import valthorne.Keyboard;
import valthorne.event.events.KeyPressEvent;
import valthorne.ui.UIInputEvent;
import java.util.List;
import java.util.Objects;
import java.util.function.IntConsumer;

/**
 * Mutually exclusive, vertically arranged labeled choices. Selection is stored by
 * index and mirrored into each themed button's selected state. Arrow keys wrap;
 * programmatic selection is silent, while user changes notify after synchronization.
 * <pre>{@code
 * RadioGroup mode = new RadioGroup(List.of("Windowed", "Fullscreen"));
 * mode.selectedIndex(0).onChange(index -> applyMode(index));
 * }</pre>
 * An empty group has no selection. A nonempty group initially selects its first
 * option; index -1 explicitly clears it. Labels are immutable snapshots.
 * @author Albert Beaupre
 */
public class RadioGroup extends Panel {
    private final List<String> labels; // Immutable labels in keyboard order.
    private final List<Button> buttons = new java.util.ArrayList<>(); // Owned choice controls.
    private int selected; // Current selected index or -1.
    private IntConsumer change = index -> {}; // User-change callback; programmatic setters are silent.

    /**
     * Copies nonnull labels and creates one selectable button per option.
     * @param labels nonnull labels, including an empty list
     */
    public RadioGroup(List<String> labels) {
        this.labels = List.copyOf(labels); getLayout().column();
        for (int i = 0; i < labels.size(); i++) {
            int index = i;
            Button button = new Button(labels.get(i)).action(b -> select(index));
            button.getLayout().height(32).widthPercent(100).noShrink();
            buttons.add(button); add(button);
        }
        selectedIndex(labels.isEmpty() ? -1 : 0);
    }

    /**
     * Reads the selected option without changing focus.
     * @return index or -1 when no option is selected
     */
    public int getSelectedIndex() { return selected; }

    /**
     * Returns the immutable label snapshot, independent of the original list.
     * @return labels in display order
     */
    public List<String> getLabels() { return labels; }

    /**
     * Silently updates all selected states after validating the index.
     * @param index valid option index or -1
     * @return this group
     * @throws IndexOutOfBoundsException if index is invalid
     */
    public RadioGroup selectedIndex(int index) {
        if (index < -1 || index >= buttons.size()) throw new IndexOutOfBoundsException(index);
        selected = index;
        for (int i = 0; i < buttons.size(); i++) buttons.get(i).setSelected(i == index);
        return this;
    }

    /**
     * Applies a user selection, moving focus to that choice when attached. Duplicate
     * choices do not notify; disabled groups ignore the request.
     * @param index valid index or -1 to clear
     */
    public void select(int index) {
        if (isDisabled()) return;
        int previous = selected; selectedIndex(index);
        if (getRoot() != null && index >= 0) getRoot().setFocusTo(buttons.get(index));
        if (previous != selected) change.accept(selected);
    }

    /**
     * Replaces the synchronous user-selection callback without emitting an event.
     * @param listener nonnull callback
     * @return this group
     */
    public RadioGroup onChange(IntConsumer listener) { change = Objects.requireNonNull(listener); return this; }

    /**
     * Wraps arrow navigation among choices while leaving activation keys to buttons.
     * @param context routed preview event
     */
    @Override public void onInputPreview(UIInputEvent context) {
        if (isDisabled() || buttons.isEmpty() || !(context.event() instanceof KeyPressEvent key)) return;
        int direction = switch (key.getKey()) {
            case Keyboard.RIGHT, Keyboard.DOWN -> 1;
            case Keyboard.LEFT, Keyboard.UP -> -1;
            default -> 0;
        };
        if (direction != 0) {
            select(selected < 0 ? direction > 0 ? 0 : buttons.size() - 1 : Math.floorMod(selected + direction, buttons.size()));
            context.consume();
        }
    }
}
