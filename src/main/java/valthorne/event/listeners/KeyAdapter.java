package valthorne.event.listeners;

import valthorne.event.events.KeyPressEvent;
import valthorne.event.events.KeyReleaseEvent;

/**
 * Adapter with no-op key callbacks so subclasses override only the events they need.
 *
 * <p>
 * Register an adapter instance through {@link KeyListener#register(valthorne.event.EventPublisher)}
 * or its priority overload. That helper explicitly attaches the instance to the key-press and
 * key-release numeric routes.
 * </p>
 *
 * @author Albert Beaupre
 * @since February 6th, 2026
 */
public class KeyAdapter implements KeyListener {

    /** Default no-op key-press callback. */
    @Override
    public void keyPressed(KeyPressEvent event) {
    }

    /** Default no-op key-release callback. */
    @Override
    public void keyReleased(KeyReleaseEvent event) {
    }
}
