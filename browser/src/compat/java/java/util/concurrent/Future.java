package java.util.concurrent;

/**
 * Cooperative future waits and cancellation used by the browser completion API.
 * Waiting yields the current Java coroutine instead of blocking the browser.
 * @param <V> successful result type
 */
public interface Future<V> {
    /**
     * Publishes cancellation; cooperative tasks already executing are not interrupted.
     * @param mayInterruptIfRunning retained Future parameter; running actions are not interrupted
     * @return completion operation result
     */
    boolean cancel(boolean mayInterruptIfRunning);

    /**
     * Reports whether this future itself was cancelled.
     * @return completion operation result
     */
    boolean isCancelled();

    /**
     * Reports whether any terminal outcome has been published.
     * @return completion operation result
     */
    boolean isDone();

    /**
     * Suspends the calling coroutine until completion, interruption, or the optional deadline.
     * @return completion operation result
     */
    V get() throws InterruptedException, ExecutionException;

    /**
     * Suspends the calling coroutine until completion, interruption, or the optional deadline.
     * @param timeout deadline duration in the supplied unit
     * @param unit nonnull duration unit
     * @return completion operation result
     */
    V get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException;
}
