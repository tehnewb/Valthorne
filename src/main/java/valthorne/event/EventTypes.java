package valthorne.event;

import valthorne.event.events.*;
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
 * <pre>{@code
 * EventHandler<KeyPressEvent> listener = event -> {
 *     System.out.println(event.getKey());
 * };
 * valthorne.JGL.subscribe(EventTypes.KEY_PRESS, listener);
 * // Remove the same listener when its owner no longer needs input.
 * valthorne.JGL.unsubscribe(EventTypes.KEY_PRESS, listener);
 * }</pre>
 *
 * @author Albert Beaupre
 */
public final class EventTypes {

    /**
     * Route for explicit base KeyEvent publications. Key press and release events
     * do not also dispatch through this route merely because they inherit KeyEvent.
     */
    public static final EventType<KeyEvent> KEY = new EventType<>(0, "key");

    /**
     * Key-press route used for physical key commands. Text entry is delivered
     * separately through TEXT_INPUT rather than inferred from key codes.
     */
    public static final EventType<KeyPressEvent> KEY_PRESS = new EventType<>(1, "key-press");

    /**
     * Key-release route, independent of both the raw key and key-press routes.
     */
    public static final EventType<KeyReleaseEvent> KEY_RELEASE = new EventType<>(2, "key-release");

    /**
     * Route for explicit base MouseEvent publications. Specialized mouse events
     * use their own routes and do not automatically notify this one.
     */
    public static final EventType<MouseEvent> MOUSE = new EventType<>(3, "mouse");

    /**
     * Mouse-position movement route carrying the producer's movement payload.
     * Drag notifications have a separate route.
     */
    public static final EventType<MouseMoveEvent> MOUSE_MOVE = new EventType<>(4, "mouse-move");

    /**
     * Mouse-drag route, allowing drag listeners to subscribe independently of
     * ordinary motion or button events.
     */
    public static final EventType<MouseDragEvent> MOUSE_DRAG = new EventType<>(5, "mouse-drag");

    /**
     * Mouse-button press route used by input handlers and UI press routing.
     */
    public static final EventType<MousePressEvent> MOUSE_PRESS = new EventType<>(6, "mouse-press");

    /**
     * Mouse-button release route used by input handlers and UI release routing.
     */
    public static final EventType<MouseReleaseEvent> MOUSE_RELEASE = new EventType<>(7, "mouse-release");

    /**
     * Wheel and trackpad scroll route. MouseScrollEvent exposes precise fractional
     * deltas as well as integer compatibility accessors.
     */
    public static final EventType<MouseScrollEvent> MOUSE_SCROLL = new EventType<>(8, "mouse-scroll");

    /**
     * Window dimension-change route used to notify viewport and UI sizing logic.
     */
    public static final EventType<WindowResizeEvent> WINDOW_RESIZE = new EventType<>(9, "window-resize");

    /**
     * Global theme mutation route for token, resource and registered rule-map
     * notifications. Listeners inspect the event payload to identify the theme.
     */
    public static final EventType<ThemeDataChangeEvent> THEME_DATA_CHANGE =
            new EventType<>(10, "theme-data-change");

    /**
     * Number of slots needed for built-in route IDs zero through twelve. This
     * exclusive upper bound must be updated when a new built-in ID is added.
     */
    public static final int COUNT = 13;

    /**
     * Window focus-change route used to distinguish focus gain and loss, including
     * input cleanup when the window loses focus.
     */
    public static final EventType<WindowFocusEvent> WINDOW_FOCUS = new EventType<>(11, "window-focus");
    /**
     * Committed-text route carrying a string. The native character callback
     * supplies one Unicode code point per event, while other producers may supply
     * longer strings. This is separate from physical key commands.
     */
    public static final EventType<TextInputEvent> TEXT_INPUT = new EventType<>(12, "text-input");

    /**
     * Prevents instances of the static route registry. The assertion also rejects
     * attempted reflective construction rather than creating a meaningless holder.
     *
     * @throws AssertionError whenever this constructor executes
     */
    private EventTypes() {
        throw new AssertionError("No instances.");
    }
}
