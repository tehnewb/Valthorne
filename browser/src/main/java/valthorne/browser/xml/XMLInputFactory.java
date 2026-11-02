package valthorne.browser.xml;

import java.io.IOException;
import java.io.InputStream;
import org.teavm.jso.JSBody;

/**
 * Creates independent browser XML cursors for TMX and TSX documents. Parsing uses
 * the browser's XML parser, with DTDs and external entity declarations rejected.
 * Each cursor owns its document; the factory holds no mutable parsing state.
 */
public final class XMLInputFactory {
    /**
     * Creates a stateless factory.
     */
    private XMLInputFactory() {
    }

    /**
     * Creates a factory without retaining document state.
     * @return independent factory
     */
    public static XMLInputFactory newInstance() {
        /*
         * Documents and cursor positions belong exclusively to each reader.
         */
        return new XMLInputFactory();
    }

    /**
     * Decodes a complete Tiled document using its BOM or XML encoding declaration
     * and validates its XML structure. Browser TextDecoder defines supported labels.
     * @param input borrowed input stream, not closed by this method
     * @return cursor initially positioned before the root element
     * @throws IOException if input cannot be read
     * @throws XMLStreamException if XML is malformed or declares a DTD
     */
    public XMLStreamReader createXMLStreamReader(InputStream input) throws IOException, XMLStreamException {
        byte[] bytes = input.readAllBytes();
        String text;
        try {
            text = decode(bytes);
        } catch (RuntimeException failure) {
            throw new IOException("Could not decode XML document", failure);
        }
        return new XMLStreamReader(text);
    }
    /**
     * Honors XML byte-order signatures and explicit legacy encoding declarations.
     * @param bytes encoded XML document
     * @return decoded Unicode text
     */
    @JSBody(params = "bytes", script = "var data=new Uint8Array(bytes), encoding='utf-8'; if ((data[0]===255 && data[1]===254) || (data[0]===60 && data[1]===0)) encoding='utf-16le'; else if ((data[0]===254 && data[1]===255) || (data[0]===0 && data[1]===60)) encoding='utf-16be'; else { var prefix=''; for (var i=0;i<Math.min(data.length,512);i++) prefix+=String.fromCharCode(data[i]); var match=/^\\s*<\\?xml\\s+[^?]*encoding\\s*=\\s*['\"]([^'\"]+)['\"]/i.exec(prefix); if (match) encoding=match[1]; } return new TextDecoder(encoding,{fatal:true}).decode(data);")
    private static native String decode(byte[] bytes);
}
