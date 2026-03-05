package valthorne.graphics.map.tiled;

import valthorne.graphics.texture.Texture;
import valthorne.graphics.texture.TextureRegion;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class TileSet {
    public final int firstGlobalTileID;                 // Global first gid.
    public final String name;                  // TileSet name.
    public final Texture texture;              // TileSet image texture.
    public final int tileWidth;                // Tile width.
    public final int tileHeight;               // Tile height.
    public final int spacing;                  // Spacing between tiles in image.
    public final int margin;                   // Margin around tiles in image.
    public final int tileCount;                // Tile count.
    public final int columns;                  // Columns in the image.
    public final int imageWidth;               // Image width (px).
    public final int imageHeight;              // Image height (px).
    public final Map<String, String> properties;// TileSet properties.

    private final Map<Integer, TileDefinition> tileDefs;     // localId -> TileDefinition
    private final Map<Integer, TextureRegion> regionCache;   // localId -> region

    public TileSet(int firstGlobalTileID, String name, Texture texture, int tileWidth, int tileHeight, int spacing, int margin, int tileCount, int columns, int imageWidth, int imageHeight, Map<String, String> properties, Map<Integer, TileDefinition> tileDefs) {
        this.firstGlobalTileID = firstGlobalTileID;
        this.name = (name != null) ? name : "";
        this.texture = Objects.requireNonNull(texture, "texture");
        this.tileWidth = tileWidth;
        this.tileHeight = tileHeight;
        this.spacing = spacing;
        this.margin = margin;
        this.tileCount = tileCount;
        this.columns = columns;
        this.imageWidth = imageWidth;
        this.imageHeight = imageHeight;
        this.properties = (properties != null) ? properties : new HashMap<>();
        this.tileDefs = (tileDefs != null) ? tileDefs : new HashMap<>();
        this.regionCache = new HashMap<>();
    }

    /**
     * Returns a region for a local tile id (0-based within this tileset).
     */
    public TextureRegion getRegionForLocalId(int localID) {
        TextureRegion cached = regionCache.get(localID);
        if (cached != null) return cached;

        if (tileWidth <= 0 || tileHeight <= 0) return null;

        int cols = (columns > 0) ? columns : Math.max(1, imageWidth / tileWidth);
        int col = localID % cols;
        int row = localID / cols;
        int srcX = margin + col * (tileWidth + spacing);
        int srcY = imageHeight - margin - (row + 1) * tileHeight - row * spacing;

        TextureRegion region = new TextureRegion(texture, srcX, srcY, tileWidth, tileHeight);

        regionCache.put(localID, region);
        return region;
    }

    /**
     * If localId is animated, resolves the frame’s localId using timeSeconds.
     * Otherwise returns localId unchanged.
     */
    public int resolveAnimatedLocalId(int localId, float timeSeconds) {
        TileDefinition def = tileDefs.get(localId);
        if (def == null || def.animation() == null || def.animation().isEmpty()) return localId;

        int totalMs = 0;
        for (TileAnimationFrame f : def.animation()) totalMs += f.duration();
        if (totalMs <= 0) return localId;

        int tMs = (int) Math.floor((timeSeconds * 1000.0));
        int mod = tMs % totalMs;
        if (mod < 0) mod += totalMs;

        int acc = 0;
        for (TileAnimationFrame f : def.animation()) {
            acc += f.duration();
            if (mod < acc) return f.tileID();
        }
        return def.animation().getLast().tileID();
    }

    public static TileSet readTileset(Path tmxPath, XMLStreamReader r) throws Exception {
        int firstGid = TiledXML.readInteger(r, "firstgid", 1);
        String source = TiledXML.readAttribute(r, "source", null);
        if (source != null && !source.isBlank()) {
            Path tsxPath = TiledXML.resolveRelative(tmxPath, source);
            try (InputStream in = Files.newInputStream(tsxPath)) {
                XMLStreamReader tsr = TiledXML.getXMLFactory().createXMLStreamReader(in);
                TiledXML.moveToStart(tsr, "tileset");
                TileSet ts = readTilesetBody(tsxPath, tsr, firstGid);
                tsr.close();
                TiledXML.skipElement(r);
                return ts;
            }
        }
        return readTilesetBody(tmxPath, r, firstGid);
    }

    private static TileSet readTilesetBody(Path basePath, XMLStreamReader r, int firstGid) throws Exception {
        String name = TiledXML.readAttribute(r, "name", "");
        int tileWidth = TiledXML.readInteger(r, "tilewidth", 0);
        int tileHeight = TiledXML.readInteger(r, "tileheight", 0);
        int spacing = TiledXML.readInteger(r, "spacing", 0);
        int margin = TiledXML.readInteger(r, "margin", 0);
        int tileCount = TiledXML.readInteger(r, "tilecount", 0);
        int columns = TiledXML.readInteger(r, "columns", 0);

        Map<String, String> props = new HashMap<>();
        Texture texture = null;
        int imageWidth = 0, imageHeight = 0;

        Map<Integer, TileDefinition> tiles = new HashMap<>();

        while (r.hasNext()) {
            int ev = r.next();
            if (ev == XMLStreamConstants.START_ELEMENT) {
                String tag = r.getLocalName();

                switch (tag) {
                    case "properties" -> props.putAll(TiledXML.readProperties(r));
                    case "image" -> {
                        String imgSource = TiledXML.readAttribute(r, "source", null);
                        imageWidth = TiledXML.readInteger(r, "width", 0);
                        imageHeight = TiledXML.readInteger(r, "height", 0);

                        if (imgSource != null && !imgSource.isBlank()) {
                            Path imagePath = TiledXML.resolveRelative(basePath, imgSource);
                            texture = new Texture(imagePath.toString());
                        }
                        TiledXML.skipElement(r);
                    }
                    case "tile" -> {
                        int id = TiledXML.readInteger(r, "id", -1);
                        TileDefinition def = TileDefinition.load(r, id);
                        if (id >= 0) tiles.put(id, def);
                    }
                    case null, default -> TiledXML.skipElement(r);
                }
            } else if (ev == XMLStreamConstants.END_ELEMENT) {
                if ("tileset".equals(r.getLocalName())) break;
            }
        }

        if (texture == null) {
            throw new IllegalStateException("TileSet '" + name + "' is missing an <image> source.");
        }

        return new TileSet(firstGid, name, texture, tileWidth, tileHeight, spacing, margin, tileCount, columns, imageWidth, imageHeight, props, tiles);
    }

}