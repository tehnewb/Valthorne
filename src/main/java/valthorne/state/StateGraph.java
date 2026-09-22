package valthorne.state;

import java.util.Arrays;
import java.util.Objects;

/**
 * Shared state definitions and transitions, separate from each machine's runtime.
 *
 * <p>Build through a {@link StateMachine}, then call {@link StateMachine#freeze()}.
 * A frozen graph can create many machines without copying callbacks, transitions,
 * trigger names, or adjacency arrays. Each machine gets independent user data,
 * current state, timer, and pending trigger bits. New machines are stopped.</p>
 *
 * <p>Freezing protects graph structure and named-state callback assignments, not
 * mutable data captured by callbacks or fields in custom {@link State} objects.
 * Such shared behavior must be stateless or otherwise safe for its intended use.</p>
 *
 * <p>Configure and freeze one machine, then create independent runtimes from its
 * graph:</p>
 * <pre>{@code
 * StateMachine<Player> template = new StateMachine<>();
 * template.state("Idle");
 * template.state("Run");
 * template.from("Idle").when(ctx -> ctx.data().moving()).to("Run");
 * StateGraph<Player> graph = template.freeze();
 * StateMachine<Player> playerOne = graph.create(firstPlayer);
 * StateMachine<Player> playerTwo = graph.create(secondPlayer);
 * }</pre>
 *
 * @param <C> user-data type
 * @author Albert Beaupre
 * @since September 22nd, 2026
 */
public final class StateGraph<C> {
    StateSlot<C>[] states; // Registered state slots, lazily allocated.
    int stateCount; // Occupied state slots.
    StateSlot<C> global; // Global adjacency list, allocated only when needed.
    String[] triggers; // Registered event names, lazily allocated.
    int triggerCount; // Occupied event-name slots.
    long order; // Next stable rule insertion order.
    private boolean frozen; // Whether definitions have been sealed for sharing.
    int activeCalls; // Callbacks or updates currently using a mutable graph.

    /**
     * Creates an initially empty graph for one configuring machine.
     */
    StateGraph() {
    }

    /**
     * Creates a stopped runtime using this frozen graph.
     *
     * @param data independent user data for the new machine
     * @return new runtime sharing only definitions
     * @throws IllegalStateException if the graph has not been frozen
     */
    public StateMachine<C> create(C data) {
        if (!frozen) throw new IllegalStateException("Freeze the graph before sharing it.");
        return new StateMachine<>(data, this);
    }

    /**
     * Returns whether configuration is sealed.
     *
     * @return true after freeze
     */
    public boolean isFrozen() {
        return frozen;
    }

    /**
     * Returns registered state count.
     *
     * @return number of state definitions
     */
    public int stateCount() {
        return stateCount;
    }

    /**
     * Returns registered trigger count.
     *
     * @return number of event IDs
     */
    public int triggerCount() {
        return triggerCount;
    }

    /**
     * Counts local and global rules.
     *
     * @return number of registered transition definitions
     */
    public int transitionCount() {
        int n = global == null ? 0 : global.count;
        for (int i = 0; i < stateCount; i++) n += states[i].count;
        return n;
    }

    /**
     * Rejects changes to sealed or currently executing definitions.
     */
    void checkEditable() {
        if (frozen) throw new IllegalStateException("This state graph is frozen.");
        if (activeCalls != 0)
            throw new IllegalStateException("Configure states and rules outside callbacks and updates.");
    }

    /**
     * Seals definitions and trims unused reference-array capacity.
     *
     * @return this graph
     */
    StateGraph<C> freeze() {
        if (frozen) return this;
        checkEditable();
        if (states != null) {
            states = Arrays.copyOf(states, stateCount);
            for (int i = 0; i < stateCount; i++) states[i].trim();
        }
        if (global != null) global.trim();
        if (triggers != null) triggers = Arrays.copyOf(triggers, triggerCount);
        frozen = true;
        return this;
    }

