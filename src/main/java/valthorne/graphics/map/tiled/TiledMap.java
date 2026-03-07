package valthorne.graphics.map.tiled;

import valthorne.graphics.texture.TextureBatch;
import valthorne.graphics.texture.TextureRegion;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

/**
 * Represents a fully loaded Tiled TMX map, including map metadata, tilesets, layers,
 * properties, animation timing, and rendering helpers for tile-based layers.
 *
 * <p>
 * This class is the main entry point for loading and rendering TMX maps produced by
 * the Tiled editor. It reads the root map metadata, loads all referenced tilesets,
 * collects supported layer types, resolves properties, and provides methods to render
 * the entire map or a single named layer through your {@link TextureBatch}.
 * </p>
 *
 * <h2>What this class stores</h2>
 * <ul>
 *     <li>The map identity and layout metadata such as name, width, height, tile size, orientation, and whether the map is infinite</li>
 *     <li>All parsed map properties from the root {@code <map>} element</li>
 *     <li>All parsed tilesets sorted by their first global tile ID so tile lookup remains efficient and deterministic</li>
 *     <li>All supported layers, including tile layers, object layers, and image layers</li>
 *     <li>An animation timer used to resolve animated Tiled tiles during rendering</li>
 * </ul>
 *
 * <h2>Rendering behavior</h2>
 * <p>
 * Rendering focuses on {@link TiledTileMapLayer} instances. For each non-empty tile, the map resolves
 * the owning {@link TileSet}, converts the global tile ID into a local tile ID, resolves animation
 * frames if the tile is animated, fetches the proper {@link TextureRegion}, computes the final world
 * position, and submits it to the supplied {@link TextureBatch}.
 * </p>
 *
 * <p>
 * Finite maps are rendered using a top-origin interpretation from Tiled and converted into your
 * bottom-left style runtime coordinates. Infinite maps instead render each chunk relative to its chunk
 * offset and flip row traversal so tiles still appear in the expected world positions.
 * </p>
 *
 * <h2>Supported layer loading</h2>
 * <ul>
 *     <li>{@code <layer>} into {@link TiledTileMapLayer}</li>
 *     <li>{@code <objectgroup>} into {@link TiledObjectMapLayer}</li>
 *     <li>{@code <imagelayer>} into {@link TiledImageMapLayer}</li>
 * </ul>
 *
 * <p>
 * Unsupported tags are skipped safely during parsing so the loader can tolerate extra TMX content
 * without crashing as long as the map does not require those skipped features for rendering.
 * </p>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * TiledDependencyResolver resolver = new TiledDependencyResolver() {
 *     @Override
 *     public byte[] resolve(String basePath, String relativePath) {
 *         try {
 *             return Files.readAllBytes(Paths.get(relativePath));
 *         } catch (Exception e) {
 *             throw new RuntimeException(e);
 *         }
 *     }
 * };
 *
 * TiledMap map = TiledMap.load("assets/maps/world.tmx", resolver);
 * if (map == null) {
 *     throw new IllegalStateException("Failed to load map.");
 * }
 *
 * TextureBatch batch = new TextureBatch(8192);
 *
 * map.update(delta);
 *
 * batch.begin();
 * map.render(batch);
 * batch.end();
 *
 * map.dispose();
 * batch.dispose();
 * }</pre>
 *
 * @author Albert Beaupre
 * @since March 5th, 2026
 */
public final class TiledMap {

    /**
     * Bit flag used by Tiled inside a raw global tile ID to indicate a horizontal flip.
     */
    private static final int FLIP_H = 0x8000_0000;

    /**
     * Bit flag used by Tiled inside a raw global tile ID to indicate a vertical flip.
     */
    private static final int FLIP_V = 0x4000_0000;

    /**
     * Bit flag used by Tiled inside a raw global tile ID to indicate a diagonal flip.
     */
    private static final int FLIP_D = 0x2000_0000;

    /**
     * Bit mask used to strip flip bits from a raw Tiled global tile ID and recover the actual tile ID.
     */
    private static final int GID_MASK = 0x1FFF_FFFF;

