package valthorne.event.listeners;

import valthorne.event.events.KeyPressEvent;
import valthorne.event.events.KeyReleaseEvent;

/**
 * The {@code KeyAdapter} class is an abstract adapter class for receiving key events.
 * This class provides default (empty) implementations for the methods defined in the
 * {@code KeyListener} interface, allowing subclasses to selectively override only
 * the methods they are interested in.
 * <p>
 * Subclassing {@code KeyAdapter} is useful when creating a listener for key press and
 * key release events when not all methods from the {@code KeyListener} interface are
 * required to be implemented.
 * <p>
 * The {@code keyPressed} method is called when a key is pressed, and the
 * {@code keyReleased} method is called when a key is released. By default, these methods
 * are non-operative and can be overridden by a subclass to define specific behavior.
 *
 * @author Albert Beaupre
 * @since February 6th, 2026
 */
public class KeyAdapter implements KeyListener {

    @Override
    public void keyPressed(KeyPressEvent event) {}

    @Override
    public void keyReleased(KeyReleaseEvent event) {}
}
