package valthorne.graphics.map.tiled;

import valthorne.graphics.texture.TextureBatch;
import valthorne.graphics.texture.TextureRegion;

import java.util.*;

/**
 * <h1>TiledMap</h1>
 *
 * <p>
 * {@code TiledMap} is the runtime renderable representation of a map exported from the Tiled map editor.
 * It stores the basic map metadata such as dimensions, tile sizes, orientation, properties, tilesets,
 * and layers, then provides update and render methods for drawing tile layers through a
 * {@link TextureBatch}.
 * </p>
 *
 * <p>
 * This class is meant to be constructed from a {@link TiledMapData} instance, which acts as the parsed
 * intermediate data model. During construction, the map converts each {@link TileSetData} entry into a
 * runtime {@link TileSet} so the map is immediately ready for animated tile lookup and rendering.
 * </p>
 *
 * <h2>How rendering works</h2>
 * <ul>
 *     <li>The map iterates over its {@link MapLayer} list.</li>
 *     <li>Only {@link TiledTileMapLayer} instances are rendered by this class.</li>
 *     <li>Each tile GID is resolved to the correct {@link TileSet}.</li>
 *     <li>The tile's local ID is optionally remapped through animation timing.</li>
 *     <li>The final {@link TextureRegion} is drawn through the provided {@link TextureBatch}.</li>
 * </ul>
 *
 * <h2>Coordinate behavior</h2>
 * <p>
 * For finite maps, Tiled stores rows from top to bottom, but your renderer commonly works in a
 * bottom-left world. Because of that, finite layers are vertically flipped during rendering so tiles
 * appear in the expected world-space positions.
 * </p>
 *
 * <p>
 * Infinite map chunks are handled differently. Chunk coordinates are already relative to the chunk
 * origin, so the runtime uses chunk-local iteration and converts those rows into bottom-left space
 * for drawing.
 * </p>
 *
 * <h2>Animation behavior</h2>
 * <p>
 * Animated tiles are driven by {@link #animationTimeSeconds}. Call {@link #update(float)} every frame
 * with delta time, then render normally. Each {@link TileSet} decides which local tile ID should be
 * displayed for the current animation time.
 * </p>
 *
 * <h2>Resource ownership</h2>
 * <p>
 * This map owns the runtime {@link TileSet} instances it creates from {@link TileSetData}. Because of
 * that, calling {@link #dispose()} will dispose any tileset textures held by those runtime tilesets.
 * This makes the map responsible for cleaning up its own texture resources when it is no longer needed.
 * </p>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * TiledMapData mapData = TiledMapLoader.load("assets/maps/world.tmx", resolver);
 * TiledMap map = new TiledMap(mapData);
 *
 * TextureBatch batch = new TextureBatch(4096);
 *
 * // Game loop
 * map.update(delta);
 *
 * batch.begin();
 * map.render(batch);
 * batch.end();
 *
 * // Render only one named layer if needed
 * batch.begin();
 * map.renderLayer(batch, "Foreground");
 * batch.end();
 *
 * map.dispose();
 * }</pre>
 *
 * @author Albert Beaupre
 * @since March 5th, 2026
 */
public final class TiledMap {

    /**
     * Bit mask used to strip Tiled's tile flip and rotation flags from a raw global tile ID.
     *
     * <p>
     * Tiled stores several transform flags inside the upper bits of a tile GID. This mask keeps only
     * the actual tile ID portion so the runtime can correctly resolve the owning tileset and tile entry.
     * </p>
     */
    private static final int GID_MASK = 0x1FFF_FFFF;

    private final String name; // The display name of the map, as defined by the parsed map data.
    private final int width; // The number of tiles horizontally in this map.
    private final int height; // The number of tiles vertically in this map.
    private final int tileWidth; // The width of one tile in pixels.
    private final int tileHeight; // The height of one tile in pixels.
    private final boolean infinite; // Whether this map uses Tiled's infinite-map chunk format.
    private final String orientation; // The declared map orientation, usually orthogonal.
    private final Map<String, String> properties; // Arbitrary custom properties attached to the map.
    private final List<TileSet> tileSets; // Runtime tilesets used for tile resolution and rendering.
    private final List<MapLayer> mapLayers; // All layers contained in the map, including non-tile layers.
    private float animationTimeSeconds; // Accumulated animation time used when resolving animated tiles.

