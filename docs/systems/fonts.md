# Bitmap fonts and glyph styling

Author: Albert Beaupre

[System manual](README.md)

## Purpose

The bitmap font system bakes glyphs into an atlas, measures text, and submits textured glyph quads. `FontData` contains metrics and atlas data; `Font` adds text layout, size, color, outlines, and styling. Use this path when an atlas-based font suits the expected display sizes.

## Features and when to use them

| Feature | Purpose |
| --- | --- |
| Baking parameters | Source, size, and character range determine which glyphs are available. |
| Measurement and layout | Line metrics, tabs, advances, and fallback behavior determine positioning. |
| Glyph styling | FontStyler receives glyph context and can alter visibility, transform, or appearance. |
| Outlines | Offset glyph passes build an outline using configurable color and thickness. |
| Cached versus immediate text | Reuse stored layout for repeated strings or draw changing text through the immediate path. |

## Getting started

1. Load FontData using FontParameters or construct a Font from its documented source overload.
2. Choose a character range that covers the text your application needs.
3. Set size, color, and optional styling, then draw through an active TextureBatch.
4. Release the font's owned texture and handle borrowed FontData according to the constructor used.

## Ownership and lifecycle

Creating the font's atlas texture requires an OpenGL context. Glyph styling callbacks run during drawing; avoid changing shared font state reentrantly. A bitmap atlas baked at one size may not produce the same appearance at very different scales.

## Important behavior

- A missing glyph follows the documented fallback advance; it is not automatically fetched from another font.
- UTF-16 traversal is not equivalent to advanced shaping or automatic font fallback.
- Font asset keys must distinguish bake variants if both should remain cached.

## Components and examples

The sections below explain each component and its declared public or protected operations. Expand an operation reference for signatures, parameters, results, and edge cases. Inherited operations are documented with their base type. Examples embedded in component descriptions are integration fragments: supply the surrounding resources and application variables they name.

