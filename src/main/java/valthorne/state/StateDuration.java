package valthorne.state;

/**
 * Single-use unit selector for minimum time in the current state.
 * This is state time, not a sequential tick schedule: it resets on every state
 * entry and does not advance while paused or when the machine time scale is zero.
 *
 * @param <C> user-data type
 * @author Albert Beaupre
 * @since September 22nd, 2026
 */
public final class StateDuration<C> {
    private final TransitionBuilder<C> owner; // Transition receiving the converted duration.
    private final double amount; // Validated quantity before unit conversion.
    private boolean used; // Whether a unit has already been chosen.

    /**
     * Creates a selector.
     *
     * @param owner  transition builder
     * @param amount validated duration
     */
    StateDuration(TransitionBuilder<C> owner, double amount) {
        this.owner = owner;
        this.amount = amount;
    }

    /**
     * Selects milliseconds.
     *
     * @return original transition builder
     * @throws IllegalStateException if a unit was already selected
     */
    public TransitionBuilder<C> milliseconds() {
        return convert(0.001);
    }

    /**
     * Selects seconds.
     *
     * @return original transition builder
     * @throws IllegalStateException if a unit was already selected
     */
    public TransitionBuilder<C> seconds() {
        return convert(1.0);
    }

    /**
     * Selects minutes of 60 seconds.
     *
     * @return original transition builder
     * @throws IllegalStateException if a unit was already selected
     * @throws IllegalArgumentException if conversion produces a non-finite duration
     */
    public TransitionBuilder<C> minutes() {
        return convert(60.0);
    }

    /**
     * Selects hours of 3,600 seconds.
     *
     * @return original transition builder
     * @throws IllegalStateException if a unit was already selected
     * @throws IllegalArgumentException if conversion produces a non-finite duration
     */
    public TransitionBuilder<C> hours() {
        return convert(3600.0);
    }

    /**
     * Validates conversion and completes this selector.
     *
     * @param scale seconds per unit
     * @return original transition builder
     * @throws IllegalStateException if this selector was already consumed
     * @throws IllegalArgumentException if the converted duration is non-finite
     */
    private TransitionBuilder<C> convert(double scale) {
        if (used) throw new IllegalStateException("A time unit has already been selected.");
        TransitionBuilder<C> result = owner.seconds(amount * scale);
        used = true;
        return result;
    }
}
