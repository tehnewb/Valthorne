package valthorne.graphics.map.ldtk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import valthorne.graphics.texture.TextureData;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Complete CPU-side representation of an LDtk project. The parser supports embedded
 * and external levels, all layer instance kinds, entities and typed fields, IntGrid
 * CSV data, manual and automatic tiles, definitions, enums, worlds, and decoded
 * tileset and background images. Returned collections are immutable snapshots.
 *
 * <p>Load and inspect on a worker thread, then create GPU resources on the render
 * thread:</p>
 * <pre>{@code
 * LdtkProject project = LdtkProject.load("maps/world.ldtk");
 * LdtkLevel level = project.level("Level_0");
 * LdtkMap map = project.asMap();
 * map.render(batch, level);
 * map.dispose();
 * project.dispose();
 * }</pre>
 *
 * <p>The project owns decoded CPU-side {@link TextureData}; callers must invoke
 * {@link #dispose()} after no map construction or CPU image access remains.</p>
 */
public final class LdtkProject {
    /**
     * Shared stateless JSON parser used for project and external-level documents.
     */
    private static final ObjectMapper JSON = new ObjectMapper();
    private final String iid, jsonVersion, appBuildId, worldLayout, backgroundColor; // Project metadata.
    private final int defaultGridSize, defaultLevelWidth, defaultLevelHeight; // Project defaults.
    private final List<LdtkLevel> levels; // Immutable decoded levels in project order.
    private final Map<Integer, LdtkTileset> tilesets; // Immutable tilesets keyed by UID.
    private final List<LdtkDefinition.Layer> layerDefinitions; // Immutable layer definitions.
    private final List<LdtkDefinition.Entity> entityDefinitions; // Immutable entity definitions.
    private final List<LdtkDefinition.Enum> enumDefinitions; // Immutable enum definitions.

    /**
     * Stores parsed metadata and snapshots every collection.
     *
     * @param iid project instance identifier
     * @param jsonVersion LDtk JSON format version
     * @param appBuildId LDtk application build identifier
     * @param worldLayout configured world layout, if present
     * @param backgroundColor default project background color
     * @param defaultGridSize default cell size in pixels
     * @param defaultLevelWidth default level width in pixels
     * @param defaultLevelHeight default level height in pixels
     * @param levels decoded levels in project order
     * @param tilesets decoded tilesets keyed by UID
     * @param layerDefinitions layer definitions
     * @param entityDefinitions entity definitions
     * @param enumDefinitions internal and external enum definitions
     */
    private LdtkProject(String iid, String jsonVersion, String appBuildId, String worldLayout, String backgroundColor, int defaultGridSize, int defaultLevelWidth, int defaultLevelHeight, List<LdtkLevel> levels, Map<Integer, LdtkTileset> tilesets, List<LdtkDefinition.Layer> layerDefinitions, List<LdtkDefinition.Entity> entityDefinitions, List<LdtkDefinition.Enum> enumDefinitions) {
        this.iid = iid;
        this.jsonVersion = jsonVersion;
        this.appBuildId = appBuildId;
        this.worldLayout = worldLayout;
        this.backgroundColor = backgroundColor;
        this.defaultGridSize = defaultGridSize;
        this.defaultLevelWidth = defaultLevelWidth;
        this.defaultLevelHeight = defaultLevelHeight;
        this.levels = List.copyOf(levels);
        this.tilesets = Map.copyOf(tilesets);
        this.layerDefinitions = List.copyOf(layerDefinitions);
        this.entityDefinitions = List.copyOf(entityDefinitions);
        this.enumDefinitions = List.copyOf(enumDefinitions);
    }

    /**
     * Reads a filesystem project and resolves dependencies beside their parent files.
     *
     * @param path non-null path to an LDtk project document
     * @return parsed CPU-side project owning all decoded images
     * @throws RuntimeException if the project or any dependency cannot be read or parsed
     */
    public static LdtkProject load(String path) {
        try {
            byte[] bytes = Files.readAllBytes(Path.of(path));
            return load(bytes, path, LdtkResolvers.from(new LdtkDependencySource.FileSystemSource()));
        } catch (Exception e) {
            throw new RuntimeException("Failed to load LDtk project: " + path, e);
        }
    }

    /**
     * Parses project bytes with a caller-supplied dependency resolver.
     *
     * <p>If parsing fails, every image decoded during the attempt is disposed before
     * the failure is wrapped. The input array is read but not retained or modified.</p>
     *
     * @param bytes non-null LDtk JSON bytes
     * @param path non-null logical path used for diagnostics and relative references
     * @param resolver non-null resolver for external levels and images
     * @return parsed CPU-side project owning successfully decoded images
     * @throws NullPointerException if any argument is null
     * @throws RuntimeException if JSON parsing, dependency resolution, or image decoding fails
     */
    public static LdtkProject load(byte[] bytes, String path, LdtkDependencyResolver resolver) {
        Objects.requireNonNull(bytes, "bytes");
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(resolver, "resolver");
        List<TextureData> decoded = new ArrayList<>();
        try {
            JsonNode root = JSON.readTree(bytes);
            JsonNode defs = root.path("defs");
            Map<Integer, LdtkTileset> tilesets = new LinkedHashMap<>();
            for (JsonNode node : defs.path("tilesets")) {
                int uid = integer(node, "uid");
                String rel = text(node, "relPath", null);
                TextureData image = null;
                if (rel != null && !rel.isBlank()) {
                    image = TextureData.load(resolver.resolve(bytes, path, rel), true);
                    decoded.add(image);
                } else {
                    String embedded = text(node, "embedAtlas", null);
                    if (embedded != null && !embedded.isBlank()) {
                        int comma = embedded.indexOf(',');
                        image = TextureData.load(Base64.getDecoder().decode(comma >= 0 ? embedded.substring(comma + 1) : embedded), true);
                        decoded.add(image);
                    }
                }
                tilesets.put(uid, new LdtkTileset(uid, text(node, "identifier", ""), rel, integer(node, "tileGridSize"), integer(node, "spacing"), integer(node, "padding"), integer(node, "pxWid"), integer(node, "pxHei"), strings(node.path("tags")), image));
            }
            List<LdtkDefinition.Layer> layerDefs = new ArrayList<>();
            for (JsonNode node : defs.path("layers")) {
                List<LdtkDefinition.IntGridValue> values = new ArrayList<>();
                for (JsonNode value : node.path("intGridValues"))
                    values.add(new LdtkDefinition.IntGridValue(integer(value, "value"), text(value, "identifier", ""), text(value, "color", "")));
                layerDefs.add(new LdtkDefinition.Layer(integer(node, "uid"), text(node, "identifier", ""), text(node, "type", ""), integer(node, "gridSize"), nullableInt(node, "tilesetDefUid", -1), values));
            }
            List<LdtkDefinition.Entity> entityDefs = new ArrayList<>();
            for (JsonNode node : defs.path("entities"))
                entityDefs.add(new LdtkDefinition.Entity(integer(node, "uid"), text(node, "identifier", ""), integer(node, "width"), integer(node, "height"), text(node, "color", ""), strings(node.path("tags"))));
            List<LdtkDefinition.Enum> enumDefs = new ArrayList<>();
            parseEnums(defs.path("enums"), enumDefs);
            parseEnums(defs.path("externalEnums"), enumDefs);

            List<LdtkLevel> levels = new ArrayList<>();
            JsonNode sourceLevels = root.has("worlds") && root.path("worlds").isArray() && !root.path("worlds").isEmpty() ? null : root.path("levels");
            if (sourceLevels != null) parseLevels(sourceLevels, bytes, path, resolver, levels, decoded);
            else for (JsonNode world : root.path("worlds"))
                parseLevels(world.path("levels"), bytes, path, resolver, levels, decoded);

            return new LdtkProject(text(root, "iid", ""), text(root, "jsonVersion", ""), text(root, "appBuildId", ""), text(root, "worldLayout", null), text(root, "bgColor", "#000000"), integer(root, "defaultGridSize"), integer(root, "defaultLevelWidth"), integer(root, "defaultLevelHeight"), levels, tilesets, layerDefs, entityDefs, enumDefs);
        } catch (Exception e) {
            decoded.forEach(TextureData::dispose);
            throw new RuntimeException("Failed to parse LDtk project: " + path, e);
        }
    }

    /**
     * Parses embedded or external level stubs and appends them in encounter order.
     *
     * @param nodes level stub array
     * @param parentBytes bytes containing the stubs
     * @param parentPath logical path containing the stubs
     * @param resolver dependency resolver for external levels and backgrounds
     * @param out destination level list
     * @param decoded ownership list used to clean up partially decoded images
     * @throws Exception if a dependency cannot be resolved or decoded
     */
    private static void parseLevels(JsonNode nodes, byte[] parentBytes, String parentPath, LdtkDependencyResolver resolver, List<LdtkLevel> out, List<TextureData> decoded) throws Exception {
        for (JsonNode stub : nodes) {
            String external = text(stub, "externalRelPath", null);
            JsonNode node = stub;
            String levelPath = parentPath;
            byte[] levelBytes = parentBytes;
            if (external != null && !external.isBlank()) {
                levelBytes = resolver.resolve(parentBytes, parentPath, external);
                node = JSON.readTree(levelBytes);
                levelPath = LdtkResolvers.resolve(parentPath, external).toString();
            }
            List<LdtkLayer> layers = new ArrayList<>();
            for (JsonNode layer : node.path("layerInstances")) layers.add(parseLayer(layer));
            LdtkBackground background = null;
            String bgPath = text(node, "bgRelPath", null);
            if (bgPath != null && !bgPath.isBlank()) {
                TextureData image = TextureData.load(resolver.resolve(levelBytes, levelPath, bgPath), true);
                decoded.add(image);
                JsonNode pos = node.path("__bgPos");
                JsonNode crop = pos.path("cropRect");
                JsonNode scale = pos.path("scale");
                JsonNode topLeft = pos.path("topLeftPx");
                background = new LdtkBackground(bgPath, arrayFloat(topLeft, 0), arrayFloat(topLeft, 1), scale.isNumber() ? (float) scale.asDouble() : arrayFloat(scale, 0), scale.isNumber() ? (float) scale.asDouble() : arrayFloat(scale, 1), arrayInt(crop, 0), arrayInt(crop, 1), arrayInt(crop, 2), arrayInt(crop, 3), image);
            }
            out.add(new LdtkLevel(text(node, "identifier", text(stub, "identifier", "")), text(node, "iid", text(stub, "iid", "")), integer(node, "uid", integer(stub, "uid")), integer(node, "worldX", integer(stub, "worldX")), integer(node, "worldY", integer(stub, "worldY")), integer(node, "pxWid", integer(stub, "pxWid")), integer(node, "pxHei", integer(stub, "pxHei")), text(node, "bgColor", text(stub, "bgColor", "")), background, layers, fields(node.path("fieldInstances")), external));
        }
    }

    /**
     * Decodes one LDtk layer and all of its tiles, entities, fields, and IntGrid data.
     *
     * @param node layer-instance JSON object
     * @return immutable decoded layer
     */
    private static LdtkLayer parseLayer(JsonNode node) {
        List<LdtkTile> grid = tiles(node.path("gridTiles")), auto = tiles(node.path("autoLayerTiles"));
        List<LdtkEntity> entities = new ArrayList<>();
        for (JsonNode entity : node.path("entityInstances"))
            entities.add(new LdtkEntity(text(entity, "__identifier", ""), text(entity, "iid", ""), arrayInt(entity.path("px"), 0), arrayInt(entity.path("px"), 1), integer(entity, "width"), integer(entity, "height"), arrayFloat(entity.path("__pivot"), 0), arrayFloat(entity.path("__pivot"), 1), integer(entity, "defUid"), strings(entity.path("__tags")), fields(entity.path("fieldInstances"))));
        return new LdtkLayer(text(node, "__identifier", ""), text(node, "iid", ""), text(node, "__type", ""), integer(node, "layerDefUid"), integer(node, "__gridSize"), integer(node, "__cWid"), integer(node, "__cHei"), integer(node, "__pxTotalOffsetX", integer(node, "pxOffsetX")), integer(node, "__pxTotalOffsetY", integer(node, "pxOffsetY")), nullableInt(node, "__tilesetDefUid", -1), text(node, "__tilesetRelPath", null), (float) number(node, "__opacity", 1), node.path("visible").asBoolean(true), ints(node.path("intGridCsv")), grid, auto, entities, fields(node.path("fieldInstances")));
    }

    /**
     * Decodes tile placements from an array, preserving source order.
     *
     * @param nodes tile JSON array or missing node
     * @return mutable parse-time list later snapshotted by its owning layer
     */
    private static List<LdtkTile> tiles(JsonNode nodes) {
        List<LdtkTile> result = new ArrayList<>();
        for (JsonNode tile : nodes)
            result.add(new LdtkTile(arrayInt(tile.path("px"), 0), arrayInt(tile.path("px"), 1), arrayInt(tile.path("src"), 0), arrayInt(tile.path("src"), 1), integer(tile, "t"), integer(tile, "f"), (float) number(tile, "a", 1)));
        return result;
    }

    /**
     * Decodes field instances into an insertion-ordered identifier map.
     *
     * @param nodes field-instance JSON array or missing node
     * @return mutable parse-time map later snapshotted by its owner
     */
    private static Map<String, LdtkField> fields(JsonNode nodes) {
        Map<String, LdtkField> fields = new LinkedHashMap<>();
        for (JsonNode node : nodes) {
            String id = text(node, "__identifier", "");
            fields.put(id, new LdtkField(id, text(node, "__type", ""), value(node.get("__value"))));
        }
        return fields;
    }

    /**
     * Converts a JSON value recursively into scalars and immutable lists or maps.
     * Unknown node kinds fall back to their JSON text representation.
     *
     * @param node value node, possibly null or JSON null
     * @return decoded value, or {@code null}
     */
    private static Object value(JsonNode node) {
        if (node == null || node.isNull()) return null;
        if (node.isTextual()) return node.textValue();
        if (node.isBoolean()) return node.booleanValue();
        if (node.isIntegralNumber()) return node.longValue();
        if (node.isFloatingPointNumber()) return node.doubleValue();
        if (node.isArray()) {
            List<Object> values = new ArrayList<>();
            node.forEach(it -> values.add(value(it)));
            return Collections.unmodifiableList(values);
        }
        if (node.isObject()) {
            Map<String, Object> values = new LinkedHashMap<>();
            node.fields().forEachRemaining(it -> values.put(it.getKey(), value(it.getValue())));
            return Collections.unmodifiableMap(values);
        }
        return node.toString();
    }

    /**
     * Appends enum definitions while preserving declaration order and value colors.
     *
     * @param nodes enum-definition array
     * @param out destination shared by internal and external definitions
     */
    private static void parseEnums(JsonNode nodes, List<LdtkDefinition.Enum> out) {
        for (JsonNode node : nodes) {
            Map<String, String> values = new LinkedHashMap<>();
            for (JsonNode value : node.path("values")) values.put(text(value, "id", ""), text(value, "color", ""));
            out.add(new LdtkDefinition.Enum(integer(node, "uid"), text(node, "identifier", ""), values));
        }
    }

    /**
     * Reads text from an object property with null-aware fallback behavior.
     *
     * @param n containing JSON object
     * @param key property name
     * @param fallback value used for absent or JSON-null properties
     * @return decoded text or fallback
     */
    private static String text(JsonNode n, String key, String fallback) {
        JsonNode v = n.get(key);
        return v == null || v.isNull() ? fallback : v.asText();
    }

    /**
     * Reads an integer property, defaulting absent or null data to zero.
     *
     * @param n containing JSON object
     * @param key property name
     * @return decoded integer or zero
     */
    private static int integer(JsonNode n, String key) {
        return integer(n, key, 0);
    }

    /**
     * Reads an integer property with a caller-selected fallback.
     *
     * @param n containing JSON object
     * @param key property name
     * @param fallback value used for absent, null, or incompatible data
     * @return decoded integer or fallback
     */
    private static int integer(JsonNode n, String key, int fallback) {
        JsonNode v = n.get(key);
        return v == null || v.isNull() ? fallback : v.asInt(fallback);
    }

    /**
     * Reads a nullable integer-valued LDtk reference using its sentinel fallback.
     *
     * @param n containing JSON object
     * @param key property name
     * @param fallback sentinel used for absent or null data
     * @return decoded integer or fallback
     */
    private static int nullableInt(JsonNode n, String key, int fallback) {
        return integer(n, key, fallback);
    }

    /**
     * Reads a numeric property as a double.
     *
     * @param n containing JSON object
     * @param key property name
     * @param fallback value used for absent, null, or incompatible data
     * @return decoded number or fallback
     */
    private static double number(JsonNode n, String key, double fallback) {
        JsonNode v = n.get(key);
        return v == null || v.isNull() ? fallback : v.asDouble(fallback);
    }

    /**
     * Reads an integer array element with a safe zero fallback.
     *
     * @param n expected JSON array
     * @param i zero-based element index
     * @return decoded integer, or zero when the node or index is unsuitable
     */
    private static int arrayInt(JsonNode n, int i) {
        return n.isArray() && n.size() > i ? n.get(i).asInt() : 0;
    }

    /**
     * Reads a floating-point array element with a safe zero fallback.
     *
     * @param n expected JSON array
     * @param i zero-based element index
     * @return decoded float, or zero when the node or index is unsuitable
     */
    private static float arrayFloat(JsonNode n, int i) {
        return n.isArray() && n.size() > i ? (float) n.get(i).asDouble() : 0;
    }

    /**
     * Copies a JSON integer array into primitive row-major storage.
     *
     * @param n expected JSON array
     * @return decoded values, or an empty array for non-array input
     */
    private static int[] ints(JsonNode n) {
        if (!n.isArray()) return new int[0];
        int[] v = new int[n.size()];
        for (int i = 0; i < v.length; i++) v[i] = n.get(i).asInt();
        return v;
    }

    /**
     * Copies a JSON array into a list of text values.
     *
     * @param n expected JSON array
     * @return decoded strings, or an immutable empty list for non-array input
     */
    private static List<String> strings(JsonNode n) {
        if (!n.isArray()) return List.of();
        List<String> v = new ArrayList<>();
        n.forEach(it -> v.add(it.asText()));
        return v;
    }

    /**
     * Returns the LDtk project instance identifier.
     *
     * @return project IID, possibly empty when omitted by the source document
     */
    public String iid() {
        return iid;
    }

    /**
     * Returns the LDtk JSON schema version declared by the project.
     *
     * @return JSON version string
     */
    public String jsonVersion() {
        return jsonVersion;
    }

    /**
     * Returns the LDtk application build identifier that wrote the project.
     *
     * @return application build identifier
     */
    public String appBuildId() {
        return appBuildId;
    }

    /**
     * Returns the configured world layout name.
     *
     * @return layout name, or {@code null} when the project does not declare one
     */
    public String worldLayout() {
        return worldLayout;
    }

    /**
     * Returns the project-wide fallback background color.
     *
     * @return LDtk color string, defaulting to black during parsing
     */
    public String backgroundColor() {
        return backgroundColor;
    }

    /**
     * Returns the default grid size used by new levels and layers.
     *
     * @return default cell size in pixels
     */
    public int defaultGridSize() {
        return defaultGridSize;
    }

    /**
     * Returns the project's default level width.
     *
     * @return width in pixels
     */
    public int defaultLevelWidth() {
        return defaultLevelWidth;
    }

    /**
     * Returns the project's default level height.
     *
     * @return height in pixels
     */
    public int defaultLevelHeight() {
        return defaultLevelHeight;
    }

    /**
     * Returns all decoded levels in source order across embedded worlds.
     *
     * @return immutable level list
     */
    public List<LdtkLevel> levels() {
        return levels;
    }

    /**
     * Returns all tileset definitions indexed by UID.
     *
     * @return immutable tileset map
     */
    public Map<Integer, LdtkTileset> tilesets() {
        return tilesets;
    }

    /**
     * Returns project-level layer definitions in declaration order.
     *
     * @return immutable layer-definition list
     */
    public List<LdtkDefinition.Layer> layerDefinitions() {
        return layerDefinitions;
    }

    /**
     * Returns project-level entity definitions in declaration order.
     *
     * @return immutable entity-definition list
     */
    public List<LdtkDefinition.Entity> entityDefinitions() {
        return entityDefinitions;
    }

    /**
     * Returns internal and external enum definitions in parse order.
     *
     * @return immutable enum-definition list
     */
    public List<LdtkDefinition.Enum> enumDefinitions() {
        return enumDefinitions;
    }

    /**
     * Finds the first level whose identifier exactly matches the argument.
     *
     * @param identifier level identifier to find
     * @return matching level, or {@code null} when absent
     */
    public LdtkLevel level(String identifier) {
        return levels.stream().filter(it -> it.identifier().equals(identifier)).findFirst().orElse(null);
    }

    /**
     * Finds a tileset definition by its numeric UID.
     *
     * @param uid tileset definition UID
     * @return matching tileset, or {@code null} when absent
     */
    public LdtkTileset tileset(int uid) {
        return tilesets.get(uid);
    }

    /**
     * Creates a GPU-backed renderer for this project.
     * The caller must invoke {@link LdtkMap#dispose()} on the render thread.
     *
     * @return newly allocated map with textures uploaded from decoded images
     * @throws RuntimeException if GPU texture creation fails
     */
    public LdtkMap asMap() {
        return new LdtkMap(this);
    }

    /**
     * Releases every decoded tileset and background image owned by this project.
     * This method does not dispose maps previously created with {@link #asMap()}.
     * Call it only after no further map creation or texture-data access is needed.
     */
    public void dispose() {
        tilesets.values().stream().map(LdtkTileset::textureData).filter(Objects::nonNull).forEach(TextureData::dispose);
        levels.stream().map(LdtkLevel::background).filter(Objects::nonNull).map(LdtkBackground::textureData).filter(Objects::nonNull).forEach(TextureData::dispose);
    }
}
