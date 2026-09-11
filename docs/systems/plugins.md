# Plugin loading and lifecycle

Author: Albert Beaupre

[System manual](README.md)

## Purpose

The plugin system discovers and initializes implementations of Plugin from supplied loading locations. PluginLoader coordinates asynchronous loading through an executor, and Plugin defines initialize/unload callbacks. Use this for application extensions whose lifecycle you control.

## Features and when to use them

| Feature | Purpose |
| --- | --- |
| Discovery and loading | The loader inspects supplied files/folders using its supported class-loading mechanism. |
| Initialization | A discovered plugin performs setup in initialize. |
| Loaded registry | Successfully loaded plugins are retained for later access and cleanup. |
| Unloading and shutdown | Plugin cleanup and executor shutdown are distinct responsibilities. |
| Failure context | PluginLoadingException preserves a loading error and its cause. |

## Getting started

1. Implement Plugin with a loadable implementation and explicit cleanup in unload.
2. Package the extension according to the loader's discovery contract.
3. Create a loader, request loading, and observe completion before using plugins.
4. Unload plugins and shut down loading work during application cleanup using the appropriate operations.

## Ownership and lifecycle

Plugin initialization can run on a loading thread. Schedule graphics work onto the engine thread rather than assuming initialization owns the OpenGL context. Plugin code executes as application code; the loader is not a sandbox.

## Important behavior

- Do not assume returning from an asynchronous request means initialization has completed.
- Executor shutdown is not a substitute for a plugin's resource cleanup.
- Keep registration/removal symmetric for plugin-created event listeners and UI.

## Components and examples

The sections below explain each component and its declared public or protected operations. Expand an operation reference for signatures, parameters, results, and edge cases. Inherited operations are documented with their base type. Examples embedded in component descriptions are integration fragments: supply the surrounding resources and application variables they name.

- [`Plugin`](#type-plugin)
- [`PluginLoader`](#type-pluginloader)
- [`PluginLoadingException`](#type-pluginloadingexception)

<a id="type-plugin"></a>

### Plugin

[Source](../../src/main/java/valthorne/plugin/Plugin.java#L11)

Represents a plugin interface that defines the lifecycle methods for a plugin system.
Implementing classes should provide initialization and cleanup logic to be executed
when the plugin is loaded or unloaded by the plugin manager.

<details>
<summary>Plugin operation reference (2 declarations)</summary>

#### initialize

```java
void initialize()
```

Initializes the plugin after instantiation.
Implementations should perform any necessary setup here.

#### unload

```java
void unload()
```

Unloads the plugin and performs any necessary cleanup operations.
Implementations should release resources, save states if required,
and perform other shutdown tasks as needed.

</details>

<a id="type-pluginloader"></a>

### PluginLoader

[Source](../../src/main/java/valthorne/plugin/PluginLoader.java#L43)

A utility class for loading plugins from JAR files asynchronously.
Plugins are expected to implement the `Plugin` interface, and this class manages their discovery,
loading, and instantiation in a thread-safe and efficient manner.

This class uses a configurable thread pool to load plugins concurrently, ensuring scalability while
preventing resource exhaustion. It provides detailed logging for debugging and monitoring using
Java's built-in `Logger`, and it maintains a list of successfully loaded plugins for later access.

Example usage:

```java
PluginLoader loader = new PluginLoader(4); // Use 4 threads
loader.loadFromFolder(new File("plugins"));
List<Plugin> plugins = loader.getLoadedPlugins();
loader.shutdown();
```

<details>
<summary>PluginLoader operation reference (8 declarations)</summary>

#### Constructor

```java
public PluginLoader(int threadPoolSize)
```

Constructs a `PluginLoader` with a specified thread pool size.

- **`threadPoolSize`** — The number of threads to use for loading plugins. Must be positive.

**Throws `IllegalArgumentException`:** if `threadPoolSize` is less than 1.

#### Constructor

```java
public PluginLoader()
```

Constructs a `PluginLoader` with the default thread pool size (`DEFAULT_THREAD_POOL_SIZE`).

#### loadFromFolder

```java
public void loadFromFolder(File folder)
```

Loads all plugin JAR files from the specified folder asynchronously.
Each JAR file is processed concurrently up to the configured thread pool size, and the method blocks
until all plugins are loaded or an error occurs.

- **`folder`** — The folder containing plugin JAR files. Must be a valid, existing directory.

**Throws `IllegalArgumentException`:** if `folder` is null, does not exist, or is not a directory.

**Throws `PluginLoadingException`:** if an unrecoverable error occurs during loading.

#### loadFromFile

```java
public CompletableFuture<List<Plugin>> loadFromFile(File file)
```

Loads plugins from a single JAR file asynchronously.
Only classes implementing `Plugin` are loaded and instantiated.

- **`file`** — The JAR file to load plugins from. Must be a valid, readable JAR file.

**Returns:** A `CompletableFuture` containing the list of loaded plugins from this JAR file.

**Throws `IllegalArgumentException`:** if `file` is null, does not exist, or is not a JAR file.

#### getLoadedPlugins

```java
public List<Plugin> getLoadedPlugins()
```

Returns an unmodifiable view of the currently loaded plugins.

**Returns:** A thread-safe, unmodifiable list of loaded `Plugin` instances.

#### shutdown

```java
public void shutdown()
```

Shuts down the internal executor service gracefully, waiting up to `SHUTDOWN_TIMEOUT_SECONDS`
seconds for all tasks to complete.

This method should be called when the `PluginLoader` is no longer needed to ensure proper
resource cleanup.

#### removePlugin

```java
public boolean removePlugin(Plugin plugin)
```

Unloads and removes a plugin instance from this loader.

This will invoke `plugin.unload()` and then remove the instance
from the internal `loadedPlugins` list. Any exception thrown by
`unload()` is logged but will not prevent the plugin from being
removed from the list.

- **`plugin`** — the plugin instance to remove (must not be `null`)

**Returns:** `true` if the plugin was present and removed, `false` otherwise

**Throws `NullPointerException`:** if `plugin` is `null`

#### removePlugin

```java
public boolean removePlugin(Class<? extends Plugin> pluginClass)
```

Convenience overload: unloads and removes the first loaded plugin of the given type.

- **`pluginClass`** — the concrete plugin class to remove

**Returns:** `true` if a matching plugin was found and removed, `false` otherwise

**Throws `NullPointerException`:** if `pluginClass` is `null`

</details>

<a id="type-pluginloadingexception"></a>

### PluginLoadingException

[Source](../../src/main/java/valthorne/plugin/PluginLoadingException.java#L11)

Unchecked failure carrying plugin-loading context and its underlying cause.
The loader uses this to preserve the original exception while identifying the
failed loading operation. Constructing the exception does not unload plugins,
retry loading, or perform any resource cleanup.

<details>
<summary>PluginLoadingException operation reference (1 declarations)</summary>

#### Constructor

```java
public PluginLoadingException(String message, Throwable cause)
```

Constructs a new `PluginLoadingException` with the specified message and cause.

- **`message`** — The error message.
- **`cause`** — The underlying cause of the failure.

</details>

## Related guides

- [Events and listeners](events.md)
- [Application lifecycle and window management](runtime.md)
- [Classpath and filesystem utilities](files.md)
