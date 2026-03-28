package valthorne.graphics.map.tiled;

import valthorne.graphics.texture.Texture;
import valthorne.graphics.texture.TextureRegion;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Represents a runtime-ready Tiled tileset backed by a GPU {@link Texture}.
 *
 * <p>
 * A {@code TileSet} is created after TMX or TSX data has already been parsed into
 * {@link TileSetData}. At that point the tileset has all of its metadata available,
 * including tile dimensions, spacing, margin, atlas image size, custom properties,
 * and optional per-tile definitions such as animation data. This runtime class then
 * wraps that metadata together with an actual GPU texture so tiles can be resolved
 * and rendered efficiently.
 * </p>
 *
 * <p>
 * Tiled maps use <b>global tile IDs</b> when storing tile data in layers. A tileset
 * owns a contiguous range of those IDs beginning at {@link #firstGlobalTileID}. When
 * a map renderer finds that a given global tile ID belongs to this tileset, it converts
 * that global ID into a <b>local tile ID</b> by subtracting the first global tile ID.
 * That local tile ID is then used by this class to:
 * </p>
 *
 * <ul>
 *     <li>Resolve the tile's source coordinates within the tileset atlas</li>
 *     <li>Create and cache a {@link TextureRegion} for fast reuse</li>
 *     <li>Resolve animated tile frames based on elapsed time</li>
 * </ul>
 *
 * <h2>Atlas layout</h2>
 * <p>
 * The texture atlas is assumed to be arranged in a grid. The tile width, tile height,
 * spacing, margin, column count, image width, and image height are all used to compute
 * the exact source rectangle for each tile. The Y coordinate is resolved using a
 * bottom-left texture origin so the produced {@link TextureRegion} matches the runtime
 * rendering system.
 * </p>
 *
 * <h2>Animation model</h2>
 * <p>
 * If a tile has animation data in {@link #tileDefs}, {@link #resolveAnimatedLocalId(int, float)}
 * will step through the animation frames using the provided elapsed time. If a tile has
 * no animation, the original tile ID is returned unchanged.
 * </p>
 *
 * <h2>Caching</h2>
 * <p>
 * Texture regions are generated lazily and stored in {@link #regionCache}. This avoids
 * recalculating source rectangles for the same local tile ID over and over during
 * rendering.
 * </p>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * TileSetData data = ...;
 * TileSet tileSet = TileSet.fromData(data);
 *
 * int gid = 37;
 * int localId = gid - tileSet.getFirstGlobalTileID();
 *
 * int drawId = tileSet.resolveAnimatedLocalId(localId, timeSeconds);
 * TextureRegion region = tileSet.getRegionForLocalId(drawId);
 *
 * batch.draw(region, 100, 100, tileSet.getTileWidth(), tileSet.getTileHeight());
 * }</pre>
 *
 * @author Albert Beaupre
 * @since March 5th, 2026
 */
public final class TileSet {

    private final int firstGlobalTileID; // The first global tile ID owned by this tileset.
    private final String name; // The tileset name read from Tiled data.
    private final Texture texture; // The GPU texture atlas used by this tileset.
    private final int tileWidth; // The width of one tile in pixels.
    private final int tileHeight; // The height of one tile in pixels.
    private final int spacing; // The spacing in pixels between tiles inside the atlas.
    private final int margin; // The outer margin in pixels around the atlas tile grid.
    private final int tileCount; // The total number of tiles declared in this tileset.
    private final int columns; // The number of atlas columns declared for this tileset.
    private final int imageWidth; // The atlas image width in pixels.
    private final int imageHeight; // The atlas image height in pixels.
    private final Map<String, String> properties; // The custom tileset properties defined in Tiled.
    private final Map<Integer, TileDefinition> tileDefs; // Per-tile metadata such as animation definitions.
    private final Map<Integer, TextureRegion> regionCache; // Cached texture regions keyed by local tile ID.

    /**
     * Creates a new runtime tileset.
     *
     * <p>
     * This constructor stores all already-parsed tileset metadata and the runtime
     * texture atlas. It also initializes an empty region cache so tile regions can
     * be created lazily on demand.
     * </p>
     *
     * <p>
     * Null values are normalized where appropriate:
     * </p>
     *
     * <ul>
     *     <li>{@code name} becomes an empty string when null</li>
     *     <li>{@code properties} becomes an empty map when null</li>
     *     <li>{@code tileDefs} becomes an empty map when null</li>
     * </ul>
     *
     * <p>
     * The {@code texture} argument must not be null because this runtime form of a
     * tileset is intended to be renderable immediately.
     * </p>
     *
     * @param firstGlobalTileID the first global tile ID belonging to this tileset
     * @param name              the tileset name
     * @param texture           the GPU texture atlas for this tileset
     * @param tileWidth         the width of one tile in pixels
     * @param tileHeight        the height of one tile in pixels
     * @param spacing           the spacing in pixels between adjacent tiles
     * @param margin            the outer atlas margin in pixels
     * @param tileCount         the total number of tiles in the tileset
     * @param columns           the number of columns in the atlas
     * @param imageWidth        the width of the atlas image in pixels
     * @param imageHeight       the height of the atlas image in pixels
     * @param properties        custom tileset properties
     * @param tileDefs          per-tile metadata definitions
     * @throws NullPointerException if {@code texture} is null
     */
    public TileSet(int firstGlobalTileID, String name, Texture texture, int tileWidth, int tileHeight, int spacing, int margin, int tileCount, int columns, int imageWidth, int imageHeight, Map<String, String> properties, Map<Integer, TileDefinition> tileDefs) {
        this.firstGlobalTileID = firstGlobalTileID;
        this.name = name != null ? name : "";
        this.texture = Objects.requireNonNull(texture, "texture");
        this.tileWidth = tileWidth;
        this.tileHeight = tileHeight;
        this.spacing = spacing;
        this.margin = margin;
        this.tileCount = tileCount;
        this.columns = columns;
        this.imageWidth = imageWidth;
        this.imageHeight = imageHeight;
        this.properties = properties != null ? properties : new HashMap<>();
        this.tileDefs = tileDefs != null ? tileDefs : new HashMap<>();
        this.regionCache = new HashMap<>();
    }

    /**
     * Returns the cached or newly created texture region for the specified local tile ID.
     *
     * <p>
     * The local tile ID is the index of a tile within this specific tileset, not the
     * global tile ID stored inside map layers. The region is resolved by computing the
     * tile's row and column inside the atlas grid and then translating those values into
     * exact source coordinates.
     * </p>
     *
     * <p>
     * If a region has already been created for this local tile ID, the cached instance is
     * returned immediately. Otherwise a new {@link TextureRegion} is created, stored in the
     * cache, and then returned.
     * </p>
     *
     * <p>
     * If the tile dimensions are invalid, this method returns {@code null} because a usable
     * source rectangle cannot be computed.
     * </p>
     *
     * @param localID the tile ID relative to this tileset
     * @return the corresponding texture region, or {@code null} if tile dimensions are invalid
     */
    public TextureRegion getRegionForLocalId(int localID) {
        TextureRegion cached = regionCache.get(localID);
        if (cached != null) return cached;
        if (tileWidth <= 0 || tileHeight <= 0) return null;

        int cols = columns > 0 ? columns : Math.max(1, imageWidth / tileWidth);
        int col = localID % cols;
        int row = localID / cols;
        int srcX = margin + col * (tileWidth + spacing);
        int srcY = imageHeight - margin - (row + 1) * tileHeight - row * spacing;

        TextureRegion region = new TextureRegion(texture, srcX, srcY, tileWidth, tileHeight);
        regionCache.put(localID, region);
        return region;
    }

    /**
     * Resolves the correct local tile ID for an animated tile at the given elapsed time.
     *
     * <p>
     * If the specified local tile has an animation definition in {@link #tileDefs}, this
     * method calculates which animation frame should be displayed using the provided elapsed
     * time in seconds. The animation duration is treated as a looping timeline.
     * </p>
     *
     * <p>
     * If the tile has no animation, has an empty animation list, or has an invalid total
     * duration, the original local tile ID is returned unchanged.
     * </p>
     *
     * <p>
     * The returned tile ID is still local to this tileset and can be passed directly into
     * {@link #getRegionForLocalId(int)}.
     * </p>
     *
     * @param localId     the original local tile ID
     * @param timeSeconds the elapsed animation time in seconds
     * @return the local tile ID of the animation frame that should currently be drawn
     */
    public int resolveAnimatedLocalId(int localId, float timeSeconds) {
        TileDefinition def = tileDefs.get(localId);
        if (def == null || def.animation() == null || def.animation().isEmpty()) return localId;

        int totalMs = 0;
        for (TileAnimationFrame frame : def.animation()) totalMs += frame.duration();
        if (totalMs <= 0) return localId;

        int tMs = (int) Math.floor(timeSeconds * 1000.0);
        int mod = tMs % totalMs;
        if (mod < 0) mod += totalMs;

        int acc = 0;
        for (TileAnimationFrame frame : def.animation()) {
            acc += frame.duration();
            if (mod < acc) return frame.tileID();
        }

        return def.animation().get(def.animation().size() - 1).tileID();
    }

    /**
     * Creates a runtime {@code TileSet} from CPU-side {@link TileSetData}.
     *
     * <p>
     * This method is typically called on the OpenGL thread after the tileset image bytes
     * have already been decoded into {@link valthorne.graphics.texture.TextureData} on a
     * background thread. It converts that CPU-side data into a GPU {@link Texture} and then
     * builds a fully renderable tileset instance.
     * </p>
     *
     * @param data the CPU-side tileset data
     * @return a runtime tileset backed by a GPU texture
     * @throws NullPointerException if {@code data} is null or if its texture data is invalid
     */
    public static TileSet fromData(TileSetData data) {
        return new TileSet(
                data.firstGlobalTileID(),
                data.name(),
                new Texture(data.textureData()),
                data.tileWidth(),
                data.tileHeight(),
                data.spacing(),
                data.margin(),
                data.tileCount(),
                data.columns(),
                data.imageWidth(),
                data.imageHeight(),
                data.properties(),
                data.tileDefs()
        );
    }

    /**
     * Returns the name of this tileset.
     *
     * @return the tileset name, never null
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the first global tile ID owned by this tileset.
     *
     * <p>
     * Any map-layer global tile ID greater than or equal to this value and below the next
     * tileset's first global tile ID belongs to this tileset.
     * </p>
     *
     * @return the first global tile ID for this tileset
     */
    public int getFirstGlobalTileID() {
        return firstGlobalTileID;
    }

    /**
     * Returns the GPU texture atlas used by this tileset.
     *
     * @return the tileset texture
     */
    public Texture getTexture() {
        return texture;
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
     * Returns the spacing between adjacent tiles in the atlas.
     *
     * @return the tile spacing in pixels
     */
    public int getSpacing() {
        return spacing;
    }

    /**
     * Returns the outer margin around the atlas tile grid.
     *
     * @return the atlas margin in pixels
     */
    public int getMargin() {
        return margin;
    }

    /**
     * Returns the declared tile count for this tileset.
     *
     * @return the total number of tiles
     */
    public int getTileCount() {
        return tileCount;
    }

    /**
     * Returns the declared number of atlas columns.
     *
     * @return the number of columns in the atlas
     */
    public int getColumns() {
        return columns;
    }

    /**
     * Returns the width of the tileset image in pixels.
     *
     * @return the atlas image width
     */
    public int getImageWidth() {
        return imageWidth;
    }

    /**
     * Returns the height of the tileset image in pixels.
     *
     * @return the atlas image height
     */
    public int getImageHeight() {
        return imageHeight;
    }

    /**
     * Returns the custom properties defined for this tileset.
     *
     * <p>
     * The returned map is the stored map instance for this tileset.
     * </p>
     *
     * @return the tileset property map
     */
    public Map<String, String> getProperties() {
        return properties;
    }

    /**
     * Returns the per-tile metadata definitions for this tileset.
     *
     * <p>
     * These definitions may contain animation data or other custom tile-specific data
     * parsed from the source TMX or TSX file.
     * </p>
     *
     * @return the tile definition map
     */
    public Map<Integer, TileDefinition> getTileDefs() {
        return tileDefs;
    }

    /**
     * Returns a derived tile-count-like value based on this tileset's total tile count and columns.
     *
     * <p>
     * The {@code layerID} parameter is not currently used. This method effectively returns
     * the number of rows when the tileset is interpreted as a grid.
     * </p>
     *
     * @param layerID an unused layer identifier
     * @return {@code tileCount / columns}
     */
    public int getTileCountForLayer(int layerID) {
        return tileCount / columns;
    }

    /**
     * Returns a derived tile-count-like value using the supplied layer width and this tileset's columns.
     *
     * <p>
     * The {@code layerID} parameter is not currently used. This method does not reflect normal
     * Tiled semantics directly and simply multiplies the given width by the tileset column count.
     * </p>
     *
     * @param layerID    an unused layer identifier
     * @param layerWidth the layer width used in the calculation
     * @return {@code layerWidth * columns}
     */
    public int getTileCountForLayer(int layerID, int layerWidth) {
        return layerWidth * columns;
    }

    /**
     * Returns a derived tile-count-like value using the supplied layer dimensions and this tileset's columns.
     *
     * <p>
     * The {@code layerID} parameter is not currently used. This method multiplies the provided
     * width and height by the tileset's column count.
     * </p>
     *
     * @param layerID     an unused layer identifier
     * @param layerWidth  the layer width used in the calculation
     * @param layerHeight the layer height used in the calculation
     * @return {@code layerWidth * columns * layerHeight}
     */
    public int getTileCountForLayer(int layerID, int layerWidth, int layerHeight) {
        return layerWidth * columns * layerHeight;
    }

    /**
     * Retrieves a list of TiledObject instances associated with the specified local ID.
     *
     * @param localId the local ID of the tile for which the objects are to be retrieved
     * @return a list of TiledObject instances associated with the provided local ID,
     * or an empty list if no objects are found
     */
    public List<TiledObject> getObjectsForLocalId(int localId) {
        TileDefinition def = tileDefs.get(localId);
        if (def == null || def.objects() == null) {
            return java.util.Collections.emptyList();
        }
        return def.objects();
    }

    /**
     * Retrieves the TileDefinition associated with the specified local ID.
     *
     * @param localId the local ID of the tile whose definition is to be retrieved
     * @return the TileDefinition object associated with the given local ID, or null if no definition exists
     */
    public TileDefinition getDefinition(int localId) {
        return tileDefs.get(localId);
    }
}