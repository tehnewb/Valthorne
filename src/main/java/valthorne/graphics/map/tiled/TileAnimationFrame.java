package valthorne.graphics.map.tiled;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a single frame of a tile animation within a tilemap.
 * Each frame is defined by a tile ID and its duration in milliseconds.
 * Used primarily to handle the sequence of animations for tiles.
 *
 * @param tileID   The ID of the tile for this frame.
 * @param duration The duration of this frame in milliseconds.
 */
public record TileAnimationFrame(int tileID, int duration) {

    /**
     * Loads a list of tile animation frames from an XML element. This function
     * parses the provided XMLStreamReader, extracts frame data (tile ID and duration),
     * and builds a list of {@code TileAnimationFrame} objects. The method stops when
     * the "animation" end element is encountered.
     *
     * @param r the {@code XMLStreamReader} to read XML data from
     * @return a {@code List} of {@code TileAnimationFrame} representing the animation frames
     * @throws Exception if an error occurs while reading or processing the XML data
     */
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