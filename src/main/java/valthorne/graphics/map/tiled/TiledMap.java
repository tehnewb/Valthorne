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
     * Renders the specified texture batch within the defined dimensions.
     *
     * @param batch the texture batch to be rendered; must not be null
     */
    public void render(TextureBatch batch) {
        Objects.requireNonNull(batch, "batch");
        render(batch, 0, 0, width - 1, height - 1);
    }

    /**
     * Renders the visible portion of the map layers within the specified tile boundaries.
     *
     * @param batch    the TextureBatch used to draw tiles, must not be null
     * @param minTileX the minimum x-coordinate of the visible tile range
     * @param minTileY the minimum y-coordinate of the visible tile range
     * @param maxTileX the maximum x-coordinate of the visible tile range
     * @param maxTileY the maximum y-coordinate of the visible tile range
     * @throws NullPointerException if the batch is null
     */
    public void render(TextureBatch batch, int minTileX, int minTileY, int maxTileX, int maxTileY) {
        Objects.requireNonNull(batch, "batch");

        for (MapLayer mapLayer : mapLayers) {
            if (mapLayer instanceof TiledTileMapLayer tileLayer) {
                renderTileLayer(batch, tileLayer, minTileX, minTileY, maxTileX, maxTileY);
            }
        }
    }

    /**
     * Renders a texture batch within a specified circular area around a given center tile.
     *
     * @param batch       The {@code TextureBatch} to render. Must not be null.
     * @param centerTileX The X-coordinate of the center tile.
     * @param centerTileY The Y-coordinate of the center tile.
     * @param radiusTiles The radius, in tiles, defining the circular area to render.
     *                    Must be a positive integer.
     * @throws NullPointerException if {@code batch} is null.
     */
    public void render(TextureBatch batch, int centerTileX, int centerTileY, int radiusTiles) {
        Objects.requireNonNull(batch, "batch");

        int minTileX = centerTileX - radiusTiles;
        int minTileY = centerTileY - radiusTiles;
        int maxTileX = centerTileX + radiusTiles;
        int maxTileY = centerTileY + radiusTiles;

        render(batch, minTileX, minTileY, maxTileX, maxTileY);
    }

    /**
     * Renders the specified layers of a tile map using the provided texture batch.
     *
     * @param batch        the {@code TextureBatch} used for rendering; must not be null
     * @param layerIndices the indices of the map layers to render; can be empty or null
     */
    public void renderLayers(TextureBatch batch, int... layerIndices) {
        Objects.requireNonNull(batch, "batch");
        if (layerIndices == null) return;

        for (int layerIndex : layerIndices) {
            MapLayer mapLayer = getLayer(layerIndex);
            if (mapLayer instanceof TiledTileMapLayer tileLayer) {
                renderTileLayer(batch, tileLayer, 0, 0, width - 1, height - 1);
            }
        }
    }

    /**
     * Renders specific layers of a tile map within the specified tile boundaries using the given texture batch.
     *
     * @param batch        the texture batch used for rendering; must not be null
     * @param minTileX     the minimum x-coordinate of the tile range to render
     * @param minTileY     the minimum y-coordinate of the tile range to render
     * @param maxTileX     the maximum x-coordinate of the tile range to render
     * @param maxTileY     the maximum y-coordinate of the tile range to render
     * @param layerIndices the indices of the layers to be rendered; can be empty or null
     */
    public void renderLayers(TextureBatch batch, int minTileX, int minTileY, int maxTileX, int maxTileY, int... layerIndices) {
        Objects.requireNonNull(batch, "batch");
        if (layerIndices == null) return;

        for (int layerIndex : layerIndices) {
            MapLayer mapLayer = getLayer(layerIndex);
            if (mapLayer instanceof TiledTileMapLayer tileLayer) {
                renderTileLayer(batch, tileLayer, minTileX, minTileY, maxTileX, maxTileY);
            }
        }
    }

    /**
     * Renders specified layers within a circular area defined by a central tile and radius.
     *
     * @param batch        the TextureBatch used for rendering; must not be null
     * @param centerTileX  the x-coordinate of the central tile
     * @param centerTileY  the y-coordinate of the central tile
     * @param radiusTiles  the radius of tiles around the center tile that defines the rendering area
     * @param layerIndices the indices of the layers to be rendered
     */
    public void renderLayers(TextureBatch batch, int centerTileX, int centerTileY, int radiusTiles, int... layerIndices) {
        Objects.requireNonNull(batch, "batch");

        int minTileX = centerTileX - radiusTiles;
        int minTileY = centerTileY - radiusTiles;
        int maxTileX = centerTileX + radiusTiles;
        int maxTileY = centerTileY + radiusTiles;

        renderLayers(batch, minTileX, minTileY, maxTileX, maxTileY, layerIndices);
    }

    /**
     * Renders specified layers using the provided texture batch.
     *
     * @param batch      the texture batch used for rendering, must not be null
     * @param layerNames the names of the layers to be rendered; can be null
     */
    public void renderLayers(TextureBatch batch, String... layerNames) {
        Objects.requireNonNull(batch, "batch");
        if (layerNames == null) return;

        for (String layerName : layerNames) {
            MapLayer mapLayer = getLayer(layerName);
            if (mapLayer instanceof TiledTileMapLayer tileLayer) {
                renderTileLayer(batch, tileLayer, 0, 0, width - 1, height - 1);
            }
        }
    }

    /**
     * Renders specified layers of a tile map within the defined tile boundaries.
     *
     * @param batch      the texture batch used for rendering; must not be null
     * @param minTileX   the minimum X-coordinate (in tiles) for rendering
     * @param minTileY   the minimum Y-coordinate (in tiles) for rendering
     * @param maxTileX   the maximum X-coordinate (in tiles) for rendering
     * @param maxTileY   the maximum Y-coordinate (in tiles) for rendering
     * @param layerNames the names of the layers to be rendered; if null, no layers are rendered
     */
    public void renderLayers(TextureBatch batch, int minTileX, int minTileY, int maxTileX, int maxTileY, String... layerNames) {
        Objects.requireNonNull(batch, "batch");
        if (layerNames == null) return;

        for (String layerName : layerNames) {
            MapLayer mapLayer = getLayer(layerName);
            if (mapLayer instanceof TiledTileMapLayer tileLayer) {
                renderTileLayer(batch, tileLayer, minTileX, minTileY, maxTileX, maxTileY);
            }
        }
    }

    /**
     * Renders multiple layers within a specified radius around a central tile.
     *
     * @param batch       the {@link TextureBatch} used to render the layers; must not be null
     * @param centerTileX the x-coordinate of the central tile
     * @param centerTileY the y-coordinate of the central tile
     * @param radiusTiles the radius in tiles around the central tile to include in rendering
     * @param layerNames  the names of the layers to be rendered
     */
    public void renderLayers(TextureBatch batch, int centerTileX, int centerTileY, int radiusTiles, String... layerNames) {
        Objects.requireNonNull(batch, "batch");

        int minTileX = centerTileX - radiusTiles;
        int minTileY = centerTileY - radiusTiles;
        int maxTileX = centerTileX + radiusTiles;
        int maxTileY = centerTileY + radiusTiles;

        renderLayers(batch, minTileX, minTileY, maxTileX, maxTileY, layerNames);
    }

    /**
     * Renders the specified tile layer using the provided texture batch within a defined tile range.
     *
     * @param batch    the {@code TextureBatch} used to render the tile graphics
     * @param layer    the {@code TiledTileMapLayer} to be rendered
     * @param minTileX the minimum x coordinate of the tile range to render
     * @param minTileY the minimum y coordinate of the tile range to render
     * @param maxTileX the maximum x coordinate of the tile range to render
     * @param maxTileY the maximum y coordinate of the tile range to render
     */
    private void renderTileLayer(TextureBatch batch, TiledTileMapLayer layer, int minTileX, int minTileY, int maxTileX, int maxTileY) {
        if (!layer.isVisible()) {
            return;
        }

        if (layer.isInfinite() && !layer.getChunks().isEmpty()) {
            for (MapChunk chunk : layer.getChunks().values()) {
                int chunkMinX = chunk.x();
                int chunkMinY = chunk.y();
                int chunkMaxX = chunk.x() + chunk.width() - 1;
                int chunkMaxY = chunk.y() + chunk.height() - 1;

                if (chunkMaxX < minTileX || chunkMinX > maxTileX || chunkMaxY < minTileY || chunkMinY > maxTileY) {
                    continue;
                }

                drawGrid(batch, chunk.width(), chunk.height(), chunk.globalTileIDs(), chunk.x(), chunk.y(), minTileX, minTileY, maxTileX, maxTileY);
            }
            return;
        }

        if (layer.getGids() == null || layer.getGids().length == 0) {
            return;
        }

        drawGrid(batch, layer.getWidth(), layer.getHeight(), layer.getGids(), 0, 0, minTileX, minTileY, maxTileX, maxTileY);
    }

    /**
     * Renders a specified layer within a defined circular radius around a center tile.
     *
     * @param batch       The texture batch used for rendering.
     * @param layerName   The name of the layer to be rendered.
     * @param centerTileX The x-coordinate of the center tile.
     * @param centerTileY The y-coordinate of the center tile.
     * @param radiusTiles The radius, in tiles, around the center tile to be rendered.
     */
    public void renderLayer(TextureBatch batch, String layerName, int centerTileX, int centerTileY, int radiusTiles) {
        renderLayer(batch, layerName, centerTileX - radiusTiles, centerTileY - radiusTiles, centerTileX + radiusTiles, centerTileY + radiusTiles);
    }

    /**
     * Renders the specified tile layer within a given rectangular region.
     *
     * @param batch     the texture batch used for rendering, must not be null
     * @param layerName the name of the layer to render, must not be null
     * @param minTileX  the minimum tile index along the X-axis to render
     * @param minTileY  the minimum tile index along the Y-axis to render
     * @param maxTileX  the maximum tile index along the X-axis to render
     * @param maxTileY  the maximum tile index along the Y-axis to render
     * @throws NullPointerException if {@code batch} or {@code layerName} is null
     */
    public void renderLayer(TextureBatch batch, String layerName, int minTileX, int minTileY, int maxTileX, int maxTileY) {
        Objects.requireNonNull(batch, "batch");
        Objects.requireNonNull(layerName, "layerName");

        MapLayer layer = getLayer(layerName);
        if (layer instanceof TiledTileMapLayer tileLayer) {
            renderTileLayer(batch, tileLayer, minTileX, minTileY, maxTileX, maxTileY);
        }
    }

    /**
     * Renders a grid of tiles onto the provided texture batch. The grid is defined
     * by its dimensions, global tile IDs, and other positional parameters. The
     * method calculates which tiles to draw based on visible tile boundaries
     * and retrieves the corresponding texture regions before rendering.
     *
     * @param batch         the batch used for rendering the tiles
     * @param widthTiles    the width of the grid in tiles
     * @param heightTiles   the height of the grid in tiles
     * @param globalTileIDs an array of global tile IDs representing the grid
     * @param offsetXTiles  the horizontal offset of the grid in tiles
     * @param offsetYTiles  the vertical offset of the grid in tiles
     * @param minTileX      the minimum visible tile index in the X axis
     * @param minTileY      the minimum visible tile index in the Y axis
     * @param maxTileX      the maximum visible tile index in the X axis
     * @param maxTileY      the maximum visible tile index in the Y axis
     */
    private void drawGrid(TextureBatch batch, int widthTiles, int heightTiles, int[] globalTileIDs, int offsetXTiles, int offsetYTiles, int minTileX, int minTileY, int maxTileX, int maxTileY) {
        final int tilePixelWidth = this.tileWidth;
        final int tilePixelHeight = this.tileHeight;

        int startLocalX = Math.max(0, minTileX - offsetXTiles);
        int endLocalX = Math.min(widthTiles - 1, maxTileX - offsetXTiles);

        int startWorldY = Math.max(minTileY, offsetYTiles);
        int endWorldY = Math.min(maxTileY, offsetYTiles + heightTiles - 1);

        if (startLocalX > endLocalX || startWorldY > endWorldY) {
            return;
        }

        for (int worldTileY = startWorldY; worldTileY <= endWorldY; worldTileY++) {
            int localTileY = heightTiles - 1 - (worldTileY - offsetYTiles);

            for (int localTileX = startLocalX; localTileX <= endLocalX; localTileX++) {
                int index = localTileY * widthTiles + localTileX;
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

                float worldX = (offsetXTiles + localTileX) * (float) tilePixelWidth;
                float worldY = worldTileY * (float) tilePixelHeight;

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

    public ResolvedTile getTile(String layerName, int tileX, int tileY) {
        MapLayer layer = getLayer(layerName);
        if (!(layer instanceof TiledTileMapLayer tileLayer)) {
            return null;
        }
        return getTile(tileLayer, tileX, tileY);
    }

    public ResolvedTile getTile(int layerIndex, int tileX, int tileY) {
        MapLayer layer = getLayer(layerIndex);
        if (!(layer instanceof TiledTileMapLayer tileLayer)) {
            return null;
        }
        return getTile(tileLayer, tileX, tileY);
    }

    public TileDefinition getTileDefinition(String layerName, int tileX, int tileY) {
        ResolvedTile tile = getTile(layerName, tileX, tileY);
        return tile != null ? tile.definition() : null;
    }

    public TileDefinition getTileDefinition(int layerIndex, int tileX, int tileY) {
        ResolvedTile tile = getTile(layerIndex, tileX, tileY);
        return tile != null ? tile.definition() : null;
    }

    public int getTileGid(String layerName, int tileX, int tileY) {
        ResolvedTile tile = getTile(layerName, tileX, tileY);
        return tile != null ? tile.gid() : 0;
    }

    public int getTileGid(int layerIndex, int tileX, int tileY) {
        ResolvedTile tile = getTile(layerIndex, tileX, tileY);
        return tile != null ? tile.gid() : 0;
    }

    public ResolvedTile getTile(TiledTileMapLayer layer, int tileX, int tileY) {
        if (layer == null || layer.getGids() == null) {
            return null;
        }

        if (tileX < 0 || tileY < 0 || tileX >= layer.getWidth() || tileY >= layer.getHeight()) {
            return null;
        }

        int sourceTileY = layer.getHeight() - 1 - tileY;
        int index = sourceTileY * layer.getWidth() + tileX;

        int rawGid = layer.getGids()[index];
        int gid = rawGid & GID_MASK;
        if (gid == 0) {
            return null;
        }

        TileSet tileSet = findTilesetForGID(gid);
        if (tileSet == null) {
            return null;
        }

        int localId = gid - tileSet.getFirstGlobalTileID();
        TileDefinition definition = tileSet.getDefinition(localId);

        return new ResolvedTile(tileX, tileY, rawGid, gid, localId, tileSet, definition);
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