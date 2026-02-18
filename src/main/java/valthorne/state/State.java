package valthorne.state;

/**
 * A state in a finite state machine (FSM).
 *
 * <h2>Example</h2>
 * <pre>{@code
 * public final class IdleState implements State<PlayerCtx> {
 *     @Override
 *     public void onEnter(StateContext<PlayerCtx> ctx) {
 *         // Reset state-local timers, play enter animation, etc.
 *     }
 *
 *     @Override
 *     public void onUpdate(StateContext<PlayerCtx> ctx, float dtSec) {
 *         // Run logic for this state.
 *         // Transition rules are handled by the StateMachine, not here.
 *     }
 *
 *     @Override
 *     public void onExit(StateContext<PlayerCtx> ctx) {
 *         // Cleanup: stop sounds, clear flags, etc.
 *     }
 * }
 * }</pre>
 *
 * <p>States should be lightweight and typically stateless, with shared data stored in {@link StateContext#data()}.</p>
 *
 * @param <C> user-defined context type
 * @author Albert Beaupre
 * @since February 12th, 2026
 */
public interface State<C> {

    /**
     * Called when the state becomes active.
     *
     * <p>This is called after a transition is taken and {@link StateContext#timeInStateSec()} has been reset to 0.</p>
     * <p>If the transition had an action, the action is executed before this method.</p>
     *
     * @param ctx state context (shared, reused)
     */
    void onEnter(StateContext<C> ctx);

    /**
     * Called every update while the state is active.
     *
     * <p>This is called once per {@link StateMachine#update(float)} call (as long as this state remains active
     * during the update).</p>
     *
     * @param ctx   state context (shared, reused)
     * @param dtSec delta time in seconds (clamped >= 0 by the machine)
     */
    void onUpdate(StateContext<C> ctx, float dtSec);

    /**
     * Called when the state stops being active.
     *
     * <p>This is called before the transition action (if any) and before entering the next state.</p>
     *
     * @param ctx state context (shared, reused)
     */
    void onExit(StateContext<C> ctx);
}