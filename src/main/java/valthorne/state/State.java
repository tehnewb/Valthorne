package valthorne.state;

/**
 * Optional lifecycle callbacks for one state. Override only the methods you need,
 * or use {@link StateMachine#state(String)} to define a state with lambdas.
 *
 * <p>States in a shared {@link StateGraph} must not keep per-machine mutable data.
 * Store that data in {@link StateContext#data()} instead. Entry and exit callbacks
 * may fire registered triggers, but must not recursively change states.</p>
 *
 * @param <C> shared user-data type
 * @author Albert Beaupre
 * @since February 12th, 2026
 */
public interface State<C> {
    /**
     * Called after this state becomes current and its timer is reset.
     *
     * @param ctx reused machine context
     */
    default void onEnter(StateContext<C> ctx) {
    }

    /**
     * Called once per running update, before automatic transition selection.
     *
     * @param ctx   reused machine context
     * @param delta finite, nonnegative scaled seconds
     */
    default void onUpdate(StateContext<C> ctx, float delta) {
    }

    /**
     * Called before leaving this state and before any transition action.
     *
     * @param ctx reused machine context
     */
    default void onExit(StateContext<C> ctx) {
    }
}
