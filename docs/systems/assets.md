# Asset loading and caching

Author: Albert Beaupre

[System manual](README.md)

## Purpose

`Assets` loads CPU-side asset representations asynchronously and caches futures by an asset key. Parameter objects describe the source and decoding options; registered loaders perform the actual conversion. This allows a loading screen to observe progress without doing all decoding in its render callback.

## Features and when to use them

| Feature | Purpose |
| --- | --- |
| Prepared batches | Queue parameters with `prepare`, then call `load` and observe batch progress. |
| Direct loads | `loadAsync` requests an individual asset and returns a future. |
| Key deduplication | Requests with the same key share cache state; keys must distinguish variants that must coexist. |
| Loader registration | Custom parameter types can be paired with custom loaders. |
| Failure and release | Future completion, cancellation, cache removal, and shutdown have distinct effects. |

## Getting started

1. Choose the appropriate parameter type for a texture, font, sound, map, or model.
2. Assign a stable unique key for each variant, including size or source differences when needed.
3. Prepare a batch or request a typed asynchronous load.
4. After CPU data is available, create GPU objects on the context thread and define who releases both CPU data and runtime resources.

## Ownership and lifecycle

A completed future does not transfer you to the graphics thread. Treat cached data as shared. Removing or disposing shared data while another runtime object still needs it can invalidate that object. Cancellation does not guarantee that a loader stops immediately.

## Important behavior

- Progress counters describe prepared batches; direct loads are a separate workflow.
- Reusing a key for a different source or parameter variant can retrieve the earlier cached result.
- A texture's GPU handle and its decoded pixel buffer have separate ownership contracts.

## Components and examples

The sections below explain each component and its declared public or protected operations. Expand an operation reference for signatures, parameters, results, and edge cases. Inherited operations are documented with their base type. Examples embedded in component descriptions are integration fragments: supply the surrounding resources and application variables they name.

