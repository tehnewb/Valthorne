package valthorne.ui.behavior;

/**
 * Identity-based, idempotent removal action for one UI change listener.
 * The listener stays callable from an already captured dispatch snapshot after
 * removal. This handle retains its callback and owner until callers release it.
 * All access is confined to the UI thread.
 */
final class ChangeSubscription implements Runnable {
    final Runnable callback; // Listener retained for snapshot dispatch.
    private final ChangeSignal owner; // Signal containing this registration.
    private boolean removed; // Whether removal has already been requested.

    /**
     * Retains one validated callback and its registration owner.
     *
     * @param owner signal publishing the registration
     * @param callback nonnull listener action
     */
    ChangeSubscription(ChangeSignal owner, Runnable callback) {
        this.owner = owner;
        this.callback = callback;
    }

    @Override
    public void run() {
        if (removed) return;
        removed = true;
        owner.remove(this);
    }
}
