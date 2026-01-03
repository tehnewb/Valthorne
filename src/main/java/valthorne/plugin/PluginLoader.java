package valthorne.plugin;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A utility class for loading plugins from JAR files asynchronously.
 * Plugins are expected to implement the {@link Plugin} interface, and this class manages their discovery,
 * loading, and instantiation in a thread-safe and efficient manner.
 *
 * <p>This class uses a configurable thread pool to load plugins concurrently, ensuring scalability while
 * preventing resource exhaustion. It provides detailed logging for debugging and monitoring using
 * Java's built-in {@link Logger}, and it maintains a list of successfully loaded plugins for later access.</p>
 *
 * <p>Example usage:</p>
 * <pre>
 * PluginLoader loader = new PluginLoader(4); // Use 4 threads
 * loader.loadFromFolder(new File("plugins"));
 * List<Plugin> plugins = loader.getLoadedPlugins();
 * loader.shutdown();
 * </pre>
 *
 * @author Albert Beaupre
 * @version 2.0
 * @since May 1st, 2024
 */
public class PluginLoader {

    /**
     * Logger instance for tracking plugin loading events and errors.
     */
    private static final Logger logger = Logger.getLogger(PluginLoader.class.getName());

    /**
     * Default number of threads for the executor service if not specified.
     */
    private static final int DEFAULT_THREAD_POOL_SIZE = 2;

    /**
     * Maximum time to wait for executor shutdown in seconds.
     */
    private static final int SHUTDOWN_TIMEOUT_SECONDS = 10;

    /**
     * Executor service for managing asynchronous plugin loading tasks.
     */
    private final ExecutorService executor;

    /**
     * Thread-safe list of successfully loaded plugins.
     */
    private final List<Plugin> loadedPlugins;

    /**
     * Constructs a {@code PluginLoader} with a specified thread pool size.
     *
     * @param threadPoolSize The number of threads to use for loading plugins. Must be positive.
     * @throws IllegalArgumentException if {@code threadPoolSize} is less than 1.
     */
    public PluginLoader(int threadPoolSize) {
        if (threadPoolSize < 1)
            throw new IllegalArgumentException("Thread pool size must be at least 1, got: " + threadPoolSize);

        this.executor = Executors.newFixedThreadPool(threadPoolSize);
        this.loadedPlugins = Collections.synchronizedList(new ArrayList<>());
        logger.log(Level.INFO, "Initialized PluginLoader with {0} threads", threadPoolSize);
    }

    /**
     * Constructs a {@code PluginLoader} with the default thread pool size ({@value DEFAULT_THREAD_POOL_SIZE}).
     */
    public PluginLoader() {
        this(DEFAULT_THREAD_POOL_SIZE);
    }

    /**
     * Loads all plugin JAR files from the specified folder asynchronously.
     * Each JAR file is processed concurrently up to the configured thread pool size, and the method blocks
     * until all plugins are loaded or an error occurs.
     *
     * @param folder The folder containing plugin JAR files. Must be a valid, existing directory.
     * @throws IllegalArgumentException if {@code folder} is null, does not exist, or is not a directory.
     * @throws PluginLoadingException   if an unrecoverable error occurs during loading.
     */
    public void loadFromFolder(File folder) {
        validateFolder(folder);
        File[] jarFiles = listJarFiles(folder);
        if (jarFiles.length == 0) {
            logger.log(Level.WARNING, "No JAR files found in folder: {0}", folder.getAbsolutePath());
            return;
        }

        logger.log(Level.INFO, "Found {0} JAR files in folder: {1}", new Object[]{jarFiles.length, folder.getAbsolutePath()});
        List<CompletableFuture<List<Plugin>>> futures = submitLoadingTasks(jarFiles);
        waitForCompletion(futures);
    }

    /**
     * Loads plugins from a single JAR file asynchronously.
     * Only classes implementing {@link Plugin} are loaded and instantiated.
     *
     * @param file The JAR file to load plugins from. Must be a valid, readable JAR file.
     * @return A {@link CompletableFuture} containing the list of loaded plugins from this JAR file.
     * @throws IllegalArgumentException if {@code file} is null, does not exist, or is not a JAR file.
     */
    public CompletableFuture<List<Plugin>> loadFromFile(File file) {
        validateFile(file);
        return CompletableFuture.supplyAsync(() -> loadPluginsFromJar(file), executor)
                .exceptionally(throwable -> {
                    logger.log(Level.SEVERE, "Failed to load plugins from JAR: " + file.getName(), throwable);
                    return Collections.emptyList(); // Return empty list on failure
                });
    }

