package valthorne.ui.nodes.nano;

import valthorne.Keyboard;
import valthorne.Mouse;
import valthorne.event.events.KeyPressEvent;
import valthorne.event.events.MouseDragEvent;
import valthorne.event.events.MousePressEvent;
import valthorne.event.events.MouseReleaseEvent;
import valthorne.event.events.MouseScrollEvent;
import valthorne.ui.LayoutValue;
import valthorne.ui.UIInputEvent;
import valthorne.ui.UINode;
import valthorne.ui.theme.ResolvedStyle;
import valthorne.ui.theme.UITokens;

import java.util.Objects;
import java.util.function.DoubleConsumer;

/**
 * Bounded NanoVG numeric field with centered text and small stacked arrows at its
 * right edge. One rounded outline covers the complete input and follows focus.
 * Enter and focus loss commit text; Up/Down and the wheel step values, while
 * Home/End retain ordinary caret navigation. Middle-button dragging right or upward scrubs values
 * in six-pixel increments, keeping capture when the pointer leaves the control.
 * Escape restores pending text or cancels the active scrub. Invalid text is
 * restored to the last committed value. Programmatic value changes are silent;
 * successful user changes notify once, after all child controls are synchronized.
 * Pressing an arrow steps immediately, then repeats after a short hold delay;
 * releasing never adds a second step. Arrow input retains focus in the editor.
 * <pre>{@code
 * NanoNumberSpinner count = new NanoNumberSpinner(0, 100, 1, 10);
 * count.onChange(value -> setCount((int) value));
 * }</pre>
 * All numbers must be finite. Steps clamp at endpoints without wrapping; stepping
 * does not quantize manually entered values. Use from the owning UI thread.
 * @author Albert Beaupre
 */
public class NanoNumberSpinner extends NanoContainer {

    /**
     * Compact control height used when the theme supplies no density-scaled height.
     */
    private static final float DEFAULT_CONTROL_HEIGHT = 26f;

    /**
     * Width of each step button at the default control height.
     */
    private static final float DEFAULT_STEP_WIDTH = 20f;

    /**
     * Screen-pixel movement needed for each step while dragging the middle button.
     */
    private static final float SCRUB_PIXELS_PER_STEP = 6f;

    /**
     * Delay before a held arrow begins repeating, in seconds.
     */
    private static final float REPEAT_DELAY = .4f;

    /**
     * Time between subsequent arrow steps, in seconds.
     */
    private static final float REPEAT_INTERVAL = .08f;

    private double min; // Finite inclusive lower endpoint, replaceable through range configuration.
    private double max; // Finite inclusive upper endpoint, replaceable through range configuration.
    private final double step; // Finite positive increment size.
    private double value; // Last committed finite value within bounds.
    private final NanoTextField editor = new NanoSpinnerTextField(this::commit); // Owned decimal field with focus-loss submission.
    private final NanoButton decrease = new NanoButton(); // Owned lower half-arrow decreasing the numeric value.
    private final NanoButton increase = new NanoButton(); // Owned upper half-arrow increasing the numeric value.
    private final NanoContainer arrows = new NanoContainer(); // Compact column docking both arrows at the right edge.
    private UINode repeatTarget; // Captured arrow held by the primary button; null while idle.
    private int repeatDirection; // Signed unit step for the currently held arrow.
    private float repeatRemaining; // Seconds before the next held-arrow step.
    private LayoutValue automaticHeight = LayoutValue.points(DEFAULT_CONTROL_HEIGHT); // Spinner-generated height, retaining identity to preserve caller overrides.
    private float controlHeight = DEFAULT_CONTROL_HEIGHT; // Last density-scaled compact height applied to owned controls.
    private DoubleConsumer change = value -> {}; // Synchronous user-change callback.
    private double wheelRemainder; // Unconsumed fractional wheel detents.
    private UINode scrubTarget; // Captured child during middle-button scrubbing; null when idle.
    private float scrubX; // Screen X coordinate at the start of the current scrub.
    private float scrubY; // Screen Y coordinate at the start of the current scrub.
    private double scrubValue; // Committed value at the start of the current scrub.

