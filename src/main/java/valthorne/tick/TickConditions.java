package valthorne.tick;

import java.util.function.Predicate;

/**
 * Lazily allocated predicate groups for a {@link Tick}.
 *
 * <p>A tick without conditions holds no instance of this type. Single conditions
 * are stored directly; repeated registrations preserve Predicate short-circuit
 * composition and registration order. All access is on the tick's owning thread.</p>
 *
 * @author Albert Beaupre
 * @since September 22nd, 2026
 */
final class TickConditions {
    Predicate<Tick> endCondition; // OR-combined conditions that end an active tick.
    Predicate<Tick> pauseCondition; // OR-combined conditions that request or maintain a pause.
    Predicate<Tick> continueCondition; // OR-combined conditions that resume a paused tick.
    Predicate<Tick> runCondition; // AND-combined conditions that permit callback time to advance.
    Predicate<Tick> skipCondition; // OR-combined conditions that consume intervals without callbacks.

    /**
     * Creates empty condition storage.
     */
    TickConditions() {
    }

    /**
     * Checks whether the owner can release this optional storage.
     *
     * @return true when every group is empty
     */
    boolean isEmpty() {
        return endCondition == null && pauseCondition == null && continueCondition == null && runCondition == null && skipCondition == null;
    }
}
