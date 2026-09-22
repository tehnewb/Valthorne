package valthorne.tick;

import java.util.Arrays;

/**
 * Compact, single-owner storage for a tick's ordered action sequence.
 *
 * <p>Parallel arrays replace the list wrapper and one object per scheduled step.
 * The first array stores unscaled delays; the second holds shared built-in action
 * constants or custom callbacks. Arrays grow only during registration, never
 * during playback. Completed slots remain available for restart and therefore
 * retain their custom callbacks until the schedule is cleared or the tick dies.</p>
 *
 * @author Albert Beaupre
 * @since September 22nd, 2026
 */
final class TickSchedule {
    double[] delays = new double[2]; // Registered delays, in unscaled seconds.
    Object[] actions = new Object[2]; // Shared action constants or custom callbacks.
    int size; // Number of registered entries, including completed ones.
    int index; // Index of the next entry to execute.
    double elapsed; // Seconds already accepted for the current wait.
    double backlog; // Unprocessed delta retained after exhausting the action budget.

    /**
     * Creates storage for two entries, only when the first action is registered.
     */
    TickSchedule() {
    }

    /**
     * Appends a validated step, growing both arrays geometrically when required.
     *
     * @param seconds finite nonnegative delay
     * @param action  built-in action constant or custom callback
     */
    void append(double seconds, Object action) {
        if (size == delays.length) {
            int capacity = delays.length + (delays.length >> 1) + 1;
            if (capacity < 0) throw new OutOfMemoryError("Tick schedule capacity overflow.");
            // Commit only after both allocations succeed.
            double[] newDelays = Arrays.copyOf(delays, capacity);
            Object[] newActions = Arrays.copyOf(actions, capacity);
            delays = newDelays;
            actions = newActions;
        }
        delays[size] = seconds;
        actions[size++] = action;
    }

    /**
     * Rewinds playback without discarding definitions or allocating new arrays.
     */
    void rewind() {
        index = 0;
        elapsed = 0.0;
        backlog = 0.0;
    }
}
