package valthorne.state;

import java.util.function.Consumer;

/**
 * A named state configured with optional enter, update, and exit callbacks.
 *
 * <pre>{@code
 * machine.state("Idle").onEnter(ctx -> System.out.println("Idle"));
 * machine.state("Run").onUpdate((ctx, delta) -> ctx.data().move(delta));
 * }</pre>
 *
 * <p>Configure callbacks outside updates. Once the owning graph is frozen, these
 * definitions cannot change. Sharing this state shares its callback objects;
 * callbacks must access per-entity data through their context, not captured entities.</p>
 *
 * @param <C> user-data type
 * @author Albert Beaupre
 * @since September 22nd, 2026
 */
public final class StateNode<C> implements State<C> {
    private final StateGraph<C> graph; // Graph enforcing configuration ownership and freezing.
    private final String name; // Unique, case-sensitive state name.
    private Consumer<StateContext<C>> enter; // Optional entry callback.
    private StateUpdate<C> update; // Optional update callback with an unboxed delta.
    private Consumer<StateContext<C>> exit; // Optional exit callback.

    /**
     * Creates a definition owned by a graph.
     *
     * @param graph owner
     * @param name  validated state name
     */
    StateNode(StateGraph<C> graph, String name) {
        this.graph = graph;
        this.name = name;
    }

    /**
     * Replaces the entry callback; use {@link #clearEnter()} to remove it.
     *
     * @param action entry action or null
     * @return this definition
     */
    public StateNode<C> onEnter(Consumer<StateContext<C>> action) {
        graph.checkEditable();
        enter = action;
        return this;
    }

    /**
     * Replaces the update callback; null removes it.
     *
     * @param action update action or null
     * @return this definition
     */
    public StateNode<C> onUpdate(StateUpdate<C> action) {
        graph.checkEditable();
        update = action;
        return this;
    }

    /**
     * Replaces the exit callback; use {@link #clearExit()} to remove it.
     *
     * @param action exit action or null
     * @return this definition
     */
    public StateNode<C> onExit(Consumer<StateContext<C>> action) {
        graph.checkEditable();
        exit = action;
        return this;
    }

    /**
     * Removes the entry callback without an overloaded null argument.
     *
     * @return this definition
     */
    public StateNode<C> clearEnter() {
        graph.checkEditable();
        enter = null;
        return this;
    }

    /**
     * Removes the update callback.
     *
     * @return this definition
     */
    public StateNode<C> clearUpdate() {
        graph.checkEditable();
        update = null;
        return this;
    }

    /**
     * Removes the exit callback without an overloaded null argument.
     *
     * @return this definition
     */
    public StateNode<C> clearExit() {
        graph.checkEditable();
        exit = null;
        return this;
    }

    /**
     * Tests graph ownership before registering a named state by identity.
     *
     * @param candidate possible owner
     * @return true for the defining graph
     */
    boolean belongsTo(StateGraph<?> candidate) {
        return graph == candidate;
    }

    /**
     * Returns the configured name.
     *
     * @return case-sensitive name
     */
    public String name() {
        return name;
    }

    /**
     * Invokes the configured entry callback when one is present.
     *
     * @param ctx live context for the entering machine
     */
    @Override
    public void onEnter(StateContext<C> ctx) {
        if (enter != null) enter.accept(ctx);
    }

    /**
     * Invokes the configured update callback when one is present.
     *
     * @param ctx live context for the active machine
     * @param delta accepted, scaled update time in seconds
     */
    @Override
    public void onUpdate(StateContext<C> ctx, float delta) {
        if (update != null) update.update(ctx, delta);
    }

    /**
     * Invokes the configured exit callback when one is present.
     *
     * @param ctx live context whose current state is still this source node
     */
    @Override
    public void onExit(StateContext<C> ctx) {
        if (exit != null) exit.accept(ctx);
    }

    /**
     * Returns the case-sensitive state name for diagnostics.
     *
     * @return configured state name
     */
    @Override
    public String toString() {
        return name;
    }
}
