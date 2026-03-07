package valthorne.graphics.map.tiled;

import valthorne.graphics.texture.TextureData;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Represents the fully parsed, CPU-side data for a single Tiled tileset.
 *
 * <p>
 * {@code TileSetData} is the non-runtime form of a tileset. It stores all tileset metadata
 * parsed from TMX or TSX content together with the decoded {@link TextureData} for the tileset
 * image. Because it only holds CPU-side data, it can be created on worker threads without
 * requiring an active OpenGL context.
 * </p>
 *
 * <p>
 * This class exists as an intermediate step between raw TMX or TSX parsing and the runtime
 * {@link TileSet} object. The usual workflow looks like this:
 * </p>
 *
 * <ol>
 *     <li>Parse the map and its tilesets into {@code TileSetData}</li>
 *     <li>Decode the referenced image bytes into {@link TextureData}</li>
 *     <li>Later, on the render thread, convert the data into a runtime {@link TileSet}</li>
 * </ol>
 *
 * <h2>What this class stores</h2>
 * <ul>
 *     <li>The first global tile ID owned by the tileset</li>
 *     <li>The tileset name</li>
 *     <li>The decoded CPU-side texture data for the tileset atlas image</li>
 *     <li>Tile sizing information such as tile width, tile height, spacing, and margin</li>
 *     <li>Atlas layout information such as tile count, columns, image width, and image height</li>
 *     <li>Custom tileset properties defined in Tiled</li>
 *     <li>Optional per-tile metadata such as animation definitions</li>
 * </ul>
 *
 * <h2>External TSX support</h2>
 * <p>
 * Tiled maps may define tilesets inline inside the TMX file or externally in a TSX file.
 * This class supports both cases. If the current {@code <tileset>} element references a
 * {@code source} attribute, the external TSX bytes are resolved first and then parsed as
 * the real tileset body.
 * </p>
 *
 * <h2>Image dependency support</h2>
 * <p>
 * The tileset image is resolved through {@link TiledDependencyResolver}, which means the
 * image does not need to come from the file system directly. It can come from disk, memory,
 * cache archives, classpath resources, or any custom source supported by the resolver.
 * </p>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * TiledDependencyResolver resolver = new FileSystemResolver();
 *
 * TileSetData tileSetData = TileSetData.load(
 *         tmxBytes,
 *         "assets/maps/world.tmx",
 *         resolver,
 *         reader
 * );
 *
 * int firstGid = tileSetData.getFirstGlobalTileID();
 * int tileWidth = tileSetData.getTileWidth();
 * TextureData image = tileSetData.getTextureData();
 * }</pre>
 *
 * @param firstGlobalTileID The first global tile ID owned by this tileset.
 * @param name              The name of the tileset.
 * @param textureData       The decoded CPU-side texture data for the atlas image.
 * @param tileWidth         The width of one tile in pixels.
 * @param tileHeight        The height of one tile in pixels.
 * @param spacing           The spacing between tiles inside the atlas image.
 * @param margin            The outer margin around the atlas tile grid.
 * @param tileCount         The total number of tiles declared in this tileset.
 * @param columns           The number of columns in the atlas grid.
 * @param imageWidth        The width of the atlas image in pixels.
 * @param imageHeight       The height of the atlas image in pixels.
 * @param properties        The custom properties defined for the tileset.
 * @param tileDefs          The optional per-tile metadata definitions.
 * @author Albert Beaupre
 * @since March 7th, 2026
 */
public record TileSetData(int firstGlobalTileID, String name, TextureData textureData, int tileWidth, int tileHeight, int spacing, int margin, int tileCount, int columns, int imageWidth, int imageHeight, Map<String, String> properties, Map<Integer, TileDefinition> tileDefs) {

