package valthorne.ui.theme;

import valthorne.event.Event;

/**
 * Represents an event that carries {@link ThemeData} as its payload, typically emitted when
 * theme-related data changes in the system. This event extends the {@code Event} class, enabling
 * it to be processed and optionally consumed by subscribers or listeners.
 * <p>
 * The {@code ThemeDataChangeEvent} contains a {@code ThemeData} object that holds the
 * associated information about the theme that has been modified, updated, or changed.
 * <p>
 * This class provides methods to retrieve and modify the contained {@code ThemeData}.
 */
public class ThemeDataChangeEvent extends Event {

    private ThemeData data;

    /**
     * Constructs a new {@code ThemeDataChangeEvent} with the specified {@code ThemeData}.
     * This event signifies a change in theme-related data and carries {@code ThemeData}
     * as its payload, which clients can process or utilize as necessary.
     *
     * @param data the {@code ThemeData} object representing the new or updated theme-related data
     */
    public ThemeDataChangeEvent(ThemeData data) {
        this.data = data;
    }

    /**
     * Retrieves the theme-related data associated with this event.
     *
     * @return the {@code ThemeData} object representing the current theme-related data
     */
    public ThemeData getData() {
        return data;
    }

    /**
     * Sets the theme-related data for this event.
     *
     * @param data the {@code ThemeData} object representing the new or updated theme-related data
     * @return the current {@code ThemeDataChangeEvent} instance with the updated {@code ThemeData}
     */
    public ThemeDataChangeEvent setData(ThemeData data) {
        this.data = data;
        return this;
    }
}
