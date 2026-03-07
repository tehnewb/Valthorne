package valthorne.graphics.map.tiled;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

/**
 * Represents the fully parsed, CPU-side data of a Tiled TMX map.
 *
 * <p>
 * {@code TiledMapData} is the non-rendering form of a Tiled map. It contains the parsed
 * map metadata, tileset data, layer data, and custom properties, but it does not create
 * GPU textures or any OpenGL resources. This makes it safe to load on background threads
 * through Valthorne's asset system.
 * </p>
 *
 * <p>
 * The intended lifecycle is:
 * </p>
 *
 * <ol>
 *     <li>Load TMX and dependency data into {@code TiledMapData} on a worker thread.</li>
 *     <li>Decode image bytes into CPU-side structures such as {@link TileSetData}.</li>
 *     <li>On the OpenGL thread, convert this object into a runtime {@link TiledMap}.</li>
 * </ol>
 *
 * <h2>What this class stores</h2>
 * <ul>
 *     <li>The root map identity and dimensions such as name, width, height, tile size, and orientation</li>
 *     <li>The infinite-map flag used by Tiled for chunk-based tile layers</li>
 *     <li>All root-level custom map properties</li>
 *     <li>All parsed {@link TileSetData} entries sorted by first global tile ID</li>
 *     <li>All supported parsed map layers in source order</li>
 * </ul>
 *
 * <h2>Threading model</h2>
 * <p>
 * This class is specifically useful because it avoids GPU creation during loading. That means
 * it can be safely created inside asynchronous loaders without requiring an active OpenGL context.
 * Once loaded, the data can be handed to {@link TiledMap#TiledMap(TiledMapData)} on the render thread.
 * </p>
 *
 * <h2>Supported TMX content</h2>
 * <ul>
 *     <li>{@code <tileset>} into {@link TileSetData}</li>
 *     <li>{@code <layer>} into {@link TiledTileMapLayer}</li>
 *     <li>{@code <objectgroup>} into {@link TiledObjectMapLayer}</li>
 *     <li>{@code <imagelayer>} into {@link TiledImageMapLayerData}</li>
 *     <li>{@code <properties>} into the root property map</li>
 * </ul>
 *
 * <p>
 * Unknown or unsupported tags are skipped safely so the parser can continue reading known
 * content without failing immediately on extra data.
 * </p>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * TiledDependencyResolver resolver = new FileSystemResolver();
 *
 * TiledMapData data = TiledMapData.load("assets/map/TestMap.tmx", resolver);
 *
 * // Later, on the OpenGL thread:
 * TiledMap map = new TiledMap(data);
 * }</pre>
 *
 * @author Albert Beaupre
 * @since March 7th, 2026
 */
public final class TiledMapData {

    private final String name; // The map name read from the TMX root element.
    private final int width; // The map width in tiles for finite maps.
    private final int height; // The map height in tiles for finite maps.
    private final int tileWidth; // The width of one tile in pixels.
    private final int tileHeight; // The height of one tile in pixels.
    private final boolean infinite; // Whether this TMX map uses Tiled's infinite mode.
    private final String orientation; // The map orientation string such as orthogonal.
    private final Map<String, String> properties; // The root-level custom properties defined on the map.
    private final List<TileSetData> tileSetData; // The parsed CPU-side tileset data used to build runtime tilesets later.
    private final List<MapLayer> mapLayers; // The parsed map layers in source order.

    /**
     * Creates a fully initialized {@code TiledMapData} instance from already parsed components.
     *
     * <p>
     * This constructor normalizes nullable collections and strings into safe defaults and then
     * sorts the tileset list by first global tile ID. That sorting step is important because
     * runtime tileset lookup depends on tilesets being ordered from lowest to highest global ID.
     * </p>
     *
     * @param name the map name, or null to use an empty string
     * @param width the map width in tiles
     * @param height the map height in tiles
     * @param tileWidth the tile width in pixels
     * @param tileHeight the tile height in pixels
     * @param infinite true if the map uses Tiled's infinite-map mode
     * @param orientation the orientation string, or null to default to {@code orthogonal}
     * @param properties the root property map, or null to use an empty map
     * @param tileSetData the parsed tileset data list, or null to use an empty list
     * @param mapLayers the parsed map layers, or null to use an empty list
     */
    private TiledMapData(String name, int width, int height, int tileWidth, int tileHeight, boolean infinite, String orientation, Map<String, String> properties, List<TileSetData> tileSetData, List<MapLayer> mapLayers) {
        this.name = name != null ? name : "";
        this.width = width;
        this.height = height;
        this.tileWidth = tileWidth;
        this.tileHeight = tileHeight;
        this.infinite = infinite;
        this.orientation = orientation != null ? orientation : "orthogonal";
        this.properties = properties != null ? properties : new HashMap<>();
        this.tileSetData = tileSetData != null ? tileSetData : new ArrayList<>();
        this.mapLayers = mapLayers != null ? mapLayers : new ArrayList<>();
        this.tileSetData.sort(Comparator.comparingInt(TileSetData::firstGlobalTileID));
    }

