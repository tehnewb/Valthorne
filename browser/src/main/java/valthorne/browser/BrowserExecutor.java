package valthorne.browser;

import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * Runs asset and file work on TeaVM cooperative coroutines. Work can suspend for
 * browser I/O without blocking DOM input. This is not parallel CPU execution:
 * callbacks must remain on the browser UI thread and yield through browser APIs.
 * Shutdown rejects new work and invalidates work not yet started; callers cancel
 * their own futures and active browser requests retain session cancellation.
 */
public final class BrowserExecutor implements Executor {
    private boolean shutdown; // Whether new work and queued starts are rejected.

    /**
     * Creates an active coroutine executor with no native worker resources.
     */
    public BrowserExecutor() {
    }

    @Override
    public void execute(Runnable command) {
        Objects.requireNonNull(command, "command");
        if (shutdown) throw new IllegalStateException("Browser executor has shut down");
        new Thread(() -> {
            if (!shutdown) command.run();
        }).start();
    }

    /**
     * Rejects new work and skips queued coroutines that have not started.
     */
    public void shutdownNow() {
        shutdown = true;
    }

    /**
     * Reports whether this executor rejects new work.
     * @return shutdown state
     */
    public boolean isShutdown() {
        return shutdown;
    }

    /**
     * Reports whether the executor has released its scheduling capability.
     * @return shutdown state; running I/O is cancelled by its owning session
     */
    public boolean isTerminated() {
        return shutdown;
    }
}