- [`AssetLoader`](#type-assetloader)
- [`AssetParameters`](#type-assetparameters)
- [`Assets`](#type-assets)

<a id="type-assetloader"></a>

### AssetLoader

[Source](../../src/main/java/valthorne/asset/AssetLoader.java#L14)

Converts an asset request into loaded data. `Assets` selects a registered
loader by the request's concrete parameter class and can invoke it on an executor
thread. Implementations used there must respect the thread requirements of their
resources and must not assume a current graphics or audio context.

- **`<P>`** — the type of the parameters used to configure the loading of the asset. This type must extend the AssetParameters interface.
- **`<T>`** — the type of the asset data to be loaded.

<details>
<summary>AssetLoader operation reference (1 declarations)</summary>

#### load

```java
T load(P parameters)
```

Loads asset data using the given parameters. This operation performs the work
synchronously on the calling thread; scheduling and cache reuse belong to
`Assets`. Loading failures may be reported as runtime exceptions, which
complete an asynchronous asset request exceptionally. Concrete implementations
define validation and ownership of the returned data.

- **`parameters`** — the parameters required to configure and execute the asset loading process

**Returns:** the loaded asset data of the specified type

</details>

<a id="type-assetparameters"></a>

### AssetParameters

[Source](../../src/main/java/valthorne/asset/AssetParameters.java#L11)

Describes an asset request and its identity in the shared `Assets` cache.
The concrete parameter class selects the registered loader; `key()` selects
the cached future. Implementations should include every loading option that changes
the result in the key and avoid collisions with other asset types.

<details>
<summary>AssetParameters operation reference (1 declarations)</summary>

#### key

```java
String key()
```

Returns the stable cache identity for this request. Requests with the same key
share an existing load even if their parameter objects differ. The key must be
non-null when used with `Assets`; generating it should not load the asset.

**Returns:** a string representing the unique identifier key for the asset parameters

</details>

<a id="type-assets"></a>

### Assets

[Source](../../src/main/java/valthorne/asset/Assets.java#L87)

Asset manager for Valthorne that supports "prepare then load" and direct async loads with caching.

##### Example

```java
// 1) Register loaders (optional if you rely on the defaults in the static initializer).
Assets.addLoader(TextureParameters.class, new TextureLoader());
Assets.addLoader(SoundParameters.class,   new SoundLoader());
Assets.addLoader(FontParameters.class,    new FontLoader());

// 2) Queue assets you want to load as a batch.
Assets.resetProgress();
Assets.prepare(TextureParameters.fromClasspath("ui/button.png", "btn"));
Assets.prepare(FontParameters.fromClasspath("ui/font.ttf", "font", 18));

// 3) Kick off the batch load.
Assets.load().thenRun(() -> {
    TextureData btn = Assets.get("btn", TextureData.class);
    FontData font = Assets.get("font", FontData.class);
});

// 4) You can also load something immediately (deduped by key).
Assets.loadAsync(TextureParameters.fromClasspath("ui/bg.png", "bg"), TextureData.class)
      .thenAccept(bg -> {  });

// 5) Progress reporting for loading screens.
float p = Assets.getProgress(); // 0..1

// 6) Shutdown at the end of the app if you want to stop background work.
Assets.shutdown();
```

##### What this class does

`Assets` provides a static, thread-safe cache of asynchronously loaded assets keyed by
`AssetParameters#key()`. It supports two primary workflows:

- **Batch loading**: call `prepare(AssetParameters)` many times, then call `load()`
once to load the entire batch and track progress.

- **Direct loading**: call `loadAsync(AssetParameters, Class)` for one-off loads (also cached).

##### Caching and deduplication

Both workflows deduplicate by the parameter key. If the same key is requested multiple times,
callers will share the same `CompletableFuture` instance from `cache`.

##### Threading model

All loading work runs on `service`. Loaders are looked up by the runtime class of the parameter
instance (e.g. `TextureParameters.class`). The cache stores futures so callers can join or compose
without blocking the render thread.

##### Progress tracking

Progress is defined as `completedCount / preparedCount`. It only applies to the batch workflow
(prepared and load). Direct `loadAsync(AssetParameters, Class)` calls do not increment the prepared count.

<details>
<summary>Assets operation reference (12 declarations)</summary>

#### addLoader

```java
public static <P extends AssetParameters, T> void addLoader(Class<P> parametersType, AssetLoader<P, T> loader)
```

Registers an `AssetLoader` for a specific `AssetParameters` type. This method maps
the specified type of asset parameters to its corresponding loader, enabling the loading of
assets defined by the given parameters type.

- **`<P>`** — The type of the asset parameters that the loader can process, extending `AssetParameters`.
- **`<T>`** — The type of the asset to be loaded by the loader.
- **`parametersType`** — The class type of the asset parameters for which the loader is to be registered. Must not be null.
- **`loader`** — The `AssetLoader` instance responsible for loading assets of the specified parameters type. Must not be null.

**Throws `NullPointerException`:** If either `parametersType` or `loader` is null.

#### resetProgress

```java
public static void resetProgress()
```

Resets the progress tracking for asset loading operations.
This method sets both the number of prepared assets and the number of completed assets to zero.
It is useful for initializing or restarting the loading progress tracking.

#### getProgress

```java
public static float getProgress()
```

Calculates and returns the progress of some operation as a floating-point value
between 0 and 1, inclusive. The progress is determined based on the ratio of
completed tasks to the total number of prepared tasks. If no tasks have been
prepared, the method returns 1.0, indicating completion by default.

**Returns:** the progress as a floating-point value between 0 and 1.

#### loadAsync

```java
@SuppressWarnings({"unchecked", "rawtypes"})
    public static <P extends AssetParameters, T> CompletableFuture<T> loadAsync(P parameters, Class<T> assetType)
```

Asynchronously loads an asset of the specified type using the provided asset parameters.
This method first checks if the requested asset is already being loaded or cached. If so,
it returns the cached future. Otherwise, it initiates a new asynchronous load operation.

- **`<P>`** — The type of the asset parameters, which must extend `AssetParameters`.
- **`<T>`** — The type of the asset to be loaded.
- **`parameters`** — The asset parameters that define the asset to be loaded. Must not be null.
- **`assetType`** — The class type of the asset to be loaded. Must not be null.

**Returns:** A `CompletableFuture` representing the asynchronous loading process of the asset.

**Throws `NullPointerException`:** If either `parameters` or `assetType` is null.

**Throws `IllegalStateException`:** If no `AssetLoader` is registered for the given asset parameters type.

#### get

```java
public static <T> T get(String key, Class<T> type)
```

Retrieves a cached value associated with the given key and casts it to the specified type.

- **`<T>`** — The type of the object to be returned.
- **`key`** — The key associated with the cached value. Must not be null.
- **`type`** — The class type to which the cached value should be cast. Must not be null.

**Returns:** The cached value cast to the specified type, or null if no value is associated with the key.

**Throws `ClassCastException`:** If the cached value cannot be cast to the specified type.

#### remove

```java
public static boolean remove(String key)
```

Removes the cached value associated with the specified key.
If the key exists in the cache, its associated value is removed.

- **`key`** — The key of the cached value to be removed. Must not be null.

**Returns:** `true` if a value was removed from the cache, `false` if no value was associated with the key.

#### unload

```java
public static boolean unload(String key)
```

Removes the cached value associated with the specified key and disposes it when possible.

If the cached future is still in flight it is cancelled and removed. If it has already
completed successfully, the loaded asset is asked to dispose itself before the cache
entry is forgotten.

- **`key`** — The key of the cached value to unload. Must not be null.

**Returns:** `true` if a cached entry existed and was removed.

#### clear

```java
public static int clear()
```

Clears all cached assets and prepared batch entries.

Loaded values are disposed when possible, incomplete futures are cancelled, and
progress counters are reset so a new batch can start cleanly.

**Returns:** the number of cached entries removed

#### shutdown

```java
public static void shutdown()
```

Shuts down the internal service responsible for managing asset-related operations.
This method ensures that any ongoing asset management tasks are stopped and the
associated resources are cleaned up. After invoking this method, the asset manager
should no longer be used.

It is typically called during the disposal or termination process of an application
or context that relies on the assets, to safely release resources.

#### isFinished

```java
public static boolean isFinished()
```

Determines whether all prepared assets have been processed and no assets remain
in the preparation queue.

**Returns:** `true` if the prepared queue is empty and the number of completed
assets matches the number of prepared assets; `false` otherwise.

#### prepare

```java
public static void prepare(AssetParameters params)
```

Prepares the specified asset parameters for loading by marking them as prepared.
This method ensures that the given parameters are only prepared once.
If the parameters are already prepared, the method returns without action.

- **`params`** — The asset parameters to prepare. Must not be null.

**Throws `NullPointerException`:** If `params` is null.

#### load

```java
@SuppressWarnings({"unchecked", "rawtypes"})
    public static CompletableFuture<Void> load()
```

Initiates the loading of all prepared assets asynchronously. This method processes the
`prepared` queue by delegating the loading of each asset to its corresponding
`AssetLoader`, based on the asset's `AssetParameters`. The progress is tracked
using a `ConcurrentLinkedQueue` of `CompletableFuture` instances.

Each asset's loading result is stored in the `cache` using its unique key, and
the `completedCount` is incremented as each asset finishes loading. Additionally,
an aggregated `CompletableFuture` is created to represent the collective completion
of all the individual asset loading tasks.

If no loader is registered for a particular asset type, an `IllegalStateException`
is thrown.

Optional completion actions can be performed after the entire batch of assets finishes loading.

This method is typically called after preparing assets using the `prepare(AssetParameters)`
method. It ensures that all prepared assets are processed in an asynchronous and thread-safe manner.

**Throws `IllegalStateException`:** If no `AssetLoader` is registered for the given asset parameters type.

</details>

## Related guides

- [Textures, sprites, atlases, and batching](textures.md)
- [Bitmap fonts and glyph styling](fonts.md)
- [Audio playback and ambient areas](audio.md)
- [Tiled maps and tilesets](tiled-maps.md)
- [3D models, materials, scenes, and billboards](models.md)
