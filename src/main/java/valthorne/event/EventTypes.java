package valthorne.event;

import valthorne.event.events.KeyEvent;
import valthorne.event.events.KeyPressEvent;
import valthorne.event.events.KeyReleaseEvent;
import valthorne.event.events.MouseDragEvent;
import valthorne.event.events.MouseEvent;
import valthorne.event.events.MouseMoveEvent;
import valthorne.event.events.MousePressEvent;
import valthorne.event.events.MouseReleaseEvent;
import valthorne.event.events.MouseScrollEvent;
import valthorne.event.events.WindowResizeEvent;
import valthorne.ui.theme.ThemeDataChangeEvent;

/**
 * Central registry of every built-in event route used by the Valthorne event system.
 *
 * <p>
 * Numeric IDs replace class-keyed routing. IDs are intentionally explicit, unique, dense, and
 * stable. Do not derive these values from enum ordinals, class hash codes, class names, or runtime
 * registration order.
 * </p>
 *
 * <h2>Inheritance is not routing</h2>
 * <p>
 * Java inheritance remains useful for sharing payload fields and APIs, but the publisher no longer
 * walks superclasses. For example, {@link KeyPressEvent} extends {@link KeyEvent}, yet a key-press
 * publication uses only {@link #KEY_PRESS}. A listener that wants both key press and key release
 * explicitly registers for both routes. The specialized listener interfaces in
 * {@code valthorne.event.listeners} provide convenience methods for exactly that purpose.
 * </p>
 *
 * <h2>Adding a new event type</h2>
 * <ol>
 *     <li>Assign the next unused integer ID.</li>
 *     <li>Add an {@link EventType} constant here.</li>
 *     <li>Increment {@link #COUNT}.</li>
 *     <li>Pass that constant to the concrete event's {@link Event} constructor chain.</li>
 * </ol>
 *
 * @author Albert Beaupre
 */
public final class EventTypes {

    /** Base/raw key event route. */
    public static final EventType<KeyEvent> KEY = new EventType<>(0, "key");

    /** Key-down route. */
    public static final EventType<KeyPressEvent> KEY_PRESS = new EventType<>(1, "key-press");

    /** Key-up route. */
    public static final EventType<KeyReleaseEvent> KEY_RELEASE = new EventType<>(2, "key-release");

    /** Base/raw mouse event route. */
    public static final EventType<MouseEvent> MOUSE = new EventType<>(3, "mouse");

    /** Mouse movement route. */
    public static final EventType<MouseMoveEvent> MOUSE_MOVE = new EventType<>(4, "mouse-move");

    /** Mouse drag route. */
    public static final EventType<MouseDragEvent> MOUSE_DRAG = new EventType<>(5, "mouse-drag");

    /** Mouse-button press route. */
    public static final EventType<MousePressEvent> MOUSE_PRESS = new EventType<>(6, "mouse-press");

    /** Mouse-button release route. */
    public static final EventType<MouseReleaseEvent> MOUSE_RELEASE = new EventType<>(7, "mouse-release");

    /** Mouse-wheel/trackpad scroll route. */
    public static final EventType<MouseScrollEvent> MOUSE_SCROLL = new EventType<>(8, "mouse-scroll");

    /** Window dimension change route. */
    public static final EventType<WindowResizeEvent> WINDOW_RESIZE = new EventType<>(9, "window-resize");

    /** Theme token/resource/rule mutation route. */
    public static final EventType<ThemeDataChangeEvent> THEME_DATA_CHANGE =
            new EventType<>(10, "theme-data-change");

    /** Number of route slots required by all built-in event IDs. */
    public static final int COUNT = 11;

    private EventTypes() {
        throw new AssertionError("No instances.");
    }
}
