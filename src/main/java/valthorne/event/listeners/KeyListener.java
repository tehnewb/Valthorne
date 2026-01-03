package valthorne.event.listeners;

import valthorne.event.EventListener;
import valthorne.event.events.KeyEvent;
import valthorne.event.events.KeyPressEvent;
import valthorne.event.events.KeyReleaseEvent;

/**
 * The {@code KeyListener} interface represents a specialized {@code EventListener}
 * for handling {@code KeyEvent} instances, such as key press and key release events.
 * <p>
 * This interface provides default behavior for processing {@code KeyEvent} instances
 * and delegates specific event types (e.g., {@code KeyPressEvent} and {@code KeyReleaseEvent})
 * to their respective abstract methods, {@code keyPressed} and {@code keyReleased}.
 * <p>
 * Implementors of this interface must define the behavior for these specific key events.
 * <p>
 * It is designed to handle two types of events:
 * <p>
 * - {@code KeyPressEvent}: Triggered when a key is pressed.
 * - {@code KeyReleaseEvent}: Triggered when a key is released.
 *
 * @author Albert Beaupre
 * @since December 16th, 2025
 */
public interface KeyListener extends EventListener<KeyEvent> {

    @Override
    default void handle(KeyEvent event) {
        switch (event) {
            case KeyPressEvent e -> keyPressed(e);
            case KeyReleaseEvent e -> keyReleased(e);
            default -> throw new IllegalStateException("Unexpected value: " + event);
        }
    }

    /**
     * Handles a key press event triggered when a key is pressed.
     * Implementors should define the specific behavior that should occur
     * when a key press is detected.
     *
     * @param event the key press event to handle, containing details about
     *              the key pressed and any associated modifiers (such as
     *              Shift, Ctrl, Alt, or Super).
     */
    void keyPressed(KeyPressEvent event);

    /**
     * Handles a key release event triggered when a key is released.
     * Implementors should define the specific behavior that should occur
     * when a key release is detected.
     *
     * @param event the key release event to handle, containing details about
     *              the key released and any associated modifiers (such as
     *              Shift, Ctrl, Alt, or Super).
     */
    void keyReleased(KeyReleaseEvent event);

}
