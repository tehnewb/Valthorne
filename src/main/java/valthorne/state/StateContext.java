package valthorne.state;

/**
 * One reusable context per machine; it is a live view, not an event snapshot.
 *
 * <p>During exit and transition actions the source remains current. Before enter,
 * the destination, last transition, and zero state time are committed. Explicit
 * starts/changes create transition metadata; automatic transitions reuse their
 * immutable registered definition.</p>
 *
 * @param <C> user-data type
 * @author Albert Beaupre
 * @since February 12th, 2026
 */
public final class StateContext<C> {
    private final StateMachine<C> machine; // Owning runtime.
    private final C data; // Per-machine user data; may be null.
    double time; // Scaled seconds accumulated in the current state.
    float delta; // Delta of the latest accepted state update.
    Transition<C> last; // Last committed immutable transition definition.

    /**
     * Creates the machine's live context.
     *
     * @param machine owner
     * @param data    user data
     */
    StateContext(StateMachine<C> machine, C data) {
        this.machine = machine;
        this.data = data;
    }

    /**
     * Returns the owner.
     *
     * @return owning machine
     */
    public StateMachine<C> machine() {
        return machine;
    }

    /**
     * Returns user data.
     *
     * @return data supplied at construction
     */
    public C data() {
        return data;
    }

    /**
     * Returns the active state.
     *
     * @return active state, or null when stopped
     */
    public State<C> currentState() {
        return machine.getCurrentState();
    }

    /**
     * Returns state time through the original float API.
     *
     * @return scaled seconds in the current state
     */
    public float timeInStateSec() {
        return (float) time;
    }

    /**
     * Returns state time without narrowing to float.
     *
     * @return scaled seconds in the current state
     */
    public double timeInState() {
        return time;
    }

    /**
     * Returns the most recent accepted update delta.
     *
     * @return scaled delta; zero after a start, reset, or stop
     */
    public float delta() {
        return delta;
    }

    /**
     * Returns the last committed transition; stable references may be retained.
     *
     * @return immutable metadata, or null before the first start or after reset
     */
    public Transition<C> lastTransition() {
        return last;
    }
}
