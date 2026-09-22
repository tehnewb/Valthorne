package valthorne.state;

import java.util.function.Consumer;

/**
 * Lazily allocated optional lifecycle conditions and time scaling.
 * Machines without these features do not allocate this object.
 *
 * @param <C> user-data type
 * @author Albert Beaupre
 * @since September 22nd, 2026
 */
final class StateControl<C> {
    Guard<C> end; // Any matching end condition stops the machine, including while paused.
    Guard<C> pause; // Any matching pause condition blocks automatic continuation.
    Guard<C> resume; // Any matching continuation condition resumes a paused machine.
    Guard<C> run; // AND-combined gates for time and state updates.
    Consumer<StateContext<C>> start; // Optional listener after a stopped machine starts.
    Consumer<StateContext<C>> pauseListener; // Optional listener after pausing.
    Consumer<StateContext<C>> resumeListener; // Optional listener after resuming.
    Consumer<StateContext<C>> endListener; // Optional listener after stopping.
    float scale = 1f; // Time multiplier; conditions still run at zero.
}
