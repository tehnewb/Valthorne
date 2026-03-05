package valthorne.graphics.map.tiled;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class TiledObject {
    public final int id;                       // Object id.
    public final String name;                  // Object name.
    public final String type;                  // Object type (often used like an entity class).
    public final float x;                      // X in pixels (Tiled space).
    public final float y;                      // Y in pixels (Tiled space).
    public final float width;                  // Width in pixels.
    public final float height;                 // Height in pixels.
    public final float rotation;               // Rotation degrees.
    public final boolean visible;              // Visible.
    public final int gid;                      // If this object is a tile object, gid != 0.
    public final Map<String, String> properties;// Properties.
    public final TiledShapeType tiledShapeType;                  // TiledShapeType type.
    public final float[] points;               // For polygon/polyline: x0,y0,x1,y1...
    public final String text;                  // For text objects.

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