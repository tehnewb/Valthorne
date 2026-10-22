package valthorne.ui.nodes;

import java.util.Objects;

/**
 * Immutable command description. Disabled items remain visible but cannot activate.
 * The callback is borrowed and is not invoked during construction or formatting.
 * @param text nonnull displayed label
 * @param action nonnull synchronous command
 * @param enabled whether user activation is permitted
 * @author Albert Beaupre
 */
public record PopupMenuItem(String text, Runnable action, boolean enabled) {
    /**
     * Validates command data before it can enter a menu snapshot.
     * @param text displayed label
     * @param action callback to run after dismissal
     * @param enabled initial availability
     * @throws NullPointerException if text or action is null
     */
    public PopupMenuItem {
        Objects.requireNonNull(text);
        Objects.requireNonNull(action);
    }
}
