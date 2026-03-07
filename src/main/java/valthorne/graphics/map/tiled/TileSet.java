package valthorne.graphics.map.tiled;

import valthorne.graphics.texture.Texture;
import valthorne.graphics.texture.TextureRegion;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Represents a collection of tiles used in a tile-based map system.
 * This class provides details about the tileset including the image source, tile dimensions,
 * spacing, margin, properties, and methods to retrieve specific tile regions or resolve animations.
 */
public class TileSet {

    private final int firstGlobalTileID; // The global ID of the first tile in this TileSet.
    private final String name; // The name of the TileSet.
    private final Texture texture; // The texture containing the image for this TileSet.
    private final int tileWidth; // The width of an individual tile in pixels.
    private final int tileHeight; // The height of an individual tile in pixels.
    private final int spacing; // The spacing in pixels between tiles in the texture.
    private final int margin; // The margin in pixels around the tiles in the texture.
    private final int tileCount; // Total number of tiles in this TileSet.
    private final int columns; // Number of columns in the TileSet's texture.
    private final int imageWidth; // The width of the texture image in pixels.
    private final int imageHeight; // The height of the texture image in pixels.
    private final Map<String, String> properties; // Custom properties of the TileSet.
    private final Map<Integer, TileDefinition> tileDefs; // Definitions of specific tiles within the TileSet.
    private final Map<Integer, TextureRegion> regionCache; // Cache of texture regions for quick access.

    /**
     * Constructs a new TileSet instance, which represents a set of tiles in a tiled map.
     *
     * @param firstGlobalTileID the global ID of the first tile in this TileSet.
     * @param name              the name of the TileSet, or an empty string if null.
     * @param texture           the texture containing the image for this TileSet. Cannot be null.
     * @param tileWidth         the width of each tile in pixels.
     * @param tileHeight        the height of each tile in pixels.
     * @param spacing           the space in pixels between individual tiles in the texture.
     * @param margin            the margin in pixels around the tiles in the texture.
     * @param tileCount         the total number of tiles in the TileSet.
     * @param columns           the number of tile columns in the texture.
     * @param imageWidth        the width of the texture image in pixels.
     * @param imageHeight       the height of the texture image in pixels.
     * @param properties        a map of custom properties associated with this TileSet. May be null.
     * @param tileDefs          a map defining specific attributes for individual tiles within this TileSet. May be null.
     */
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
     * Retrieves the texture region for a specific tile's local ID in the TileSet.
     *
     * @param localID the local tile ID within this TileSet.
     * @return the TextureRegion representing the requested local tile, or null if invalid dimensions.
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
     * Resolves the adjusted local tile ID for animated tiles based on the current time.
     *
     * @param localId     the original local ID of the animated tile.
     * @param timeSeconds the current animation time in seconds.
     * @return the updated local ID based on animation progress, or the provided ID if not animated.
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

    /**
     * Loads a TileSet instance from provided TMX data and resolves resources using a dependency resolver.
     *
     * @param tmxBytes the byte array of the TMX file content.
     * @param tmxPath  the path to the TMX file.
     * @param resolver the dependency resolver for textures and referenced TSX files.
     * @param r        the XML stream reader positioned at a <tileset> node.
     * @return the loaded TileSet instance.
     * @throws Exception if errors occur during loading.
     */
    public static TileSet load(byte[] tmxBytes, String tmxPath, TiledDependencyResolver resolver, XMLStreamReader r) throws Exception {
        int firstGid = TiledXML.readInteger(r, "firstgid", 1);
        String source = TiledXML.readAttribute(r, "source", null);

        if (source != null && !source.isBlank()) {
            byte[] tsxBytes = TiledXML.readBytesFromTmxDependency(resolver, tmxBytes, tmxPath, source);
            String tsxPath = TiledXML.resolvePathString(tmxPath, source);

            try (InputStream in = new ByteArrayInputStream(tsxBytes)) {
                XMLStreamReader tsr = TiledXML.getXMLFactory().createXMLStreamReader(in);
                TiledXML.moveToStart(tsr, "tileset");
                TileSet ts = readTilesetBody(tsxBytes, tsxPath, resolver, tsr, firstGid);
                tsr.close();
                TiledXML.skipElement(r);
                return ts;
            }
        }

        return readTilesetBody(tmxBytes, tmxPath, resolver, r, firstGid);
    }

