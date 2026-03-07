package valthorne.graphics.map.tiled;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents a base class for various types of map layers.
 * It provides a common structure and functionality for handling map layers,
 * including properties such as name, visibility, opacity, and offsets.
 * This class is abstract and is intended to be extended by specific map layer types.
 */
public abstract class MapLayer {
    private final String name;                     // MapLayer name.
    protected final boolean visible;                // Visible flag.
    protected final float opacity;                  // Opacity (0..1).
    protected final float offsetX;                  // Pixel offset X.
    protected final float offsetY;                  // Pixel offset Y.
    protected final Map<String, String> properties; // Custom properties.

    /**
     * Constructs a new MapLayer instance with the specified parameters.
     *
     * @param name       the name of the map layer. If null, an empty string is assigned.
     * @param visible    a boolean indicating whether the map layer is visible.
     * @param opacity    the opacity of the map layer, specified as a float value between 0 and 1.
     * @param offsetX    the horizontal pixel offset of the map layer.
     * @param offsetY    the vertical pixel offset of the map layer.
     * @param properties a map of custom properties associated with the map layer.
     *                   If null, an empty map is assigned.
     */
    public MapLayer(String name, boolean visible, float opacity, float offsetX, float offsetY, Map<String, String> properties) {
        this.name = (name != null) ? name : "";
        this.visible = visible;
        this.opacity = opacity;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.properties = (properties != null) ? properties : new HashMap<>();
    }

    /**
     * Retrieves the name of the map layer.
     *
     * @return the name of the map layer as a string.
     */
    public String getName() {return name;}

    /**
     * Returns the visibility state of the map layer.
     *
     * @return true if the map layer is visible, false otherwise.
     */
    public boolean isVisible() {return visible;}

    /**
     * Retrieves the opacity of the map layer.
     * The opacity is a value between 0 and 1, where 0 represents full transparency
     * and 1 represents full opacity.
     *
     * @return the opacity of the map layer as a float.
     */
    public float getOpacity() {return opacity;}

    /**
     * Retrieves the horizontal pixel offset of the map layer.
     *
     * @return the horizontal pixel offset as a float.
     */
    public float getOffsetX() {return offsetX;}

    /**
     * Retrieves the vertical pixel offset of the map layer.
     *
     * @return the vertical pixel offset as a float.
     */
    public float getOffsetY() {return offsetY;}

    /**
     * Retrieves the custom properties associated with the map layer.
     *
     * @return a map containing the custom properties of the map layer,
     * where the keys and values are strings. If no properties are defined,
     * an empty map is returned.
     */
    public Map<String, String> getProperties() {return properties;}
}