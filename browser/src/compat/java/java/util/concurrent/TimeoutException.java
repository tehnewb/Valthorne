package java.util.concurrent;

/**
 * Signals expiration of an explicit future deadline.
 */
public class TimeoutException extends Exception {
    /**
     * Creates a deadline-expiration failure with the supplied optional message.
     */
    public TimeoutException() {
    }

    /**
     * Creates a deadline-expiration failure with the supplied optional message.
     * @param message failure description, including null
     */
    public TimeoutException(String message) {
        super(message);
    }
}
