package valthorne.state;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Single-use fluent transition definition. The terminal {@code to} call registers
 * the rule; an unfinished builder does not affect the machine.
 *
 * <pre>{@code
 * machine.from("Idle").when(ctx -> ctx.data().moving).to("Run");
 * machine.from("Attack").after(500).milliseconds().to("Idle");
 * machine.any().when(ctx -> ctx.data().health <= 0).priority(100).to("Dead");
 * }</pre>
 *
 * <p>Repeated when/unless clauses are AND-combined. Use OR inside one predicate
 * or register separate rules. Delay, trigger, priority, and reason setters replace
 * their previous value. Configuration allocates; updating registered rules does not.</p>
 *
 * @param <C> user-data type
 * @author Albert Beaupre
 * @since September 22nd, 2026
 */
public final class TransitionBuilder<C> {
    private final StateMachine<C> owner; // Configuring machine.
    private final StateSlot<C> from; // Source slot; null means any state.
    private Guard<C> guard; // Combined optional guard.
    private Trigger trigger; // Optional required event.
    private double seconds; // Minimum scaled state seconds.
    private int priority; // Rule priority; larger values win.
    private String reason; // Optional debug reason.
    private TransitionAction<C> action; // Optional transition action.
    private boolean used; // Prevents registering this definition twice.

    /**
     * Creates a source-specific or global builder.
     *
     * @param owner configuring runtime
     * @param from  source slot, or null
     */
    TransitionBuilder(StateMachine<C> owner, StateSlot<C> from) {
        this.owner = owner;
        this.from = from;
    }

    /**
     * Adds a read-only context condition; all added conditions must pass.
     *
     * @param condition non-null guard
     * @return this builder
     */
    public TransitionBuilder<C> when(Guard<C> condition) {
        check();
        Objects.requireNonNull(condition, "condition");
        Guard<C> previous = guard;
        guard = previous == null ? condition : ctx -> previous.allow(ctx) && condition.allow(ctx);
        return this;
    }

    /**
     * Adds a condition capturing external data.
     *
     * @param condition non-null no-argument condition
     * @return this builder
     */
    public TransitionBuilder<C> when(Condition condition) {
        Objects.requireNonNull(condition, "condition");
        return when(ctx -> condition.test());
    }

    /**
     * Requires a context condition to be false.
     *
     * @param condition condition to negate
     * @return this builder
     */
    public TransitionBuilder<C> unless(Guard<C> condition) {
        Objects.requireNonNull(condition, "condition");
        return when(ctx -> !condition.allow(ctx));
    }

    /**
     * Requires an external condition to be false.
     *
     * @param condition condition to negate
     * @return this builder
     */
    public TransitionBuilder<C> unless(Condition condition) {
        Objects.requireNonNull(condition, "condition");
        return when(ctx -> !condition.test());
    }

    /**
     * Requires a named queued event, consumed only if this rule is taken.
     *
     * @param name event name
     * @return this builder
     */
    public TransitionBuilder<C> on(String name) {
        return on(new Trigger(name));
    }

    /**
     * Requires a queued event identified by its name.
     *
     * @param event non-null trigger
     * @return this builder
     */
    public TransitionBuilder<C> on(Trigger event) {
        check();
        trigger = Objects.requireNonNull(event, "event");
        return this;
    }

    /**
     * Begins selection of a minimum state-time duration.
     *
     * @param amount nonnegative finite duration
     * @return single-use time-unit selector
     */
    public StateDuration<C> after(double amount) {
        check();
        StateMachine.validTime(amount, "amount");
        return new StateDuration<>(this, amount);
    }

    /**
     * Replaces the rule priority.
     *
     * @param value priority; larger values win
     * @return this builder
     */
    public TransitionBuilder<C> priority(int value) {
        check();
        priority = value;
        return this;
    }

    /**
     * Replaces the debug reason.
     *
     * @param value reason, or null for the default
     * @return this builder
     */
    public TransitionBuilder<C> reason(String value) {
        check();
        reason = value;
        return this;
    }

    /**
     * Replaces the action between exit and entry.
     *
     * @param value action, or null to remove it
     * @return this builder
     */
    public TransitionBuilder<C> action(TransitionAction<C> value) {
        check();
        action = value;
        return this;
    }

    /**
     * Sets a simpler one-argument transition action.
     *
     * @param value non-null context consumer
     * @return this builder
     */
    public TransitionBuilder<C> then(Consumer<StateContext<C>> value) {
        Objects.requireNonNull(value, "value");
        return action((ctx, transition) -> value.accept(ctx));
    }

    /**
     * Registers the rule to a previously defined name.
     *
     * @param name destination name
     * @return owning machine
     */
    public StateMachine<C> to(String name) {
        return finish(owner.slot(name));
    }

    /**
     * Registers the rule to a state identity, registering custom states if needed.
     *
     * @param state destination behavior
     * @return owning machine
     */
    public StateMachine<C> to(State<C> state) {
        check();
        return finish(owner.slot(state, true));
    }

    /**
     * Stores a converted duration.
     *
     * @param value minimum state seconds
     * @return this builder
     */
    TransitionBuilder<C> seconds(double value) {
        check();
        StateMachine.validTime(value, "seconds");
        seconds = value;
        return this;
    }

    /**
     * Commits one rule.
     *
     * @param target destination slot
     * @return owner
     */
    private StateMachine<C> finish(StateSlot<C> target) {
        check();
        owner.register(from, target, trigger, guard, seconds, priority, reason, action);
        used = true;
        return owner;
    }

    /**
     * Rejects reuse or editing an executing/frozen graph.
     */
    private void check() {
        if (used) throw new IllegalStateException("Transition builder has already been registered.");
        owner.editable();
    }
}
