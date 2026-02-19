package valthorne.state;

/**
 * Runs when a transition is taken.
 *
 * <h2>Example</h2>
 * <pre>{@code
 * TransitionAction<PlayerCtx> resetJumpTimer = (ctx, tr) -> {
 *     ctx.data().lastJumpTime = 0f;
 * };
 *
 * fsm.addTransition(idle, jump, 10, new Trigger("jump"), ctx -> ctx.data().grounded(), 0f, "jump", resetJumpTimer);
 * }</pre>
 *
 * <p>This is where you put side effects that must happen exactly when the transition occurs:
 * reset timers, play sounds, fire events, set animation, etc.</p>
 *
 * <p>Execution order:</p>
 * <ol>
 *     <li>oldState.onExit(ctx)</li>
 *     <li>action.run(ctx, transition)</li>
 *     <li>newState.onEnter(ctx)</li>
 * </ol>
 *
 * @param <C> user-defined context type
 * @author Albert Beaupre
 * @since February 12th, 2026
 */
@FunctionalInterface
public interface TransitionAction<C> {

    /**
     * Called when the FSM takes a transition.
     *
     * <p>This runs after the old state exits and before the new state enters.</p>
     *
     * @param ctx        state context
     * @param transition the transition being taken
     */
    void run(StateContext<C> ctx, Transition<C> transition);
}