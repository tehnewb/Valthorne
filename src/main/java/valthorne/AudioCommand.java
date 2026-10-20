package valthorne;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

/**
 * One audio-session command and its result. Native work runs only when selected
 * by the owning audio thread. Startup failure and shutdown can complete pending
 * results exceptionally without executing their callables or stranding waiters.
 *
 * @param <T> command result type
 */
final class AudioCommand<T> implements Runnable {
    private final Callable<T> callable; // Borrowed operation executed at most once by its session.
    private final CompletableFuture<T> result = new CompletableFuture<>(); // Outcome observed by asynchronous and blocking callers.

    /**
     * Retains a validated command for later execution on its owning session.
     *
     * @param callable nonnull audio-thread operation
     */
    AudioCommand(Callable<T> callable) {
        this.callable = callable;
    }

    /**
     * Returns the command outcome; cancelling it prevents queued execution.
     * Work already executing on the audio thread may finish before cancellation.
     *
     * @return retained result future
     */
    CompletableFuture<T> result() {
        return result;
    }

    /**
     * Rejects an unexecuted command with a lifecycle failure or shutdown cause.
     *
     * @param failure nonnull terminal cause
     */
    void reject(Throwable failure) {
        result.completeExceptionally(failure);
    }

    @Override
    public void run() {
        if (result.isDone()) return;
        try {
            result.complete(callable.call());
        } catch (Throwable failure) {
            result.completeExceptionally(failure);
        }
    }
}
