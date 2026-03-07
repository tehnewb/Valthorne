package valthorne.graphics.map.tiled;

import valthorne.graphics.texture.Texture;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents a tiled image map layer in a map. This class extends the base functionality
 * of the MapLayer class, providing additional capabilities to work with an image
 * as a part of the map layer.
 */
public class TiledImageMapLayer extends MapLayer {

    public final Texture image; // The texture associated with the layer

    /**
     * Creates a new instance of TiledImageMapLayer, a layer that includes an image
     * as part of the map layout. This class extends the MapLayer class and
     * incorporates additional features specific to tiled images.
     *
     * @param name       the name of the layer
     * @param visible    a boolean indicating whether the layer is visible
     * @param opacity    the opacity level of the layer, ranging from 0.0 to 1.0
     * @param offsetX    the x-axis offset of the layer
     * @param offsetY    the y-axis offset of the layer
     * @param properties a map containing key-value pairs of layer properties
     * @param image      the texture representing the image associated with the layer
     */
    public TiledImageMapLayer(String name, boolean visible, float opacity, float offsetX, float offsetY, Map<String, String> properties, Texture image) {
        super(name, visible, opacity, offsetX, offsetY, properties);
        this.image = image;
    }

    /**
     * Loads a Tiled image map layer from the provided XML stream reader, TMX data, and associated dependencies.
     * This method parses the relevant attributes and properties of an image layer, including its image source,
     * and constructs a {@link TiledImageMapLayer} instance.
     *
     * @param tmxBytes the byte content of the TMX file, used for resolving dependencies
     * @param tmxPath  the path to the TMX file, serving as the base for resolving relative paths
     * @param resolver the dependency resolver for external resources such as images
     * @param r        the XML stream reader for reading the TMX file content
     * @return a {@link TiledImageMapLayer} instance containing the parsed layer data, including properties and image
     * @throws Exception if there is an error during the parsing or resource resolution process
     */
    public static TiledImageMapLayer load(byte[] tmxBytes, String tmxPath, TiledDependencyResolver resolver, XMLStreamReader r) throws Exception {
        String name = TiledXML.readAttribute(r, "name", "");
        boolean visible = TiledXML.readInteger(r, "visible", 1) == 1;
        float opacity = TiledXML.readFloat(r, "opacity", 1f);
        float offsetX = TiledXML.readFloat(r, "offsetx", 0f);
        float offsetY = TiledXML.readFloat(r, "offsety", 0f);

        Map<String, String> props = new HashMap<>();
        Texture image = null;

        while (r.hasNext()) {
            int ev = r.next();
            if (ev == XMLStreamConstants.START_ELEMENT) {
                String tag = r.getLocalName();

                if ("properties".equals(tag)) {
                    props.putAll(TiledXML.readProperties(r));
                } else if ("image".equals(tag)) {
                    String src = TiledXML.readAttribute(r, "source", null);
                    if (src != null && !src.isBlank()) {
                        byte[] imageBytes = TiledXML.readBytesFromTmxDependency(resolver, tmxBytes, tmxPath, src);
                        image = new Texture(imageBytes);
                    }
                    TiledXML.skipElement(r);
                } else {
                    TiledXML.skipElement(r);
                }
            } else if (ev == XMLStreamConstants.END_ELEMENT) {
                if ("imagelayer".equals(r.getLocalName())) break;
            }
        }

        return new TiledImageMapLayer(name, visible, opacity, offsetX, offsetY, props, image);
    }
}