package valthorne.graphics.map.tiled;

import java.util.HashMap;
import java.util.Map;

public abstract class MapLayer {
    private final String name;                    // MapLayer name.
    protected final boolean visible;                // Visible flag.
    protected final float opacity;                  // Opacity (0..1).
    protected final float offsetX;                  // Pixel offset X.
    protected final float offsetY;                  // Pixel offset Y.
    protected final Map<String, String> properties; // Custom properties.

    public MapLayer(String name, boolean visible, float opacity, float offsetX, float offsetY, Map<String, String> properties) {
        this.name = (name != null) ? name : "";
        this.visible = visible;
        this.opacity = opacity;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.properties = (properties != null) ? properties : new HashMap<>();
    }

    public String getName() {return name;}

    public boolean isVisible() {return visible;}

    public float getOpacity() {return opacity;}

    public float getOffsetX() {return offsetX;}

    public float getOffsetY() {return offsetY;}

    public Map<String, String> getProperties() {return properties;}
}