package valthorne.state;

/**
 * Immutable transition metadata reused whenever a registered rule is taken.
 * Higher priority wins; equal priorities use earlier registration order.
 * A null source marks a global rule. A null target occurs only on explicit stop.
 *
 * <p>Rules are created with {@link StateMachine#from(String)} or the original
 * addTransition methods. State time, required events, and guards must all pass.
 * Self-targeting rules are ignored rather than consuming a transition budget.</p>
 *
 * @param <C> user-data type
 * @author Albert Beaupre
 * @since February 12th, 2026
 */
public final class Transition<C> {
    private final State<C> from; // Source identity; null for global or initial metadata.
    final StateSlot<C> target; // Direct destination slot, avoiding transition lookup.
    private final Trigger trigger; // Required trigger metadata, or null.
    final int event; // Required event bit index, or -1.
    final Guard<C> guard; // Optional read-only guard.
    final double seconds; // Minimum scaled state time in seconds.
    private final int priority; // Higher priorities are considered first.
    private final long order; // Stable registration-order tie breaker.
    private final String reason; // Nonblank debugging reason.
    final TransitionAction<C> action; // Optional action between exit and entry.

    /**
     * Stores a validated definition.
     *
     * @param from     source or null
     * @param target   target slot or null for stop metadata
     * @param trigger  optional trigger
     * @param event    event bit index or minus one
     * @param guard    optional guard
     * @param seconds  minimum state time
     * @param priority rule priority
     * @param order    registration order
     * @param reason   debug reason
     * @param action   optional action
     */
    Transition(State<C> from, StateSlot<C> target, Trigger trigger, int event, Guard<C> guard, double seconds, int priority, long order, String reason, TransitionAction<C> action) {
        this.from = from;
        this.target = target;
        this.trigger = trigger;
        this.event = event;
        this.guard = guard;
        this.seconds = seconds;
        this.priority = priority;
        this.order = order;
        this.reason = reason == null || reason.isBlank() ? "transition" : reason;
        this.action = action;
    }

    /**
     * Returns source identity.
     *
     * @return source or null for a global/initial transition
     */
    public State<C> from() {
        return from;
    }

    /**
     * Returns destination identity.
     *
     * @return destination, or null for explicit stop metadata
     */
    public State<C> to() {
        return target == null ? null : target.state;
    }

    /**
     * Returns required event metadata.
     *
     * @return trigger or null
     */
    public Trigger requiredTrigger() {
        return trigger;
    }

    /**
     * Returns the read-only guard.
     *
     * @return guard or null
     */
    public Guard<C> guard() {
        return guard;
    }

    /**
     * Returns minimum state time through the original float API.
     *
     * @return minimum scaled seconds
     */
    public float minTimeInStateSec() {
        return (float) seconds;
    }

    /**
     * Returns minimum state time without narrowing.
     *
     * @return minimum scaled seconds
     */
    public double afterSeconds() {
        return seconds;
    }

    /**
     * Returns priority.
     *
     * @return higher values win
     */
    public int priority() {
        return priority;
    }

    /**
     * Returns insertion order.
     *
     * @return lower values win tied priorities
     */
    public long order() {
        return order;
    }

    /**
     * Returns the debugging reason.
     *
     * @return nonblank reason
     */
    public String reason() {
        return reason;
    }

    /**
     * Returns the action.
     *
     * @return action or null
     */
    public TransitionAction<C> action() {
        return action;
    }
}
