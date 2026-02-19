package valthorne.state;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A condition-driven finite state machine (FSM).
 *
 * <h2>Example</h2>
 * <pre>{@code
 * // Your shared data for states/guards/actions.
 * public record PlayerCtx(boolean grounded, float vx, boolean dead) {}
 *
 * // Example states.
 * State<PlayerCtx> idle = new State<>() {
 *     @Override public void onEnter(StateContext<PlayerCtx> ctx) {}
 *     @Override public void onUpdate(StateContext<PlayerCtx> ctx, float dt) {}
 *     @Override public void onExit(StateContext<PlayerCtx> ctx) {}
 * };
 *
 * State<PlayerCtx> run = new State<>() {
 *     @Override public void onEnter(StateContext<PlayerCtx> ctx) {}
 *     @Override public void onUpdate(StateContext<PlayerCtx> ctx, float dt) {}
 *     @Override public void onExit(StateContext<PlayerCtx> ctx) {}
 * };
 *
 * State<PlayerCtx> dead = new State<>() {
 *     @Override public void onEnter(StateContext<PlayerCtx> ctx) {}
 *     @Override public void onUpdate(StateContext<PlayerCtx> ctx, float dt) {}
 *     @Override public void onExit(StateContext<PlayerCtx> ctx) {}
 * };
 *
 * // Create the FSM with initial state.
 * StateMachine<PlayerCtx> fsm = new StateMachine<>(new PlayerCtx(true, 0f, false), idle);
 *
 * // Global "any state -> dead" transition.
 * fsm.addGlobalTransition(
 *     dead,
 *     1000, // priority
 *     null, // trigger
 *     ctx -> ctx.data().dead(), // guard
 *     0f,   // min time in state
 *     "player died",
 *     (ctx, tr) -> {
 *         // Transition action: reset something, play sound, fire event, etc.
 *     }
 * );
 *
 * // State-specific transitions.
 * fsm.addTransition(
 *     idle, run,
 *     10,
 *     null,
 *     ctx -> Math.abs(ctx.data().vx()) > 0.1f,
 *     0.05f, // debounce: must be in IDLE for 50ms before leaving
 *     "start moving",
 *     null
 * );
 *
 * fsm.addTransition(
 *     run, idle,
 *     10,
 *     null,
 *     ctx -> Math.abs(ctx.data().vx()) <= 0.1f,
 *     0.05f,
 *     "stop moving",
 *     null
 * );
 *
 * // Trigger-based transition (example: jump).
 * Trigger jump = new Trigger("jump");
 * State<PlayerCtx> jumpState = ...;
 *
 * fsm.addTransition(
 *     idle, jumpState,
 *     50,
 *     jump,
 *     ctx -> ctx.data().grounded(),
 *     0f,
 *     "jump pressed",
 *     (ctx, tr) -> {
 *         // e.g. set vertical velocity
 *     }
 * );
 *
 * // In your input handling:
 * // if (jumpPressed) fsm.fireTrigger("jump");
 *
 * // In your game loop:
 * fsm.update(deltaSeconds);
 * }</pre>
 *
 * <h2>How transition selection works</h2>
 * <ul>
 *     <li>Global transitions are evaluated first and compete with state-specific transitions by priority.</li>
 *     <li>Lists are pre-sorted by priority (descending), then insertion order (ascending).</li>
 *     <li>A transition is valid only if:
 *         <ul>
 *             <li>{@code timeInStateSec >= minTimeInStateSec}</li>
 *             <li>guard is null or {@code guard.allow(ctx)} returns true</li>
 *             <li>requiredTrigger is null or its name exists in the trigger queue</li>
 *         </ul>
 *     </li>
 *     <li>If a transition requires a trigger, the trigger is consumed when the transition is taken.</li>
 *     <li>By default, multiple transitions may occur per {@link #update(float)} call (capped by {@link #maxTransitionsPerUpdate}).</li>
 * </ul>
 *
 * <h2>Transition lifecycle</h2>
 * <ol>
 *     <li>oldState.onExit(ctx)</li>
 *     <li>transition.action.run(ctx, transition) (if present)</li>
 *     <li>current is switched, {@code timeInStateSec} resets to 0</li>
 *     <li>newState.onEnter(ctx)</li>
 * </ol>
 *
 * @param <C> user-defined context type
 * @author Albert Beaupre
 * @since February 12th, 2026
 */
public class StateMachine<C> {

    private final Map<State<C>, List<Transition<C>>> perState = new IdentityHashMap<>(); // State -> sorted transition list.
    private final Set<Transition<C>> temporarilyUnavailable = new HashSet<>();           // Transitions skipped this update due to trigger-consume conflicts.
    private final List<Transition<C>> global = new ArrayList<>();                        // Sorted global transitions (any-state rules).
    private final AtomicLong orderCounter = new AtomicLong(0);                           // Monotonic insertion order for tie-breaking.
    private final Set<String> triggerQueue = new LinkedHashSet<>();                      // Unique queued trigger names, preserves insertion order.
    private final StateContext<C> ctx;                                                   // Shared context passed to states/guards/actions.
    private State<C> current;                                                            // Currently active state (may be null).
    private boolean allowMultipleTransitionsPerUpdate = true;                            // If true, may chain transitions in one update.
    private int maxTransitionsPerUpdate = 8;                                             // Safety cap to prevent infinite loops per update call.

    /**
     * Creates a new FSM and immediately enters the initial state (if non-null).
     *
     * <p>The provided {@code userContext} becomes available through {@link StateContext#data()}.</p>
     * <p>The initial enter reason is stored on {@link StateContext#lastTransition()} as {@code "initial"} (or your override).</p>
     *
     * @param userContext your context data object (may be null, depending on your design)
     * @param initial     initial state (may be null)
     */
    public StateMachine(C userContext, State<C> initial) {
        this.ctx = new StateContext<>(this, userContext);
        setInitialState(initial, "initial");
    }

    /**
     * Enables or disables taking multiple transitions in a single {@link #update(float)} call.
     *
     * <p>If enabled, the FSM will keep evaluating transitions after each state change until:
     * it finds no valid transition, reaches {@link #maxTransitionsPerUpdate}, or this option is disabled.</p>
     *
     * @param allow true to allow multiple transitions per update
     * @return this for chaining
     */
    public StateMachine<C> setAllowMultipleTransitionsPerUpdate(boolean allow) {
        this.allowMultipleTransitionsPerUpdate = allow;
        return this;
    }

    /**
     * Sets the maximum number of transitions allowed in one {@link #update(float)}.
     *
     * <p>This is a safety valve against conditions that form a cycle, e.g. A->B and B->A both valid.</p>
     *
     * @param max maximum transitions per update (minimum 1)
     * @return this for chaining
     */
    public StateMachine<C> setMaxTransitionsPerUpdate(int max) {
        this.maxTransitionsPerUpdate = Math.max(1, max);
        return this;
    }

    /**
     * Fires a trigger (queued event) by name.
     *
     * <p>Triggers are stored as unique names. Firing the same trigger multiple times before it is consumed
     * will still result in only one queued instance.</p>
     *
     * <p>A transition that requires this trigger will consume it when taken.</p>
     *
     * @param triggerName trigger name (ignored if null/blank)
     */
    public void fireTrigger(String triggerName) {
        if (triggerName == null || triggerName.isBlank()) {
            return;
        }
        triggerQueue.add(triggerName);
    }

    /**
     * Clears all queued triggers.
     *
     * <p>Use this if you want to guarantee that triggers do not carry across frames.</p>
     */
    public void clearTriggers() {
        triggerQueue.clear();
    }

    /**
     * Adds a transition that is only considered when {@code from} is the current state.
     *
     * <p>Transition selection is priority-based:
     * higher priority wins; ties are resolved by insertion order.</p>
     *
     * <p>Use {@code requiredTrigger} to require a queued event (consumed when taken).</p>
     * <p>Use {@code guard} to implement boolean logic checks against {@link StateContext}.</p>
     * <p>Use {@code minTimeInStateSec} as a debounce/cooldown (must remain in state for this many seconds).</p>
     * <p>Use {@code reason} to store a human-readable reason on {@link Transition#reason()}.</p>
     * <p>Use {@code action} to run custom logic during the transition.</p>
     *
     * @param from              source state (must be non-null)
     * @param to                target state (must be non-null)
     * @param priority          priority (higher wins)
     * @param requiredTrigger   required trigger (nullable)
     * @param guard             guard predicate (nullable)
     * @param minTimeInStateSec debounce/cooldown in seconds (clamped to >= 0)
     * @param reason            human-readable reason (nullable/blank becomes default)
     * @param action            transition action (nullable)
     * @return this for chaining
     */
    public StateMachine<C> addTransition(State<C> from, State<C> to, int priority, Trigger requiredTrigger, Guard<C> guard, float minTimeInStateSec, String reason, TransitionAction<C> action) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");

        Transition<C> t = new Transition<>(from, to, requiredTrigger, guard, minTimeInStateSec, priority, orderCounter.getAndIncrement(), reason, action);

        perState.computeIfAbsent(from, k -> new ArrayList<>()).add(t);
        sortTransitions(perState.get(from));
        return this;
    }

    /**
     * Adds a global transition (any-state rule).
     *
     * <p>Global transitions are evaluated alongside the current state's transitions and compete by priority.</p>
     * <p>A global transition uses {@code from=null} inside {@link Transition#from()}.</p>
     *
     * @param to                target state (must be non-null)
     * @param priority          priority (higher wins)
     * @param requiredTrigger   required trigger (nullable)
     * @param guard             guard predicate (nullable)
     * @param minTimeInStateSec debounce/cooldown in seconds (clamped to >= 0)
     * @param reason            human-readable reason (nullable/blank becomes default)
     * @param action            transition action (nullable)
     * @return this for chaining
     */
    public StateMachine<C> addGlobalTransition(State<C> to, int priority, Trigger requiredTrigger, Guard<C> guard, float minTimeInStateSec, String reason, TransitionAction<C> action) {
        Objects.requireNonNull(to, "to");

        Transition<C> t = new Transition<>(null, to, requiredTrigger, guard, minTimeInStateSec, priority, orderCounter.getAndIncrement(), reason, action);

        global.add(t);
        sortTransitions(global);
        return this;
    }

    /**
     * Updates the FSM by:
     * <ol>
     *     <li>Accumulating time in the current state</li>
     *     <li>Calling {@code current.onUpdate(ctx, dt)}</li>
     *     <li>Evaluating transitions and taking the best valid one</li>
     *     <li>Optionally repeating transition evaluation (multi-transition mode)</li>
     * </ol>
     *
     * <p>Trigger handling:</p>
     * <ul>
     *     <li>If a transition requires a trigger, it must exist in the trigger queue.</li>
     *     <li>On take, that trigger name is removed from the queue.</li>
     *     <li>If the best transition requires a trigger but consumption fails, that transition is skipped
     *         for this update and evaluation continues.</li>
     * </ul>
     *
     * <p>Safety:</p>
     * <ul>
     *     <li>{@code dtSec} is clamped to {@code >= 0}.</li>
     *     <li>Transition chaining is capped by {@link #maxTransitionsPerUpdate}.</li>
     * </ul>
     *
     * @param dtSec delta time in seconds
     */
    public void update(float dtSec) {
        if (current == null) {
            return;
        }

        float safeDt = Math.max(0f, dtSec);

        ctx.timeInStateSec += safeDt;

        current.onUpdate(ctx, safeDt);

        int taken = 0;

        while (true) {
            Transition<C> next = findBestValidTransition();
            if (next == null) {
                break;
            }

            boolean consumed = consumeTriggerIfNeeded(next);
            if (!consumed) {
                temporarilyUnavailable.add(next);
                continue;
            }

            takeTransition(next);
            taken++;

            if (!allowMultipleTransitionsPerUpdate) {
                break;
            }

            if (taken >= maxTransitionsPerUpdate) {
                break;
            }
        }

        temporarilyUnavailable.clear();
    }

    /**
     * Returns the current active state.
     *
     * @return current state (may be null)
     */
    public State<C> getCurrentState() {
        return current;
    }

    /**
     * Returns the live {@link StateContext} used for updates and transitions.
     *
     * <p>This context object is reused; do not store it as if it were immutable snapshot state.</p>
     *
     * @return the state context instance
     */
    public StateContext<C> getContext() {
        return ctx;
    }

    /**
     * Forces an immediate state change (bypasses guards, triggers, and cooldown checks).
     *
     * <p>This still performs the normal transition lifecycle:
     * oldState.onExit, optional action, switch current, reset timeInState, newState.onEnter.</p>
     *
     * <p>The forced transition uses a very high priority and sets {@link Transition#reason()} to your provided reason
     * (or {@code "forced"} if null).</p>
     *
     * @param next   next state (may be null)
     * @param reason reason string (nullable)
     */
    public void changeState(State<C> next, String reason) {
        if (next == current) {
            return;
        }

        Transition<C> forced = new Transition<>(current, next, null, null, 0f, Integer.MAX_VALUE, orderCounter.getAndIncrement(), (reason == null ? "forced" : reason), null);

        takeTransition(forced);
    }

    /**
     * Sets the initial state and calls {@link State#onEnter(StateContext)} immediately.
     *
     * <p>This clears:</p>
     * <ul>
     *     <li>current state</li>
     *     <li>time in state</li>
     *     <li>last transition</li>
     * </ul>
     *
     * <p>If {@code initial} is null, the machine becomes idle (no current state).</p>
     *
     * @param initial initial state (nullable)
     * @param reason  reason string used on the synthetic "initial" transition (nullable)
     */
    public void setInitialState(State<C> initial, String reason) {
        current = null;
        ctx.current = null;
        ctx.timeInStateSec = 0f;
        ctx.lastTransition = null;

        if (initial != null) {
            Transition<C> init = new Transition<>(null, initial, null, null, 0f, Integer.MAX_VALUE, orderCounter.getAndIncrement(), (reason == null ? "initial" : reason), null);

            current = initial;
            ctx.current = initial;
            ctx.lastTransition = init;
            ctx.timeInStateSec = 0f;

            initial.onEnter(ctx);
        }
    }

    /**
     * Chooses the best valid transition for the current state.
     *
     * <p>This evaluates global transitions and state-specific transitions and then compares the best candidate
     * from each by priority and insertion order.</p>
     *
     * @return the best transition to take, or null if none valid
     */
    private Transition<C> findBestValidTransition() {
        if (current == null) {
            return null;
        }

        List<Transition<C>> stateList = perState.get(current);

        Transition<C> best = null;

        best = chooseBest(best, findBestValidFromList(global));
        if (stateList != null && !stateList.isEmpty()) {
            best = chooseBest(best, findBestValidFromList(stateList));
        }

        return best;
    }

    /**
     * Finds the first valid transition from a list that is already sorted by:
     * priority descending, then insertion order ascending.
     *
     * <p>This method applies:
     * cooldown (min time in state),
     * guard,
     * trigger presence,
     * and the "temporarily unavailable" set used during one update pass.</p>
     *
     * @param list sorted transition list
     * @return first valid transition, or null
     */
    private Transition<C> findBestValidFromList(List<Transition<C>> list) {
        for (Transition<C> t : list) {
            if (temporarilyUnavailable.contains(t)) {
                continue;
            }
            if (ctx.timeInStateSec < t.minTimeInStateSec()) {
                continue;
            }
            if (t.guard() != null && !t.guard().allow(ctx)) {
                continue;
            }
            if (t.requiredTrigger() != null && !triggerQueue.contains(t.requiredTrigger().name())) {
                continue;
            }
            return t;
        }
        return null;
    }

    /**
     * Compares two transitions and returns the better one.
     *
     * <p>Comparison rules:</p>
     * <ul>
     *     <li>Higher priority wins.</li>
     *     <li>If priority ties, lower insertion order wins (earlier added).</li>
     * </ul>
     *
     * @param a candidate A (nullable)
     * @param b candidate B (nullable)
     * @return the best candidate, or null if both are null
     */
    private Transition<C> chooseBest(Transition<C> a, Transition<C> b) {
        if (a == null) return b;
        if (b == null) return a;

        int p = Integer.compare(b.priority(), a.priority());
        if (p != 0) {
            return (p > 0) ? b : a;
        }

        return (b.order() < a.order()) ? b : a;
    }

    /**
     * Consumes the required trigger for a transition (if any).
     *
     * <p>If the transition does not require a trigger, this returns true.</p>
     * <p>If it requires one, this removes it from the trigger queue and returns whether removal succeeded.</p>
     *
     * @param t transition
     * @return true if trigger is not needed or was successfully consumed
     */
    private boolean consumeTriggerIfNeeded(Transition<C> t) {
        if (t.requiredTrigger() == null) {
            return true;
        }
        return triggerQueue.remove(t.requiredTrigger().name());
    }

    /**
     * Sorts transitions in-place by priority descending then insertion order ascending.
     *
     * <p>This ensures that "first valid wins" also respects priority.</p>
     *
     * @param list transition list
     */
    private void sortTransitions(List<Transition<C>> list) {
        list.sort((a, b) -> {
            int p = Integer.compare(b.priority(), a.priority());
            if (p != 0) return p;
            return Long.compare(a.order(), b.order());
        });
    }

    /**
     * Performs the actual transition lifecycle:
     * <ol>
     *     <li>call from.onExit(ctx) if from != null</li>
     *     <li>run transition action if present</li>
     *     <li>switch current state, update context, reset timeInState</li>
     *     <li>call to.onEnter(ctx) if to != null</li>
     * </ol>
     *
     * <p>If {@code to == from}, this method does nothing.</p>
     *
     * @param t transition to take
     */
    private void takeTransition(Transition<C> t) {
        if (t == null) {
            return;
        }

        State<C> from = current;
        State<C> to = t.to();

        if (to == from) {
            return;
        }

        if (from != null) {
            from.onExit(ctx);
        }

        if (t.action() != null) {
            t.action().run(ctx, t);
        }

        current = to;
        ctx.current = to;
        ctx.lastTransition = t;
        ctx.timeInStateSec = 0f;

        if (to != null) {
            to.onEnter(ctx);
        }
    }
}