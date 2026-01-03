package valthorne.plugin;

/**
 * Represents a plugin interface that defines the lifecycle methods for a plugin system.
 * Implementing classes should provide initialization and cleanup logic to be executed
 * when the plugin is loaded or unloaded by the plugin manager.
 *
 * @author Albert Beaupre
 * @since March 13th, 2025
 */
public interface Plugin {

    /**
     * Initializes the plugin after instantiation.
     * Implementations should perform any necessary setup here.
     */
    void initialize();

    /**
     * Unloads the plugin and performs any necessary cleanup operations.
     * Implementations should release resources, save states if required,
     * and perform other shutdown tasks as needed.
     */
    void unload();
}