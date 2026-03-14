package valthorne.ui.theme;

import valthorne.event.EventListener;

/**
 * A {@code ThemeListener} listens for changes in theme data and performs actions upon receiving
 * theme-related events.
 * <p>
 * This interface extends {@code EventListener<ThemeDataChangeEvent>} to define event handling
 * specific to {@code ThemeDataChangeEvent}. It provides a default method for handling events and
 * delegates the handling logic to {@code onThemeChanged}.
 * <p>
 * Implementing classes should override {@code onThemeChanged} to define custom behavior when
 * a theme change occurs.
 */
public interface ThemeListener extends EventListener<ThemeDataChangeEvent> {

    /**
     * Handles the ThemeDataChangeEvent when published by an {@code EventPublisher}.
     *
     * <p>
     * If the event is consumed within this method (via {@code Event.consume()}), no subsequent
     * listeners will process it. Exceptions thrown here may disrupt the event handling chain,
     * depending on the publisher's implementation.
     *
     * @param event the event to handle
     */
    default void handle(ThemeDataChangeEvent event) {
        onThemeChanged(event);
    }

    /**
     * Called when a theme change event occurs. This method is intended to be overridden
     * in implementing classes to define specific behavior that should be executed when the
     * theme changes.
     *
     * @param theme the ThemeDataChangeEvent containing information about the new theme data
     */
    void onThemeChanged(ThemeDataChangeEvent theme);

}