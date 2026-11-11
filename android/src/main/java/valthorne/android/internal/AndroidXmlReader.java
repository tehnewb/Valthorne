package valthorne.android.internal;

import android.util.Xml;
import java.io.IOException;
import java.io.InputStream;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

/**
 * Minimal Tiled XML cursor backed by Android's platform parser. This covers the
 * element, attribute and text operations used by Valthorne's TMX/TSX loaders.
 * DTD declarations and unresolved entities are rejected; no external document
 * resolution is performed. The caller retains ownership of the encoded stream.
 */
public final class AndroidXmlReader {
    /**
     * Start-element event used by the shared Tiled parsing algorithms.
     */
    public static final int START_ELEMENT = 1;
    /**
     * End-element event used by the shared Tiled parsing algorithms.
     */
    public static final int END_ELEMENT = 2;
    /**
     * Ordinary text event used by property and CSV/base64 layer readers.
     */
    public static final int CHARACTERS = 4;
    /**
     * CDATA text event, preserved as text without interpreting markup.
     */
    public static final int CDATA = 12;
    private final XmlPullParser parser; // Independent document cursor owned by this reader.
    private boolean ended; // Whether end-of-document has been observed.
    private boolean closed; // Whether the caller has finished using this cursor.

    /**
     * Initializes a non-resolving platform parser for one borrowed input stream.
     * @param input encoded XML
     * @throws AndroidXmlException if parser configuration or input setup fails
     */
    AndroidXmlReader(InputStream input) throws AndroidXmlException {
        try {
            parser = Xml.newPullParser();
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false);
            parser.setInput(input, null);
        } catch (XmlPullParserException failure) {
            throw new AndroidXmlException("Unable to initialize Android XML parser", failure);
        }
    }

    /**
     * Reports whether another document event may be read.
     * @return false after close or end-of-document
     */
    public boolean hasNext() { return !closed && !ended; }

    /**
     * Advances one event while retaining text, including built-in XML entities.
     * @return shared Tiled event code; unrelated events are zero
     * @throws AndroidXmlException for malformed XML, forbidden DTDs or I/O failures
     */
    public int next() throws AndroidXmlException {
        if (closed) throw new AndroidXmlException("XML cursor is closed", null);
        try {
            int event = parser.nextToken();
            if (event == XmlPullParser.DOCDECL || (event == XmlPullParser.ENTITY_REF && parser.getText() == null))
                throw new AndroidXmlException("DTD declarations and unresolved XML entities are unsupported", null);
            ended = event == XmlPullParser.END_DOCUMENT;
            return switch (event) {
                case XmlPullParser.START_TAG -> START_ELEMENT;
                case XmlPullParser.END_TAG -> END_ELEMENT;
                case XmlPullParser.TEXT, XmlPullParser.ENTITY_REF, XmlPullParser.IGNORABLE_WHITESPACE -> CHARACTERS;
                case XmlPullParser.CDSECT -> CDATA;
                default -> 0;
            };
        } catch (XmlPullParserException | IOException failure) {
            throw new AndroidXmlException("Unable to read Tiled XML", failure);
        }
    }

    /**
     * Returns the local element name at the current start or end event.
     * @return local element name
     */
    public String getLocalName() { return parser.getName(); }

    /**
     * Reads an attribute without allocating an intermediate map.
     * @param namespace namespace URI, or null for an unqualified attribute
     * @param name local attribute name
     * @return attribute value, or null if absent
     */
    public String getAttributeValue(String namespace, String name) { return parser.getAttributeValue(namespace, name); }

    /**
     * Returns current text for CSV, base64, CDATA or property content.
     * @return current decoded text
     */
    public String getText() { return parser.getText(); }

    /**
     * Ends cursor access without closing the caller-owned encoded stream.
     */
    public void close() { closed = true; }
}
