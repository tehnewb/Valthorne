package valthorne.graphics.map.tiled;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import java.util.ArrayList;
import java.util.List;

public record TileAnimationFrame(int tileID, int duration) {

    public static List<TileAnimationFrame> load(XMLStreamReader r) throws Exception {
        List<TileAnimationFrame> frames = new ArrayList<>();
        while (r.hasNext()) {
            int ev = r.next();
            if (ev == XMLStreamConstants.START_ELEMENT) {
                if ("frame".equals(r.getLocalName())) {
                    int tileID = TiledXML.readInteger(r, "tileid", -1);
                    int durMs = TiledXML.readInteger(r, "duration", 100);
                    frames.add(new TileAnimationFrame(tileID, Math.max(1, durMs)));
                    TiledXML.skipElement(r);
                } else {
                    TiledXML.skipElement(r);
                }
            } else if (ev == XMLStreamConstants.END_ELEMENT) {
                if ("animation".equals(r.getLocalName())) break;
            }
        }
        return frames;
    }
}