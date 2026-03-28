package valthorne.graphics.map.tiled;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents the definition of a tile in a tilemap. This class encapsulates the
 * unique identifier of the tile, its properties, animation frames, and associated
 * objects. Each tile has a set of properties in key-value format, optional animation
 * frames defining its animated behavior, and optional tiled objects representing
 * associated metadata or interactive features.
 *
 * @param id         The unique identifier of the tile.
 * @param properties A map of properties associated with the tile in key-value format.
 * @param animation  A list of animation frames representing the tile's animation,
 *                   or null if the tile has no animation.
 * @param objects    A list of tiled objects representing metadata or interactive features
 *                   of the tile.
 */
public record TileDefinition(int id, Map<String, String> properties, List<TileAnimationFrame> animation, List<TiledObject> objects) {

    /**
     * Constructs a new {@code TileDefinition} object.
     * This constructor initializes the {@code properties} and {@code objects} fields
     * to default non-null values if they are passed as null.
     *
     * @param properties a {@code Map} representing the properties of the tile, or null to initialize with an empty map
     * @param objects    a {@code List} representing the associated objects of the tile, or null to initialize with an empty list
     */
    public TileDefinition {
        properties = properties != null ? properties : new HashMap<>();
        objects = objects != null ? objects : new ArrayList<>();
    }

    /**
     * Loads a {@code TileDefinition} object from the given {@code XMLStreamReader}.
     * This method reads tile properties, animations, and objects from the XML data, and
     * returns a new {@code TileDefinition} instance encapsulating the parsed information.
     *
     * @param r  the {@code XMLStreamReader} used to read the XML data
     * @param id the unique identifier for the tile
     * @return a {@code TileDefinition} object containing the parsed tile data
     * @throws Exception if there is an error while parsing the XML data
     */
    public static TileDefinition load(XMLStreamReader r, int id) throws Exception {
        Map<String, String> props = new HashMap<>();
        List<TileAnimationFrame> anim = null;
        List<TiledObject> objects = new ArrayList<>();

        while (r.hasNext()) {
            int ev = r.next();

            if (ev == XMLStreamConstants.START_ELEMENT) {
                String tag = r.getLocalName();

                switch (tag) {
                    case "properties" -> props.putAll(TiledXML.readProperties(r));
                    case "animation" -> anim = TileAnimationFrame.load(r);
                    case "objectgroup" -> readObjectGroup(r, objects);
                    case null, default -> TiledXML.skipElement(r);
                }
            } else if (ev == XMLStreamConstants.END_ELEMENT) {
                if ("tile".equals(r.getLocalName())) {
                    break;
                }
            }
        }

        return new TileDefinition(id, props, anim, objects);
    }

    private static void readObjectGroup(XMLStreamReader r, List<TiledObject> objects) throws Exception {
        while (r.hasNext()) {
            int ev = r.next();

            if (ev == XMLStreamConstants.START_ELEMENT) {
                String tag = r.getLocalName();

                if ("object".equals(tag)) {
                    objects.add(TiledObject.readObject(r));
                } else if ("properties".equals(tag)) {
                    TiledXML.readProperties(r);
                } else {
                    TiledXML.skipElement(r);
                }
            } else if (ev == XMLStreamConstants.END_ELEMENT) {
                if ("objectgroup".equals(r.getLocalName())) {
                    break;
                }
            }
        }
    }
}