    private final String name; // The map name read from the TMX root element.
    private final int width; // The map width in tiles for finite maps.
    private final int height; // The map height in tiles for finite maps.
    private final int tileWidth; // The width of one tile in pixels.
    private final int tileHeight; // The height of one tile in pixels.
    private final boolean infinite; // Whether the TMX map uses Tiled's infinite-map mode.
    private final String orientation; // The map orientation string such as orthogonal.
    private final Map<String, String> properties; // Root-level custom map properties.
    private final List<TileSet> tileSets; // All parsed tilesets sorted by first global tile ID.
    private final List<MapLayer> mapLayers; // All parsed map layers in source order.
    private float animationTimeSeconds; // Running animation timer used to resolve animated tiles.

    /**
     * Creates a fully initialized Tiled map instance from already parsed map data.
     *
     * <p>
     * This constructor normalizes null values into safe defaults, stores all incoming map data,
     * and sorts tilesets by {@code firstGlobalTileID}. That sorting step is important because
     * later tile lookups rely on tilesets being ordered from lowest to highest global tile range.
     * </p>
     *
     * @param name        map name, or null to use an empty name
     * @param width       map width in tiles
     * @param height      map height in tiles
     * @param tileWidth   tile width in pixels
     * @param tileHeight  tile height in pixels
     * @param infinite    true if the map is infinite
     * @param orientation orientation string, or null to default to {@code orthogonal}
     * @param properties  root map properties, or null for an empty property map
     * @param tileSets    parsed tilesets, or null for an empty tileset list
     * @param mapLayers   parsed layers, or null for an empty layer list
     */
    private TiledMap(String name, int width, int height, int tileWidth, int tileHeight, boolean infinite, String orientation, Map<String, String> properties, List<TileSet> tileSets, List<MapLayer> mapLayers) {
        this.name = name != null ? name : "";
        this.width = width;
        this.height = height;
        this.tileWidth = tileWidth;
        this.tileHeight = tileHeight;
        this.infinite = infinite;
        this.orientation = orientation != null ? orientation : "orthogonal";
        this.properties = properties != null ? properties : new HashMap<>();
        this.tileSets = tileSets != null ? tileSets : new ArrayList<>();
        this.mapLayers = mapLayers != null ? mapLayers : new ArrayList<>();
        this.tileSets.sort(Comparator.comparingInt(TileSet::getFirstGlobalTileID));
    }

