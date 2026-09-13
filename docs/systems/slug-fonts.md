# Slug vector fonts

Author: Albert Beaupre

[System manual](README.md)

## Purpose

Slug renders font outlines from curve and band textures rather than storing only a bitmap glyph image. `SlugFont` prepares shared curve data, `SlugBatch` submits glyph instances, and `SlugTextRun` retains laid-out glyph positions for text drawn repeatedly.

## Features and when to use them

| Feature | Purpose |
| --- | --- |
| Curve coverage | The fragment program evaluates outline coverage from the font's curve data. |
| Instanced glyphs | The batch expands compact glyph records into quads. |
| Reusable runs | Precompute glyph placement, tabs, line breaks, and kerning for stable text. |
| Scale and color | A run or glyph uses world units per em and per-draw color. |
| UI text nodes | SlugLabel retains layout under ordinary or NanoVG containers and borrows a shared font and batch. |
| CPU rejection and counters | Whole-run and glyph bounds avoid submissions outside compatible viewport/clip bounds; counters distinguish accepted glyphs from actual draws. |
| Scoped GL state | Batch end or cancel restores the GL state it changed; texture uploads also restore pixel-unpack and texture-binding state. |

## Getting started

1. Create the SlugFont and batch on the graphics owner thread.
2. Create a text run for a stable string, or use the direct drawing entry points for changing text.
3. Begin with the projection and current viewport pixel dimensions, draw runs, and end. Call cancel in a finally block so failed drawing callbacks restore captured state.
4. Rebuild runs when text or size changes; release batches and fonts after dependent runs stop drawing.

## Ownership and lifecycle

Text runs borrow their font. Their layout cache does not keep disposed GPU curve textures alive. This path still draws curves every frame; a run is not a cached output image.

## Optimized retained text and mixed UI

Use `font.createRun(text, size)` for repeated text. `rebuild` skips unchanged content and
reuses arrays when changed content fits their retained capacity. Sizes must be finite
and nonnegative; empty text measures zero. Runs cache ink bounds for whole-run culling;
individual glyphs are rejected or clipped before instance upload. Array capacity follows
the largest text seen by that run; release the run to release that retained storage.

`SlugLabel` is a regular UI node and works beneath either texture or NanoVG containers:

```java
SlugBatch textBatch = new SlugBatch(2048); // share across labels
SlugLabel title = new SlugLabel(font, textBatch, "Curve-rendered UI", 28);
panel.add(title); // regular or NanoVG panel, managed by UIRoot
```

Import `valthorne.ui.nodes.SlugLabel`. The label honors the active texture batch's
translation and clip, uses the current projection and framebuffer viewport, and flushes
at renderer boundaries to preserve painter order. Text/size setters update intrinsic
layout dimensions. Font and batch are borrowed: dispose them on the graphics thread
after the UI is finished. Do not manually begin the shared Slug batch while drawing labels.
For large homogeneous text passes, draw multiple runs within one begin/end scope to
amortize state capture and avoid a draw boundary per label.

`SlugBatch.setClip` applies a world-coordinate rectangle to subsequent glyphs;
`clearClip` removes it. It does not change hardware scissor or stencil. Axis-aligned
projections use viewport-aware coverage and culling; rotated/nonuniform projections
use derivative coverage. Supply the actual viewport's **pixel** dimensions, not its
logical UI size. This remains a 2D overlay renderer, not depth-tested 3D text: depth
testing/writes and face culling are temporarily disabled. Program, vertex/buffer bindings,
texture units 0/1, samplers, blend settings, depth and cull state are restored by `end`
or `cancel`; the framebuffer, viewport, scissor and stencil are left untouched.

`getGlyphsSubmitted()` counts accepted glyph instances since begin (including ones
subsequently cancelled); `getDrawCalls()` counts actual batch draws. Disposed fonts
cannot be submitted, and batch disposal is idempotent. Font uploads preserve texture
and pixel-unpack/PBO state. None of these objects is thread-safe.

See [benchmark results and reproduction](../benchmarks/slug-2026-09-10/README.md).

## Retained UI label reference