- [`Font`](#type-font)
- [`FontData`](#type-fontdata)
- [`FontLoader`](#type-fontloader)
- [`FontParameters`](#type-fontparameters)
- [`FontSource`](#type-fontsource)
- [`FontSource.PathSource`](#type-fontsource-pathsource)
- [`FontSource.BytesSource`](#type-fontsource-bytessource)
- [`FontStyler`](#type-fontstyler)
- [`Glyph`](#type-glyph)
- [`GlyphContext`](#type-glyphcontext)
- [`GlyphStyle`](#type-glyphstyle)

<a id="type-font"></a>

### Font

[Source](../../src/main/java/valthorne/graphics/font/Font.java#L98)

Renders cached bitmap glyphs from a baked `FontData` atlas through a `TextureBatch`.

This class is designed for fast repeated text rendering in places like UI, debug overlays,
HUD labels, dialog boxes, and other 2D text-heavy systems. A `Font` instance owns one
atlas `Texture` and converts the current text into cached quads ahead of time so the
draw step only needs to iterate the prepared glyph entries and submit them to the batch.

##### How this class works

The supplied `FontData` contains baked glyph metrics and atlas texture information.
When text is assigned through `setText(String)`, this class walks the string once,
computes every drawable glyph's position and UV coordinates, and stores the result inside
a reusable quad cache. That cache is then used by `draw(TextureBatch)` for rendering.
This avoids recalculating line positions, glyph rectangles, and UV coordinates every frame.

##### What gets cached

- Measured width of the current text block

- Measured height of the current text block

- Per-glyph local position

- Per-glyph local size

- Per-glyph atlas UV coordinates

- Per-glyph metadata for runtime stylers

##### Rendering passes

Rendering can happen in up to three passes:

- Shadow pass

- Outline pass

- Main glyph pass

Each pass uses the same cached glyph geometry. Only offsets, colors, and optional runtime
style adjustments differ between passes.

##### Tabs, missing glyphs, and newlines

This class supports newline and tab processing when measuring and building cached layout data.
Newlines start a new visual line. Tabs advance by a width derived from a configurable number
of spaces. Missing glyphs do not crash rendering; they use a fallback horizontal advance so
layout can continue in a stable way.

##### Runtime styling

A `FontStyler` may be attached to modify each glyph as it is rendered. This allows effects
like shaking, waving, color cycling, visibility toggles, or per-letter scaling without rebuilding
the cached layout itself.

##### Typical usage

```java
Font font = new Font("assets/fonts/inter.ttf", 32);
font.setText("Inventory");
font.setPosition(24f, 680f);
font.setColor(new Color(1f, 1f, 1f, 1f));
font.setShadow(2f, -2f, new Color(0f, 0f, 0f, 0.65f));
font.setOutline(1, new Color(0f, 0f, 0f, 1f));


TextureBatch batch = new TextureBatch(4096);

batch.begin();
font.draw(batch);
batch.end();

font.dispose();
batch.dispose();
```

##### Important ownership note

This class owns the atlas `Texture` it creates from the provided `FontData`. When the
font is no longer needed, call `dispose()` to release the texture and clear internal references.

<details>
<summary>Font operation reference (43 declarations)</summary>

#### Constructor

```java
public Font(FontData data)
```

Creates a new font renderer from existing baked `FontData`.

This constructor creates the atlas `Texture`, caches commonly used metric values,
prepares tab width behavior, and builds the initial layout cache for the current text,
which starts as an empty string.

The supplied `FontData` must not be null.

- **`data`** — baked font data containing glyph metrics and atlas texture data

**Throws `NullPointerException`:** if `data` is null

#### Constructor

```java
public Font(String path, int fontSize)
```

Loads font data from disk and creates a font renderer from it.

This constructor is a convenience overload that calls `FontData#load(String, int, int, int)`
using the character range `0..254`.

- **`path`** — path to the font file
- **`fontSize`** — target baked font size

#### getText

```java
public String getText()
```

Returns the current text assigned to this font instance.

**Returns:** current text

#### setText

```java
public Font setText(String text)
```

Replaces the current text and rebuilds the cached glyph layout if the text changed.

Null input is converted into an empty string. If the incoming text is equal to the current text,
the method returns immediately without rebuilding caches.

Rebuilding the layout updates:

- cached glyph quads

- cached width

- cached height

- **`text`** — new text to render

**Returns:** this font instance for chaining

#### draw

```java
public void draw(TextureBatch batch, String text, float x, float y)
```

Renders the specified text using the provided TextureBatch at the given coordinates.

- **`batch`** — the TextureBatch used for rendering
- **`text`** — the text to be drawn
- **`x`** — the x-coordinate where the text will be rendered
- **`y`** — the y-coordinate where the text will be rendered

#### draw

```java
public void draw(TextureBatch batch, String text, float x, float y, Color tint)
```

Renders the specified text at the given position with the provided tint color.
Supports additional features such as shadow and outline rendering if enabled.

- **`batch`** — the TextureBatch used for rendering
- **`text`** — the string to draw; if null or empty, nothing will be rendered
- **`x`** — the x-coordinate where the text drawing starts
- **`y`** — the y-coordinate where the text drawing starts
- **`tint`** — the color to tint the text during rendering

#### draw

```java
public void draw(TextureBatch batch)
```

Draws the currently cached text through the supplied `TextureBatch`.

Rendering is skipped if there are no cached glyphs or if the atlas texture reports
invalid dimensions.

Pass order is:

- shadow pass, if enabled

- outline pass, if enabled

- main pass

The method expects the caller to already have started the batch.

- **`batch`** — batch used to submit glyph quads

#### dispose

```java
public void dispose()
```

Releases owned resources and clears references used by this font instance.

This disposes the owned atlas `Texture`, then nulls other references so the font
is no longer usable for rendering.

#### clearStyler

```java
public Font clearStyler()
```

Removes any runtime styler currently assigned to this font.

**Returns:** this font instance for chaining

#### getStyler

```java
public FontStyler getStyler()
```

Returns the styler currently assigned to this font.

**Returns:** active styler, or null if none is assigned

#### setStyler

```java
public Font setStyler(FontStyler styler)
```

Assigns a runtime `FontStyler` that may modify glyph output during drawing.

The styler is consulted per glyph during normal, shadow, and outline rendering paths.
It can hide glyphs, recolor them, offset them, or apply scale changes without forcing
a layout rebuild.

- **`styler`** — styler to use, or null to disable styling

**Returns:** this font instance for chaining

#### setOutline

```java
public Font setOutline(int outlinePx, Color color)
```

Enables outline rendering and updates outline settings.

The outline radius is clamped to at least `1`. When a non-null color is supplied,
the existing outline color is replaced. The outline offset cache is also refreshed so the
next draw uses the correct radius data.

- **`outlinePx`** — outline radius in pixels
- **`color`** — outline color, or null to keep the current color

**Returns:** this font instance for chaining

#### disableOutline

```java
public void disableOutline()
```

Disables outline rendering.

#### isOutlineEnabled

```java
public boolean isOutlineEnabled()
```

Returns whether outline rendering is currently enabled.

**Returns:** true if outline rendering is enabled

#### getOutlinePx

```java
public int getOutlinePx()
```

Returns the current outline radius in pixels.

**Returns:** outline radius

#### getOutlineColor

```java
public Color getOutlineColor()
```

Returns the mutable color object used by the outline pass.

**Returns:** outline color

#### setShadow

```java
public Font setShadow(float offsetX, float offsetY, Color color)
```

Enables shadow rendering and updates shadow settings.

The supplied offsets are stored directly. When a non-null color is provided,
the existing shadow color is replaced.

- **`offsetX`** — horizontal shadow offset
- **`offsetY`** — vertical shadow offset
- **`color`** — shadow color, or null to keep the current color

**Returns:** this font instance for chaining

#### disableShadow

```java
public void disableShadow()
```

Disables shadow rendering.

#### isShadowEnabled

```java
public boolean isShadowEnabled()
```

Returns whether shadow rendering is currently enabled.

**Returns:** true if shadow rendering is enabled

#### getShadowOffsetX

```java
public float getShadowOffsetX()
```

Returns the horizontal shadow offset.

**Returns:** shadow x offset

#### getShadowOffsetY

```java
public float getShadowOffsetY()
```

Returns the vertical shadow offset.

**Returns:** shadow y offset

#### getShadowColor

```java
public Color getShadowColor()
```

Returns the mutable color object used by the shadow pass.

**Returns:** shadow color

#### setScale

```java
public Font setScale(float sx, float sy)
```

Sets the glyph scale and rebuilds cached layout data.

Scale affects glyph positions, glyph size, line spacing, measured width, and measured height.
Because of that, changing either scale value triggers recalculation of metric caches and a full
rebuild of the current text layout.

- **`sx`** — horizontal scale
- **`sy`** — vertical scale

**Returns:** this font instance for chaining

#### getScaleX

```java
public float getScaleX()
```

Returns the current horizontal scale.

**Returns:** horizontal scale

#### getScaleY

```java
public float getScaleY()
```

Returns the current vertical scale.

**Returns:** vertical scale

#### setPosition

```java
public void setPosition(float x, float y)
```

Sets the drawing origin for this font.

The x and y values define the world-space origin used when cached local glyph positions
are submitted during rendering.

- **`x`** — world-space x position
- **`y`** — world-space y position

#### getX

```java
public float getX()
```

Returns the current world-space x position of this font.

**Returns:** x position

#### setX

```java
public void setX(float x)
```

Sets the world-space x position of this font.

- **`x`** — new x position

#### getY

```java
public float getY()
```

Returns the current world-space y position of this font.

**Returns:** y position

#### setY

```java
public void setY(float y)
```

Sets the world-space y position of this font.

- **`y`** — new y position

#### getWidth

```java
    public float getWidth()
```

Returns the cached width of the currently assigned text.

This value is updated whenever cached layout data is rebuilt.

**Returns:** cached text width

#### setWidth

```java
    public void setWidth(float width)
```

Directly sets the cached width value.

This method does not rebuild text layout.

- **`width`** — new width value

#### getWidth

```java
public float getWidth(String text)
```

Measures the width of the supplied text.

This overload measures the full string from start to finish. It respects tab expansion,
missing-glyph fallback advance, and newline resets.

- **`text`** — text to measure

**Returns:** measured width

#### getWidth

```java
public float getWidth(String text, int endExclusive)
```

Measures the width of the supplied text from index `0` to `endExclusive`.

This is useful when measuring partial visible ranges, caret positions, or selections.

- **`text`** — text to measure
- **`endExclusive`** — exclusive end index

**Returns:** measured width of the requested range

#### getWidth

```java
public float getWidth(String text, int startInclusive, int endExclusive)
```

Measures the width of the supplied text within a specific range.

The range is clamped internally so invalid index combinations do not throw.

- **`text`** — text to measure
- **`startInclusive`** — inclusive start index
- **`endExclusive`** — exclusive end index

**Returns:** measured width of the requested range

#### getHeight

```java
    public float getHeight()
```

Returns the cached height of the currently assigned text.

**Returns:** cached text height

#### setHeight

```java
    public void setHeight(float height)
```

Directly sets the cached height value.

This method does not rebuild text layout.

- **`height`** — new height value

#### getHeight

```java
public float getHeight(String text)
```

Measures the height of the supplied text.

Height is derived from line count. Single-line text uses the cached first-line height.
Multi-line text adds scaled line advance for each additional line.

- **`text`** — text to measure

**Returns:** measured height

#### setSize

```java
    public void setSize(float width, float height)
```

Directly sets the cached width and height values.

This does not rebuild glyph layout. It only replaces the stored dimension fields.

- **`width`** — new width value
- **`height`** — new height value

#### getColor

```java
public Color getColor()
```

Returns the mutable color object used for the main glyph pass.

**Returns:** font color

#### setColor

```java
public void setColor(Color color)
```

Sets the main glyph color by forwarding the value to the atlas texture.

- **`color`** — new main color

#### getTexture

```java
public Texture getTexture()
```

Returns the atlas texture used by this font.

**Returns:** atlas texture

#### getData

```java
public FontData getData()
```

Borrows the FontData associated with this font without copying glyph or atlas data. Resource lifetime remains governed by the font/data ownership contract; this accessor does not transfer disposal responsibility.

**Returns:** The FontData corresponding to this Font.

</details>

<a id="type-font-cachedquad"></a>

### Font.CachedQuad — internal support type

[Source](../../src/main/java/valthorne/graphics/font/Font.java#L1485)

Cached render data for one drawable glyph.

Each cached quad stores the glyph's local position, size, UVs, and metadata
used by runtime styling.

Geometry is relative to the text origin and UVs refer to the font's shared atlas.
Character, glyph, and line indices let the styler associate cached geometry with
source text without rebuilding layout every frame.

<a id="type-font-outlineoffset"></a>

### Font.OutlineOffset — internal support type

[Source](../../src/main/java/valthorne/graphics/font/Font.java#L1512)

Cached offset data used for outline rendering.

Each offset stores a pixel offset from the glyph origin and an alpha multiplier
used to soften the outline edge.

<a id="type-fontdata"></a>

### FontData

[Source](../../src/main/java/valthorne/graphics/font/FontData.java#L32)

Represents baked font atlas data and metrics for a specific pixel size and character range.

Notes:

- ascent/descent/lineHeight are stored in **pixels** at the requested font size.

- stb's `descent` is typically negative (below baseline).

- `baseline` is equivalent to `ascent` in pixels (baseline-to-top).

- Includes an STB font handle + backing buffer so we can query **kerning**.

<details>
<summary>FontData operation reference (7 declarations)</summary>

#### load

```java
public static FontData load(byte[] fontBytes, int fontSize, int startChar, int numChars)
```

Loads font data from a byte array and bakes an atlas using stb_truetype.

- **`fontBytes`** — font file bytes
- **`fontSize`** — pixel height
- **`startChar`** — first character codepoint to bake
- **`numChars`** — number of consecutive characters to bake

**Returns:** baked `FontData`

#### load

```java
public static FontData load(String path, int fontSize, int firstChar, int numChars)
```

Loads font data from a file path.

- **`path`** — font file path
- **`fontSize`** — pixel height
- **`firstChar`** — first baked character
- **`numChars`** — number of baked characters

**Returns:** baked `FontData`

#### asFont

```java
public Font asFont()
```

Converts the current `FontData` object into a `Font` object.

**Returns:** a new `Font` instance created from the current `FontData`.

#### dispose

```java
public void dispose()
```

Releases native resources owned by this baked font data.

This frees the decoded atlas image data while leaving lightweight metric and glyph
metadata available for any callers that still hold references to this object.

#### glyph

```java
public Glyph glyph(char c)
```

Fast glyph lookup.

- **`c`** — character

**Returns:** glyph or null if outside baked range

#### getKerningAdvance

```java
public float getKerningAdvance(char left, char right)
```

Kerning advance in pixels (already scaled to the baked font size).

This value should be added to your pen position before placing the second glyph.
Returns 0 if kerning data isn't available.

- **`left`** — previous character
- **`right`** — current character

**Returns:** kerning advance in pixels at baked size

#### contains

```java
public boolean contains(char c)
```

Tests whether a UTF-16 character falls within the contiguous baked glyph-array range. This checks the index range only and does not establish that a particular glyph has a drawable outline.

- **`c`** — character

**Returns:** true if the character is inside the baked range

</details>

<a id="type-fontloader"></a>

### FontLoader

[Source](../../src/main/java/valthorne/graphics/font/FontLoader.java#L21)

The FontLoader class is responsible for loading font data using specified font parameters.
It implements the AssetLoader interface, utilizing FontParameters as the configuration
and returning FontData as the loaded asset type.

This class supports loading fonts from both file paths and byte arrays, utilizing the
FontSource encapsulated in the provided FontParameters. Depending on the type of
FontSource provided, the appropriate loading method is invoked.

If an unsupported or unknown FontSource is provided, an IllegalStateException is thrown.

<details>
<summary>FontLoader operation reference (1 declarations)</summary>

#### load

```java
    public FontData load(FontParameters parameters)
```

Decodes and packs the requested character range from a path or copied byte
source using the configured pixel font size. This creates font data; callers
manage its lifetime and separately construct a rendering font when needed.

- **`parameters`** — source, font size, first character, and character count

**Returns:** newly loaded font data

**Throws `NullPointerException`:** if parameters is null

**Throws `IllegalStateException`:** if the source type is unsupported

</details>

<a id="type-fontparameters"></a>

### FontParameters

[Source](../../src/main/java/valthorne/graphics/font/FontParameters.java#L22)

Describes an encoded font source, asset name, bake size, and character range.
Construction validates a non-null source, nonblank name, positive font size, and
positive character count; the first character index is retained unchecked.
Default-range factories start at character 30 and include 254 characters.

Only name supplies the shared asset-cache key. Use distinct names for source,
size, or range variants that must coexist. Path factories defer file reads,
byte factories copy input, and classpath factories read resources immediately.

- **`source`** — encoded font source
- **`name`** — nonblank shared asset-cache key
- **`fontSize`** — positive bake size
- **`firstCharacterIndex`** — first character in the baked range
- **`characterCount`** — positive number of baked characters

<details>
<summary>FontParameters operation reference (8 declarations)</summary>

#### Constructor

```java
public FontParameters
```

Validates the initialization of a FontParameters record to ensure that all required fields
are non-null, non-blank if applicable, and greater than zero when constraints are specified.

- **`source`** — the source from which the font is loaded, must not be null
- **`name`** — the name of the font, must not be null or blank
- **`fontSize`** — the size of the font, must be greater than 0
- **`firstCharacterIndex`** — the index of the first character in the font
- **`characterCount`** — the number of characters in the font, must be greater than 0

**Throws `IllegalArgumentException`:** if source is null, name is null or blank, fontSize is less than or equal to 0, or characterCount is less than or equal to 0

#### fromPath

```java
public static FontParameters fromPath(String path, int fontSize)
```

Creates a new `FontParameters` instance given the path to the font file
and the desired font size. This method uses default values for the first character
index and character count.

- **`path`** — the file path pointing to the font resource, must not be null or blank
- **`fontSize`** — the size of the font, must be greater than 0

**Returns:** a new `FontParameters` instance representing the font configuration

**Throws `IllegalArgumentException`:** if the path is null, blank, or if fontSize is less than or equal to 0

#### fromPath

```java
public static FontParameters fromPath(String path, String name, int fontSize, int firstChar, int count)
```

Creates a new `FontParameters` instance from the specified file path, font name,
font size, first character index, and character count.

- **`path`** — the file path pointing to the font resource; must not be null or blank
- **`name`** — the name of the font; must not be null or blank
- **`fontSize`** — the size of the font; must be greater than 0
- **`firstChar`** — the index of the first character in the font
- **`count`** — the number of characters in the font; must be greater than 0

**Returns:** a new `FontParameters` instance representing the font configuration

**Throws `IllegalArgumentException`:** if the path is null or blank,
if name is null or blank,
if fontSize is less than or equal to 0,
or if count is less than or equal to 0

#### fromBytes

```java
public static FontParameters fromBytes(byte[] bytes, String name, int fontSize)
```

Creates a new `FontParameters` instance from the specified byte array, font name,
and font size. This method uses default values for the first character index and character count.

- **`bytes`** — a byte array containing the font data; must not be null or empty
- **`name`** — the name of the font; must not be null or blank
- **`fontSize`** — the size of the font; must be greater than 0

**Returns:** a new `FontParameters` instance representing the font configuration

**Throws `IllegalArgumentException`:** if bytes are null or empty, if name is null or blank,
or if fontSize is less than or equal to 0

#### fromBytes

```java
public static FontParameters fromBytes(byte[] bytes, String name, int fontSize, int firstChar, int count)
```

Creates a new `FontParameters` instance from the specified byte array, font name,
font size, first character index, and character count.

- **`bytes`** — a byte array containing the font data; must not be null or empty
- **`name`** — the name of the font; must not be null or blank
- **`fontSize`** — the size of the font; must be greater than 0
- **`firstChar`** — the index of the first character in the font
- **`count`** — the number of characters in the font; must be greater than 0

**Returns:** a new `FontParameters` instance representing the font configuration

**Throws `IllegalArgumentException`:** if bytes are null or empty, if name is null or blank,
if fontSize is less than or equal to 0, or if count is less than or equal to 0

#### fromClasspath

```java
public static FontParameters fromClasspath(String resourcePath, String name, int fontSize)
```

Creates a new `FontParameters` instance from a resource path on the classpath,
along with the specified font name and font size. This method uses default values
for the first character index and character count.

- **`resourcePath`** — the path to the font resource located on the classpath; must not be null or blank
- **`name`** — the name of the font; must not be null or blank
- **`fontSize`** — the size of the font; must be greater than 0

**Returns:** a new `FontParameters` instance representing the font configuration

**Throws `IllegalArgumentException`:** if resourcePath is null or blank, if name is null or blank,
or if fontSize is less than or equal to 0

**Throws `valthorne.io.file.ValthorneFileException`:** if the classpath resource
is missing or cannot be read; resource bytes are read synchronously

#### fromClasspath

```java
public static FontParameters fromClasspath(String resourcePath, String name, int fontSize, int firstChar, int count)
```

Creates a new `FontParameters` instance from the specified resource path on the
classpath, font name, font size, first character index, and character count.

- **`resourcePath`** — the path to the font resource located on the classpath; must not be null or blank
- **`name`** — the name of the font; must not be null or blank
- **`fontSize`** — the size of the font; must be greater than 0
- **`firstChar`** — the index of the first character in the font
- **`count`** — the number of characters in the font; must be greater than 0

**Returns:** a new `FontParameters` instance representing the font configuration

**Throws `IllegalArgumentException`:** if resourcePath is null or blank, if name is null or blank,
if fontSize is less than or equal to 0, or if count is less than or equal to 0

**Throws `valthorne.io.file.ValthorneFileException`:** if the classpath resource
is missing or cannot be read; resource bytes are read synchronously

#### key

```java
    public String key()
```

Returns name as the asset-cache identity without including source contents
or loading options. Equal names can therefore reuse an existing cached load
even when other parameters differ.

**Returns:** nonblank cache key supplied at construction

</details>

<a id="type-fontsource"></a>

### FontSource

[Source](../../src/main/java/valthorne/graphics/font/FontSource.java#L14)

Represents the source of font data. It is a sealed interface that has two specific implementations:
one for file path-based font sources and another for raw byte array-based font sources.
Sources describe encoded data without decoding it or allocating graphics resources.
Byte sources use defensive copies on input and access; path sources validate text
only and defer resource existence checks until loading.

<a id="type-fontsource-pathsource"></a>

### FontSource.PathSource

[Source](../../src/main/java/valthorne/graphics/font/FontSource.java#L24)

Immutable description of a font path whose contents are loaded later.
Only nonblank path text is validated; construction does not check existence,
read bytes, or allocate a font. The original path string is retained unchanged.

- **`path`** — nonblank font path

<details>
<summary>FontSource.PathSource operation reference (1 declarations)</summary>

#### Constructor

```java
public PathSource
```

Records a nonblank path without resolving or opening it. Whitespace is
checked for emptiness but the original path text is retained unchanged.

- **`path`** — resource path to retain

**Throws `IllegalArgumentException`:** if path is null or blank

</details>

<a id="type-fontsource-bytessource"></a>

### FontSource.BytesSource

[Source](../../src/main/java/valthorne/graphics/font/FontSource.java#L45)

A record that represents a font source defined by raw byte data.
This source allows a font to be initialized with a byte array of the font's encoded data.

Instances of this record ensure the encapsulated byte array is safely copied for immutability.

<details>
<summary>FontSource.BytesSource operation reference (2 declarations)</summary>

#### Constructor

```java
public BytesSource
```

Copies encoded font bytes so later changes to the input cannot change
this source. Format validity is checked by the loader, not this constructor.

- **`bytes`** — nonempty encoded data

**Throws `IllegalArgumentException`:** if bytes is null or empty

#### bytes

```java
        public byte[] bytes()
```

Returns a fresh copy of the encoded data. Mutating the returned array
does not modify this source; every call allocates another array.

**Returns:** independent copy of the stored bytes

</details>

<a id="type-fontstyler"></a>

### FontStyler

[Source](../../src/main/java/valthorne/graphics/font/FontStyler.java#L27)

Represents a functional interface for applying custom styling to glyphs during rendering.
Implementations of this interface define the logic for modifying glyph styles based
on their associated context.

The `style` method allows adjustment of glyph visual attributes such as
color, scale, offset, or visibility by operating on the `GlyphStyle` object,
using information provided in the `GlyphContext`.

This interface is typically used to define transformations or effects to be applied
to individual glyphs in a textual rendering pipeline.

Example usage:

```java
FontStyler styler = (style, ctx) -> {
    style.setOffset(ctx.getCharIndex() * 2.0f, 0f);
    style.setScale(1.5f, 1.5f);
    style.setColor(1.0f, 0.5f, 0.0f, 1.0f);
};
```

<details>
<summary>FontStyler operation reference (1 declarations)</summary>

#### style

```java
void style(GlyphStyle style, GlyphContext ctx)
```

Applies a styling operation to a glyph's visual properties.

This method modifies the attributes of the provided `GlyphStyle` instance,
such as its position, scale, color, or visibility, using information from the
associated `GlyphContext`. The `GlyphContext` contains metadata
about the glyph being styled, such as its character, indices, and position
within the text layout.

- **`style`** — the `GlyphStyle` object that represents the visual attributes of the glyph. This object is modified by the implementation to apply the desired styling.
- **`ctx`** — the `GlyphContext` providing metadata about the glyph, including its character, indices, and positional context, to inform the styling operation.

</details>

<a id="type-glyph"></a>

### Glyph

[Source](../../src/main/java/valthorne/graphics/font/Glyph.java#L24)

Represents a glyph in a font, containing positional and offset information.

This class is a record that stores data about a glyph's character, its
bounding box coordinates, and its positional offsets.
It is used to describe glyphs for rendering text in graphical applications.
Atlas bounds are pixel coordinates from packed font data; offsets and advance
describe placement relative to the text pen. Values are retained without validation.

- **`character`** — UTF-16 character represented by the packed glyph
- **`x0`** — left atlas pixel coordinate
- **`y0`** — top atlas pixel coordinate
- **`x1`** — exclusive right atlas pixel coordinate
- **`y1`** — exclusive bottom atlas pixel coordinate
- **`xOffset`** — horizontal offset of the first quad corner from the pen
- **`yOffset`** — vertical offset of the first quad corner from the baseline
- **`xOffset2`** — horizontal offset of the opposite quad corner from the pen
- **`yOffset2`** — vertical offset of the opposite quad corner from the baseline
- **`xAdvance`** — horizontal pen advance in pixels after this glyph

<details>
<summary>Glyph operation reference (2 declarations)</summary>

#### width

```java
public int width()
```

Calculates the width of the glyph's bounding box.

**Returns:** the width of the bounding box, computed as the difference between x1 and x0

#### height

```java
public int height()
```

Calculates the height of the glyph's bounding box.

**Returns:** the height of the bounding box, computed as the difference between y1 and y0

</details>

<a id="type-glyphcontext"></a>

### GlyphContext

[Source](../../src/main/java/valthorne/graphics/font/GlyphContext.java#L13)

Represents context information for a glyph in the text rendering pipeline.

This class encapsulates metadata about a glyph, including its character,
position within the text layout, and layout bounding properties. It is used
to manage and retrieve glyph-specific information during rendering and styling.

<details>
<summary>GlyphContext operation reference (11 declarations)</summary>

#### character

```java
public char character()
```

Retrieves the character represented by this glyph.

**Returns:** the character.

#### charIndex

```java
public int charIndex()
```

Retrieves the index of the character within the original text.

**Returns:** the character index.

#### glyphIndex

```java
public int glyphIndex()
```

Retrieves the index of the glyph in the rendered sequence.

**Returns:** the glyph index.

#### lineIndex

```java
public int lineIndex()
```

Retrieves the index of the line in the text layout where this glyph is located.

**Returns:** the line index.

#### baseX

```java
public float baseX()
```

Retrieves the base x-coordinate of the glyph in layout space.

**Returns:** the base x-coordinate.

#### baseY

```java
public float baseY()
```

Retrieves the base y-coordinate of the glyph in layout space.

**Returns:** the base y-coordinate.

#### baseW

```java
public float baseW()
```

Retrieves the width of the glyph's bounding box in layout space.

**Returns:** the base width.

#### baseH

```java
public float baseH()
```

Retrieves the height of the glyph's bounding box in layout space.

**Returns:** the base height.

#### drawX

```java
public float drawX()
```

Retrieves the x-coordinate of the glyph in drawing space.

**Returns:** the draw x-coordinate.

#### drawY

```java
public float drawY()
```

Retrieves the y-coordinate of the glyph in drawing space.

**Returns:** the draw y-coordinate.

#### set

```java
public void set(char c, int charIndex, int glyphIndex, int lineIndex, float baseX, float baseY, float baseW, float baseH, float drawX, float drawY)
```

Updates all properties of the glyph context with the provided values.

- **`c`** — the character represented by the glyph.
- **`charIndex`** — the index of the character in the original text.
- **`glyphIndex`** — the index of the glyph in the rendered sequence.
- **`lineIndex`** — the index of the line in the text layout.
- **`baseX`** — the base x-coordinate in layout space.
- **`baseY`** — the base y-coordinate in layout space.
- **`baseW`** — the width of the glyph's bounding box in layout space.
- **`baseH`** — the height of the glyph's bounding box in layout space.
- **`drawX`** — the x-coordinate in drawing space.
- **`drawY`** — the y-coordinate in drawing space.

</details>

<a id="type-glyphstyle"></a>

### GlyphStyle

[Source](../../src/main/java/valthorne/graphics/font/GlyphStyle.java#L16)

Represents the visual styling attributes for a glyph.

This class provides various properties to define the appearance of a glyph,
including its color, scale, position offset, and visibility. Additionally,
this class allows for resetting the styling attributes to their default values
and dynamically modifying the color settings.

<details>
<summary>GlyphStyle operation reference (24 declarations)</summary>

#### getOffsetX

```java
public float getOffsetX()
```

Retrieves the horizontal offset of the glyph.

**Returns:** the horizontal offset.

#### setOffsetX

```java
public void setOffsetX(float offsetX)
```

Sets the horizontal offset for the glyph.

- **`offsetX`** — the value to set as the horizontal offset.

#### getOffsetY

```java
public float getOffsetY()
```

Retrieves the vertical offset of the glyph.

**Returns:** the vertical offset.

#### setOffsetY

```java
public void setOffsetY(float offsetY)
```

Sets the vertical offset for the glyph.

- **`offsetY`** — the value to set as the vertical offset.

#### getScaleX

```java
public float getScaleX()
```

Retrieves the horizontal scale of the glyph.

**Returns:** the horizontal scale.

#### setScaleX

```java
public void setScaleX(float scaleX)
```

Sets the horizontal scale of the glyph.

- **`scaleX`** — the value to set as the horizontal scale.

#### getScaleY

```java
public float getScaleY()
```

Retrieves the vertical scale of the glyph.

**Returns:** the vertical scale.

#### setScaleY

```java
public void setScaleY(float scaleY)
```

Sets the vertical scale of the glyph.

- **`scaleY`** — the value to set as the vertical scale.

#### isVisible

```java
public boolean isVisible()
```

Checks if the glyph is visible.

**Returns:** `true` if the glyph is visible, otherwise `false`.

#### setVisible

```java
public void setVisible(boolean visible)
```

Sets the visibility of the glyph.

- **`visible`** — `true` to make the glyph visible, `false` to hide it.

#### hasColor

```java
public boolean hasColor()
```

Checks if the glyph has a custom color set.

**Returns:** `true` if a custom color is applied, otherwise `false`.

#### setOffset

```java
public void setOffset(float offsetX, float offsetY)
```

Sets both horizontal and vertical offsets for the glyph.

- **`offsetX`** — the value to set as the horizontal offset.
- **`offsetY`** — the value to set as the vertical offset.

#### setScale

```java
public void setScale(float scaleX, float scaleY)
```

Sets both horizontal and vertical scales for the glyph.

- **`scaleX`** — the value to set as the horizontal scale.
- **`scaleY`** — the value to set as the vertical scale.

#### hide

```java
public void hide()
```

Hides the glyph by setting its visibility to `false`.

#### show

```java
public void show()
```

Shows the glyph by setting its visibility to `true`.

#### getColor

```java
public Color getColor()
```

Retrieves the current color of the glyph.

The color returned is an immutable reference to the `Color` object.

**Returns:** the color of the glyph.

#### setColor

```java
public void setColor(Color color)
```

Sets a custom color for the glyph by copying the values from another `Color` instance.
If the provided color is `null`, the custom color is cleared.

- **`color`** — the `Color` instance to copy values from, or `null` to clear the color.

#### clearColor

```java
public void clearColor()
```

Clears the custom color of the glyph, resetting it to the default state.
Sets `hasColor` to `false`.

#### setColor

```java
public void setColor(float r, float g, float b, float a)
```

Sets a custom color for the glyph using individual RGBA components.

- **`r`** — the red component (0.0 - 1.0).
- **`g`** — the green component (0.0 - 1.0).
- **`b`** — the blue component (0.0 - 1.0).
- **`a`** — the alpha (opacity) component (0.0 - 1.0).

#### r

```java
public float r()
```

Retrieves the red component of the glyph's color.

**Returns:** the red component of the color.

#### g

```java
public float g()
```

Retrieves the green component of the glyph's color.

**Returns:** the green component of the color.

#### b

```java
public float b()
```

Retrieves the blue component of the glyph's color.

**Returns:** the blue component of the color.

#### a

```java
public float a()
```

Retrieves the alpha (opacity) component of the glyph's color.

**Returns:** the alpha component of the color.

#### reset

```java
public void reset()
```

Resets all the styling attributes of the glyph to their default values.

This includes resetting offsets, scales, visibility, and color state.
The color remains white with full opacity, but any custom color is cleared.

</details>

## Related guides

- [Asset loading and caching](assets.md)
- [Textures, sprites, atlases, and batching](textures.md)
- [Slug vector fonts](slug-fonts.md)
- [Standard UI controls](ui-controls.md)
