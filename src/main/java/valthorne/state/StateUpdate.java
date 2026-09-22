package valthorne.state;

/**
 * A state update callback without boxing the frame delta.
 *
 * @param <C> user-data type
 * @author Albert Beaupre
 * @since September 22nd, 2026
 */
@FunctionalInterface
public interface StateUpdate<C> {
    /**
     * Updates the active state's behavior.
     *
     * @param ctx   reused machine context
     * @param delta scaled frame seconds
     */
    void update(StateContext<C> ctx, float delta);
}
