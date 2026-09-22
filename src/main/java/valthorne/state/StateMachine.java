package valthorne.state;

import java.util.Arrays;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * A manually driven, single-threaded finite state machine with a fluent rule API.
 *
 * <pre>{@code
 * StateMachine<Player> machine = new StateMachine<>(player);
 * machine.state("Idle");
 * machine.state("Run").onUpdate((ctx, dt) -> ctx.data().move(dt));
 * machine.from("Idle").when(ctx -> ctx.data().moving).to("Run");
 * machine.from("Run").unless(ctx -> ctx.data().moving).to("Idle");
 * machine.start("Idle");
 * machine.update(delta);
 * }</pre>
 *
 * <h2>Update order</h2>
 * <p>Validate delta; evaluate end/pause/continuation/run conditions; advance scaled
 * state time; call the current state's update once; evaluate end/pause/run again;
 * choose a rule; exit, run its action, commit destination and zero state time,
 * then enter. By default at most one automatic transition is taken per update.
 * {@link #maxTransitionsPerUpdate(int)} explicitly enables bounded chaining.</p>
 *
 * <p>Rules compete by descending priority, then registration order, regardless of
 * whether they are local or global. Self-targeting rules are skipped. A time
 * threshold and required event are checked before invoking a guard. Guards must
 * be read-only and may not mutate this machine. Only the winning rule consumes
 * its event. Unconsumed events persist until taken or explicitly cleared.</p>
 *
 * <h2>Time and pausing</h2>
 * <p>All time comes from update calls; there are no threads or wall-clock reads.
 * A paused machine retains its state and timer but still checks end/pause/resume
 * conditions. A resumed update accepts that call's delta. A zero time scale
 * freezes state updates and automatic transitions, but not lifecycle conditions.
 * Timed rules test time in the current state; they are not tick-style schedules.
 * No frame splitting or leftover-time transfer occurs across state changes.</p>
 *
 * <h2>Mutation and exceptions</h2>
 * <p>State update callbacks may pause, stop, or force a state change; such operations
 * interrupt the remaining work of that update, even if immediately reversed.
 * Enter/exit/action and machine lifecycle listeners must not recursively change lifecycle;
 * registered events may be fired from them. Recursive update is rejected.
 * Configure graph structure and named callbacks outside callbacks and updates.</p>
 *
 * <p>Exceptions propagate. If exit/action throws, the source remains current and
 * any winning trigger is already consumed. If entry throws, the destination is
 * committed. Timers and user side effects are not rolled back. Guards/callbacks
 * should not throw routinely. Internal reentry flags are restored in finally blocks.</p>
 *
 * <h2>Storage</h2>
 * <p>Normal updates and registered transitions allocate no machine-owned objects.
 * Current adjacency is cached; local and global arrays are searched without scratch
 * collections. Optional control storage and trigger overflow bits are lazy.
 * Explicit start/change/stop can allocate immutable transition metadata. Builders,
 * registration, capacity growth, exceptions, and user code may also allocate.
 * Freeze and share the graph for many machines with identical behavior.</p>
 *
 * @param <C> user-data type
 * @author Albert Beaupre
 * @since February 12th, 2026
 */
public final class StateMachine<C> {
    /**
     * Flag indicating an update is in progress.
     */
    private static final int UPDATING = 1;
    /**
     * Flag indicating a lifecycle transition callback is in progress.
     */
    private static final int CHANGING = 2;
    /**
     * Flag indicating a read-only predicate is being evaluated.
     */
    private static final int EVALUATING = 4;
    private final StateGraph<C> graph; // Shared definitions, mutable until frozen.
    private final StateContext<C> ctx; // Live per-machine user context and timer.
    private StateSlot<C> current; // Current adjacency slot; null when stopped.
    private StateControl<C> control; // Optional conditions, scaling, and listeners.
    private long events; // First 64 pending event IDs without another allocation.
    private long[] extraEvents; // Event IDs beyond 63, allocated only when needed.
    private long revision; // Detects lifecycle or timing changes from callbacks.
    private int flags; // Update, transition, and predicate execution flags.
    private int transitionLimit = 8; // Maximum automatic transitions per update.
    private boolean multiple; // Whether multiple automatic transitions are enabled.
    private boolean paused; // Whether callback time is suspended in the current state.

    /**
     * Creates a stopped machine with no user data.
     */
    public StateMachine() {
        this(null);
    }

    /**
     * Creates a stopped machine with an empty editable graph.
     *
     * @param data user data, optionally null
     */
    public StateMachine(C data) {
        this(data, new StateGraph<>());
    }

    /**
     * Creates a machine and immediately enters a custom initial state.
     * Register rules before the first update, or outside later updates.
     *
     * @param data    user data
     * @param initial initial state, or null to remain stopped
     */
    public StateMachine(C data, State<C> initial) {
        this(data);
        setInitialState(initial, "initial");
    }

    /**
     * Creates independent runtime state using an existing graph.
     *
     * @param data  user data
     * @param graph definitions
     */
    StateMachine(C data, StateGraph<C> graph) {
        this.graph = graph;
        this.ctx = new StateContext<>(this, data);
    }

    /**
     * Creates or returns a named state. Names are case-sensitive.
     *
     * @param name nonblank name
     * @return fluent callback definition
     */
    public StateNode<C> state(String name) {
        return graph.define(name);
    }

    /**
     * Begins a rule from a previously defined state name.
     *
     * @param name source name
     * @return single-use transition builder
     */
    public TransitionBuilder<C> from(String name) {
        editable();
        return new TransitionBuilder<>(this, graph.named(name));
    }

    /**
     * Begins a rule from a custom state identity, registering it if needed.
     *
     * @param state non-null source
     * @return single-use transition builder
     */
    public TransitionBuilder<C> from(State<C> state) {
        editable();
        return new TransitionBuilder<>(this, graph.resolve(state, true));
    }

    /**
     * Begins a global rule competing with local rules by priority.
     *
     * @return single-use transition builder
     */
    public TransitionBuilder<C> any() {
        editable();
        return new TransitionBuilder<>(this, null);
    }

    /**
     * Seals configuration and returns its reusable, compacted graph.
     * Runtime state, lifecycle settings, pending events, and user data are not shared.
     *
     * @return frozen definitions for creating independent machines
     */
    public StateGraph<C> freeze() {
        if (flags != 0) throw new IllegalStateException("Freeze outside callbacks and updates.");
        return graph.freeze();
    }

    /**
     * Starts or moves to an existing named state. No-op if already running there.
     *
     * @param name destination state
     * @return this machine
     */
    public StateMachine<C> start(String name) {
        return startSlot(graph.named(name));
    }

    /**
     * Starts or moves to a state identity; custom states may be registered beforehand.
     *
     * @param state non-null destination
     * @return this machine
     */
    public StateMachine<C> start(State<C> state) {
        return startSlot(graph.resolve(state, true));
    }

    /**
     * Starts a resolved state through normal transition lifecycle.
     *
     * @param target destination
     * @return this machine
     */
    private StateMachine<C> startSlot(StateSlot<C> target) {
        mutableLifecycle();
        if (current == target) {
            resume();
            return this;
        }
        boolean wasStopped = current == null;
        take(new Transition<>(getCurrentState(), target, null, -1, null, 0, Integer.MAX_VALUE, -1, wasStopped ? "initial" : "started", null), wasStopped);
        return this;
    }

    /**
     * Forces a change to a named state without testing rules.
     *
     * @param name destination state
     * @return this machine
     */
    public StateMachine<C> goTo(String name) {
        changeSlot(graph.named(name), "forced");
        return this;
    }

    /**
     * Forces a change to custom state behavior without testing rules.
     *
     * @param state non-null destination
     * @return this machine
     */
    public StateMachine<C> goTo(State<C> state) {
        changeState(state, "forced");
        return this;
    }

    /**
     * Advances one machine on its owning thread.
     *
     * @param delta finite, nonnegative seconds
     * @throws IllegalArgumentException if time is invalid or scaling overflows float
     * @throws IllegalStateException    if recursively called
     */
    public void update(float delta) {
        validTime(delta, "delta");
        if (flags != 0) throw new IllegalStateException("StateMachine.update cannot be called recursively.");
        if (current == null) return;
        flags = UPDATING;
        boolean editableGraph = !graph.isFrozen();
        if (editableGraph) graph.activeCalls++;
        try {
            if (control != null && !allowsUpdate(true)) return;
            if (paused || current == null) return;
            float scaled = control == null ? delta : delta * control.scale;
            if (control != null && control.scale == 0f) return;
            if (!Float.isFinite(scaled)) throw new IllegalArgumentException("Scaled delta overflowed float.");
            ctx.time += scaled;
            ctx.delta = scaled;
            long expected = revision;
            current.state.onUpdate(ctx, scaled);
            if (revision != expected || current == null || paused) return;
            if (control != null && !allowsUpdate(false)) return;
            int limit = multiple ? transitionLimit : 1;
            for (int taken = 0; taken < limit; taken++) {
                Transition<C> next = choose();
                if (next == null) return;
                if (next.event >= 0) consume(next.event);
                take(next, false);
                if (current == null || paused) return;
                if (control != null && !allowsUpdate(false)) return;
            }
        } finally {
            flags = 0;
            if (editableGraph) graph.activeCalls--;
        }
    }

    /**
     * Finds the best valid rule without merging arrays or allocating scratch storage.
     * Lists are searched highest-head-priority first. Once a candidate is found,
     * the other list stops as soon as its remaining rules cannot outrank it.
     * Guards are read-only; their invocation order is not an API guarantee.
     *
     * @return winning definition, or null
     */
    private Transition<C> choose() {
        StateSlot<C> local = current;
        StateSlot<C> global = graph.global;
        if (global == null || global.count == 0) return chooseFrom(local, null);
        if (local.count == 0) return chooseFrom(global, null);
        if (outranks(global.rules[0], local.rules[0])) return chooseFrom(local, chooseFrom(global, null));
        return chooseFrom(global, chooseFrom(local, null));
    }

    /**
     * Searches one sorted adjacency array, stopping at its first valid improvement.
     *
     * @param slot adjacency list
     * @param best existing winner, or null
     * @return improved winner or the original best
     */
    private Transition<C> chooseFrom(StateSlot<C> slot, Transition<C> best) {
        for (int i = 0; i < slot.count; i++) {
            Transition<C> rule = slot.rules[i];
            if (best != null && !outranks(rule, best)) return best;
            if (rule.target == current || ctx.time < rule.seconds) continue;
            if (rule.event >= 0 && !pending(rule.event)) continue;
            if (rule.guard != null && !test(rule.guard)) continue;
            return rule;
        }
        return best;
    }

    /**
     * Compares stable rule ordering without subtraction overflow.
     *
     * @param first  possible higher-ranked rule
     * @param second other rule
     * @return true when first has higher priority or earlier tied registration
     */
    private static boolean outranks(Transition<?> first, Transition<?> second) {
        return first.priority() > second.priority() || (first.priority() == second.priority() && first.order() < second.order());
    }

    /**
     * Performs exit/action/commit/enter, preserving exception and reentry rules.
     *
     * @param transition already selected metadata
     * @param starting   whether to notify the machine's start listener
     */
    private void take(Transition<C> transition, boolean starting) {
        mutableLifecycle();
        if (transition.target == current) return;
        int previousFlags = flags;
        flags |= CHANGING;
        boolean editableGraph = !graph.isFrozen();
        if (editableGraph) graph.activeCalls++;
        try {
            if (current != null) current.state.onExit(ctx);
            if (transition.action != null) transition.action.run(ctx, transition);
            current = transition.target;
            ctx.time = 0;
            ctx.last = transition;
            paused = false;
            revision++;
            if (current != null) {
                current.state.onEnter(ctx);
                if (starting && control != null && control.start != null) control.start.accept(ctx);
            } else {
                ctx.delta = 0;
                if (control != null && control.endListener != null) control.endListener.accept(ctx);
            }
        } finally {
            flags = previousFlags;
            if (editableGraph) graph.activeCalls--;
        }
    }

    /**
     * Evaluates lifecycle conditions with end before pause before continuation.
     *
     * @param allowContinue whether automatic continuation is allowed now
     * @return true if state updates may proceed
     */
    private boolean allowsUpdate(boolean allowContinue) {
        StateControl<C> c = control;
        if (c == null) return current != null && !paused;
        if (c.end != null && test(c.end)) {
            stop();
            return false;
        }
        if (c.pause != null && test(c.pause)) {
            pause();
            return false;
        }
        if (paused) {
            if (!allowContinue || c.resume == null || !test(c.resume)) return false;
            long before = revision;
            resume();
            if (current == null || paused || revision != before + 1) return false;
            return allowsUpdate(false);
        }
        return current != null && (c.run == null || test(c.run));
    }

    /**
     * Runs one read-only guard while protecting machine mutation.
     *
     * @param guard condition to evaluate
     * @return predicate result
     */
    private boolean test(Guard<C> guard) {
        flags |= EVALUATING;
        try {
            return guard.allow(ctx);
        } finally {
            flags &= ~EVALUATING;
        }
    }

    /**
     * Pauses without leaving the current state or resetting time.
     *
     * @return this machine
     */
    public StateMachine<C> pause() {
        mutableLifecycle();
        if (current == null || paused) return this;
        paused = true;
        revision++;
        if (control != null && control.pauseListener != null) notifyListener(control.pauseListener);
        return this;
    }

    /**
     * Resumes a paused machine; never starts a stopped machine.
     *
     * @return this machine
     */
    public StateMachine<C> resume() {
        mutableLifecycle();
        if (current == null || !paused) return this;
        paused = false;
        revision++;
        if (control != null && control.resumeListener != null) notifyListener(control.resumeListener);
        return this;
    }

    /**
     * Runs a lifecycle notification with the same no-recursion contract as entry/exit.
     *
     * @param listener non-null lifecycle listener
     */
    private void notifyListener(Consumer<StateContext<C>> listener) {
        int previous = flags;
        flags |= CHANGING;
        boolean editableGraph = !graph.isFrozen();
        if (editableGraph) graph.activeCalls++;
        try {
            listener.accept(ctx);
        } finally {
            flags = previous;
            if (editableGraph) graph.activeCalls--;
        }
    }

    /**
     * Leaves the current state and stops updates; pending triggers are retained.
     * Explicit stop creates one immutable metadata object; repeated stop is a no-op.
     *
     * @return this machine
     */
    public StateMachine<C> stop() {
        changeSlot(null, "stopped");
        return this;
    }

    /**
     * Alias for {@link #stop()}.
     *
     * @return this machine
     */
    public StateMachine<C> end() {
        return stop();
    }

    /**
     * Silently clears runtime state and events, retaining definitions and controls.
     * Does not invoke exit or lifecycle listeners.
     *
     * @return this machine
     */
    public StateMachine<C> reset() {
        mutableLifecycle();
        current = null;
        paused = false;
        ctx.time = 0;
        ctx.delta = 0;
        ctx.last = null;
        revision++;
        clearTriggers();
        return this;
    }

    /**
     * Rewinds runtime state and enters a registered name, invoking its entry callback.
     * Pending triggers are cleared; previous exit is not invoked.
     *
     * @param name initial name
     * @return this machine
     */
    public StateMachine<C> restart(String name) {
        StateSlot<C> target = graph.named(name);
        reset();
        return startSlot(target);
    }

    /**
     * Returns whether a state is currently active and not paused.
     *
     * @return running lifecycle state; a run gate may still block execution
     */
    public boolean isRunning() {
        return current != null && !paused;
    }

    /**
     * Returns whether state/time are retained while updates are suspended.
     *
     * @return true while paused
     */
    public boolean isPaused() {
        return current != null && paused;
    }

    /**
     * Returns whether no state is active.
     *
     * @return true when stopped
     */
    public boolean isStopped() {
        return current == null;
    }

    /**
     * Returns whether lifecycle conditions are still observed.
     *
     * @return running or paused
     */
    public boolean isActive() {
        return current != null;
    }

    /**
     * Tests current state identity without a name lookup.
     *
     * @param state state identity
     * @return true when it is current; null tests stopped
     */
    public boolean isIn(State<C> state) {
        return getCurrentState() == state;
    }

    /**
     * Tests a previously defined state name; unknown names throw.
     *
     * @param name name to test
     * @return true when the resolved state is current
     */
    public boolean isIn(String name) {
        return current == graph.named(name);
    }

    /**
     * Returns the active state identity.
     *
     * @return state, or null when stopped
     */
    public State<C> getCurrentState() {
        return current == null ? null : current.state;
    }

    /**
     * Returns a named-state name or a custom state's simple class name.
     *
     * @return display name, or null when stopped
     */
    public String getCurrentName() {
        State<C> state = getCurrentState();
        return state == null ? null : state instanceof StateNode<?> node ? node.name() : state.getClass().getSimpleName();
    }

    /**
     * Returns the reused live context.
     *
     * @return context; not an immutable snapshot
     */
    public StateContext<C> getContext() {
        return ctx;
    }

    /**
     * Returns accumulated scaled state time.
     *
     * @return seconds, or zero when stopped
     */
    public double getTimeInState() {
        return ctx.time;
    }

    /**
     * Registers/resolves an event and returns a graph-local ID for fast firing.
     * Use this during setup; IDs must not be mixed between different graphs.
     *
     * @param name event name
     * @return nonnegative event ID
     */
    public int trigger(String name) {
        return graph.trigger(name, true);
    }

    /**
     * Fires a registered event by name, deduplicating repeated fires.
     *
     * @param name previously registered event
     * @return this machine
     */
    public StateMachine<C> fire(String name) {
        return fire(graph.trigger(name, false));
    }

    /**
     * Fires a registered event using its name.
     *
     * @param trigger event metadata
     * @return this machine
     */
    public StateMachine<C> fire(Trigger trigger) {
        return fire(Objects.requireNonNull(trigger, "trigger").name());
    }

    /**
     * Fires an event ID from this graph in constant time.
     * The first 64 IDs need no extra storage. Overflow storage is prepared by
     * {@link #prepare()} or allocated lazily when a higher event is first fired.
     *
     * @param id registered graph-local event ID
     * @return this machine
     */
    public StateMachine<C> fire(int id) {
        mutableEvents();
        checkEvent(id);
        if (id < 64) events |= 1L << id;
        else {
            int word = (id >>> 6) - 1;
            if (extraEvents == null || word >= extraEvents.length) prepare();
            extraEvents[word] |= 1L << id;
        }
        return this;
    }

    /**
     * Returns whether a registered event is waiting.
     *
     * @param name registered event name
     * @return pending status
     */
    public boolean hasTrigger(String name) {
        return pending(graph.trigger(name, false));
    }

    /**
     * Returns whether an event ID is waiting.
     *
     * @param id graph-local ID
     * @return pending status
     */
    public boolean hasTrigger(int id) {
        checkEvent(id);
        return pending(id);
    }

    /**
     * Clears all pending events without releasing reusable overflow storage.
     */
    public void clearTriggers() {
        mutableEvents();
        events = 0;
        if (extraEvents != null) Arrays.fill(extraEvents, 0);
    }

    /**
     * Preallocates overflow event words for all currently registered triggers.
     * Call after setup when even the first high-ID fire must not allocate.
     *
     * @return this machine
     */
    public StateMachine<C> prepare() {
        mutableEvents();
        int words = (int) (((long) graph.triggerCount + 63) >>> 6) - 1;
        if (words > 0 && (extraEvents == null || extraEvents.length < words))
            extraEvents = extraEvents == null ? new long[words] : Arrays.copyOf(extraEvents, words);
        return this;
    }

    /**
     * Legacy trigger API; null/blank is ignored, unknown names register only during setup.
     * Register all events before freezing or firing from callbacks.
     *
     * @param name event name
     */
    public void fireTrigger(String name) {
        if (name == null || name.isBlank()) return;
        fire(graph.trigger(name, true));
    }

    /**
     * Tests a validated event ID without allocating.
     *
     * @param id event ID
     * @return whether its bit is present
     */
    private boolean pending(int id) {
        if (id < 64) return (events & (1L << id)) != 0;
        int word = (id >>> 6) - 1;
        return extraEvents != null && word < extraEvents.length && (extraEvents[word] & (1L << id)) != 0;
    }

    /**
     * Consumes one known pending event.
     *
     * @param id event ID
     */
    private void consume(int id) {
        if (id < 64) events &= ~(1L << id);
        else extraEvents[(id >>> 6) - 1] &= ~(1L << id);
    }

    /**
     * Checks a graph-local event ID.
     *
     * @param id event ID
     */
    private void checkEvent(int id) {
        if (id < 0 || id >= graph.triggerCount) throw new IllegalArgumentException("Unknown trigger ID: " + id);
    }

    /**
     * Configures an OR-combined automatic stop condition, active while paused.
     *
     * @param condition read-only guard
     * @return this machine
     */
    public StateMachine<C> endIf(Guard<C> condition) {
        controls().end = or(controls().end, condition);
        return this;
    }

    /**
     * Configures an external automatic stop condition.
     *
     * @param condition read-only external condition
     * @return this machine
     */
    public StateMachine<C> endIf(Condition condition) {
        Objects.requireNonNull(condition);
        return endIf(c -> condition.test());
    }

    /**
     * Configures an OR-combined pause condition, which blocks continuation while true.
     *
     * @param condition read-only guard
     * @return this machine
     */
    public StateMachine<C> pauseIf(Guard<C> condition) {
        controls().pause = or(controls().pause, condition);
        return this;
    }

    /**
     * Configures an external pause condition.
     *
     * @param condition read-only external condition
     * @return this machine
     */
    public StateMachine<C> pauseIf(Condition condition) {
        Objects.requireNonNull(condition);
        return pauseIf(c -> condition.test());
    }

    /**
     * Configures OR-combined continuation of an already paused machine.
     *
     * @param condition read-only guard
     * @return this machine
     */
    public StateMachine<C> continueIf(Guard<C> condition) {
        controls().resume = or(controls().resume, condition);
        return this;
    }

    /**
     * Configures external continuation of an already paused machine.
     *
     * @param condition read-only external condition
     * @return this machine
     */
    public StateMachine<C> continueIf(Condition condition) {
        Objects.requireNonNull(condition);
        return continueIf(c -> condition.test());
    }

    /**
     * Adds an AND-combined time/update gate without changing lifecycle state.
     *
     * @param condition read-only guard
     * @return this machine
     */
    public StateMachine<C> runIf(Guard<C> condition) {
        StateControl<C> c = controls();
        Objects.requireNonNull(condition, "condition");
        Guard<C> old = c.run;
        c.run = old == null ? condition : ctx -> old.allow(ctx) && condition.allow(ctx);
        return this;
    }

    /**
     * Adds an external update gate.
     *
     * @param condition read-only external condition
     * @return this machine
     */
    public StateMachine<C> runIf(Condition condition) {
        Objects.requireNonNull(condition);
        return runIf(c -> condition.test());
    }

    /**
     * Removes lifecycle and run predicates, retaining speed and listeners.
     *
     * @return this machine
     */
    public StateMachine<C> clearConditions() {
        mutableLifecycle();
        if (control != null) control.end = control.pause = control.resume = control.run = null;
        releaseEmptyControl();
        revision++;
        return this;
    }

    /**
     * Sets state-time speed; zero freezes updates but still checks conditions.
     *
     * @param scale finite, nonnegative multiplier
     * @return this machine
     */
    public StateMachine<C> timeScale(float scale) {
        validTime(scale, "scale");
        mutableLifecycle();
        if (scale != getTimeScale()) {
            controls().scale = scale;
            revision++;
            releaseEmptyControl();
        }
        return this;
    }

    /**
     * Returns the state-time multiplier.
     *
     * @return configured speed
     */
    public float getTimeScale() {
        return control == null ? 1f : control.scale;
    }

    /**
     * Sets the maximum automatic changes per update; one disables chaining.
     *
     * @param maximum positive bound
     * @return this machine
     */
    public StateMachine<C> maxTransitionsPerUpdate(int maximum) {
        if (maximum < 1) throw new IllegalArgumentException("maximum must be at least one.");
        mutableLifecycle();
        transitionLimit = maximum;
        multiple = maximum > 1;
        revision++;
        return this;
    }

    /**
     * Legacy alias enabling/disabling chaining with the configured bound.
     *
     * @param allow true to enable bounded chaining
     * @return this machine
     */
    public StateMachine<C> setAllowMultipleTransitionsPerUpdate(boolean allow) {
        mutableLifecycle();
        multiple = allow;
        revision++;
        return this;
    }

    /**
     * Legacy alias; clamps to at least one and leaves chaining enablement unchanged.
     *
     * @param maximum desired bound
     * @return this machine
     */
    public StateMachine<C> setMaxTransitionsPerUpdate(int maximum) {
        mutableLifecycle();
        transitionLimit = Math.max(1, maximum);
        revision++;
        return this;
    }

    /**
     * Returns the effective current transition budget.
     *
     * @return one unless chaining is enabled
     */
    public int getMaxTransitionsPerUpdate() {
        return multiple ? transitionLimit : 1;
    }

    /**
     * Replaces the listener called after entering from stopped.
     *
     * @param listener action, or null
     * @return this machine
     */
    public StateMachine<C> onStart(Consumer<StateContext<C>> listener) {
        controls().start = listener;
        releaseEmptyControl();
        return this;
    }

    /**
     * Replaces the pause listener.
     *
     * @param listener action, or null
     * @return this machine
     */
    public StateMachine<C> onPause(Consumer<StateContext<C>> listener) {
        controls().pauseListener = listener;
        releaseEmptyControl();
        return this;
    }

    /**
     * Replaces the continuation listener.
     *
     * @param listener action, or null
     * @return this machine
     */
    public StateMachine<C> onContinue(Consumer<StateContext<C>> listener) {
        controls().resumeListener = listener;
        releaseEmptyControl();
        return this;
    }

    /**
     * Replaces the listener called after the current state has exited on stop.
     *
     * @param listener action, or null
     * @return this machine
     */
    public StateMachine<C> onEnd(Consumer<StateContext<C>> listener) {
        controls().endListener = listener;
        releaseEmptyControl();
        return this;
    }

    /**
     * Allocates optional runtime settings on first use.
     *
     * @return settings
     */
    private StateControl<C> controls() {
        mutableLifecycle();
        if (control == null) control = new StateControl<>();
        return control;
    }

    /**
     * Releases an optional settings object when all features are disabled.
     */
    private void releaseEmptyControl() {
        StateControl<C> c = control;
        if (c != null && c.scale == 1f && c.end == null && c.pause == null && c.resume == null && c.run == null && c.start == null && c.pauseListener == null && c.resumeListener == null && c.endListener == null)
            control = null;
    }

    /**
     * Combines guards at configuration time with short-circuit OR.
     *
     * @param first previous guard
     * @param next  new guard
     * @return combined guard
     */
    private static <C> Guard<C> or(Guard<C> first, Guard<C> next) {
        Objects.requireNonNull(next, "condition");
        return first == null ? next : ctx -> first.allow(ctx) || next.allow(ctx);
    }

    /**
     * Original positional registration API, preserved for migration.
     *
     * @param from     source state
     * @param to       target state
     * @param priority higher values win
     * @param trigger  optional trigger
     * @param guard    optional guard
     * @param minTime  minimum state seconds; finite negatives clamp to zero
     * @param reason   optional reason
     * @param action   optional transition action
     * @return this machine
     */
    public StateMachine<C> addTransition(State<C> from, State<C> to, int priority, Trigger trigger, Guard<C> guard, float minTime, String reason, TransitionAction<C> action) {
        editable();
        finiteLegacy(minTime);
        register(graph.resolve(from, true), graph.resolve(to, true), trigger, guard, Math.max(0f, minTime), priority, reason, action);
        return this;
    }

    /**
     * Original positional global-rule registration API.
     *
     * @param to       target state
     * @param priority higher values win
     * @param trigger  optional trigger
     * @param guard    optional guard
     * @param minTime  minimum state seconds; finite negatives clamp to zero
     * @param reason   optional reason
     * @param action   optional action
     * @return this machine
     */
    public StateMachine<C> addGlobalTransition(State<C> to, int priority, Trigger trigger, Guard<C> guard, float minTime, String reason, TransitionAction<C> action) {
        editable();
        finiteLegacy(minTime);
        register(null, graph.resolve(to, true), trigger, guard, Math.max(0f, minTime), priority, reason, action);
        return this;
    }

    /**
     * Forces a normal lifecycle change. A null destination now correctly stops.
     *
     * @param next   target, or null to stop
     * @param reason reason, or null for "forced"
     */
    public void changeState(State<C> next, String reason) {
        mutableLifecycle();
        changeSlot(next == null ? null : graph.resolve(next, true), reason == null ? "forced" : reason);
    }

    /**
     * Changes to a resolved destination.
     *
     * @param target destination, or null
     * @param reason metadata reason
     */
    private void changeSlot(StateSlot<C> target, String reason) {
        mutableLifecycle();
        if (target == current) return;
        boolean starting = current == null;
        take(new Transition<>(getCurrentState(), target, null, -1, null, 0, Integer.MAX_VALUE, -1, reason, null), starting);
    }

    /**
     * Original silent-initialization API: no exit of a previous state.
     * Pending triggers are retained; a null initial state clears current metadata.
     *
     * @param initial new initial state, or null
     * @param reason  initial metadata reason
     */
    public void setInitialState(State<C> initial, String reason) {
        mutableLifecycle();
        StateSlot<C> target = initial == null ? null : graph.resolve(initial, true);
        current = null;
        paused = false;
        ctx.time = 0;
        ctx.delta = 0;
        ctx.last = null;
        revision++;
        if (target != null)
            take(new Transition<>(null, target, null, -1, null, 0, Integer.MAX_VALUE, -1, reason == null ? "initial" : reason, null), true);
    }

    /**
     * Resolves a name for a builder.
     *
     * @param name state name
     * @return state slot
     */
    StateSlot<C> slot(String name) {
        return graph.named(name);
    }

    /**
     * Resolves a state for a builder.
     *
     * @param state  identity
     * @param create whether registration is allowed
     * @return state slot
     */
    StateSlot<C> slot(State<C> state, boolean create) {
        return graph.resolve(state, create);
    }

    /**
     * Checks graph editability for builders.
     */
    void editable() {
        graph.checkEditable();
    }

    /**
     * Registers one completed builder definition.
     *
     * @param from     source or null
     * @param to       destination
     * @param trigger  optional trigger
     * @param guard    optional guard
     * @param seconds  minimum time
     * @param priority priority
     * @param reason   reason
     * @param action   optional action
     */
    void register(StateSlot<C> from, StateSlot<C> to, Trigger trigger, Guard<C> guard, double seconds, int priority, String reason, TransitionAction<C> action) {
        graph.add(from, to, trigger, guard, seconds, priority, reason, action);
    }

    /**
     * Rejects lifecycle/timing mutation in predicates or transition callbacks.
     */
    private void mutableLifecycle() {
        if ((flags & (CHANGING | EVALUATING)) != 0)
            throw new IllegalStateException("Do not change lifecycle inside guards, enter/exit/actions, or lifecycle listeners.");
    }

    /**
     * Allows event firing in callbacks, but not in guards.
     */
    private void mutableEvents() {
        if ((flags & EVALUATING) != 0) throw new IllegalStateException("Guards must not mutate pending events.");
    }

    /**
     * Validates time and unit conversions.
     *
     * @param value time value
     * @param name  parameter name
     */
    static void validTime(double value, String name) {
        if (!Double.isFinite(value) || value < 0)
            throw new IllegalArgumentException(name + " must be finite and nonnegative.");
    }

    /**
     * Validates legacy durations while allowing their original negative clamp.
     *
     * @param value legacy time value
     */
    private static void finiteLegacy(float value) {
        if (!Float.isFinite(value)) throw new IllegalArgumentException("Time must be finite.");
    }
}
