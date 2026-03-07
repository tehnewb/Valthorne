package valthorne.graphics.map.tiled;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a map layer containing tiled objects.
 * This class extends the functionality of the base MapLayer class
 * and introduces a collection of TiledObject elements that belong
 * to the layer. Each object in the layer can have its own attributes
 * and properties.
 */
public class TiledObjectMapLayer extends MapLayer {

    private final List<TiledObject> objects;

    /**
     * Constructs a new TiledObjectMapLayer with the specified parameters.
     * The layer represents a collection of tiled objects and inherits properties
     * from the base MapLayer class.
     *
     * @param name       the name of the layer
     * @param visible    the visibility state of the layer
     * @param opacity    the opacity level of the layer, ranging from 0.0 to 1.0
     * @param offsetX    the horizontal offset of the layer
     * @param offsetY    the vertical offset of the layer
     * @param properties a map of key-value pairs defining custom properties for the layer
     * @param objects    a list of TiledObject instances contained in the layer; if null, an empty list is used
     */
    public TiledObjectMapLayer(String name, boolean visible, float opacity, float offsetX, float offsetY, Map<String, String> properties, List<TiledObject> objects) {
        super(name, visible, opacity, offsetX, offsetY, properties);
        this.objects = (objects != null) ? objects : new ArrayList<>();
    }

    /**
     * Loads a TiledObjectMapLayer from the given XML stream reader.
     * This method processes the XML representation of a tiled object group,
     * parsing its properties and objects to instantiate a TiledObjectMapLayer.
     *
     * @param r the XML stream reader positioned at the object group element
     * @return a TiledObjectMapLayer instance populated with data from the XML
     * @throws Exception if an error occurs while reading the XML
     */
    public static TiledObjectMapLayer load(XMLStreamReader r) throws Exception {
        String name = TiledXML.readAttribute(r, "name", "");
        boolean visible = TiledXML.readInteger(r, "visible", 1) == 1;
        float opacity = TiledXML.readFloat(r, "opacity", 1f);
        float offsetX = TiledXML.readFloat(r, "offsetx", 0f);
        float offsetY = TiledXML.readFloat(r, "offsety", 0f);

        Map<String, String> props = new HashMap<>();
        List<TiledObject> objects = new ArrayList<>();

        while (r.hasNext()) {
            int ev = r.next();
            if (ev == XMLStreamConstants.START_ELEMENT) {
                String tag = r.getLocalName();

                if ("properties".equals(tag)) {
                    props.putAll(TiledXML.readProperties(r));
                } else if ("object".equals(tag)) {
                    objects.add(TiledObject.readObject(r));
                } else {
                    TiledXML.skipElement(r);
                }
            } else if (ev == XMLStreamConstants.END_ELEMENT) {
                if ("objectgroup".equals(r.getLocalName())) break;
            }
        }

        return new TiledObjectMapLayer(name, visible, opacity, offsetX, offsetY, props, objects);
    }

    /**
     * Retrieves the list of TiledObject instances associated with this layer.
     *
     * @return a list of TiledObject instances contained in this TiledObjectMapLayer
     */
    public List<TiledObject> getObjects() {
        return objects;
    }
}