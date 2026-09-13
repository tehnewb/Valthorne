# Textures, sprites, atlases, and batching

Author: Albert Beaupre

[System manual](README.md)

## Purpose

Use `Texture` for a GPU image, `TextureRegion` for a pixel rectangle within it, and `Sprite` when the draw object also needs transform and tint state. `TextureBatch` submits many textured quads efficiently. Atlas and nine-patch tools address different problems: packing many images together and resizing UI images while retaining borders.

## Features and when to use them

| Feature | Purpose |
| --- | --- |
| Texture data and upload | TextureData holds decoded pixels; Texture uploads or wraps the GPU image. |
| Regions and sprites | Regions select source pixels; sprites add scale, rotation, flipping, bounds, and color. |
| Batching | Begin, submit, and end group compatible draws; texture limits and state changes can flush queued work. |
| Atlases and packing | Pack images or subregions into shared storage and use returned regions. |
| Nine-patch | Split borders and center areas so panels can resize without stretching every edge equally. |
| Offscreen drawing and clipping | Framebuffers redirect output; scissor and translation stacks constrain or offset batch content. |

## Getting started

1. Create a batch and textures with a current context. Keep resource loading separate from per-frame drawing.
2. Set projection or use your scene's configured batch, then begin once.
3. Submit sprites or texture regions, balancing translation and clipping operations.
4. End before switching incompatible render state, and dispose owned textures/batches when finished.

## Ownership and lifecycle

`new Texture(TextureData)` borrows the CPU data. A sprite built from an existing texture generally borrows that texture. An atlas result can require releasing both its GPU texture and decoded atlas data. Do not dispose an attachment that is still owned by its framebuffer.

## Important behavior

- Sprite draw overloads combine supplied transforms with stored scale, origin, rotation, flips, and tint.
- Source rectangles use pixels while UV-specific methods use normalized coordinates.
- A draw count can rise when textures or shaders change; group compatible work before increasing capacity.

## Components and examples

The sections below explain each component and its declared public or protected operations. Expand an operation reference for signatures, parameters, results, and edge cases. Inherited operations are documented with their base type. Examples embedded in component descriptions are integration fragments: supply the surrounding resources and application variables they name.

