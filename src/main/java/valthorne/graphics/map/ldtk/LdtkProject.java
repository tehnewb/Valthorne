package valthorne.graphics.map.ldtk;

import com.alibaba.fastjson2.JSONException;
import com.alibaba.fastjson2.JSONReader;
import com.alibaba.fastjson2.JSONValidator;
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
    private final String iid; // Project instance identifier.
    private final String jsonVersion; // LDtk JSON format version.
    private final String appBuildId; // LDtk application build identifier.
    private final String worldLayout; // Optional configured world layout.
    private final String backgroundColor; // Project background color.
    private final int defaultGridSize; // Default cell size in pixels.
    private final int defaultLevelWidth; // Default level width in pixels.
    private final int defaultLevelHeight; // Default level height in pixels.
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
        /*
         * Read the parent once so dependency resolution sees the same project bytes.
         */
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
        /*
         * Track decoded images until the complete immutable project takes ownership.
         */
        Objects.requireNonNull(bytes, "bytes");
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(resolver, "resolver");
        List<TextureData> decoded = new ArrayList<>();
        try {
            Object root = document(bytes);
            Object defs = property(root, "defs");
            Map<Integer, LdtkTileset> tilesets = new LinkedHashMap<>();
            for (Object node : array(property(defs, "tilesets"))) {
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
                tilesets.put(uid, new LdtkTileset(uid, text(node, "identifier", ""), rel, integer(node, "tileGridSize"), integer(node, "spacing"), integer(node, "padding"), integer(node, "pxWid"), integer(node, "pxHei"), strings(property(node, "tags")), image));
            }
            List<LdtkDefinition.Layer> layerDefs = new ArrayList<>();
            for (Object node : array(property(defs, "layers"))) {
                List<LdtkDefinition.IntGridValue> values = new ArrayList<>();
                for (Object value : array(property(node, "intGridValues")))
                    values.add(new LdtkDefinition.IntGridValue(integer(value, "value"), text(value, "identifier", ""), text(value, "color", "")));
                layerDefs.add(new LdtkDefinition.Layer(integer(node, "uid"), text(node, "identifier", ""), text(node, "type", ""), integer(node, "gridSize"), nullableInt(node, "tilesetDefUid", -1), values));
            }
            List<LdtkDefinition.Entity> entityDefs = new ArrayList<>();
            for (Object node : array(property(defs, "entities")))
                entityDefs.add(new LdtkDefinition.Entity(integer(node, "uid"), text(node, "identifier", ""), integer(node, "width"), integer(node, "height"), text(node, "color", ""), strings(property(node, "tags"))));
            List<LdtkDefinition.Enum> enumDefs = new ArrayList<>();
            parseEnums(property(defs, "enums"), enumDefs);
            parseEnums(property(defs, "externalEnums"), enumDefs);

            List<LdtkLevel> levels = new ArrayList<>();
            Object sourceLevels = !array(property(root, "worlds")).isEmpty() ? null : property(root, "levels");
            if (sourceLevels != null) parseLevels(sourceLevels, bytes, path, resolver, levels, decoded);
            else for (Object world : array(property(root, "worlds")))
                parseLevels(property(world, "levels"), bytes, path, resolver, levels, decoded);

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
    private static void parseLevels(Object nodes, byte[] parentBytes, String parentPath, LdtkDependencyResolver resolver, List<LdtkLevel> out, List<TextureData> decoded) throws Exception {
        /*
         * Resolve each external level relative to its own document before loading images.
         */
        for (Object stub : array(nodes)) {
            String external = text(stub, "externalRelPath", null);
            Object node = stub;
            String levelPath = parentPath;
            byte[] levelBytes = parentBytes;
            if (external != null && !external.isBlank()) {
                levelBytes = resolver.resolve(parentBytes, parentPath, external);
                node = document(levelBytes);
                levelPath = LdtkResolvers.resolve(parentPath, external).toString();
            }
            List<LdtkLayer> layers = new ArrayList<>();
            for (Object layer : array(property(node, "layerInstances"))) layers.add(parseLayer(layer));
            LdtkBackground background = null;
            String bgPath = text(node, "bgRelPath", null);
            if (bgPath != null && !bgPath.isBlank()) {
                TextureData image = TextureData.load(resolver.resolve(levelBytes, levelPath, bgPath), true);
                decoded.add(image);
                Object pos = property(node, "__bgPos");
                Object crop = property(pos, "cropRect");
                Object scale = property(pos, "scale");
                Object topLeft = property(pos, "topLeftPx");
                background = new LdtkBackground(bgPath, arrayFloat(topLeft, 0), arrayFloat(topLeft, 1), scale instanceof Number size ? size.floatValue() : arrayFloat(scale, 0), scale instanceof Number size ? size.floatValue() : arrayFloat(scale, 1), arrayInt(crop, 0), arrayInt(crop, 1), arrayInt(crop, 2), arrayInt(crop, 3), image);
            }
            out.add(new LdtkLevel(text(node, "identifier", text(stub, "identifier", "")), text(node, "iid", text(stub, "iid", "")), integer(node, "uid", integer(stub, "uid")), integer(node, "worldX", integer(stub, "worldX")), integer(node, "worldY", integer(stub, "worldY")), integer(node, "pxWid", integer(stub, "pxWid")), integer(node, "pxHei", integer(stub, "pxHei")), text(node, "bgColor", text(stub, "bgColor", "")), background, layers, fields(property(node, "fieldInstances")), external));
        }
    }

    /**
     * Decodes one LDtk layer and all of its tiles, entities, fields, and IntGrid data.
     *
     * @param node layer-instance JSON object
     * @return immutable decoded layer
     */
    private static LdtkLayer parseLayer(Object node) {
        /*
         * Decode each placement array once before constructing the immutable layer.
         */
        List<LdtkTile> grid = tiles(property(node, "gridTiles")), auto = tiles(property(node, "autoLayerTiles"));
        List<LdtkEntity> entities = new ArrayList<>();
        for (Object entity : array(property(node, "entityInstances")))
            entities.add(new LdtkEntity(text(entity, "__identifier", ""), text(entity, "iid", ""), arrayInt(property(entity, "px"), 0), arrayInt(property(entity, "px"), 1), integer(entity, "width"), integer(entity, "height"), arrayFloat(property(entity, "__pivot"), 0), arrayFloat(property(entity, "__pivot"), 1), integer(entity, "defUid"), strings(property(entity, "__tags")), fields(property(entity, "fieldInstances"))));
        return new LdtkLayer(text(node, "__identifier", ""), text(node, "iid", ""), text(node, "__type", ""), integer(node, "layerDefUid"), integer(node, "__gridSize"), integer(node, "__cWid"), integer(node, "__cHei"), integer(node, "__pxTotalOffsetX", integer(node, "pxOffsetX")), integer(node, "__pxTotalOffsetY", integer(node, "pxOffsetY")), nullableInt(node, "__tilesetDefUid", -1), text(node, "__tilesetRelPath", null), (float) number(node, "__opacity", 1), flag(property(node, "visible"), true), ints(property(node, "intGridCsv")), grid, auto, entities, fields(property(node, "fieldInstances")));
    }

    /**
     * Decodes tile placements from an array, preserving source order.
     *
     * @param nodes tile JSON array or missing node
     * @return mutable parse-time list later snapshotted by its owning layer
     */
    private static List<LdtkTile> tiles(Object nodes) {
        /*
         * Preserve source order because overlapping tile placements depend on it.
         */
        List<LdtkTile> result = new ArrayList<>();
        for (Object tile : array(nodes))
            result.add(new LdtkTile(arrayInt(property(tile, "px"), 0), arrayInt(property(tile, "px"), 1), arrayInt(property(tile, "src"), 0), arrayInt(property(tile, "src"), 1), integer(tile, "t"), integer(tile, "f"), (float) number(tile, "a", 1)));
        return result;
    }

    /**
     * Decodes field instances into an insertion-ordered identifier map.
     *
     * @param nodes field-instance JSON array or missing node
     * @return mutable parse-time map later snapshotted by its owner
     */
    private static Map<String, LdtkField> fields(Object nodes) {
        /*
         * Preserve nullable and nested field values while snapshotting their containers.
         */
        Map<String, LdtkField> fields = new LinkedHashMap<>();
        for (Object node : array(nodes)) {
            String id = text(node, "__identifier", "");
            fields.put(id, new LdtkField(id, text(node, "__type", ""), value(property(node, "__value"))));
        }
        return fields;
    }

    /**
     * Snapshots fastjson2 values into the immutable scalar and collection API.
     * Integral field values remain longs; decimal field values become doubles.
     *
     * @param node decoded field value, possibly null
     * @return scalar value or immutable collection retaining null elements
     */
    private static Object value(Object node) {
        /*
         * Copy only containers; scalar values are immutable. Preserve explicit
         * nulls because LDtk nullable fields and array entries depend on them.
         */
        if (node instanceof Map<?, ?> object) {
            Map<String, Object> values = new LinkedHashMap<>(object.size());
            for (Map.Entry<?, ?> entry : object.entrySet())
                values.put((String) entry.getKey(), value(entry.getValue()));
            return Collections.unmodifiableMap(values);
        }
        if (node instanceof List<?> array) {
            List<Object> values = new ArrayList<>(array.size());
            for (Object element : array) values.add(value(element));
            return Collections.unmodifiableList(values);
        }
        if (node instanceof Number number) {
            if (number instanceof Long || number instanceof Integer) return number.longValue();
            return number.doubleValue();
        }
        return node;
    }

    /**
     * Appends enum definitions while preserving declaration order and value colors.
     *
     * @param nodes enum-definition array
     * @param out destination shared by internal and external definitions
     */
    private static void parseEnums(Object nodes, List<LdtkDefinition.Enum> out) {
        /*
         * Keep enum declaration order stable across internal and external definitions.
         */
        for (Object node : array(nodes)) {
            Map<String, String> values = new LinkedHashMap<>();
            for (Object value : array(property(node, "values"))) values.put(text(value, "id", ""), text(value, "color", ""));
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
    private static String text(Object n, String key, String fallback) {
        /*
         * Containers are not text values. Do not invoke JSON container toString,
         * which would serialize an unexpected schema value unnecessarily.
         */
        Object value = property(n, key);
        return value == null ? fallback : scalarText(value);
    }

    /**
     * Reads an integer property with zero for missing or incompatible values.
     *
     * @param n containing object
     * @param key property name
     * @return decoded integer or zero
     */
    private static int integer(Object n, String key) {
        /*
         * Share the fallback conversion used by nullable LDtk references.
         */
        return integer(n, key, 0);
    }

    /**
     * Reads an integer property with a caller-selected fallback.
     *
     * @param n containing object
     * @param key property name
     * @param fallback absent, null, or incompatible value fallback
     * @return decoded integer or fallback
     */
    private static int integer(Object n, String key, int fallback) {
        /*
         * Convert directly from numeric storage without decimal text allocation.
         */
        return integerValue(property(n, key), fallback);
    }

    /**
     * Reads a nullable integer reference with its sentinel fallback.
     *
     * @param n containing object
     * @param key property name
     * @param fallback sentinel for absent or null references
     * @return decoded reference or sentinel
     */
    private static int nullableInt(Object n, String key, int fallback) {
        /*
         * Nullable references use the same scalar rules as ordinary integers.
         */
        return integer(n, key, fallback);
    }

    /**
     * Reads a numeric property as a double.
     *
     * @param n containing object
     * @param key property name
     * @param fallback absent, null, or incompatible value fallback
     * @return decoded number or fallback
     */
    private static double number(Object n, String key, double fallback) {
        /*
         * Keep schema conversion separate from the JSON reader's token handling.
         */
        return numeric(property(n, key), fallback);
    }

    /**
     * Reads an integer array element with zero for unsuitable input.
     *
     * @param n expected array
     * @param i zero-based element index
     * @return decoded integer or zero
     */
    private static int arrayInt(Object n, int i) {
        /*
         * Bounds checking also handles omitted optional coordinate arrays.
         */
        List<?> values = array(n);
        return i >= 0 && i < values.size() ? integerValue(values.get(i), 0) : 0;
    }

    /**
     * Reads a floating-point array element with zero for unsuitable input.
     *
     * @param n expected array
     * @param i zero-based element index
     * @return decoded coordinate or zero
     */
    private static float arrayFloat(Object n, int i) {
        /*
         * Convert only the requested coordinate without copying its array.
         */
        List<?> values = array(n);
        return i >= 0 && i < values.size() ? (float) numeric(values.get(i), 0) : 0;
    }

    /**
     * Copies an integer array into primitive row-major storage.
     *
     * @param n expected array
     * @return decoded values or an empty array
     */
    private static int[] ints(Object n) {
        /*
         * Allocate exactly the final primitive storage required by IntGrid.
         */
        List<?> values = array(n);
        int[] result = new int[values.size()];
        for (int i = 0; i < result.length; i++) result[i] = integerValue(values.get(i), 0);
        return result;
    }

    /**
     * Copies a schema array into text values, preserving order.
     *
     * @param n expected array
     * @return decoded strings or an empty list
     */
    private static List<String> strings(Object n) {
        /*
         * Avoid serializing containers passed in place of scalar tag values.
         */
        List<?> values = array(n);
        List<String> result = new ArrayList<>(values.size());
        for (Object element : values) result.add(scalarText(element));
        return result;
    }

    /**
     * Parses one object document using fastjson2 without bean binding or AutoType.
     *
     * @param bytes UTF-8 project or external-level document
     * @return mutable parse-time object tree owned by this loading operation
     * @throws JSONException if the input is not one complete object document
     */
    private static Map<String, Object> document(byte[] bytes) {
        /*
         * A fresh context confines nesting limits and suppliers to this load.
         * Native containers keep the tree independent of serialization APIs.
         */
        JSONReader.Context context = new JSONReader.Context(null, JSONReader.Feature.DisableReferenceDetect.mask | JSONReader.Feature.DisableSingleQuote.mask | JSONReader.Feature.UseLongForInts.mask | JSONReader.Feature.UseDoubleForDecimals.mask);
        context.setMaxLevel(256);
        context.setObjectSupplier(LinkedHashMap::new);
        context.setArraySupplier(ArrayList::new);
        try (JSONReader reader = JSONReader.of(bytes, context)) {
            if (reader.current() != '{') throw new JSONException("Expected an LDtk object document");
            Map<String, Object> root = reader.readObject();
            if (!reader.isEnd() || reader.hasComma()) throw new JSONException("Unexpected data after LDtk document");
            /*
             * The tree reader accepts some incomplete numeric tokens. Validate
             * with fastjson2's separate token checker before loading images.
             * Parsing first enforces the nesting bound on the validation pass.
             */
            try (JSONReader validation = JSONReader.of(bytes, context)) {
                if (!JSONValidator.from(validation).validate()) throw new JSONException("Invalid LDtk document");
            }
            return root;
        }
    }

    /**
     * Looks up a schema property without allocating a missing-node wrapper.
     *
     * @param object expected object
     * @param key property name
     * @return property value or null for absent or unsuitable input
     */
    private static Object property(Object object, String key) {
        /*
         * Missing and JSON-null schema properties share the documented fallback.
         */
        return object instanceof Map<?, ?> map ? map.get(key) : null;
    }

    /**
     * Views a schema array without copying its elements.
     *
     * @param value expected array
     * @return array view or an immutable empty list
     */
    private static List<?> array(Object value) {
        /*
         * Optional missing arrays need no allocated placeholder object.
         */
        return value instanceof List<?> list ? list : List.of();
    }

    /**
     * Converts a scalar to its text spelling with empty text for containers.
     *
     * @param value decoded value, possibly null
     * @return scalar spelling or empty text for containers
     */
    private static String scalarText(Object value) {
        /*
         * Explicit scalar cases avoid invoking arbitrary object serialization.
         */
        if (value == null) return "null";
        if (value instanceof String text) return text;
        if (value instanceof Number number) return number.toString();
        if (value instanceof Boolean flag) return flag ? "true" : "false";
        return "";
    }

    /**
     * Coerces a schema numeric scalar while retaining previous fallback behavior.
     *
     * @param value decoded numeric, Boolean, or numeric-text scalar
     * @param fallback value for null or incompatible input
     * @return numeric value or fallback
     */
    private static double numeric(Object value, double fallback) {
        /*
         * Most schema values are already numbers. Text coercion is a cold path
         * for older documents that encoded numeric metadata as strings.
         */
        if (value instanceof Number number) return number.doubleValue();
        if (value instanceof Boolean flag) return flag ? 1 : 0;
        if (value instanceof String text) {
            try {
                return Double.parseDouble(text.trim());
            } catch (NumberFormatException failure) {
                return fallback;
            }
        }
        return fallback;
    }

    /**
     * Converts integer metadata without routing exact longs through double.
     *
     * @param value decoded scalar
     * @param fallback value for missing or incompatible input
     * @return narrowed integer or fallback
     */
    private static int integerValue(Object value, int fallback) {
        /*
         * Preserve Java's numeric narrowing behavior for integral JSON values.
         */
        return value instanceof Number number ? number.intValue() : (int) numeric(value, fallback);
    }

    /**
     * Reads an optional schema Boolean with a caller-selected fallback.
     *
     * @param value decoded Boolean, number, or Boolean text
     * @param fallback value for null or incompatible input
     * @return decoded flag or fallback
     */
    private static boolean flag(Object value, boolean fallback) {
        /*
         * Retain the scalar flag coercions accepted by existing LDtk loading.
         */
        if (value instanceof Boolean flag) return flag;
        if (value instanceof Number number) return number.doubleValue() != 0;
        if (value instanceof String text) {
            text = text.trim();
            if ("true".equals(text)) return true;
            if ("false".equals(text)) return false;
        }
        return fallback;
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
