package valthorne.graphics.map.tiled;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TiledObjectMapLayer extends MapLayer {
    public final List<TiledObject> objects;

    public TiledObjectMapLayer(String name, boolean visible, float opacity, float offsetX, float offsetY, Map<String, String> properties, List<TiledObject> objects) {
        super(name, visible, opacity, offsetX, offsetY, properties);
        this.objects = (objects != null) ? objects : new ArrayList<>();
    }

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
}