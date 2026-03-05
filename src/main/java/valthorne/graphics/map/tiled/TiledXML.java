package valthorne.graphics.map.tiled;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class TiledXML {

    private static final XMLInputFactory XML_FACTORY = XMLInputFactory.newInstance();

    public static Path resolveRelative(Path baseFile, String rel) {
        Path baseDir = baseFile.toAbsolutePath().getParent();
        if (baseDir == null) return Paths.get(rel).toAbsolutePath().normalize();
        return baseDir.resolve(rel).toAbsolutePath().normalize();
    }

    public static byte[] readBytesFromTmxDependency(byte[] tmxBytes, String relativePath) {
        try {
            Path path = Paths.get(relativePath);
            if (!path.isAbsolute()) {
                path = path.toAbsolutePath().normalize();
            }
            return java.nio.file.Files.readAllBytes(path);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load TMX dependency: " + relativePath, e);
        }
    }

    public static String readElementText(XMLStreamReader r, String elementName) throws XMLStreamException {
        StringBuilder sb = new StringBuilder(256);
        while (r.hasNext()) {
            int ev = r.next();
            if (ev == XMLStreamConstants.CHARACTERS || ev == XMLStreamConstants.CDATA) {
                sb.append(r.getText());
            } else if (ev == XMLStreamConstants.END_ELEMENT) {
                if (elementName.equals(r.getLocalName())) break;
            } else if (ev == XMLStreamConstants.START_ELEMENT) {
                TiledXML.skipElement(r);
            }
        }
        return sb.toString();
    }

    public static Map<String, String> readProperties(XMLStreamReader r) throws Exception {
        Map<String, String> props = new HashMap<>();
        while (r.hasNext()) {
            int ev = r.next();
            if (ev == XMLStreamConstants.START_ELEMENT) {
                if ("property".equals(r.getLocalName())) {
                    String name = TiledXML.readAttribute(r, "name", "");
                    String value = TiledXML.readAttribute(r, "value", null);
                    if (value == null) value = "";
                    props.put(name, value);
                    TiledXML.skipElement(r);
                } else {
                    TiledXML.skipElement(r);
                }
            } else if (ev == XMLStreamConstants.END_ELEMENT) {
                if ("properties".equals(r.getLocalName())) break;
            }
        }
        return props;
    }

    public static String readAttribute(XMLStreamReader r, String name, String def) {
        String v = r.getAttributeValue(null, name);
        return (v != null) ? v : def;
    }

    public static int readInteger(XMLStreamReader r, String name, int def) {
        String v = r.getAttributeValue(null, name);
        if (v == null || v.isBlank()) return def;
        try {
            return Integer.parseInt(v);
        } catch (Exception ignored) {
            return def;
        }
    }

    public static float readFloat(XMLStreamReader r, String name, float def) {
        String v = r.getAttributeValue(null, name);
        if (v == null || v.isBlank()) return def;
        try {
            return Float.parseFloat(v);
        } catch (Exception ignored) {
            return def;
        }
    }

    public static void moveToStart(XMLStreamReader r, String element) throws XMLStreamException {
        while (r.hasNext()) {
            int ev = r.next();
            if (ev == XMLStreamConstants.START_ELEMENT && element.equals(r.getLocalName())) return;
        }
        throw new IllegalStateException("Missing <" + element + "> in XML.");
    }

    public static void skipElement(XMLStreamReader r) throws XMLStreamException {
        int depth = 1;
        while (r.hasNext() && depth > 0) {
            int ev = r.next();
            if (ev == XMLStreamConstants.START_ELEMENT) depth++;
            else if (ev == XMLStreamConstants.END_ELEMENT) depth--;
        }
    }

    public static XMLInputFactory getXMLFactory() {
        return XML_FACTORY;
    }
}
