package valthorne.graphics.map.tiled;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Utility class for handling XML-related operations specific to Tiled map structures. This includes
 * reading and resolving paths, extracting XML element contents, handling attributes, and processing
 * properties or other common behaviors found in Tiled TMX/TSX files.
 */
public class TiledXML {

    /**
     * Factory instance for creating XML parsers. It is reused within the class to ensure efficient resource utilization.
     */
    private static final XMLInputFactory XML_FACTORY = XMLInputFactory.newInstance();

    /**
     * Resolves and retrieves the raw content of a dependency file (e.g., external tilesets or images)
     * relative to the parent file by leveraging the provided resolver logic.
     *
     * @param resolver       The dependency resolver responsible for locating and retrieving dependencies.
     * @param parentBytes    The byte-stream corresponding to the parent TMX or TSX file.
     * @param parentPath     The file path of the parent TMX or TSX resource.
     * @param dependencyPath The dependency's relative path specified in the parent resource.
     * @return The byte content of the resolved dependency file.
     * @throws RuntimeException If the resolver fails or provides invalid results.
     */
    public static byte[] readBytesFromTmxDependency(TiledDependencyResolver resolver, byte[] parentBytes, String parentPath, String dependencyPath) {
        Objects.requireNonNull(resolver, "The dependency resolver cannot be null.");
        Objects.requireNonNull(dependencyPath, "Dependency path cannot be null.");
        try {
            byte[] out = resolver.resolve(parentBytes, parentPath, dependencyPath);
            if (out == null || out.length == 0) {
                throw new RuntimeException("Resolver returned no content for the dependency path: " + dependencyPath);
            }
            return out;
        } catch (Exception e) {
            throw new RuntimeException("Failed to load dependency: " + dependencyPath, e);
        }
    }

    /**
     * Resolves a relative file path to an absolute normalized path based on a given base path.
     *
     * @param basePath The base directory (can be null or empty).
     * @param rel      The relative path to resolve.
     * @return An absolute, normalized path string or null if the relative path is null.
     */
    public static String resolvePathString(String basePath, String rel) {
        if (rel == null) return null;

        Path relativePath = Paths.get(rel);
        if (relativePath.isAbsolute()) {
            return relativePath.toAbsolutePath().normalize().toString();
        }

        if (basePath == null || basePath.isBlank()) {
            return Paths.get(System.getProperty("user.dir")).resolve(rel).toAbsolutePath().normalize().toString();
        }

        Path baseAbsPath = Paths.get(basePath).toAbsolutePath().normalize();
        Path parentDir = baseAbsPath.getParent();
        if (parentDir == null) return relativePath.toAbsolutePath().normalize().toString();
        return parentDir.resolve(rel).toAbsolutePath().normalize().toString();
    }

    /**
     * Resolves a relative path against a base file, returning an absolute normalized path.
     *
     * @param baseFile The base file whose directory is used for resolution.
     * @param rel      The relative path to resolve.
     * @return An absolute, normalized Path instance.
     */
    public static Path resolveRelative(Path baseFile, String rel) {
        Path baseDir = baseFile.toAbsolutePath().getParent();
        if (baseDir == null) return Paths.get(rel).toAbsolutePath().normalize();
        return baseDir.resolve(rel).toAbsolutePath().normalize();
    }

    /**
     * Reads the text content of an XML element from the current position of the given reader until
     * the end tag corresponding to the provided element name is encountered.
     *
     * @param r           The XMLStreamReader positioned at the desired element.
     * @param elementName The name of the target element whose text content is to be read.
     * @return The cumulative text content of the element.
     * @throws XMLStreamException If XML parsing fails.
     */
    public static String readElementText(XMLStreamReader r, String elementName) throws XMLStreamException {
        StringBuilder sb = new StringBuilder(256);
        while (r.hasNext()) {
            int event = r.next();
            if (event == XMLStreamConstants.CHARACTERS || event == XMLStreamConstants.CDATA) {
                sb.append(r.getText());
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                if (elementName.equals(r.getLocalName())) break;
            } else if (event == XMLStreamConstants.START_ELEMENT) {
                TiledXML.skipElement(r);
            }
        }
        return sb.toString();
    }

