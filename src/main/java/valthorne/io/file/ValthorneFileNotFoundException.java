package valthorne.io.file;

/**
 * Thrown when a classpath resource cannot be found.
 * Allows callers to distinguish a missing resource from other file failures while
 * retaining the unchecked {@link ValthorneFileException} contract.
 *
 * @author Albert Beaupre
 */
public class ValthorneFileNotFoundException extends ValthorneFileException {
    /**
     * Creates a missing-resource failure with the supplied diagnostic text.
     * The constructor only records the message; it does not attempt another lookup.
     *
     * @param message resource identifier and failure details; may be null
     */
    public ValthorneFileNotFoundException(String message) {super(message);}
}