    /**
     * Returns an unmodifiable view of the currently loaded plugins.
     *
     * @return A thread-safe, unmodifiable list of loaded {@link Plugin} instances.
     */
    public List<Plugin> getLoadedPlugins() {
        return Collections.unmodifiableList(loadedPlugins);
    }

    /**
     * Shuts down the internal executor service gracefully, waiting up to {@value SHUTDOWN_TIMEOUT_SECONDS}
     * seconds for all tasks to complete.
     *
     * <p>This method should be called when the {@code PluginLoader} is no longer needed to ensure proper
     * resource cleanup.</p>
     */
    public void shutdown() {
        if (!executor.isShutdown()) {
            logger.info("Shutting down PluginLoader executor service");
            executor.shutdown();
            try {
                if (!executor.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    logger.log(Level.WARNING, "Executor did not terminate within {0} seconds, forcing shutdown", SHUTDOWN_TIMEOUT_SECONDS);
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                logger.log(Level.SEVERE, "Interrupted while shutting down executor", e);
                executor.shutdownNow();
                Thread.currentThread().interrupt(); // Preserve interrupt status
            }
        }
    }

    /**
     * Validates that the provided folder is a valid directory.
     *
     * @param folder The folder to validate.
     * @throws IllegalArgumentException if the folder is invalid.
     */
    private void validateFolder(File folder) {
        Objects.requireNonNull(folder, "Folder cannot be null");
        if (!folder.exists() || !folder.isDirectory())
            throw new IllegalArgumentException("Invalid folder: " + folder.getAbsolutePath() + " (must exist and be a directory)");
    }

    /**
     * Validates that the provided file is a valid JAR file.
     *
     * @param file The file to validate.
     * @throws IllegalArgumentException if the file is invalid.
     */
    private void validateFile(File file) {
        Objects.requireNonNull(file, "File cannot be null");
        if (!file.exists() || !file.isFile() || !file.getName().endsWith(".jar"))
            throw new IllegalArgumentException("Invalid JAR file: " + file.getAbsolutePath() + " (must exist and end with .jar)");
    }

    /**
     * Lists all JAR files in the specified folder.
     *
     * @param folder The folder to scan.
     * @return An array of JAR files, or an empty array if none are found.
     */
    private File[] listJarFiles(File folder) {
        File[] jarFiles = folder.listFiles(file -> file.isFile() && file.getName().endsWith(".jar"));
        return jarFiles != null ? jarFiles : new File[0];
    }

    /**
     * Submits loading tasks for all JAR files and returns the list of futures.
     *
     * @param jarFiles The array of JAR files to process.
     * @return A list of {@link CompletableFuture} instances representing the loading tasks.
     */
    private List<CompletableFuture<List<Plugin>>> submitLoadingTasks(File[] jarFiles) {
        List<CompletableFuture<List<Plugin>>> futures = new ArrayList<>(jarFiles.length);
        for (File file : jarFiles) {
            futures.add(loadFromFile(file).thenApply(plugins -> {
                synchronized (loadedPlugins) {
                    loadedPlugins.addAll(plugins);
                }
                return plugins;
            }));
        }
        return futures;
    }

    /**
     * Waits for all loading tasks to complete.
     *
     * @param futures The list of futures to wait for.
     * @throws PluginLoadingException if an unrecoverable error occurs.
     */
    private void waitForCompletion(List<CompletableFuture<List<Plugin>>> futures) {
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            logger.log(Level.INFO, "Successfully loaded {0} plugins", loadedPlugins.size());
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error during plugin loading", e);
            throw new PluginLoadingException("Failed to load plugins from folder", e);
        }
    }

