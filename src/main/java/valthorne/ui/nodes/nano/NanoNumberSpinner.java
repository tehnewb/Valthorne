package valthorne.ui.nodes.nano;

import valthorne.Keyboard;
import valthorne.event.events.KeyPressEvent;
import valthorne.ui.UIInputEvent;
import java.util.Objects;
import java.util.function.DoubleConsumer;

/**
 * NanoVG variant. Bounded numeric editor with decrement/increment buttons and editable decimal text.
 * Enter commits text; Up/Down step and Home/End select the bounds. Invalid text is
 * restored to the last committed value. Programmatic value changes are silent;
 * successful user changes notify once, after all child controls are synchronized.
 * <pre>{@code
 * NanoNumberSpinner count = new NanoNumberSpinner(0, 100, 1, 10);
 * count.onChange(value -> setCount((int) value));
 * }</pre>
 * All numbers must be finite. Steps clamp at endpoints without wrapping; stepping
 * does not quantize manually entered values. Use from the owning UI thread.
 * @author Albert Beaupre
 */
public class NanoNumberSpinner extends NanoContainer {
    private final double min, max, step; // Finite inclusive bounds and positive step size.
    private double value; // Last committed finite value within bounds.
    private final NanoTextField editor = new NanoTextField(); // Owned decimal-entry control.
    private final NanoButton decrease = new NanoButton("-"); // Owned decrement action.
    private final NanoButton increase = new NanoButton("+"); // Owned increment action.
    private DoubleConsumer change = value -> {}; // Synchronous user-change callback.

    /**
     * Creates the bounded editor, clamping its initial value without notifying.
     * @param min inclusive lower endpoint
     * @param max inclusive upper endpoint, at least min
     * @param step positive increment magnitude
     * @param value finite initial value
     * @throws IllegalArgumentException if configuration is nonfinite, reversed, or step is not positive
     */
    public NanoNumberSpinner(double min, double max, double step, double value) {
        if (!Double.isFinite(min) || !Double.isFinite(max) || !Double.isFinite(step)
                || min > max || step <= 0 || !Double.isFinite(value)) throw new IllegalArgumentException("Invalid numeric range");
        this.min = min; this.max = max; this.step = step;
        getLayout().row().height(36).minWidth(180);
        decrease.getLayout().width(36).heightPercent(100).noShrink();
        increase.getLayout().width(36).heightPercent(100).noShrink();
        editor.getLayout().grow(1).minWidth(0).heightPercent(100);
        decrease.action(button -> step(-1)); increase.action(button -> step(1));
        editor.action(field -> commit());
        add(decrease, editor, increase); value(value);
    }

    /**
     * Reads the last committed value; uncommitted text is not parsed here.
     * @return current bounded number
     */
    public double getValue() { return value; }

    /**
     * Borrows the owned editor for typography, focus, or explicit text entry.
     * @return editor; use commit to apply its pending text
     */
    public NanoTextField getEditor() { return editor; }

    /**
     * Silently clamps and stores a finite number, replacing pending text.
     * @param value requested finite number
     * @return this spinner
     * @throws IllegalArgumentException if value is NaN or infinite
     */
    public NanoNumberSpinner value(double value) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Value must be finite");
        this.value = Math.clamp(value, min, max);
        editor.text(Double.toString(this.value));
        decrease.setEnabled(this.value > min); increase.setEnabled(this.value < max);
        return this;
    }

    /**
     * Replaces the callback used by commits and stepping, without an initial event.
     * @param listener nonnull callback receiving the committed value
     * @return this spinner
     */
    public NanoNumberSpinner onChange(DoubleConsumer listener) { change = Objects.requireNonNull(listener); return this; }

    /**
     * Parses Java decimal syntax and commits finite input, clamping to the bounds.
     * Invalid input restores the prior text and returns false without notification.
     * @return whether enabled input was a valid finite number
     */
    public boolean commit() {
        if (isDisabled()) return false;
        double parsed;
        try { parsed = Double.parseDouble(editor.getText().trim()); }
        catch (NumberFormatException failure) { value(value); return false; }
        if (!Double.isFinite(parsed)) { value(value); return false; }
        apply(parsed); return true;
    }

    /**
     * Steps from the committed value; pending text is replaced. Overflow saturates
     * at the appropriate endpoint. Disabled controls and zero direction do nothing.
     * @param direction negative to decrease, positive to increase, zero to leave unchanged
     */
    public void step(int direction) {
        if (isDisabled() || direction == 0) return;
        double next = value + (direction > 0 ? step : -step);
        apply(Double.isFinite(next) ? next : direction > 0 ? max : min);
    }

    /**
     * Commits a user value before notifying, suppressing duplicate numeric changes.
     * @param next finite candidate value
     */
    private void apply(double next) {
        double before = value; value(next);
        if (before != value) change.accept(value);
    }

    /**
     * Handles stepping keys over the editor or either button before child dispatch.
     * Other keys retain the text field's normal editing semantics.
     * @param context routed preview event
     */
    @Override public void onInputPreview(UIInputEvent context) {
        if (isDisabled() || !(context.event() instanceof KeyPressEvent key)) return;
        switch (key.getKey()) {
            case Keyboard.UP -> step(1);
            case Keyboard.DOWN -> step(-1);
            case Keyboard.HOME -> apply(min);
            case Keyboard.END -> apply(max);
            default -> { return; }
        }
        context.consume();
    }
}
