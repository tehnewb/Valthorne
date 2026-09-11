package valthorne.plugin;

/**
 * Unchecked failure carrying plugin-loading context and its underlying cause.
 * The loader uses this to preserve the original exception while identifying the
 * failed loading operation. Constructing the exception does not unload plugins,
 * retry loading, or perform any resource cleanup.
 *
 * @author Albert Beaupre
 */
public class PluginLoadingException extends RuntimeException {
    /**
     * Constructs a new {@code PluginLoadingException} with the specified message and cause.
     *
     * @param message The error message.
     * @param cause   The underlying cause of the failure.
     */
    public PluginLoadingException(String message, Throwable cause) {
        super(message, cause);
    }
}