package valthorne.android.internal;

/**
 * Checked failure from Android's streaming XML parser while loading a TMX/TSX file.
 * The original parser or I/O cause remains available for asset-load diagnostics.
 */
public final class AndroidXmlException extends Exception {
    /**
     * Preserves the parser failure with context about the rejected document.
     * @param message failure context
     * @param cause original parser or input failure
     */
    public AndroidXmlException(String message, Throwable cause) { super(message, cause); }
}
