package valthorne.graphics;

/**
 * Reports that both a backend transition and restoration of the original device
 * failed. Rendering cannot safely continue; JGL closes the application through its
 * normal disposal lifecycle after completing the backend-change future.
 */
public final class GraphicsRecoveryException extends IllegalStateException {
    /**
     * Serialization identity for the exceptional graphics lifecycle failure.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Retains the transition failure and its suppressed recovery failures.
     *
     * @param cause original transition failure with recovery diagnostics
     */
    public GraphicsRecoveryException(Throwable cause) {
        super("Graphics transition and renderer recovery both failed", cause);
    }
}
