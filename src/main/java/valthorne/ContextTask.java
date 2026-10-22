package valthorne;

/**
 * Queued render-thread work retaining its originating logical presentation.
 * Late work from a closed presentation is ignored without entering a retired scope.
 */
final class ContextTask implements Runnable {
    private final WindowContext context; // Captured live presentation.
    private final Runnable task; // Caller work executed in that presentation.

    /**
     * Captures one scheduled action at the queue boundary.
     *
     * @param context presentation identity
     * @param task scheduled action
     */
    ContextTask(WindowContext context, Runnable task) {
        this.context = context;
        this.task = task;
    }

    @Override
    public void run() {
        if (context.isClosed()) return;
        WindowContext previous = WindowContext.use(context);
        try {
            task.run();
        }
        finally {
            WindowContext.use(previous);
        }
    }
}