See [SlugLabel and its complete operation contracts](ui-controls.md#type-sluglabel) for construction, text and size changes, tint ownership, and UI lifecycle integration.

## Important behavior

- Keep pixel dimensions supplied to the batch consistent with the active viewport.
- Font loading and layout coverage are limited to the characters and parsing supported by the implementation.
- Use the bitmap path when atlas rendering is the intended tradeoff.
- `setClip` uses glyph world coordinates, rejects outside glyphs, and crops intersecting quads and their em-space coordinates before upload. It does not change hardware scissor state or clip previously queued geometry.
- Unchanged normalized text and size reuse run layout. A font convenience draw retains one run; use separate SlugTextRun objects for multiple independently retained strings.
- The batch captures program, vertex/buffer bindings, texture/sampler units zero and one, active texture unit, depth/cull state, depth mask, and separate blend state. It must be used on the graphics context thread and must not be nested inside another pass on the same batch.
- `getGlyphsSubmitted()` and `getDrawCalls()` reset at begin. Font changes and capacity limits may trigger multiple draws, while rejected glyphs do not count as accepted submissions.

## Components and examples

The sections below explain each component and its declared public or protected operations. Expand an operation reference for signatures, parameters, results, and edge cases. Inherited operations are documented with their base type. Examples embedded in component descriptions are integration fragments: supply the surrounding resources and application variables they name.

- [`SlugBatch`](#type-slugbatch)
- [`SlugCurve`](#type-slugcurve)
- [`SlugFont`](#type-slugfont)
- [`SlugGlyph`](#type-slugglyph)
- [`SlugTextRun`](#type-slugtextrun)

<a id="type-slugbatch"></a>

### SlugBatch

[Source](../../src/main/java/valthorne/graphics/font/slug/SlugBatch.java#L56)

Fast 2D instanced batch renderer for `SlugFont` glyphs.

This path is optimized for live font rendering in a normal orthographic 2D pass. It removes
the expensive Slug reference vertex dilation math and applies a small CPU-side pad to every glyph
quad instead. That keeps the fragment shader from clipping antialiasing while making the vertex
shader much cheaper. This pass also sends the pixels-per-em value as a flat instance
attribute so the fragment shader does not need to call fwidth() for ordinary 2D text.

Use one graphics context thread. Begin captures the GL state changed by this
batch; end flushes pending instances and restores it, while cancel restores it
without drawing pending work. Clip and viewport bounds reject outside glyphs
and crop intersecting glyph quads on the CPU without changing hardware scissor state.

```java
SlugTextRun run = font.createRun("Ready", 24f);
batch.begin(projection, viewportWidth, viewportHeight);
try {
    run.draw(batch, 20f, 40f, Color.WHITE);
    batch.end();
} finally {
    batch.cancel();
}
```

<details>
<summary>SlugBatch operation reference (16 declarations)</summary>

#### setClip

```java
public void setClip(float x, float y, float width, float height)
```

Restricts subsequently submitted glyphs to intersection with a world-space rectangle.
Outside glyphs are rejected; intersecting quads and their em-space coordinates
are cropped before upload. Hardware scissor state and existing queued glyphs
are unchanged. The rectangle follows world axes, including under rotated projections.

- **`x`** — rectangle left coordinate in glyph world units
- **`y`** — rectangle lower coordinate in glyph world units
- **`width`** — finite nonnegative rectangle width
- **`height`** — finite nonnegative rectangle height

**Throws `IllegalArgumentException`:** if any component is nonfinite or a dimension is negative

#### clearClip

```java
public void clearClip()
```

Removes the optional CPU clip rectangle for subsequent submissions. Viewport
rejection still applies when begin can derive bounds from the projection.

#### getGlyphsSubmitted

```java
public int getGlyphsSubmitted()
```

Reads accepted glyph submissions since the latest begin, including flushed glyphs.

**Returns:** accepted instance count for the current or most recently completed pass

#### getDrawCalls

```java
public int getDrawCalls()
```

Reads actual nonempty batch draws since the latest begin; font changes and capacity
flushes can make this exceed one even during a single text pass.

**Returns:** draw-call count for the current or most recently completed pass

#### Constructor

```java
public SlugBatch()
```

Creates a Slug batch with a default capacity of 4096 glyphs.

#### Constructor

```java
public SlugBatch(int maxGlyphs)
```

Creates a Slug batch with a custom glyph capacity.

- **`maxGlyphs`** — maximum glyph instances queued before flush

#### begin

```java
public void begin(Matrix4f mvp, float viewportW, float viewportH)
```

Begins collecting Slug glyph draw calls.

Viewport dimensions must be the active framebuffer viewport's pixel dimensions.
Axis-aligned projections enable CPU culling and pixel-correct coverage. Other
projections use derivative-based coverage without viewport CPU culling.
This is an overlay renderer: depth testing/writes and face culling are disabled
temporarily. Program, bindings, samplers, and blend/depth/cull state are restored
by end or cancel. Framebuffer, viewport, scissor, and stencil state are untouched.

- **`mvp`** — model-view-projection matrix used to transform glyph world positions
- **`viewportW`** — current viewport width in pixels
- **`viewportH`** — current viewport height in pixels

#### end

```java
public void end()
```

Ends the current batch and flushes remaining glyphs.

#### cancel

```java
public void cancel()
```

Abandons unflushed glyphs and restores captured GL state if a pass is active.
Already flushed draws remain visible. Safe to call after end or from a finally block.

#### flush

```java
public void flush()
```

Flushes queued glyphs to the GPU.

#### getQuadPadding

```java
public float getQuadPadding()
```

Returns the CPU-side quad padding in world units.

**Returns:** quad padding

#### setQuadPadding

```java
public void setQuadPadding(float quadPadding)
```

Sets CPU-side quad padding in world units.

The default is 0.5 world units. Axis-aligned projections automatically raise
this to at least half a screen pixel. For other projections choose enough
world-space padding to cover antialiasing at the smallest visible scale.

- **`quadPadding`** — quad padding in world units

#### isOrphanOnFlush

```java
public boolean isOrphanOnFlush()
```

Returns whether the instance buffer is orphaned before each flush.

**Returns:** true if buffer orphaning is enabled

#### setOrphanOnFlush

```java
public void setOrphanOnFlush(boolean orphanOnFlush)
```

Enables or disables stream-buffer orphaning before each flush.

Leaving this enabled is usually faster for dynamic text because it prevents the driver from
waiting on the previous frame's instance buffer. Disable only if profiling shows your driver is
faster without it.

- **`orphanOnFlush`** — true to orphan the instance buffer before uploading queued glyphs

#### dispose

```java
public void dispose()
```

Releases the shader and OpenGL buffers owned by this batch.

#### getMaxGlyphs

```java
public int getMaxGlyphs()
```

Returns the maximum glyph capacity of this batch.

**Returns:** maximum glyphs before flush

</details>

<a id="type-slugcurve"></a>

### SlugCurve

[Source](../../src/main/java/valthorne/graphics/font/slug/SlugCurve.java#L18)

A single quadratic Bezier segment stored in normalized em-space.

Straight line segments are represented as degenerate quadratics whose control point is the
midpoint between the segment endpoints.

- **`p1x`** — first endpoint x in em-space
- **`p1y`** — first endpoint y in em-space
- **`p2x`** — control point x in em-space
- **`p2y`** — control point y in em-space
- **`p3x`** — second endpoint x in em-space
- **`p3y`** — second endpoint y in em-space

<details>
<summary>SlugCurve operation reference (4 declarations)</summary>

#### minX

```java
public float minX()
```

Returns the smallest x coordinate used by this curve.

**Returns:** minimum x coordinate

#### maxX

```java
public float maxX()
```

Returns the largest x coordinate used by this curve.

**Returns:** maximum x coordinate

#### minY

```java
public float minY()
```

Returns the smallest y coordinate used by this curve.

**Returns:** minimum y coordinate

#### maxY

```java
public float maxY()
```

Returns the largest y coordinate used by this curve.

**Returns:** maximum y coordinate

</details>

<a id="type-slugfont"></a>

### SlugFont

[Source](../../src/main/java/valthorne/graphics/font/slug/SlugFont.java#L62)

Owns GPU outline data and font metrics for a contiguous character range,
using Slug-style banded curve evaluation. Loading compiles glyph outlines into
quadratic segments, horizontal/vertical curve lookup bands, and a dense kerning
table, then uploads RGBA16F curve and RG32UI band textures. It therefore requires
a current OpenGL context even though font decoding uses STB on the CPU.

Text drawing and measurement iterate UTF-16 characters, recognize newline and
tab, and use a quarter-em advance for characters outside the compiled range.
They do not perform script shaping or surrogate-pair composition. Cubic outlines
are approximated by short line sequences; small segments are omitted according
to the configured thresholds.

The font retains backing bytes for STB fallback kerning queries and owns its two
GPU textures. Dispose them with the context current after all drawing completes.
Measurement data remains stored after disposal, but drawing then has no valid
texture resources. Optional retained text, baseline position, size, and color
support Dimensional usage; explicit width/height setters only override measured
metadata and do not scale rendered glyphs.

```java
SlugFont font = SlugFont.load("assets/font.ttf");
try {
    font.setText("Hello").setSize(32);
    font.setPosition(20, 60);
    font.draw(batch); // Inside the caller's SlugBatch drawing lifecycle.
} finally {
    font.dispose();
}
```

<details>
<summary>SlugFont operation reference (27 declarations)</summary>

#### load

```java
public static SlugFont load(String path)
```

Loads and uploads the 95 printable ASCII characters beginning at codepoint 32.
Requires a current OpenGL context for data texture creation.

- **`path`** — TrueType/OpenType filesystem path

**Returns:** newly owned font

**Throws `RuntimeException`:** if reading or STB initialization fails

#### load

```java
public static SlugFont load(String path, int firstCodepoint, int characterCount)
```

Reads a font file and compiles the requested contiguous character range,
including its pairwise kerning table and GPU data textures.

- **`path`** — TrueType/OpenType filesystem path
- **`firstCodepoint`** — first character to compile
- **`characterCount`** — range length from 1 through 256

**Returns:** newly owned font

**Throws `IllegalArgumentException`:** if characterCount is outside its supported range

**Throws `RuntimeException`:** if reading or STB initialization fails

#### load

```java
public static SlugFont load(byte[] fontBytes, int firstCodepoint, int characterCount)
```

Copies source bytes into retained direct storage, initializes STB, compiles
glyphs and kerning, and uploads the two owned data textures. The caller may
reuse its byte array afterward. Character-count validation does not validate
the first codepoint. Texture creation preserves texture and pixel-unpack state.

- **`fontBytes`** — nonnull, nonempty font data
- **`firstCodepoint`** — first codepoint in the lookup table
- **`characterCount`** — table length from 1 through 256

**Returns:** newly owned font

**Throws `IllegalArgumentException`:** if bytes are absent or characterCount is invalid

**Throws `RuntimeException`:** if STB cannot initialize the font

#### createRun

```java
public SlugTextRun createRun(String text, float size)
```

Creates a reusable layout tied to this font, avoiding repeated glyph and kerning
layout during drawing. Keep the font's GPU resources alive while drawing the run.

- **`text`** — text to lay out
- **`size`** — world units per em

**Returns:** new reusable run

#### draw

```java
public void draw(SlugBatch batch, String text, float x, float y, float size, Color color)
```

Appends drawable glyphs at a baseline origin, applying kerning and advances.
Newlines reset X and move the baseline down one line height; tabs advance four
spaces and reset kerning. Uncompiled UTF-16 characters advance a quarter em.
Null/empty text or zero size emits nothing. The batch manages actual submission.

- **`batch`** — nonnull destination batch
- **`text`** — text to draw
- **`x`** — baseline origin X
- **`y`** — baseline origin Y
- **`size`** — world units per em; not validated here
- **`color`** — copied draw tint, or null for white

**Throws `NullPointerException`:** if batch is null

#### draw

```java
public void draw(SlugBatch batch)
```

Appends the retained text using its current baseline, size, and mutable tint.

- **`batch`** — nonnull destination batch

**Throws `NullPointerException`:** if batch is null

#### setText

```java
public SlugFont setText(String text)
```

Replaces retained text, normalizing null to empty, and recalculates measured
width and height at the retained font size.

- **`text`** — new retained text

**Returns:** this font

#### setPosition

```java
    public void setPosition(float x, float y)
```

Sets the retained baseline origin without changing layout or measurements.

- **`x`** — baseline X
- **`y`** — baseline Y

#### getX

```java
    public float getX()
```

Returns the retained baseline X coordinate.

**Returns:** baseline X

#### setX

```java
    public void setX(float x)
```

Sets retained baseline X without changing measured width.

- **`x`** — baseline X

#### getY

```java
    public float getY()
```

Returns the retained baseline Y coordinate.

**Returns:** baseline Y

#### setY

```java
    public void setY(float y)
```

Sets retained baseline Y without changing measured height.

- **`y`** — baseline Y

#### setSize

```java
public SlugFont setSize(float size)
```

Sets retained world units per em and recomputes measured dimensions.
The value is stored without sign or finiteness validation.

- **`size`** — new font scale

**Returns:** this font

#### getWidth

```java
public float getWidth(String text, float size)
```

Measures the maximum line advance using the same UTF-16 kerning, tab, and
fallback rules as draw. This measures pen advances rather than tight ink bounds.
Does not change retained state; null or empty text returns zero.

- **`text`** — text to measure
- **`size`** — world units per em

**Returns:** maximum line advance in world units

#### getHeight

```java
public float getHeight(String text, float size)
```

Measures line-box height as newline count plus one times font line height and
size. A trailing newline adds an empty line; null or empty text returns zero.
This is not a tight glyph-ink measurement.

- **`text`** — text to measure
- **`size`** — world units per em

**Returns:** total line-box height

#### getWidth

```java
    public float getWidth()
```

Returns cached retained width, either measured or explicitly overridden.

**Returns:** retained width metadata

#### setWidth

```java
    public void setWidth(float width)
```

Overrides cached width metadata without scaling glyphs or changing font size.
A later text or single-argument size change recomputes it.

- **`width`** — width metadata to store

#### getHeight

```java
    public float getHeight()
```

Returns cached retained height, either measured or explicitly overridden.

**Returns:** retained height metadata

#### setHeight

```java
    public void setHeight(float height)
```

Overrides cached height metadata without scaling glyphs or changing line spacing.
A later text or single-argument size change recomputes it.

- **`height`** — height metadata to store

#### setSize

```java
    public void setSize(float width, float height)
```

Overrides both cached dimensions without changing the font scale used to draw.
Use the single-argument font-size setter to resize glyphs and recalculate bounds.

- **`width`** — width metadata
- **`height`** — height metadata

#### getFontSize

```java
public float getFontSize()
```

Returns the retained drawing scale, independent of overridden width/height metadata.

**Returns:** world units per em

#### getColor

```java
public Color getColor()
```

Returns the live retained tint. Direct mutations affect subsequent retained draws.

**Returns:** mutable font-owned color

#### setColor

```java
public SlugFont setColor(Color color)
```

Copies a supplied color into the retained tint, leaving it unchanged for null.

- **`color`** — tint to copy, or null

**Returns:** this font

#### ascent

```java
public float ascent()
```

Returns STB ascent converted from font units to em units.

**Returns:** baseline-to-ascent metric in em units

#### descent

```java
public float descent()
```

Returns STB descent in em units, normally a negative baseline-relative value.

**Returns:** descent metric in em units

#### lineHeight

```java
public float lineHeight()
```

Returns ascent minus descent plus line gap, used for baseline spacing.

**Returns:** line height in em units

#### dispose

```java
public void dispose()
```

Deletes owned curve and band textures with the GL context current, replacing
their IDs with zero. Repeated calls are harmless. Retains CPU glyph, kerning,
and font-byte data, so measurement still works while drawing is no longer valid.

</details>

<a id="type-slugfont-uploadstate"></a>

### SlugFont.UploadState — internal support type

[Source](../../src/main/java/valthorne/graphics/font/slug/SlugFont.java#L659)

Scopes texture-upload state on the current GL context. Construction disables the
pixel-unpack buffer and establishes tightly packed rows; close restores the previous
texture binding and unpack settings even when upload exits exceptionally.
Instances belong to one context-thread operation and must be closed on that thread.

<details>
<summary>SlugFont.UploadState operation reference (1 declarations)</summary>

#### close

```java
@Override public void close()
```

Restores the captured texture binding, pixel-unpack buffer, alignment, row layout,
skip offsets, and byte-swap flag on the same context used during construction.

</details>

<a id="type-slugfont-floattexelwriter"></a>

### SlugFont.FloatTexelWriter — internal support type

[Source](../../src/main/java/valthorne/graphics/font/slug/SlugFont.java#L1180)

CPU staging writer for four-float curve texels. Each quadratic occupies two
adjacent texels, with row-end padding to keep the pair on the same row.
Address packing assumes the production width of 4096.

<a id="type-slugfont-uinttexelwriter"></a>

### SlugFont.UIntTexelWriter — internal support type

[Source](../../src/main/java/valthorne/graphics/font/slug/SlugFont.java#L1265)

CPU staging writer for two-integer band headers and curve-address texels.
Tracks linear texel offsets before padding rows for an RG32UI upload.

The writer owns CPU staging values only. Generated integer buffers feed band-texture
uploads; texture creation and disposal belong to SlugFont rather than this helper.

<a id="type-slugglyph"></a>

### SlugGlyph

[Source](../../src/main/java/valthorne/graphics/font/slug/SlugGlyph.java#L13)

Metadata needed to draw one Slug glyph.

The glyph stores layout metrics in normalized em-space and packed locations into the Slug
band texture. The curve data itself is stored globally by `SlugFont` so many glyphs can
share one GPU upload.

<details>
<summary>SlugGlyph operation reference (5 declarations)</summary>

#### codepoint

```java
public int codepoint()
```

Returns the Unicode codepoint represented by this glyph.

**Returns:** glyph codepoint

#### advance

```java
public float advance()
```

Returns the horizontal advance in em units.

**Returns:** advance in em units

#### isDrawable

```java
public boolean isDrawable()
```

Returns whether this glyph has visible outline data.

**Returns:** true if drawable

#### width

```java
public float width()
```

Returns this glyph's width in em units.

**Returns:** glyph bounds width

#### height

```java
public float height()
```

Returns this glyph's height in em units.

**Returns:** glyph bounds height

</details>

<a id="type-slugshader"></a>

### SlugShader — internal support type

[Source](../../src/main/java/valthorne/graphics/font/slug/SlugShader.java#L18)

GLSL shader used by the fast 2D Slug font renderer.

This variant keeps Slug's curve coverage fragment shader, but removes the expensive reference
vertex dilation path. Glyph quads are padded on the CPU by `SlugBatch`, so the vertex shader
only expands a compact instance into a four-corner triangle strip.

<details>
<summary>SlugShader operation reference (1 declarations)</summary>

#### Constructor

```java
public SlugShader()
```

Creates the shader program.

</details>

<a id="type-slugtextrun"></a>

### SlugTextRun

[Source](../../src/main/java/valthorne/graphics/font/slug/SlugTextRun.java#L25)

Reusable pre-laid-out Slug text.

Use this for fair live-rendering benchmarks and for text that is drawn repeatedly. The run
removes per-frame layout, newline handling, tab handling, and kerning lookups. It still renders
live Slug curves every frame; it does not cache text into a texture.

```java
SlugTextRun run = font.createRun("The quick brown fox", 36f);

batch.begin(projection, width, height);
run.draw(batch, 40f, 180f, Color.WHITE);
batch.end();
```

<details>
<summary>SlugTextRun operation reference (7 declarations)</summary>

#### rebuild

```java
public void rebuild(String text, float size)
```

Rebuilds this run for new text or size.

- **`text`** — text to layout
- **`size`** — world units per em

#### draw

```java
public void draw(SlugBatch batch, float x, float y, Color color)
```

Draws this pre-laid-out run.

- **`batch`** — batch that receives glyph instances
- **`x`** — baseline x position
- **`y`** — baseline y position
- **`color`** — text color

#### text

```java
public String text()
```

Returns the source text.

**Returns:** source text

#### size

```java
public float size()
```

Returns the run size in world units per em.

**Returns:** size

#### width

```java
public float width()
```

Returns the measured width.

**Returns:** width

#### height

```java
public float height()
```

Returns the measured height.

**Returns:** height

#### glyphCount

```java
public int glyphCount()
```

Returns the number of drawable glyphs in this run.

**Returns:** drawable glyph count

</details>

## Related guides

- [Bitmap fonts and glyph styling](fonts.md)
- [Viewport scaling and coordinate conversion](viewports.md)
- [Shaders and visual effects](shaders.md)
- [Standard UI controls](ui-controls.md)
