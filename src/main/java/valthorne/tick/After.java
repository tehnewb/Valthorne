package valthorne.tick;

/**
 * Selects the time unit for a delayed action created by {@link Tick#after(double)}.
 *
 * <p>Each instance is a single-use builder. Selecting milliseconds, seconds,
 * minutes, or hours returns a {@link DelayedAction}, but does not yet register
 * a schedule step. A terminal action on that next builder performs registration
 * and returns the owning tick so that the fluent chain can continue.</p>
 *
 * <p>Obtain instances from {@code tick.after(amount)} rather than constructing
 * them directly. Like the owning tick, builders are not thread-safe.</p>
 *
 * @author Albert Beaupre
 * @since September 22nd, 2026
 */
public final class After {
    private final Tick owner; // Tick that will own the completed step.
    private final double amount; // Validated duration before conversion to seconds.
    private boolean used; // Whether this builder has already produced an action builder.

    /**
     * Creates a unit selector; use {@link Tick#after(double)} instead.
     *
     * @param owner  owning tick
     * @param amount validated nonnegative finite duration
     */
    After(Tick owner, double amount) {
        this.owner = owner;
        this.amount = amount;
    }

    /**
     * Interprets the duration as milliseconds.
     *
     * @return a single-use action-selection builder
     * @throws IllegalStateException if a unit was already selected
     */
    public DelayedAction milliseconds() {
        return units(0.001);
    }

    /**
     * Interprets the duration as seconds.
     *
     * @return a single-use action-selection builder
     * @throws IllegalStateException if a unit was already selected
     */
    public DelayedAction seconds() {
        return units(1.0);
    }

    /**
     * Interprets the duration as minutes of 60 seconds each.
     *
     * @return a single-use action-selection builder
     * @throws IllegalStateException    if a unit was already selected
     * @throws IllegalArgumentException if conversion overflows
     */
    public DelayedAction minutes() {
        return units(60.0);
    }

    /**
     * Interprets the duration as hours of 3,600 seconds each.
     *
     * @return a single-use action-selection builder
     * @throws IllegalStateException    if a unit was already selected
     * @throws IllegalArgumentException if conversion overflows
     */
    public DelayedAction hours() {
        return units(3600.0);
    }

    /**
     * Converts the requested duration without registering an action yet.
     *
     * @param secondsPerUnit conversion multiplier
     * @return an action-selection builder
     * @throws IllegalStateException    if a unit was already selected
     * @throws IllegalArgumentException if conversion is non-finite
     */
    private DelayedAction units(double secondsPerUnit) {
        if (used) throw new IllegalStateException("A time unit has already been selected.");
        double seconds = amount * secondsPerUnit;
        Tick.requireNonNegativeFinite(seconds, "duration in seconds");
        used = true;
        return new DelayedAction(owner, seconds);
    }
}
