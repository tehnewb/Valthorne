package valthorne.asset;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Couples a cached future with its name and optional explicit resource cleanup.
 * Loaded data uses Assets' existing disposal discovery; constructed resources use
 * the supplied type-safe callback. Ownership removal is coordinated by Assets.
 *
 * @param <T> cached resource type
 */
final class AssetEntry<T> {

    private final String key; // Cache name used for removal from the lookup index.
    private final CompletableFuture<T> future; // Shared pending or completed resource.
    private final Consumer<? super T> disposer; // Explicit cleanup, or null for loaded-data discovery.

    /**
     * Creates an entry before publishing it in the cache.
     *
     * @param key validated cache name
     * @param future shared resource future
     * @param disposer explicit cleanup, or null for automatic discovery
     */
    AssetEntry(String key, CompletableFuture<T> future, Consumer<? super T> disposer) {
        this.key = key;
        this.future = future;
        this.disposer = disposer;
    }

    /**
     * Returns the name without allocating a lookup key.
     *
     * @return registered name
     */
    String key() {
        return key;
    }

    /**
     * Returns the shared future used by existing asynchronous APIs.
     *
     * @return resource future
     */
    CompletableFuture<T> future() {
        return future;
    }

    /**
     * Reads a successfully completed value without waiting for loading.
     *
     * @return completed value, or null when absent, pending, cancelled or failed
     */
    T completedValue() {
        return future.isDone() && !future.isCompletedExceptionally() ? future.getNow(null) : null;
    }

    /**
     * Releases a successfully completed resource after cache ownership removal.
     * Explicit callbacks replace disposal discovery rather than supplementing it.
     *
     * @return cleanup failure, or null
     */
    Throwable disposeValue() {
        T value = future.getNow(null);
        if (disposer == null) return Assets.disposeAssetValue(value);
        try {
            disposer.accept(value);
            return null;
        } catch (Throwable failure) {
            return failure;
        }
    }
}