    /**
     * Loads a Tiled TMX map from a file path.
     *
     * <p>
     * This is the main convenience loader for disk-based TMX files. The method reads the TMX file
     * into memory and then delegates to {@link #load(byte[], String, TiledDependencyResolver)} so the
     * parsing logic remains centralized in one place.
     * </p>
     *
     * <p>
     * If loading fails for any reason, the exception is printed and this method returns {@code null}.
     * </p>
     *
     * @param tmxFilePath path to the TMX file on disk
     * @param resolver    dependency resolver used to load external TSX files and image data
     * @return loaded map instance, or {@code null} if loading failed
     */
    public static TiledMap load(String tmxFilePath, TiledDependencyResolver resolver) {
        try {
            byte[] bytes = Files.readAllBytes(Paths.get(tmxFilePath));
            return load(bytes, tmxFilePath, resolver);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Loads a Tiled TMX map from raw TMX bytes.
     *
     * <p>
     * This method parses the TMX XML stream, reads root map attributes, collects root properties,
     * loads all tilesets, loads supported map layers, and then constructs the final {@link TiledMap}.
     * The supplied {@code tmxPath} is passed through so relative external resources can be resolved
     * correctly by the dependency resolver.
     * </p>
     *
     * <p>
     * Parsing is performed in a streaming fashion using {@link XMLStreamReader}, which keeps memory
     * usage lower than loading the full XML tree into a DOM-style structure.
     * </p>
     *
     * <p>
     * If parsing fails for any reason, the exception is printed and this method returns {@code null}.
     * </p>
     *
     * @param tmxBytes raw TMX XML bytes
     * @param tmxPath  original TMX path used as the base path for dependency resolution
     * @param resolver dependency resolver used to load tileset and image dependencies
     * @return loaded map instance, or {@code null} if parsing failed
     * @throws NullPointerException if {@code tmxBytes} or {@code resolver} is null
     */
    public static TiledMap load(byte[] tmxBytes, String tmxPath, TiledDependencyResolver resolver) {
        Objects.requireNonNull(tmxBytes, "tmxBytes");
        Objects.requireNonNull(resolver, "resolver");

        try (InputStream in = new ByteArrayInputStream(tmxBytes)) {
            XMLStreamReader reader = TiledXML.getXMLFactory().createXMLStreamReader(in);

            TiledXML.moveToStart(reader, "map");
            String orientation = TiledXML.readAttribute(reader, "orientation", "orthogonal");
            int width = TiledXML.readInteger(reader, "width", 0);
            int height = TiledXML.readInteger(reader, "height", 0);
            int tileWidth = TiledXML.readInteger(reader, "tilewidth", 0);
            int tileHeight = TiledXML.readInteger(reader, "tileheight", 0);
            boolean infinite = TiledXML.readInteger(reader, "infinite", 0) == 1;
            String name = TiledXML.readAttribute(reader, "name", "");

            Map<String, String> mapProps = new HashMap<>();
            List<TileSet> tileSets = new ArrayList<>();
            List<MapLayer> mapLayers = new ArrayList<>();

            while (reader.hasNext()) {
                int event = reader.next();

                if (event == XMLStreamConstants.START_ELEMENT) {
                    String tag = reader.getLocalName();

                    switch (tag) {
                        case "properties" -> mapProps.putAll(TiledXML.readProperties(reader));
                        case "tileset" -> tileSets.add(TileSet.load(tmxBytes, tmxPath, resolver, reader));
                        case "layer" -> mapLayers.add(TiledTileMapLayer.load(tmxBytes, reader));
                        case "objectgroup" -> mapLayers.add(TiledObjectMapLayer.load(reader));
                        case "imagelayer" ->
                                mapLayers.add(TiledImageMapLayer.load(tmxBytes, tmxPath, resolver, reader));
                        default -> TiledXML.skipElement(reader);
                    }
                } else if (event == XMLStreamConstants.END_ELEMENT) {
                    if ("map".equals(reader.getLocalName())) {
                        break;
                    }
                }
            }

            return new TiledMap(name, width, height, tileWidth, tileHeight, infinite, orientation, mapProps, tileSets, mapLayers);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Advances the internal animation timer used for animated tiles.
     *
     * <p>
     * Tiled tile animations are resolved during rendering by asking the owning tileset which local
     * tile frame should currently be displayed. This method simply accumulates elapsed time so those
     * frame calculations have a continuously advancing clock.
     * </p>
     *
     * @param delta elapsed time in seconds since the last update
     */
    public void update(float delta) {
        animationTimeSeconds += delta;
    }

    /**
     * Renders all tile layers in the map in their stored order.
     *
     * <p>
     * Only {@link TiledTileMapLayer} instances are rendered by this method. Other layer types remain
     * loaded and accessible through {@link #getLayer(String)}, but they are not submitted here.
     * </p>
     *
     * @param batch batch used to render map tiles
     * @throws NullPointerException if {@code batch} is null
     */
    public void render(TextureBatch batch) {
        Objects.requireNonNull(batch, "batch");

        for (MapLayer mapLayer : mapLayers) {
            if (mapLayer instanceof TiledTileMapLayer tileLayer) {
                renderTileLayer(batch, tileLayer);
            }
        }
    }

    /**
     * Renders a single named layer when that layer is a tile layer.
     *
     * <p>
     * This is useful when a game wants to draw only a subset of the TMX map, such as a background
     * layer, collision visualization layer, or foreground overlay layer.
     * </p>
     *
     * <p>
     * If the named layer does not exist or is not a {@link TiledTileMapLayer}, nothing is rendered.
     * </p>
     *
     * @param batch     batch used to render map tiles
     * @param layerName exact name of the layer to render
     * @throws NullPointerException if {@code batch} or {@code layerName} is null
     */
    public void renderLayer(TextureBatch batch, String layerName) {
        Objects.requireNonNull(batch, "batch");
        Objects.requireNonNull(layerName, "layerName");

        MapLayer mapLayer = getLayer(layerName);
        if (mapLayer instanceof TiledTileMapLayer tileLayer) {
            renderTileLayer(batch, tileLayer);
        }
    }

    /**
     * Renders one tile layer, handling both finite and infinite Tiled layer layouts.
     *
     * <p>
     * Invisible layers are skipped immediately. Infinite layers render each stored chunk separately
     * using chunk offsets. Finite layers render their main GID grid directly from the layer's
     * contiguous tile array.
     * </p>
     *
     * @param batch batch used to render tiles
     * @param layer tile layer to render
     */
    private void renderTileLayer(TextureBatch batch, TiledTileMapLayer layer) {
        if (!layer.visible) {
            return;
        }

        if (layer.isInfinite() && !layer.getChunks().isEmpty()) {
            for (MapChunk chunk : layer.getChunks().values()) {
                drawGrid(batch, chunk.width(), chunk.height(), chunk.globalTileIDs(), chunk.x(), chunk.y());
            }
            return;
        }

        if (layer.getGids() == null || layer.getGids().length == 0) {
            return;
        }

        drawGrid(batch, layer.getWidth(), layer.getHeight(), layer.getGids(), 0, 0);
    }

    /**
     * Draws a rectangular tile grid into the supplied batch.
     *
     * <p>
     * Each raw tile entry is decoded by masking out Tiled flip bits, finding the owning tileset,
     * converting the global tile ID into a local tileset tile ID, resolving animation, and then
     * drawing the correct texture region at the final world position.
     * </p>
     *
     * <p>
     * Finite maps use the map height to translate Tiled's row ordering into bottom-left world-space
     * coordinates. Infinite maps instead compute positions relative to the chunk offset and reverse
     * the row order within the current chunk.
     * </p>
     *
     * <p>
     * The flip flags are currently stripped from the GID so the correct base tile can be found, but
     * the rendering path shown here does not yet apply the flip transforms to the drawn region.
     * </p>
     *
     * @param batch         batch used to draw the tiles
     * @param widthTiles    width of the grid in tiles
     * @param heightTiles   height of the grid in tiles
     * @param globalTileIDs raw Tiled GID array for the grid
     * @param offsetXTiles  tile-space x offset for the grid or chunk
     * @param offsetYTiles  tile-space y offset for the grid or chunk
     */
    private void drawGrid(TextureBatch batch, int widthTiles, int heightTiles, int[] globalTileIDs, int offsetXTiles, int offsetYTiles) {
        final int tilePixelWidth = this.tileWidth;
        final int tilePixelHeight = this.tileHeight;

        for (int tileY = 0; tileY < heightTiles; tileY++) {
            for (int tileX = 0; tileX < widthTiles; tileX++) {
                int index = tileY * widthTiles + tileX;
                int raw = globalTileIDs[index];
                if (raw == 0) {
                    continue;
                }

                int gid = raw & GID_MASK;
                if (gid == 0) {
                    continue;
                }

                TileSet tileSet = findTilesetForGID(gid);
                if (tileSet == null) {
                    continue;
                }

                int localId = gid - tileSet.getFirstGlobalTileID();
                int drawLocalId = tileSet.resolveAnimatedLocalId(localId, animationTimeSeconds);

                TextureRegion region = tileSet.getRegionForLocalId(drawLocalId);
                if (region == null) {
                    continue;
                }

                float worldX = (offsetXTiles + tileX) * (float) tilePixelWidth;

                float worldY;
                if (!this.infinite && this.height > 0) {
                    int mapPixelHeight = this.height * tilePixelHeight;
                    float yTop = (offsetYTiles + tileY) * (float) tilePixelHeight;
                    worldY = (mapPixelHeight - yTop) - tilePixelHeight;
                } else {
                    worldY = (offsetYTiles + (heightTiles - 1 - tileY)) * (float) tilePixelHeight;
                }

                batch.drawRegion(region, worldX, worldY, tilePixelWidth, tilePixelHeight);
            }
        }
    }

    /**
     * Returns the first layer whose name exactly matches the supplied value.
     *
     * <p>
     * Layer lookup is performed in source order. If no layer matches, this method returns {@code null}.
     * </p>
     *
     * @param name exact layer name to search for
     * @return matching layer, or {@code null} if none exists
     * @throws NullPointerException if {@code name} is null
     */
    public MapLayer getLayer(String name) {
        Objects.requireNonNull(name, "name");
        for (MapLayer layer : mapLayers) {
            if (name.equals(layer.getName())) {
                return layer;
            }
        }
        return null;
    }

    /**
     * Finds the tileset that owns a given global tile ID.
     *
     * <p>
     * Because tilesets are sorted by {@code firstGlobalTileID}, this method walks them in order and
     * keeps the most recent tileset whose first ID does not exceed the requested GID. That last valid
     * tileset is the owner of the requested tile.
     * </p>
     *
     * @param globalTileID map-global tile ID
     * @return owning tileset, or {@code null} if none applies
     */
    public TileSet findTilesetForGID(int globalTileID) {
        TileSet best = null;
        for (TileSet tileSet : tileSets) {
            if (tileSet.getFirstGlobalTileID() <= globalTileID) {
                best = tileSet;
            } else {
                break;
            }
        }
        return best;
    }

    /**
     * Disposes textures owned by all loaded tilesets.
     *
     * <p>
     * This method attempts to release tileset textures even if one disposal fails. Any thrown error
     * during an individual tileset dispose is swallowed so disposal of later tilesets can still proceed.
     * </p>
     */
    public void dispose() {
        for (TileSet tileSet : tileSets) {
            if (tileSet != null && tileSet.getTexture() != null) {
                try {
                    tileSet.getTexture().dispose();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    /**
     * Returns the map name.
     *
     * @return map name, never null
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the map width in tiles.
     *
     * <p>
     * For infinite maps, this may be zero or may simply reflect the root TMX metadata rather than
     * the total occupied chunk size.
     * </p>
     *
     * @return map width in tiles
     */
    public int getWidth() {
        return width;
    }

    /**
     * Returns the map height in tiles.
     *
     * <p>
     * For infinite maps, this may be zero or may simply reflect the root TMX metadata rather than
     * the total occupied chunk size.
     * </p>
     *
     * @return map height in tiles
     */
    public int getHeight() {
        return height;
    }

    /**
     * Returns the width of one tile in pixels.
     *
     * @return tile width in pixels
     */
    public int getTileWidth() {
        return tileWidth;
    }

    /**
     * Returns the height of one tile in pixels.
     *
     * @return tile height in pixels
     */
    public int getTileHeight() {
        return tileHeight;
    }

    /**
     * Returns whether this map uses Tiled's infinite mode.
     *
     * @return true if the map is infinite
     */
    public boolean isInfinite() {
        return infinite;
    }

    /**
     * Returns the map orientation string.
     *
     * @return orientation string, never null
     */
    public String getOrientation() {
        return orientation;
    }

    /**
     * Returns the root property map.
     *
     * @return map properties
     */
    public Map<String, String> getProperties() {
        return properties;
    }

    /**
     * Returns the loaded tilesets.
     *
     * @return tileset list
     */
    public List<TileSet> getTileSets() {
        return tileSets;
    }

    /**
     * Returns the loaded map layers.
     *
     * @return layer list
     */
    public List<MapLayer> getMapLayers() {
        return mapLayers;
    }

    /**
     * Returns the current accumulated animation time.
     *
     * @return animation timer in seconds
     */
    public float getAnimationTimeSeconds() {
        return animationTimeSeconds;
    }
}