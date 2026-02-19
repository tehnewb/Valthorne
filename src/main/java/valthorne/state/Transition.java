package valthorne.state;

import java.util.Objects;

/**
 * A transition rule.
 *
 * <h2>Example</h2>
 * <pre>{@code
 * Trigger jump = new Trigger("jump");
 *
 * Transition<PlayerCtx> t = new Transition<>(
 *     idle, jumpState,
 *     jump,
 *     ctx -> ctx.data().grounded(),
 *     0.05f,
 *     10,
 *     0L,
 *     "jump pressed",
 *     (ctx, tr) -> {  reset timers, play sound, etc.  }
 * );
 * }</pre>
 *
 * <p>Transitions can be:</p>
 * <ul>
 *     <li><b>State-specific</b>: {@link #from()} is non-null and must match the current state.</li>
 *     <li><b>Global</b>: {@link #from()} is null and can apply from any state.</li>
 * </ul>
 *
 * <p>Ordering:</p>
 * <ul>
 *     <li>Higher {@link #priority()} wins.</li>
 *     <li>If priorities tie, lower {@link #order()} (earlier insertion) wins.</li>
 * </ul>
 *
 * @param <C> user-defined context type
 * @author Albert Beaupre
 * @since February 12th, 2026
 */
public final class Transition<C> {

    private final State<C> from;                     // Source state; null means global/any-state.
    private final State<C> to;                       // Target state (required).
    private final Trigger requiredTrigger;           // Trigger required to take this transition; null means none.
    private final Guard<C> guard;                    // Guard predicate; null means always allowed.
    private final float minTimeInStateSec;           // Debounce/cooldown: minimum time spent in current state.
    private final int priority;                      // Priority: higher wins.
    private final long order;                        // Insertion order: lower wins (tie-breaker).
    private final String reason;                     // Human-readable reason for debugging/telemetry.
    private final TransitionAction<C> action;        // Optional action run during transition; null means none.

    /**
     * Creates a transition.
     *
     * <p>This constructor is package-private because transitions are typically created through {@link StateMachine}.</p>
     *
     * @param from              source state (nullable for global)
     * @param to                target state (non-null)
     * @param requiredTrigger   trigger required (nullable)
     * @param guard             guard predicate (nullable)
     * @param minTimeInStateSec cooldown seconds required in current state (clamped >= 0)
     * @param priority          priority (higher wins)
     * @param order             insertion order (lower wins)
     * @param reason            reason string (blank becomes default)
     * @param action            transition action (nullable)
     */
    Transition(State<C> from, State<C> to, Trigger requiredTrigger, Guard<C> guard, float minTimeInStateSec, int priority, long order, String reason, TransitionAction<C> action) {
        this.from = from;
        this.to = Objects.requireNonNull(to, "to");
        this.requiredTrigger = requiredTrigger;
        this.guard = guard;
        this.minTimeInStateSec = Math.max(0f, minTimeInStateSec);
        this.priority = priority;
        this.order = order;
        this.reason = (reason == null || reason.isBlank()) ? "transition" : reason;
        this.action = action;
    }

    /**
     * Returns the source state.
     *
     * <p>If this is a global transition, this will be null.</p>
     *
     * @return source state, or null
     */
    public State<C> from() {
        return from;
    }

    /**
     * Returns the target state.
     *
     * @return target state (never null)
     */
    public State<C> to() {
        return to;
    }

    /**
     * Returns the required trigger for this transition, if any.
     *
     * <p>If non-null, the trigger must be present in the state machine's trigger queue to be valid,
     * and will be consumed (removed) when the transition is taken.</p>
     *
     * @return required trigger, or null
     */
    public Trigger requiredTrigger() {
        return requiredTrigger;
    }

    /**
     * Returns the guard predicate, if any.
     *
     * <p>If non-null, it must return true for this transition to be valid.</p>
     *
     * @return guard, or null
     */
    public Guard<C> guard() {
        return guard;
    }

    /**
     * Returns the minimum time required in the current state before this transition becomes eligible.
     *
     * <p>This is a debounce/cooldown mechanism to prevent rapid flipping between states.</p>
     *
     * @return minimum seconds in state (>= 0)
     */
    public float minTimeInStateSec() {
        return minTimeInStateSec;
    }

    /**
     * Returns the priority for this transition.
     *
     * <p>Higher values win when multiple transitions are valid.</p>
     *
     * @return priority
     */
    public int priority() {
        return priority;
    }

    /**
     * Returns the insertion order for tie-breaking.
     *
     * <p>Lower values indicate earlier insertion.</p>
     *
     * @return insertion order
     */
    public long order() {
        return order;
    }

    /**
     * Returns a human-readable reason associated with this transition.
     *
     * <p>This is useful for debugging, logs, or telemetry.</p>
     *
     * @return reason string (never blank)
     */
    public String reason() {
        return reason;
    }

    /**
     * Returns the transition action, if any.
     *
     * <p>Actions run after oldState.onExit and before newState.onEnter.</p>
     *
     * @return action or null
     */
    public TransitionAction<C> action() {
        return action;
    }
}