    /**
     * Loads and instantiates plugins from a JAR file.
     *
     * @param file The JAR file to process.
     * @return A list of loaded {@link Plugin} instances.
     */
    private List<Plugin> loadPluginsFromJar(File file) {
        List<Plugin> plugins = new ArrayList<>();
        try (URLClassLoader classLoader = createClassLoader(file)) {
            try (JarFile jar = new JarFile(file)) {
                jar.stream()
                        .filter(entry -> entry.getName().endsWith(".class"))
                        .map(entry -> entry.getName().replace("/", ".").replace(".class", ""))
                        .forEach(className -> loadAndInstantiateClass(className, classLoader, file, plugins));
                logger.log(Level.FINE, "Loaded {0} plugins from JAR: {1}", new Object[]{plugins.size(), file.getName()});
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to process JAR file: " + file.getName(), e);
        }
        return plugins;
    }

    /**
     * Creates a {@link URLClassLoader} for the specified JAR file.
     *
     * @param file The JAR file.
     * @return A new {@link URLClassLoader} instance.
     * @throws IOException if the URL conversion fails.
     */
    private URLClassLoader createClassLoader(File file) throws IOException {
        try {
            return new URLClassLoader(new URL[]{file.toURI().toURL()}, ClassLoader.getSystemClassLoader());
        } catch (MalformedURLException e) {
            throw new IOException("Invalid JAR file URL: " + file.getAbsolutePath(), e);
        }
    }

    /**
     * Loads and instantiates a single class if it implements {@link Plugin}.
     *
     * @param className   The fully qualified class name.
     * @param classLoader The class loader to use.
     * @param file        The JAR file being processed (for logging).
     * @param plugins     The list to add instantiated plugins to.
     */
    private void loadAndInstantiateClass(String className, URLClassLoader classLoader, File file, List<Plugin> plugins) {
        try {
            Class<?> clazz = classLoader.loadClass(className);
            if (isValidPluginClass(clazz)) {
                Plugin plugin = instantiatePlugin(clazz);
                plugins.add(plugin);
                logger.log(Level.FINE, "Loaded plugin: {0} from JAR: {1}", new Object[]{className, file.getName()});
            }
        } catch (ClassNotFoundException e) {
            logger.log(Level.SEVERE, "Class not found: " + className + " in JAR: " + file.getName(), e);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to instantiate plugin: " + className + " in JAR: " + file.getName(), e);
        }
    }

    /**
     * Checks if a class is a valid plugin (implements {@link Plugin}, not abstract, not an interface).
     *
     * @param clazz The class to check.
     * @return {@code true} if the class is a valid plugin, {@code false} otherwise.
     */
    private boolean isValidPluginClass(Class<?> clazz) {
        return Plugin.class.isAssignableFrom(clazz) && !clazz.isInterface() && !Modifier.isAbstract(clazz.getModifiers());
    }

    /**
     * Instantiates a plugin class using its no-arg constructor.
     *
     * @param clazz The class to instantiate.
     * @return The instantiated {@link Plugin} instance.
     * @throws ReflectiveOperationException if instantiation fails.
     */
    private Plugin instantiatePlugin(Class<?> clazz) throws ReflectiveOperationException {
        try {
            Plugin plugin = (Plugin) clazz.getDeclaredConstructor().newInstance();
            plugin.initialize();
            return plugin;
        } catch (NoSuchMethodException e) {
            throw new ReflectiveOperationException("Plugin class " + clazz.getName() + " must have a no-arg constructor", e);
        } catch (InvocationTargetException e) {
            throw new ReflectiveOperationException("Plugin initialization failed for " + clazz.getName(), e.getCause());
        }
    }

    /**
     * Unloads and removes a plugin instance from this loader.
     *
     * <p>This will invoke {@code plugin.unload()} and then remove the instance
     * from the internal {@code loadedPlugins} list. Any exception thrown by
     * {@code unload()} is logged but will not prevent the plugin from being
     * removed from the list.</p>
     *
     * @param plugin the plugin instance to remove (must not be {@code null})
     * @return {@code true} if the plugin was present and removed, {@code false} otherwise
     * @throws NullPointerException if {@code plugin} is {@code null}
     */
    public boolean removePlugin(Plugin plugin) {
        Objects.requireNonNull(plugin, "plugin cannot be null");

        // Fast check: only proceed if we actually track this plugin
        boolean contained;
        synchronized (loadedPlugins) {
            contained = loadedPlugins.contains(plugin);
        }
        if (!contained) {
            logger.log(Level.FINE, "Attempted to remove a plugin that is not loaded: {0}", plugin.getClass().getName());
            return false;
        }

        // Try to unload the plugin; log but continue on failure
        try {
            plugin.unload();
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error while unloading plugin: " + plugin.getClass().getName(), e);
        }

        // Remove from the list
        synchronized (loadedPlugins) {
            return loadedPlugins.remove(plugin);
        }
    }

    /**
     * Convenience overload: unloads and removes the first loaded plugin of the given type.
     *
     * @param pluginClass the concrete plugin class to remove
     * @return {@code true} if a matching plugin was found and removed, {@code false} otherwise
     * @throws NullPointerException if {@code pluginClass} is {@code null}
     */
    public boolean removePlugin(Class<? extends Plugin> pluginClass) {
        Objects.requireNonNull(pluginClass, "pluginClass cannot be null");
        Plugin toRemove = null;
        synchronized (loadedPlugins) {
            for (Plugin p : loadedPlugins) {
                if (pluginClass.isInstance(p)) {
                    toRemove = p;
                    break;
                }
            }
        }
        return toRemove != null && removePlugin(toRemove);
    }

}