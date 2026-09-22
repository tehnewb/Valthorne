package valthorne.tick;

import java.util.function.Consumer;

/**
 * Immutable definition of one delayed action in a {@link Tick} schedule.
 *
 * <p>The duration, operation, and optional callback are fixed at construction.
 * Mutable playback progress remains on the owning tick so reset and restart
 * can replay the registered definitions. This implementation type and its
 * final fields are package-private, not part of the public fluent API.</p>
 *
 * @author Albert Beaupre
 * @since September 22nd, 2026
 */
final class ScheduledAction {
    final double seconds; // Unscaled delay after the preceding scheduled action.
    final ActionKind kind; // Operation invoked after the delay.
    final Consumer<Tick> callback; // Custom action, or null for built-in operations.

    /**
     * Stores a validated schedule step.
     *
     * @param seconds nonnegative finite duration
     * @param kind operation to perform; must be {@link ActionKind#CUSTOM} when
     *             {@code callback} is non-null
     * @param callback action for custom operations, otherwise null
     */
    ScheduledAction(double seconds, ActionKind kind, Consumer<Tick> callback) {
        this.seconds = seconds;
        this.kind = kind;
        this.callback = callback;
    }
}