- [`Color`](#type-color)
- [`Drawable`](#type-drawable)
- [`DrawFunction`](#type-drawfunction)
- [`ImmediateTextureRenderer`](#type-immediatetexturerenderer)
- [`Sprite`](#type-sprite)
- [`FrameBuffer`](#type-framebuffer)
- [`NinePatchDrawable`](#type-ninepatchdrawable)
- [`NinePatchTexture`](#type-ninepatchtexture)
- [`SpriteCulling`](#type-spriteculling)
- [`Texture`](#type-texture)
- [`TextureAtlas`](#type-textureatlas)
- [`TextureAtlas.Result`](#type-textureatlas-result)
- [`TextureBatch`](#type-texturebatch)
- [`TextureBatchContract`](#type-texturebatchcontract)
- [`TextureBatchShader`](#type-texturebatchshader)
- [`TextureData`](#type-texturedata)
- [`TextureDrawable`](#type-texturedrawable)
- [`TextureFilter`](#type-texturefilter)
- [`TextureLoader`](#type-textureloader)
- [`TexturePacker`](#type-texturepacker)
- [`TextureParameters`](#type-textureparameters)
- [`TextureRegion`](#type-textureregion)
- [`TextureRegionDrawable`](#type-textureregiondrawable)
- [`TextureSource`](#type-texturesource)
- [`TextureSource.PathSource`](#type-texturesource-pathsource)
- [`TextureSource.BytesSource`](#type-texturesource-bytessource)
- [`TextureUtility`](#type-textureutility)

<a id="type-color"></a>

### Color

[Source](../../src/main/java/valthorne/graphics/Color.java#L12)

Represents a color in RGBA format. Colors are immutable and stored internally as 32-bit integers
in the format 0xAARRGGBB where AA=alpha, RR=red, GG=green, BB=blue.

<details>
<summary>Color operation reference (142 declarations)</summary>

#### WHITE

```java
public static final  Color WHITE
```

Pure white color (0xFFFFFFFF)

#### BLACK

```java
public static final  Color BLACK
```

Pure black color (0xFF000000)

#### RED

```java
public static final  Color RED
```

Pure red color (0xFFFF0000)

#### GREEN

```java
public static final  Color GREEN
```

Pure green color (0xFF00FF00)

#### BLUE

```java
public static final  Color BLUE
```

Pure blue color (0xFF0000FF)

#### MAGENTA

```java
public static final  Color MAGENTA
```

Magenta color (0xFFFF00FF)

#### YELLOW

```java
public static final  Color YELLOW
```

Yellow color (0xFFFFFF00)

#### CYAN

```java
public static final  Color CYAN
```

Cyan color (0xFF00FFFF)

#### ORANGE

```java
public static final  Color ORANGE
```

Orange color (0xFFFFA500)

#### PURPLE

```java
public static final  Color PURPLE
```

Purple color (0xFF800080)

#### PINK

```java
public static final  Color PINK
```

Pink color (0xFFFFC0CB)

#### LIME

```java
public static final  Color LIME
```

Lime color (0xFF32CD32)

#### TEAL

```java
public static final  Color TEAL
```

Teal color (0xFF008080)

#### NAVY

```java
public static final  Color NAVY
```

Navy blue color (0xFF000080)

#### GRAY

```java
public static final  Color GRAY
```

Medium gray color (0xFF808080)

#### LIGHT_GRAY

```java
public static final  Color LIGHT_GRAY
```

Light gray color (0xFFD3D3D3)

#### DARK_GRAY

```java
public static final  Color DARK_GRAY
```

Dark gray color (0xFF404040)

#### BROWN

```java
public static final  Color BROWN
```

Brown color (0xFF8B4513)

#### GOLD

```java
public static final  Color GOLD
```

Gold color (0xFFFFD700)

#### CRIMSON

```java
public static final  Color CRIMSON
```

Crimson red color (0xFFDC143C)

#### MAROON

```java
public static final  Color MAROON
```

Maroon red color (0xFF800000)

#### SALMON

```java
public static final  Color SALMON
```

Salmon pink color (0xFFFA8072)

#### FIREBRICK

```java
public static final  Color FIREBRICK
```

Firebrick red color (0xFFB22222)

#### DARK_RED

```java
public static final  Color DARK_RED
```

Dark red color (0xFF8B0000)

#### INDIAN_RED

```java
public static final  Color INDIAN_RED
```

Indian red color (0xFFCD5C5C)

#### TOMATO

```java
public static final  Color TOMATO
```

Tomato red color (0xFFFF6347)

#### CORAL

```java
public static final  Color CORAL
```

Coral orange color (0xFFFF7F50)

#### DARK_ORANGE

```java
public static final  Color DARK_ORANGE
```

Dark orange color (0xFFFF8C00)

#### CHOCOLATE

```java
public static final  Color CHOCOLATE
```

Chocolate brown color (0xFFD2691E)

#### TAN

```java
public static final  Color TAN
```

Tan brown color (0xFFD2B48C)

#### KHAKI

```java
public static final  Color KHAKI
```

Khaki yellow color (0xFFF0E68C)

#### BEIGE

```java
public static final  Color BEIGE
```

Beige color (0xFFF5F5DC)

#### OLIVE

```java
public static final  Color OLIVE
```

Olive green color (0xFF808000)

#### MUSTARD

```java
public static final  Color MUSTARD
```

Mustard yellow color (0xFFFFDB58)

#### LIGHT_YELLOW

```java
public static final  Color LIGHT_YELLOW
```

Light yellow color (0xFFFFFFE0)

#### DARK_GREEN

```java
public static final  Color DARK_GREEN
```

Dark green color (0xFF006400)

#### FOREST_GREEN

```java
public static final  Color FOREST_GREEN
```

Forest green color (0xFF228B22)

#### SEA_GREEN

```java
public static final  Color SEA_GREEN
```

Sea green color (0xFF2E8B57)

#### SPRING_GREEN

```java
public static final  Color SPRING_GREEN
```

Spring green color (0xFF00FF7F)

#### TURQUOISE

```java
public static final  Color TURQUOISE
```

Turquoise color (0xFF40E0D0)

#### MINT_GREEN

```java
public static final  Color MINT_GREEN
```

Mint green color (0xFF98FF98)

#### OLIVE_DRAB

```java
public static final  Color OLIVE_DRAB
```

Olive drab green color (0xFF6B8E23)

#### HONEYDEW

```java
public static final  Color HONEYDEW
```

Honeydew green color (0xFFF0FFF0)

#### SKY_BLUE

```java
public static final  Color SKY_BLUE
```

Sky blue color (0xFF87CEEB)

#### LIGHT_BLUE

```java
public static final  Color LIGHT_BLUE
```

Light blue color (0xFFADD8E6)

#### STEEL_BLUE

```java
public static final  Color STEEL_BLUE
```

Steel blue color (0xFF4682B4)

#### ROYAL_BLUE

```java
public static final  Color ROYAL_BLUE
```

Royal blue color (0xFF4169E1)

#### DEEP_SKY_BLUE

```java
public static final  Color DEEP_SKY_BLUE
```

Deep sky blue color (0xFF00BFFF)

#### MIDNIGHT_BLUE

```java
public static final  Color MIDNIGHT_BLUE
```

Midnight blue color (0xFF191970)

#### DODGER_BLUE

```java
public static final  Color DODGER_BLUE
```

Dodger blue color (0xFF1E90FF)

#### PLUM

```java
public static final  Color PLUM
```

Plum purple color (0xFFDDA0DD)

#### ORCHID

```java
public static final  Color ORCHID
```

Orchid purple color (0xFFDA70D6)

#### LAVENDER

```java
public static final  Color LAVENDER
```

Lavender color (0xFFE6E6FA)

#### INDIGO

```java
public static final  Color INDIGO
```

Indigo color (0xFF4B0082)

#### VIOLET

```java
public static final  Color VIOLET
```

Violet color (0xFFEE82EE)

#### HOT_PINK

```java
public static final  Color HOT_PINK
```

Hot pink color (0xFFFF69B4)

#### DEEP_PINK

```java
public static final  Color DEEP_PINK
```

Deep pink color (0xFFFF1493)

#### SADDLE_BROWN

```java
public static final  Color SADDLE_BROWN
```

Saddle brown color (0xFF8B4513)

#### PERU

```java
public static final  Color PERU
```

Peru brown color (0xFFCD853F)

#### WHEAT

```java
public static final  Color WHEAT
```

Wheat color (0xFFF5DEB3)

#### SIENNA

```java
public static final  Color SIENNA
```

Sienna brown color (0xFFA0522D)

#### BURLYWOOD

```java
public static final  Color BURLYWOOD
```

Burlywood brown color (0xFFDEB887)

#### MOCCASIN

```java
public static final  Color MOCCASIN
```

Moccasin color (0xFFFFE4B5)

#### AQUA_MARINE

```java
public static final  Color AQUA_MARINE
```

Aquamarine color (0xFF7FFFD4)

#### PALE_TURQUOISE

```java
public static final  Color PALE_TURQUOISE
```

Pale turquoise color (0xFFAFEEEE)

#### CADET_BLUE

```java
public static final  Color CADET_BLUE
```

Cadet blue color (0xFF5F9EA0)

#### POWDER_BLUE

```java
public static final  Color POWDER_BLUE
```

Powder blue color (0xFFB0E0E6)

#### TEAL_BLUE

```java
public static final  Color TEAL_BLUE
```

Teal blue color (0xFF367588)

#### PEACH_PUFF

```java
public static final  Color PEACH_PUFF
```

Peach puff color (0xFFFFDAB9)

#### MISTY_ROSE

```java
public static final  Color MISTY_ROSE
```

Misty rose color (0xFFFFE4E1)

#### LIGHT_CORAL

```java
public static final  Color LIGHT_CORAL
```

Light coral color (0xFFF08080)

#### BISQUE

```java
public static final  Color BISQUE
```

Bisque color (0xFFFFE4C4)

#### LIGHT_SALMON

```java
public static final  Color LIGHT_SALMON
```

Light salmon color (0xFFFFA07A)

#### BLUSH

```java
public static final  Color BLUSH
```

Blush pink color (0xFFDE5D83)

#### DARK_GOLDENROD

```java
public static final  Color DARK_GOLDENROD
```

Dark goldenrod color (0xFFB8860B)

#### ROSY_BROWN

```java
public static final  Color ROSY_BROWN
```

Rosy brown color (0xFFBC8F8F)

#### CORNSILK

```java
public static final  Color CORNSILK
```

Cornsilk color (0xFFFFF8DC)

#### LINEN

```java
public static final  Color LINEN
```

Linen color (0xFFFAF0E6)

#### OLD_LACE

```java
public static final  Color OLD_LACE
```

Old lace color (0xFFFDF5E6)

#### SEPIA

```java
public static final  Color SEPIA
```

Sepia color (0xFF704214)

#### PAPAYA_WHIP

```java
public static final  Color PAPAYA_WHIP
```

Papaya whip color (0xFFFFEFD5)

#### PALE_GREEN

```java
public static final  Color PALE_GREEN
```

Pale green color (0xFF98FB98)

#### PALE_GOLDENROD

```java
public static final  Color PALE_GOLDENROD
```

Pale goldenrod color (0xFFEEE8AA)

#### PALE_VIOLET_RED

```java
public static final  Color PALE_VIOLET_RED
```

Pale violet red color (0xFFDB7093)

#### PALE_BLUE

```java
public static final  Color PALE_BLUE
```

Pale blue color (0xFFAFDCFF)

#### PALE_PINK

```java
public static final  Color PALE_PINK
```

Pale pink color (0xFFFFCCFF)

#### SILVER

```java
public static final  Color SILVER
```

Silver color (0xFFC0C0C0)

#### SLATE_GRAY

```java
public static final  Color SLATE_GRAY
```

Slate gray color (0xFF708090)

#### LIGHT_SLATE_GRAY

```java
public static final  Color LIGHT_SLATE_GRAY
```

Light slate gray color (0xFF778899)

#### GAINSBORO

```java
public static final  Color GAINSBORO
```

Gainsboro color (0xFFDCDCDC)

#### ASH_GRAY

```java
public static final  Color ASH_GRAY
```

Ash gray color (0xFFB2BEB5)

#### CHARCOAL

```java
public static final  Color CHARCOAL
```

Charcoal color (0xFF36454F)

#### JET

```java
public static final  Color JET
```

Jet black color (0xFF343434)

#### ALICE_BLUE

```java
public static final  Color ALICE_BLUE
```

Alice blue color (0xFFF0F8FF)

#### AZURE

```java
public static final  Color AZURE
```

Azure color (0xFFF0FFFF)

#### GHOST_WHITE

```java
public static final  Color GHOST_WHITE
```

Ghost white color (0xFFF8F8FF)

#### SNOW

```java
public static final  Color SNOW
```

Snow white color (0xFFFFFAFA)

#### IVORY

```java
public static final  Color IVORY
```

Ivory color (0xFFFFFFF0)

#### FLORAL_WHITE

```java
public static final  Color FLORAL_WHITE
```

Floral white color (0xFFFFFAF0)

#### CLOUD_WHITE

```java
public static final  Color CLOUD_WHITE
```

Cloud white color (0xFFF7F7FF)

#### ELECTRIC_LIME

```java
public static final  Color ELECTRIC_LIME
```

Electric lime color (0xFFCCFF00)

#### NEON_PINK

```java
public static final  Color NEON_PINK
```

Neon pink color (0xFFFF6EC7)

#### NEON_GREEN

```java
public static final  Color NEON_GREEN
```

Neon green color (0xFF39FF14)

#### NEON_BLUE

```java
public static final  Color NEON_BLUE
```

Neon blue color (0xFF1F51FF)

#### NEON_PURPLE

```java
public static final  Color NEON_PURPLE
```

Neon purple color (0xFFBC13FE)

#### FERN_GREEN

```java
public static final  Color FERN_GREEN
```

Fern green color (0xFF4F7942)

#### MOSS_GREEN

```java
public static final  Color MOSS_GREEN
```

Moss green color (0xFF8A9A5B)

#### ARMY_GREEN

```java
public static final  Color ARMY_GREEN
```

Army green color (0xFF4B5320)

#### SAGE

```java
public static final  Color SAGE
```

Sage green color (0xFF9C9F84)

#### BASIL

```java
public static final  Color BASIL
```

Basil green color (0xFF568203)

#### TRANSPARENT

```java
public static final  Color TRANSPARENT
```

Fully transparent color (0x00000000)

#### TRANSLUCENT

```java
public static final  Color TRANSLUCENT
```

Semi-transparent black color (0x80000000)

#### Constructor

```java
public Color(int rgba)
```

Creates a new Color from an RGBA integer value.

- **`rgba`** — The color value in 0xAARRGGBB format

#### Constructor

```java
public Color(float red, float green, float blue, float alpha)
```

Creates a new Color from individual color components.

- **`red`** — Red component (0.0-1.0)
- **`green`** — Green component (0.0-1.0)
- **`blue`** — Blue component (0.0-1.0)
- **`alpha`** — Alpha component (0.0-1.0)

#### fromRGBA

```java
public static Color fromRGBA(int r, int g, int b, int a)
```

Creates a new `Color` from packed components.

- **`r`** — red 0..255
- **`g`** — green 0..255
- **`b`** — blue 0..255
- **`a`** — alpha 0..255

**Returns:** packed color

#### fromHex

```java
public static Color fromHex(String hex)
```

Parses a hex string into a `Color`.

Accepted formats:

- `"RRGGBB"` or `"#RRGGBB"` (alpha assumed 255)

- `"AARRGGBB"` or `"#AARRGGBB"`

- **`hex`** — hex string

**Returns:** parsed color

#### set

```java
public void set(Color endColor)
```

Sets the current color to the given color by copying its RGBA value.

- **`endColor`** — The color from which to copy the RGBA value.

#### set

```java
public void set(float red, float green, float blue, float alpha)
```

Sets the color components of this object using the provided red, green, blue,
and alpha values. Each component should be a normalized float value in the
range [0.0, 1.0]. The specified values are internally converted to an
integer-based RGBA representation.

- **`red`** — The red component as a normalized float in the range [0.0, 1.0].
- **`green`** — The green component as a normalized float in the range [0.0, 1.0].
- **`blue`** — The blue component as a normalized float in the range [0.0, 1.0].
- **`alpha`** — The alpha (transparency) component as a normalized float in the range [0.0, 1.0].

#### mul

```java
public void mul(Color other)
```

Multiplies this color by another color (component-wise), storing the result in this instance.

- **`other`** — other color

#### add

```java
public void add(Color other)
```

Adds another color to this one (component-wise), clamped to [0..1], storing the result in this instance.

- **`other`** — other color

#### lerp

```java
public void lerp(Color target, float t)
```

Linearly interpolates this color towards `target` by `t` and stores the result in this instance.

- **`target`** — target color
- **`t`** — interpolation factor in [0..1]

#### opaque

```java
public void opaque()
```

Sets this color to opaque (alpha = 1).

#### transparent

```java
public void transparent()
```

Sets this color to fully transparent (alpha = 0).

#### withAlpha

```java
public Color withAlpha(float alpha)
```

Creates an independent color retaining the packed RGB bytes and replacing alpha. Finite alpha is clamped to zero through one and truncated to an eight-bit value; this color is unchanged.

- **`alpha`** — normalized alpha in [0..1]

**Returns:** a new Color with the same RGB but a different alpha.

#### toHex

```java
public String toHex()
```

Formats all four packed bytes as an uppercase, zero-padded eight-digit hexadecimal string prefixed with #. Alpha precedes red, green, and blue.

**Returns:** hex string in `#AARRGGBB` format.

#### isOpaque

```java
public boolean isOpaque()
```

Checks whether the stored eight-bit alpha is exactly 255. This tests color data only; texture alpha and blend state may still affect final coverage.

**Returns:** true if this color is fully opaque (alpha == 255).

#### isTransparent

```java
public boolean isTransparent()
```

Checks whether the stored eight-bit alpha is exactly zero. RGB bytes are retained and do not affect this predicate.

**Returns:** true if this color is fully transparent (alpha == 0).

#### copy

```java
public Color copy()
```

Creates a separate mutable color with identical packed channel bytes. Later channel changes on either instance do not affect the other.

**Returns:** a copy of this color.

#### a

```java
public void a(float a)
```

Sets the alpha (transparency) component of the color.
The alpha value determines the transparency level, where 0.0 represents
fully transparent and 1.0 represents fully opaque.

- **`a`** — The alpha component as a normalized float value in the range [0.0, 1.0].

#### r

```java
public void r(float r)
```

Sets the red component of the color.
The red value is specified as a normalized float in the range [0.0, 1.0].
This method updates the internal RGBA representation based on the provided value.

- **`r`** — The red component as a normalized float value in the range [0.0, 1.0].

#### g

```java
public void g(float g)
```

Sets the green component of the color.
The green value is specified as a normalized float in the range [0.0, 1.0].
This method updates the internal RGBA representation based on the provided value.

- **`g`** — The green component as a normalized float value in the range [0.0, 1.0].

#### b

```java
public void b(float b)
```

Sets the blue component of the color.
The blue value is specified as a normalized float in the range [0.0, 1.0].
This method updates the internal RGBA representation based on the provided value.

- **`b`** — The blue component as a normalized float value in the range [0.0, 1.0].

#### getRed

```java
public int getRed()
```

Gets the red component of this color (0-255).

**Returns:** The red value

#### getGreen

```java
public int getGreen()
```

Gets the green component of this color (0-255).

**Returns:** The green value

#### getBlue

```java
public int getBlue()
```

Gets the blue component of this color (0-255).

**Returns:** The blue value

#### getAlpha

```java
public int getAlpha()
```

Gets the alpha component of this color (0-255).

**Returns:** The alpha value

#### a

```java
public float a()
```

Calculates the alpha component of this color as a normalized float value in the range [0.0, 1.0].

**Returns:** The normalized alpha value

#### r

```java
public float r()
```

Calculates the red component of this color as a normalized float value
in the range [0.0, 1.0].

**Returns:** The normalized red value

#### g

```java
public float g()
```

Calculates the green component of this color as a normalized float value
in the range [0.0, 1.0].

**Returns:** The normalized green value

#### b

```java
public float b()
```

Calculates the blue component of this color as a normalized float value
in the range [0.0, 1.0].

**Returns:** The normalized blue value

#### toNanoVGColor

```java
public NVGColor toNanoVGColor(NVGColor color)
```

Converts the current color to a NanoVG color representation.

- **`color`** — the NVGColor instance to populate with color values

**Returns:** the updated NVGColor instance with the current color values

#### toNanoVGColor

```java
public NVGColor toNanoVGColor()
```

Converts the current color instance to an NVGColor object with corresponding
red, green, blue, and alpha channel values.

**Returns:** A new NVGColor instance with the color's RGBA channels mapped
from this object's properties.

</details>

<a id="type-drawable"></a>

### Drawable

[Source](../../src/main/java/valthorne/graphics/Drawable.java#L19)

Defines an interface for objects that can be drawn to a specific position
and size on a rendering surface. Implementations of this interface
should handle the logic for rendering visual elements such as textures,
shapes, or other drawable components.

The `draw` method provides the necessary parameters to define
the position and dimensions of the drawable object. Implementing classes
are expected to use these parameters to render their content at the
specified location with the given width and height.

<details>
<summary>Drawable operation reference (7 declarations)</summary>

#### draw

```java
void draw(TextureBatch batch, float x, float y, float width, float height, float regionX, float regionY, float regionWidth, float regionHeight, float originX, float originY, float rotation, Color tint)
```

Renders a drawable object onto the given `TextureBatch` at the specified position,
size, region, origin, rotation, and with an optional color tint.

- **`batch`** — the `TextureBatch` to which the drawable object is rendered
- **`x`** — the x-coordinate on the rendering surface where the object should be drawn
- **`y`** — the y-coordinate on the rendering surface where the object should be drawn
- **`width`** — the width to render the object
- **`height`** — the height to render the object
- **`regionX`** — the x-coordinate of the texture region within the texture
- **`regionY`** — the y-coordinate of the texture region within the texture
- **`regionWidth`** — the width of the texture region
- **`regionHeight`** — the height of the texture region
- **`originX`** — the x-coordinate of the origin point for rotation, scaled relative to the object's size
- **`originY`** — the y-coordinate of the origin point for rotation, scaled relative to the object's size
- **`rotation`** — the rotation angle in degrees to apply to the object, relative to the origin point
- **`tint`** — the color tint to apply to the object; if `null`, no tint will be applied

#### draw

```java
default void draw(TextureBatch batch, float x, float y, float width, float height)
```

Renders a drawable object onto the specified `TextureBatch` at the given position
and size using the default texture region, origin, rotation, and without a color tint.

- **`batch`** — the `TextureBatch` used to render the drawable object
- **`x`** — the x-coordinate on the rendering surface where the object should be drawn
- **`y`** — the y-coordinate on the rendering surface where the object should be drawn
- **`width`** — the width to render the object
- **`height`** — the height to render the object

#### draw

```java
default void draw(TextureBatch batch, float x, float y, float width, float height, Color tint)
```

Renders a drawable object onto the given `TextureBatch` at a specified position, size,
and with an optional color tint. The texture region and other parameters are derived
automatically.

- **`batch`** — the `TextureBatch` used to render the drawable object
- **`x`** — the x-coordinate on the rendering surface where the object should be drawn
- **`y`** — the y-coordinate on the rendering surface where the object should be drawn
- **`width`** — the width to render the object
- **`height`** — the height to render the object
- **`tint`** — the color tint to apply to the object; if `null`, no tint will be applied

#### draw

```java
default void draw(TextureBatch batch, float x, float y, float width, float height, float originX, float originY, float rotation, Color tint)
```

Renders a drawable object onto the given `TextureBatch` at the specified position, size,
origin, rotation, and with an optional color tint. The texture region is derived automatically.

- **`batch`** — the `TextureBatch` used to render the drawable object
- **`x`** — the x-coordinate on the rendering surface where the object should be drawn
- **`y`** — the y-coordinate on the rendering surface where the object should be drawn
- **`width`** — the width to render the object
- **`height`** — the height to render the object
- **`originX`** — the x-coordinate of the origin point for rotation, scaled relative to the object's size
- **`originY`** — the y-coordinate of the origin point for rotation, scaled relative to the object's size
- **`rotation`** — the rotation angle in degrees to apply to the object, relative to the origin point
- **`tint`** — the color tint to apply to the object; if `null`, no tint will be applied

#### draw

```java
default void draw(TextureBatch batch, float x, float y, float width, float height, float regionX, float regionY, float regionWidth, float regionHeight)
```

Renders a drawable object onto the specified `TextureBatch` at the given position,
size, and texture region. Other parameters such as origin, rotation, and tint are assigned
default values.

- **`batch`** — the `TextureBatch` used to render the drawable object
- **`x`** — the x-coordinate on the rendering surface where the object should be drawn
- **`y`** — the y-coordinate on the rendering surface where the object should be drawn
- **`width`** — the width to render the object
- **`height`** — the height to render the object
- **`regionX`** — the x-coordinate of the texture region within the texture
- **`regionY`** — the y-coordinate of the texture region within the texture
- **`regionWidth`** — the width of the texture region
- **`regionHeight`** — the height of the texture region

#### getWidth

```java
float getWidth()
```

Retrieves the width of the drawable object.

**Returns:** the width of the object as a floating-point value.

#### getHeight

```java
float getHeight()
```

Retrieves the height of the drawable object.

**Returns:** the height of the object as a floating-point value.

</details>

<a id="type-drawfunction"></a>

### DrawFunction

[Source](../../src/main/java/valthorne/graphics/DrawFunction.java#L15)

Functional interface representing a drawing functionality.
This interface defines a single abstract method, `draw`, which can
be implemented to perform custom drawing logic.

Being a functional interface, it can be used as a target for lambda expressions
or method references.
The caller supplies the rendering context through its surrounding state; this
callback does not establish a batch, projection, or graphics context itself.

<details>
<summary>DrawFunction operation reference (1 declarations)</summary>

#### draw

```java
void draw()
```

Executes the drawing operation.
Implementations use the caller's current graphics state and are responsible
for any state-restoration obligations of that rendering integration. Exceptions
propagate to the invoking code; the interface performs no recovery or scheduling.

</details>

<a id="type-immediatetexturerenderer"></a>

### ImmediateTextureRenderer

[Source](../../src/main/java/valthorne/graphics/ImmediateTextureRenderer.java#L28)

Shared immediate quad renderer used by standalone textured draw helpers.

The renderer accepts quad-ordered position and UV buffers, expands them to triangles,
uploads them to a small dynamic VBO, and renders through either the currently bound
textured-quad shader or an internal fallback shader.

<details>
<summary>ImmediateTextureRenderer operation reference (2 declarations)</summary>

#### dispose

```java
public static void dispose()
```

Deletes the shared fallback program, vertex array, and buffer, then clears
CPU staging state. Repeated successful disposal is harmless. The next draw
lazily allocates a new set; call with the owning GL context current.

#### drawQuads

```java
public static void drawQuads(int textureID, FloatBuffer positions, FloatBuffer uvs, int quadCount, Color color)
```

Expands each four-corner quad to triangles (0,1,2) and (2,3,0), uploads copied
positions/UVs and a uniform vertex tint, then draws with the current program
or a fallback textured-quad shader when no program is bound.

Buffers are read by absolute indices starting at zero; their current positions
are ignored and preserved. Each must contain at least eight floats per quad.
The method sets standard sampler/projection uniforms when present, binds the
texture on unit zero, and leaves array bindings at zero. It does not generally
restore GL state or configure blending/depth policy.

- **`textureID`** — borrowed 2D texture name
- **`positions`** — four XY corners per quad, starting at index zero
- **`uvs`** — corresponding UV pairs
- **`quadCount`** — number of quads; nonpositive values return immediately
- **`color`** — tint copied to each vertex, or null for white

**Throws `NullPointerException`:** if a required buffer is null for positive quadCount

**Throws `IndexOutOfBoundsException`:** if an input buffer has insufficient readable elements

</details>

<a id="type-sprite"></a>

### Sprite

[Source](../../src/main/java/valthorne/graphics/Sprite.java#L89)

`Sprite` represents a textured 2D drawable built from a `TextureRegion`.
It stores transform state such as position, size, scale, rotation, rotation origin,
flip flags, and tint color, while also maintaining cached vertex and UV buffers that
can be used for direct OpenGL drawing or by higher-level rendering systems.

This class is designed as a lightweight textured quad abstraction. A sprite does not
own complex rendering state. Instead, it focuses on describing how a rectangular
region of a texture should be drawn in world space. It can be created from a file
path, raw image bytes, `TextureData`, a `Texture`, or a
`TextureRegion`.

Internally, the sprite keeps two important cached buffers:

- a vertex buffer containing the world-space positions of the four corners

- a UV buffer containing the texture coordinates for the same four corners

These buffers are updated whenever a relevant property changes:

- region changes update the UV buffer

- flip changes update the UV buffer

- size or scale changes update the local vertices and world vertices

- position or rotation changes update the world vertices

- rotation origin changes update both local and world vertices

The sprite supports direct rendering through the engine's explicit textured-quad
renderer. It can also be consumed by systems like
`valthorne.graphics.texture.TextureBatch` which read sprite state and render
it in a batched way.

##### Example Usage

```java
Sprite sprite = new Sprite("assets/player.png");
sprite.setPosition(100f, 80f);
sprite.setSize(64f, 64f);
sprite.setScale(1.25f, 1.25f);
sprite.setRotationOriginCenter();
sprite.setRotation(15f);
sprite.setFlip(false, true);
sprite.setColor(new Color(1f, 1f, 1f, 0.9f));

sprite.draw();

Texture texture = sprite.getTexture();
TextureRegion region = sprite.getRegion();
FloatBuffer vertices = sprite.getVertexBuffer();
FloatBuffer uvs = sprite.getUVBuffer();

sprite.reset();
sprite.dispose();
```

This example demonstrates the full workflow of the class: construction, transform
updates, flipping, tinting, drawing, buffer access, resetting, and disposal.

<details>
<summary>Sprite operation reference (66 declarations)</summary>

#### bounds

```java
protected final Rectangle bounds
```

World position and unscaled size of the sprite

#### region

```java
protected TextureRegion region
```

Texture region currently used by this sprite

#### vertexBuffer

```java
protected  FloatBuffer vertexBuffer
```

Cached world-space vertex positions for the four sprite corners

#### uvBuffer

```java
protected  FloatBuffer uvBuffer
```

Cached UV coordinates for the four sprite corners

#### localVertices

```java
protected  float[] localVertices
```

Cached local-space vertices before world transform is applied

#### origin

```java
protected  Vector2f origin
```

Rotation origin used for local-to-world transformation

#### rotation

```java
protected float rotation
```

Current rotation in degrees

#### sinRot

```java
protected float sinRot
```

Cached sine of the current rotation

#### cosRot

```java
protected  float cosRot
```

Cached cosine of the current rotation

#### flippedX

```java
protected boolean flippedX
```

Whether the sprite UVs are flipped horizontally

#### flippedY

```java
protected boolean flippedY
```

Whether the sprite UVs are flipped vertically

#### scaleX

```java
protected  float scaleX
```

Horizontal scale factor

#### scaleY

```java
protected  float scaleY
```

Vertical scale factor

#### color

```java
protected  Color color
```

Tint color used when drawing the sprite

#### ownsTexture

```java
protected boolean ownsTexture
```

Whether this sprite should dispose its backing texture when released

#### disposed

```java
protected boolean disposed
```

Whether this sprite has already released its owned resources

#### Constructor

```java
public Sprite(String path)
```

Creates a sprite by loading a texture from a file path.

This constructor creates a `Texture` from the given path and then
delegates to `Sprite(Texture)`.

- **`path`** — the file path of the texture to load

#### Constructor

```java
public Sprite(byte[] data)
```

Creates a sprite by loading a texture from encoded image bytes.

This constructor creates a `Texture` from the provided bytes and then
delegates to `Sprite(Texture)`.

- **`data`** — the encoded image bytes to load

#### Constructor

```java
public Sprite(TextureData data)
```

Creates a sprite from prepared `TextureData`.

This constructor creates a `Texture` from the provided texture data and
then delegates to `Sprite(Texture)`.

- **`data`** — the prepared texture data

#### Constructor

```java
public Sprite(Texture texture)
```

Creates a sprite that uses the full area of the given `Texture`.

This constructor creates a `TextureRegion` spanning the entire texture and
then delegates to `Sprite(TextureRegion)`.

- **`texture`** — the texture to use

#### Constructor

```java
public Sprite(TextureRegion region)
```

Creates a sprite from an existing `TextureRegion`.

The sprite initially uses the region's width and height as its bounds size,
starts at position `(0, 0)`, uses a zero rotation origin, has no flipping,
uses white tinting, and immediately builds its local vertices, UV buffer, and
world-space vertex buffer.

- **`region`** — the texture region used by this sprite

**Throws `NullPointerException`:** if `region` is `null`

#### getTexture

```java
public Texture getTexture()
```

Returns the texture currently used by this sprite.

**Returns:** the backing texture

#### getRegion

```java
public TextureRegion getRegion()
```

Returns the texture region currently used by this sprite.

**Returns:** the current texture region

#### setRegion

```java
public void setRegion(TextureRegion region)
```

Replaces the sprite's current texture region.

This updates the region reference and rebuilds the UV buffer so subsequent
rendering uses the new source rectangle.

- **`region`** — the new texture region

**Throws `NullPointerException`:** if `region` is `null`

#### setRegion

```java
public void setRegion(float regionX, float regionY, float regionWidth, float regionHeight)
```

Updates the current texture region's pixel bounds in place.

After changing the region, the UV buffer is rebuilt so rendering uses the
updated coordinates.

- **`regionX`** — the new region X position
- **`regionY`** — the new region Y position
- **`regionWidth`** — the new region width
- **`regionHeight`** — the new region height

#### getRegionX

```java
public float getRegionX()
```

Returns the X position of the current texture region in pixels.

**Returns:** the region X position

#### getRegionY

```java
public float getRegionY()
```

Returns the Y position of the current texture region in pixels.

**Returns:** the region Y position

#### getRegionWidth

```java
public float getRegionWidth()
```

Returns the width of the current texture region in pixels.

**Returns:** the region width

#### getRegionHeight

```java
public float getRegionHeight()
```

Returns the height of the current texture region in pixels.

**Returns:** the region height

#### setScale

```java
public void setScale(float sx, float sy)
```

Sets the horizontal and vertical scale of the sprite.

The current rotation origin is adjusted proportionally so that an existing origin
remains visually aligned when the sprite is rescaled. After that, local vertices
and world vertices are rebuilt.

- **`sx`** — the new horizontal scale
- **`sy`** — the new vertical scale

#### getScaleX

```java
public float getScaleX()
```

Returns the current horizontal scale.

**Returns:** the horizontal scale

#### getScaleY

```java
public float getScaleY()
```

Returns the current vertical scale.

**Returns:** the vertical scale

#### setFlipX

```java
public void setFlipX(boolean flip)
```

Sets whether the sprite is horizontally flipped.

Flipping only affects the UV buffer, not the actual geometry.

- **`flip`** — `true` to flip horizontally

#### setFlipY

```java
public void setFlipY(boolean flip)
```

Sets whether the sprite is vertically flipped.

Flipping only affects the UV buffer, not the actual geometry.

- **`flip`** — `true` to flip vertically

#### setFlip

```java
public void setFlip(boolean flipX, boolean flipY)
```

Sets both horizontal and vertical flip state at once.

- **`flipX`** — `true` to flip horizontally
- **`flipY`** — `true` to flip vertically

#### isFlippedX

```java
public boolean isFlippedX()
```

Returns whether the sprite is currently flipped horizontally.

**Returns:** `true` if flipped horizontally

#### isFlippedY

```java
public boolean isFlippedY()
```

Returns whether the sprite is currently flipped vertically.

**Returns:** `true` if flipped vertically

#### setPosition

```java
public void setPosition(float x, float y)
```

Sets the world position of the sprite.

If the position is unchanged, the method returns immediately. Otherwise the
bounds are updated and the world-space vertex buffer is rebuilt.

- **`x`** — the new X position
- **`y`** — the new Y position

#### getX

```java
public float getX()
```

Returns the sprite's world X position.

**Returns:** the X position

#### getY

```java
public float getY()
```

Returns the sprite's world Y position.

**Returns:** the Y position

#### draw

```java
    public void draw(TextureBatch batch, float x, float y, float width, float height)
```

Draws the full region at explicit bounds using the sprite's scale, origin,
rotation, flips, and tint. Stored position and dimensions remain unchanged.
A missing region is skipped.

- **`batch`** — active destination texture batch
- **`x`** — horizontal destination position
- **`y`** — vertical destination position
- **`width`** — destination width in batch units
- **`height`** — destination height in batch units

#### draw

```java
    public void draw(TextureBatch batch, float x, float y, float width, float height, Color tint)
```

Draws the full region at explicit bounds with an additional tint multiplier.
The sprite's scale, origin, rotation, and flips still apply without changing
stored bounds. A missing region is skipped.

- **`batch`** — active destination texture batch
- **`x`** — horizontal destination position
- **`y`** — vertical destination position
- **`width`** — destination width in batch units
- **`height`** — destination height in batch units
- **`tint`** — color multiplied by the sprite tint, or null to use its tint alone

#### draw

```java
    public void draw(TextureBatch batch, float x, float y, float width, float height, float originX, float originY, float rotation, Color tint)
```

Draws the full region using explicit bounds and additional origin and rotation.
Origin offsets and rotation are added to the sprite's stored values; scale,
flips, and tint still apply. Stored sprite geometry is not changed.

- **`batch`** — active destination texture batch
- **`x`** — horizontal destination position
- **`y`** — vertical destination position
- **`width`** — destination width in batch units
- **`height`** — destination height in batch units
- **`originX`** — horizontal rotation-origin offset in destination units
- **`originY`** — vertical rotation-origin offset in destination units
- **`rotation`** — clockwise rotation in degrees
- **`tint`** — optional tint multiplier, or null for the drawable's default tint

#### draw

```java
    public void draw(TextureBatch batch, float x, float y, float width, float height, float regionX, float regionY, float regionWidth, float regionHeight)
```

Draws a pixel subsection relative to the sprite's region at explicit bounds.
Stored scale, rotation, origin, flips, and tint remain active, and source
coordinates are not clamped to the region.

- **`batch`** — active destination texture batch
- **`x`** — horizontal destination position
- **`y`** — vertical destination position
- **`width`** — destination width in batch units
- **`height`** — destination height in batch units
- **`regionX`** — horizontal source offset in pixels
- **`regionY`** — vertical source offset in pixels
- **`regionWidth`** — source width in pixels
- **`regionHeight`** — source height in pixels

#### draw

```java
    public void draw(TextureBatch batch, float x, float y, float width, float height, float regionX, float regionY, float regionWidth, float regionHeight, float originX, float originY, float rotation, Color tint)
```

Submits a source subsection relative to the sprite region, honoring its flips.
Destination dimensions are multiplied by stored scale; supplied origins and
rotation are added to stored values. Tint multiplies the sprite color. Missing
regions/textures and zero-size backing textures are skipped. This does not
change stored bounds or clamp source coordinates to the region.

- **`batch`** — active destination texture batch
- **`x`** — horizontal destination position
- **`y`** — vertical destination position
- **`width`** — destination width in batch units
- **`height`** — destination height in batch units
- **`regionX`** — horizontal source offset in pixels
- **`regionY`** — vertical source offset in pixels
- **`regionWidth`** — source width in pixels
- **`regionHeight`** — source height in pixels
- **`originX`** — horizontal rotation-origin offset in destination units
- **`originY`** — vertical rotation-origin offset in destination units
- **`rotation`** — clockwise rotation in degrees
- **`tint`** — optional tint multiplier, or null for the drawable's default tint

#### getWidth

```java
public float getWidth()
```

Returns the sprite's unscaled width stored in its bounds.

**Returns:** the width

#### setWidth

```java
public void setWidth(float width)
```

Sets the sprite width.

Changing the width requires the local quad geometry and world-space vertices to
be rebuilt.

- **`width`** — the new width

#### getHeight

```java
public float getHeight()
```

Returns the sprite's unscaled height stored in its bounds.

**Returns:** the height

#### setHeight

```java
public void setHeight(float height)
```

Sets the sprite height.

Changing the height requires the local quad geometry and world-space vertices to
be rebuilt.

- **`height`** — the new height

#### setSize

```java
public void setSize(float width, float height)
```

Sets the sprite size in one call.

If either dimension changes, local geometry and world-space vertices are rebuilt.

- **`width`** — the new width
- **`height`** — the new height

#### getRotation

```java
public float getRotation()
```

Returns the current rotation in degrees.

**Returns:** the rotation angle in degrees

#### setRotation

```java
public void setRotation(float degrees)
```

Sets the sprite rotation in degrees.

The angle is converted into cached sine and cosine values immediately so
repeated vertex updates do not need to recompute the trigonometric functions.
The world-space vertex buffer is rebuilt after the rotation changes.

- **`degrees`** — the new rotation angle in degrees

#### setRotationOrigin

```java
public void setRotationOrigin(float ox, float oy)
```

Sets the rotation origin used when transforming the sprite.

Since the local quad is defined relative to the origin, changing the origin
requires both the local vertices and world-space vertices to be rebuilt.

- **`ox`** — the new origin X
- **`oy`** — the new origin Y

#### setRotationOriginCenter

```java
public void setRotationOriginCenter()
```

Sets the rotation origin to the center of the scaled sprite.

The center is computed using the current bounds size multiplied by the current
scale values.

#### getRotationOrigin

```java
public Vector2f getRotationOrigin()
```

Returns the current rotation origin vector.

**Returns:** the rotation origin

#### getColor

```java
public Color getColor()
```

Returns the sprite tint color.

**Returns:** the tint color

#### setColor

```java
public void setColor(Color color)
```

Sets the tint color used when drawing the sprite.

The provided color reference is stored directly.

- **`color`** — the new tint color

**Throws `NullPointerException`:** if `color` is `null`

#### getBounds

```java
public Rectangle getBounds()
```

Returns the sprite bounds rectangle.

**Returns:** the bounds rectangle

#### getVertexBuffer

```java
public FloatBuffer getVertexBuffer()
```

Returns the cached world-space vertex buffer.

**Returns:** the vertex buffer

#### getUVBuffer

```java
public FloatBuffer getUVBuffer()
```

Returns the cached UV buffer.

**Returns:** the UV buffer

#### updateUVBuffer

```java
protected void updateUVBuffer()
```

Rebuilds the UV buffer from the current region and flip state.

The region UVs are read from the current `TextureRegion`. If either flip
flag is active, the corresponding UV pair is swapped before being written into
the buffer in quad order.

#### updateLocalVertices

```java
protected void updateLocalVertices()
```

Rebuilds the local vertex positions for the sprite quad.

The local quad is defined relative to the current rotation origin and scaled
bounds size. These local coordinates are later transformed into world-space by
`updateVertexBuffer()`.

#### updateVertexBuffer

```java
protected void updateVertexBuffer()
```

Rebuilds the world-space vertex buffer from the local vertex data.

Each local point is rotated using the cached sine and cosine values, then
translated by the sprite position plus its origin. The resulting four points
are written into the vertex buffer in quad order.

#### draw

```java
public void draw()
```

Draws the sprite immediately using the shared textured-quad renderer.

The current tint color is applied, the backing texture is bound, and the cached
quad data is rendered through an explicit shader-driven path.

#### dispose

```java
public void dispose()
```

Disposes internal references held by this sprite.

This method does not dispose the underlying texture. It only clears this sprite's
references and buffers. After this call, the sprite should no longer be used.

#### reset

```java
    public void reset()
```

Resets this sprite to its default pooled state.

The position is reset to zero, the size is restored to the region size, rotation
is cleared, cached sine and cosine are reset, flip flags are disabled, scale is
reset to one, the origin is reset to zero, the tint color is set to
`Color#WHITE`, and all cached geometry is rebuilt.

</details>

<a id="type-framebuffer"></a>

### FrameBuffer

[Source](../../src/main/java/valthorne/graphics/texture/FrameBuffer.java#L55)

Simple 2D framebuffer (render target) you can draw to, then draw its color texture like a sprite.

##### Example

```java
// Create a framebuffer matching your initial window size.
FrameBuffer fbo = new FrameBuffer(1280, 720, true);

// 1) Render scene into the FBO.
fbo.begin();
glClearColor(0f, 0f, 0f, 1f);
glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
// draw your world/batches here...
fbo.end();

// 2) Present the FBO's color texture to the currently bound framebuffer (usually the screen).
glClear(GL_COLOR_BUFFER_BIT);
fbo.draw(0f, 0f, 1280f, 720f);

// If your output is upside-down, use:
// fbo.drawFlippedY(0f, 0f, 1280f, 720f);

// Cleanup.
fbo.dispose();
```

##### What this class does

- Creates an OpenGL framebuffer object (FBO) with a color texture attachment.

- Optionally creates a depth renderbuffer attachment.

- Captures and restores the previously bound framebuffer and viewport around `begin()` / `end()`.

- Provides `draw(float, float, float, float)` and `drawFlippedY(float, float, float, float)` through the shared textured-quad renderer.

##### Important notes

- `draw(float, float, float, float)` and `drawFlippedY(float, float, float, float)` use the engine's explicit shader-based quad path.

- `resize(int, int)` recreates the underlying OpenGL objects. Call it when your window/viewport size changes.

<details>
<summary>FrameBuffer operation reference (12 declarations)</summary>

#### Constructor

```java
public FrameBuffer(int width, int height, boolean hasDepth)
```

Creates a framebuffer with a color texture attachment and optional depth attachment.

This allocates the OpenGL objects immediately and prepares internal quad buffers used by draw methods.

- **`width`** — framebuffer width in pixels (must be > 0)
- **`height`** — framebuffer height in pixels (must be > 0)
- **`hasDepth`** — true to attach a depth renderbuffer, false for color-only

**Throws `IllegalArgumentException`:** if width or height are &lt;= 0

**Throws `IllegalStateException`:** if OpenGL reports the framebuffer is incomplete

#### begin

```java
public void begin()
```

Begins rendering into this framebuffer.

This method:

- Captures the currently bound framebuffer id so it can be restored later.

- Captures the current viewport so it can be restored later.

- Binds this framebuffer and sets the viewport to `0,0,width,height`.

Typical usage is `begin(); ...draw scene...; end();`.

#### end

```java
public void end()
```

Ends rendering into this framebuffer and restores the previous framebuffer and viewport.

This method assumes `begin()` was called earlier in the same render flow.
It restores exactly what was captured in `begin()`.

#### render

```java
public void render(Runnable drawCalls)
```

Convenience wrapper that runs render logic inside `begin()` / `end()` safely.

This guarantees `end()` is called even if `drawCalls` throws.

- **`drawCalls`** — render logic to execute while this framebuffer is bound

**Throws `NullPointerException`:** if `drawCalls` is null

#### resize

```java
public void resize(int newWidth, int newHeight)
```

Resizes the framebuffer by recreating its underlying OpenGL attachments.

Behavior:

- If the size is unchanged, this is a no-op.

- If the size changes, this deletes the old OpenGL objects and allocates new ones.

- This does not automatically update any projection matrices in your engine; it only changes the render target size.

- **`newWidth`** — new framebuffer width in pixels (must be > 0)
- **`newHeight`** — new framebuffer height in pixels (must be > 0)

**Throws `IllegalArgumentException`:** if newWidth or newHeight are &lt;= 0

#### draw

```java
public void draw(float x, float y, float w, float h)
```

Draws the framebuffer's color texture to the currently bound framebuffer (usually the screen).

UVs are set so the texture is sampled normally (0,0) bottom-left and (1,1) top-right.

Important: This does not bind shaders or change matrices. It assumes your caller has already configured
projection/modelview state the way your engine expects.

- **`x`** — bottom-left x in world/screen space
- **`y`** — bottom-left y in world/screen space
- **`w`** — width in world/screen space
- **`h`** — height in world/screen space

#### drawFlippedY

```java
public void drawFlippedY(float x, float y, float w, float h)
```

Draws the framebuffer's color texture flipped vertically.

This is useful when your FBO output appears upside down due to coordinate conventions.
The flip is done by swapping V coordinates in the quad UVs (no extra rendering passes).

- **`x`** — bottom-left x in world/screen space
- **`y`** — bottom-left y in world/screen space
- **`w`** — width in world/screen space
- **`h`** — height in world/screen space

#### dispose

```java
public void dispose()
```

Disposes all OpenGL resources owned by this framebuffer.

Safe to call multiple times. After disposal, the framebuffer is no longer usable
unless you recreate it by constructing a new `FrameBuffer`.

#### getWidth

```java
public int getWidth()
```

Returns the current framebuffer width in pixels.

**Returns:** width in pixels

#### getHeight

```java
public int getHeight()
```

Returns the current framebuffer height in pixels.

**Returns:** height in pixels

#### getFrameBufferId

```java
public int getFrameBufferId()
```

Returns the OpenGL framebuffer object id.

This is useful when you need to bind the framebuffer yourself or integrate with other rendering code.

**Returns:** framebuffer object id

#### getColorTextureId

```java
public int getColorTextureId()
```

Returns the OpenGL texture id for the color attachment.

You can bind this texture and sample it in shaders, or draw it using fixed-function like a normal texture.

**Returns:** color texture id

</details>

<a id="type-ninepatchdrawable"></a>

### NinePatchDrawable

[Source](../../src/main/java/valthorne/graphics/texture/NinePatchDrawable.java#L16)

`NinePatchDrawable` is a small `Drawable` adapter that wraps a
`NinePatchTexture` so it can be used anywhere a generic drawable object
is expected.

<details>
<summary>NinePatchDrawable operation reference (7 declarations)</summary>

#### Constructor

```java
public NinePatchDrawable(String string, int left, int top, int right, int bottom)
```

Creates a new `NinePatchDrawable` by loading a `NinePatchTexture`
from a path string.

The supplied border sizes are forwarded into the wrapped
`NinePatchTexture` constructor. Note that the constructor parameter order
accepted here is `left, top, right, bottom`, while the wrapped texture
constructor is called using `left, right, top, bottom`.

- **`string`** — the texture path
- **`left`** — the left border size
- **`top`** — the top border size
- **`right`** — the right border size
- **`bottom`** — the bottom border size

#### Constructor

```java
public NinePatchDrawable(TextureData data, int left, int top, int right, int bottom)
```

Creates a new `NinePatchDrawable` from existing `TextureData`.

The supplied border sizes are forwarded into the wrapped
`NinePatchTexture` constructor. Note that the constructor parameter order
accepted here is `left, top, right, bottom`, while the wrapped texture
constructor is called using `left, right, top, bottom`.

- **`data`** — the source texture data
- **`left`** — the left border size
- **`top`** — the top border size
- **`right`** — the right border size
- **`bottom`** — the bottom border size

#### Constructor

```java
public NinePatchDrawable(Texture texture, int left, int top, int right, int bottom)
```

Creates a new `NinePatchDrawable` from an existing `Texture`.

The supplied border sizes are forwarded into the wrapped
`NinePatchTexture` constructor. Note that the constructor parameter order
accepted here is `left, top, right, bottom`, while the wrapped texture
constructor is called using `left, right, top, bottom`.

- **`texture`** — the source texture
- **`left`** — the left border size
- **`top`** — the top border size
- **`right`** — the right border size
- **`bottom`** — the bottom border size

#### draw

```java
@Override
    public void draw(TextureBatch batch, float x, float y, float width, float height, float regionX, float regionY, float regionWidth, float regionHeight, float originX, float originY, float rotation, Color tint)
```

Draws the wrapped `NinePatchTexture` through the provided
`TextureBatch` using the supplied destination rectangle.

This delegates directly to `TextureBatch#draw(NinePatchTexture, float, float, float, float)`.

- **`batch`** — the batch used for drawing
- **`x`** — the destination X position
- **`y`** — the destination Y position
- **`width`** — the destination width
- **`height`** — the destination height

#### getWidth

```java
@Override
    public float getWidth()
```

Returns the current width of the wrapped `NinePatchTexture`.

**Returns:** the wrapped texture width

#### getHeight

```java
@Override
    public float getHeight()
```

Returns the current height of the wrapped `NinePatchTexture`.

**Returns:** the wrapped texture height

#### texture

```java
@Override
    public NinePatchTexture texture()
```

Returns the wrapped `NinePatchTexture`.

This method overrides the record-generated accessor explicitly so it can remain
part of the `Drawable` contract with clear documentation.

**Returns:** the wrapped nine-patch texture

</details>

<a id="type-ninepatchtexture"></a>

### NinePatchTexture

[Source](../../src/main/java/valthorne/graphics/texture/NinePatchTexture.java#L91)

`NinePatchTexture` represents a drawable nine-patch texture built around a
backing `Texture` and a `TextureRegion`. It divides a texture into a
3x3 grid made of fixed-size corners, stretchable edges, and a stretchable center.
This allows UI panels, windows, buttons, and other scalable elements to grow or
shrink without distorting their borders.

Internally, this class precomputes a local nine-patch mesh and a matching UV layout.
When geometry-related properties change, such as size, scale, region, flip state,
or slice thickness, the local mesh is marked dirty and rebuilt on demand. When only
world-space properties change, such as position or rotation, the world buffer is
updated without rebuilding the local mesh. This split keeps rendering efficient while
still supporting dynamic transforms.

The nine-patch is composed of nine quads:

- bottom-left, bottom-center, bottom-right

- middle-left, middle-center, middle-right

- top-left, top-center, top-right

Each quad stores local vertex positions and UV coordinates. These are later converted
into world-space positions using the current position, origin, scale, and rotation.
Because of that, the class supports:

- custom source regions

- custom destination position and size

- scaling

- rotation around a configurable origin

- horizontal and vertical flipping

- runtime color tinting

- pool reset behavior through `Poolable`

This class performs immediate textured-quad rendering through the engine's shared
shader-driven quad renderer. That means it is useful for standalone drawing and is
also compatible with higher-level systems that want to inspect its nine-patch
properties and feed them into a batch renderer such as `TextureBatch`.

##### Example Usage

```java
NinePatchTexture panel = new NinePatchTexture("panel.png", 8, 8, 8, 8);
panel.setPosition(100, 80);
panel.setSize(320, 180);
panel.setRotationOriginCenter();
panel.setRotation(5f);
panel.setColor(new Color(1f, 1f, 1f, 0.95f));

panel.draw();

panel.setFlip(true, false);
panel.setScale(1.25f, 1.25f);
panel.draw();

panel.reset();
panel.dispose();
```

The example above demonstrates the full lifecycle of the class: construction,
resizing, transforming, tinting, drawing, resetting, and disposal.

<details>
<summary>NinePatchTexture operation reference (47 declarations)</summary>

#### Constructor

```java
public NinePatchTexture(String path, int left, int right, int top, int bottom)
```

Creates a new `NinePatchTexture` from a texture path.

This constructor creates a new `Texture` from the supplied path and then
delegates to the texture-based constructor.

- **`path`** — the texture path to load
- **`left`** — the left border size in pixels
- **`right`** — the right border size in pixels
- **`top`** — the top border size in pixels
- **`bottom`** — the bottom border size in pixels

#### Constructor

```java
public NinePatchTexture(TextureData data, int left, int right, int top, int bottom)
```

Creates a new `NinePatchTexture` from an existing `TextureData` instance.

This constructor creates a new `Texture` from the supplied texture data and
then delegates to the texture-based constructor.

- **`data`** — the texture data used to construct the backing texture
- **`left`** — the left border size in pixels
- **`right`** — the right border size in pixels
- **`top`** — the top border size in pixels
- **`bottom`** — the bottom border size in pixels

#### Constructor

```java
public NinePatchTexture(Texture texture, int left, int right, int top, int bottom)
```

Creates a new `NinePatchTexture` from an existing `Texture`.

The full texture is initially used as the source region. The object starts with
bounds matching the texture's native size, a zero origin, no flipping, no rotation,
unit scale, and a white tint. The mesh and world caches are marked dirty so the
buffers are generated lazily on first use.

- **`texture`** — the backing texture
- **`left`** — the left border size in pixels
- **`right`** — the right border size in pixels
- **`top`** — the top border size in pixels
- **`bottom`** — the bottom border size in pixels

**Throws `NullPointerException`:** if `texture` is `null`

#### getTexture

```java
public Texture getTexture()
```

Returns the backing texture used by this nine-patch.

**Returns:** the backing texture

#### getData

```java
public TextureData getData()
```

Returns the backing texture data.

**Returns:** the texture data, or whatever is returned by the backing texture

#### getRegion

```java
public TextureRegion getRegion()
```

Returns the source region used by this nine-patch.

**Returns:** the source region

#### setRegion

```java
public void setRegion(float regionX, float regionY, float regionWidth, float regionHeight)
```

Updates the active source region and marks all cached mesh data dirty.

Because the UV layout of each slice depends on the region, changing the region
requires the nine-patch mesh and UVs to be rebuilt.

- **`regionX`** — the source region X position in pixels
- **`regionY`** — the source region Y position in pixels
- **`regionWidth`** — the source region width in pixels
- **`regionHeight`** — the source region height in pixels

#### getRegionX

```java
public float getRegionX()
```

Returns the source region X position.

**Returns:** the source region X position

#### getRegionY

```java
public float getRegionY()
```

Returns the source region Y position.

**Returns:** the source region Y position

#### getRegionWidth

```java
public float getRegionWidth()
```

Returns the source region width.

**Returns:** the source region width

#### getRegionHeight

```java
public float getRegionHeight()
```

Returns the source region height.

**Returns:** the source region height

#### setPosition

```java
public void setPosition(float x, float y)
```

Sets the world position of the nine-patch.

If the new coordinates match the current position, the method returns early.
Otherwise, the bounds are updated and only the world buffer is marked dirty.

- **`x`** — the new X position
- **`y`** — the new Y position

#### getX

```java
public float getX()
```

Returns the current world X position.

**Returns:** the X position

#### getY

```java
public float getY()
```

Returns the current world Y position.

**Returns:** the Y position

#### getWidth

```java
public float getWidth()
```

Returns the current destination width.

**Returns:** the destination width

#### setWidth

```java
public void setWidth(float width)
```

Sets the destination width and marks the mesh dirty if the value changes.

- **`width`** — the new width

#### getHeight

```java
public float getHeight()
```

Returns the current destination height.

**Returns:** the destination height

#### setHeight

```java
public void setHeight(float height)
```

Sets the destination height and marks the mesh dirty if the value changes.

- **`height`** — the new height

#### setSize

```java
public void setSize(float width, float height)
```

Sets the destination size and marks the mesh dirty if either dimension changes.

- **`width`** — the new width
- **`height`** — the new height

#### setScale

```java
public void setScale(float sx, float sy)
```

Sets the horizontal and vertical scale of the nine-patch.

The rotation origin is adjusted proportionally so that an existing origin stays
visually aligned with the scaled geometry. After the update, the local mesh and
world buffers are marked dirty.

- **`sx`** — the new horizontal scale
- **`sy`** — the new vertical scale

#### getScaleX

```java
public float getScaleX()
```

Returns the current horizontal scale.

**Returns:** the horizontal scale

#### getScaleY

```java
public float getScaleY()
```

Returns the current vertical scale.

**Returns:** the vertical scale

#### getRotation

```java
public float getRotation()
```

Returns the current rotation in degrees.

**Returns:** the rotation angle in degrees

#### setRotation

```java
public void setRotation(float degrees)
```

Sets the rotation of the nine-patch in degrees.

If the value is unchanged, the method returns immediately. Otherwise only the
world-space buffers need to be recomputed.

- **`degrees`** — the new rotation angle in degrees

#### setRotationOrigin

```java
public void setRotationOrigin(float ox, float oy)
```

Sets the rotation origin used when transforming the local mesh into world space.

- **`ox`** — the origin X
- **`oy`** — the origin Y

#### setRotationOriginCenter

```java
public void setRotationOriginCenter()
```

Sets the rotation origin to the visual center of the scaled destination area.

Because the origin is stored in local scaled space, this uses the bounds size
multiplied by the current scale.

#### getRotationOrigin

```java
public Vector2f getRotationOrigin()
```

Returns the current rotation origin vector.

**Returns:** the rotation origin

#### setFlipX

```java
public void setFlipX(boolean flip)
```

Sets whether the nine-patch should be flipped horizontally.

- **`flip`** — `true` to flip horizontally

#### setFlipY

```java
public void setFlipY(boolean flip)
```

Sets whether the nine-patch should be flipped vertically.

- **`flip`** — `true` to flip vertically

#### setFlip

```java
public void setFlip(boolean flipX, boolean flipY)
```

Sets both horizontal and vertical flip state at once.

- **`flipX`** — `true` to flip horizontally
- **`flipY`** — `true` to flip vertically

#### isFlippedX

```java
public boolean isFlippedX()
```

Returns whether the nine-patch is currently flipped horizontally.

**Returns:** `true` if flipped horizontally

#### isFlippedY

```java
public boolean isFlippedY()
```

Returns whether the nine-patch is currently flipped vertically.

**Returns:** `true` if flipped vertically

#### getColor

```java
public Color getColor()
```

Returns the current tint color.

**Returns:** the tint color

#### setColor

```java
public void setColor(Color color)
```

Sets the tint color used during rendering.

The provided reference is stored directly, so future changes to that color
object will affect this nine-patch as well.

- **`color`** — the new tint color

**Throws `NullPointerException`:** if `color` is `null`

#### getBounds

```java
public Rectangle getBounds()
```

Returns the destination bounds rectangle.

**Returns:** the bounds rectangle

#### draw

```java
public void draw()
```

Draws the nine-patch immediately using the shared textured-quad renderer.

If the local mesh is dirty, it is rebuilt first. If the world-space buffer is
dirty, it is updated next. The backing texture, transformed vertices, and UVs are
then submitted through the engine's explicit quad renderer in a single call.

#### getLeft

```java
public int getLeft()
```

Returns the left border size in pixels.

**Returns:** the left border size

#### setLeft

```java
public void setLeft(int left)
```

Sets the left border size and marks the mesh dirty if the value changes.

- **`left`** — the new left border size

#### getRight

```java
public int getRight()
```

Returns the right border size in pixels.

**Returns:** the right border size

#### setRight

```java
public void setRight(int right)
```

Sets the right border size and marks the mesh dirty if the value changes.

- **`right`** — the new right border size

#### getTop

```java
public int getTop()
```

Returns the top border size in pixels.

**Returns:** the top border size

#### setTop

```java
public void setTop(int top)
```

Sets the top border size and marks the mesh dirty if the value changes.

- **`top`** — the new top border size

#### getBottom

```java
public int getBottom()
```

Returns the bottom border size in pixels.

**Returns:** the bottom border size

#### setBottom

```java
public void setBottom(int bottom)
```

Sets the bottom border size and marks the mesh dirty if the value changes.

- **`bottom`** — the new bottom border size

#### reset

```java
    public void reset()
```

Resets this nine-patch to its default pooled state.

The position is restored to zero, the bounds size is restored to the backing
texture size, the region is reset to cover the full texture, the origin is reset
to zero, scale is reset to one, rotation is reset to zero, flipping is disabled,
and the cached rotation state is invalidated. The tint is set to
`Color#WHITE`. After reset, all cached geometry is marked dirty.

#### dispose

```java
public void dispose()
```

Disposes resources and clears references owned by this nine-patch.

The backing texture is disposed, the region is cleared, the CPU-side buffers are
nulled, and mutable object references such as origin and color are cleared.
After this method is called, the instance should no longer be used.

#### setFilter

```java
public void setFilter(TextureFilter textureFilter)
```

Sets the filtering mode of the backing texture.

This delegates directly to the wrapped texture.

- **`textureFilter`** — the texture filter to apply

</details>

<a id="type-spriteculling"></a>

### SpriteCulling

[Source](../../src/main/java/valthorne/graphics/texture/SpriteCulling.java#L18)

Allocation-free conservative clip test for a sprite quad on the world XY plane
at Z zero. The test rotates its four corners around a local origin, translates
them into world space, and applies a column-major homogeneous transform. It
rejects a quad only when all four corners are outside one common OpenGL clip
plane, retaining potential intersections without perspective division.

A positive result means potentially visible, not an exact intersection or
guaranteed pixel coverage. A small tolerance proportional to clip-coordinate
magnitude retains near-boundary quads despite floating-point roundoff. The
helper owns no state; callers supply current matrix coefficients and already
computed sine/cosine values from the same rotation used for drawing.

<details>
<summary>SpriteCulling operation reference (1 declarations)</summary>

#### visible

```java
public static boolean visible(float[] m, float x, float y, float w, float h, float originX, float originY, float sin, float cos)
```

Tests the transformed rectangle against the six homogeneous clip limits
`-w <= x, y, z <= w`. For each corner, local coordinates are offset
by the supplied origin, rotated, and added to (x + originX, y + originY).
The matrix then transforms that world XY position with Z zero.

Outside-plane bits are intersected across corners. Once no common outside
plane remains, the method returns true immediately. Each corner uses a
tolerance of 1e-6 times one plus the sum of the absolute clip coordinates,
including homogeneous W. No input is modified or copied into an array.

Inputs are not validated. Supply at least sixteen matrix coefficients
and finite coordinates and trigonometric values. Signed extents are processed
as supplied, and degenerate quads are not separately rejected. Invalid numeric
values do not provide a meaningful visibility guarantee.

- **`m`** — the column-major world-to-clip matrix with at least sixteen entries
- **`x`** — the unrotated rectangle's world X position
- **`y`** — the unrotated rectangle's world Y position
- **`w`** — the rectangle's local horizontal extent
- **`h`** — the rectangle's local vertical extent
- **`originX`** — the rotation pivot's local X offset
- **`originY`** — the rotation pivot's local Y offset
- **`sin`** — the sine of the sprite rotation angle
- **`cos`** — the cosine of the same rotation angle

**Returns:** false if a common clip plane rejects every corner; true otherwise

**Throws `NullPointerException`:** if m is null

**Throws `ArrayIndexOutOfBoundsException`:** if m has fewer than sixteen entries

</details>

<a id="type-texture"></a>

### Texture

[Source](../../src/main/java/valthorne/graphics/texture/Texture.java#L84)

`Texture` represents a GPU-backed 2D OpenGL texture in Valthorne.
It acts as the primary wrapper around a texture object ID and its associated
`TextureData`. This class is responsible for creating the OpenGL texture,
uploading pixel data, configuring filtering behavior, exposing width and height,
and cleaning up the GPU resource when it is no longer needed.

A `Texture` can be constructed from a file path, raw encoded image bytes,
or an already prepared `TextureData` object. During construction, the
texture is immediately created on the GPU, bound, configured with default filter
parameters, assigned clamp-to-edge wrapping, and populated with RGBA pixel data.

This class is intentionally small and focused. It does not attempt to manage
atlases, regions, batching, or draw state directly. Instead, it serves as the
low-level image resource that other rendering types build on top of, such as:

- `TextureRegion`

- `Sprite`

- `NinePatchTexture`

- `TextureBatch`

The texture starts with `TextureFilter#NEAREST` filtering by default,
making it suitable for crisp pixel-art rendering. The filter can later be
changed with `setFilter(TextureFilter)`. If the chosen filter uses
mipmaps, mipmaps are generated automatically after the filter is applied.

Since this class implements `Poolable`, it can participate in pooling
systems. Its `reset()` implementation restores the filter back to
`TextureFilter#NEAREST`.

##### Example Usage

```java
Texture texture = new Texture("assets/player.png");
texture.setFilter(TextureFilter.LINEAR);

int width = texture.getWidth();
int height = texture.getHeight();

texture.bind();

Sprite sprite = texture.sprite();

TextureBatch batch = new TextureBatch(1000);
batch.begin();
batch.draw(texture, 100, 50, width, height);
batch.draw(sprite);
batch.end();

texture.reset();
texture.dispose();
```

This example shows the full typical lifecycle of the class: loading,
filtering, binding, converting into a `Sprite`, drawing, resetting,
and disposal.

<details>
<summary>Texture operation reference (20 declarations)</summary>

#### textureID

```java
protected final int textureID
```

OpenGL texture object ID created for this texture

#### ownsData

```java
protected final boolean ownsData
```

Whether this texture should dispose the decoded TextureData when the GPU texture is released

#### data

```java
protected TextureData data
```

CPU-side texture metadata and pixel buffer associated with this texture

#### filter

```java
protected  TextureFilter filter
```

Current filtering mode applied to the texture

#### Constructor

```java
public Texture(String path)
```

Creates a texture by loading image data from a file path.

This constructor delegates to `TextureData#load(String)` and then
forwards the resulting `TextureData` to
`Texture(TextureData)`.

- **`path`** — the image path to load

#### Constructor

```java
public Texture(byte[] data)
```

Creates a texture by loading image data from encoded image bytes.

This constructor delegates to `TextureData#load(byte[])` and then
forwards the resulting `TextureData` to
`Texture(TextureData)`.

- **`data`** — the encoded image bytes to load

#### Constructor

```java
public Texture(TextureData data)
```

Creates a texture from an existing `TextureData` instance.

This constructor creates a new OpenGL texture object, binds it,
applies the default filtering mode, applies clamp-to-edge wrapping,
and uploads the RGBA pixel buffer to the GPU using
`glTexImage2D`.

The default filter at construction time is
`TextureFilter#NEAREST`.

- **`data`** — the texture data to upload

**Throws `NullPointerException`:** if `data` is `null`

#### Constructor

```java
protected Texture(TextureData data, boolean ownsData)
```

Creates and uploads an RGBA8 OpenGL texture, retaining the supplied pixel data.
Configures the default filters and clamp-to-edge wrapping and leaves the texture
bound on the active texture unit. Disposal always releases the GPU object and
also releases pixel data when ownsData is true. Requires a current OpenGL context.

- **`data`** — decoded RGBA pixels and dimensions
- **`ownsData`** — whether disposal also releases the decoded pixel buffer

**Throws `NullPointerException`:** if data is null

#### Constructor

```java
protected Texture(int textureID, TextureData data)
```

Creates a texture wrapper around an existing OpenGL texture ID and
texture data.

This constructor does not generate a new OpenGL texture object or upload
data. It simply wraps an already existing texture ID with the supplied
metadata. This is useful for subclasses or advanced cases where the
texture object has already been created elsewhere.

- **`textureID`** — the existing OpenGL texture ID
- **`data`** — the associated texture data

**Throws `NullPointerException`:** if `data` is `null`

#### Constructor

```java
protected Texture(int textureID, TextureData data, boolean ownsData)
```

Wraps an existing texture handle without uploading pixels or changing GL state.
The wrapper takes responsibility for deleting the handle on disposal; ownsData
separately controls release of the associated CPU pixel buffer.

- **`textureID`** — existing OpenGL texture handle
- **`data`** — retained pixel data and dimensions
- **`ownsData`** — whether disposal also releases the pixel buffer

**Throws `NullPointerException`:** if data is null

#### bind

```java
public void bind()
```

Binds this texture to `GL_TEXTURE_2D`.

This makes the texture the current active 2D texture for subsequent
OpenGL operations in the currently active texture unit.

#### getFilter

```java
public TextureFilter getFilter()
```

Returns the currently active filter for this texture.

**Returns:** the current texture filter

#### setFilter

```java
public void setFilter(TextureFilter filter)
```

Applies a new filtering mode to this texture.

The texture is bound, the minification and magnification parameters are
updated, and mipmaps are generated automatically when the supplied filter
requires them.

- **`filter`** — the new filter to apply

**Throws `NullPointerException`:** if `filter` is `null`

#### getData

```java
public TextureData getData()
```

Returns the `TextureData` associated with this texture.

This provides access to the texture's width, height, and original
pixel buffer metadata as long as the texture has not been disposed.

**Returns:** the texture data

#### getTextureID

```java
public int getTextureID()
```

Returns the OpenGL texture object ID.

**Returns:** the OpenGL texture ID

#### getWidth

```java
public int getWidth()
```

Returns the width of this texture in pixels.

**Returns:** the texture width

#### getHeight

```java
public int getHeight()
```

Returns the height of this texture in pixels.

**Returns:** the texture height

#### sprite

```java
public Sprite sprite()
```

Creates a new `Sprite` backed by this texture.

This is a convenience factory method for quickly turning a texture into a
full-region sprite without manually constructing the sprite yourself.

**Returns:** a new sprite using this texture

#### dispose

```java
public void dispose()
```

Disposes this texture's GPU resource and clears stored references.

This deletes the OpenGL texture object using `glDeleteTextures`.
After disposal, the texture data and filter references are set to
`null`. The instance should not be used again after this call.

#### reset

```java
    public void reset()
```

Resets this texture to its pooled default state.

The reset behavior restores the filtering mode to
`TextureFilter#NEAREST`.

</details>

<a id="type-textureatlas"></a>

### TextureAtlas

[Source](../../src/main/java/valthorne/graphics/texture/TextureAtlas.java#L40)

Builds a square GPU texture atlas and keyed region views from borrowed CPU
image data. Inputs may be complete textures, decoded images, or rectangular
regions, but source pixels must remain readable until build. Adding inputs
does not copy pixels or transfer ownership.

Build sorts inputs by descending height and packs unrotated shelves from left
to right, adding configured region padding and an outer border. It estimates
a square size, doubles until packing succeeds, and optionally rounds dimensions
to powers of two. Placement and source rectangles follow TexturePacker's
top-left pixel convention. Padding is empty space, not edge-pixel extrusion.

Each build allocates a separate TextureData and GPU texture and requires a current
OpenGL context. The builder retains pending inputs for subsequent builds.
The caller owns result cleanup; regions share the result texture. Duplicate
keys are accepted during collection and rejected at build time.

```java
TextureAtlas atlas = new TextureAtlas().setPadding(2).setBorder(2);
atlas.add("icon", sourceData);
TextureAtlas.Result result = atlas.build();
try {
    TextureRegion icon = result.getRegion("icon");
    // Use icon while the result texture remains alive.
} finally {
    result.getAtlasTexture().dispose();
    result.getAtlasData().dispose();
}
```

<details>
<summary>TextureAtlas operation reference (11 declarations)</summary>

#### setPadding

```java
public TextureAtlas setPadding(int padding)
```

Sets empty pixel spacing between neighboring shelf items and between shelves.
Does not extrude edge colors into the padding.

- **`padding`** — nonnegative spacing in pixels

**Returns:** this builder

**Throws `IllegalArgumentException`:** if padding is negative

#### setBorder

```java
public TextureAtlas setBorder(int border)
```

Sets the empty border reserved on every side of packed content.

- **`border`** — nonnegative border width in pixels

**Returns:** this builder

**Throws `IllegalArgumentException`:** if border is negative

#### setPowerOfTwo

```java
public TextureAtlas setPowerOfTwo(boolean powerOfTwo)
```

Controls rounding of attempted square sizes to the next power of two.
The result remains square even when this option is disabled.

- **`powerOfTwo`** — whether to round attempted dimensions

**Returns:** this builder

#### setMaxSize

```java
public TextureAtlas setMaxSize(int maxSize)
```

Sets the maximum attempted dimension for nonempty builds. This is an application
limit rather than a query of the GPU texture limit. The empty-atlas path uses
the sanitized start size without checking this bound.

- **`maxSize`** — positive dimension limit in pixels

**Returns:** this builder

**Throws `IllegalArgumentException`:** if maxSize is nonpositive

#### setStartSize

```java
public TextureAtlas setStartSize(int startSize)
```

Sets the minimum starting dimension before optional power-of-two rounding.
Nonempty builds may start larger according to content estimates and grow by
doubling when packing fails.

- **`startSize`** — positive initial dimension in pixels

**Returns:** this builder

**Throws `IllegalArgumentException`:** if startSize is nonpositive

#### add

```java
public TextureAtlas add(String key, Texture texture)
```

Collects the complete retained CPU image of a texture. The texture is not read
back from the GPU; keep its data alive until build.

- **`key`** — nonnull key, validated for uniqueness during build
- **`texture`** — nonnull texture with retained CPU data

**Returns:** this builder

**Throws `NullPointerException`:** if key, texture, or retained data is null

**Throws `IllegalArgumentException`:** if image dimensions are nonpositive

#### add

```java
public TextureAtlas add(String key, TextureData data)
```

Collects a complete borrowed decoded image without copying its pixels.

- **`key`** — nonnull key, validated for uniqueness during build
- **`data`** — nonnull source image retained until build

**Returns:** this builder

**Throws `NullPointerException`:** if key or data is null

**Throws `IllegalArgumentException`:** if dimensions are nonpositive

#### add

```java
public TextureAtlas add(String key, TextureRegion region)
```

Collects a source region from its backing texture's retained pixels, truncating
region coordinates and dimensions to integers. Does not bake UV transformations.

- **`key`** — nonnull key, validated for uniqueness during build
- **`region`** — nonnull source region with retained CPU pixels

**Returns:** this builder

**Throws `NullPointerException`:** if a required source reference or key is null

**Throws `IllegalArgumentException`:** if truncated dimensions are nonpositive

#### add

```java
public TextureAtlas add(String key, TextureData src, int sx, int sy, int sw, int sh)
```

Stores a borrowed source rectangle. Validates references and positive dimensions
now; source-coordinate validity and duplicate keys are handled during building
and pixel copying.

- **`key`** — nonnull result key
- **`src`** — source image that must remain readable
- **`sx`** — source X in the packer's top-left convention
- **`sy`** — source Y in the packer's top-left convention
- **`sw`** — positive source width
- **`sh`** — positive source height

**Returns:** this builder

**Throws `NullPointerException`:** if key or src is null

**Throws `IllegalArgumentException`:** if sw or sh is nonpositive

#### clear

```java
public TextureAtlas clear()
```

Drops pending source references without disposing them or any previously built
result. Packing configuration is retained.

**Returns:** this builder

#### build

```java
public Result build()
```

Packs pending images and creates a new CPU/GPU atlas pair without clearing the
builder. Empty input creates a blank atlas at the sanitized start size.
Nonempty input validates keys, sorts by descending height, and grows square
attempts until they fit or exceed maxSize. Result map iteration follows that
height-sorted order.

**Returns:** newly owned atlas resources and borrowed region views

**Throws `IllegalStateException`:** if keys repeat or content cannot fit within the size limit

</details>

<a id="type-textureatlas-item"></a>

### TextureAtlas.Item — internal support type

[Source](../../src/main/java/valthorne/graphics/texture/TextureAtlas.java#L388)

One borrowed source rectangle and its mutable shelf-packing destination.
Failed placement attempts may overwrite only a prefix of the item list; every
successful build assigns all final destinations before copying pixels.

<a id="type-textureatlas-result"></a>

### TextureAtlas.Result

[Source](../../src/main/java/valthorne/graphics/texture/TextureAtlas.java#L432)

Holds one built atlas's CPU data, GPU texture, and unmodifiable key mapping.
Region and pixel objects remain mutable; the wrapper supplies no automatic
cleanup. Dispose the atlas texture and its separately owned CPU data after
all returned regions are no longer needed; this texture borrows the data.

<details>
<summary>TextureAtlas.Result operation reference (4 declarations)</summary>

#### getAtlasData

```java
public TextureData getAtlasData()
```

Returns the live baked CPU image associated with the result texture.
Changing it does not itself upload replacement GPU pixels.

**Returns:** result CPU data

#### getAtlasTexture

```java
public Texture getAtlasTexture()
```

Returns the result's GPU texture, shared by every region. The caller is
responsible for disposing it after the atlas is no longer used.

**Returns:** result texture

#### getRegion

```java
public TextureRegion getRegion(String key)
```

Looks up a borrowed region by its original input key.

- **`key`** — atlas input key

**Returns:** live region, or null when missing

#### getRegions

```java
public Map<String, TextureRegion> getRegions()
```

Returns the unmodifiable key mapping. Its region values remain mutable and
share the atlas texture.

**Returns:** stable key-to-region map

</details>

<a id="type-texturebatch"></a>

### TextureBatch

[Source](../../src/main/java/valthorne/graphics/texture/TextureBatch.java#L94)

`TextureBatch` is Valthorne's instanced textured-quad renderer. It is designed
to batch many sprite draw operations into as few OpenGL draw calls as possible while
still supporting several advanced rendering features such as color tinting, sprite
scaling, sprite rotation, nine-patch rendering, nested clipping, translation stacks,
shader overrides, and rendering into a `FrameBuffer`.

Instead of immediately sending every individual sprite to the GPU, this class stores
per-instance data inside an internal `FloatBuffer`. Once the batch is full or
the caller explicitly ends the batch, that instance data is uploaded to the GPU and
rendered with `glDrawArraysInstanced`. This greatly reduces driver overhead and
improves rendering performance when drawing large numbers of textured objects.

The batch uses a static quad as its base geometry. Every sprite draw call writes a
small set of instance attributes describing where that quad should appear, which
texture unit it should sample from, what UV region should be used, what color should
tint it, whether clipping is active, and how rotation should be applied. The shader
then reconstructs the final sprite from this data for each instance.

This class supports multiple textures in a single batch, up to the configured texture
unit limit. When a draw call references a texture that does not fit in the currently
active set of bound textures, the batch is flushed automatically and starts filling
again with a fresh texture unit set.

Typical usage follows the standard batching lifecycle:

```java
TextureBatch batch = new TextureBatch(2000);
Texture texture = new Texture(...);
TextureRegion region = new TextureRegion(texture);
Sprite sprite = new Sprite(region);

sprite.setX(100);
sprite.setY(50);
sprite.setRotation(25f);

batch.begin();
batch.draw(sprite);
batch.draw(texture, 300, 100, 64, 64);

batch.pushTranslation(20, 10);
batch.draw(sprite, 400, 200, 96, 96);
batch.popTranslation();

batch.beginScissor(0, 0, 800, 600);
batch.drawRegion(region, 50, 50, 128, 128);
batch.endScissor();

batch.end();
```

This class is intended to be reused every frame rather than recreated constantly.
Create it once, use it for the lifetime of the renderer, and dispose it when the
graphics context is being shut down.

<details>
<summary>TextureBatch operation reference (50 declarations)</summary>

#### Constructor

```java
public TextureBatch(int maxSprites)
```

Creates a new `TextureBatch` with the requested sprite capacity and an
automatically detected texture unit limit clamped to sixteen.

This constructor is convenient for most normal usage. It queries the OpenGL
context for supported texture image units and keeps the batch within a safe
upper bound.

- **`maxSprites`** — the maximum number of sprite instances that may be queued before the batch must flush

#### Constructor

```java
public TextureBatch(int maxSprites, int maxTextureUnits)
```

Creates a new `TextureBatch` with an explicit sprite capacity and explicit
texture unit limit.

This constructor allocates the CPU-side instance buffer, creates the default
shader, binds its expected attributes and samplers, builds the base quad vertex
data, and creates the OpenGL buffer objects required for batched instanced
rendering.

The texture unit count is clamped to sixteen to keep the generated fragment shader
and sampler setup within the intended supported range for this batch.

- **`maxSprites`** — the maximum number of sprite instances that may be queued before the batch flushes
- **`maxTextureUnits`** — the maximum number of texture units this batch should use

**Throws `IllegalArgumentException`:** if `maxSprites <= 0`

**Throws `IllegalArgumentException`:** if `maxTextureUnits < 2`

#### getShader

```java
public Shader getShader()
```

Returns the shader that is currently considered active for external callers.

If a custom shader has been assigned with `setShader(Shader)`, that shader
is returned. Otherwise the internally managed default shader is returned.

**Returns:** the currently selected shader for this batch

#### setShader

```java
public void setShader(Shader shader)
```

Sets a custom shader for the batch.

Passing the same instance as the default shader clears the custom override and
reverts the batch back to its internal shader. If the batch is currently drawing,
it flushes queued data first, unbinds the old shader, switches the active shader,
and then rebinds the required vertex attribute state.

- **`shader`** — the shader to use, or `null` to revert to the default shader

#### getDefaultShader

```java
public Shader getDefaultShader()
```

Returns the default shader owned by this batch.

This can be useful when the caller wants to inspect, configure, or compare the
built-in shader without affecting the custom shader state.

**Returns:** the default batch shader

#### setColor

```java
public void setColor(Color tint)
```

Sets the batch's default tint color using a `Color` instance.

This default tint is used for draw calls that do not provide their own tint
argument. The supplied color is copied into the internal batch color rather
than storing the reference directly.

- **`tint`** — the new default tint color

**Throws `NullPointerException`:** if `tint` is `null`

#### setColor

```java
public void setColor(float r, float g, float b, float a)
```

Sets the batch's default tint color using explicit RGBA components.

This affects future draw calls that do not pass a per-draw tint.

- **`r`** — the red component
- **`g`** — the green component
- **`b`** — the blue component
- **`a`** — the alpha component

#### pushTranslation

```java
public void pushTranslation(float x, float y)
```

Pushes the current translation state onto the translation stack and applies an
additional translation offset.

This is useful for hierarchical rendering where a parent container wants child
draw calls to inherit an offset without manually adjusting every coordinate.
The translation affects queued draw coordinates only and remains active until
`popTranslation()` is called.

- **`x`** — the translation offset to add on the X axis
- **`y`** — the translation offset to add on the Y axis

**Throws `IllegalStateException`:** if the batch is not currently drawing

**Throws `IllegalStateException`:** if the translation stack overflows

#### popTranslation

```java
public void popTranslation()
```

Pops the most recently pushed translation state from the translation stack.

After this call, future draw operations use the previously stored translation
values. This method must be paired with an earlier call to
`pushTranslation(float, float)`.

**Throws `IllegalStateException`:** if the batch is not currently drawing

**Throws `IllegalStateException`:** if there is no translation state to pop

#### getTranslationX

```java
public float getTranslationX()
```

Returns the currently accumulated X translation offset.

This value reflects the active translation stack state and is added to draw
positions when sprites are queued.

**Returns:** the current X translation offset

#### getTranslationY

```java
public float getTranslationY()
```

Returns the currently accumulated Y translation offset.

This value reflects the active translation stack state and is added to draw
positions when sprites are queued.

**Returns:** the current Y translation offset

#### isClipEnabled

```java
public boolean isClipEnabled()
```

Reports whether the batch currently has a logical clip rectangle enabled.
This is batch state rather than a fresh query of GL scissor enablement.

**Returns:** whether logical clipping is active

#### getClipX

```java
public float getClipX()
```

Returns the active logical clip's X coordinate in batch drawing space.

**Returns:** clip X; meaningful when clipping is enabled

#### getClipY

```java
public float getClipY()
```

Returns the active logical clip's Y coordinate in batch drawing space.

**Returns:** clip Y; meaningful when clipping is enabled

#### getClipWidth

```java
public float getClipWidth()
```

Returns the active logical clip width before conversion to framebuffer scissor pixels.

**Returns:** clip width

#### getClipHeight

```java
public float getClipHeight()
```

Returns the active logical clip height before conversion to framebuffer scissor pixels.

**Returns:** clip height

#### resumeAfterExternalDraw

```java
public void resumeAfterExternalDraw()
```

Restores GPU bindings after another renderer has drawn. Call flush() before
handing control away. Unlike begin(), this preserves clip and translation
stacks, tint, shader choice, and framebuffer ownership.

#### beginScissor

```java
public void beginScissor(float x, float y, float width, float height)
```

Begins a new scissor clip region for subsequently queued draw operations.

The clip state is stored on an internal stack, allowing nested clipping. When a
new clip is added while another clip is already active, the resulting effective
region becomes the intersection of the existing clip and the new one. This
guarantees that nested clips cannot expand outside of their parent region.

The clip rectangle is stored as instance data and later interpreted by the shader.
It does not directly call `glScissor` for every nested region.

- **`x`** — the clip region X coordinate
- **`y`** — the clip region Y coordinate
- **`width`** — the clip region width
- **`height`** — the clip region height

**Throws `IllegalStateException`:** if the batch is not currently drawing

**Throws `IllegalStateException`:** if the clip stack overflows

#### endScissor

```java
public void endScissor()
```

Ends the most recently begun scissor clip region.

If a parent clip still exists after the pop, that parent becomes the newly active
clip. If no clip remains, clipping is disabled entirely for future queued draws.

**Throws `IllegalStateException`:** if the batch is not currently drawing

**Throws `IllegalStateException`:** if there is no active scissor region to end

#### begin

```java
public void begin()
```

Begins a new rendering batch using the default framebuffer.

This is the standard entry point for normal rendering when no off-screen target
is needed.

#### begin

```java
public void begin(FrameBuffer fbo)
```

Begins a new rendering batch, optionally rendering into a `FrameBuffer`.

This method initializes batch state, resets clip and translation stacks, saves
and temporarily modifies relevant OpenGL state, clears instance bookkeeping, and
binds the currently active shader and vertex attributes. If an FBO is provided,
the current framebuffer and viewport are captured and restored later in
`end()`.

If the provided framebuffer size does not match the current viewport size, the
framebuffer is resized to match the viewport before rendering begins.

- **`fbo`** — the framebuffer target to render into, or `null` to render to the currently bound default framebuffer

**Throws `IllegalStateException`:** if the batch is already active

**Throws `IllegalStateException`:** if the current viewport is invalid while rendering
into a framebuffer

#### draw

```java
public void draw(Sprite sprite)
```

Queues a `Sprite` for rendering using the sprite's own transform, region,
size, flip state, rotation state, and color.

This overload simply delegates to `draw(Sprite, Color)` with a
`null` tint, allowing the sprite's own color to be used.

- **`sprite`** — the sprite to draw

**Throws `NullPointerException`:** if `sprite` is `null`

#### draw

```java
public void draw(Sprite sprite, Color tint)
```

Queues a `Sprite` for rendering using the sprite's current transform data
but with an optional tint override.

The sprite's region UVs are resolved, its flip flags are applied by swapping UV
bounds, scaled width and height are computed, and the sprite's rotation is
converted into sine and cosine values used by the shader. The result is then
forwarded to `drawUV(Texture, float, float, float, float, float, float, float, float, float, float, float, float, Color)`.

- **`sprite`** — the sprite to draw
- **`tint`** — an optional tint override, or `null` to use the sprite's own color

**Throws `NullPointerException`:** if `sprite` is `null`

#### draw

```java
public void draw(Sprite sprite, float x, float y)
```

Queues a `Sprite` for rendering at a custom position using the sprite's
original width and height.

- **`sprite`** — the sprite to draw
- **`x`** — the destination X position
- **`y`** — the destination Y position

**Throws `NullPointerException`:** if `sprite` is `null`

#### draw

```java
public void draw(Sprite sprite, float x, float y, Color tint)
```

Queues a `Sprite` for rendering at a custom position using the sprite's
original width and height and an optional tint override.

- **`sprite`** — the sprite to draw
- **`x`** — the destination X position
- **`y`** — the destination Y position
- **`tint`** — an optional tint override

**Throws `NullPointerException`:** if `sprite` is `null`

#### draw

```java
public void draw(Sprite sprite, float x, float y, float width, float height)
```

Queues a `Sprite` for rendering at a custom position and custom size.

- **`sprite`** — the sprite to draw
- **`x`** — the destination X position
- **`y`** — the destination Y position
- **`width`** — the destination width
- **`height`** — the destination height

**Throws `NullPointerException`:** if `sprite` is `null`

#### draw

```java
public void draw(Sprite sprite, float x, float y, float width, float height, Color tint)
```

Queues a `Sprite` for rendering at a custom position and size with an
optional tint override.

This overload recalculates the effective rotation origin based on how the
destination size compares to the sprite's original size. That allows rotated
rendering to remain visually correct even when the sprite is stretched or shrunk.

- **`sprite`** — the sprite to draw
- **`x`** — the destination X position
- **`y`** — the destination Y position
- **`width`** — the destination width
- **`height`** — the destination height
- **`tint`** — an optional tint override, or `null` to use the sprite's color

**Throws `NullPointerException`:** if `sprite` is `null`

#### draw

```java
public void draw(NinePatchTexture texture)
```

Queues a `NinePatchTexture` using the texture's own position and size.

This overload delegates to
`draw(NinePatchTexture, float, float, float, float)`.

- **`texture`** — the nine-patch texture to draw

#### draw

```java
public void draw(NinePatchTexture texture, float x, float y, float width, float height)
```

Queues a `NinePatchTexture` for rendering at the requested destination
rectangle.

The source region is divided into a 3x3 grid representing fixed-size corners,
stretchable edges, and a stretchable center. Each visible patch is translated
into its own draw call with correct UVs, optional flips, rotation origin
adjustment, and tint. Empty patches caused by zero size are skipped.

- **`texture`** — the nine-patch texture to draw
- **`x`** — the destination X position
- **`y`** — the destination Y position
- **`width`** — the destination width
- **`height`** — the destination height

**Throws `NullPointerException`:** if `texture` is `null`

#### draw

```java
public void draw(NinePatchTexture texture, float x, float y, float width, float height, float regionX, float regionY, float regionWidth, float regionHeight, float originX, float originY, float rotation, Color tint)
```

Draws a nine-patch texture using the specified parameters.

- **`texture`** — the nine-patch texture to be drawn; cannot be null
- **`x`** — the X-coordinate of the bottom-left corner where the texture will be drawn
- **`y`** — the Y-coordinate of the bottom-left corner where the texture will be drawn
- **`width`** — the total width of the area to be drawn
- **`height`** — the total height of the area to be drawn
- **`regionX`** — the X-coordinate of the source region to be drawn (in region space)
- **`regionY`** — the Y-coordinate of the source region to be drawn (in region space)
- **`regionWidth`** — the width of the source region to be drawn (in region space)
- **`regionHeight`** — the height of the source region to be drawn (in region space)
- **`originX`** — the X-coordinate of the origin within the texture space, used for rotations
- **`originY`** — the Y-coordinate of the origin within the texture space, used for rotations
- **`rotation`** — the rotation angle in degrees (counter-clockwise positive)
- **`tint`** — the color tint to be applied to the texture; may be null for default tint

#### draw

```java
public void draw(Texture tex, float x, float y, float w, float h)
```

Queues a full `Texture` for rendering with normalized UVs covering the
entire image and no additional tint override.

- **`tex`** — the texture to draw
- **`x`** — the destination X position
- **`y`** — the destination Y position
- **`w`** — the destination width
- **`h`** — the destination height

#### draw

```java
public void draw(Texture tex, float x, float y, float w, float h, Color tint)
```

Queues a full `Texture` for rendering with normalized UVs covering the
entire image and an optional tint override.

- **`tex`** — the texture to draw
- **`x`** — the destination X position
- **`y`** — the destination Y position
- **`w`** — the destination width
- **`h`** — the destination height
- **`tint`** — the tint override to apply

#### draw

```java
public void draw(Texture tex, float x, float y, float w, float h, float regionX, float regionY, float regionWidth, float regionHeight)
```

Queues a pixel-space source region from a `Texture` for rendering without
a tint override.

The supplied source rectangle is converted into normalized UV coordinates using
the texture's metadata.

- **`tex`** — the texture to sample from
- **`x`** — the destination X position
- **`y`** — the destination Y position
- **`w`** — the destination width
- **`h`** — the destination height
- **`regionX`** — the source region X in pixels
- **`regionY`** — the source region Y in pixels
- **`regionWidth`** — the source region width in pixels
- **`regionHeight`** — the source region height in pixels

#### draw

```java
public void draw(Texture tex, float x, float y, float w, float h, float regionX, float regionY, float regionWidth, float regionHeight, Color tint)
```

Queues a pixel-space source region from a `Texture` for rendering with an
optional tint override.

If the texture does not expose valid metadata, the method falls back to drawing
the full texture rather than failing. If the texture width or height is zero, it
also falls back to full-texture normalized UVs.

- **`tex`** — the texture to sample from
- **`x`** — the destination X position
- **`y`** — the destination Y position
- **`w`** — the destination width
- **`h`** — the destination height
- **`regionX`** — the source region X in pixels
- **`regionY`** — the source region Y in pixels
- **`regionWidth`** — the source region width in pixels
- **`regionHeight`** — the source region height in pixels
- **`tint`** — the tint override to apply

**Throws `NullPointerException`:** if `tex` is `null`

#### draw

```java
public void draw(TextureRegion region, float x, float y, float w, float h)
```

Queues a `TextureRegion` for rendering without a tint override.

This overload resolves the region's source rectangle and delegates to the
texture-based `drawRegion(...)` overload.

- **`region`** — the texture region to draw
- **`x`** — the destination X position
- **`y`** — the destination Y position
- **`w`** — the destination width
- **`h`** — the destination height

**Throws `NullPointerException`:** if `region` is `null`

#### draw

```java
public void draw(TextureRegion region, float x, float y, float w, float h, Color tint)
```

Queues a `TextureRegion` for rendering with an optional tint override.

- **`region`** — the texture region to draw
- **`x`** — the destination X position
- **`y`** — the destination Y position
- **`w`** — the destination width
- **`h`** — the destination height
- **`tint`** — the tint override to apply

**Throws `NullPointerException`:** if `region` is `null`

#### draw

```java
public void draw(Texture tex, float x, float y, float w, float h, float originX, float originY, float rotation, Color tint)
```

Draws a texture on the screen with specified position, size, origin, rotation, and color tint.

- **`tex`** — the texture to be drawn; must not be null
- **`x`** — the x-coordinate where the texture will be drawn
- **`y`** — the y-coordinate where the texture will be drawn
- **`w`** — the width of the texture
- **`h`** — the height of the texture
- **`originX`** — the x-coordinate of the origin point for rotation
- **`originY`** — the y-coordinate of the origin point for rotation
- **`rotation`** — the angle of rotation in degrees, counter-clockwise
- **`tint`** — the color tint to be applied to the texture; null for no tint

#### draw

```java
public void draw(Texture tex, float x, float y, float w, float h, float regionX, float regionY, float regionWidth, float regionHeight, float originX, float originY, float rotation, Color tint)
```

Draws a portion of a texture on a specified position with given dimensions, rotation, and color tint.

- **`tex`** — the texture to be drawn; must not be null
- **`x`** — the x-coordinate of the bottom-left corner where the texture will be drawn
- **`y`** — the y-coordinate of the bottom-left corner where the texture will be drawn
- **`w`** — the width of the texture to be drawn
- **`h`** — the height of the texture to be drawn
- **`regionX`** — the x-coordinate of the region within the texture to use
- **`regionY`** — the y-coordinate of the region within the texture to use
- **`regionWidth`** — the width of the region within the texture to use
- **`regionHeight`** — the height of the region within the texture to use
- **`originX`** — the x-coordinate of the rotation origin, relative to the bottom-left corner of the texture
- **`originY`** — the y-coordinate of the rotation origin, relative to the bottom-left corner of the texture
- **`rotation`** — the rotation angle in degrees, where a positive value rotates counter-clockwise
- **`tint`** — the color tint to apply to the texture; must not be null

#### draw

```java
public void draw(TextureRegion region, float x, float y, float w, float h, float originX, float originY, float rotation, Color tint)
```

Draws the specified texture region with the given position, size,
rotation, origin, and color tint.

- **`region`** — the texture region to be drawn; must not be null
- **`x`** — the x-coordinate of the bottom-left corner of the drawing area
- **`y`** — the y-coordinate of the bottom-left corner of the drawing area
- **`w`** — the width of the drawing area
- **`h`** — the height of the drawing area
- **`originX`** — the x-coordinate of the origin for rotation, relative to the bottom-left corner of the drawing area
- **`originY`** — the y-coordinate of the origin for rotation, relative to the bottom-left corner of the drawing area
- **`rotation`** — the rotation angle in degrees, applied around the origin
- **`tint`** — the color tint to apply to the texture region during drawing

#### draw

```java
public void draw(TextureRegion region, float x, float y, float w, float h, float regionX, float regionY, float regionWidth, float regionHeight, float originX, float originY, float rotation, Color tint)
```

Draws a textured region with the specified transformation and tint options.

- **`region`** — the texture region to draw, must not be null
- **`x`** — the x-coordinate of the bottom-left corner of the rectangle in world space
- **`y`** — the y-coordinate of the bottom-left corner of the rectangle in world space
- **`w`** — the width of the rectangle in world space
- **`h`** — the height of the rectangle in world space
- **`regionX`** — the x-coordinate of the starting point in the texture region
- **`regionY`** — the y-coordinate of the starting point in the texture region
- **`regionWidth`** — the width of the region inside the texture
- **`regionHeight`** — the height of the region inside the texture
- **`originX`** — the origin's x-coordinate relative to the bottom-left corner of the rectangle
- **`originY`** — the origin's y-coordinate relative to the bottom-left corner of the rectangle
- **`rotation`** — the rotation angle, in degrees, to apply to the texture region
- **`tint`** — the color to tint the texture region

#### drawUV

```java
public void drawUV(Texture tex, float x, float y, float w, float h, float u0, float v0, float u1, float v1, Color tint)
```

Queues a texture draw using explicit normalized UV bounds without rotation data.

This overload is a convenience wrapper that forwards to the more complete
rotation-aware UV draw method with a zero origin and identity rotation.

- **`tex`** — the texture to draw
- **`x`** — the destination X position
- **`y`** — the destination Y position
- **`w`** — the destination width
- **`h`** — the destination height
- **`u0`** — the starting U coordinate
- **`v0`** — the starting V coordinate
- **`u1`** — the ending U coordinate
- **`v1`** — the ending V coordinate
- **`tint`** — the tint override to apply

#### drawUV

```java
public void drawUV(Texture tex, float x, float y, float w, float h, float u0, float v0, float u1, float v1, float originX, float originY, float sinRot, float cosRot, Color tint)
```

Queues a texture draw using explicit normalized UV bounds, an arbitrary rotation
origin, precomputed rotation sine and cosine values, and an optional tint.

This is the core draw path used by most other draw overloads. It validates batch
state, flushes if instance or texture-unit limits are reached, resolves the
correct texture unit for the texture, selects the final tint color, applies the
current translation stack, writes all instance attributes into the internal buffer,
and increments the queued instance count.

- **`tex`** — the texture to draw
- **`x`** — the destination X position
- **`y`** — the destination Y position
- **`w`** — the destination width
- **`h`** — the destination height
- **`u0`** — the starting U coordinate
- **`v0`** — the starting V coordinate
- **`u1`** — the ending U coordinate
- **`v1`** — the ending V coordinate
- **`originX`** — the rotation origin X relative to the destination quad
- **`originY`** — the rotation origin Y relative to the destination quad
- **`sinRot`** — the sine of the desired rotation angle
- **`cosRot`** — the cosine of the desired rotation angle
- **`tint`** — the tint override to apply, or `null` to use the batch color

**Throws `IllegalStateException`:** if the batch is not currently drawing

**Throws `NullPointerException`:** if `tex` is `null`

#### end

```java
public void end()
```

Ends the current batch, flushes any remaining queued instances, restores OpenGL
state modified by `begin()` and unbinds textures and shaders used by the
batch.

This method also resets clip and translation state, restores the previous
framebuffer and viewport when rendering to an FBO, and clears texture cache state
so the next batch begins cleanly.

**Throws `IllegalStateException`:** if the batch is not currently active

#### isCullingEnabled

```java
public boolean isCullingEnabled()
```

Reports whether standard-quad viewport culling is enabled. Custom shaders
remain conservatively exempt from this optimization.

**Returns:** configured culling flag

#### setCullingEnabled

```java
public TextureBatch setCullingEnabled(boolean enabled)
```

Standard-quad viewport culling. Custom shaders are conservatively exempt.

#### getTotalSubmittedSprites

```java
public long getTotalSubmittedSprites()
```

Returns the cumulative sprite submission counter used by rendering diagnostics.

**Returns:** lifetime submitted-sprite count

#### getTotalCulledSprites

```java
public long getTotalCulledSprites()
```

Returns the cumulative number of sprites rejected by viewport culling.

**Returns:** lifetime culled-sprite count

#### getTotalDrawCalls

```java
public long getTotalDrawCalls()
```

Returns the cumulative count of nonempty instanced draws issued by flush.

**Returns:** lifetime draw-call count

#### flush

```java
public void flush()
```

Uploads queued instances after applying the active projection, orphans the
stream buffer, and issues one instanced draw. Clears CPU instance/texture-slot
bookkeeping afterward while retaining the drawing scope. Empty queues return
immediately. Call within an active batch scope with its GL state intact;
this method does not establish or restore the full scope itself.

#### dispose

```java
public void dispose()
```

Releases native and OpenGL resources owned by this batch.

This disposes the internally owned default shader and deletes the quad and
instance VBOs. After this method is called, the batch should no longer be used.

</details>

<a id="type-texturebatchcontract"></a>

### TextureBatchContract

[Source](../../src/main/java/valthorne/graphics/texture/TextureBatchContract.java#L65)

`TextureBatchContract` defines the shared rendering contract used by
`TextureBatch` and its shaders. It centralizes all attribute indices,
attribute names, vertex layout constants, instance layout constants, and
shader source generation helpers so that both the Java-side renderer and the
GLSL-side programs stay in sync.

The main purpose of this class is to ensure that the batch renderer uses a
single authoritative definition for:

- how many vertices make up the base quad

- how many floats are stored per quad vertex

- how many floats are stored per instance

- which attribute locations map to which shader variables

- how the default batch shaders are generated

- how texture sampler uniforms are bound

Since the batch renderer relies on instanced rendering, the layout defined
here is extremely important. If the attribute indices, names, or buffer
packing order ever drift apart from the data written by `TextureBatch`,
rendering will break immediately. Keeping all of that information in one
final utility class prevents duplication and makes maintenance much easier.

This class also generates shader source strings for the default vertex shader,
the default fragment shader, and the reusable fragment preamble used when
building custom fragment shaders that still need access to the batch's
texture-sampling and clipping logic.

##### Example Usage

```java
Shader shader = new Shader(
        TextureBatchContract.defaultVertexShader(),
        TextureBatchContract.buildDefaultFragmentShader(8)
);

TextureBatchContract.bindAttributes(shader);
shader.reload();
TextureBatchContract.bindSamplers(shader, 8);
```

In normal usage, you will not create instances of this class. It is a pure
static contract holder and helper.

<details>
<summary>TextureBatchContract operation reference (31 declarations)</summary>

#### UNIFORM_MVP

```java
public static final  String UNIFORM_MVP
```

Uniform name used for the batch projection matrix.

#### QUAD_VERTS

```java
public static final  int QUAD_VERTS
```

The number of vertices used to represent the base quad.

#### QUAD_FLOATS_PER_VERT

```java
public static final  int QUAD_FLOATS_PER_VERT
```

The number of float components stored for each quad vertex.

#### BYTES_PER_FLOAT

```java
public static final  int BYTES_PER_FLOAT
```

The size of a single float in bytes.

#### INST_FLOATS

```java
public static final  int INST_FLOATS
```

The number of float components stored for each sprite instance.

#### INST_STRIDE_BYTES

```java
public static final  int INST_STRIDE_BYTES
```

The total size in bytes of one instance entry.

#### ATTR_LOCAL

```java
public static final  int ATTR_LOCAL
```

Attribute index for the quad's local position attribute.

#### ATTR_UV

```java
public static final  int ATTR_UV
```

Attribute index for the quad's local UV attribute.

#### ATTR_XYWH

```java
public static final  int ATTR_XYWH
```

Attribute index for instance position and size.

#### ATTR_COL

```java
public static final  int ATTR_COL
```

Attribute index for instance color.

#### ATTR_TEX

```java
public static final  int ATTR_TEX
```

Attribute index for instance texture unit selection.

#### ATTR_UVRECT

```java
public static final  int ATTR_UVRECT
```

Attribute index for instance UV rectangle bounds.

#### ATTR_ORIGIN

```java
public static final  int ATTR_ORIGIN
```

Attribute index for instance rotation origin.

#### ATTR_ROT

```java
public static final  int ATTR_ROT
```

Attribute index for instance rotation sine and cosine data.

#### ATTR_CLIPRECT

```java
public static final  int ATTR_CLIPRECT
```

Attribute index for instance clip rectangle data.

#### ATTR_CLIPENABLED

```java
public static final  int ATTR_CLIPENABLED
```

Attribute index for instance clip-enabled state.

#### ATTR_NAME_LOCAL

```java
public static final  String ATTR_NAME_LOCAL
```

Shader attribute name for the quad's local position.

#### ATTR_NAME_UV

```java
public static final  String ATTR_NAME_UV
```

Shader attribute name for the quad's UV coordinate.

#### ATTR_NAME_XYWH

```java
public static final  String ATTR_NAME_XYWH
```

Shader attribute name for instance position and size.

#### ATTR_NAME_COL

```java
public static final  String ATTR_NAME_COL
```

Shader attribute name for instance color.

#### ATTR_NAME_TEX

```java
public static final  String ATTR_NAME_TEX
```

Shader attribute name for instance texture unit selection.

#### ATTR_NAME_UVRECT

```java
public static final  String ATTR_NAME_UVRECT
```

Shader attribute name for instance UV rectangle.

#### ATTR_NAME_ORIGIN

```java
public static final  String ATTR_NAME_ORIGIN
```

Shader attribute name for instance rotation origin.

#### ATTR_NAME_ROT

```java
public static final  String ATTR_NAME_ROT
```

Shader attribute name for instance rotation sine and cosine.

#### ATTR_NAME_CLIPRECT

```java
public static final  String ATTR_NAME_CLIPRECT
```

Shader attribute name for instance clip rectangle.

#### ATTR_NAME_CLIPENABLED

```java
public static final  String ATTR_NAME_CLIPENABLED
```

Shader attribute name for instance clip-enabled flag.

#### defaultVertexShader

```java
public static String defaultVertexShader()
```

Builds and returns the default vertex shader source used by
`TextureBatch`.

This shader reads the shared quad's local vertex position and UV, applies
per-instance size, origin, rotation, translation, and clip data, then
forwards the computed values to the fragment shader.

The rotation data is passed as precomputed sine and cosine values in
`i_rot`, allowing the CPU side to avoid sending raw angles and the
shader to avoid computing trigonometric functions per vertex.

**Returns:** the default vertex shader source code

#### buildDefaultFragmentShader

```java
public static String buildDefaultFragmentShader(int maxTextureUnits)
```

Builds the default fragment shader source for the batch.

The generated shader declares one sampler uniform per supported texture
unit, applies shader-side clipping when a clip rectangle is active, selects
the correct sampler based on the per-instance texture index, samples the
texture, and multiplies the sampled color by the instance tint color.

The sampler selection is generated as a chained sequence of conditional
branches so the shader can work with an arbitrary number of supported
texture units up to the limit provided by the caller.

- **`maxTextureUnits`** — the number of texture samplers the generated shader should support

**Returns:** the default fragment shader source code

#### buildFragmentPreamble

```java
public static String buildFragmentPreamble(int maxTextureUnits)
```

Builds a reusable fragment shader preamble for custom batch fragment shaders.

This preamble declares the required sampler uniforms and varyings, and also
injects helper functions for:

- sampling the currently selected batch texture via `sampleBatchTexture(vec2 uv)`

- checking whether the current fragment is clipped via `batchClipped()`

This helper is useful when building custom fragment shaders that should still
participate in the same texture-routing and clipping rules used by the default
batch shader.

- **`maxTextureUnits`** — the number of texture samplers the generated preamble should support

**Returns:** a fragment shader preamble string that can be concatenated with custom shader logic

#### bindAttributes

```java
public static void bindAttributes(Shader shader)
```

Binds all batch attribute locations on the supplied shader.

The attribute indices defined in this contract must match the VBO layout
written by `TextureBatch`. This method ensures that the shader uses
the correct fixed attribute locations before it is linked or reloaded.

- **`shader`** — the shader whose attributes should be bound to the batch contract

#### bindSamplers

```java
public static void bindSamplers(Shader shader, int maxTextureUnits)
```

Binds the batch sampler uniforms on the supplied shader.

Each generated sampler uniform is assigned to its matching texture unit
index so that `u_tex0` samples from texture unit 0,
`u_tex1` samples from texture unit 1, and so on.

The shader is temporarily bound while uniforms are assigned and then unbound
before the method returns.

- **`shader`** — the shader whose sampler uniforms should be initialized
- **`maxTextureUnits`** — the number of texture-unit sampler uniforms to bind

</details>

<a id="type-texturebatchshader"></a>

### TextureBatchShader

[Source](../../src/main/java/valthorne/graphics/texture/TextureBatchShader.java#L71)

##### TextureBatchShader

`TextureBatchShader` is a specialized `Shader` implementation designed to work directly
with `TextureBatch`. It automatically applies the attribute contract defined by
`TextureBatchContract`, reloads itself after binding those attribute locations, and binds all
texture sampler uniforms expected by the batch.

This class exists so batch-compatible shaders can be created with minimal boilerplate. Instead of
manually remembering the required attribute names, sampler uniforms, and default fallback shader
sources, you can construct a `TextureBatchShader` and let it normalize everything for you.

##### How fragment source works

The fragment source can be supplied in two different modes:

- If `fullFragmentSource` is `false`, the provided fragment body is treated as a
partial shader body and is prefixed with the standard batch fragment preamble generated by
`TextureBatchContract#buildFragmentPreamble(int)`.

- If `fullFragmentSource` is `true`, the fragment source is treated as a complete,
already-finished GLSL fragment shader and is used exactly as provided.

This makes it easy to either write quick batch-aware effects using helper functions like
`sampleBatchTexture(...)` and `batchClipped()`, or provide a completely custom shader
when full control is needed.

##### Vertex source behavior

If no vertex shader source is supplied, the default batch vertex shader from
`TextureBatchContract#defaultVertexShader()` is used automatically.

##### Typical usage

The most common use case is creating a fragment effect while still using the default batch vertex
shader and batch sampler setup.

##### Example

```java
TextureBatchShader shader = new TextureBatchShader(
    8,
    ShaderSources.load("examples/batch-tint.frag.glsl")
);

TextureBatch batch = new TextureBatch(4096, 8);
batch.begin();
batch.setShader(shader);
batch.draw(texture, 100f, 100f, 64f, 64f);
batch.end();
```

<details>
<summary>TextureBatchShader operation reference (5 declarations)</summary>

#### Constructor

```java
public TextureBatchShader(int maxTextureUnits)
```

Creates a batch-compatible shader using the default batch vertex shader and default batch
fragment shader.

This is the simplest constructor and is useful when you want a shader object that matches the
batch contract exactly without supplying any custom GLSL.

- **`maxTextureUnits`** — the number of texture units the shader should support

#### Constructor

```java
public TextureBatchShader(int maxTextureUnits, String fragmentSource)
```

Creates a batch-compatible shader using the default batch vertex shader and a custom fragment
shader source.

The provided fragment source is treated as a partial fragment body and is automatically prefixed
with the batch fragment preamble unless a different constructor is used with
`fullFragmentSource = true`.

- **`maxTextureUnits`** — the number of texture units the shader should support
- **`fragmentSource`** — the custom fragment source body to append after the batch preamble

#### Constructor

```java
public TextureBatchShader(int maxTextureUnits, String vertexSource, String fragmentSource)
```

Creates a batch-compatible shader using an optional custom vertex shader and an optional custom
fragment shader.

The vertex source falls back to the default batch vertex shader when null or blank. The fragment
source falls back to the default batch fragment shader when null or blank. When a fragment source
is provided here, it is treated as a partial shader body and receives the batch preamble.

- **`maxTextureUnits`** — the number of texture units the shader should support
- **`vertexSource`** — the custom vertex shader source, or null to use the default batch vertex shader
- **`fragmentSource`** — the custom fragment shader body, or null to use the default batch fragment shader

#### Constructor

```java
public TextureBatchShader(int maxTextureUnits, String vertexSource, String fragmentSource, boolean fullFragmentSource)
```

Creates a batch-compatible shader with full control over how the fragment source is interpreted.

This constructor performs the complete setup required for batch compatibility:

- Normalizes the vertex source, falling back to the batch default when necessary

- Normalizes the fragment source, either building a default shader, prefixing the batch
preamble, or using the source exactly as provided

- Binds all required batch attribute locations

- Reloads the shader so the attribute bindings take effect

- Binds all `u_texN` sampler uniforms for the requested texture unit count

- **`maxTextureUnits`** — the number of texture units the shader should support
- **`vertexSource`** — the custom vertex shader source, or null to use the batch default
- **`fragmentSource`** — the fragment shader source or body, depending on `fullFragmentSource`
- **`fullFragmentSource`** — true to treat `fragmentSource` as a complete fragment shader, false to prefix the batch fragment preamble

**Throws `IllegalArgumentException`:** if `maxTextureUnits < 1`

**Throws `IllegalStateException`:** if shader compilation or linking fails during reload

#### getMaxTextureUnits

```java
public int getMaxTextureUnits()
```

Returns the number of texture units this shader was built to support.

This value should match the texture unit configuration expected by the
`TextureBatch` that uses this shader.

**Returns:** the supported texture unit count

</details>

<a id="type-texturedata"></a>

### TextureData

[Source](../../src/main/java/valthorne/graphics/texture/TextureData.java#L41)

Immutable container holding OpenGL texture information and its decoded width/height.

This record acts as a lightweight wrapper around a created OpenGL texture.
It stores the OpenGL-generated texture ID along with the pixel dimensions obtained
from STBImage. No rendering functionality is included\u2014only loading and metadata.

The texture loading pipeline performs the following operations:

- Converts raw bytes into a direct `ByteBuffer`

- Decodes image buffer using `STBImage#stbi_load_from_memory`

- Creates an OpenGL texture via `glGenTextures()`

- Uploads RGBA8 buffer to GPU memory

- Applies texture parameters (min/mag filter, wrap mode)

- Frees decoded image memory

This class always forces the loaded image into 4-channel RGBA format for consistency.
STBImage is configured to vertically flip textures to match OpenGL UV orientation.

- **`buffer`** — the raw binary buffer containing the texture image buffer
- **`width`** — the width of the decoded texture in pixels
- **`height`** — the height of the decoded texture in pixels

<details>
<summary>TextureData operation reference (7 declarations)</summary>

#### load

```java
public static TextureData load(String path)
```

Loads a texture from the specified file path, decodes it, and uploads it to OpenGL.

- **`path`** — the file path to the texture image (e.g., PNG, JPEG)

**Returns:** a new `TextureData` instance containing the GPU texture ID and pixel size

**Throws `RuntimeException`:** if an I/O error occurs or if the image decoding fails

#### load

```java
public static TextureData load(String path, boolean flipVertically)
```

Loads a texture from the specified file path and optionally flips it vertically before decoding.

- **`path`** — the file path to the texture image (e.g., PNG, JPEG)
- **`flipVertically`** — whether to flip the image vertically during decoding

**Returns:** a new `TextureData` instance containing the decoded image data

**Throws `RuntimeException`:** if an I/O error occurs while reading the file

#### load

```java
public static TextureData load(byte[] data)
```

Loads a texture from raw image bytes and decodes it.

- **`data`** — the raw image bytes (e.g., PNG, JPEG)

**Returns:** a new `TextureData` instance containing the decoded image data

#### load

```java
public static TextureData load(byte[] data, boolean flipVertically)
```

Loads a texture from raw image bytes, decodes it, and uploads it to OpenGL.

This method handles:

- ByteBuffer preparation

- STB image decoding

- Texture creation

- Texture parameter configuration

- GL texture upload

- **`data`** — raw PNG/JPEG/etc. bytes

**Returns:** a new `TextureData` containing the GPU texture ID and pixel size

**Throws `RuntimeException`:** if STBImage fails to decode the image

#### asTexture

```java
public Texture asTexture()
```

Converts the `TextureData` instance into a `Texture` object.

**Returns:** a new `Texture` instance created from the current `TextureData`

#### asNinePatchTexture

```java
public NinePatchTexture asNinePatchTexture(int left, int right, int top, int bottom)
```

Converts the current `TextureData` instance into a `NinePatchTexture`.

- **`left`** — the number of pixels to define the left stretchable area
- **`right`** — the number of pixels to define the right stretchable area
- **`top`** — the number of pixels to define the top stretchable area
- **`bottom`** — the number of pixels to define the bottom stretchable area

**Returns:** a new `NinePatchTexture` instance created based on the specified stretchable areas

#### dispose

```java
public void dispose()
```

Releases the native resources associated with the texture data.

This method frees the memory associated with the image buffer to prevent
memory leaks. It should be called when the texture data is no longer needed.

</details>

<a id="type-texturedrawable"></a>

### TextureDrawable

[Source](../../src/main/java/valthorne/graphics/texture/TextureDrawable.java#L16)

A drawable implementation that renders a texture on the screen.

This class allows textures to be positioned and resized before being rendered.
It relies on the functionality of the provided `Texture` instance to manage
its drawing behavior.

<details>
<summary>TextureDrawable operation reference (6 declarations)</summary>

#### Constructor

```java
public TextureDrawable(String path)
```

Creates a new TextureDrawable instance using a file path to a texture.

- **`path`** — the file path to the texture to be used for this drawable

#### Constructor

```java
public TextureDrawable(TextureData data)
```

Creates a new TextureDrawable instance using the specified texture data.

- **`data`** — the `TextureData` object containing the decoded texture and its metadata

#### draw

```java
    public void draw(TextureBatch batch, float x, float y, float width, float height, float regionX, float regionY, float regionWidth, float regionHeight, float originX, float originY, float rotation, Color tint)
```

Delegates a transformed texture subsection to the active batch.
A null texture skips drawing; no resource ownership changes and no batch
lifecycle operations occur in this method.

- **`batch`** — active destination texture batch
- **`x`** — horizontal destination position
- **`y`** — vertical destination position
- **`width`** — destination width in batch units
- **`height`** — destination height in batch units
- **`regionX`** — horizontal source offset in pixels
- **`regionY`** — vertical source offset in pixels
- **`regionWidth`** — source width in pixels
- **`regionHeight`** — source height in pixels
- **`originX`** — horizontal rotation-origin offset in destination units
- **`originY`** — vertical rotation-origin offset in destination units
- **`rotation`** — clockwise rotation in degrees
- **`tint`** — optional tint multiplier, or null for the drawable's default tint

#### getWidth

```java
    public float getWidth()
```

Returns the wrapped texture's current pixel width without applying draw scaling.

**Returns:** source width in pixels

**Throws `NullPointerException`:** if the wrapped texture is null

#### getHeight

```java
    public float getHeight()
```

Returns the wrapped texture's current pixel height without applying draw scaling.

**Returns:** source height in pixels

**Throws `NullPointerException`:** if the wrapped texture is null

#### texture

```java
    public Texture texture()
```

Returns the texture associated with this drawable.

**Returns:** the texture used for rendering

</details>

<a id="type-texturefilter"></a>

### TextureFilter

[Source](../../src/main/java/valthorne/graphics/texture/TextureFilter.java#L23)

Represents the filtering mode applied to an OpenGL texture.

This enum abstracts over the raw OpenGL constants and provides
descriptive names for common filtering behaviors. These modes determine
how OpenGL samples texture pixels when the texture is scaled.

- `NEAREST` \u2014 crisp, blocky, pixel-art style

- `LINEAR` \u2014 smooth, blended scaling

The values stored here are passed directly into
`glTexParameteri(GL_TEXTURE_2D, ...)`.

<details>
<summary>TextureFilter operation reference (10 declarations)</summary>

#### NEAREST

```java
public static final  TextureFilter NEAREST
```

Nearest-neighbor filtering.

Uses the closest pixel when scaling the texture. Produces a sharp,
pixelated look. Ideal for retro art, voxel textures, or crisp UI.

#### LINEAR

```java
public static final  TextureFilter LINEAR
```

Linear filtering.

Interpolates between adjacent pixels, producing a smoother image.
Best for modern graphics, high-res textures, and UI widgets.

#### NEAREST_MIPMAP_NEAREST

```java
public static final  TextureFilter NEAREST_MIPMAP_NEAREST
```

Nearest filtering with mipmaps.

Uses nearest neighbor sampling and nearest mipmap level.
Reduces aliasing at distance while keeping a pixel-art look.

#### NEAREST_MIPMAP_LINEAR

```java
public static final  TextureFilter NEAREST_MIPMAP_LINEAR
```

Nearest filtering with linear mipmap blending.

Sharp pixels with smoother transitions between mip levels.

#### LINEAR_MIPMAP_NEAREST

```java
public static final  TextureFilter LINEAR_MIPMAP_NEAREST
```

Linear filtering with nearest mipmap selection.

#### LINEAR_MIPMAP_LINEAR

```java
public static final  TextureFilter LINEAR_MIPMAP_LINEAR
```

Linear filtering with linear mipmap blending.

The highest quality standard OpenGL filtering.

#### minFilter

```java
public final int minFilter
```

OpenGL minification filter, including mipmap selection where applicable.

#### magFilter

```java
public final int magFilter
```

OpenGL magnification filter; always nearest or linear.

#### usesMipmaps

```java
public boolean usesMipmaps()
```

Checks whether the minification filter selects a mipmapped OpenGL mode. It does not inspect a texture or generate missing mip levels.

**Returns:** true if this filter requires mipmaps

#### isPixelPerfect

```java
public boolean isPixelPerfect()
```

Checks whether magnification uses nearest-neighbor sampling. This predicate alone does not guarantee pixel alignment, integer scaling, or nearest sampling during minification.

**Returns:** true if this filter is pixel-perfect

</details>

<a id="type-textureloader"></a>

### TextureLoader

[Source](../../src/main/java/valthorne/graphics/texture/TextureLoader.java#L17)

TextureLoader is responsible for loading texture data based on given parameters. It implements
the `AssetLoader` interface and supports loading textures from both filesystem paths and
raw byte arrays.

The loading behavior is determined by the `TextureParameters`, which specify
the source of the texture and other configurations such as whether the image should
be flipped vertically during decoding.
The result is decoded pixel data; GPU texture creation is a separate operation.

<details>
<summary>TextureLoader operation reference (1 declarations)</summary>

#### load

```java
    public TextureData load(TextureParameters parameters)
```

Decodes the selected image source with the requested vertical orientation.
Byte-backed sources provide a defensive copy to the decoder. The returned
data has its own lifetime and must be released when no longer required.

- **`parameters`** — image source and vertical-flip setting

**Returns:** newly decoded texture data

**Throws `NullPointerException`:** if parameters is null

**Throws `IllegalStateException`:** if the source type is unsupported

</details>

<a id="type-texturepacker"></a>

### TexturePacker

[Source](../../src/main/java/valthorne/graphics/texture/TexturePacker.java#L52)

CPU-side texture atlas builder that copies pixels from existing `TextureData`, `Texture`,
or `TextureRegion` sources into a new RGBA8 atlas buffer.

##### Example

```java
// Suppose you already have CPU-side data for several textures/regions.
Texture player = new Texture("assets/player.png");       // Must have CPU pixels via getData()
Texture ui     = new Texture("assets/ui.png");

TexturePacker packer = new TexturePacker(1024, 1024);

// Copy a raw rectangle from a texture into the atlas at (0,0).
packer.addRegion(player,  0, 0, 128, 128,   0,   0);

// Copy a TextureRegion into the atlas at (256,0).
TextureRegion button = new TextureRegion(ui, 16, 16, 64, 32);
packer.addRegion(button, 256, 0);

// Bake CPU atlas pixels.
TextureData atlasData = packer.bake();

// Upload to GPU (your rendering layer responsibility).
Texture atlas = new Texture(atlasData);
```

##### Coordinate expectations

- All region coordinates (sx, sy, dx, dy) are interpreted as **top-left origin** in pixel space.

- Internally, the pixel buffers are treated as **bottom-up** (common in OpenGL-facing pipelines),
so a conversion is performed when reading/writing.

##### What this class does not do

- No packing algorithm. You must decide placement (dx/dy) yourself.

- No OpenGL calls. This only produces a `TextureData` atlas in CPU memory.

<details>
<summary>TexturePacker operation reference (6 declarations)</summary>

#### Constructor

```java
public TexturePacker(int width, int height)
```

Creates a texture packer that produces an atlas with a fixed final size.

This does not allocate the atlas buffer immediately. Allocation happens in `bake()`.

- **`width`** — atlas width in pixels (must be > 0)
- **`height`** — atlas height in pixels (must be > 0)

**Throws `IllegalArgumentException`:** if width or height is &lt;= 0

#### addRegion

```java
public void addRegion(TextureData src, int sx, int sy, int sw, int sh, int dx, int dy)
```

Queues a rectangular region copy from a source `TextureData` into the atlas.

All coordinates are interpreted as if (0,0) is the **top-left** of the image in pixel space.

This method only records the request. The actual pixel copying occurs when `bake()` is called.

- **`src`** — source CPU pixels (RGBA8) (must be non-null)
- **`sx`** — source X in pixels (top-left origin)
- **`sy`** — source Y in pixels (top-left origin)
- **`sw`** — region width in pixels (must be > 0)
- **`sh`** — region height in pixels (must be > 0)
- **`dx`** — destination X in pixels within the atlas (top-left origin)
- **`dy`** — destination Y in pixels within the atlas (top-left origin)

**Throws `NullPointerException`:** if src is null

**Throws `IllegalArgumentException`:** if sw/sh &lt;= 0

#### addRegion

```java
public void addRegion(Texture src, int sx, int sy, int sw, int sh, int dx, int dy)
```

Queues a rectangular region copy from a `Texture` into the atlas.

This is a convenience overload that uses `Texture#getData()` as the CPU-side pixel source.

- **`src`** — source texture (must be non-null)
- **`sx`** — source X in pixels (top-left origin)
- **`sy`** — source Y in pixels (top-left origin)
- **`sw`** — region width in pixels (must be > 0)
- **`sh`** — region height in pixels (must be > 0)
- **`dx`** — destination X in pixels within the atlas (top-left origin)
- **`dy`** — destination Y in pixels within the atlas (top-left origin)

**Throws `NullPointerException`:** if src is null

**Throws `IllegalArgumentException`:** if sw/sh &lt;= 0

#### addRegion

```java
public void addRegion(Texture texture, int dx, int dy)
```

Queues an entire `Texture` copy into the atlas at (dx,dy).

This copies the full texture rectangle: (0,0) to (width,height) in top-left pixel space.

- **`texture`** — source texture (must be non-null)
- **`dx`** — destination X in pixels within the atlas (top-left origin)
- **`dy`** — destination Y in pixels within the atlas (top-left origin)

**Throws `NullPointerException`:** if texture is null

#### addRegion

```java
public void addRegion(TextureRegion region, int dx, int dy)
```

Queues an entire `TextureRegion` copy into the atlas at (dx,dy).

The region rectangle is taken from the region's pixel fields:
`TextureRegion#getRegionX()`, `TextureRegion#getRegionY()`,
`TextureRegion#getRegionWidth()`, `TextureRegion#getRegionHeight()`.

- **`region`** — region to copy (must be non-null)
- **`dx`** — destination X in pixels within the atlas (top-left origin)
- **`dy`** — destination Y in pixels within the atlas (top-left origin)

**Throws `NullPointerException`:** if region is null, or if its backing texture is null

#### bake

```java
public TextureData bake()
```

Builds the final atlas into a new `TextureData` (RGBA8).

Implementation details:

- Allocates a `finalWidth * finalHeight * 4` RGBA8 `ByteBuffer`.

- Initializes it to fully transparent (0,0,0,0).

- Applies each queued `RegionRequest` by copying pixels row-by-row.

Bounds behavior:

- Requests that lie partially outside the source or destination are **clipped**.

- Requests that do not overlap the atlas after clipping become a no-op.

**Returns:** new `TextureData` containing the atlas pixels

</details>

<a id="type-texturepacker-regionrequest"></a>

### TexturePacker.RegionRequest — internal support type

[Source](../../src/main/java/valthorne/graphics/texture/TexturePacker.java#L291)

Immutable description of one region copy request.

This is a deferred CPU pixel-copy request, not a GPU texture allocation. The source
pixel buffer must remain readable until the packer performs the copy; source and
destination coordinates both use a top-left pixel origin.

- **`src`** — source texture pixels
- **`sx`** — source X (top-left origin)
- **`sy`** — source Y (top-left origin)
- **`sw`** — width to copy
- **`sh`** — height to copy
- **`dx`** — destination X in atlas (top-left origin)
- **`dy`** — destination Y in atlas (top-left origin)

<a id="type-textureparameters"></a>

### TextureParameters

[Source](../../src/main/java/valthorne/graphics/texture/TextureParameters.java#L25)

Represents configuration parameters for loading a texture asset.
A texture can be sourced either from a file system path or in-memory bytes,
is identified by a unique name, and can optionally be vertically flipped during loading.

This record implements the `AssetParameters` interface, allowing it to
define a unique identifier for managing texture assets.

Only name supplies the shared asset-cache key; source and flip setting
do not distinguish cached requests. Use different names for variants that must
coexist. Path factories defer file reads, byte factories defensively copy input,
and classpath factories read the resource immediately before wrapping its bytes.

- **`source`** — validated path or encoded-byte source
- **`name`** — nonblank cache identity
- **`flipVertically`** — whether decoding reverses image row order

<details>
<summary>TextureParameters operation reference (9 declarations)</summary>

#### Constructor

```java
public TextureParameters
```

Constructs a TextureParameters instance with the specified texture source, name, and flip option.
Validates the input arguments to ensure the source and name are not null or invalid.

- **`source`** — the source of the texture, must not be null. It can represent either a file path or in-memory byte source for the texture.
- **`name`** — the unique name of the texture. Must not be null or blank as it is used as the identifier for the texture asset.
- **`flipVertically`** — a boolean indicating if the texture should be flipped vertically during loading. This is typically helpful for certain graphical conventions or requirements.

**Throws `IllegalArgumentException`:** if source is null or if name is null/blank.

#### fromPath

```java
public static TextureParameters fromPath(String path)
```

Creates a new instance of `TextureParameters` using a file system path as the texture source.
The texture is automatically assigned the path as its name and is configured to be vertically flipped during loading.

- **`path`** — the file system path of the texture. Must not be null or blank.

**Returns:** a `TextureParameters` instance configured with the specified path as the texture source and name.

**Throws `IllegalArgumentException`:** if the `path` is null or blank.

#### fromPath

```java
public static TextureParameters fromPath(String path, String name)
```

Creates a new instance of `TextureParameters` using a file system path as the texture source.
The texture is configured with the specified name and is set to be vertically flipped during loading.

- **`path`** — the file system path of the texture. Must not be null or blank.
- **`name`** — the unique name of the texture. Must not be null or blank.

**Returns:** a `TextureParameters` instance configured with the specified path as the texture source
and the specified name.

**Throws `IllegalArgumentException`:** if the `path` is null or blank, or if the `name` is null or blank.

#### fromPath

```java
public static TextureParameters fromPath(String path, String name, boolean flipVertically)
```

Creates a new instance of `TextureParameters` using a file system path as the texture source.
The texture is configured with the specified name and the specified flip vertically option during loading.

- **`path`** — the file system path of the texture. Must not be null or blank.
- **`name`** — the unique name of the texture. Must not be null or blank.
- **`flipVertically`** — a boolean indicating whether the texture should be flipped vertically during loading.

**Returns:** a `TextureParameters` instance configured with the specified path as the texture source,
the specified name, and the specified flip vertically option.

**Throws `IllegalArgumentException`:** if the `path` is null or blank, or if the `name` is null or blank.

#### fromBytes

```java
public static TextureParameters fromBytes(byte[] bytes, String name)
```

Creates a new `TextureParameters` instance from raw, encoded image bytes.
The texture is configured with the specified name and is set to be vertically flipped by default.

- **`bytes`** — the raw encoded image bytes for the texture (e.g., PNG, JPG). Must not be null or empty.
- **`name`** — the unique name of the texture. Must not be null or blank.

**Returns:** a `TextureParameters` instance configured with the given byte source and name.

**Throws `IllegalArgumentException`:** if `bytes` is null or empty, or if `name` is null or blank.

#### fromBytes

```java
public static TextureParameters fromBytes(byte[] bytes, String name, boolean flipVertically)
```

Creates a new `TextureParameters` instance from raw encoded image bytes.
The texture is configured with the specified name and the specified flip vertically option.

- **`bytes`** — the raw encoded image bytes for the texture (e.g., PNG, JPG). Must not be null or empty.
- **`name`** — the unique name of the texture. Must not be null or blank.
- **`flipVertically`** — a boolean indicating whether the texture should be flipped vertically during loading.

**Returns:** a `TextureParameters` instance configured with the given byte source, name, and flip vertically option.

**Throws `IllegalArgumentException`:** if `bytes` is null or empty, or if `name` is null or blank.

#### fromClasspath

```java
public static TextureParameters fromClasspath(String resourcePath, String name)
```

Creates a new `TextureParameters` instance from a resource located on the classpath.
The texture is configured with the specified resource name and is set to be vertically flipped by default.

- **`resourcePath`** — the path to the resource on the classpath. Must not be null or blank.
- **`name`** — the unique name of the texture. Must not be null or blank.

**Returns:** a `TextureParameters` instance configured with the specified classpath resource and name.

**Throws `IllegalArgumentException`:** if `resourcePath` is null or blank, or if `name` is null or blank.

**Throws `valthorne.io.file.ValthorneFileException`:** if the classpath resource
is missing or cannot be read; resource bytes are read synchronously

#### fromClasspath

```java
public static TextureParameters fromClasspath(String resourcePath, String name, boolean flipVertically)
```

Creates a new `TextureParameters` instance from a resource located on the classpath.
The texture is configured with the specified resource name and the specified flip vertically option.

- **`resourcePath`** — the path to the resource on the classpath. Must not be null or blank.
- **`name`** — the unique name of the texture. Must not be null or blank.
- **`flipVertically`** — a boolean indicating whether the texture should be flipped vertically during loading.

**Returns:** a `TextureParameters` instance configured with the specified classpath resource, name, and flip vertically option.

**Throws `IllegalArgumentException`:** if `resourcePath` is null or blank, or if `name` is null or blank.

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

<a id="type-textureregion"></a>

### TextureRegion

[Source](../../src/main/java/valthorne/graphics/texture/TextureRegion.java#L74)

`TextureRegion` represents a rectangular subsection of a `Texture`.
Instead of rendering an entire texture, this class allows a specific portion
of the texture to be referenced and used for rendering. This is commonly used
with sprite sheets, texture atlases, tile maps, and UI element collections
where multiple images are stored within a single texture.

The region is defined using pixel coordinates relative to the underlying
texture. The class then exposes both pixel-space values and normalized
texture coordinates (`u, v`) used by OpenGL rendering.

A `TextureRegion` does not allocate GPU resources and does not perform
rendering itself. It simply describes a subsection of a texture that can be
used by higher-level rendering types such as:

- `Sprite`

- `NinePatchTexture`

- `TextureBatch`

Regions can be freely modified after creation. Changing the region updates
the pixel bounds used to compute UV coordinates but does not affect the
underlying texture.

##### Example Usage

```java
Texture texture = new Texture("spritesheet.png");

TextureRegion playerIdle = new TextureRegion(texture, 0, 0, 32, 32);
TextureRegion playerWalk = new TextureRegion(texture, 32, 0, 32, 32);

float u = playerIdle.getU();
float v = playerIdle.getV();
float u2 = playerIdle.getU2();
float v2 = playerIdle.getV2();

playerWalk.setRegion(64, 0, 32, 32);

TextureBatch batch = new TextureBatch(1000);
batch.begin();
batch.drawRegion(playerIdle.getTexture(), 100, 100,
                 playerIdle.getRegionWidth(),
                 playerIdle.getRegionHeight(),
                 playerIdle.getRegionX(),
                 playerIdle.getRegionY(),
                 playerIdle.getRegionWidth(),
                 playerIdle.getRegionHeight());
batch.end();
```

This example demonstrates creating regions from a sprite sheet, modifying
their coordinates, retrieving normalized UV values, and using them during
rendering.

<details>
<summary>TextureRegion operation reference (18 declarations)</summary>

#### Constructor

```java
public TextureRegion(Texture texture)
```

Creates a `TextureRegion` that represents the entire texture.

The region bounds are initialized to match the full width and height
of the provided texture.

- **`texture`** — the texture backing this region

#### Constructor

```java
public TextureRegion(Texture texture, float regionX, float regionY, float regionWidth, float regionHeight)
```

Creates a `TextureRegion` describing a subsection of a texture.

The supplied values define the rectangular area in pixel coordinates
relative to the texture.

- **`texture`** — the texture backing this region
- **`regionX`** — the X coordinate of the region in pixels
- **`regionY`** — the Y coordinate of the region in pixels
- **`regionWidth`** — the width of the region in pixels
- **`regionHeight`** — the height of the region in pixels

**Throws `NullPointerException`:** if `texture` is `null`

#### getTexture

```java
public Texture getTexture()
```

Returns the texture associated with this region.

**Returns:** the underlying texture

#### getRegionX

```java
public float getRegionX()
```

Returns the region's X coordinate in pixels.

**Returns:** the region X position

#### setRegionX

```java
public void setRegionX(float regionX)
```

Sets the region's X coordinate.

- **`regionX`** — the new region X position in pixels

#### getRegionY

```java
public float getRegionY()
```

Returns the region's Y coordinate in pixels.

**Returns:** the region Y position

#### setRegionY

```java
public void setRegionY(float regionY)
```

Sets the region's Y coordinate.

- **`regionY`** — the new region Y position in pixels

#### getRegionWidth

```java
public float getRegionWidth()
```

Returns the region width in pixels.

**Returns:** the region width

#### setRegionWidth

```java
public void setRegionWidth(float regionWidth)
```

Sets the region width.

- **`regionWidth`** — the new region width in pixels

#### getRegionHeight

```java
public float getRegionHeight()
```

Returns the region height in pixels.

**Returns:** the region height

#### setRegionHeight

```java
public void setRegionHeight(float regionHeight)
```

Sets the region height.

- **`regionHeight`** — the new region height in pixels

#### setRegion

```java
public void setRegion(float regionX, float regionY, float regionWidth, float regionHeight)
```

Updates the full region bounds at once.

This method replaces the X, Y, width, and height values in a single call.

- **`regionX`** — the new region X coordinate
- **`regionY`** — the new region Y coordinate
- **`regionWidth`** — the new region width
- **`regionHeight`** — the new region height

#### getRight

```java
public float getRight()
```

Returns the right edge of the region in pixel space.

**Returns:** the right coordinate

#### getBottom

```java
public float getBottom()
```

Returns the bottom edge of the region in pixel space.

**Returns:** the bottom coordinate

#### getU

```java
public float getU()
```

Returns the normalized U coordinate for the left side of the region.

This value is calculated by dividing the region's X position by the
full texture width.

**Returns:** the normalized U coordinate

#### getV

```java
public float getV()
```

Returns the normalized V coordinate for the top side of the region.

This value is calculated by dividing the region's Y position by the
full texture height.

**Returns:** the normalized V coordinate

#### getU2

```java
public float getU2()
```

Returns the normalized U coordinate for the right side of the region.

**Returns:** the normalized U2 coordinate

#### getV2

```java
public float getV2()
```

Returns the normalized V coordinate for the bottom side of the region.

**Returns:** the normalized V2 coordinate

</details>

<a id="type-textureregiondrawable"></a>

### TextureRegionDrawable

[Source](../../src/main/java/valthorne/graphics/texture/TextureRegionDrawable.java#L18)

A drawable implementation that allows rendering a `TextureRegion` to a specified
position and size. This class wraps a `TextureRegion` and provides a mechanism
to draw it with given dimensions and coordinates.

The primary usage involves setting the position and size of the texture region
based on the provided parameters, then delegating the actual drawing process
to the underlying `TextureRegion`.

<details>
<summary>TextureRegionDrawable operation reference (6 declarations)</summary>

#### Constructor

```java
public TextureRegionDrawable(Texture texture, float regionX, float regionY, float regionWidth, float regionHeight)
```

Creates a region wrapper over the supplied texture without allocating a new
GPU texture. The region uses source pixel coordinates; the caller remains
responsible for the backing texture's lifetime.

- **`texture`** — backing texture
- **`regionX`** — horizontal source offset in pixels
- **`regionY`** — vertical source offset in pixels
- **`regionWidth`** — source width in pixels
- **`regionHeight`** — source height in pixels

#### Constructor

```java
public TextureRegionDrawable
```

Creates a new `TextureRegionDrawable` that wraps the specified
`TextureRegion`, allowing it to be drawn with specified dimensions
and coordinates.

- **`region`** — the `TextureRegion` to be wrapped and rendered by this drawable; must not be null

#### draw

```java
    public void draw(TextureBatch batch, float x, float y, float width, float height, float regionX, float regionY, float regionWidth, float regionHeight, float originX, float originY, float rotation, Color tint)
```

Delegates a transformed region-relative subsection to the active batch.
A null region skips drawing; no resource ownership changes and no batch
lifecycle operations occur in this method.

- **`batch`** — active destination texture batch
- **`x`** — horizontal destination position
- **`y`** — vertical destination position
- **`width`** — destination width in batch units
- **`height`** — destination height in batch units
- **`regionX`** — horizontal source offset in pixels
- **`regionY`** — vertical source offset in pixels
- **`regionWidth`** — source width in pixels
- **`regionHeight`** — source height in pixels
- **`originX`** — horizontal rotation-origin offset in destination units
- **`originY`** — vertical rotation-origin offset in destination units
- **`rotation`** — clockwise rotation in degrees
- **`tint`** — optional tint multiplier, or null for the drawable's default tint

#### getWidth

```java
    public float getWidth()
```

Returns the wrapped region's current pixel width without applying draw scaling.

**Returns:** source width in pixels

**Throws `NullPointerException`:** if the wrapped region is null

#### getHeight

```java
    public float getHeight()
```

Returns the wrapped region's current pixel height without applying draw scaling.

**Returns:** source height in pixels

**Throws `NullPointerException`:** if the wrapped region is null

#### region

```java
    public TextureRegion region()
```

Returns the `TextureRegion` wrapped by this drawable. The `TextureRegion`
represents the rectangular portion of a texture being drawn.

**Returns:** the `TextureRegion` associated with this drawable

</details>

<a id="type-texturesource"></a>

### TextureSource

[Source](../../src/main/java/valthorne/graphics/texture/TextureSource.java#L14)

Represents a source for a texture, which can either be loaded from a file path or from raw image bytes.
This interface is sealed, allowing only specific implementations for texture sources.
Sources describe encoded data without decoding it or allocating graphics resources.
Byte sources use defensive copies on input and access; path sources validate text
only and defer resource existence checks until loading.

<a id="type-texturesource-pathsource"></a>

### TextureSource.PathSource

[Source](../../src/main/java/valthorne/graphics/texture/TextureSource.java#L24)

Immutable description of an encoded image file to be loaded later.
Construction validates nonblank path text without checking existence or image
format. No decoding or OpenGL allocation occurs until the source is loaded.

- **`path`** — nonblank image path, retained unchanged

<details>
<summary>TextureSource.PathSource operation reference (1 declarations)</summary>

#### Constructor

```java
public PathSource
```

Records a nonblank path without resolving or opening it. Whitespace is
checked for emptiness but the original path text is retained unchanged.

- **`path`** — resource path to retain

**Throws `IllegalArgumentException`:** if path is null or blank

</details>

<a id="type-texturesource-bytessource"></a>

### TextureSource.BytesSource

[Source](../../src/main/java/valthorne/graphics/texture/TextureSource.java#L46)

Immutable encoded-image source that copies bytes on construction and access.
The byte array must be nonempty, but image-format validity is deferred to loading.
Every bytes() call returns a separate copy; this source owns no decoded pixels
or OpenGL texture handle.

- **`bytes`** — nonempty encoded image data

<details>
<summary>TextureSource.BytesSource operation reference (2 declarations)</summary>

#### Constructor

```java
public BytesSource
```

Copies encoded texture bytes so later changes to the input cannot change
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

<a id="type-textureutility"></a>

### TextureUtility

[Source](../../src/main/java/valthorne/graphics/texture/TextureUtility.java#L24)

CPU helpers for extracting a simplified pixel contour and dividing a texture
into grid regions. Contour tracing reads retained RGBA bytes, selects the largest
four-connected nontransparent component, and returns its largest boundary loop
after collinear and 0.75-pixel Ramer-Douglas-Peucker simplification. Holes and
smaller disconnected components are not returned.

Tracing does not read GPU pixels: textures without retained TextureData produce
an empty contour. Returned points lie on pixel edges in the source buffer's
coordinate orientation; region traces are local to the extracted region.
Texture splitting creates borrowed views without copying pixels or owning the
source texture.

<details>
<summary>TextureUtility operation reference (3 declarations)</summary>

#### trace

```java
public static Vector2f[] trace(Texture texture, Color... ignore)
```

Traces the outer boundary of the largest four-connected solid pixel component.
Alpha-zero pixels are always excluded; ignore colors match all four byte channels
exactly. Retained data must be readable, tightly packed RGBA. The returned loop
does not repeat its closing point and is simplified with 0.75-pixel tolerance.

- **`texture`** — source with optional retained CPU data
- **`ignore`** — exact colors to exclude; null array or entries have no effect

**Returns:** independent pixel-edge points, or an empty array for absent data, nonpositive dimensions, or no boundary

**Throws `NullPointerException`:** if texture is null

#### trace

```java
public static Vector2f[] trace(TextureRegion textureRegion, Color... ignore)
```

Copies a region's retained RGBA pixels and traces its largest solid component.
Region coordinates and dimensions are truncated to integers. Returned points
are relative to the region origin, without adding the atlas offset or applying
a UV flip; the closing point is omitted.

- **`textureRegion`** — source region with valid bounds inside retained pixel data
- **`ignore`** — exact RGBA colors to exclude in addition to transparent pixels

**Returns:** simplified region-local boundary, or empty when data or contour is absent

**Throws `NullPointerException`:** if textureRegion is null

**Throws `IndexOutOfBoundsException`:** if region bounds exceed readable source data

#### split

```java
public static TextureRegion[][] split(Texture texture, int rows, int columns)
```

Divides a texture into equal integer-sized borrowed regions indexed by row then
column. Row zero uses the highest Y interval, and columns increase from X zero.
Integer division discards remainder columns at the right and remainder rows
at the low-Y edge. Too many rows or columns can produce zero-sized regions.

- **`texture`** — nonnull source texture
- **`rows`** — positive row count
- **`columns`** — positive column count

**Returns:** independent region grid sharing the source texture

**Throws `NullPointerException`:** if texture is null

**Throws `IllegalArgumentException`:** if rows or columns are nonpositive

</details>

<a id="type-textureutility-point"></a>

### TextureUtility.Point — internal support type

[Source](../../src/main/java/valthorne/graphics/texture/TextureUtility.java#L630)

Immutable integer pixel-edge coordinate used as a graph key during boundary
stitching. Equality and hashing compare coordinate values.

Pixel-edge coordinates identify corners between image pixels rather than pixel-center
samples. Value equality lets independently created boundary segments meet at a
shared graph vertex.

<details>
<summary>TextureUtility.Point operation reference (2 declarations)</summary>

#### equals

```java
        public boolean equals(Object obj)
```

Compares coordinate values with another internal point.

- **`obj`** — candidate point

**Returns:** true for matching X and Y coordinates

#### hashCode

```java
        public int hashCode()
```

Combines coordinate values consistently with equality for boundary lookup.

**Returns:** coordinate hash

</details>

<a id="type-textureutility-edge"></a>

### TextureUtility.Edge — internal support type

[Source](../../src/main/java/valthorne/graphics/texture/TextureUtility.java#L680)

Directed pixel-boundary segment with a mutable traversal marker. Endpoints
are immutable point values; stitching consumes each edge at most once.

Direction preserves the orientation of the extracted pixel outline. The used flag
belongs to one stitching traversal and does not alter endpoint coordinates or
change the source image.

## Related guides

- [Asset loading and caching](assets.md)
- [Shaders and visual effects](shaders.md)
- [Viewport scaling and coordinate conversion](viewports.md)
- [Standard UI controls](ui-controls.md)
