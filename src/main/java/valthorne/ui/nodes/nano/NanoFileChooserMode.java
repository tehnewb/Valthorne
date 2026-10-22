package valthorne.ui.nodes.nano;

/**
 * Determines whether approval requires an existing item or permits a new file.
 * @author Albert Beaupre
 */
public enum NanoFileChooserMode {
    /**
     * Select existing readable files or directories according to the selection policy.
     */
    OPEN,
    /**
     * Choose one writable file destination, confirming any existing target.
     */
    SAVE
}
