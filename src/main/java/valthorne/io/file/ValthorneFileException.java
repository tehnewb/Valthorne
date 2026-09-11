package valthorne.io.file;

/**
 * Base runtime exception for Valthorne file operations.
 * Wraps file access or decoding failures without requiring checked exception
 * declarations. A supplied cause retains the underlying failure for diagnostics.
 *
 * @author Albert Beaupre
 */
public class ValthorneFileException extends RuntimeException {
    /**
     * Creates a failure with a descriptive message and no explicit cause.
     *
     * @param message explanation of the failed operation; may be null
     */
    public ValthorneFileException(String message) {super(message);}

    /**
     * Creates a failure that preserves the underlying exception.
     *
     * @param message explanation of the failed operation; may be null
     * @param cause   underlying failure; may be null
     */
    public ValthorneFileException(String message, Throwable cause) {super(message, cause);}
}
