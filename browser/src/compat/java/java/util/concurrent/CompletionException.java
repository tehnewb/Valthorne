package java.util.concurrent;

/**
 * Unchecked wrapper retaining a failed asynchronous operation's cause.
 */
public class CompletionException extends RuntimeException {
    /**
     * Creates an unchecked completion failure, retaining its supplied message and cause.
     */
    protected CompletionException() {
    }

    /**
     * Creates an unchecked completion failure, retaining its supplied message and cause.
     * @param message failure description, including null
     */
    protected CompletionException(String message) {
        super(message);
    }

    /**
     * Creates an unchecked completion failure, retaining its supplied message and cause.
     * @param cause retained exceptional cause
     */
    public CompletionException(Throwable cause) {
        super(cause);
    }

    /**
     * Creates an unchecked completion failure, retaining its supplied message and cause.
     * @param message failure description, including null
     * @param cause retained exceptional cause
     */
    public CompletionException(String message, Throwable cause) {
        super(message, cause);
    }
}