    /**
     * Reads all 'property' elements nested within an XML 'properties' section.
     *
     * @param r The XMLStreamReader positioned at the start of the 'properties' element.
     * @return A map containing property names as keys and their respective values as values.
     * @throws Exception If an error occurs during parsing.
     */
    public static Map<String, String> readProperties(XMLStreamReader r) throws Exception {
        Map<String, String> properties = new HashMap<>();
        while (r.hasNext()) {
            int event = r.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                if ("property".equals(r.getLocalName())) {
                    String name = TiledXML.readAttribute(r, "name", "");
                    String value = TiledXML.readAttribute(r, "value", "");
                    properties.put(name, value != null ? value : "");
                    TiledXML.skipElement(r);
                } else {
                    TiledXML.skipElement(r);
                }
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                if ("properties".equals(r.getLocalName())) break;
            }
        }
        return properties;
    }

    /**
     * Reads an attribute value from the current element in the XML reader.
     *
     * @param r    The XMLStreamReader positioned at a start element.
     * @param name The name of the attribute to read.
     * @param def  The default value to return if the attribute is not present.
     * @return The attribute value or the default value if the attribute is missing.
     */
    public static String readAttribute(XMLStreamReader r, String name, String def) {
        String value = r.getAttributeValue(null, name);
        return value != null ? value : def;
    }

    /**
     * Reads an integer-valued attribute from the current element in the reader.
     *
     * @param r    The XMLStreamReader positioned at a start element.
     * @param name The attribute name.
     * @param def  The default value to return if parsing fails or the attribute is missing.
     * @return The parsed integer value or the default value if parsing fails.
     */
    public static int readInteger(XMLStreamReader r, String name, int def) {
        String value = r.getAttributeValue(null, name);
        if (value == null || value.isBlank()) return def;
        try {
            return Integer.parseInt(value);
        } catch (Exception ignored) {
            return def;
        }
    }

    /**
     * Reads a float-valued attribute from the current element in the reader.
     *
     * @param r    The XMLStreamReader positioned at a start element.
     * @param name The attribute name.
     * @param def  The default value to return if parsing fails or the attribute is missing.
     * @return The parsed float value or the default value if parsing fails.
     */
    public static float readFloat(XMLStreamReader r, String name, float def) {
        String value = r.getAttributeValue(null, name);
        if (value == null || value.isBlank()) return def;
        try {
            return Float.parseFloat(value);
        } catch (Exception ignored) {
            return def;
        }
    }

    /**
     * Advances the XMLStreamReader to the start of the specified element or throws an exception if not found.
     *
     * @param r       The XMLStreamReader to navigate.
     * @param element The target element name.
     * @throws XMLStreamException If the element is missing or XML parsing fails.
     */
    public static void moveToStart(XMLStreamReader r, String element) throws XMLStreamException {
        while (r.hasNext()) {
            int event = r.next();
            if (event == XMLStreamConstants.START_ELEMENT && element.equals(r.getLocalName())) return;
        }
        throw new IllegalStateException("Missing start element: <" + element + ">.");
    }

    /**
     * Skips over the current XML element (including nested child elements) in the reader.
     *
     * @param r The XMLStreamReader positioned at a start element.
     * @throws XMLStreamException If XML parsing fails.
     */
    public static void skipElement(XMLStreamReader r) throws XMLStreamException {
        int depth = 1;
        while (r.hasNext() && depth > 0) {
            int event = r.next();
            if (event == XMLStreamConstants.START_ELEMENT) depth++;
            else if (event == XMLStreamConstants.END_ELEMENT) depth--;
        }
    }

    /**
     * Retrieves the shared XMLInputFactory instance for creating XML parsers.
     *
     * @return The shared XMLInputFactory instance.
     */
    public static XMLInputFactory getXMLFactory() {
        return XML_FACTORY;
    }
}