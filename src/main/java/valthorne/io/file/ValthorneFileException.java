package valthorne.io.file;

/**
 * Base runtime exception for Valthorne file operations.
 */
public class ValthorneFileException extends RuntimeException {
    public ValthorneFileException(String message) {super(message);}

    public ValthorneFileException(String message, Throwable cause) {super(message, cause);}
}