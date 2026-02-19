package valthorne.state;

/**
 * A guard blocks transitions.
 *
 * <h2>Example</h2>
 * <pre>{@code
 * Guard<PlayerCtx> isGrounded = ctx -> ctx.data().grounded();
 *
 * fsm.addTransition(idle, jump, 10, new Trigger("jump"), isGrounded, 0f, "jump", null);
 * }</pre>
 *
 * <p>Guards are separate from triggers:</p>
 * <ul>
 *     <li>Triggers represent queued events (like "jumpPressed").</li>
 *     <li>Guards represent boolean checks (like "isGrounded").</li>
 * </ul>
 *
 * @param <C> user-defined context type
 * @author Albert Beaupre
 * @since February 12th, 2026
 */
@FunctionalInterface
public interface Guard<C> {

    /**
     * Evaluates whether a transition is allowed right now.
     *
     * <p>Return true to allow the transition to be considered valid.</p>
     * <p>Return false to block the transition.</p>
     *
     * @param ctx state context
     * @return true if allowed
     */
    boolean allow(StateContext<C> ctx);
}