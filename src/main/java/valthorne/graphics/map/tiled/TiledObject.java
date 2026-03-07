package valthorne.graphics.map.tiled;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;


/**
 * Represents an object within a Tiled map with a variety of properties.
 *
 * @param id             The unique identifier for the object.
 * @param name           The name of the object, which may be an empty string if not specified.
 * @param type           The type of the object, which is used to define its category and may be an empty string if not specified.
 * @param x              The x-coordinate of the object's position in the Tiled map.
 * @param y              The y-coordinate of the object's position in the Tiled map.
 * @param width          The width of the object, or zero if it is undefined.
 * @param height         The height of the object, or zero if it is undefined.
 * @param rotation       The rotation of the object in degrees.
 * @param visible        A boolean indicating whether the object is visible.
 * @param gid            The global identifier for a tile, or zero if the object is not connected to a tile.
 * @param properties     A map of custom properties as key-value pairs defined for the object.
 * @param tiledShapeType The shape type of the object (e.g., RECT, POINT, ELLIPSE, etc.) as defined by {@link TiledShapeType}.
 * @param points         An array of points representing polygons or polylines, or null if the object does not use points.
 * @param text           The text content of the object (if it is a text object), or null if no text is associated.
 */
public record TiledObject(int id, String name, String type, float x, float y, float width, float height, float rotation, boolean visible, int gid, Map<String, String> properties, TiledShapeType tiledShapeType, float[] points, String text) {

    /**
     * Constructs a TiledObject instance with the specified parameters.
     *
     * @param id             the unique identifier for the TiledObject
     * @param name           the name of the TiledObject, defaults to an empty string if null
     * @param type           the type of the TiledObject, defaults to an empty string if null
     * @param x              the x-coordinate position of the TiledObject
     * @param y              the y-coordinate position of the TiledObject
     * @param width          the width of the TiledObject
     * @param height         the height of the TiledObject
     * @param rotation       the rotation angle of the TiledObject in degrees
     * @param visible        the visibility state of the TiledObject
     * @param gid            the global identifier of the associated tile, or 0 if there is no associated tile
     * @param properties     a map of custom properties for the TiledObject, defaults to an empty map if null
     * @param tiledShapeType the shape type of the TiledObject, defaults to TiledShapeType.RECT if null
     * @param points         an array of floats representing points for polygon or polyline shapes
     * @param text           the text content of the TiledObject, if applicable
     */
    public TiledObject(int id, String name, String type, float x, float y, float width, float height, float rotation, boolean visible, int gid, Map<String, String> properties, TiledShapeType tiledShapeType, float[] points, String text) {
        this.id = id;
        this.name = (name != null) ? name : "";
        this.type = (type != null) ? type : "";
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.rotation = rotation;
        this.visible = visible;
        this.gid = gid;
        this.properties = (properties != null) ? properties : new HashMap<>();
        this.tiledShapeType = (tiledShapeType != null) ? tiledShapeType : TiledShapeType.RECT;
        this.points = points;
        this.text = text;
    }

    /**
     * Reads and parses a TiledObject from the given XMLStreamReader.
     * This method processes various attributes and elements of a TiledObject,
     * including its geometry, properties, and other metadata,
     * during the parsing of an XML document.
     *
     * @param r the XMLStreamReader from which the TiledObject is read and parsed
     * @return a TiledObject instance representing the parsed XML element
     * @throws Exception if an error occurs while parsing the XML
     */
    public static TiledObject readObject(XMLStreamReader r) throws Exception {
        int id = TiledXML.readInteger(r, "id", 0);
        String name = TiledXML.readAttribute(r, "name", "");
        String type = TiledXML.readAttribute(r, "type", "");
        float x = TiledXML.readFloat(r, "x", 0f);
        float y = TiledXML.readFloat(r, "y", 0f);
        float w = TiledXML.readFloat(r, "width", 0f);
        float h = TiledXML.readFloat(r, "height", 0f);
        float rotation = TiledXML.readFloat(r, "rotation", 0f);
        boolean visible = TiledXML.readInteger(r, "visible", 1) == 1;
        int gid = TiledXML.readInteger(r, "gid", 0);

        Map<String, String> objectProperties = new HashMap<>();
        TiledShapeType tiledShapeType = TiledShapeType.RECT;
        float[] points = null;
        String text = null;

        while (r.hasNext()) {
            int ev = r.next();
            if (ev == XMLStreamConstants.START_ELEMENT) {
                String tag = r.getLocalName();

                switch (tag) {
                    case "properties" -> objectProperties.putAll(TiledXML.readProperties(r));
                    case "ellipse" -> {
                        tiledShapeType = TiledShapeType.ELLIPSE;
                        TiledXML.skipElement(r);
                    }
                    case "point" -> {
                        tiledShapeType = TiledShapeType.POINT;
                        TiledXML.skipElement(r);
                    }
                    case "polygon" -> {
                        tiledShapeType = TiledShapeType.POLYGON;
                        points = parsePoints(TiledXML.readAttribute(r, "points", ""));
                        TiledXML.skipElement(r);
                    }
                    case "polyline" -> {
                        tiledShapeType = TiledShapeType.POLYLINE;
                        points = parsePoints(TiledXML.readAttribute(r, "points", ""));
                        TiledXML.skipElement(r);
                    }
                    case "text" -> {
                        tiledShapeType = TiledShapeType.TEXT;
                        text = TiledXML.readElementText(r, "text").trim();
                    }
                    case null, default -> TiledXML.skipElement(r);
                }
            } else if (ev == XMLStreamConstants.END_ELEMENT) {
                if ("object".equals(r.getLocalName())) break;
            }
        }

        return new TiledObject(id, name, type, x, y, w, h, rotation, visible, gid, objectProperties, tiledShapeType, points, text);
    }

    private static float[] parsePoints(String points) {
        if (points == null) return null;
        String p = points.trim();
        if (p.isEmpty()) return null;

        String[] pairs = p.split("\\s+");
        float[] out = new float[pairs.length * 2];

        int idx = 0;
        for (String pair : pairs) {
            String[] xy = pair.split(",");
            if (xy.length != 2) continue;
            out[idx++] = parseFloatSafe(xy[0]);
            out[idx++] = parseFloatSafe(xy[1]);
        }

        if (idx == out.length) return out;
        return Arrays.copyOf(out, idx);
    }

    private static float parseFloatSafe(String s) {
        try {
            return Float.parseFloat(s.trim());
        } catch (Exception ignored) {
            return 0f;
        }
    }
}