package valthorne.graphics.map.tiled;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents a tile-based layer in a tiled map, extending from the base MapLayer class.
 * This layer contains information about tile arrangement, dimensions, tile IDs,
 * and whether the map uses infinite tiling.
 */
public class TiledTileMapLayer extends MapLayer {
    private final int width;
    private final int height;
    private final int[] gids;
    private final boolean infinite;
    private final Map<Long, MapChunk> chunks;

    /**
     * Creates a new instance of the TiledTileMapLayer class, representing a layer in a tiled map.
     *
     * @param name       the name of the layer.
     * @param width      the width of the layer in tiles.
     * @param height     the height of the layer in tiles.
     * @param visible    a boolean indicating whether the layer is visible.
     * @param opacity    the opacity of the layer, specified as a float value between 0 and 1.
     * @param offsetX    the horizontal offset of the layer in pixels.
     * @param offsetY    the vertical offset of the layer in pixels.
     * @param properties a map containing custom properties associated with the layer.
     * @param gids       a one-dimensional array of global tile IDs representing the layer's tiles.
     * @param infinite   a boolean indicating whether the layer is an infinite layer (supports chunks).
     * @param chunks     a map associating chunk keys (packed long values) to {@code MapChunk} objects.
     *                   If null, an empty map is assigned.
     */
    public TiledTileMapLayer(String name, int width, int height, boolean visible, float opacity, float offsetX, float offsetY, Map<String, String> properties, int[] gids, boolean infinite, Map<Long, MapChunk> chunks) {
        super(name, visible, opacity, offsetX, offsetY, properties);
        this.width = width;
        this.height = height;
        this.gids = gids;
        this.infinite = infinite;
        this.chunks = (chunks != null) ? chunks : new HashMap<>();
    }

    /**
     * Loads a TiledTileMapLayer from the provided TMX bytes and XML stream reader.
     *
     * @param tmxBytes the byte array containing the TMX file data.
     * @param r        the XMLStreamReader used to parse the TMX file.
     * @return an instance of TiledTileMapLayer, representing the layer described in the TMX data.
     * @throws Exception if an error occurs during the reading or parsing process.
     */
    public static TiledTileMapLayer load(byte[] tmxBytes, XMLStreamReader r) throws Exception {
        String name = TiledXML.readAttribute(r, "name", "");
        int width = TiledXML.readInteger(r, "width", 0);
        int height = TiledXML.readInteger(r, "height", 0);
        boolean visible = TiledXML.readInteger(r, "visible", 1) == 1;
        float opacity = TiledXML.readFloat(r, "opacity", 1f);
        float offsetX = TiledXML.readFloat(r, "offsetx", 0f);
        float offsetY = TiledXML.readFloat(r, "offsety", 0f);

        Map<String, String> props = new HashMap<>();

        int[] gids = null;
        boolean infinite = false;
        Map<Long, MapChunk> chunks = new HashMap<>();

        while (r.hasNext()) {
            int ev = r.next();
            if (ev == XMLStreamConstants.START_ELEMENT) {
                String tag = r.getLocalName();

                if ("properties".equals(tag)) {
                    props.putAll(TiledXML.readProperties(r));
                } else if ("data".equals(tag)) {
                    String encoding = TiledXML.readAttribute(r, "encoding", null);
                    String compression = TiledXML.readAttribute(r, "compression", null);

                    while (r.hasNext()) {
                        int ev2 = r.next();
                        if (ev2 == XMLStreamConstants.START_ELEMENT) {
                            if ("chunk".equals(r.getLocalName())) {
                                infinite = true;
                                int cx = TiledXML.readInteger(r, "x", 0);
                                int cy = TiledXML.readInteger(r, "y", 0);
                                int cw = TiledXML.readInteger(r, "width", 0);
                                int ch = TiledXML.readInteger(r, "height", 0);

                                String chunkText = TiledXML.readElementText(r, "chunk");
                                int[] cgids = TiledDecoding.decodeLayerData(chunkText, encoding, compression, cw * ch);
                                MapChunk c = new MapChunk(cx, cy, cw, ch, cgids);
                                chunks.put(packChunkKey(cx, cy), c);
                            } else {
                                TiledXML.skipElement(r);
                            }
                        } else if (ev2 == XMLStreamConstants.END_ELEMENT) {
                            if ("data".equals(r.getLocalName())) break;
                        }
                    }
                } else {
                    TiledXML.skipElement(r);
                }

            } else if (ev == XMLStreamConstants.END_ELEMENT) {
                if ("layer".equals(r.getLocalName())) break;
            }
        }

        if (!infinite) {
            gids = secondPassDecodeFiniteLayerData(tmxBytes, name, width, height);
        }

        return new TiledTileMapLayer(name, width, height, visible, opacity, offsetX, offsetY, props, gids, infinite, chunks);
    }

