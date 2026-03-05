package valthorne.graphics.map.tiled;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record TileDefinition(int id, Map<String, String> properties, List<TileAnimationFrame> animation) {
    public TileDefinition {
        properties = (properties != null) ? properties : new HashMap<>();
    }

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