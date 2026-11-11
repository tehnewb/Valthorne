package valthorne.browser.xml;

import org.teavm.jso.JSBody;
import org.teavm.jso.JSObject;

/**
 * Traverses a browser XML document in StAX event order without duplicating a list
 * of events. Element attributes remain borrowed from the immutable DOM. Text and
 * CDATA preserve their decoded contents; comments and processing instructions
 * are skipped. Closing releases the entire DOM even if parsing stopped early.
 */
public final class XMLStreamReader {
    private JSObject cursor; // Owned document and current traversal position; null after close.

    /**
     * Parses and validates one independent XML document.
     * @param text decoded XML Unicode source
     * @throws XMLStreamException if document parsing fails
     */
    XMLStreamReader(String text) throws XMLStreamException {
        cursor = parse(text);
        String failure = error(cursor);
        if (failure != null) {
            cursor = null;
            throw new XMLStreamException(failure);
        }
    }

    /**
     * Reports whether another event remains before the end-document event.
     * @return whether next can advance
     */
    public boolean hasNext() {
        return cursor != null && event(cursor) != 8;
    }

    /**
     * Advances to the next start, end, text, CDATA, or end-document event.
     * @return StAX-compatible event value
     * @throws XMLStreamException if no next event is available
     */
    public int next() throws XMLStreamException {
        if (!hasNext()) throw new XMLStreamException("XML cursor is exhausted or closed");
        return advance(cursor);
    }

    /**
     * Returns the local name at the current opening or closing element.
     * @return element local name
     */
    public String getLocalName() {
        return name(cursor);
    }

    /**
     * Returns decoded text at the current character or CDATA event.
     * @return text content
     */
    public String getText() {
        return text(cursor);
    }

    /**
     * Reads one attribute from the current opening element.
     * @param namespace optional namespace URI; null selects an unqualified attribute
     * @param name local attribute name
     * @return attribute value or null when absent
     */
    public String getAttributeValue(String namespace, String name) {
        return attribute(cursor, namespace, name);
    }

    /**
     * Drops the DOM cursor; the input stream remains owned by the caller.
     */
    public void close() {
        cursor = null;
    }

    /**
     * Parses without resolving external entities and reports DOM parser failures.
     * @param text document source
     * @return cursor or diagnostic container
     */
    @JSBody(params = "text", script = "if (/<!DOCTYPE/i.test(text.replace(/<!--[\\s\\S]*?-->|<!\\[CDATA\\[[\\s\\S]*?\\]\\]>/g,''))) return {error:'XML DTD declarations are not supported'}; var doc=new DOMParser().parseFromString(text,'application/xml'); var failure=doc.getElementsByTagName('parsererror')[0]; return failure ? {error:failure.textContent} : {node:doc,event:7};")
    private static native JSObject parse(String text);

    /**
     * Reads a parse diagnostic without retaining the source string.
     * @param cursor parse result
     * @return diagnostic or null
     */
    @JSBody(params = "cursor", script = "return cursor.error || null;")
    private static native String error(JSObject cursor);

    /**
     * Reads the cursor event.
     * @param cursor live cursor
     * @return current event value
     */
    @JSBody(params = "cursor", script = "return cursor.event;")
    private static native int event(JSObject cursor);

    /**
     * Walks children, siblings, and parents once in document order.
     * @param cursor live cursor
     * @return next event value
     */
    @JSBody(params = "cursor", script = "var node=cursor.node, child; if (cursor.event===1 || cursor.event===7) { child=node.firstChild; } else { child=node.nextSibling; } while (child && child.nodeType!==1 && child.nodeType!==3 && child.nodeType!==4) child=child.nextSibling; if (child) { cursor.node=child; cursor.event=child.nodeType===1 ? 1 : child.nodeType===4 ? 12 : 4; } else if (cursor.event===1) { cursor.event=2; } else { cursor.node=node.parentNode; cursor.event=cursor.node && cursor.node.nodeType===1 ? 2 : 8; } return cursor.event;")
    private static native int advance(JSObject cursor);

    /**
     * Reads a qualified element's local name.
     * @param cursor live cursor
     * @return local element name
     */
    @JSBody(params = "cursor", script = "if (!cursor || (cursor.event!==1 && cursor.event!==2)) throw new Error('XML cursor is not on an element'); return cursor.node.localName;")
    private static native String name(JSObject cursor);

    /**
     * Reads one decoded text node.
     * @param cursor live cursor
     * @return text node contents
     */
    @JSBody(params = "cursor", script = "if (!cursor || (cursor.event!==4 && cursor.event!==12)) throw new Error('XML cursor is not on text'); return cursor.node.nodeValue;")
    private static native String text(JSObject cursor);

    /**
     * Reads an opening element's attribute with namespace-aware lookup.
     * @param cursor live cursor
     * @param namespace namespace URI or null
     * @param name attribute name
     * @return decoded value or null
     */
    @JSBody(params = {"cursor", "namespace", "name"}, script = "if (!cursor || cursor.event!==1) throw new Error('XML cursor is not on a start element'); return namespace===null ? cursor.node.getAttribute(name) : cursor.node.getAttributeNS(namespace,name);")
    private static native String attribute(JSObject cursor, String namespace, String name);
}