    /**
     * Decodes the data for a specific finite layer from a byte array, based on its name.
     * This method performs a second pass to parse and decode the XML representation
     * of the layer data, including attributes like encoding and compression.
     *
     * @param data       the byte array containing the XML data of the map.
     * @param layerName  the name of the layer to decode.
     * @param w          the width of the layer in tiles.
     * @param h          the height of the layer in tiles.
     * @return an integer array representing the decoded tile data of the specified layer.
     *         If the layer is not found, a default array of size w * h is returned.
     * @throws Exception if an error occurs during XML parsing or data decoding.
     */
    private static int[] secondPassDecodeFiniteLayerData(byte[] data, String layerName, int w, int h) throws Exception {
        try (InputStream in = new ByteArrayInputStream(data)) {
            XMLStreamReader r = TiledXML.getXMLFactory().createXMLStreamReader(in);
            TiledXML.moveToStart(r, "map");

            while (r.hasNext()) {
                int ev = r.next();
                if (ev == XMLStreamConstants.START_ELEMENT && "layer".equals(r.getLocalName())) {
                    String name = TiledXML.readAttribute(r, "name", "");
                    int lw = TiledXML.readInteger(r, "width", 0);
                    int lh = TiledXML.readInteger(r, "height", 0);

                    if (!java.util.Objects.equals(name, layerName)) {
                        TiledXML.skipElement(r);
                        continue;
                    }

                    while (r.hasNext()) {
                        int ev2 = r.next();
                        if (ev2 == XMLStreamConstants.START_ELEMENT && "data".equals(r.getLocalName())) {
                            String encoding = TiledXML.readAttribute(r, "encoding", null);
                            String compression = TiledXML.readAttribute(r, "compression", null);

                            String text = TiledXML.readElementText(r, "data");
                            int expected = Math.max(0, lw * lh);
                            if (expected == 0) expected = Math.max(0, w * h);
                            return TiledDecoding.decodeLayerData(text, encoding, compression, expected);
                        } else if (ev2 == XMLStreamConstants.END_ELEMENT && "layer".equals(r.getLocalName())) {
                            break;
                        } else if (ev2 == XMLStreamConstants.START_ELEMENT) {
                            TiledXML.skipElement(r);
                        }
                    }
                }
            }
        }

        return new int[Math.max(0, w * h)];
    }

    private static long packChunkKey(int x, int y) {
        return (((long) x) << 32) ^ (y & 0xFFFF_FFFFL);
    }

    /**
     * Retrieves the width of the layer in tiles.
     *
     * @return the width of the layer as an integer.
     */
    public int getWidth() {
        return width;
    }

    /**
     * Retrieves the height of the layer in tiles.
     *
     * @return the height of the layer as an integer.
     */
    public int getHeight() {
        return height;
    }

    /**
     * Retrieves the array of global tile IDs (gids) representing the tiles in this layer.
     *
     * @return an integer array of global tile IDs, where each ID corresponds to a tile in the layer.
     */
    public int[] getGids() {
        return gids;
    }

    /**
     * Determines if the layer is infinite, meaning it supports chunked maps rather
     * than having a fixed width and height.
     *
     * @return true if the layer is infinite, false otherwise.
     */
    public boolean isInfinite() {
        return infinite;
    }

    /**
     * Retrieves the chunks associated with this layer. Each chunk is represented as
     * a {@code MapChunk} object and is associated with a unique key of type {@code Long}.
     *
     * @return a map where keys are {@code Long} values representing chunk identifiers
     *         and values are {@code MapChunk} objects representing the corresponding
     *         chunk data.
     */
    public Map<Long, MapChunk> getChunks() {
        return chunks;
    }
}