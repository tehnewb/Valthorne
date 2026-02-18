package valthorne.state;

/**
 * Context passed into states, guards, and transition actions.
 *
 * <h2>Example</h2>
 * <pre>{@code
 * // Your data model.
 * public final class PlayerData {
 *     public boolean grounded;
 *     public float vx;
 * }
 *
 * State<PlayerData> run = new State<>() {
 *     @Override public void onEnter(StateContext<PlayerData> ctx) {
 *         // ctx.data() gives you your object.
 *         // ctx.timeInStateSec() starts at 0 on enter.
 *     }
 *
 *     @Override public void onUpdate(StateContext<PlayerData> ctx, float dt) {
 *         float time = ctx.timeInStateSec();
 *         Transition<PlayerData> last = ctx.lastTransition();
 *     }
 *
 *     @Override public void onExit(StateContext<PlayerData> ctx) {}
 * };
 * }</pre>
 *
 * <p>This object is owned by the {@link StateMachine} and is reused each update.</p>
 * <p>It provides:</p>
 * <ul>
 *     <li>Access to your user data via {@link #data()}.</li>
 *     <li>Current state via {@link #currentState()}.</li>
 *     <li>Time spent in the current state via {@link #timeInStateSec()}.</li>
 *     <li>Last transition taken via {@link #lastTransition()} (includes reason).</li>
 * </ul>
 *
 * @param <C> user-defined context type
 * @author Albert Beaupre
 * @since February 12th, 2026
 */
public final class StateContext<C> {

    private final StateMachine<C> machine;     // Owning FSM instance.
    private final C data;                      // User-supplied shared data object.

    State<C> current;                          // Current active state (package-private for machine).
    float timeInStateSec;                      // Seconds spent in current state (package-private for machine).
    Transition<C> lastTransition;              // Last transition taken (package-private for machine).

    /**
     * Creates a new context.
     *
     * <p>This constructor is package-private because the {@link StateMachine} owns the lifecycle.</p>
     *
     * @param machine owning state machine
     * @param data    user data object
     */
    StateContext(StateMachine<C> machine, C data) {
        this.machine = machine;
        this.data = data;
    }

    /**
     * Returns the owning FSM.
     *
     * <p>Useful if a state wants to query current state, force changes, or fire triggers.</p>
     *
     * @return the state machine instance
     */
    public StateMachine<C> machine() {
        return machine;
    }

    /**
     * Returns your shared user data object.
     *
     * <p>This is the primary mechanism to share state between states and guards.</p>
     *
     * @return user context data (may be null depending on your design)
     */
    public C data() {
        return data;
    }

    /**
     * Returns the currently active state.
     *
     * @return current state (may be null if machine is idle)
     */
    public State<C> currentState() {
        return current;
    }

    /**
     * Returns how many seconds have elapsed since the current state was entered.
     *
     * <p>This resets to 0 when a transition is taken or when the initial state is set.</p>
     *
     * @return time in current state, seconds
     */
    public float timeInStateSec() {
        return timeInStateSec;
    }

    /**
     * Returns the last transition taken.
     *
     * <p>This can be used to inspect {@link Transition#reason()} or other transition metadata.</p>
     *
     * @return last transition, or null if none taken yet
     */
    public Transition<C> lastTransition() {
        return lastTransition;
    }
}