    /**
     * Creates the bounded editor, clamping its initial value without notifying.
     * @param min inclusive lower endpoint
     * @param max inclusive upper endpoint, at least min
     * @param step positive increment magnitude
     * @param value finite initial value
     * @throws IllegalArgumentException if configuration is nonfinite, reversed, or step is not positive
     */
    public NanoNumberSpinner(double min, double max, double step, double value) {
        if (!Double.isFinite(min) || !Double.isFinite(max) || !Double.isFinite(step) || min > max || step <= 0 || !Double.isFinite(value))
            throw new IllegalArgumentException("Invalid numeric range");
        this.min = min;
        this.max = max;
        this.step = step;
        setClickable(true);
        setScrollable(true);
        getLayout()
                .row()
                .itemsCenter()
                .minWidth(60)
                .height(automaticHeight);
        decrease.getLayout()
                .widthPercent(100)
                .heightPercent(50)
                .minHeight(0)
                .noShrink();
        increase.getLayout()
                .widthPercent(100)
                .heightPercent(50)
                .minHeight(0)
                .noShrink();
        editor.getLayout()
                .width(0)
                .grow(1)
                .minWidth(0)
                .heightPercent(100);
        arrows.getLayout()
                .column()
                .width(DEFAULT_STEP_WIDTH)
                .heightPercent(100)
                .noShrink();
        editor.setStyleName("spinner-editor");
        decrease.setStyleName("spinner-step");
        increase.setStyleName("spinner-step");
        decrease.setFocusable(false);
        increase.setFocusable(false);
        decrease.add(new NanoSpinnerIndicator(false));
        increase.add(new NanoSpinnerIndicator(true));
        decrease.setTooltip("Decrease value");
        increase.setTooltip("Increase value");
        decrease.action(button -> step(-1));
        increase.action(button -> step(1));
        editor.action(field -> commit());
        arrows.add(increase, decrease);
        add(new NanoSpinnerSurface(), editor, arrows);
        value(value);
    }

    /**
     * Reads the last committed value; uncommitted text is not parsed here.
     * @return current bounded number
     */
    public double getValue() {
        return value;
    }

    /**
     * Borrows the owned editor for typography, focus, or explicit text entry.
     * @return editor; use commit to apply its pending text
     */
    public NanoTextField getEditor() {
        return editor;
    }