    /**
     * Parses the body of a <tileset> XML element to initialize a TileSet instance.
     *
     * @param parentBytes the byte array of the parent TMX/TSX file.
     * @param parentPath  the file path of the parent TMX/TSX file.
     * @param resolver    the dependency resolver for related resources.
     * @param r           the XML stream reader positioned at the <tileset> element.
     * @param firstGid    the first global ID to assign to tiles in this TileSet.
     * @return the created TileSet instance.
     * @throws Exception if errors occur during parsing.
     */
    private static TileSet readTilesetBody(byte[] parentBytes, String parentPath, TiledDependencyResolver resolver, XMLStreamReader r, int firstGid) throws Exception {
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
                            byte[] imageBytes = TiledXML.readBytesFromTmxDependency(resolver, parentBytes, parentPath, imgSource);
                            texture = new Texture(imageBytes);
                        }

                        TiledXML.skipElement(r);
                    }
                    case "tile" -> {
                        int id = TiledXML.readInteger(r, "id", -1);
                        TileDefinition def = TileDefinition.load(r, id);
                        if (id >= 0) tiles.put(id, def);
                    }
                    default -> TiledXML.skipElement(r);
                }
            } else if (ev == XMLStreamConstants.END_ELEMENT) {
                if ("tileset".equals(r.getLocalName())) break;
            }
        }

        if (texture == null) throw new IllegalStateException("TileSet '" + name + "' is missing an <image> source.");

        return new TileSet(firstGid, name, texture, tileWidth, tileHeight, spacing, margin, tileCount, columns, imageWidth, imageHeight, props, tiles);
    }

    /**
     * Retrieves the name of this TileSet.
     *
     * @return the name of the TileSet as a String.
     */
    public String getName() {
        return name;
    }

    /**
     * Retrieves the width of each tile in the TileSet.
     *
     * @return the width of the tiles in pixels.
     */
    public int getTileWidth() {
        return tileWidth;
    }

    /**
     * Retrieves the height of each tile in the TileSet.
     *
     * @return the height of the tiles in pixels.
     */
    public int getTileHeight() {
        return tileHeight;
    }

    /**
     * @return the spacing in pixels between individual tiles in the TileSet texture.
     */
    public int getSpacing() {
        return spacing;
    }

    /**
     * @return the margin size in pixels around the tiles in the TileSet texture.
     */
    public int getMargin() {
        return margin;
    }

    /**
     * @return the number of tiles within this TileSet.
     */
    public int getTileCount() {
        return tileCount;
    }

    /**
     * @return the number of columns within this TileSet.
     */
    public int getColumns() {
        return columns;
    }

    /**
     * @return the width of this TileSet.
     */
    public int getImageWidth() {
        return imageWidth;
    }

    /**
     * @return the height of this TileSet.
     */
    public int getImageHeight() {
        return imageHeight;
    }

    /**
     * @return the map of properties within this TileSet.
     */
    public Map<String, String> getProperties() {
        return properties;
    }

    /**
     * @return the map of TileDefinitions within this TileSet.
     */
    public Map<Integer, TileDefinition> getTileDefs() {
        return tileDefs;
    }

    /**
     * @return the map of Texture Regions within this TileSet.
     */
    public Map<Integer, TextureRegion> getRegionCache() {
        return regionCache;
    }

    /**
     * @return the first global tile ID within this TileSet.
     */
    public int getFirstGlobalTileID() {
        return firstGlobalTileID;
    }

    /**
     * @return the Texture associated with this TileSet.
     */
    public Texture getTexture() {
        return texture;
    }
}