    /**
     * Creates a runtime map from parsed {@link TiledMapData}.
     *
     * <p>
     * This constructor copies the high-level map metadata and converts every {@link TileSetData}
     * into a runtime {@link TileSet}. It also sorts the resulting tilesets by first global tile ID
     * so later GID lookup remains predictable and efficient.
     * </p>
     *
     * <p>
     * Null collections from the input data are replaced with empty mutable collections so the runtime
     * instance always has safe containers available.
     * </p>
     *
     * @param data the parsed Tiled map data used to build this runtime map
     * @throws NullPointerException if {@code data} is null
     */
    public TiledMap(TiledMapData data) {
        Objects.requireNonNull(data, "data");
        this.name = data.getName();
        this.width = data.getWidth();
        this.height = data.getHeight();
        this.tileWidth = data.getTileWidth();
        this.tileHeight = data.getTileHeight();
        this.infinite = data.isInfinite();
        this.orientation = data.getOrientation();
        this.properties = data.getProperties() != null ? data.getProperties() : new HashMap<>();
        this.mapLayers = data.getMapLayers() != null ? data.getMapLayers() : new ArrayList<>();
        this.tileSets = new ArrayList<>();

        for (TileSetData tileSetData : data.getTileSetData()) {
            this.tileSets.add(TileSet.fromData(tileSetData));
        }

        this.tileSets.sort(Comparator.comparingInt(TileSet::getFirstGlobalTileID));
    }

    /**
     * Advances the internal animation timer used for animated tile rendering.
     *
     * <p>
     * This method should typically be called once per frame using the frame delta time in seconds.
     * The accumulated time is later passed to each {@link TileSet} when choosing which frame of an
     * animated tile should currently be displayed.
     * </p>
     *
     * @param delta the elapsed time in seconds since the previous update call
     */
    public void update(float delta) {
        animationTimeSeconds += delta;
    }

    /**
     * Renders every visible tile layer in this map.
     *
     * <p>
     * Only {@link TiledTileMapLayer} instances are drawn here. Other layer types remain stored in
     * {@link #mapLayers}, but they are ignored by this method unless you add separate runtime
     * rendering paths for them elsewhere.
     * </p>
     *
     * @param batch the texture batch used to queue and draw tile regions
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
     * Renders a single named tile layer if it exists and is a {@link TiledTileMapLayer}.
     *
     * <p>
     * This method is useful when you want to draw only specific layers, such as background,
     * collision-preview, or foreground layers, instead of rendering the entire map at once.
     * </p>
     *
     * @param batch     the texture batch used to queue and draw tile regions
     * @param layerName the exact layer name to render
     * @throws NullPointerException if {@code batch} or {@code layerName} is null
     */
    public void renderLayer(TextureBatch batch, String layerName) {
        Objects.requireNonNull(batch, "batch");
        Objects.requireNonNull(layerName, "layerName");

        MapLayer layer = getLayer(layerName);
        if (layer instanceof TiledTileMapLayer tileLayer) {
            renderTileLayer(batch, tileLayer);
        }
    }