    /**
     * Creates a new CPU-side tileset data object.
     *
     * <p>
     * This constructor stores all already-parsed tileset information and normalizes nullable
     * fields into safe defaults where appropriate. The texture data is required because a
     * valid renderable tileset must have an atlas image.
     * </p>
     *
     * @param firstGlobalTileID the first global tile ID owned by this tileset
     * @param name              the tileset name
     * @param textureData       the decoded CPU-side texture data for the atlas image
     * @param tileWidth         the width of one tile in pixels
     * @param tileHeight        the height of one tile in pixels
     * @param spacing           the spacing between adjacent tiles
     * @param margin            the outer margin around the tile grid
     * @param tileCount         the total number of tiles in the tileset
     * @param columns           the number of atlas columns
     * @param imageWidth        the atlas image width in pixels
     * @param imageHeight       the atlas image height in pixels
     * @param properties        the custom tileset properties
     * @param tileDefs          the per-tile metadata definitions
     * @throws NullPointerException if {@code textureData} is null
     */
    public TileSetData(int firstGlobalTileID, String name, TextureData textureData, int tileWidth, int tileHeight, int spacing, int margin, int tileCount, int columns, int imageWidth, int imageHeight, Map<String, String> properties, Map<Integer, TileDefinition> tileDefs) {
        this.firstGlobalTileID = firstGlobalTileID;
        this.name = name != null ? name : "";
        this.textureData = Objects.requireNonNull(textureData, "textureData");
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
    }

    /**
     * Loads tileset data from the current {@code <tileset>} XML element.
     *
     * <p>
     * This method supports both inline and external tilesets:
     * </p>
     *
     * <ul>
     *     <li>If the current {@code <tileset>} has a {@code source} attribute, the referenced TSX file is resolved and parsed</li>
     *     <li>If there is no {@code source} attribute, the tileset body is parsed directly from the current XML reader</li>
     * </ul>
     *
     * <p>
     * The returned object contains CPU-side data only and does not create any OpenGL resources.
     * </p>
     *
     * @param tmxBytes the raw bytes of the parent TMX file
     * @param tmxPath  the logical or physical path of the parent TMX file
     * @param resolver the dependency resolver used to load TSX files and image dependencies
     * @param reader   the XML reader positioned at a {@code <tileset>} start element
     * @return the parsed tileset data
     * @throws Exception if TSX resolution, XML parsing, or image decoding fails
     */
    public static TileSetData load(byte[] tmxBytes, String tmxPath, TiledDependencyResolver resolver, XMLStreamReader reader) throws Exception {
        int firstGid = TiledXML.readInteger(reader, "firstgid", 1);
        String source = TiledXML.readAttribute(reader, "source", null);

        if (source != null && !source.isBlank()) {
            byte[] tsxBytes = TiledXML.readBytesFromTmxDependency(resolver, tmxBytes, tmxPath, source);
            String tsxPath = TiledXML.resolvePathString(tmxPath, source);

            try (InputStream in = new ByteArrayInputStream(tsxBytes)) {
                XMLStreamReader tsxReader = TiledXML.getXMLFactory().createXMLStreamReader(in);
                TiledXML.moveToStart(tsxReader, "tileset");
                TileSetData data = readTilesetBody(tsxBytes, tsxPath, resolver, tsxReader, firstGid);
                tsxReader.close();
                TiledXML.skipElement(reader);
                return data;
            }
        }

        return readTilesetBody(tmxBytes, tmxPath, resolver, reader, firstGid);
    }

