package valthorne.ui;

/**
 * Owns one pending restoration action for a translation or clipping scope.
 * Use with try/finally inside the root draw and restore nested scopes in
 * reverse order. Restoration is idempotent, but the scope does not validate nesting
 * order or extend the lifetime of the batch it restores.
 *
 * <p>The restoration reference is cleared before invocation, so an action that
 * throws is not retried on another restoration. Instances are not synchronized.</p>
 *
 * @author Albert Beaupre
 */
public final class UIRenderScope {
    private Runnable restore; // One-shot restoration callback, cleared before execution.

    /**
     * Retains a restoration callback without running it or changing batch state.
     * The enclosing context creates this only after pushing the matching scope.
     *
     * @param restore the action to run once on restoration, or null for an inactive scope
     */
    UIRenderScope(Runnable restore) {this.restore = restore;}

    /**
     * Runs the pending restoration once and marks this scope restored before
     * invoking it. Later calls do nothing, including after restoration throws.
     * Batch state failures propagate to the caller.
     *
     * @throws IllegalStateException if the underlying batch restoration rejects
     *                               the current drawing or stack state
     */
    public void restore() {
        if (restore != null) {
            Runnable action = restore;
            restore = null;
            action.run();
        }
    }
}
