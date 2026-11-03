package valthorne.graphics.map.tiled;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import java.util.HashMap;
import java.util.Map;

/**
 * Stores a TMX tile layer as either a finite row-major ID array or a collection
 * of infinite-map chunks. Global IDs retain Tiled's flip bits; consumers decode
 * those flags when resolving tiles. Width and height are measured in tiles,
 * while inherited offsets are measured in pixels.
 * <p>The constructor retains supplied arrays and maps, and getters expose those live
 * objects. This layer does not own textures or perform rendering. Infinite layers
 * loaded from TMX use chunks and have a null finite ID array.
 *
 * @author Albert Beaupre
 */
public class TiledTileMapLayer extends MapLayer {
    private final int width; // Declared layer width in tile columns.
    private final int height; // Declared layer height in tile rows.
    private final int[] gids; // Borrowed row-major finite IDs with flip bits; null for loaded infinite layers.
    private final boolean infinite; // Whether tile contents use chunk storage.
    private final Map<Long, MapChunk> chunks; // Live chunk map keyed by packed signed tile-origin coordinates.

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
     * @param tmxBytes original TMX bytes, retained for source compatibility; decoding uses the reader
     * @param r        the XMLStreamReader used to parse the TMX file.
     * @return an instance of TiledTileMapLayer, representing the layer described in the TMX data.
     * @throws Exception if an error occurs during the reading or parsing process.
     */
    public static TiledTileMapLayer load(byte[] tmxBytes, XMLStreamReader r) throws Exception {
        /*
         * Decode each layer at its current stream position. Reopening the map by
         * layer name repeats work and cannot distinguish duplicate layer names.
         */
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
                    gids = readData(r, width, height, chunks);
                    infinite = gids == null;
                } else {
                    TiledXML.skipElement(r);
                }

            } else if (ev == XMLStreamConstants.END_ELEMENT) {
                if ("layer".equals(r.getLocalName())) break;
            }
        }

        if (!infinite && gids == null) gids = new int[Math.max(0, width * height)];

        return new TiledTileMapLayer(name, width, height, visible, opacity, offsetX, offsetY, props, gids, infinite, chunks);
    }

    /**
     * Consumes the current data element, decoding finite text or retaining chunks.
     * Chunk payloads are consumed separately so surrounding whitespace never
     * becomes part of an infinite layer's tile data. The reader ends on data's
     * closing tag; the enclosing layer loader continues from that position.
     *
     * @param reader stream positioned on the layer's data start element
     * @param width finite layer width in tiles
     * @param height finite layer height in tiles
     * @param chunks destination for decoded infinite-map chunks
     * @return finite global IDs, or null when chunks define an infinite layer
     * @throws Exception if XML traversal or tile payload decoding fails
     */
    private static int[] readData(XMLStreamReader reader, int width, int height, Map<Long, MapChunk> chunks) throws Exception {
        /*
         * One forward pass handles both storage forms without a second parser
         * or dependence on a layer's nonunique display name.
         */
        String encoding = TiledXML.readAttribute(reader, "encoding", null);
        String compression = TiledXML.readAttribute(reader, "compression", null);
        StringBuilder text = new StringBuilder();
        while (reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.END_ELEMENT && "data".equals(reader.getLocalName())) break;
            if (event == XMLStreamConstants.CHARACTERS || event == XMLStreamConstants.CDATA || event == XMLStreamConstants.SPACE) {
                text.append(reader.getText());
                continue;
            }
            if (event != XMLStreamConstants.START_ELEMENT) continue;
            if (!"chunk".equals(reader.getLocalName())) {
                TiledXML.skipElement(reader);
                continue;
            }
            int x = TiledXML.readInteger(reader, "x", 0);
            int y = TiledXML.readInteger(reader, "y", 0);
            int chunkWidth = TiledXML.readInteger(reader, "width", 0);
            int chunkHeight = TiledXML.readInteger(reader, "height", 0);
            String payload = TiledXML.readElementText(reader, "chunk");
            int[] ids = TiledDecoding.decodeLayerData(payload, encoding, compression, chunkWidth * chunkHeight);
            chunks.put(packChunkKey(x, y), new MapChunk(x, y, chunkWidth, chunkHeight, ids));
        }
        return chunks.isEmpty() ? TiledDecoding.decodeLayerData(text.toString(), encoding, compression, Math.max(0, width * height)) : null;
    }

    /**
     * Combines signed chunk-origin tile coordinates into a collision-free 64-bit key.
     * The x bit pattern occupies the high half and y occupies the low half; negative
     * coordinates remain distinct from positive coordinates.
     *
     * @param x chunk origin in tile columns
     *
     * @param y chunk origin in tile rows
     * @return packed coordinate key
     */
    private static long packChunkKey(int x, int y) {
        /*
         * Preserve both signed coordinate bit patterns in separate halves.
         */
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
     * Exposes the finite layer's row-major ID storage, including encoded flip flags.
     * The array is borrowed directly from construction and is not copied; callers
     * that modify it change the layer's data.
     *
     * @return live finite ID array, or null for an infinite layer loaded from TMX
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
     * Exposes the live map of chunk origins to chunk data. Keys contain signed x in
     * the high 32 bits and signed y in the low 32 bits. Mutations affect this layer;
     * the map is not a snapshot and no synchronization is supplied.
     *
     * @return mutable chunk map, normally empty for finite layers
     */
    public Map<Long, MapChunk> getChunks() {
        return chunks;
    }
}