    /**
     * Locates a state name without creating an implicit misspelled state.
     *
     * @param name nonblank case-sensitive name
     * @return matching slot
     * @throws IllegalArgumentException if absent
     */
    StateSlot<C> named(String name) {
        validName(name);
        for (int i = 0; i < stateCount; i++) {
            StateSlot<C> slot = states[i];
            if (slot.state instanceof StateNode<?> node && node.name().equals(name)) return slot;
        }
        throw new IllegalArgumentException("Unknown state: " + name);
    }

    /**
     * Looks up state identity, optionally registering new custom behavior.
     *
     * @param state  behavior to resolve
     * @param create whether to register missing behavior
     * @return matching slot
     */
    StateSlot<C> resolve(State<C> state, boolean create) {
        Objects.requireNonNull(state, "state");
        for (int i = 0; i < stateCount; i++) if (states[i].state == state) return states[i];
        if (!create) throw new IllegalArgumentException("State is not registered in this graph.");
        checkEditable();
        if (state instanceof StateNode<?> node && !node.belongsTo(this))
            throw new IllegalArgumentException("Named state belongs to another graph; share its frozen graph instead.");
        return append(state);
    }

    /**
     * Creates or returns the definition for a name.
     *
     * @param name case-sensitive name
     * @return named definition
     */
    @SuppressWarnings("unchecked")
    StateNode<C> define(String name) {
        validName(name);
        for (int i = 0; i < stateCount; i++) {
            if (states[i].state instanceof StateNode<?> node && node.name().equals(name)) return (StateNode<C>) node;
        }
        checkEditable();
        StateNode<C> node = new StateNode<>(this, name);
        append(node);
        return node;
    }

    /**
     * Appends a state slot during configuration.
     *
     * @param state state behavior
     * @return newly added slot
     */
    @SuppressWarnings("unchecked")
    private StateSlot<C> append(State<C> state) {
        if (states == null) states = (StateSlot<C>[]) new StateSlot<?>[4];
        else if (stateCount == states.length) states = Arrays.copyOf(states, stateCount + (stateCount >> 1) + 1);
        StateSlot<C> slot = new StateSlot<>(state);
        states[stateCount++] = slot;
        return slot;
    }

    /**
     * Resolves an event name and optionally registers it.
     *
     * @param name   event name
     * @param create whether unknown events may be registered
     * @return graph-local bit index
     */
    int trigger(String name, boolean create) {
        validName(name);
        for (int i = 0; i < triggerCount; i++) if (triggers[i].equals(name)) return i;
        if (!create) throw new IllegalArgumentException("Unknown trigger: " + name + "; register it during setup.");
        checkEditable();
        if (triggers == null) triggers = new String[4];
        else if (triggerCount == triggers.length)
            triggers = Arrays.copyOf(triggers, triggerCount + (triggerCount >> 1) + 1);
        triggers[triggerCount] = name;
        return triggerCount++;
    }

    /**
     * Registers a validated rule with stable priority ordering.
     *
     * @param from     local source, or null for global
     * @param to       non-null target
     * @param trigger  optional event
     * @param guard    optional read-only condition
     * @param seconds  required state time
     * @param priority higher priorities win
     * @param reason   debug reason
     * @param action   optional transition action
     */
    void add(StateSlot<C> from, StateSlot<C> to, Trigger trigger, Guard<C> guard,
             double seconds, int priority, String reason, TransitionAction<C> action) {
        checkEditable();
        if (order == Long.MAX_VALUE) throw new IllegalStateException("Transition insertion order exhausted.");
        int event = trigger == null ? -1 : trigger(trigger.name(), true);
        Transition<C> rule = new Transition<>(from == null ? null : from.state, to, trigger, event,
                guard, seconds, priority, order++, reason, action);
        if (from != null) from.add(rule);
        else {
            if (global == null) global = new StateSlot<>(null);
            global.add(rule);
        }
    }

    /**
     * Validates state and event names.
     *
     * @param name name to validate
     */
    static void validName(String name) {
        Objects.requireNonNull(name, "name");
        if (name.isBlank()) throw new IllegalArgumentException("Name must not be blank.");
    }
}
