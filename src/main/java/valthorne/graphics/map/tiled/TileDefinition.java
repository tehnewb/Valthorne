package valthorne.graphics.map.tiled;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents the definition of a single tile within a tilemap. Each tile is defined
 * by an ID, a set of properties, and an optional animation sequence.
 * This class is immutable and utilizes Java's record feature for compact representation.
 *
 * @param id         The unique identifier of the tile.
 * @param properties A map of property name-value pairs associated with the tile. This
 *                   map may define additional metadata or custom properties for the tile.
 * @param animation  A list of {@code TileAnimationFrame} objects representing the animation
 *                   sequence for the tile, or {@code null} if the tile has no animation.
 */
public record TileDefinition(int id, Map<String, String> properties, List<TileAnimationFrame> animation) {

    /**
     * Constructs a {@code TileDefinition} instance. If the provided map of properties is {@code null},
     * an empty {@code HashMap} will be used as the default value.
     *
     * @param id         The unique identifier for the tile.
     * @param properties A map of property name-value pairs associated with the tile. If null, this will
     *                   default to an empty {@code HashMap}.
     * @param animation  A list of {@code TileAnimationFrame} objects defining the animation sequence
     *                   for the tile, or {@code null} if the tile is static and has no animation.
     */
    public TileDefinition {
        properties = (properties != null) ? properties : new HashMap<>();
    }

    /**
     * Loads and creates a {@code TileDefinition} object by reading and parsing an XML stream.
     * The method processes the "tile" XML element and its nested elements, extracting properties
     * and optional animation frames for the tile.
     *
     * @param r  the {@code XMLStreamReader} to read the XML data from
     * @param id the unique identifier of the tile being loaded
     * @return a {@code TileDefinition} object containing the parsed tile data, including its ID,
     * properties, and optional animation
     * @throws Exception if an error occurs while parsing the XML data
     */
    public static TileDefinition load(XMLStreamReader r, int id) throws Exception {
        Map<String, String> props = new HashMap<>();
        List<TileAnimationFrame> anim = null;

        while (r.hasNext()) {
            int ev = r.next();
            if (ev == XMLStreamConstants.START_ELEMENT) {
                String tag = r.getLocalName();
                if ("properties".equals(tag)) {
                    props.putAll(TiledXML.readProperties(r));
                } else if ("animation".equals(tag)) {
                    anim = TileAnimationFrame.load(r);
                } else {
                    TiledXML.skipElement(r);
                }
            } else if (ev == XMLStreamConstants.END_ELEMENT) {
                if ("tile".equals(r.getLocalName())) break;
            }
        }

        return new TileDefinition(id, props, anim);
    }
}