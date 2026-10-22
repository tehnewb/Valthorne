package valthorne.ui.nodes.nano;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Immutable command description. Disabled items remain visible but cannot activate.
 * The callback is borrowed and is not invoked during construction or formatting.
 * @param text nonnull displayed label
 * @param action nonnull synchronous command
 * @param enabled whether user activation is permitted
 * @param checkable whether this command displays a checkbox
 * @param checked whether that checkbox is selected
 * @param icon optional leading icon
 * @param submenu optional provider of nested entries
 * @author Albert Beaupre
 */
public record NanoPopupMenuItem(String text, Runnable action, boolean enabled, boolean checkable, boolean checked, NanoPopupMenuIcon icon, Supplier<List<NanoPopupMenuItem>> submenu) {
    /**
     * Creates an immutable command description while borrowing its callback and optional icon.
     *
     * @param text nonnull text to display or process
     * @param action nonnull command invoked after menu dismissal
     * @param enabled whether this behavior is enabled
     */
    public NanoPopupMenuItem(String text, Runnable action, boolean enabled) {
        this(text, action, enabled, false, false, null, null);
    }
    /**
     * Creates an immutable command description while borrowing its callback and optional icon.
     *
     * @param text nonnull text to display or process
     * @param action nonnull command invoked after menu dismissal
     * @param enabled whether this behavior is enabled
     * @param icon optional borrowed leading icon
     */
    public NanoPopupMenuItem(String text, Runnable action, boolean enabled, NanoPopupMenuIcon icon) {
        this(text, action, enabled, false, false, icon, null);
    }
    /**
     * Creates an immutable command description while borrowing its callback and optional icon.
     *
     * @param text nonnull text to display or process
     * @param action nonnull command invoked after menu dismissal
     * @param enabled whether this behavior is enabled
     * @param checkable whether the command displays a checkbox
     * @param checked initial checkbox selection state
     */
    public NanoPopupMenuItem(String text, Runnable action, boolean enabled, boolean checkable, boolean checked) {
        this(text, action, enabled, checkable, checked, null, null);
    }
    /**
     * Creates an immutable command description while borrowing its callback and optional icon.
     *
     * @param text nonnull text to display or process
     * @param action nonnull command invoked after menu dismissal
     * @param enabled whether this behavior is enabled
     * @param checkable whether the command displays a checkbox
     * @param checked initial checkbox selection state
     * @param icon optional borrowed leading icon
     */
    public NanoPopupMenuItem(String text, Runnable action, boolean enabled, boolean checkable, boolean checked, NanoPopupMenuIcon icon) {
        this(text, action, enabled, checkable, checked, icon, null);
    }

    /**
     * Creates an enabled checkable command with the requested initial state.
     *
     * @param text nonnull text to display or process
     * @param action nonnull command invoked after menu dismissal
     * @param checked initial checkbox selection state
     * @return this control for further configuration
     */
    public static NanoPopupMenuItem check(String text, Runnable action, boolean checked) {
        /*
         * Retain the original action and checkbox value in one immutable command description.
         */
        return new NanoPopupMenuItem(text, action, true, true, checked, null, null);
    }
    /**
     * Creates a submenu with a fixed child snapshot.
     * @param text displayed parent label
     * @param children nested entries
     * @return submenu entry
     */
    public static NanoPopupMenuItem submenu(String text, List<NanoPopupMenuItem> children) {
        /*
         * Capture fixed child membership once, or retain the provider when entries must refresh at opening time.
         */
        List<NanoPopupMenuItem> snapshot = List.copyOf(children);
        return submenu(text, () -> snapshot);
    }
    /**
     * Creates a submenu whose children are refreshed on each opening.
     * @param text displayed parent label
     * @param children provider of nested entries
     * @return submenu entry
     */
    public static NanoPopupMenuItem submenu(String text, Supplier<List<NanoPopupMenuItem>> children) {
        /*
         * Capture fixed child membership once, or retain the provider when entries must refresh at opening time.
         */
        return new NanoPopupMenuItem(text, () -> {}, true, false, false, null, Objects.requireNonNull(children));
    }
    /**
     * Validates command data before it can enter a menu snapshot.
     * @param text displayed label
     * @param action callback to run after dismissal
     * @param enabled initial availability
     * @throws NullPointerException if text or action is null
     */
    public NanoPopupMenuItem {
        Objects.requireNonNull(text);
        Objects.requireNonNull(action);
        if (submenu != null && checkable) throw new IllegalArgumentException("A submenu cannot also be checked");
    }
}