    /**
     * Loads a TMX map from a file path and parses it into CPU-side map data.
     *
     * <p>
     * This is the path-based convenience loader. It reads the TMX file bytes from disk
     * and delegates to {@link #load(byte[], String, TiledDependencyResolver)} so all core
     * parsing logic remains centralized in the byte-based loader.
     * </p>
     *
     * <p>
     * This method is useful when your map is stored directly on disk and any external
     * TSX or image references should be resolved relative to that file path.
     * </p>
     *
     * @param tmxFilePath the file path to the TMX file
     * @param resolver the dependency resolver used to load referenced TSX files and image bytes
     * @return the parsed map data
     * @throws RuntimeException if the file cannot be read or parsing fails
     */
    public static TiledMapData load(String tmxFilePath, TiledDependencyResolver resolver) {
        try {
            byte[] bytes = Files.readAllBytes(Paths.get(tmxFilePath));
            return load(bytes, tmxFilePath, resolver);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Loads a TMX map from raw bytes and parses it into CPU-side map data.
     *
     * <p>
     * This method performs the main XML parsing process for the TMX document. It reads the
     * root map attributes, collects root properties, loads tileset data, parses supported
     * layer types, and then produces a complete {@code TiledMapData} instance.
     * </p>
     *
     * <p>
     * The provided {@code tmxPath} is used as the parent location for dependency resolution.
     * Even though the main TMX content is supplied as bytes, relative TSX and image references
     * still need a logical base path so the resolver can locate them correctly.
     * </p>
     *
     * <p>
     * This method is intended to be safe for worker-thread use because it only creates
     * CPU-side decoded data and does not create OpenGL resources.
     * </p>
     *
     * @param tmxBytes the raw TMX XML bytes
     * @param tmxPath the logical or real path of the TMX file for dependency resolution
     * @param resolver the dependency resolver used to load referenced files
     * @return the parsed map data
     * @throws NullPointerException if {@code tmxBytes} or {@code resolver} is null
     * @throws RuntimeException if XML parsing or dependency loading fails
     */
    public static TiledMapData load(byte[] tmxBytes, String tmxPath, TiledDependencyResolver resolver) {
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
            List<TileSetData> tileSets = new ArrayList<>();
            List<MapLayer> layers = new ArrayList<>();

            while (reader.hasNext()) {
                int event = reader.next();

                if (event == XMLStreamConstants.START_ELEMENT) {
                    String tag = reader.getLocalName();

                    switch (tag) {
                        case "properties" -> mapProps.putAll(TiledXML.readProperties(reader));
                        case "tileset" -> tileSets.add(TileSetData.load(tmxBytes, tmxPath, resolver, reader));
                        case "layer" -> layers.add(TiledTileMapLayer.load(tmxBytes, reader));
                        case "objectgroup" -> layers.add(TiledObjectMapLayer.load(reader));
                        case "imagelayer" -> layers.add(TiledImageMapLayerData.load(tmxBytes, tmxPath, resolver, reader));
                        default -> TiledXML.skipElement(reader);
                    }
                } else if (event == XMLStreamConstants.END_ELEMENT) {
                    if ("map".equals(reader.getLocalName())) {
                        break;
                    }
                }
            }

            return new TiledMapData(name, width, height, tileWidth, tileHeight, infinite, orientation, mapProps, tileSets, layers);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Returns the map name.
     *
     * @return the map name, never null
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the map width in tiles.
     *
     * <p>
     * For finite maps this is the actual map width. For infinite maps this may simply
     * reflect the TMX metadata and not the full occupied chunk range.
     * </p>
     *
     * @return the map width in tiles
     */
    public int getWidth() {
        return width;
    }

    /**
     * Returns the map height in tiles.
     *
     * <p>
     * For finite maps this is the actual map height. For infinite maps this may simply
     * reflect the TMX metadata and not the full occupied chunk range.
     * </p>
     *
     * @return the map height in tiles
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
     * Returns whether this map uses Tiled's infinite-map mode.
     *
     * @return true if the map is infinite
     */
    public boolean isInfinite() {
        return infinite;
    }

    /**
     * Returns the map orientation string.
     *
     * <p>
     * Typical values include {@code orthogonal}, though other Tiled orientations may
     * also appear depending on the exported map.
     * </p>
     *
     * @return the orientation string, never null
     */
    public String getOrientation() {
        return orientation;
    }

    /**
     * Returns the root map property map.
     *
     * <p>
     * These are the custom properties defined directly on the TMX {@code <map>} element.
     * </p>
     *
     * @return the map property map
     */
    public Map<String, String> getProperties() {
        return properties;
    }

    /**
     * Returns the parsed tileset data list.
     *
     * <p>
     * The returned list is already sorted by first global tile ID so it can be converted
     * directly into runtime tilesets later.
     * </p>
     *
     * @return the tileset data list
     */
    public List<TileSetData> getTileSetData() {
        return tileSetData;
    }

    /**
     * Returns the parsed map layers.
     *
     * <p>
     * The list preserves the order they were encountered in the TMX file.
     * </p>
     *
     * @return the parsed map layer list
     */
    public List<MapLayer> getMapLayers() {
        return mapLayers;
    }
}