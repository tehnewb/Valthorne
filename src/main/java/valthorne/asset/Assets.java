package valthorne.asset;

import valthorne.graphics.font.FontLoader;
import valthorne.graphics.font.FontParameters;
import valthorne.graphics.texture.TextureLoader;
import valthorne.graphics.texture.TextureParameters;
import valthorne.sound.SoundLoader;
import valthorne.sound.SoundParameters;

import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The Assets class is a utility class for managing the asynchronous loading and caching
 * of assets used within an application. It provides mechanisms to add asset loaders, load assets
 * asynchronously, prepare assets for loading, and retrieve loaded assets.
 * <p>
 * This class is designed to ensure efficient and thread-safe asset management using a combination
 * of concurrent utilities, such as ExecutorService and ConcurrentHashMap. It supports typical
 * asset lifecycle operations like preparation, asynchronous loading, caching, and progress tracking.
 * <p>
 * Key Features:
 * - Registering custom asset loaders
 * - Preparing assets to be loaded
 * - Asynchronous asset loading with automatic caching
 * - Accessing and removing assets from the cache
 * - Tracking loading progress and load completion state
 * - Safe shutdown of the ExecutorService used for loading operations
 * <p>
 * Thread safety is ensured for all static methods in this class.
 *
 * @author Albert Beaupre
 * @since December 7th, 2025
 */
public final class Assets {

    private static final ExecutorService service = Executors.newVirtualThreadPerTaskExecutor();
    private static final ConcurrentMap<Class<?>, AssetLoader<?, ?>> loaders = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, CompletableFuture<?>> cache = new ConcurrentHashMap<>();
    private static final AtomicInteger completedCount = new AtomicInteger(0);
    private static final BlockingDeque<AssetParameters> prepared = new LinkedBlockingDeque<>();
    private static final AtomicInteger preparedCount = new AtomicInteger(0);

    static {
        addLoader(SoundParameters.class, new SoundLoader());
        addLoader(TextureParameters.class, new TextureLoader());
        addLoader(FontParameters.class, new FontLoader());
    }

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

        // Use already cached future if present
        return (CompletableFuture<T>) cache.computeIfAbsent(key, k ->
                CompletableFuture.supplyAsync(() -> {
                    AssetLoader loader = loaders.get(parameters.getClass());
                    if (loader == null)
                        throw new IllegalStateException("No loader for " + parameters.getClass().getName());

                    return assetType.cast(loader.load(parameters));
                }, service)
        );
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
        CompletableFuture<?> f = cache.get(key);
        if (f == null)
            return null;
        return type.cast(f.join());
    }

    /**
     * Removes the cached value associated with the specified key.
     * If the key exists in the cache, its associated value is removed.
     *
     * @param key The key of the cached value to be removed. Must not be null.
     * @return {@code true} if a value was removed from the cache, {@code false} if no value was associated with the key.
     */
    public static boolean remove(String key) {
        return cache.remove(key) != null;
    }

    /**
     * Shuts down the internal service responsible for managing asset-related operations.
     * This method ensures that any ongoing asset management tasks are stopped and the
     * associated resources are cleaned up. After invoking this method, the asset manager
     * should no longer be used.
     * <p>
     * It is typically called during the disposal or termination process of an application
     * or context that relies on the assets, to safely release resources.
     */
    public static void shutdown() {
        service.shutdown();
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
        if (prepared.contains(params)) return;

        prepared.add(params);
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
     * <p>
     * This method is typically called after preparing assets using the {@link #prepare(AssetParameters)}
     * method. It ensures that all prepared assets are processed in an asynchronous and thread-safe manner.
     *
     * @throws IllegalStateException If no {@link AssetLoader} is registered for the given asset parameters type.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static CompletableFuture<Void> load() {
        ConcurrentLinkedQueue<CompletableFuture<?>> futures = new ConcurrentLinkedQueue<>();

        while (!prepared.isEmpty()) {
            final AssetParameters parameters = prepared.poll();
            final String key = parameters.key();

            CompletableFuture<?> future = CompletableFuture
                    .supplyAsync(() -> {
                        AssetLoader loader = loaders.get(parameters.getClass());
                        if (loader == null)
                            throw new IllegalStateException("No loader registered for " + parameters.getClass().getName());

                        return loader.load(parameters);
                    }, service)
                    .whenComplete((_, ex) -> {
                        if (ex != null) { ex.printStackTrace(); return; }
                        completedCount.incrementAndGet();
                    });

            cache.put(key, future);
            futures.add(future);
        }
        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).whenComplete((_, _) -> System.gc());
    }

}
