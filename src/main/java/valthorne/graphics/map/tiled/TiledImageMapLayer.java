package valthorne.graphics.map.tiled;

import valthorne.graphics.texture.Texture;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class TiledImageMapLayer extends MapLayer {
    public final Texture image;

    public TiledImageMapLayer(String name, boolean visible, float opacity, float offsetX, float offsetY, Map<String, String> properties, Texture image) {
        super(name, visible, opacity, offsetX, offsetY, properties);
        this.image = image;
    }

    public static TiledImageMapLayer load(Path tmxPath, XMLStreamReader r) throws Exception {
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
                        Path p = TiledXML.resolveRelative(tmxPath, src);
                        image = new Texture(p.toString());
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