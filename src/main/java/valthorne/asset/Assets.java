package valthorne.asset;

import valthorne.audio.SoundLoader;
import valthorne.audio.SoundParameters;
import valthorne.graphics.animation.AnimationLoader;
import valthorne.graphics.animation.AnimationParameters;
import valthorne.graphics.font.FontLoader;
import valthorne.graphics.font.adaptive.AdaptiveFontLoader;
import valthorne.graphics.font.adaptive.AdaptiveFontParameters;
import valthorne.graphics.font.FontParameters;
import valthorne.graphics.map.ldtk.LdtkLoader;
import valthorne.graphics.map.ldtk.LdtkMap;
import valthorne.graphics.map.ldtk.LdtkParameters;
import valthorne.graphics.map.ldtk.LdtkProject;
import valthorne.graphics.map.tiled.TiledMapLoader;
import valthorne.graphics.map.tiled.TiledMapParameters;
import valthorne.graphics.particle.ParticleLoader;
import valthorne.graphics.particle.ParticleParameters;
import valthorne.graphics.texture.TextureLoader;
import valthorne.graphics.texture.TextureParameters;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Asset manager for Valthorne that supports "prepare then load" and direct async loads with caching.
 *
 * <h2>Example</h2>
 * <pre>{@code
 * // 1) Register loaders (optional if you rely on the defaults in the static initializer).
 * Assets.addLoader(TextureParameters.class, new TextureLoader());
 * Assets.addLoader(SoundParameters.class,   new SoundLoader());
 * Assets.addLoader(FontParameters.class,    new FontLoader());
 *
 * // 2) Queue assets you want to load as a batch.
 * Assets.resetProgress();
 * Assets.prepare(TextureParameters.fromClasspath("ui/button.png", "btn"));
 * Assets.prepare(FontParameters.fromClasspath("ui/font.ttf", "font", 18));
 *
 * // 3) Kick off the batch load.
 * Assets.load().thenRun(() -> {
 *     TextureData btn = Assets.get("btn", TextureData.class);
 *     FontData font = Assets.get("font", FontData.class);
 * });
 *
 * // 4) You can also load something immediately (deduped by key).
 * Assets.loadAsync(TextureParameters.fromClasspath("ui/bg.png", "bg"), TextureData.class)
 *       .thenAccept(bg -> {  });
 *
 * // 5) Progress reporting for loading screens.
 * float p = Assets.getProgress(); // 0..1
 *
 * // 6) Shutdown at the end of the app if you want to stop background work.
 * Assets.shutdown();
 * }</pre>
 *
 * <h2>What this class does</h2>
 * <p>
 * {@code Assets} provides a static, thread-safe cache of asynchronously loaded assets keyed by
 * {@link AssetParameters#key()}. It supports two primary workflows:
 * </p>
 * <ul>
 *     <li><b>Batch loading</b>: call {@link #prepare(AssetParameters)} many times, then call {@link #load()}
 *     once to load the entire batch and track progress.</li>
 *     <li><b>Direct loading</b>: call {@link #loadAsync(AssetParameters, Class)} for one-off loads (also cached).</li>
 * </ul>
 *
 * <h2>Caching and deduplication</h2>
 * <p>
 * Both workflows deduplicate by the parameter key. If the same key is requested multiple times,
 * callers will share the same {@link CompletableFuture} instance from {@link #cache}.
 * Already constructed resources can be registered with {@link #put(String, Object, Consumer)}:
 * </p>
 * <pre>{@code
 * // Run on the graphics thread after loading the CPU data.
 * Assets.put("logo", Assets.get("logo-data", TextureData.class).asTexture(), Texture::dispose);
 * Image logo = new Image(Assets.get("logo", Texture.class));
 * // After all consumers stop, while the graphics context remains valid:
 * Assets.clear();
 * }</pre>
 * <p>Loaded data and runtime resources share one key namespace, so use separate
 * names for both. Registered resources are owned by Assets; retrieved references
 * are borrowed. Do not register resources already owned by another resource.
 * Clear releases entries in reverse registration order, not completion order.
 * </p>
 *
 * <h2>Threading model</h2>
 * <p>
 * All loading work runs on {@link #service}. Loaders are looked up by the runtime class of the parameter
 * instance (e.g. {@code TextureParameters.class}). The cache stores futures so callers can join or compose
 * without blocking the render thread.
 * Cache registration/removal is coordinated briefly; retrieval does not acquire
 * that lock. Cleanup callbacks execute on the caller of unload/clear, so satisfy
 * each resource's thread/context requirements. Coordinate cleanup with consumers
 * and do not register resources from cleanup callbacks. Shutdown stops workers;
 * it does not replace clear for releasing completed resources.
 * </p>
 *
 * <h2>Progress tracking</h2>
 * <p>
 * Progress is defined as {@code completedCount / preparedCount}. It only applies to the batch workflow
 * (prepared and load). Direct {@link #loadAsync(AssetParameters, Class)} calls do not increment the prepared count.
 * </p>
 *
 * @author Albert Beaupre
 * @since December 7th, 2025
 */
public final class Assets {

    /**
     * Lock protecting recreation and replacement of the shared loader executor.
     */
    private static final Object SERVICE_LOCK = new Object();
    /**
     * Coordinates cache registration with ownership order and removal.
     */
    private static final Object CACHE_LOCK = new Object();
    /**
     * Concurrent registry mapping parameter classes to asset loaders.
     */
    private static final ConcurrentMap<Class<?>, AssetLoader<?, ?>> loaders = new ConcurrentHashMap<>();
    /**
     * Concurrent cache of load futures indexed by asset key.
     */
    private static final ConcurrentMap<String, AssetEntry<?>> cache = new ConcurrentHashMap<>();
    /**
     * Registration order used only for ownership mutation and reverse cleanup.
     */
    private static final ArrayList<AssetEntry<?>> registrationOrder = new ArrayList<>();
    /**
     * Atomic completed-load progress counter.
     */
    private static final AtomicInteger completedCount = new AtomicInteger(0);
    /**
     * Concurrent set of assets queued for prepared loading.
     */
    private static final Set<AssetParameters> prepared = ConcurrentHashMap.newKeySet();
    /**
     * Atomic count used as the prepared-loading progress denominator.
     */
    private static final AtomicInteger preparedCount = new AtomicInteger(0);
    /**
     * Volatile owned virtual-thread executor, recreated after shutdown when needed.
     */
    private static volatile ExecutorService service = createExecutorService();

    static {
        addLoader(SoundParameters.class, new SoundLoader());
        addLoader(TextureParameters.class, new TextureLoader());
        addLoader(AnimationParameters.class, new AnimationLoader());
        addLoader(FontParameters.class, new FontLoader());
        addLoader(AdaptiveFontParameters.class, new AdaptiveFontLoader());
        addLoader(TiledMapParameters.class, new TiledMapLoader());
        addLoader(LdtkParameters.class, new LdtkLoader());
        addLoader(ParticleParameters.class, new ParticleLoader());
    }

    /**
     * Prevents construction of the shared asset loading and caching service.
     */
    private Assets() {
    }

    /**
     * Registers an {@link AssetLoader} for a specific {@link AssetParameters} type. This method maps
     * the specified type of asset parameters to its corresponding loader, enabling the loading of
     * assets defined by the given parameters type.
     *
     * @param <P>            The type of the asset parameters that the loader can process, extending {@link AssetParameters}.
     * @param <T>            The type of the asset to be loaded by the loader.
     * @param parametersType The class type of the asset parameters for which the loader is to be registered.
     *                       Must not be null.
     * @param loader         The {@link AssetLoader} instance responsible for loading assets of the specified
     *                       parameters type. Must not be null.
     * @throws NullPointerException If either {@code parametersType} or {@code loader} is null.
     */
    public static <P extends AssetParameters, T> void addLoader(Class<P> parametersType, AssetLoader<P, T> loader) {
        Objects.requireNonNull(parametersType, "");
        Objects.requireNonNull(loader);

        loaders.put(parametersType, loader);
    }

    /**
     * Resets the progress tracking for asset loading operations.
     * This method sets both the number of prepared assets and the number of completed assets to zero.
     * It is useful for initializing or restarting the loading progress tracking.
     */
    public static void resetProgress() {
        preparedCount.set(0);
        completedCount.set(0);
    }

    /**
     * Calculates and returns the progress of some operation as a floating-point value
     * between 0 and 1, inclusive. The progress is determined based on the ratio of
     * completed tasks to the total number of prepared tasks. If no tasks have been
     * prepared, the method returns 1.0, indicating completion by default.
     *
     * @return the progress as a floating-point value between 0 and 1.
     */
    public static float getProgress() {
        int total = preparedCount.get();
        return total == 0 ? 1f : completedCount.get() / (float) total;
    }

    /**
     * Asynchronously loads an asset of the specified type using the provided asset parameters.
     * This method first checks if the requested asset is already being loaded or cached. If so,
     * it returns the cached future. Otherwise, it initiates a new asynchronous load operation.
     *
     * @param <P>        The type of the asset parameters, which must extend {@link AssetParameters}.
     * @param <T>        The type of the asset to be loaded.
     * @param parameters The asset parameters that define the asset to be loaded. Must not be null.
     * @param assetType  The class type of the asset to be loaded. Must not be null.
     * @return A {@link CompletableFuture} representing the asynchronous loading process of the asset.
     * @throws NullPointerException  If either {@code parameters} or {@code assetType} is null.
     * @throws IllegalStateException If no {@link AssetLoader} is registered for the given asset parameters type.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <P extends AssetParameters, T> CompletableFuture<T> loadAsync(P parameters, Class<T> assetType) {
        Objects.requireNonNull(parameters);
        Objects.requireNonNull(assetType);

        final String key = parameters.key();

        AssetEntry<T> entry;
        synchronized (CACHE_LOCK) {
            AssetEntry<?> existing = cache.get(key);
            if (existing != null) return (CompletableFuture<T>) existing.future();
            entry = new AssetEntry<>(key, new CompletableFuture<>(), null);
            register(entry);
        }
        try {
            ensureService().execute(() -> {
                if (entry.future().isDone()) return;
                T value;
                try {
                    AssetLoader loader = loaders.get(parameters.getClass());
                    if (loader == null)
                        throw new IllegalStateException("No loader for " + parameters.getClass().getName());
                    value = assetType.cast(loader.load(parameters));
                } catch (Throwable failure) {
                    entry.future().completeExceptionally(failure);
                    return;
                }
                if (!entry.future().complete(value)) {
                    Throwable failure = disposeAssetValue(value);
                    if (failure != null) rethrowUnchecked(failure);
                }
            });
        } catch (RuntimeException failure) {
            entry.future().completeExceptionally(failure);
        }
        return entry.future();
    }

    /**
     * Registers an already constructed resource for shared retrieval and cleanup.
     * Ownership transfers only on success. Duplicate keys, including pending loads,
     * and aliases of currently cached completed values are rejected. A rejected
     * value remains caller-owned. Construct GPU resources on their owner thread;
     * this method performs no loading or thread dispatch.
     *
     * @param key unique nonblank name, distinct from the source data's key
     * @param value non-null resource to own
     * @param disposer cleanup callback used instead of automatic disposal discovery
     * @param <T> resource type
     * @return registered resource
     * @throws NullPointerException if an argument is null
     * @throws IllegalArgumentException if the key is blank, already registered, or the value is already cached
     */
    public static <T> T put(String key, T value, Consumer<? super T> disposer) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(disposer, "disposer");
        if (key.isBlank()) throw new IllegalArgumentException("Asset key must not be blank");
        synchronized (CACHE_LOCK) {
            if (cache.containsKey(key)) throw new IllegalArgumentException("Asset key already registered: " + key);
            for (int i = 0; i < registrationOrder.size(); i++) {
                if (registrationOrder.get(i).completedValue() == value)
                    throw new IllegalArgumentException("Resource is already cached");
            }
            register(new AssetEntry<>(key, CompletableFuture.completedFuture(value), disposer));
        }
        return value;
    }

    /**
     * Adds an entry to both ownership indexes while holding the cache lock.
     * Rolls back registration if publication fails, retaining caller ownership.
     *
     * @param entry new unique entry
     */
    private static void register(AssetEntry<?> entry) {
        registrationOrder.add(entry);
        try {
            cache.put(entry.key(), entry);
        } catch (RuntimeException | Error failure) {
            registrationOrder.remove(registrationOrder.size() - 1);
            cache.remove(entry.key(), entry);
            throw failure;
        }
    }

    /**
     * Retrieves a cached value associated with the given key and casts it to the specified type.
     *
     * @param <T>  The type of the object to be returned.
     * @param key  The key associated with the cached value. Must not be null.
     * @param type The class type to which the cached value should be cast. Must not be null.
     * @return The cached value cast to the specified type, or null if no value is associated with the key.
     * @throws ClassCastException If the cached value cannot be cast to the specified type.
     */
    public static <T> T get(String key, Class<T> type) {
        AssetEntry<?> entry = cache.get(key);
        if (entry == null)
            return null;
        return type.cast(entry.future().join());
    }

    /**
     * Removes the cached value associated with the specified key.
     * If the key exists in the cache, its associated value is removed.
     *
     * @param key The key of the cached value to be removed. Must not be null.
     * @return {@code true} if a value was removed from the cache, {@code false} if no value was associated with the key.
     */
    public static boolean remove(String key) {
        return unload(key);
    }

    /**
     * Removes the cached value associated with the specified key and disposes it when possible.
     *
     * <p>
     * If the cached future is still in flight it is cancelled and removed. If it has already
     * completed successfully, the loaded asset is asked to dispose itself before the cache
     * entry is forgotten. Explicitly registered resources use their supplied
     * callback. A cancelled loader that subsequently produces data releases that
     * data on its worker thread. Call on the resource owner thread when required.
     * </p>
     *
     * @param key The key of the cached value to unload. Must not be null.
     * @return {@code true} if a cached entry existed and was removed.
     */
    public static boolean unload(String key) {
        Objects.requireNonNull(key, "key");

        AssetEntry<?> entry;
        synchronized (CACHE_LOCK) {
            entry = cache.remove(key);
            if (entry == null) return false;
            registrationOrder.remove(entry);
        }
        Throwable failure = releaseEntry(entry);
        if (failure != null) {
            rethrowUnchecked(failure);
        }

        return true;
    }

    /**
     * Clears all cached assets and prepared batch entries.
     *
     * <p>
     * Entries are released in reverse registration order, using explicit callbacks
     * for constructed resources and automatic disposal for loaded data. Incomplete
     * futures are cancelled; a late loader result is released on its worker thread.
     * Every entry is attempted even when cleanup fails; subsequent failures are
     * suppressed on the first. Progress counters reset for a new batch. Coordinate
     * this call with consumers and satisfy all resource thread/context requirements.
     * </p>
     *
     * @return the number of cached entries removed
     */
    public static int clear() {
        ArrayList<AssetEntry<?>> entries;
        synchronized (CACHE_LOCK) {
            entries = new ArrayList<>(registrationOrder);
            cache.clear();
            registrationOrder.clear();
            prepared.clear();
            resetProgress();
        }

        Throwable failure = null;
        for (int i = entries.size() - 1; i >= 0; i--) {
            failure = appendSuppressed(failure, releaseEntry(entries.get(i)));
        }

        if (failure != null) {
            rethrowUnchecked(failure);
        }

        return entries.size();
    }

    /**
     * Shuts down the internal service responsible for managing asset-related operations.
     * Requests interruption of worker tasks and cancels pending cached loads.
     * Completed data and explicitly registered resources remain cached; call
     * {@link #clear()} on the appropriate owner thread to release them. A later
     * loading request recreates the worker executor.
     * <p>
     * It is typically called during the disposal or termination process of an application
     * or context that relies on the assets, to safely release resources.
     */
    public static void shutdown() {
        ExecutorService executor = service;
        if (executor != null) {
            executor.shutdownNow();
        }

        ArrayList<AssetEntry<?>> entries;
        synchronized (CACHE_LOCK) {
            entries = new ArrayList<>(registrationOrder);
        }
        for (AssetEntry<?> entry : entries) {
            if (cancelIncomplete(entry.future())) {
                synchronized (CACHE_LOCK) {
                    if (cache.remove(entry.key(), entry)) registrationOrder.remove(entry);
                }
            }
        }
    }

    /**
     * Determines whether all prepared assets have been processed and no assets remain
     * in the preparation queue.
     *
     * @return {@code true} if the prepared queue is empty and the number of completed
     * assets matches the number of prepared assets; {@code false} otherwise.
     */
    public static boolean isFinished() {
        return prepared.isEmpty() && completedCount.get() == preparedCount.get();
    }

    /**
     * Prepares the specified asset parameters for loading by marking them as prepared.
     * This method ensures that the given parameters are only prepared once.
     * If the parameters are already prepared, the method returns without action.
     *
     * @param params The asset parameters to prepare. Must not be null.
     * @throws NullPointerException If {@code params} is null.
     */
    public static void prepare(AssetParameters params) {
        Objects.requireNonNull(params);

        if (prepared.add(params))
            preparedCount.incrementAndGet();
    }

    /**
     * Initiates the loading of all prepared assets asynchronously. This method processes the
     * {@code prepared} queue by delegating the loading of each asset to its corresponding
     * {@link AssetLoader}, based on the asset's {@link AssetParameters}. The progress is tracked
     * using a {@link ConcurrentLinkedQueue} of {@link CompletableFuture} instances.
     * <p>
     * Each asset's loading result is stored in the {@code cache} using its unique key, and
     * the {@code completedCount} is incremented as each asset finishes loading. Additionally,
     * an aggregated {@link CompletableFuture} is created to represent the collective completion
     * of all the individual asset loading tasks.
     * <p>
     * If no loader is registered for a particular asset type, an {@link IllegalStateException}
     * is thrown.
     * <p>
     * Optional completion actions can be performed after the entire batch of assets finishes loading.
     * Completion does not request process-wide garbage collection; heap collection policy belongs
     * to the application and JVM. Native resource disposal remains explicit.
     * <p>
     * This method is typically called after preparing assets using the {@link #prepare(AssetParameters)}
     * method. It ensures that all prepared assets are processed in an asynchronous and thread-safe manner.
     *
     * @throws IllegalStateException If no {@link AssetLoader} is registered for the given asset parameters type.
     */
    public static CompletableFuture<Void> load() {
        ConcurrentLinkedQueue<CompletableFuture<?>> futures = new ConcurrentLinkedQueue<>();

        for (AssetParameters parameters : prepared) {
            if (!prepared.remove(parameters))
                continue;

            CompletableFuture<?> future = loadAsync(parameters, Object.class)
                    .whenComplete((_, ex) -> {
                        if (ex != null) {
                            ex.printStackTrace();
                            return;
                        }

                        completedCount.incrementAndGet();
                    });

            futures.add(future);
        }

        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
    }

    /**
     * Creates a virtual-thread-per-task executor for asynchronous asset loading.
     * The asset service owns its shutdown.
     *
     * @return new executor
     */
    private static ExecutorService createExecutorService() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    /**
     * Returns an active loader executor, recreating it under the service lock when
     * absent, shut down, or terminated. Uses a volatile fast path for normal access.
     *
     * @return current usable executor
     */
    private static ExecutorService ensureService() {
        ExecutorService executor = service;
        if (executor != null && !executor.isShutdown() && !executor.isTerminated()) {
            return executor;
        }

        synchronized (SERVICE_LOCK) {
            executor = service;
            if (executor == null || executor.isShutdown() || executor.isTerminated()) {
                service = executor = createExecutorService();
            }
            return executor;
        }
    }

    /**
     * Attempts cancellation of a nonnull unfinished future. CompletableFuture's
     * cancellation request does not guarantee interruption of its producing work.
     *
     * @param future candidate future
     * @return true if this call successfully cancels an incomplete future
     */
    private static boolean cancelIncomplete(CompletableFuture<?> future) {
        return future != null && !future.isDone() && future.cancel(true);
    }

    /**
     * Cancels unfinished work or releases the value of a normally completed future.
     * Cancelled and exceptional futures have no value disposed here. Does not wait
     * for outstanding work to finish.
     *
     * @param entry cached ownership entry
     * @return disposal failure, or null
     */
    private static Throwable releaseEntry(AssetEntry<?> entry) {
        CompletableFuture<?> future = entry.future();
        if (cancelIncomplete(future) || future.isCancelled() || future.isCompletedExceptionally()) {
            return null;
        }

        return entry.disposeValue();
    }

    /**
     * Releases a loaded value using AutoCloseable first, otherwise a public no-argument
     * dispose method discovered by reflection. Values with neither contract are ignored.
     * Captures failures and unwraps reflective invocation causes so other assets can
     * still be released. Caller must satisfy resource-specific thread/context rules.
     *
     * @param asset loaded value, possibly null
     * @return disposal failure, or null
     */
    static Throwable disposeAssetValue(Object asset) {
        if (asset == null) {
            return null;
        }

        if (asset instanceof AutoCloseable closeable) {
            try {
                closeable.close();
                return null;
            } catch (Throwable throwable) {
                return throwable;
            }
        }

        try {
            Method dispose = asset.getClass().getMethod("dispose");
            dispose.invoke(asset);
            return null;
        } catch (NoSuchMethodException ignored) {
            return null;
        } catch (InvocationTargetException e) {
            return e.getCause() != null ? e.getCause() : e;
        } catch (Throwable throwable) {
            return throwable;
        }
    }

    /**
     * Keeps the first cleanup failure and attaches a later nonnull failure as
     * suppressed. Callers must avoid passing the same throwable as both arguments.
     *
     * @param primary earlier failure, possibly null
     * @param next later failure, possibly null
     * @return first available failure
     */
    private static Throwable appendSuppressed(Throwable primary, Throwable next) {
        if (next == null) {
            return primary;
        }
        if (primary == null) {
            return next;
        }
        if (primary != next) primary.addSuppressed(next);
        return primary;
    }

    /**
     * Rethrows runtime exceptions and errors unchanged, wrapping other throwable
     * types so cleanup can report them without a checked throws declaration.
     *
     * @param throwable nonnull failure to propagate
     */
    private static void rethrowUnchecked(Throwable throwable) {
        if (throwable instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        if (throwable instanceof Error error) {
            throw error;
        }
        throw new RuntimeException(throwable);
    }
}
