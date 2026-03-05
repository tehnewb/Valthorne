package valthorne.graphics.map.tiled;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

public class TiledTileMapLayer extends MapLayer {
    public final int width;                     // Width in tiles (finite layers).
    public final int height;                    // Height in tiles (finite layers).
    public final int[] gids;                    // Tile globalTileIDs (finite only).
    public final boolean infinite;              // True if chunks are used.
    public final Map<Long, MapChunk> chunks;       // MapChunk data (infinite only).

    public TiledTileMapLayer(String name, int width, int height, boolean visible, float opacity, float offsetX, float offsetY, Map<String, String> properties, int[] gids, boolean infinite, Map<Long, MapChunk> chunks) {
        super(name, visible, opacity, offsetX, offsetY, properties);
        this.width = width;
        this.height = height;
        this.gids = gids;
        this.infinite = infinite;
        this.chunks = (chunks != null) ? chunks : new HashMap<>();
    }

    public static TiledTileMapLayer load(Path tmxPath, XMLStreamReader r, int mapTileWidth, int mapTileHeight) throws Exception {
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
                                int[] cgids = decodeLayerData(chunkText, encoding, compression, cw * ch);
                                MapChunk c = new MapChunk(cx, cy, cw, ch, cgids);
                                chunks.put(packChunkKey(cx, cy), c);
                            } else {
                                TiledXML.skipElement(r);
                            }
                        } else if (ev2 == XMLStreamConstants.CHARACTERS || ev2 == XMLStreamConstants.CDATA) {
                            if (infinite) {
                                TiledXML.skipElement(r);
                            } else {
                                String text = readCollectedTextInsideJustClosedData(r);
                                gids = decodeLayerData(text, encoding, compression, width * height);
                            }
                            break;
                        } else if (ev2 == XMLStreamConstants.END_ELEMENT) {
                            if ("data".equals(r.getLocalName())) {
                                if (!infinite) {
                                    String text = readCollectedTextInsideJustClosedData(r);
                                }
                                break;
                            }
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
            gids = secondPassDecodeFiniteLayerData(tmxPath, name, width, height);
        }

        return new TiledTileMapLayer(name, width, height, visible, opacity, offsetX, offsetY, props, gids, infinite, chunks);
    }

    private static String readCollectedTextInsideJustClosedData(XMLStreamReader r) {
        return "";
    }

    private static int[] decodeLayerData(String text, String encoding, String compression, int expectedCount) throws IOException {
        if (text == null) return new int[Math.max(0, expectedCount)];
        String trimmed = text.trim();
        if (trimmed.isEmpty()) return new int[Math.max(0, expectedCount)];

        if (encoding == null || encoding.isBlank() || "xml".equalsIgnoreCase(encoding)) {
            throw new IllegalStateException("TMX layer encoding XML is not implemented. Use CSV or Base64.");
        }

        if ("csv".equalsIgnoreCase(encoding)) {
            return decodeCsv(trimmed, expectedCount);
        }

        if ("base64".equalsIgnoreCase(encoding)) {
            byte[] raw = Base64.getMimeDecoder().decode(trimmed);

            InputStream in = new ByteArrayInputStream(raw);
            if (compression != null && !compression.isBlank()) {
                if ("gzip".equalsIgnoreCase(compression)) {
                    in = new GZIPInputStream(in);
                } else if ("zlib".equalsIgnoreCase(compression)) {
                    in = new InflaterInputStream(in);
                } else {
                    throw new IllegalStateException("Unsupported TMX compression: " + compression);
                }
            }

            byte[] bytes = readAllBytes(in);
            return decodeLittleEndianU32(bytes, expectedCount);
        }

        throw new IllegalStateException("Unsupported TMX encoding: " + encoding);
    }


    private static int[] secondPassDecodeFiniteLayerData(Path tmxPath, String layerName, int w, int h) throws Exception {
        try (InputStream in = Files.newInputStream(tmxPath)) {
            XMLStreamReader r = TiledXML.getXMLFactory().createXMLStreamReader(in);
            TiledXML.moveToStart(r, "map");

            while (r.hasNext()) {
                int ev = r.next();
                if (ev == XMLStreamConstants.START_ELEMENT && "layer".equals(r.getLocalName())) {
                    String name = TiledXML.readAttribute(r, "name", "");
                    int lw = TiledXML.readInteger(r, "width", 0);
                    int lh = TiledXML.readInteger(r, "height", 0);

                    if (!Objects.equals(name, layerName)) {
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
                            return decodeLayerData(text, encoding, compression, expected);
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

    private static byte[] readAllBytes(InputStream in) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream(4096);
        byte[] buf = new byte[8192];
        int read;
        while ((read = in.read(buf)) != -1) bos.write(buf, 0, read);
        return bos.toByteArray();
    }

    private static int[] decodeLittleEndianU32(byte[] bytes, int expectedCount) {
        int count = (bytes.length / 4);
        int n = expectedCount > 0 ? Math.min(expectedCount, count) : count;

        int[] out = new int[n];
        ByteBuffer bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < n; i++) {
            out[i] = bb.getInt();
        }
        return out;
    }

    private static int[] decodeCsv(String csv, int expectedCount) {
        String[] parts = csv.split("[,\\s]+");
        int n = expectedCount > 0 ? expectedCount : parts.length;
        int[] out = new int[n];

        int i = 0;
        for (; i < parts.length && i < n; i++) {
            String p = parts[i].trim();
            if (p.isEmpty()) {
                out[i] = 0;
                continue;
            }
            long v = Long.parseLong(p);
            out[i] = (int) v;
        }
        for (; i < n; i++) out[i] = 0;
        return out;
    }

    private static long packChunkKey(int x, int y) {
        return (((long) x) << 32) ^ (y & 0xFFFF_FFFFL);
    }
}