    /**
     * Renders one tile layer, handling either chunked infinite maps or regular finite tile grids.
     *
     * <p>
     * Hidden layers are skipped immediately. Infinite layers are rendered chunk-by-chunk using the
     * chunk origin offsets. Finite layers are rendered from their main GID array starting at
     * tile origin {@code (0, 0)}.
     * </p>
     *
     * @param batch the batch used for tile rendering
     * @param layer the tile layer to render
     */
    private void renderTileLayer(TextureBatch batch, TiledTileMapLayer layer) {
        if (!layer.isVisible()) {
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
     * Draws a rectangular grid of tile GIDs using the provided tile dimensions and tile offsets.
     *
     * <p>
     * Each raw tile entry is decoded by stripping transform bits, resolving the owning
     * {@link TileSet}, resolving animation state, then drawing the resulting {@link TextureRegion}
     * through the provided {@link TextureBatch}.
     * </p>
     *
     * <p>
     * Finite maps use a top-to-bottom source row order from Tiled, so they are vertically flipped
     * into bottom-left world space. Infinite chunk rendering uses chunk-relative row conversion.
     * </p>
     *
     * @param batch         the texture batch used to draw tiles
     * @param widthTiles    the width of the tile grid in tiles
     * @param heightTiles   the height of the tile grid in tiles
     * @param globalTileIDs the raw global tile ID array for the grid
     * @param offsetXTiles  the horizontal tile offset of the grid origin
     * @param offsetYTiles  the vertical tile offset of the grid origin
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

                batch.draw(region, worldX, worldY, tilePixelWidth, tilePixelHeight);
            }
        }
    }

    /**
     * Returns the name assigned to this map.
     *
     * @return the map name
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the width of the map in tiles.
     *
     * @return the horizontal tile count
     */
    public int getWidth() {
        return width;
    }

    /**
     * Returns the height of the map in tiles.
     *
     * @return the vertical tile count
     */
    public int getHeight() {
        return height;
    }

    /**
     * Returns the width of one tile in pixels.
     *
     * @return the tile width in pixels
     */
    public int getTileWidth() {
        return tileWidth;
    }

    /**
     * Returns the height of one tile in pixels.
     *
     * @return the tile height in pixels
     */
    public int getTileHeight() {
        return tileHeight;
    }

    /**
     * Returns whether this map uses infinite chunk-based layout.
     *
     * @return true if the map is infinite, otherwise false
     */
    public boolean isInfinite() {
        return infinite;
    }

    /**
     * Returns the orientation string defined by the Tiled map.
     *
     * <p>
     * Common values include {@code orthogonal}, {@code isometric}, and related layout types,
     * though this runtime class currently renders as an orthogonal grid-oriented implementation.
     * </p>
     *
     * @return the orientation string
     */
    public String getOrientation() {
        return orientation;
    }

    /**
     * Returns the custom map properties.
     *
     * <p>
     * The returned map is the runtime map's stored property map, not a defensive copy.
     * </p>
     *
     * @return the map properties
     */
    public Map<String, String> getProperties() {
        return properties;
    }

    /**
     * Returns the runtime tilesets used by this map.
     *
     * <p>
     * The returned list is the internal tileset list used during rendering.
     * </p>
     *
     * @return the runtime tilesets
     */
    public List<TileSet> getTileSets() {
        return tileSets;
    }

    /**
     * Returns every layer stored in this map.
     *
     * <p>
     * The returned list may contain tile layers, object layers, image layers, or any other
     * layer type represented by your runtime model.
     * </p>
     *
     * @return the map layer list
     */
    public List<MapLayer> getMapLayers() {
        return mapLayers;
    }

    /**
     * Returns the layer at the requested index.
     *
     * <p>
     * This method delegates directly to the internal layer list and therefore follows the same
     * bounds behavior as {@link List#get(int)}.
     * </p>
     *
     * @param index the layer index
     * @return the layer at the requested index
     */
    public MapLayer getLayer(int index) {
        return mapLayers.get(index);
    }

    /**
     * Finds a layer by its exact name.
     *
     * <p>
     * This method returns the first matching layer, or {@code null} when no such layer exists.
     * </p>
     *
     * @param name the exact layer name to search for
     * @return the matching layer, or null if none exists
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
     * Finds the best matching tileset for a global tile ID.
     *
     * <p>
     * Because the tilesets are sorted by first global tile ID, the best match is the last tileset
     * whose first global tile ID is less than or equal to the requested global tile ID.
     * </p>
     *
     * @param globalTileID the global tile ID to resolve
     * @return the owning tileset, or null if no matching tileset exists
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
     * Disposes runtime texture resources owned by this map's tilesets.
     *
     * <p>
     * This method attempts to dispose every tileset texture and suppresses individual disposal
     * failures so one bad texture does not prevent later cleanup attempts.
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
}