    /**
     * Silently clamps and stores a finite number, replacing pending text.
     * Endpoint buttons remain available until their active middle capture ends,
     * allowing a scrub to reverse direction after reaching a boundary.
     * @param value requested finite number
     * @return this spinner
     * @throws IllegalArgumentException if value is NaN or infinite
     */
    public NanoNumberSpinner value(double value) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Value must be finite");
        this.value = Math.clamp(value, min, max);
        editor.text(this.value == Math.rint(this.value) && this.value >= Long.MIN_VALUE && this.value < 0x1p63 ? Long.toString((long) this.value) : Double.toString(this.value));
        decrease.setEnabled(this.value > min || scrubTarget == decrease && decrease.isPressed());
        increase.setEnabled(this.value < max || scrubTarget == increase && increase.isPressed());
        return this;
    }

    /**
     * Replaces the inclusive bounds, silently clamping the committed value and
     * synchronizing its text and endpoint buttons. Unchanged bounds do nothing.
     * New bounds apply immediately during an active middle-button scrub; the
     * original scrub value remains its movement origin until release.
     *
     * @param min finite inclusive lower endpoint
     * @param max finite inclusive upper endpoint, at least min
     * @return this spinner
     * @throws IllegalArgumentException if either endpoint is nonfinite or max is below min
     */
    public NanoNumberSpinner range(double min, double max) {
        if (!Double.isFinite(min) || !Double.isFinite(max) || min > max)
            throw new IllegalArgumentException("Invalid numeric range");
        if (this.min == min && this.max == max) return this;
        this.min = min;
        this.max = max;
        return value(value);
    }

    /**
     * Replaces the callback used by commits and stepping, without an initial event.
     * @param listener nonnull callback receiving the committed value
     * @return this spinner
     */
    public NanoNumberSpinner onChange(DoubleConsumer listener) {
        change = Objects.requireNonNull(listener);
        return this;
    }

    /**
     * Parses Java decimal syntax and commits finite input, clamping to the bounds.
     * Invalid input restores the prior text and returns false without notification.
     * @return whether enabled input was a valid finite number
     */
    public boolean commit() {
        if (isDisabled()) return false;
        double parsed = parseEditorValue();
        if (!Double.isFinite(parsed)) return false;
        apply(parsed);
        return true;
    }

    /**
     * Reads finite pending numeric text without notifying or committing it. Invalid
     * text restores the current committed number and returns a NaN sentinel.
     *
     * @return parsed number, or NaN when pending text is invalid
     */
    private double parseEditorValue() {
        double parsed;
        try {
            parsed = Double.parseDouble(editor.getText().trim());
        } catch (NumberFormatException failure) {
            value(value);
            return Double.NaN;
        }
        if (!Double.isFinite(parsed)) {
            value(value);
            return Double.NaN;
        }
        return Math.clamp(parsed, min, max);
    }

    /**
     * Steps from the committed value; pending text is replaced. Overflow saturates
     * at the appropriate endpoint. Disabled controls and zero direction do nothing.
     * @param direction negative to decrease, positive to increase, zero to leave unchanged
     */
    public void step(int direction) {
        if (isDisabled() || direction == 0) return;
        adjust(direction > 0 ? 1 : -1);
    }

    /**
     * Applies any number of whole user steps in one update and notification.
     * Finite arithmetic overflow saturates at the corresponding endpoint.
     *
     * @param increments signed step count
     */
    private void adjust(double increments) {
        if (increments == 0) return;
        double next = value + increments * step;
        apply(Double.isFinite(next) ? next : increments > 0 ? max : min);
    }

    /**
     * Applies wheel or arrow-key steps to pending text in one numeric notification.
     * Invalid text restores the committed value before adjustment. The caret ends
     * after the updated number, matching native numeric-field stepping.
     *
     * @param increments signed user step count, including zero for a fractional wheel event
     */
    private void adjustEditor(double increments) {
        double pending = parseEditorValue();
        if (!Double.isFinite(pending)) pending = value;
        double next = pending + increments * step;
        apply(Double.isFinite(next) ? next : increments > 0 ? max : min);
        editor.caretIndex(editor.getText().length());
    }

    /**
     * Commits a user value before notifying, suppressing duplicate numeric changes.
     * @param next finite candidate value
     */
    private void apply(double next) {
        double before = value;
        value(next);
        if (before != value) change.accept(value);
    }

    /**
     * Ends middle-button adjustment and restores endpoint-button availability.
     * Keeping the current value makes pointer cancellation match a completed scrub.
     */
    private void terminateScrub() {
        scrubTarget = null;
        wheelRemainder = 0;
        value(value);
    }

    @Override
    protected void applyLayout() {
        ResolvedStyle style = getStyle();
        Float themedHeight = style == null ? null : style.get(UITokens.CONTROL_HEIGHT);
        float height = themedHeight != null && Float.isFinite(themedHeight) && themedHeight > 0 ? themedHeight : DEFAULT_CONTROL_HEIGHT;
        if (controlHeight != height) {
            if (getLayout().getHeight() == automaticHeight || getLayout().getHeight().isAuto()) {
                automaticHeight = LayoutValue.points(height);
                getLayout().height(automaticHeight);
            }
            float width = DEFAULT_STEP_WIDTH * height / DEFAULT_CONTROL_HEIGHT;
            arrows.getLayout().width(width);
            controlHeight = height;
        }
        super.applyLayout();
    }

    @Override
    public void onInputPreview(UIInputEvent context) {
        if (isDisabled()) return;
        if (context.event() instanceof MousePressEvent press && press.getButton() == Mouse.LEFT && (context.target() == increase || context.target() == decrease)) {
            commit();
            repeatTarget = context.target();
            repeatDirection = repeatTarget == increase ? 1 : -1;
            repeatRemaining = REPEAT_DELAY;
            if (getRoot() != null) getRoot().setFocusTo(editor);
            step(repeatDirection);
            editor.caretIndex(editor.getText().length());
            return;
        }
        if (context.event() instanceof MouseReleaseEvent release && release.getButton() == Mouse.LEFT && (context.target() == increase || context.target() == decrease)) {
            repeatTarget = null;
            context.consume();
            return;
        }
        if (context.event() instanceof MousePressEvent press && press.getButton() == Mouse.MIDDLE) {
            commit();
            scrubTarget = context.target();
            scrubX = press.getX();
            scrubY = press.getY();
            scrubValue = value;
            wheelRemainder = 0;
            // Target delivery retains the root's capture; the bubble hook consumes this press.
            return;
        }
        if (context.event() instanceof MouseDragEvent drag && drag.getButton() == Mouse.MIDDLE && scrubTarget != null && scrubTarget.isPressed()) {
            double increments = (long) ((drag.getToX() - scrubX + drag.getToY() - scrubY) / SCRUB_PIXELS_PER_STEP);
            double next = scrubValue + increments * step;
            double bounded = Math.clamp(Double.isFinite(next) ? next : increments > 0 ? max : min, min, max);
            if (bounded != value) apply(bounded);
            context.consume();
            return;
        }
        if (context.event() instanceof MouseReleaseEvent release && release.getButton() == Mouse.MIDDLE) {
            terminateScrub();
            context.consume();
            return;
        }
        if (!(context.event() instanceof KeyPressEvent key)) return;
        switch (key.getKey()) {
            case Keyboard.UP -> adjustEditor(1);
            case Keyboard.DOWN -> adjustEditor(-1);
            case Keyboard.ESCAPE -> {
                if (scrubTarget != null && scrubTarget.isPressed()) apply(scrubValue);
                terminateScrub();
                repeatTarget = null;
            }
            default -> { return; }
        }
        context.consume();
    }

    @Override
    public void onInputBubble(UIInputEvent context) {
        if (context.event() instanceof MousePressEvent press && (press.getButton() == Mouse.MIDDLE && scrubTarget != null || press.getButton() == Mouse.LEFT && repeatTarget != null))
            context.consume();
    }

    @Override
    public void onMouseScroll(MouseScrollEvent event) {
        if (isDisabled()) return;
        double offset = event.preciseYOffset();
        if (offset == 0) return;
        if (wheelRemainder * offset < 0) wheelRemainder = 0;
        wheelRemainder += offset;
        double increments = Math.copySign(Math.floor(Math.abs(wheelRemainder)), wheelRemainder);
        wheelRemainder -= increments;
        adjustEditor(increments);
        event.consume();
    }

    @Override
    public void onPointerCancel() {
        super.onPointerCancel();
        repeatTarget = null;
        terminateScrub();
    }

    @Override
    public void update(float delta) {
        super.update(delta);
        if (scrubTarget != null && (getRoot() == null || getRoot().getCaptured() != scrubTarget || !scrubTarget.isPressed()))
            terminateScrub();
        if (repeatTarget == null) return;
        if (getRoot() == null || getRoot().getCaptured() != repeatTarget || !repeatTarget.isPressed() || repeatTarget.isDisabled()) {
            repeatTarget = null;
            return;
        }
        // Captured motion sends drag events, so the ordinary hovered node may be stale.
        if (getRoot().findNodeAt(Mouse.getX(), Mouse.getY(), UINode.CLICKABLE_BIT) != repeatTarget) {
            repeatRemaining = REPEAT_DELAY;
            return;
        }
        repeatRemaining -= delta;
        if (repeatRemaining > 0) return;
        // Skip stale repeats after a stalled frame instead of jumping many values.
        repeatRemaining = REPEAT_INTERVAL;
        step(repeatDirection);
        editor.caretIndex(editor.getText().length());
    }
}
