package valthorne.graphics.map.tiled;

import valthorne.graphics.texture.TextureData;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents the CPU-side data for a Tiled image layer.
 *
 * <p>
 * A Tiled image layer corresponds to an {@code <imagelayer>} element in a TMX map.
 * Unlike tile layers, an image layer does not store a grid of tile IDs. Instead, it
 * references a single image that is drawn as one large visual layer. In this CPU-side
 * form, the referenced image is decoded into {@link TextureData} so it can later be
 * turned into a runtime GPU texture on the OpenGL thread.
 * </p>
 *
 * <p>
 * This class extends {@link MapLayer}, so it inherits common layer metadata such as:
 * </p>
 *
 * <ul>
 *     <li>Layer name</li>
 *     <li>Visibility</li>
 *     <li>Opacity</li>
 *     <li>Pixel offsets</li>
 *     <li>Custom properties</li>
 * </ul>
 *
 * <p>
 * The main additional value stored by this class is the decoded {@link #imageData}.
 * This makes the class useful inside asynchronous loaders because it avoids creating
 * OpenGL textures while still preparing everything needed for later rendering.
 * </p>
 *
 * <h2>Typical lifecycle</h2>
 * <ol>
 *     <li>Load TMX data on a worker thread</li>
 *     <li>Resolve the image dependency and decode it into {@link TextureData}</li>
 *     <li>Store it inside {@code TiledImageMapLayerData}</li>
 *     <li>Later, on the render thread, turn that data into a GPU texture</li>
 * </ol>
 *
 * @author Albert Beaupre
 * @since March 7th, 2026
 */
public final class TiledImageMapLayerData extends MapLayer {

    private final TextureData imageData; // The decoded CPU-side image data used by this image layer.

    /**
     * Creates a new image-layer data object.
     *
     * <p>
     * This constructor stores the inherited layer metadata and the decoded image data.
     * The image data may be null if the image layer did not define a usable source image,
     * though a valid Tiled image layer will normally provide one.
     * </p>
     *
     * @param name the name of the layer
     * @param visible whether the layer is visible
     * @param opacity the layer opacity
     * @param offsetX the horizontal pixel offset
     * @param offsetY the vertical pixel offset
     * @param properties the custom layer properties
     * @param imageData the decoded image data for this layer
     */
    public TiledImageMapLayerData(String name, boolean visible, float opacity, float offsetX, float offsetY, Map<String, String> properties, TextureData imageData) {
        super(name, visible, opacity, offsetX, offsetY, properties);
        this.imageData = imageData;
    }

    /**
     * Loads a {@code TiledImageMapLayerData} instance from the current {@code <imagelayer>} XML element.
     *
     * <p>
     * This method reads the common image-layer attributes from the current XML element,
     * then scans its child elements for:
     * </p>
     *
     * <ul>
     *     <li>{@code <properties>} to collect custom layer properties</li>
     *     <li>{@code <image>} to resolve and decode the referenced image</li>
     * </ul>
     *
     * <p>
     * The image source is resolved using the supplied {@link TiledDependencyResolver},
     * allowing the layer image to come from disk, memory, cache archives, or any other
     * custom backing store supported by the resolver.
     * </p>
     *
     * <p>
     * The resolved image bytes are decoded into {@link TextureData}, which makes the
     * result safe to construct on a worker thread without touching OpenGL.
     * </p>
     *
     * @param tmxBytes the raw TMX bytes of the parent map
     * @param tmxPath the logical or physical path of the parent TMX file
     * @param resolver the dependency resolver used to load the referenced image
     * @param reader the XML reader positioned at the start of an {@code <imagelayer>} element
     * @return the parsed image-layer data object
     * @throws Exception if parsing fails or the image dependency cannot be resolved or decoded
     */
    public static TiledImageMapLayerData load(byte[] tmxBytes, String tmxPath, TiledDependencyResolver resolver, XMLStreamReader reader) throws Exception {
        String name = TiledXML.readAttribute(reader, "name", "");
        boolean visible = TiledXML.readInteger(reader, "visible", 1) == 1;
        float opacity = TiledXML.readFloat(reader, "opacity", 1f);
        float offsetX = TiledXML.readFloat(reader, "offsetx", 0f);
        float offsetY = TiledXML.readFloat(reader, "offsety", 0f);

        Map<String, String> props = new HashMap<>();
        TextureData imageData = null;

        while (reader.hasNext()) {
            int event = reader.next();

            if (event == XMLStreamConstants.START_ELEMENT) {
                String tag = reader.getLocalName();

                if ("properties".equals(tag)) {
                    props.putAll(TiledXML.readProperties(reader));
                } else if ("image".equals(tag)) {
                    String source = TiledXML.readAttribute(reader, "source", null);
                    if (source != null && !source.isBlank()) {
                        byte[] imageBytes = TiledXML.readBytesFromTmxDependency(resolver, tmxBytes, tmxPath, source);
                        imageData = TextureData.load(imageBytes, true);
                    }
                    TiledXML.skipElement(reader);
                } else {
                    TiledXML.skipElement(reader);
                }
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                if ("imagelayer".equals(reader.getLocalName())) {
                    break;
                }
            }
        }

        return new TiledImageMapLayerData(name, visible, opacity, offsetX, offsetY, props, imageData);
    }

    /**
     * Returns the decoded CPU-side image data for this layer.
     *
     * <p>
     * This data can later be converted into a runtime texture on the OpenGL thread.
     * If the image layer had no valid image source, this may be null.
     * </p>
     *
     * @return the decoded image data for this layer, or null if none was loaded
     */
    public TextureData getImageData() {
        return imageData;
    }
}