    /**
     * Parses the actual body of a tileset after the correct XML source has been selected.
     *
     * <p>
     * This method reads the tileset attributes, custom properties, image definition, and
     * per-tile metadata from either:
     * </p>
     *
     * <ul>
     *     <li>The original TMX file for inline tilesets</li>
     *     <li>An external TSX file for source-based tilesets</li>
     * </ul>
     *
     * <p>
     * The tileset image bytes are resolved through the provided dependency resolver and
     * decoded into {@link TextureData}. Tile metadata is collected into {@link TileDefinition}
     * objects keyed by local tile ID.
     * </p>
     *
     * @param parentBytes the raw bytes of the parent XML file currently being parsed
     * @param parentPath  the logical or physical path of the parent XML file
     * @param resolver    the dependency resolver used to load the referenced image
     * @param reader      the XML reader positioned at the start of a {@code <tileset>} element
     * @param firstGid    the first global tile ID to assign to this tileset
     * @return the parsed tileset data
     * @throws Exception             if parsing or image decoding fails
     * @throws IllegalStateException if the tileset does not define a usable image source
     */
    private static TileSetData readTilesetBody(byte[] parentBytes, String parentPath, TiledDependencyResolver resolver, XMLStreamReader reader, int firstGid) throws Exception {
        String name = TiledXML.readAttribute(reader, "name", "");
        int tileWidth = TiledXML.readInteger(reader, "tilewidth", 0);
        int tileHeight = TiledXML.readInteger(reader, "tileheight", 0);
        int spacing = TiledXML.readInteger(reader, "spacing", 0);
        int margin = TiledXML.readInteger(reader, "margin", 0);
        int tileCount = TiledXML.readInteger(reader, "tilecount", 0);
        int columns = TiledXML.readInteger(reader, "columns", 0);

        Map<String, String> props = new HashMap<>();
        TextureData textureData = null;
        int imageWidth = 0;
        int imageHeight = 0;
        Map<Integer, TileDefinition> tiles = new HashMap<>();

        while (reader.hasNext()) {
            int event = reader.next();

            if (event == XMLStreamConstants.START_ELEMENT) {
                String tag = reader.getLocalName();

                switch (tag) {
                    case "properties" -> props.putAll(TiledXML.readProperties(reader));
                    case "image" -> {
                        String imageSource = TiledXML.readAttribute(reader, "source", null);
                        imageWidth = TiledXML.readInteger(reader, "width", 0);
                        imageHeight = TiledXML.readInteger(reader, "height", 0);

                        if (imageSource != null && !imageSource.isBlank()) {
                            byte[] imageBytes = TiledXML.readBytesFromTmxDependency(resolver, parentBytes, parentPath, imageSource);
                            textureData = TextureData.load(imageBytes, true);
                        }

                        TiledXML.skipElement(reader);
                    }
                    case "tile" -> {
                        int id = TiledXML.readInteger(reader, "id", -1);
                        TileDefinition def = TileDefinition.load(reader, id);
                        if (id >= 0) tiles.put(id, def);
                    }
                    default -> TiledXML.skipElement(reader);
                }
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                if ("tileset".equals(reader.getLocalName())) {
                    break;
                }
            }
        }

        if (textureData == null)
            throw new IllegalStateException("TileSet '" + name + "' is missing an <image> source.");

        return new TileSetData(firstGid, name, textureData, tileWidth, tileHeight, spacing, margin, tileCount, columns, imageWidth, imageHeight, props, tiles);
    }

    /**
     * Returns the first global tile ID owned by this tileset.
     *
     * @return the first global tile ID
     */
    @Override
    public int firstGlobalTileID() {
        return firstGlobalTileID;
    }

    /**
     * Returns the tileset name.
     *
     * @return the tileset name, never null
     */
    @Override
    public String name() {
        return name;
    }

    /**
     * Returns the decoded CPU-side texture data for the tileset image.
     *
     * @return the tileset texture data
     */
    @Override
    public TextureData textureData() {
        return textureData;
    }

    /**
     * Returns the width of one tile in pixels.
     *
     * @return the tile width in pixels
     */
    @Override
    public int tileWidth() {
        return tileWidth;
    }

    /**
     * Returns the height of one tile in pixels.
     *
     * @return the tile height in pixels
     */
    @Override
    public int tileHeight() {
        return tileHeight;
    }

    /**
     * Returns the spacing between adjacent tiles in the atlas image.
     *
     * @return the tile spacing in pixels
     */
    @Override
    public int spacing() {
        return spacing;
    }

    /**
     * Returns the outer margin around the tileset atlas grid.
     *
     * @return the atlas margin in pixels
     */
    @Override
    public int margin() {
        return margin;
    }

    /**
     * Returns the declared total tile count for this tileset.
     *
     * @return the total tile count
     */
    @Override
    public int tileCount() {
        return tileCount;
    }

    /**
     * Returns the declared number of atlas columns.
     *
     * @return the number of columns
     */
    @Override
    public int columns() {
        return columns;
    }

    /**
     * Returns the width of the tileset image in pixels.
     *
     * @return the image width in pixels
     */
    @Override
    public int imageWidth() {
        return imageWidth;
    }

    /**
     * Returns the height of the tileset image in pixels.
     *
     * @return the image height in pixels
     */
    @Override
    public int imageHeight() {
        return imageHeight;
    }

    /**
     * Returns the custom properties defined for this tileset.
     *
     * @return the tileset property map
     */
    @Override
    public Map<String, String> properties() {
        return properties;
    }

    /**
     * Returns the per-tile metadata definitions keyed by local tile ID.
     *
     * @return the tile definition map
     */
    @Override
    public Map<Integer, TileDefinition> tileDefs() {
        return tileDefs;
    }
}