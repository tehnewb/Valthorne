package valthorne.ui.nodes.nano;

/**
 * Accepted Open-mode item types; directories remain navigable in every policy.
 * Save always requires a file destination regardless of this Open-mode setting.
 * @author Albert Beaupre
 */
public enum NanoFileSelectionMode {
    /**
     * Approve regular files; activating a directory navigates into it.
     */
    FILES,
    /**
     * Approve directories, including the current directory when nothing is entered.
     */
    DIRECTORIES,
    /**
     * Approve either regular files or directories.
     */
    FILES_AND_DIRECTORIES
}
