package valthorne.browser.xml;

/**
 * Reports malformed XML or invalid cursor access to the shared map loader.
 */
public final class XMLStreamException extends Exception {
    /**
     * Creates a checked parse failure preserving the browser's diagnostic.
     * @param message parser diagnostic
     */
    public XMLStreamException(String message) {
        super(message);
    }
}
