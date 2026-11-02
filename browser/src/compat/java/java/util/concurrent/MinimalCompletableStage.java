package java.util.concurrent;

import java.util.function.Supplier;

/**
 * Prevents independent mutation of a stage returned by minimalCompletionStage. Source completion is
 * copied through the public completion observer, and callers requesting a full future receive an
 * independent mutable copy.
 *
 * @param <V> completed value type
 */
final class MinimalCompletableStage<V> extends CompletableFuture<V> {
    /**
     * Observes the source without exposing its mutation methods.
     *
     * @param source original future
     */
    MinimalCompletableStage(CompletableFuture<V> source) {
        source.whenComplete(
                (value, error) -> {
                    if (error == null) super.complete(value);
                    else
                        super.completeExceptionally(
                                error instanceof CompletionException
                                        ? error
                                        : new CompletionException(error));
                });
    }

    @Override
    public boolean complete(V value) {
        throw new UnsupportedOperationException("Read-only completion stage");
    }

    @Override
    public boolean completeExceptionally(Throwable failure) {
        throw new UnsupportedOperationException("Read-only completion stage");
    }

    @Override
    public boolean cancel(boolean interrupt) {
        throw new UnsupportedOperationException("Read-only completion stage");
    }

    @Override
    public void obtrudeValue(V value) {
        throw new UnsupportedOperationException("Read-only completion stage");
    }

    @Override
    public void obtrudeException(Throwable failure) {
        throw new UnsupportedOperationException("Read-only completion stage");
    }

    @Override
    public boolean isDone() { throw new UnsupportedOperationException("Read-only completion stage"); }

    @Override
    public boolean isCancelled() { throw new UnsupportedOperationException("Read-only completion stage"); }

    @Override
    public boolean isCompletedExceptionally() { throw new UnsupportedOperationException("Read-only completion stage"); }

    @Override
    public V get() { throw new UnsupportedOperationException("Read-only completion stage"); }

    @Override
    public V get(long timeout, TimeUnit unit) { throw new UnsupportedOperationException("Read-only completion stage"); }

    @Override
    public V getNow(V fallback) { throw new UnsupportedOperationException("Read-only completion stage"); }

    @Override
    public V join() { throw new UnsupportedOperationException("Read-only completion stage"); }

    @Override
    public int getNumberOfDependents() { throw new UnsupportedOperationException("Read-only completion stage"); }

    @Override
    public CompletableFuture<V> completeAsync(Supplier<? extends V> supplier) { throw new UnsupportedOperationException("Read-only completion stage"); }

    @Override
    public CompletableFuture<V> completeAsync(Supplier<? extends V> supplier, Executor executor) { throw new UnsupportedOperationException("Read-only completion stage"); }

    @Override
    public CompletableFuture<V> orTimeout(long timeout, TimeUnit unit) { throw new UnsupportedOperationException("Read-only completion stage"); }

    @Override
    public CompletableFuture<V> completeOnTimeout(V value, long timeout, TimeUnit unit) { throw new UnsupportedOperationException("Read-only completion stage"); }

    @Override
    public CompletableFuture<V> toCompletableFuture() {
        return copy();
    }
}
