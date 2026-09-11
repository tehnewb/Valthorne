# Tiled maps and tilesets

Author: Albert Beaupre

[System manual](README.md)

## Purpose

The Tiled integration separates parsed TMX/TSX data from runtime map resources. Sources and dependency resolvers supply the map, external tilesets, and images; TiledMap creates the runtime representation. Layers include finite tile data, chunks, objects, and images.

## Features and when to use them

| Feature | Purpose |
| --- | --- |
| Sources and resolvers | Filesystem, classpath, and in-memory dependency lookup support different packaging strategies. |
| Layer types | Tile, image, and object layers carry different data and rendering responsibilities. |
| Tile resolution | Global IDs map to tilesets and local definitions, retaining flip flags when required. |
| Properties and animations | Custom properties and tile-animation frames carry authored content metadata. |
| Encoded data | CSV and Base64 payloads support specific compression paths. |

## Getting started

1. Choose a map source and a dependency resolver that can find every referenced file.
2. Load CPU map data through parameters/loader, then create runtime textures on the graphics thread.
3. Render supported layers in their authored order and inspect object/property data for game logic.
4. Dispose runtime map resources after their consumers stop drawing.

## Ownership and lifecycle

Many parsed arrays and maps are retained by reference. Runtime textures have a separate lifetime from XML bytes. Source-relative aliases and classpath roots must match authored dependency paths.

## Important behavior

- Finite `getTile` queries use a bottom-left tile origin and do not query infinite chunks.
- CSV can pad to an expected count; binary decoding returns only available complete IDs.
- Recognition of a layer does not mean every Tiled feature has a runtime renderer.

## Components and examples

The sections below explain each component and its declared public or protected operations. Expand an operation reference for signatures, parameters, results, and edge cases. Inherited operations are documented with their base type. Examples embedded in component descriptions are integration fragments: supply the surrounding resources and application variables they name.

