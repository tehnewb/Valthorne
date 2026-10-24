package valthorne.browser.xml;

/**
 * XML cursor event values consumed by the shared Tiled parsers. Values match StAX.
 */
public final class XMLStreamConstants {
    /**
     * Opening element event.
     */
    public static final int START_ELEMENT = 1;
    /**
     * Closing element event.
     */
    public static final int END_ELEMENT = 2;
    /**
     * Ordinary character data event.
     */
    public static final int CHARACTERS = 4;
    /**
     * CDATA character data event.
     */
    public static final int CDATA = 12;

    /**
     * Prevents constructing event constants.
     */
    private XMLStreamConstants() {
    }
}
