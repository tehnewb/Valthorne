package valthorne.graphics.map.tiled;

import valthorne.graphics.texture.TextureBatch;
import valthorne.graphics.texture.TextureRegion;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public final class TiledMap {

    private static final int FLIP_H = 0x8000_0000;
    private static final int FLIP_V = 0x4000_0000;
    private static final int FLIP_D = 0x2000_0000;
    private static final int GID_MASK = 0x1FFF_FFFF;

    private final Path tmxPath;
    private final String name;
    private final int width;
    private final int height;
    private final int tileWidth;
    private final int tileHeight;
    private final boolean infinite;
    private final String orientation;
    private final Map<String, String> properties;

    private final List<TileSet> tileSets;
    private final List<MapLayer> mapLayers;
    private float animationTimeSeconds;

    private TiledMap(Path tmxPath, String name, int width, int height, int tileWidth, int tileHeight, boolean infinite, String orientation, Map<String, String> properties, List<TileSet> tileSets, List<MapLayer> mapLayers) {
        this.tmxPath = Objects.requireNonNull(tmxPath, "tmxPath");
        this.name = (name != null) ? name : "";
        this.width = width;
        this.height = height;
        this.tileWidth = tileWidth;
        this.tileHeight = tileHeight;
        this.infinite = infinite;
        this.orientation = (orientation != null) ? orientation : "orthogonal";
        this.properties = (properties != null) ? properties : new HashMap<>();
        this.tileSets = (tileSets != null) ? tileSets : new ArrayList<>();
        this.mapLayers = (mapLayers != null) ? mapLayers : new ArrayList<>();
        this.tileSets.sort(Comparator.comparingInt(ts -> ts.firstGlobalTileID));
    }

    public static TiledMap load(String tmxFilePath) {
        Objects.requireNonNull(tmxFilePath, "tmxFilePath");
        return load(Paths.get(tmxFilePath));
    }

    public static TiledMap load(Path tmxPath) {
        Objects.requireNonNull(tmxPath, "tmxPath");

        try (InputStream in = Files.newInputStream(tmxPath)) {
            XMLStreamReader r = TiledXML.getXMLFactory().createXMLStreamReader(in);

            TiledXML.moveToStart(r, "map");
            String orientation = TiledXML.readAttribute(r, "orientation", "orthogonal");
            int width = TiledXML.readInteger(r, "width", 0);
            int height = TiledXML.readInteger(r, "height", 0);
            int tileWidth = TiledXML.readInteger(r, "tilewidth", 0);
            int tileHeight = TiledXML.readInteger(r, "tileheight", 0);
            boolean infinite = TiledXML.readInteger(r, "infinite", 0) == 1;
            String name = TiledXML.readAttribute(r, "name", "");

            Map<String, String> mapProps = new HashMap<>();
            List<TileSet> tileSets = new ArrayList<>();
            List<MapLayer> mapLayers = new ArrayList<>();

            while (r.hasNext()) {
                int ev = r.next();
                if (ev == XMLStreamConstants.START_ELEMENT) {
                    String tag = r.getLocalName();

                    switch (tag) {
                        case "properties" -> mapProps.putAll(TiledXML.readProperties(r));
                        case "tileset" -> tileSets.add(TileSet.readTileset(tmxPath, r));
                        case "layer" -> mapLayers.add(TiledTileMapLayer.load(tmxPath, r, tileWidth, tileHeight));
                        case "objectgroup" -> mapLayers.add(TiledObjectMapLayer.load(r));
                        case "imagelayer" -> mapLayers.add(TiledImageMapLayer.load(tmxPath, r));
                        case null, default -> TiledXML.skipElement(r);
                    }
                } else if (ev == XMLStreamConstants.END_ELEMENT) {
                    if ("map".equals(r.getLocalName())) break;
                }
            }

            return new TiledMap(tmxPath, name, width, height, tileWidth, tileHeight, infinite, orientation, mapProps, tileSets, mapLayers);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load TMX: " + tmxPath + " (" + e.getMessage() + ")", e);
        }
    }

    public void update(float delta) {
        animationTimeSeconds += delta;
    }

    public void render(TextureBatch batch) {
        Objects.requireNonNull(batch, "batch");

        for (MapLayer mapLayer : mapLayers) {
            if (mapLayer instanceof TiledTileMapLayer tl) {
                renderTileLayer(batch, tl);
            }
        }
    }

    public void renderLayer(TextureBatch batch, String layerName) {
        Objects.requireNonNull(batch, "batch");
        Objects.requireNonNull(layerName, "layerName");

        MapLayer mapLayer = getLayer(layerName);
        if (mapLayer instanceof TiledTileMapLayer tl) {
            renderTileLayer(batch, tl);
        }
    }

    private void renderTileLayer(TextureBatch batch, TiledTileMapLayer layer) {
        if (!layer.visible) return;

        if (layer.infinite && !layer.chunks.isEmpty()) {
            for (MapChunk c : layer.chunks.values()) {
                drawGrid(batch, c.width(), c.height(), c.globalTileIDs(), c.x(), c.y());
            }
            return;
        }

        if (layer.gids == null || layer.gids.length == 0) return;
        drawGrid(batch, layer.width, layer.height, layer.gids, 0, 0);
    }

    private void drawGrid(TextureBatch batch, int wTiles, int hTiles, int[] globalTileIDs, int oxTiles, int oyTiles) {

        final int tw = this.tileWidth;
        final int th = this.tileHeight;

        for (int ty = 0; ty < hTiles; ty++) {
            for (int tx = 0; tx < wTiles; tx++) {
                int idx = ty * wTiles + tx;
                int raw = globalTileIDs[idx];
                if (raw == 0) continue;

                int flags = raw & (FLIP_H | FLIP_V | FLIP_D);
                int gid = raw & GID_MASK;
                if (gid == 0) continue;

                TileSet tileset = findTilesetForGID(gid);
                if (tileset == null) continue;

                int localId = gid - tileset.firstGlobalTileID;
                int drawLocalId = tileset.resolveAnimatedLocalId(localId, animationTimeSeconds);

                TextureRegion region = tileset.getRegionForLocalId(drawLocalId);
                if (region == null) continue;

                float worldX = (oxTiles + tx) * (float) tw;

                float worldY;
                if (!this.infinite && this.height > 0) {
                    int mapPixelHeight = this.height * th;
                    float yTop = (oyTiles + ty) * (float) th;
                    worldY = (mapPixelHeight - yTop) - th;
                } else {
                    worldY = (oyTiles + (hTiles - 1 - ty)) * (float) th;
                }
                batch.drawRegion(region, worldX, worldY, tw, th);
            }
        }
    }

    public String getName() {
        return name;
    }

    public int getTileWidth() {
        return tileWidth;
    }

    public int getTileHeight() {
        return tileHeight;
    }

    public boolean isInfinite() {
        return infinite;
    }

    public String getOrientation() {
        return orientation;
    }

    public Map<String, String> getProperties() {
        return Collections.unmodifiableMap(properties);
    }

    public List<MapLayer> getLayers() {
        return Collections.unmodifiableList(mapLayers);
    }

    public MapLayer getLayer(String name) {
        Objects.requireNonNull(name, "name");
        for (MapLayer l : mapLayers) {
            if (name.equals(l.getName())) return l;
        }
        return null;
    }

    public TiledObjectMapLayer getObjectLayer(String name) {
        MapLayer l = getLayer(name);
        return (l instanceof TiledObjectMapLayer ol) ? ol : null;
    }

    public List<TileSet> getTilesets() {
        return Collections.unmodifiableList(tileSets);
    }

    public TileSet findTilesetForGID(int GID) {
        TileSet best = null;
        for (TileSet ts : tileSets) {
            if (ts.firstGlobalTileID <= GID) best = ts;
            else break;
        }
        return best;
    }

    public void dispose() {
        for (TileSet ts : tileSets) {
            if (ts != null && ts.texture != null) {
                try {
                    ts.texture.dispose();
                } catch (Throwable ignored) {
                }
            }
        }
    }


}