- [`FileSystemResolver`](#type-filesystemresolver)
- [`MapChunk`](#type-mapchunk)
- [`MapLayer`](#type-maplayer)
- [`ResolvedTile`](#type-resolvedtile)
- [`TileAnimationFrame`](#type-tileanimationframe)
- [`TiledDecoding`](#type-tileddecoding)
- [`TiledDependencyResolver`](#type-tileddependencyresolver)
- [`TiledDependencySource`](#type-tileddependencysource)
- [`TiledDependencySource.FileSystemSource`](#type-tileddependencysource-filesystemsource)
- [`TiledDependencySource.MapSource`](#type-tileddependencysource-mapsource)
- [`TileDefinition`](#type-tiledefinition)
- [`TiledImageMapLayer`](#type-tiledimagemaplayer)
- [`TiledImageMapLayerData`](#type-tiledimagemaplayerdata)
- [`TiledMap`](#type-tiledmap)
- [`TiledMapData`](#type-tiledmapdata)
- [`TiledMapLoader`](#type-tiledmaploader)
- [`TiledMapParameters`](#type-tiledmapparameters)
- [`TiledMapSource`](#type-tiledmapsource)
- [`TiledMapSource.PathSource`](#type-tiledmapsource-pathsource)
- [`TiledMapSource.BytesSource`](#type-tiledmapsource-bytessource)
- [`TiledObject`](#type-tiledobject)
- [`TiledObjectMapLayer`](#type-tiledobjectmaplayer)
- [`TiledResolvers`](#type-tiledresolvers)
- [`TiledResolvers.InMemoryResolver`](#type-tiledresolvers-inmemoryresolver)
- [`TiledShapeType`](#type-tiledshapetype)
- [`TiledTileMapLayer`](#type-tiledtilemaplayer)
- [`TiledXML`](#type-tiledxml)
- [`TileSet`](#type-tileset)
- [`TileSetData`](#type-tilesetdata)

<a id="type-filesystemresolver"></a>

### FileSystemResolver

[Source](../../src/main/java/valthorne/graphics/map/tiled/FileSystemResolver.java#L17)

Reads Tiled dependencies from the filesystem relative to their referring file.
Absolute dependency paths are used directly; relative paths use the normalized
parent's directory, falling back to the process working directory when unavailable.
Resolution performs a fresh synchronous read and does not cache content or restrict
paths to the map's directory. Parent bytes are not needed by this implementation.

<details>
<summary>FileSystemResolver operation reference (1 declarations)</summary>

#### resolve

```java
    public byte[] resolve(byte[] parentBytes, String parentPath, String dependencyPath) throws Exception
```

Resolves and normalizes a dependency to an absolute path, then reads all bytes.
A blank or null parent path uses the working directory for relative dependencies.
The returned array is newly allocated by the file read.

- **`parentBytes`** — unused parent content; may be null
- **`parentPath`** — referring file's path, or null when unavailable
- **`dependencyPath`** — absolute or relative dependency path

**Returns:** complete file contents

**Throws `NullPointerException`:** if dependencyPath is null

**Throws `Exception`:** if the path cannot be resolved or its bytes cannot be read

</details>

<a id="type-mapchunk"></a>

### MapChunk

[Source](../../src/main/java/valthorne/graphics/map/tiled/MapChunk.java#L21)

Represents a rectangular chunk of a map consisting of tiles.

A map chunk stores its position, size, and a one-dimensional array of global tile IDs
which define the tiles in the chunk. It is typically used to represent a portion of a
map layer. The `globalTileIDs` array length is expected to be equal to `width * height`.
A supplied array is retained directly and exposed by the generated accessor, so
component references are fixed but tile contents remain mutable. Array length and
dimensions are not validated. Null data allocates a zero-filled array whose length
is `Math.max(0, width * height)`, using ordinary integer multiplication.

- **`x`** — top-left column in tile units
- **`y`** — top-left row in tile units
- **`width`** — chunk width in tiles
- **`height`** — chunk height in tiles
- **`globalTileIDs`** — row-major tile IDs, retained directly, or null to allocate empty data

<details>
<summary>MapChunk operation reference (1 declarations)</summary>

#### Constructor

```java
public MapChunk
```

Initializes the `MapChunk` record component. If the provided `globalTileIDs` array is `null`,
it is initialized to a new array of zeros with length `Math.max(0, width * height)`.
Non-null arrays are neither copied nor checked against the dimensions.

- **`x`** — The x-coordinate of the top-left corner of the chunk in tile units.
- **`y`** — The y-coordinate of the top-left corner of the chunk in tile units.
- **`width`** — The width of the chunk in tiles.
- **`height`** — The height of the chunk in tiles.
- **`globalTileIDs`** — A one-dimensional array of global tile IDs defining the tiles in this chunk. If `null`, it is initialized to an array of zeros with a size equal to `width * height`.

</details>

<a id="type-maplayer"></a>

### MapLayer

[Source](../../src/main/java/valthorne/graphics/map/tiled/MapLayer.java#L17)

Represents a base class for various types of map layers.
It provides a common structure and functionality for handling map layers,
including properties such as name, visibility, opacity, and offsets.
This class is abstract and is intended to be extended by specific map layer types.
Scalar metadata is retained without range validation. Custom properties remain a
shared mutable map when supplied by the caller; the getter exposes that same map.
This base class allocates no rendering resources and does not draw layer contents.

<details>
<summary>MapLayer operation reference (12 declarations)</summary>

#### visible

```java
protected final boolean visible
```

Visible flag.

#### opacity

```java
protected final float opacity
```

Supplied opacity, conventionally 0..1 but not clamped.

#### offsetX

```java
protected final float offsetX
```

Pixel offset X.

#### offsetY

```java
protected final float offsetY
```

Pixel offset Y.

#### properties

```java
protected final Map<String, String> properties
```

Shared mutable custom properties, exposed by the getter.

#### Constructor

```java
public MapLayer(String name, boolean visible, float opacity, float offsetX, float offsetY, Map<String, String> properties)
```

Constructs a new MapLayer instance with the specified parameters.
Null names become empty strings and null properties allocate an empty mutable
map. Non-null properties are retained directly; opacity and offsets are not checked.

- **`name`** — the name of the map layer. If null, an empty string is assigned.
- **`visible`** — a boolean indicating whether the map layer is visible.
- **`opacity`** — the opacity of the map layer, specified as a float value between 0 and 1.
- **`offsetX`** — the horizontal pixel offset of the map layer.
- **`offsetY`** — the vertical pixel offset of the map layer.
- **`properties`** — a map of custom properties associated with the map layer. If null, an empty map is assigned.

#### getName

```java
public String getName()
```

Retrieves the name of the map layer.

**Returns:** the name of the map layer as a string.

#### isVisible

```java
public boolean isVisible()
```

Returns the visibility state of the map layer.

**Returns:** true if the map layer is visible, false otherwise.

#### getOpacity

```java
public float getOpacity()
```

Retrieves the opacity of the map layer.
The opacity is a value between 0 and 1, where 0 represents full transparency
and 1 represents full opacity by convention; the constructor does not enforce
this range, so direct construction may return other values.

**Returns:** the opacity of the map layer as a float.

#### getOffsetX

```java
public float getOffsetX()
```

Retrieves the horizontal pixel offset of the map layer.

**Returns:** the horizontal pixel offset as a float.

#### getOffsetY

```java
public float getOffsetY()
```

Retrieves the vertical pixel offset of the map layer.

**Returns:** the vertical pixel offset as a float.

#### getProperties

```java
public Map<String, String> getProperties()
```

Retrieves the custom properties associated with the map layer.
This is the live map, not a defensive snapshot. Mutations are visible to this
layer and to any caller that supplied the original map.

**Returns:** a map containing the custom properties of the map layer,
where the keys and values are strings. If no properties are defined,
an empty map is returned.

</details>

<a id="type-resolvedtile"></a>

### ResolvedTile

[Source](../../src/main/java/valthorne/graphics/map/tiled/ResolvedTile.java#L21)

Represents a resolved tile in a tiled map system. A ResolvedTile contains information
about its position, graphical ID, local identifier within its tileset, the associated
tileset, and the tile's defined properties.
References to the tileset and optional definition are borrowed rather than copied.
Construction performs no lookup, bounds checking, or validation that the IDs agree;
the resolving map supplies that relationship. Retaining this result does not extend
the lifetime of the tileset's rendering resources.

- **`tileX`** — The x-coordinate of the tile on the map grid.
- **`tileY`** — The y-coordinate of the tile on the map grid.
- **`rawGid`** — The raw global ID (GID) of the tile, including metadata bits.
- **`gid`** — The global ID of the tile, with metadata bits stripped.
- **`localId`** — The local ID of the tile within its associated tileset.
- **`tileSet`** — The tileset to which the tile belongs.
- **`definition`** — The specific properties and definition of the tile.

<a id="type-tileanimationframe"></a>

### TileAnimationFrame

[Source](../../src/main/java/valthorne/graphics/map/tiled/TileAnimationFrame.java#L20)

Represents a single frame of a tile animation within a tilemap.
Each frame is defined by a tile ID and its duration in milliseconds.
Used primarily to handle the sequence of animations for tiles.
Tile IDs are local to the owning tileset. Direct construction retains values without
validation; the XML loader supplies defaults and clamps loaded durations to at least
one millisecond. This record does not keep a playback clock.

- **`tileID`** — The ID of the tile for this frame.
- **`duration`** — The duration of this frame in milliseconds.

<details>
<summary>TileAnimationFrame operation reference (1 declarations)</summary>

#### load

```java
public static List<TileAnimationFrame> load(XMLStreamReader r) throws Exception
```

Loads a list of tile animation frames from an XML element. This function
parses the provided XMLStreamReader, extracts frame data (tile ID and duration),
and builds a list of `TileAnimationFrame` objects. The method stops when
the "animation" end element is encountered.
Position the reader on the opening animation element. Unknown child elements
are skipped; absent tile IDs default to -1 and absent durations to 100 ms.
Parsed durations are clamped to at least 1 ms. The returned mutable list follows
document order, and the reader remains open at the closing element or end of input.

- **`r`** — the `XMLStreamReader` to read XML data from

**Returns:** a `List` of `TileAnimationFrame` representing the animation frames

**Throws `Exception`:** if an error occurs while reading or processing the XML data

</details>

<a id="type-tileddecoding"></a>

### TiledDecoding

[Source](../../src/main/java/valthorne/graphics/map/tiled/TiledDecoding.java#L22)

Decodes TMX tile-layer payloads into raw global-ID bit patterns, including Tiled's
flip flags. CSV and MIME Base64 are supported; Base64 may additionally use gzip
or zlib compression. XML tile elements are not supported.

CSV can pad a requested result length with empty tiles. Binary decoding instead
returns only complete four-byte IDs that are actually present. Callers must not
assume every encoding produces exactly the requested count.

<details>
<summary>TiledDecoding operation reference (1 declarations)</summary>

#### decodeLayerData

```java
public static int[] decodeLayerData(String text, String encoding, String compression, int expectedCount) throws java.io.IOException
```

Decodes a layer or chunk payload without clearing the flip bits in its IDs.
Empty or null text produces `max(0, expectedCount)` zero IDs before encoding
validation. CSV ignores the compression argument; Base64 uses optional gzip or
zlib decompression. Neither path validates IDs against a tileset.

- **`text`** — encoded payload, or null for an empty layer
- **`encoding`** — case-insensitive CSV or Base64 encoding name
- **`compression`** — Base64 compression name, or null/blank for uncompressed bytes
- **`expectedCount`** — positive output limit; CSV pads to this length, while binary data may return fewer IDs; nonpositive values retain all available IDs

**Returns:** newly allocated array containing signed Java representations of ID bits

**Throws `java.io.IOException`:** if decompression or reading fails

**Throws `IllegalStateException`:** if nonempty data uses an unsupported encoding or compression

**Throws `IllegalArgumentException`:** if numeric CSV or Base64 data is malformed

</details>

<a id="type-tileddependencyresolver"></a>

### TiledDependencyResolver

[Source](../../src/main/java/valthorne/graphics/map/tiled/TiledDependencyResolver.java#L13)

Defines an interface for resolving dependencies in Tiled map structures or related assets
such as external tileset or image files. This allows implementations to provide functionality
for locating and loading resources needed by Tiled map components.
Resolution runs synchronously during loading. Implementations define the resource
namespace and may use the parent bytes, parent path, or both; this interface does
not impose filesystem access or cache decoded assets.

<details>
<summary>TiledDependencyResolver operation reference (1 declarations)</summary>

#### resolve

```java
byte[] resolve(byte[] parentBytes, String parentPath, String dependencyPath) throws Exception
```

Resolves a dependency file path relative to the given parent resource, and retrieves the
content of the dependency as a byte array.

- **`parentBytes`** — The byte content of the parent resource that references the dependency.
- **`parentPath`** — The path to the parent resource, used as the base for resolving the dependency path.
- **`dependencyPath`** — The path to the dependency resource to be resolved, relative to the parent path.

**Returns:** A byte array containing the content of the resolved dependency resource.

**Throws `Exception`:** If the resolution or retrieval of the dependency fails.

</details>

<a id="type-tileddependencysource"></a>

### TiledDependencySource

[Source](../../src/main/java/valthorne/graphics/map/tiled/TiledDependencySource.java#L30)

Describes how Tiled map dependencies should be resolved.

A TMX map often references other files such as TSX tilesets and image files. This sealed
interface provides the higher-level source description used to create runtime
`TiledDependencyResolver` instances.

##### Implementations

- `FileSystemSource` for file-based dependency resolution

- `MapSource` for in-memory dependency resolution

This abstraction keeps Tiled loading flexible so maps can be loaded from disk, memory,
cache archives, classpath resource bundles, or other custom asset systems.

<a id="type-tileddependencysource-filesystemsource"></a>

### TiledDependencySource.FileSystemSource

[Source](../../src/main/java/valthorne/graphics/map/tiled/TiledDependencySource.java#L42)

Represents dependency resolution through the file system.

When this source is used, relative dependency paths are resolved against the parent
TMX or TSX path using normal file-system rules.

<a id="type-tileddependencysource-mapsource"></a>

### TiledDependencySource.MapSource

[Source](../../src/main/java/valthorne/graphics/map/tiled/TiledDependencySource.java#L57)

Represents dependency resolution through an in-memory file map.

The provided file map is normalized and defensively copied so the dependency source
remains stable after construction.

- **`files`** — the in-memory file map keyed by normalized path

<details>
<summary>TiledDependencySource.MapSource operation reference (2 declarations)</summary>

#### Constructor

```java
public MapSource
```

Creates a new in-memory dependency source.

- **`files`** — the in-memory file map keyed by path

**Throws `IllegalArgumentException`:** if `files` is null or empty

**Throws `IllegalArgumentException`:** if any path key is null or blank

**Throws `IllegalArgumentException`:** if any file byte array is null or empty

#### files

```java
@Override
        public Map<String, byte[]> files()
```

Returns a defensive copy of the in-memory file map.

Both the map structure and the stored byte arrays are copied so callers cannot
mutate the internal state of this record.

**Returns:** a deep copy of the in-memory file map

</details>

<a id="type-tiledefinition"></a>

### TileDefinition

[Source](../../src/main/java/valthorne/graphics/map/tiled/TileDefinition.java#L29)

Represents the definition of a tile in a tilemap. This class encapsulates the
unique identifier of the tile, its properties, animation frames, and associated
objects. Each tile has a set of properties in key-value format, optional animation
frames defining its animated behavior, and optional tiled objects representing
associated metadata or interactive features.
Collections are retained and exposed directly, so their contents remain mutable.
Null properties and objects become empty mutable collections; null animation is
preserved to represent the absence of an animation declaration. No resource
loading or playback is performed by direct construction.

- **`id`** — The unique identifier of the tile.
- **`properties`** — A map of properties associated with the tile in key-value format.
- **`animation`** — A list of animation frames representing the tile's animation, or null if the tile has no animation.
- **`objects`** — A list of tiled objects representing metadata or interactive features of the tile.

<details>
<summary>TileDefinition operation reference (2 declarations)</summary>

#### Constructor

```java
public TileDefinition
```

Constructs a new `TileDefinition` object.
This constructor initializes the `properties` and `objects` fields
to default non-null values if they are passed as null.

- **`id`** — tileset-local identifier retained without validation
- **`animation`** — frame list retained directly, including null
- **`properties`** — a `Map` representing the properties of the tile, or null to initialize with an empty map
- **`objects`** — a `List` representing the associated objects of the tile, or null to initialize with an empty list

#### load

```java
public static TileDefinition load(XMLStreamReader r, int id) throws Exception
```

Loads a `TileDefinition` object from the given `XMLStreamReader`.
This method reads tile properties, animations, and objects from the XML data, and
returns a new `TileDefinition` instance encapsulating the parsed information.
Start on the opening tile element. Properties merge, the last animation
declaration replaces earlier ones, and object groups append their objects.
Unknown elements are skipped. The reader is left open at the closing tile
element or end of input, and the supplied ID is not read again from XML.

- **`r`** — the `XMLStreamReader` used to read the XML data
- **`id`** — the unique identifier for the tile

**Returns:** a `TileDefinition` object containing the parsed tile data

**Throws `Exception`:** if there is an error while parsing the XML data

</details>

<a id="type-tiledimagemaplayer"></a>

### TiledImageMapLayer

[Source](../../src/main/java/valthorne/graphics/map/tiled/TiledImageMapLayer.java#L21)

Represents a tiled image map layer in a map. This class extends the base functionality
of the MapLayer class, providing additional capabilities to work with an image
as a part of the map layer.
The texture reference is retained directly and may be null when no image was
specified. This class has no disposal method; the owning map or caller manages
texture lifetime. The static loader constructs a GPU texture synchronously and
therefore requires the appropriate current graphics context.

<details>
<summary>TiledImageMapLayer operation reference (3 declarations)</summary>

#### image

```java
public final Texture image
```

Retained layer texture, or null when no image source was provided.

#### Constructor

```java
public TiledImageMapLayer(String name, boolean visible, float opacity, float offsetX, float offsetY, Map<String, String> properties, Texture image)
```

Creates a new instance of TiledImageMapLayer, a layer that includes an image
as part of the map layout. This class extends the MapLayer class and
incorporates additional features specific to tiled images.
The texture is neither copied nor validated, and scalar metadata uses the
base layer's unchecked storage semantics.

- **`name`** — the name of the layer
- **`visible`** — a boolean indicating whether the layer is visible
- **`opacity`** — the opacity level of the layer, ranging from 0.0 to 1.0
- **`offsetX`** — the x-axis offset of the layer
- **`offsetY`** — the y-axis offset of the layer
- **`properties`** — a map containing key-value pairs of layer properties
- **`image`** — the texture representing the image associated with the layer

#### load

```java
public static TiledImageMapLayer load(byte[] tmxBytes, String tmxPath, TiledDependencyResolver resolver, XMLStreamReader r) throws Exception
```

Loads a Tiled image map layer from the provided XML stream reader, TMX data, and associated dependencies.
This method parses the relevant attributes and properties of an image layer, including its image source,
and constructs a `TiledImageMapLayer` instance.
Start on the opening imagelayer element. Missing attributes default to an empty
name, visible state, opacity one, and zero offsets. Nonblank image sources are
resolved and uploaded immediately; no source leaves a null image. Unknown
elements are skipped and the reader remains open at the closing layer element.

- **`tmxBytes`** — the byte content of the TMX file, used for resolving dependencies
- **`tmxPath`** — the path to the TMX file, serving as the base for resolving relative paths
- **`resolver`** — the dependency resolver for external resources such as images
- **`r`** — the XML stream reader for reading the TMX file content

**Returns:** a `TiledImageMapLayer` instance containing the parsed layer data, including properties and image

**Throws `Exception`:** if there is an error during the parsing or resource resolution process

</details>

<a id="type-tiledimagemaplayerdata"></a>

### TiledImageMapLayerData

[Source](../../src/main/java/valthorne/graphics/map/tiled/TiledImageMapLayerData.java#L50)

Represents the CPU-side data for a Tiled image layer.

A Tiled image layer corresponds to an `<imagelayer>` element in a TMX map.
Unlike tile layers, an image layer does not store a grid of tile IDs. Instead, it
references a single image that is drawn as one large visual layer. In this CPU-side
form, the referenced image is decoded into `TextureData` so it can later be
turned into a runtime GPU texture on the OpenGL thread.

This class extends `MapLayer`, so it inherits common layer metadata such as:

- Layer name

- Visibility

- Opacity

- Pixel offsets

- Custom properties

The main additional value stored by this class is the decoded `imageData`.
This makes the class useful inside asynchronous loaders because it avoids creating
OpenGL textures while still preparing everything needed for later rendering.

##### Typical lifecycle

- Load TMX data on a worker thread

- Resolve the image dependency and decode it into `TextureData`

- Store it inside `TiledImageMapLayerData`

- Later, on the render thread, turn that data into a GPU texture

<details>
<summary>TiledImageMapLayerData operation reference (4 declarations)</summary>

#### Constructor

```java
public TiledImageMapLayerData(String name, boolean visible, float opacity, float offsetX, float offsetY, Map<String, String> properties, TextureData imageData)
```

Creates a new image-layer data object.

This constructor stores the inherited layer metadata and the decoded image data.
The image data may be null if the image layer did not define a usable source image,
though a valid Tiled image layer will normally provide one.

- **`name`** — the name of the layer
- **`visible`** — whether the layer is visible
- **`opacity`** — the layer opacity
- **`offsetX`** — the horizontal pixel offset
- **`offsetY`** — the vertical pixel offset
- **`properties`** — the custom layer properties
- **`imageData`** — the decoded image data for this layer

#### load

```java
public static TiledImageMapLayerData load(byte[] tmxBytes, String tmxPath, TiledDependencyResolver resolver, XMLStreamReader reader) throws Exception
```

Loads a `TiledImageMapLayerData` instance from the current `<imagelayer>` XML element.

This method reads the common image-layer attributes from the current XML element,
then scans its child elements for:

- `<properties>` to collect custom layer properties

- `<image>` to resolve and decode the referenced image

The image source is resolved using the supplied `TiledDependencyResolver`,
allowing the layer image to come from disk, memory, cache archives, or any other
custom backing store supported by the resolver.

The resolved image bytes are decoded into `TextureData`, which makes the
result safe to construct on a worker thread without touching OpenGL.

- **`tmxBytes`** — the raw TMX bytes of the parent map
- **`tmxPath`** — the logical or physical path of the parent TMX file
- **`resolver`** — the dependency resolver used to load the referenced image
- **`reader`** — the XML reader positioned at the start of an `<imagelayer>` element

**Returns:** the parsed image-layer data object

**Throws `Exception`:** if parsing fails or the image dependency cannot be resolved or decoded

#### getImageData

```java
public TextureData getImageData()
```

Returns the decoded CPU-side image data for this layer.

This data can later be converted into a runtime texture on the OpenGL thread.
If the image layer had no valid image source, this may be null.

**Returns:** the decoded image data for this layer, or null if none was loaded

#### dispose

```java
public void dispose()
```

Releases the decoded image data for this layer when one is present.

</details>

<a id="type-tiledmap"></a>

### TiledMap

[Source](../../src/main/java/valthorne/graphics/map/tiled/TiledMap.java#L85)

##### TiledMap

`TiledMap` is the runtime renderable representation of a map exported from the Tiled map editor.
It stores the basic map metadata such as dimensions, tile sizes, orientation, properties, tilesets,
and layers, then provides update and render methods for drawing tile layers through a
`TextureBatch`.

This class is meant to be constructed from a `TiledMapData` instance, which acts as the parsed
intermediate data model. During construction, the map converts each `TileSetData` entry into a
runtime `TileSet` so the map is immediately ready for animated tile lookup and rendering.

##### How rendering works

- The map iterates over its `MapLayer` list.

- Only `TiledTileMapLayer` instances are rendered by this class.

- Each tile GID is resolved to the correct `TileSet`.

- The tile's local ID is optionally remapped through animation timing.

- The final `TextureRegion` is drawn through the provided `TextureBatch`.

##### Coordinate behavior

For finite maps, Tiled stores rows from top to bottom, but your renderer commonly works in a
bottom-left world. Because of that, finite layers are vertically flipped during rendering so tiles
appear in the expected world-space positions.

Infinite map chunks are handled differently. Chunk coordinates are already relative to the chunk
origin, so the runtime uses chunk-local iteration and converts those rows into bottom-left space
for drawing.

##### Animation behavior

Animated tiles are driven by `animationTimeSeconds`. Call `update(float)` every frame
with delta time, then render normally. Each `TileSet` decides which local tile ID should be
displayed for the current animation time.

##### Resource ownership

This map owns the runtime `TileSet` instances it creates from `TileSetData`. Because of
that, calling `dispose()` will dispose any tileset textures held by those runtime tilesets.
This makes the map responsible for cleaning up its own texture resources when it is no longer needed.

##### Example

```java
TiledMapData mapData = TiledMapLoader.load("assets/maps/world.tmx", resolver);
TiledMap map = new TiledMap(mapData);

TextureBatch batch = new TextureBatch(4096);

// Game loop
map.update(delta);

batch.begin();
map.render(batch);
batch.end();

// Render only one named layer if needed
batch.begin();
map.renderLayer(batch, "Foreground");
batch.end();

map.dispose();
```

<details>
<summary>TiledMap operation reference (34 declarations)</summary>

#### Constructor

```java
public TiledMap(TiledMapData data)
```

Creates a runtime map from parsed `TiledMapData`.

This constructor copies the high-level map metadata and converts every `TileSetData`
into a runtime `TileSet`. It also sorts the resulting tilesets by first global tile ID
so later GID lookup remains predictable and efficient.

Null collections from the input data are replaced with empty mutable collections so the runtime
instance always has safe containers available.

- **`data`** — the parsed Tiled map data used to build this runtime map

**Throws `NullPointerException`:** if `data` is null

#### update

```java
public void update(float delta)
```

Advances the internal animation timer used for animated tile rendering.

This method should typically be called once per frame using the frame delta time in seconds.
The accumulated time is later passed to each `TileSet` when choosing which frame of an
animated tile should currently be displayed.

- **`delta`** — the elapsed time in seconds since the previous update call

#### render

```java
public void render(TextureBatch batch)
```

Renders the specified texture batch within the defined dimensions.

- **`batch`** — the texture batch to be rendered; must not be null

#### render

```java
public void render(TextureBatch batch, int minTileX, int minTileY, int maxTileX, int maxTileY)
```

Renders the visible portion of the map layers within the specified tile boundaries.

- **`batch`** — the TextureBatch used to draw tiles, must not be null
- **`minTileX`** — the minimum x-coordinate of the visible tile range
- **`minTileY`** — the minimum y-coordinate of the visible tile range
- **`maxTileX`** — the maximum x-coordinate of the visible tile range
- **`maxTileY`** — the maximum y-coordinate of the visible tile range

**Throws `NullPointerException`:** if the batch is null

#### render

```java
public void render(TextureBatch batch, int centerTileX, int centerTileY, int radiusTiles)
```

Renders a texture batch within a specified circular area around a given center tile.

- **`batch`** — The `TextureBatch` to render. Must not be null.
- **`centerTileX`** — The X-coordinate of the center tile.
- **`centerTileY`** — The Y-coordinate of the center tile.
- **`radiusTiles`** — The radius, in tiles, defining the circular area to render. Must be a positive integer.

**Throws `NullPointerException`:** if `batch` is null.

#### renderLayers

```java
public void renderLayers(TextureBatch batch, int... layerIndices)
```

Renders the specified layers of a tile map using the provided texture batch.

- **`batch`** — the `TextureBatch` used for rendering; must not be null
- **`layerIndices`** — the indices of the map layers to render; can be empty or null

#### renderLayers

```java
public void renderLayers(TextureBatch batch, int minTileX, int minTileY, int maxTileX, int maxTileY, int... layerIndices)
```

Renders specific layers of a tile map within the specified tile boundaries using the given texture batch.

- **`batch`** — the texture batch used for rendering; must not be null
- **`minTileX`** — the minimum x-coordinate of the tile range to render
- **`minTileY`** — the minimum y-coordinate of the tile range to render
- **`maxTileX`** — the maximum x-coordinate of the tile range to render
- **`maxTileY`** — the maximum y-coordinate of the tile range to render
- **`layerIndices`** — the indices of the layers to be rendered; can be empty or null

#### renderLayers

```java
public void renderLayers(TextureBatch batch, int centerTileX, int centerTileY, int radiusTiles, int... layerIndices)
```

Renders specified layers within a circular area defined by a central tile and radius.

- **`batch`** — the TextureBatch used for rendering; must not be null
- **`centerTileX`** — the x-coordinate of the central tile
- **`centerTileY`** — the y-coordinate of the central tile
- **`radiusTiles`** — the radius of tiles around the center tile that defines the rendering area
- **`layerIndices`** — the indices of the layers to be rendered

#### renderLayers

```java
public void renderLayers(TextureBatch batch, String... layerNames)
```

Renders specified layers using the provided texture batch.

- **`batch`** — the texture batch used for rendering, must not be null
- **`layerNames`** — the names of the layers to be rendered; can be null

#### renderLayers

```java
public void renderLayers(TextureBatch batch, int minTileX, int minTileY, int maxTileX, int maxTileY, String... layerNames)
```

Renders specified layers of a tile map within the defined tile boundaries.

- **`batch`** — the texture batch used for rendering; must not be null
- **`minTileX`** — the minimum X-coordinate (in tiles) for rendering
- **`minTileY`** — the minimum Y-coordinate (in tiles) for rendering
- **`maxTileX`** — the maximum X-coordinate (in tiles) for rendering
- **`maxTileY`** — the maximum Y-coordinate (in tiles) for rendering
- **`layerNames`** — the names of the layers to be rendered; if null, no layers are rendered

#### renderLayers

```java
public void renderLayers(TextureBatch batch, int centerTileX, int centerTileY, int radiusTiles, String... layerNames)
```

Renders multiple layers within a specified radius around a central tile.

- **`batch`** — the `TextureBatch` used to render the layers; must not be null
- **`centerTileX`** — the x-coordinate of the central tile
- **`centerTileY`** — the y-coordinate of the central tile
- **`radiusTiles`** — the radius in tiles around the central tile to include in rendering
- **`layerNames`** — the names of the layers to be rendered

#### renderLayer

```java
public void renderLayer(TextureBatch batch, String layerName, int centerTileX, int centerTileY, int radiusTiles)
```

Renders a specified layer within a defined circular radius around a center tile.

- **`batch`** — The texture batch used for rendering.
- **`layerName`** — The name of the layer to be rendered.
- **`centerTileX`** — The x-coordinate of the center tile.
- **`centerTileY`** — The y-coordinate of the center tile.
- **`radiusTiles`** — The radius, in tiles, around the center tile to be rendered.

#### renderLayer

```java
public void renderLayer(TextureBatch batch, String layerName, int minTileX, int minTileY, int maxTileX, int maxTileY)
```

Renders the specified tile layer within a given rectangular region.

- **`batch`** — the texture batch used for rendering, must not be null
- **`layerName`** — the name of the layer to render, must not be null
- **`minTileX`** — the minimum tile index along the X-axis to render
- **`minTileY`** — the minimum tile index along the Y-axis to render
- **`maxTileX`** — the maximum tile index along the X-axis to render
- **`maxTileY`** — the maximum tile index along the Y-axis to render

**Throws `NullPointerException`:** if `batch` or `layerName` is null

#### getName

```java
public String getName()
```

Returns the name assigned to this map.

**Returns:** the map name

#### getWidth

```java
public int getWidth()
```

Returns the width of the map in tiles.

**Returns:** the horizontal tile count

#### getHeight

```java
public int getHeight()
```

Returns the height of the map in tiles.

**Returns:** the vertical tile count

#### getTileWidth

```java
public int getTileWidth()
```

Returns the width of one tile in pixels.

**Returns:** the tile width in pixels

#### getTileHeight

```java
public int getTileHeight()
```

Returns the height of one tile in pixels.

**Returns:** the tile height in pixels

#### isInfinite

```java
public boolean isInfinite()
```

Returns whether this map uses infinite chunk-based layout.

**Returns:** true if the map is infinite, otherwise false

#### getOrientation

```java
public String getOrientation()
```

Returns the orientation string defined by the Tiled map.

Common values include `orthogonal`, `isometric`, and related layout types,
though this runtime class currently renders as an orthogonal grid-oriented implementation.

**Returns:** the orientation string

#### getProperties

```java
public Map<String, String> getProperties()
```

Returns the custom map properties.

The returned map is the runtime map's stored property map, not a defensive copy.

**Returns:** the map properties

#### getTileSets

```java
public List<TileSet> getTileSets()
```

Returns the runtime tilesets used by this map.

The returned list is the internal tileset list used during rendering.

**Returns:** the runtime tilesets

#### getMapLayers

```java
public List<MapLayer> getMapLayers()
```

Returns every layer stored in this map.

The returned list may contain tile layers, object layers, image layers, or any other
layer type represented by your runtime model.

**Returns:** the map layer list

#### getLayer

```java
public MapLayer getLayer(int index)
```

Returns the layer at the requested index.

This method delegates directly to the internal layer list and therefore follows the same
bounds behavior as `List#get(int)`.

- **`index`** — the layer index

**Returns:** the layer at the requested index

#### getLayer

```java
public MapLayer getLayer(String name)
```

Finds a layer by its exact name.

This method returns the first matching layer, or `null` when no such layer exists.

- **`name`** — the exact layer name to search for

**Returns:** the matching layer, or null if none exists

**Throws `NullPointerException`:** if `name` is null

#### getTile

```java
public ResolvedTile getTile(String layerName, int tileX, int tileY)
```

Resolves a finite tile using the first exact layer-name match.
Coordinates use a bottom-left origin; infinite chunk storage is not queried.
The result retains raw flip flags and the associated tileset.

- **`layerName`** — exact non-null layer name
- **`tileX`** — tile column from the left
- **`tileY`** — tile row from the bottom

**Returns:** resolved tile, or null for missing, non-tile, empty, or out-of-bounds cells

**Throws `NullPointerException`:** if layerName is null

#### getTile

```java
public ResolvedTile getTile(int layerIndex, int tileX, int tileY)
```

Resolves a finite tile using the indexed layer.
Coordinates use a bottom-left origin; infinite chunk storage is not queried.
The result retains raw flip flags and the associated tileset.

- **`layerIndex`** — zero-based map-layer index
- **`tileX`** — tile column from the left
- **`tileY`** — tile row from the bottom

**Returns:** resolved tile, or null for missing, non-tile, empty, or out-of-bounds cells

**Throws `IndexOutOfBoundsException`:** if layerIndex is outside the layer list

#### getTileDefinition

```java
public TileDefinition getTileDefinition(String layerName, int tileX, int tileY)
```

Retrieves a finite tile's definition using the first exact layer-name match.
Coordinates use a bottom-left origin; infinite chunk storage is not queried.
A resolved tile can still have no definition.

- **`layerName`** — exact non-null layer name
- **`tileX`** — tile column from the left
- **`tileY`** — tile row from the bottom

**Returns:** tile definition, or null when unavailable

**Throws `NullPointerException`:** if layerName is null

#### getTileDefinition

```java
public TileDefinition getTileDefinition(int layerIndex, int tileX, int tileY)
```

Retrieves a finite tile's definition using the indexed layer.
Coordinates use a bottom-left origin; infinite chunk storage is not queried.
A resolved tile can still have no definition.

- **`layerIndex`** — zero-based map-layer index
- **`tileX`** — tile column from the left
- **`tileY`** — tile row from the bottom

**Returns:** tile definition, or null when unavailable

**Throws `IndexOutOfBoundsException`:** if layerIndex is outside the layer list

#### getTileGid

```java
public int getTileGid(String layerName, int tileX, int tileY)
```

Retrieves a finite tile's global ID with flip bits removed using the first exact layer-name match.
Coordinates use a bottom-left origin; infinite chunk storage is not queried.
Zero denotes an empty or unresolved cell.

- **`layerName`** — exact non-null layer name
- **`tileX`** — tile column from the left
- **`tileY`** — tile row from the bottom

**Returns:** resolved global ID, or zero for an unavailable tile

**Throws `NullPointerException`:** if layerName is null

#### getTileGid

```java
public int getTileGid(int layerIndex, int tileX, int tileY)
```

Retrieves a finite tile's global ID with flip bits removed using the indexed layer.
Coordinates use a bottom-left origin; infinite chunk storage is not queried.
Zero denotes an empty or unresolved cell.

- **`layerIndex`** — zero-based map-layer index
- **`tileX`** — tile column from the left
- **`tileY`** — tile row from the bottom

**Returns:** resolved global ID, or zero for an unavailable tile

**Throws `IndexOutOfBoundsException`:** if layerIndex is outside the layer list

#### getTile

```java
public ResolvedTile getTile(TiledTileMapLayer layer, int tileX, int tileY)
```

Resolves a cell from finite row-major layer storage using this map's tilesets.
The bottom-up tileY coordinate is inverted to TMX's stored row order. Zero IDs,
missing layers or finite arrays, out-of-bounds coordinates, and unmatched tilesets
return null. Flip flags are preserved in rawGid and removed from the resolved ID.

- **`layer`** — finite tile layer, or null
- **`tileX`** — tile column from the left
- **`tileY`** — tile row from the bottom

**Returns:** new resolution containing borrowed tileset/definition references, or null

**Throws `ArrayIndexOutOfBoundsException`:** if the layer's ID array is shorter than
its declared dimensions require

#### findTilesetForGID

```java
public TileSet findTilesetForGID(int globalTileID)
```

Finds the best matching tileset for a global tile ID.

Because the tilesets are sorted by first global tile ID, the best match is the last tileset
whose first global tile ID is less than or equal to the requested global tile ID.

- **`globalTileID`** — the global tile ID to resolve

**Returns:** the owning tileset, or null if no matching tileset exists

#### dispose

```java
public void dispose()
```

Disposes runtime texture resources owned by this map's tilesets.

This method attempts to dispose every tileset texture and suppresses individual disposal
failures so one bad texture does not prevent later cleanup attempts.

</details>

<a id="type-tiledmapdata"></a>

### TiledMapData

[Source](../../src/main/java/valthorne/graphics/map/tiled/TiledMapData.java#L74)

Represents the fully parsed, CPU-side data of a Tiled TMX map.

`TiledMapData` is the non-rendering form of a Tiled map. It contains the parsed
map metadata, tileset data, layer data, and custom properties, but it does not create
GPU textures or any OpenGL resources. This makes it safe to load on background threads
through Valthorne's asset system.

The intended lifecycle is:

- Load TMX and dependency data into `TiledMapData` on a worker thread.

- Decode image bytes into CPU-side structures such as `TileSetData`.

- On the OpenGL thread, convert this object into a runtime `TiledMap`.

##### What this class stores

- The root map identity and dimensions such as name, width, height, tile size, and orientation

- The infinite-map flag used by Tiled for chunk-based tile layers

- All root-level custom map properties

- All parsed `TileSetData` entries sorted by first global tile ID

- All supported parsed map layers in source order

##### Threading model

This class is specifically useful because it avoids GPU creation during loading. That means
it can be safely created inside asynchronous loaders without requiring an active OpenGL context.
Once loaded, the data can be handed to `TiledMap#TiledMap(TiledMapData)` on the render thread.

##### Supported TMX content

- `<tileset>` into `TileSetData`

- `<layer>` into `TiledTileMapLayer`

- `<objectgroup>` into `TiledObjectMapLayer`

- `<imagelayer>` into `TiledImageMapLayerData`

- `<properties>` into the root property map

Unknown or unsupported tags are skipped safely so the parser can continue reading known
content without failing immediately on extra data.

##### Example

```java
TiledDependencyResolver resolver = new FileSystemResolver();

TiledMapData data = TiledMapData.load("assets/map/TestMap.tmx", resolver);

// Later, on the OpenGL thread:
TiledMap map = new TiledMap(data);
```

<details>
<summary>TiledMapData operation reference (14 declarations)</summary>

#### load

```java
public static TiledMapData load(String tmxFilePath, TiledDependencyResolver resolver)
```

Loads a TMX map from a file path and parses it into CPU-side map data.

This is the path-based convenience loader. It reads the TMX file bytes from disk
and delegates to `load(byte[], String, TiledDependencyResolver)` so all core
parsing logic remains centralized in the byte-based loader.

This method is useful when your map is stored directly on disk and any external
TSX or image references should be resolved relative to that file path.

- **`tmxFilePath`** — the file path to the TMX file
- **`resolver`** — the dependency resolver used to load referenced TSX files and image bytes

**Returns:** the parsed map data

**Throws `RuntimeException`:** if the file cannot be read or parsing fails

#### load

```java
public static TiledMapData load(byte[] tmxBytes, String tmxPath, TiledDependencyResolver resolver)
```

Loads a TMX map from raw bytes and parses it into CPU-side map data.

This method performs the main XML parsing process for the TMX document. It reads the
root map attributes, collects root properties, loads tileset data, parses supported
layer types, and then produces a complete `TiledMapData` instance.

The provided `tmxPath` is used as the parent location for dependency resolution.
Even though the main TMX content is supplied as bytes, relative TSX and image references
still need a logical base path so the resolver can locate them correctly.

This method is intended to be safe for worker-thread use because it only creates
CPU-side decoded data and does not create OpenGL resources.

- **`tmxBytes`** — the raw TMX XML bytes
- **`tmxPath`** — the logical or real path of the TMX file for dependency resolution
- **`resolver`** — the dependency resolver used to load referenced files

**Returns:** the parsed map data

**Throws `NullPointerException`:** if `tmxBytes` or `resolver` is null

**Throws `RuntimeException`:** if XML parsing or dependency loading fails

#### asTiledMap

```java
public TiledMap asTiledMap()
```

Converts this `TiledMapData` instance into a `TiledMap` representation.

This method creates a new `TiledMap` object based on the data contained
in the current `TiledMapData` instance, allowing for runtime usage of the map.

**Returns:** a `TiledMap` instance created from this `TiledMapData`

#### getName

```java
public String getName()
```

Returns the map name.

**Returns:** the map name, never null

#### getWidth

```java
public int getWidth()
```

Returns the map width in tiles.

For finite maps this is the actual map width. For infinite maps this may simply
reflect the TMX metadata and not the full occupied chunk range.

**Returns:** the map width in tiles

#### getHeight

```java
public int getHeight()
```

Returns the map height in tiles.

For finite maps this is the actual map height. For infinite maps this may simply
reflect the TMX metadata and not the full occupied chunk range.

**Returns:** the map height in tiles

#### getTileWidth

```java
public int getTileWidth()
```

Returns the width of one tile in pixels.

**Returns:** the tile width in pixels

#### getTileHeight

```java
public int getTileHeight()
```

Returns the height of one tile in pixels.

**Returns:** the tile height in pixels

#### isInfinite

```java
public boolean isInfinite()
```

Returns whether this map uses Tiled's infinite-map mode.

**Returns:** true if the map is infinite

#### getOrientation

```java
public String getOrientation()
```

Returns the map orientation string.

Typical values include `orthogonal`, though other Tiled orientations may
also appear depending on the exported map.

**Returns:** the orientation string, never null

#### getProperties

```java
public Map<String, String> getProperties()
```

Returns the root map property map.

These are the custom properties defined directly on the TMX `<map>` element.

**Returns:** the map property map

#### getTileSetData

```java
public List<TileSetData> getTileSetData()
```

Returns the parsed tileset data list.

The returned list is already sorted by first global tile ID so it can be converted
directly into runtime tilesets later.

**Returns:** the tileset data list

#### getMapLayers

```java
public List<MapLayer> getMapLayers()
```

Returns the parsed map layers.

The list preserves the order they were encountered in the TMX file.

**Returns:** the parsed map layer list

#### dispose

```java
public void dispose()
```

Releases decoded CPU-side image data owned by this parsed map.

This traverses tileset images and image-layer textures so cached map data can be
unloaded safely after the runtime no longer needs to build GPU resources from it.

</details>

<a id="type-tiledmaploader"></a>

### TiledMapLoader

[Source](../../src/main/java/valthorne/graphics/map/tiled/TiledMapLoader.java#L32)

Asset loader responsible for converting `TiledMapParameters` into `TiledMapData`.

This loader is the bridge between Valthorne's asset system and the Tiled map parser.
It reads the primary TMX source, creates the appropriate dependency resolver from the
provided dependency source, and then loads the map into CPU-side `TiledMapData`.

The result of this loader is intentionally `TiledMapData` instead of `TiledMap`
because map loading may occur on a worker thread. The returned object contains decoded
CPU-side texture data but does not create any OpenGL resources.

##### Supported source types

- `TiledMapSource.PathSource`

- `TiledMapSource.BytesSource`

<details>
<summary>TiledMapLoader operation reference (1 declarations)</summary>

#### load

```java
@Override
    public TiledMapData load(TiledMapParameters parameters)
```

Loads a Tiled map into CPU-side `TiledMapData`.

This method creates a `TiledDependencyResolver` from the supplied dependency
source and then dispatches loading based on whether the primary TMX map was supplied
as a file path or as raw bytes.

- **`parameters`** — the Tiled map asset parameters

**Returns:** the loaded CPU-side map data

**Throws `RuntimeException`:** if loading fails or if an unsupported source type is supplied

</details>

<a id="type-tiledmapparameters"></a>

### TiledMapParameters

[Source](../../src/main/java/valthorne/graphics/map/tiled/TiledMapParameters.java#L66)

Asset parameters used for loading a Tiled TMX map through Valthorne's asset system.

This record combines three key pieces of information required to load a map:

- The primary TMX source via `TiledMapSource`

- The strategy used to resolve map dependencies via `TiledDependencySource`

- A stable asset key name used for caching and retrieval

These parameters are consumed by `TiledMapLoader`, which converts them into
`TiledMapData` objects that can later be turned into runtime `TiledMap`
instances on the OpenGL thread.

##### Convenience creation methods

This type provides helpers for common use cases:

- Load from a file path using file-system dependency resolution

- Load from raw bytes using an in-memory dependency map

- Load from classpath resources using an in-memory dependency map

##### Example

```java
TiledMapParameters paramsA = TiledMapParameters.fromPath("assets/map/TestMap.tmx", "test-map");

Map<String, byte[]> files = new HashMap<>();
files.put("assets/map/TestMap.tmx", ValthorneFiles.readBytes("assets/map/TestMap.tmx"));
files.put("assets/map/Tiles.tsx", ValthorneFiles.readBytes("assets/map/Tiles.tsx"));
files.put("assets/map/Tiles.png", ValthorneFiles.readBytes("assets/map/Tiles.png"));

TiledMapParameters paramsB = TiledMapParameters.fromBytes(
        files.get("assets/map/TestMap.tmx"),
        "assets/map/TestMap.tmx",
        files,
        "test-map"
);
```

- **`source`** — the source of the TMX data
- **`dependencies`** — the dependency-resolution source for TSX and image files
- **`name`** — the asset key used by the asset manager

<details>
<summary>TiledMapParameters operation reference (8 declarations)</summary>

#### Constructor

```java
public TiledMapParameters
```

Creates a new set of Tiled map asset parameters.

- **`source`** — the source of the TMX map
- **`dependencies`** — the dependency source used to resolve TSX and image files
- **`name`** — the cache key used by the asset manager

**Throws `IllegalArgumentException`:** if `source` is null

**Throws `IllegalArgumentException`:** if `dependencies` is null

**Throws `IllegalArgumentException`:** if `name` is null or blank

#### fromPath

```java
public static TiledMapParameters fromPath(String path)
```

Creates path-based map parameters using the same path as the asset key.

Dependency resolution is configured to use the file system.

- **`path`** — the TMX file path

**Returns:** a new path-based Tiled map parameter set

#### fromPath

```java
public static TiledMapParameters fromPath(String path, String name)
```

Creates path-based map parameters using a custom asset key.

Dependency resolution is configured to use the file system.

- **`path`** — the TMX file path
- **`name`** — the custom asset key

**Returns:** a new path-based Tiled map parameter set

#### fromBytes

```java
public static TiledMapParameters fromBytes(byte[] tmxBytes, String virtualPath, Map<String, byte[]> files)
```

Creates byte-based map parameters using the virtual path as the asset key.

Dependency resolution is configured to use the supplied in-memory file map.

- **`tmxBytes`** — the raw TMX bytes
- **`virtualPath`** — the logical TMX path used for relative dependency resolution
- **`files`** — the in-memory dependency file map

**Returns:** a new byte-based Tiled map parameter set

#### fromBytes

```java
public static TiledMapParameters fromBytes(byte[] tmxBytes, String virtualPath, Map<String, byte[]> files, String name)
```

Creates byte-based map parameters using a custom asset key.

- **`tmxBytes`** — the raw TMX bytes
- **`virtualPath`** — the logical TMX path used for relative dependency resolution
- **`files`** — the in-memory dependency file map
- **`name`** — the custom asset key

**Returns:** a new byte-based Tiled map parameter set

#### fromClasspath

```java
public static TiledMapParameters fromClasspath(String tmxResourcePath, String name)
```

Creates in-memory Tiled map parameters using a resource located on the classpath.
Resolves all dependencies recursively and stores them in-memory.

- **`tmxResourcePath`** — the path of the TMX resource file on the classpath
- **`name`** — the custom asset key used to identify the map

**Returns:** a TiledMapParameters instance representing the TMX map and its dependencies

#### fromClasspath

```java
public static TiledMapParameters fromClasspath(String tmxResourcePath)
```

Creates in-memory Tiled map parameters using a resource located on the classpath.
Resolves all dependencies recursively and stores them in-memory.

- **`tmxResourcePath`** — the path of the TMX resource file on the classpath

**Returns:** a TiledMapParameters instance representing the TMX map and its dependencies

#### key

```java
@Override
    public String key()
```

Returns the asset-manager cache key for this map.

**Returns:** the asset key

</details>

<a id="type-tiledmapsource"></a>

### TiledMapSource

[Source](../../src/main/java/valthorne/graphics/map/tiled/TiledMapSource.java#L44)

Describes the primary source used to load a Tiled TMX map.

A Tiled map can be loaded either from a real file-system path or from an in-memory
byte array. This sealed interface provides those two source forms in a type-safe way
so higher-level loading code can decide how to acquire the raw TMX bytes.

This abstraction is especially useful when integrating with Valthorne's asset system
because some projects may load maps from disk while others may load them from classpath
resources, cache archives, encrypted containers, or already-preloaded memory blocks.

##### Implementations

- `PathSource` for file-based TMX loading

- `BytesSource` for in-memory TMX loading using a virtual path

##### Virtual path usage

When loading from bytes, Tiled dependencies such as external TSX files or referenced images
may still be resolved relative to a logical parent path. For that reason, `BytesSource`
stores both the TMX bytes and a virtual path string.

##### Example

```java
TiledMapSource sourceA = new TiledMapSource.PathSource("assets/maps/world.tmx");

byte[] bytes = Files.readAllBytes(Path.of("assets/maps/world.tmx"));
TiledMapSource sourceB = new TiledMapSource.BytesSource(bytes, "assets/maps/world.tmx");
```

<a id="type-tiledmapsource-pathsource"></a>

### TiledMapSource.PathSource

[Source](../../src/main/java/valthorne/graphics/map/tiled/TiledMapSource.java#L59)

Represents a Tiled map source backed by a real file-system path.

This form is used when the TMX file should be loaded directly from disk. The path
is also used as the logical base path for resolving dependencies such as TSX files
and image files.

- **`path`** — the file-system path of the TMX map

<details>
<summary>TiledMapSource.PathSource operation reference (1 declarations)</summary>

#### Constructor

```java
public PathSource
```

Creates a new path-based map source.

- **`path`** — the file-system path of the TMX map

**Throws `IllegalArgumentException`:** if `path` is null or blank

</details>

<a id="type-tiledmapsource-bytessource"></a>

### TiledMapSource.BytesSource

[Source](../../src/main/java/valthorne/graphics/map/tiled/TiledMapSource.java#L91)

Represents a Tiled map source backed by raw TMX bytes and a logical virtual path.

This form is used when map content already exists in memory. The byte array is copied
defensively so the stored source remains stable and external mutation cannot alter the
asset after construction.

The virtual path is still required because Tiled maps often reference other files
relative to the parent TMX path.

- **`bytes`** — the raw TMX bytes
- **`virtualPath`** — the logical path used for dependency resolution

<details>
<summary>TiledMapSource.BytesSource operation reference (2 declarations)</summary>

#### Constructor

```java
public BytesSource
```

Creates a new byte-based map source.

- **`bytes`** — the raw TMX bytes
- **`virtualPath`** — the logical path used for dependency resolution

**Throws `IllegalArgumentException`:** if `bytes` is null or empty

**Throws `IllegalArgumentException`:** if `virtualPath` is null or blank

#### bytes

```java
@Override
        public byte[] bytes()
```

Returns a defensive copy of the stored TMX bytes.

This prevents callers from mutating the internal byte array held by this source.

**Returns:** a copy of the TMX byte array

</details>

<a id="type-tiledobject"></a>

### TiledObject

[Source](../../src/main/java/valthorne/graphics/map/tiled/TiledObject.java#L35)

Represents an object within a Tiled map with a variety of properties.

Geometry retains TMX coordinates and degree rotation without converting to the renderer's
coordinate system. Polygon/polyline points are packed x,y pairs local to the object.
The property map and point array are retained by reference and remain mutable;
null names/types become empty strings and a null shape type becomes RECT.

- **`id`** — The unique identifier for the object.
- **`name`** — The name of the object, which may be an empty string if not specified.
- **`type`** — The type of the object, which is used to define its category and may be an empty string if not specified.
- **`x`** — The x-coordinate of the object's position in the Tiled map.
- **`y`** — The y-coordinate of the object's position in the Tiled map.
- **`width`** — The width of the object, or zero if it is undefined.
- **`height`** — The height of the object, or zero if it is undefined.
- **`rotation`** — The rotation of the object in degrees.
- **`visible`** — A boolean indicating whether the object is visible.
- **`gid`** — The global identifier for a tile, or zero if the object is not connected to a tile.
- **`properties`** — A map of custom properties as key-value pairs defined for the object.
- **`tiledShapeType`** — The shape type of the object (e.g., RECT, POINT, ELLIPSE, etc.) as defined by `TiledShapeType`.
- **`points`** — An array of points representing polygons or polylines, or null if the object does not use points.
- **`text`** — The text content of the object (if it is a text object), or null if no text is associated.

<details>
<summary>TiledObject operation reference (2 declarations)</summary>

#### Constructor

```java
public TiledObject(int id, String name, String type, float x, float y, float width, float height, float rotation, boolean visible, int gid, Map<String, String> properties, TiledShapeType tiledShapeType, float[] points, String text)
```

Constructs a TiledObject instance with the specified parameters.

- **`id`** — the unique identifier for the TiledObject
- **`name`** — the name of the TiledObject, defaults to an empty string if null
- **`type`** — the type of the TiledObject, defaults to an empty string if null
- **`x`** — the x-coordinate position of the TiledObject
- **`y`** — the y-coordinate position of the TiledObject
- **`width`** — the width of the TiledObject
- **`height`** — the height of the TiledObject
- **`rotation`** — the rotation angle of the TiledObject in degrees
- **`visible`** — the visibility state of the TiledObject
- **`gid`** — the global identifier of the associated tile, or 0 if there is no associated tile
- **`properties`** — a map of custom properties for the TiledObject, defaults to an empty map if null
- **`tiledShapeType`** — the shape type of the TiledObject, defaults to TiledShapeType.RECT if null
- **`points`** — an array of floats representing points for polygon or polyline shapes
- **`text`** — the text content of the TiledObject, if applicable

#### readObject

```java
public static TiledObject readObject(XMLStreamReader r) throws Exception
```

Reads and parses a TiledObject from the given XMLStreamReader.
This method processes various attributes and elements of a TiledObject,
including its geometry, properties, and other metadata,
during the parsing of an XML document.

- **`r`** — the XMLStreamReader from which the TiledObject is read and parsed

**Returns:** a TiledObject instance representing the parsed XML element

**Throws `Exception`:** if an error occurs while parsing the XML

</details>

<a id="type-tiledobjectmaplayer"></a>

### TiledObjectMapLayer

[Source](../../src/main/java/valthorne/graphics/map/tiled/TiledObjectMapLayer.java#L22)

Represents a map layer containing tiled objects.
This class extends the functionality of the base MapLayer class
and introduces a collection of TiledObject elements that belong
to the layer. Each object in the layer can have its own attributes
and properties.
Object membership is a live mutable list, and layer properties follow the shared
map semantics of `MapLayer`. This data layer does not render the objects or
instantiate physics bodies from their shapes.

<details>
<summary>TiledObjectMapLayer operation reference (3 declarations)</summary>

#### Constructor

```java
public TiledObjectMapLayer(String name, boolean visible, float opacity, float offsetX, float offsetY, Map<String, String> properties, List<TiledObject> objects)
```

Constructs a new TiledObjectMapLayer with the specified parameters.
The layer represents a collection of tiled objects and inherits properties
from the base MapLayer class.
A non-null object list is retained directly; no object or collection copy is made.

- **`name`** — the name of the layer
- **`visible`** — the visibility state of the layer
- **`opacity`** — the opacity level of the layer, ranging from 0.0 to 1.0
- **`offsetX`** — the horizontal offset of the layer
- **`offsetY`** — the vertical offset of the layer
- **`properties`** — a map of key-value pairs defining custom properties for the layer
- **`objects`** — a list of TiledObject instances contained in the layer; if null, an empty list is used

#### load

```java
public static TiledObjectMapLayer load(XMLStreamReader r) throws Exception
```

Loads a TiledObjectMapLayer from the given XML stream reader.
This method processes the XML representation of a tiled object group,
parsing its properties and objects to instantiate a TiledObjectMapLayer.
Missing attributes default to an empty name, visible state, full opacity, and
zero pixel offsets. Objects append in document order and property groups merge.
Unknown elements are skipped; the reader stays open at the closing objectgroup
element or end of input.

- **`r`** — the XML stream reader positioned at the object group element

**Returns:** a TiledObjectMapLayer instance populated with data from the XML

**Throws `Exception`:** if an error occurs while reading the XML

#### getObjects

```java
public List<TiledObject> getObjects()
```

Retrieves the list of TiledObject instances associated with this layer.
The returned list is live and mutable; additions and removals directly change
this layer's contents and any caller-owned list supplied to the constructor.

**Returns:** a list of TiledObject instances contained in this TiledObjectMapLayer

</details>

<a id="type-tiledresolvers"></a>

### TiledResolvers

[Source](../../src/main/java/valthorne/graphics/map/tiled/TiledResolvers.java#L32)

Factory utilities for creating `TiledDependencyResolver` instances from higher-level
dependency source descriptions.

Tiled maps may resolve dependencies either from the file system or from an in-memory
file map. This class converts the user-facing `TiledDependencySource` into a
runtime resolver implementation that the TMX parser can use uniformly.

##### Supported dependency sources

- `TiledDependencySource.FileSystemSource`

- `TiledDependencySource.MapSource`

##### In-memory resolution

The `InMemoryResolver` resolves paths relative to the parent TMX or TSX file path,
normalizes them, and then performs a lookup in the provided in-memory file map.

<details>
<summary>TiledResolvers operation reference (1 declarations)</summary>

#### from

```java
public static TiledDependencyResolver from(TiledDependencySource source)
```

Creates a dependency resolver from the provided dependency source.

- **`source`** — the dependency source description

**Returns:** a runtime resolver for the provided source

**Throws `IllegalStateException`:** if the dependency source type is unsupported

</details>

<a id="type-tiledresolvers-inmemoryresolver"></a>

### TiledResolvers.InMemoryResolver

[Source](../../src/main/java/valthorne/graphics/map/tiled/TiledResolvers.java#L74)

Dependency resolver that loads Tiled dependencies from an in-memory file map.

This resolver is useful for cache-backed maps, classpath-preloaded resource packs,
custom archives, or test setups where all files already exist in memory.

Dependency paths are resolved relative to the parent path, normalized into slash-separated
form, and then looked up directly in the stored file map.

<details>
<summary>TiledResolvers.InMemoryResolver operation reference (2 declarations)</summary>

#### Constructor

```java
public InMemoryResolver(Map<String, byte[]> files)
```

Creates a new in-memory dependency resolver.

- **`files`** — the in-memory file map keyed by normalized path

#### resolve

```java
@Override
        public byte[] resolve(byte[] parentBytes, String parentPath, String dependencyPath)
```

Resolves a dependency path using the in-memory file map.

The parent bytes are unused by this implementation, but the parent path is used
to resolve the dependency path relative to the referencing file.

- **`parentBytes`** — the raw bytes of the parent file
- **`parentPath`** — the logical path of the parent file
- **`dependencyPath`** — the dependency path to resolve

**Returns:** the resolved dependency bytes

**Throws `RuntimeException`:** if the dependency is not present in the in-memory file map

</details>

<a id="type-tiledshapetype"></a>

### TiledShapeType

[Source](../../src/main/java/valthorne/graphics/map/tiled/TiledShapeType.java#L14)

Represents different types of shapes in a Tiled map format.

This enumeration is used to define the types of shapes that can be associated
with objects or areas within a Tiled map. Each type corresponds to specific
geometric properties or visual appearances.
Geometry and text data live in the associated `TiledObject`; this enum only
identifies how to interpret them and does not perform rendering or collision tests.

<details>
<summary>TiledShapeType operation reference (6 declarations)</summary>

#### RECT

```java
public static final  TiledShapeType RECT
```

Represents a rectangular shape type within the Tiled map format.

#### POINT

```java
public static final  TiledShapeType POINT
```

Represents a single point shape type within the Tiled map format.

#### ELLIPSE

```java
public static final  TiledShapeType ELLIPSE
```

Represents an elliptical shape type within the Tiled map format.

#### POLYGON

```java
public static final  TiledShapeType POLYGON
```

Represents a closed shape with multiple straight sides within the Tiled map format.

#### POLYLINE

```java
public static final  TiledShapeType POLYLINE
```

Represents an open, connected sequence of straight line segments.

#### TEXT

```java
public static final  TiledShapeType TEXT
```

Represents a textual object in the Tiled map format.

</details>

<a id="type-tiledtilemaplayer"></a>

### TiledTileMapLayer

[Source](../../src/main/java/valthorne/graphics/map/tiled/TiledTileMapLayer.java#L21)

Stores a TMX tile layer as either a finite row-major ID array or a collection
of infinite-map chunks. Global IDs retain Tiled's flip bits; consumers decode
those flags when resolving tiles. Width and height are measured in tiles,
while inherited offsets are measured in pixels.

The constructor retains supplied arrays and maps, and getters expose those live
objects. This layer does not own textures or perform rendering. Infinite layers
loaded from TMX use chunks and have a null finite ID array.

<details>
<summary>TiledTileMapLayer operation reference (7 declarations)</summary>

#### Constructor

```java
public TiledTileMapLayer(String name, int width, int height, boolean visible, float opacity, float offsetX, float offsetY, Map<String, String> properties, int[] gids, boolean infinite, Map<Long, MapChunk> chunks)
```

Creates a new instance of the TiledTileMapLayer class, representing a layer in a tiled map.

- **`name`** — the name of the layer.
- **`width`** — the width of the layer in tiles.
- **`height`** — the height of the layer in tiles.
- **`visible`** — a boolean indicating whether the layer is visible.
- **`opacity`** — the opacity of the layer, specified as a float value between 0 and 1.
- **`offsetX`** — the horizontal offset of the layer in pixels.
- **`offsetY`** — the vertical offset of the layer in pixels.
- **`properties`** — a map containing custom properties associated with the layer.
- **`gids`** — a one-dimensional array of global tile IDs representing the layer's tiles.
- **`infinite`** — a boolean indicating whether the layer is an infinite layer (supports chunks).
- **`chunks`** — a map associating chunk keys (packed long values) to `MapChunk` objects. If null, an empty map is assigned.

#### load

```java
public static TiledTileMapLayer load(byte[] tmxBytes, XMLStreamReader r) throws Exception
```

Loads a TiledTileMapLayer from the provided TMX bytes and XML stream reader.

- **`tmxBytes`** — the byte array containing the TMX file data.
- **`r`** — the XMLStreamReader used to parse the TMX file.

**Returns:** an instance of TiledTileMapLayer, representing the layer described in the TMX data.

**Throws `Exception`:** if an error occurs during the reading or parsing process.

#### getWidth

```java
public int getWidth()
```

Retrieves the width of the layer in tiles.

**Returns:** the width of the layer as an integer.

#### getHeight

```java
public int getHeight()
```

Retrieves the height of the layer in tiles.

**Returns:** the height of the layer as an integer.

#### getGids

```java
public int[] getGids()
```

Exposes the finite layer's row-major ID storage, including encoded flip flags.
The array is borrowed directly from construction and is not copied; callers
that modify it change the layer's data.

**Returns:** live finite ID array, or null for an infinite layer loaded from TMX

#### isInfinite

```java
public boolean isInfinite()
```

Determines if the layer is infinite, meaning it supports chunked maps rather
than having a fixed width and height.

**Returns:** true if the layer is infinite, false otherwise.

#### getChunks

```java
public Map<Long, MapChunk> getChunks()
```

Exposes the live map of chunk origins to chunk data. Keys contain signed x in
the high 32 bits and signed y in the low 32 bits. Mutations affect this layer;
the map is not a snapshot and no synchronization is supplied.

**Returns:** mutable chunk map, normally empty for finite layers

</details>

<a id="type-tiledxml"></a>

### TiledXML

[Source](../../src/main/java/valthorne/graphics/map/tiled/TiledXML.java#L20)

Utility class for handling XML-related operations specific to Tiled map structures. This includes
reading and resolving paths, extracting XML element contents, handling attributes, and processing
properties or other common behaviors found in Tiled TMX/TSX files.

<details>
<summary>TiledXML operation reference (11 declarations)</summary>

#### readBytesFromTmxDependency

```java
public static byte[] readBytesFromTmxDependency(TiledDependencyResolver resolver, byte[] parentBytes, String parentPath, String dependencyPath)
```

Resolves and retrieves the raw content of a dependency file (e.g., external tilesets or images)
relative to the parent file by leveraging the provided resolver logic.

- **`resolver`** — The dependency resolver responsible for locating and retrieving dependencies.
- **`parentBytes`** — The byte-stream corresponding to the parent TMX or TSX file.
- **`parentPath`** — The file path of the parent TMX or TSX resource.
- **`dependencyPath`** — The dependency's relative path specified in the parent resource.

**Returns:** The byte content of the resolved dependency file.

**Throws `RuntimeException`:** If the resolver fails or provides invalid results.

#### resolvePathString

```java
public static String resolvePathString(String basePath, String rel)
```

Resolves a relative file path to an absolute normalized path based on a given base path.

- **`basePath`** — The base directory (can be null or empty).
- **`rel`** — The relative path to resolve.

**Returns:** An absolute, normalized path string or null if the relative path is null.

#### resolveRelative

```java
public static Path resolveRelative(Path baseFile, String rel)
```

Resolves a relative path against a base file, returning an absolute normalized path.

- **`baseFile`** — The base file whose directory is used for resolution.
- **`rel`** — The relative path to resolve.

**Returns:** An absolute, normalized Path instance.

#### readElementText

```java
public static String readElementText(XMLStreamReader r, String elementName) throws XMLStreamException
```

Reads the text content of an XML element from the current position of the given reader until
the end tag corresponding to the provided element name is encountered.

- **`r`** — The XMLStreamReader positioned at the desired element.
- **`elementName`** — The name of the target element whose text content is to be read.

**Returns:** The cumulative text content of the element.

**Throws `XMLStreamException`:** If XML parsing fails.

#### readProperties

```java
public static Map<String, String> readProperties(XMLStreamReader r) throws Exception
```

Reads all 'property' elements nested within an XML 'properties' section.

- **`r`** — The XMLStreamReader positioned at the start of the 'properties' element.

**Returns:** A map containing property names as keys and their respective values as values.

**Throws `Exception`:** If an error occurs during parsing.

#### readAttribute

```java
public static String readAttribute(XMLStreamReader r, String name, String def)
```

Reads an attribute value from the current element in the XML reader.

- **`r`** — The XMLStreamReader positioned at a start element.
- **`name`** — The name of the attribute to read.
- **`def`** — The default value to return if the attribute is not present.

**Returns:** The attribute value or the default value if the attribute is missing.

#### readInteger

```java
public static int readInteger(XMLStreamReader r, String name, int def)
```

Reads an integer-valued attribute from the current element in the reader.

- **`r`** — The XMLStreamReader positioned at a start element.
- **`name`** — The attribute name.
- **`def`** — The default value to return if parsing fails or the attribute is missing.

**Returns:** The parsed integer value or the default value if parsing fails.

#### readFloat

```java
public static float readFloat(XMLStreamReader r, String name, float def)
```

Reads a float-valued attribute from the current element in the reader.

- **`r`** — The XMLStreamReader positioned at a start element.
- **`name`** — The attribute name.
- **`def`** — The default value to return if parsing fails or the attribute is missing.

**Returns:** The parsed float value or the default value if parsing fails.

#### moveToStart

```java
public static void moveToStart(XMLStreamReader r, String element) throws XMLStreamException
```

Advances the XMLStreamReader to the start of the specified element or throws an exception if not found.

- **`r`** — The XMLStreamReader to navigate.
- **`element`** — The target element name.

**Throws `XMLStreamException`:** If the element is missing or XML parsing fails.

#### skipElement

```java
public static void skipElement(XMLStreamReader r) throws XMLStreamException
```

Skips over the current XML element (including nested child elements) in the reader.

- **`r`** — The XMLStreamReader positioned at a start element.

**Throws `XMLStreamException`:** If XML parsing fails.

#### getXMLFactory

```java
public static XMLInputFactory getXMLFactory()
```

Retrieves the shared XMLInputFactory instance for creating XML parsers.

**Returns:** The shared XMLInputFactory instance.

</details>

<a id="type-tileset"></a>

### TileSet

[Source](../../src/main/java/valthorne/graphics/map/tiled/TileSet.java#L77)

Represents a runtime-ready Tiled tileset backed by a GPU `Texture`.

A `TileSet` is created after TMX or TSX data has already been parsed into
`TileSetData`. At that point the tileset has all of its metadata available,
including tile dimensions, spacing, margin, atlas image size, custom properties,
and optional per-tile definitions such as animation data. This runtime class then
wraps that metadata together with an actual GPU texture so tiles can be resolved
and rendered efficiently.

Tiled maps use **global tile IDs** when storing tile data in layers. A tileset
owns a contiguous range of those IDs beginning at `firstGlobalTileID`. When
a map renderer finds that a given global tile ID belongs to this tileset, it converts
that global ID into a **local tile ID** by subtracting the first global tile ID.
That local tile ID is then used by this class to:

- Resolve the tile's source coordinates within the tileset atlas

- Create and cache a `TextureRegion` for fast reuse

- Resolve animated tile frames based on elapsed time

##### Atlas layout

The texture atlas is assumed to be arranged in a grid. The tile width, tile height,
spacing, margin, column count, image width, and image height are all used to compute
the exact source rectangle for each tile. The Y coordinate is resolved using a
bottom-left texture origin so the produced `TextureRegion` matches the runtime
rendering system.

##### Animation model

If a tile has animation data in `tileDefs`, `resolveAnimatedLocalId(int, float)`
will step through the animation frames using the provided elapsed time. If a tile has
no animation, the original tile ID is returned unchanged.

##### Caching

Texture regions are generated lazily and stored in `regionCache`. This avoids
recalculating source rectangles for the same local tile ID over and over during
rendering.

##### Example

```java
TileSetData data = ...;
TileSet tileSet = TileSet.fromData(data);

int gid = 37;
int localId = gid - tileSet.getFirstGlobalTileID();

int drawId = tileSet.resolveAnimatedLocalId(localId, timeSeconds);
TextureRegion region = tileSet.getRegionForLocalId(drawId);

batch.draw(region, 100, 100, tileSet.getTileWidth(), tileSet.getTileHeight());
```

<details>
<summary>TileSet operation reference (23 declarations)</summary>

#### Constructor

```java
public TileSet(int firstGlobalTileID, String name, Texture texture, int tileWidth, int tileHeight, int spacing, int margin, int tileCount, int columns, int imageWidth, int imageHeight, Map<String, String> properties, Map<Integer, TileDefinition> tileDefs)
```

Creates a new runtime tileset.

This constructor stores all already-parsed tileset metadata and the runtime
texture atlas. It also initializes an empty region cache so tile regions can
be created lazily on demand.

Null values are normalized where appropriate:

- `name` becomes an empty string when null

- `properties` becomes an empty map when null

- `tileDefs` becomes an empty map when null

The `texture` argument must not be null because this runtime form of a
tileset is intended to be renderable immediately.

- **`firstGlobalTileID`** — the first global tile ID belonging to this tileset
- **`name`** — the tileset name
- **`texture`** — the GPU texture atlas for this tileset
- **`tileWidth`** — the width of one tile in pixels
- **`tileHeight`** — the height of one tile in pixels
- **`spacing`** — the spacing in pixels between adjacent tiles
- **`margin`** — the outer atlas margin in pixels
- **`tileCount`** — the total number of tiles in the tileset
- **`columns`** — the number of columns in the atlas
- **`imageWidth`** — the width of the atlas image in pixels
- **`imageHeight`** — the height of the atlas image in pixels
- **`properties`** — custom tileset properties
- **`tileDefs`** — per-tile metadata definitions

**Throws `NullPointerException`:** if `texture` is null

#### fromData

```java
public static TileSet fromData(TileSetData data)
```

Creates a runtime `TileSet` from CPU-side `TileSetData`.

This method is typically called on the OpenGL thread after the tileset image bytes
have already been decoded into `valthorne.graphics.texture.TextureData` on a
background thread. It converts that CPU-side data into a GPU `Texture` and then
builds a fully renderable tileset instance.

- **`data`** — the CPU-side tileset data

**Returns:** a runtime tileset backed by a GPU texture

**Throws `NullPointerException`:** if `data` is null or if its texture data is invalid

#### getRegionForLocalId

```java
public TextureRegion getRegionForLocalId(int localID)
```

Returns the cached or newly created texture region for the specified local tile ID.

The local tile ID is the index of a tile within this specific tileset, not the
global tile ID stored inside map layers. The region is resolved by computing the
tile's row and column inside the atlas grid and then translating those values into
exact source coordinates.

If a region has already been created for this local tile ID, the cached instance is
returned immediately. Otherwise a new `TextureRegion` is created, stored in the
cache, and then returned.

If the tile dimensions are invalid, this method returns `null` because a usable
source rectangle cannot be computed.

- **`localID`** — the tile ID relative to this tileset

**Returns:** the corresponding texture region, or `null` if tile dimensions are invalid

#### resolveAnimatedLocalId

```java
public int resolveAnimatedLocalId(int localId, float timeSeconds)
```

Resolves the correct local tile ID for an animated tile at the given elapsed time.

If the specified local tile has an animation definition in `tileDefs`, this
method calculates which animation frame should be displayed using the provided elapsed
time in seconds. The animation duration is treated as a looping timeline.

If the tile has no animation, has an empty animation list, or has an invalid total
duration, the original local tile ID is returned unchanged.

The returned tile ID is still local to this tileset and can be passed directly into
`getRegionForLocalId(int)`.

- **`localId`** — the original local tile ID
- **`timeSeconds`** — the elapsed animation time in seconds

**Returns:** the local tile ID of the animation frame that should currently be drawn

#### getName

```java
public String getName()
```

Returns the name of this tileset.

**Returns:** the tileset name, never null

#### getFirstGlobalTileID

```java
public int getFirstGlobalTileID()
```

Returns the first global tile ID owned by this tileset.

Any map-layer global tile ID greater than or equal to this value and below the next
tileset's first global tile ID belongs to this tileset.

**Returns:** the first global tile ID for this tileset

#### getTexture

```java
public Texture getTexture()
```

Returns the GPU texture atlas used by this tileset.

**Returns:** the tileset texture

#### getTileWidth

```java
public int getTileWidth()
```

Returns the width of one tile in pixels.

**Returns:** the tile width in pixels

#### getTileHeight

```java
public int getTileHeight()
```

Returns the height of one tile in pixels.

**Returns:** the tile height in pixels

#### getSpacing

```java
public int getSpacing()
```

Returns the spacing between adjacent tiles in the atlas.

**Returns:** the tile spacing in pixels

#### getMargin

```java
public int getMargin()
```

Returns the outer margin around the atlas tile grid.

**Returns:** the atlas margin in pixels

#### getTileCount

```java
public int getTileCount()
```

Returns the declared tile count for this tileset.

**Returns:** the total number of tiles

#### getColumns

```java
public int getColumns()
```

Returns the declared number of atlas columns.

**Returns:** the number of columns in the atlas

#### getImageWidth

```java
public int getImageWidth()
```

Returns the width of the tileset image in pixels.

**Returns:** the atlas image width

#### getImageHeight

```java
public int getImageHeight()
```

Returns the height of the tileset image in pixels.

**Returns:** the atlas image height

#### getProperties

```java
public Map<String, String> getProperties()
```

Returns the custom properties defined for this tileset.

The returned map is the stored map instance for this tileset.

**Returns:** the tileset property map

#### getTileDefs

```java
public Map<Integer, TileDefinition> getTileDefs()
```

Returns the per-tile metadata definitions for this tileset.

These definitions may contain animation data or other custom tile-specific data
parsed from the source TMX or TSX file.

**Returns:** the tile definition map

#### getTileCountForLayer

```java
public int getTileCountForLayer(int layerID)
```

Returns a derived tile-count-like value based on this tileset's total tile count and columns.

The `layerID` parameter is not currently used. This method effectively returns
the number of rows when the tileset is interpreted as a grid.

- **`layerID`** — an unused layer identifier

**Returns:** `tileCount / columns`

#### getTileCountForLayer

```java
public int getTileCountForLayer(int layerID, int layerWidth)
```

Returns a derived tile-count-like value using the supplied layer width and this tileset's columns.

The `layerID` parameter is not currently used. This method does not reflect normal
Tiled semantics directly and simply multiplies the given width by the tileset column count.

- **`layerID`** — an unused layer identifier
- **`layerWidth`** — the layer width used in the calculation

**Returns:** `layerWidth * columns`

#### getTileCountForLayer

```java
public int getTileCountForLayer(int layerID, int layerWidth, int layerHeight)
```

Returns a derived tile-count-like value using the supplied layer dimensions and this tileset's columns.

The `layerID` parameter is not currently used. This method multiplies the provided
width and height by the tileset's column count.

- **`layerID`** — an unused layer identifier
- **`layerWidth`** — the layer width used in the calculation
- **`layerHeight`** — the layer height used in the calculation

**Returns:** `layerWidth * columns * layerHeight`

#### getObjectsForLocalId

```java
public List<TiledObject> getObjectsForLocalId(int localId)
```

Retrieves a list of TiledObject instances associated with the specified local ID.

- **`localId`** — the local ID of the tile for which the objects are to be retrieved

**Returns:** a list of TiledObject instances associated with the provided local ID,
or an empty list if no objects are found

#### getDefinition

```java
public TileDefinition getDefinition(int localId)
```

Retrieves the TileDefinition associated with the specified local ID.

- **`localId`** — the local ID of the tile whose definition is to be retrieved

**Returns:** the TileDefinition object associated with the given local ID, or null if no definition exists

#### dispose

```java
public void dispose()
```

Releases runtime resources owned by this tileset.

The atlas texture is disposed and the lazily-built region cache is cleared so this
tileset can no longer be used for rendering.

</details>

<a id="type-tilesetdata"></a>

### TileSetData

[Source](../../src/main/java/valthorne/graphics/map/tiled/TileSetData.java#L92)

Represents the fully parsed, CPU-side data for a single Tiled tileset.

`TileSetData` is the non-runtime form of a tileset. It stores all tileset metadata
parsed from TMX or TSX content together with the decoded `TextureData` for the tileset
image. Because it only holds CPU-side data, it can be created on worker threads without
requiring an active OpenGL context.

This class exists as an intermediate step between raw TMX or TSX parsing and the runtime
`TileSet` object. The usual workflow looks like this:

- Parse the map and its tilesets into `TileSetData`

- Decode the referenced image bytes into `TextureData`

- Later, on the render thread, convert the data into a runtime `TileSet`

##### What this class stores

- The first global tile ID owned by the tileset

- The tileset name

- The decoded CPU-side texture data for the tileset atlas image

- Tile sizing information such as tile width, tile height, spacing, and margin

- Atlas layout information such as tile count, columns, image width, and image height

- Custom tileset properties defined in Tiled

- Optional per-tile metadata such as animation definitions

##### External TSX support

Tiled maps may define tilesets inline inside the TMX file or externally in a TSX file.
This class supports both cases. If the current `<tileset>` element references a
`source` attribute, the external TSX bytes are resolved first and then parsed as
the real tileset body.

##### Image dependency support

The tileset image is resolved through `TiledDependencyResolver`, which means the
image does not need to come from the file system directly. It can come from disk, memory,
cache archives, classpath resources, or any custom source supported by the resolver.

##### Example

```java
TiledDependencyResolver resolver = new FileSystemResolver();

TileSetData tileSetData = TileSetData.load(
        tmxBytes,
        "assets/maps/world.tmx",
        resolver,
        reader
);

int firstGid = tileSetData.getFirstGlobalTileID();
int tileWidth = tileSetData.getTileWidth();
TextureData image = tileSetData.getTextureData();
```

- **`firstGlobalTileID`** — The first global tile ID owned by this tileset.
- **`name`** — The name of the tileset.
- **`textureData`** — The decoded CPU-side texture data for the atlas image.
- **`tileWidth`** — The width of one tile in pixels.
- **`tileHeight`** — The height of one tile in pixels.
- **`spacing`** — The spacing between tiles inside the atlas image.
- **`margin`** — The outer margin around the atlas tile grid.
- **`tileCount`** — The total number of tiles declared in this tileset.
- **`columns`** — The number of columns in the atlas grid.
- **`imageWidth`** — The width of the atlas image in pixels.
- **`imageHeight`** — The height of the atlas image in pixels.
- **`properties`** — The custom properties defined for the tileset.
- **`tileDefs`** — The optional per-tile metadata definitions.

<details>
<summary>TileSetData operation reference (16 declarations)</summary>

#### Constructor

```java
public TileSetData(int firstGlobalTileID, String name, TextureData textureData, int tileWidth, int tileHeight, int spacing, int margin, int tileCount, int columns, int imageWidth, int imageHeight, Map<String, String> properties, Map<Integer, TileDefinition> tileDefs)
```

Creates a new CPU-side tileset data object.

This constructor stores all already-parsed tileset information and normalizes nullable
fields into safe defaults where appropriate. The texture data is required because a
valid renderable tileset must have an atlas image.

- **`firstGlobalTileID`** — the first global tile ID owned by this tileset
- **`name`** — the tileset name
- **`textureData`** — the decoded CPU-side texture data for the atlas image
- **`tileWidth`** — the width of one tile in pixels
- **`tileHeight`** — the height of one tile in pixels
- **`spacing`** — the spacing between adjacent tiles
- **`margin`** — the outer margin around the tile grid
- **`tileCount`** — the total number of tiles in the tileset
- **`columns`** — the number of atlas columns
- **`imageWidth`** — the atlas image width in pixels
- **`imageHeight`** — the atlas image height in pixels
- **`properties`** — the custom tileset properties
- **`tileDefs`** — the per-tile metadata definitions

**Throws `NullPointerException`:** if `textureData` is null

#### load

```java
public static TileSetData load(byte[] tmxBytes, String tmxPath, TiledDependencyResolver resolver, XMLStreamReader reader) throws Exception
```

Loads tileset data from the current `<tileset>` XML element.

This method supports both inline and external tilesets:

- If the current `<tileset>` has a `source` attribute, the referenced TSX file is resolved and parsed

- If there is no `source` attribute, the tileset body is parsed directly from the current XML reader

The returned object contains CPU-side data only and does not create any OpenGL resources.

- **`tmxBytes`** — the raw bytes of the parent TMX file
- **`tmxPath`** — the logical or physical path of the parent TMX file
- **`resolver`** — the dependency resolver used to load TSX files and image dependencies
- **`reader`** — the XML reader positioned at a `<tileset>` start element

**Returns:** the parsed tileset data

**Throws `Exception`:** if TSX resolution, XML parsing, or image decoding fails

#### firstGlobalTileID

```java
@Override
    public int firstGlobalTileID()
```

Returns the first global tile ID owned by this tileset.

**Returns:** the first global tile ID

#### name

```java
@Override
    public String name()
```

Returns the tileset name.

**Returns:** the tileset name, never null

#### textureData

```java
@Override
    public TextureData textureData()
```

Returns the decoded CPU-side texture data for the tileset image.

**Returns:** the tileset texture data

#### tileWidth

```java
@Override
    public int tileWidth()
```

Returns the width of one tile in pixels.

**Returns:** the tile width in pixels

#### tileHeight

```java
@Override
    public int tileHeight()
```

Returns the height of one tile in pixels.

**Returns:** the tile height in pixels

#### spacing

```java
@Override
    public int spacing()
```

Returns the spacing between adjacent tiles in the atlas image.

**Returns:** the tile spacing in pixels

#### margin

```java
@Override
    public int margin()
```

Returns the outer margin around the tileset atlas grid.

**Returns:** the atlas margin in pixels

#### tileCount

```java
@Override
    public int tileCount()
```

Returns the declared total tile count for this tileset.

**Returns:** the total tile count

#### columns

```java
@Override
    public int columns()
```

Returns the declared number of atlas columns.

**Returns:** the number of columns

#### imageWidth

```java
@Override
    public int imageWidth()
```

Returns the width of the tileset image in pixels.

**Returns:** the image width in pixels

#### imageHeight

```java
@Override
    public int imageHeight()
```

Returns the height of the tileset image in pixels.

**Returns:** the image height in pixels

#### properties

```java
@Override
    public Map<String, String> properties()
```

Returns the custom properties defined for this tileset.

**Returns:** the tileset property map

#### tileDefs

```java
@Override
    public Map<Integer, TileDefinition> tileDefs()
```

Returns the per-tile metadata definitions keyed by local tile ID.

**Returns:** the tile definition map

#### dispose

```java
public void dispose()
```

Releases the decoded tileset image data owned by this CPU-side tileset.

</details>

## Related guides

- [Asset loading and caching](assets.md)
- [Textures, sprites, atlases, and batching](textures.md)
- [Classpath and filesystem utilities](files.md)
