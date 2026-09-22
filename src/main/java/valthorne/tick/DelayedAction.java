package valthorne.tick;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Registers one delayed action and returns its owning {@link Tick} for chaining.
 *
 * <p>Obtain an instance by selecting a unit on an {@link After} builder. Finish
 * the definition with {@link #thenPause()}, {@link #thenContinue()},
 * {@link #thenStop()}, {@link #thenEnd()}, or {@link #then(Consumer)}.
 * The first successful terminal call appends exactly one schedule step;
 * additional terminal calls on the same instance are rejected.</p>
 *
 * <p>Scheduled waits use unscaled update time and continue during a pause.
 * They freeze when the tick is stopped. Builders are not thread-safe and must
 * be used on the same owning thread as their tick.</p>
 *
 * @author Albert Beaupre
 * @since September 22nd, 2026
 */
public final class DelayedAction {
    private final Tick owner; // Tick that will own the completed step.
    private final double seconds; // Validated wait duration in unscaled update seconds.
    private boolean used; // Whether this builder has already registered its action.

    /**
     * Creates the terminal builder after successful time-unit selection.
     *
     * @param owner   owning tick
     * @param seconds finite, nonnegative duration in seconds
     */
    DelayedAction(Tick owner, double seconds) {
        this.owner = owner;
        this.seconds = seconds;
    }

    /**
     * Schedules a one-shot pause request; an already paused tick is unchanged.
     *
     * @return the owning tick for further chaining
     * @throws IllegalStateException if this builder has already been used
     */
    public Tick thenPause() {
        return finish(ActionKind.PAUSE, null);
    }

    /**
     * Schedules a one-shot continuation request for a paused tick.
     *
     * <p>It never starts a stopped tick, and a matching pause condition blocks
     * it. A blocked or unnecessary request is still consumed. Unlike a
     * {@code continueIf} predicate, it is not repeatedly evaluated.</p>
     *
     * @return the owning tick for further chaining
     * @throws IllegalStateException if this builder has already been used
     */
    public Tick thenContinue() {
        return finish(ActionKind.CONTINUE, null);
    }

    /**
     * Schedules an end request, invoking the normal end listener.
     *
     * <p>Subsequent steps remain frozen until an explicit {@link Tick#start()}.
     * Calling {@link Tick#restart()} instead replays the whole schedule.</p>
     *
     * @return the owning tick for further chaining
     * @throws IllegalStateException if this builder has already been used
     */
    public Tick thenStop() {
        return finish(ActionKind.STOP, null);
    }

    /**
     * Schedules the same end request as {@link #thenStop()}.
     *
     * @return the owning tick for further chaining
     * @throws IllegalStateException if this builder has already been used
     */
    public Tick thenEnd() {
        return thenStop();
    }

    /**
     * Schedules a custom one-shot action, including while the tick is paused.
     *
     * <p>The step is consumed before invocation. Exceptions propagate, and
     * lifecycle, timing, or schedule changes to this tick interrupt the
     * current update just as they do in a regular tick callback.</p>
     *
     * @param action non-null action receiving the owning tick
     * @return the owning tick for further chaining
     * @throws NullPointerException  if action is null
     * @throws IllegalStateException if this builder has already been used
     */
    public Tick then(Consumer<Tick> action) {
        return finish(ActionKind.CUSTOM, Objects.requireNonNull(action, "action"));
    }

    /**
     * Commits exactly one terminal operation.
     *
     * @param kind   operation to register
     * @param action custom callback or null for a built-in operation
     * @return the owning tick
     * @throws IllegalStateException if this builder has already been used
     */
    private Tick finish(ActionKind kind, Consumer<Tick> action) {
        if (used) throw new IllegalStateException("This delayed action has already been registered.");
        used = true;
        return owner.appendAction(seconds, kind, action);
    }
}
