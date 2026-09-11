# Shaders and visual effects

Author: Albert Beaupre

[System manual](README.md)

## Purpose

Shaders are programs that define vertex, fragment, or compute work. The base classes manage compilation, linking, binding, and uniforms; specialized shaders implement common texture, shape, mesh, lighting, and post-effect contracts. Match the shader's expected vertex layout and uniforms to the renderer supplying its data.

## Features and when to use them

| Feature | Purpose |
| --- | --- |
| Program lifecycle | Compile/link, bind for use, configure uniforms, and dispose the program. |
| Resource sources | ShaderSources loads packaged shader text and templates. |
| Visual effects | Blur, glow, outline, flash, burn, reflection, and water configure different texture effects. |
| Compute dispatch | ComputeShader performs non-raster work and requires appropriate OpenGL support. |
| Renderer contracts | Mesh, billboard, shape, and textured-quad shaders expect specific attributes and matrices. |

## Getting started

1. Choose a specialized shader or supply sources to the base program class.
2. Create it with a current compatible graphics context and inspect compilation failures.
3. Flush incompatible queued draws before changing the program, then set required uniforms.
4. Draw or dispatch with the matching data layout and release the program during graphics cleanup.

## Ownership and lifecycle

Uniform locations belong to a linked program and can become stale after relinking. Binding and unbinding generally do not restore an arbitrary previous program. Treat texture units, image bindings, and synchronization as part of the shader contract.

## Important behavior

- WaterShader.apply configures and leaves the shader bound; it does not draw the sprite.
- ReflectionShader.apply temporarily changes sprite bounds and restores them on normal completion.
- Do not assume every effect helper has identical draw/unbind semantics.

## Components and examples

The sections below explain each component and its declared public or protected operations. Expand an operation reference for signatures, parameters, results, and edge cases. Inherited operations are documented with their base type. Examples embedded in component descriptions are integration fragments: supply the surrounding resources and application variables they name.

- [`Billboard3DShader`](#type-billboard3dshader)
- [`BlurShader`](#type-blurshader)
- [`BurnShader`](#type-burnshader)
- [`ComputeShader`](#type-computeshader)
- [`DepthShader3D`](#type-depthshader3d)
- [`FlashShader`](#type-flashshader)
- [`GlowShader`](#type-glowshader)
- [`LightingShader3D`](#type-lightingshader3d)
- [`Mesh3DShader`](#type-mesh3dshader)
- [`OutlineShader`](#type-outlineshader)
- [`ReflectionShader`](#type-reflectionshader)
- [`Shader`](#type-shader)
- [`ShaderSources`](#type-shadersources)
- [`ShapeShader`](#type-shapeshader)
- [`TexturedQuadShader`](#type-texturedquadshader)
- [`WaterShader`](#type-watershader)

<a id="type-billboard3dshader"></a>

### Billboard3DShader

[Source](../../src/main/java/valthorne/graphics/shader/Billboard3DShader.java#L23)

GLSL 3.30 program for textured billboard geometry already oriented in world
space by BillboardBatch3D. The vertex stage transforms supplied positions;
it does not calculate camera-facing axes. Fragment shading combines texture,
vertex color and material tint, adds emission and optional XY radiance, then
applies camera-distance fog while preserving the combined base alpha.

Fragments whose combined alpha is at or below u_alphaCutoff are discarded.
Lighting RGB is clamped to zero through one before fog mixing, so this shader
is not an HDR output path. Fog parameters in u_fog are start distance, end
distance and amount; callers must supply a nonzero distance interval.

Construction compiles and reloads the program, initializes texture samplers
on units zero and one, and finishes by unbinding. It does not restore a previously
bound program. Other uniforms, textures, blend/depth state and geometry are
supplied by the batch. Use a current compatible GL context and dispose through
Shader when finished; textures remain owned by their providers.

<details>
<summary>Billboard3DShader operation reference (17 declarations)</summary>

#### ATTR_POSITION

```java
public static final  int ATTR_POSITION
```

World-space vec3 position attribute location; billboard orientation is computed before submission.

#### ATTR_UV

```java
public static final  int ATTR_UV
```

Texture-coordinate vec2 attribute location for the billboard region.

#### ATTR_COLOR

```java
public static final  int ATTR_COLOR
```

Per-vertex RGBA color attribute location, multiplied with texture and material tint.

#### UNIFORM_MVP

```java
public static final  String UNIFORM_MVP
```

Matrix uniform mapping supplied world positions into homogeneous clip coordinates.

#### UNIFORM_CAMERA_POS

```java
public static final  String UNIFORM_CAMERA_POS
```

World-space vec3 camera position used for Euclidean fog distance.

#### UNIFORM_FOG_COLOR

```java
public static final  String UNIFORM_FOG_COLOR
```

RGBA fog-color uniform; only RGB participates in fragment fog mixing.

#### UNIFORM_MATERIAL_TINT

```java
public static final  String UNIFORM_MATERIAL_TINT
```

RGBA multiplier applied to sampled texture and vertex color, including alpha cutoff.

#### UNIFORM_MATERIAL_EMISSIVE

```java
public static final  String UNIFORM_MATERIAL_EMISSIVE
```

RGBA emission value whose RGB is multiplied by its alpha before addition.

#### UNIFORM_MATERIAL_FOG_MIX

```java
public static final  String UNIFORM_MATERIAL_FOG_MIX
```

Scalar multiplier for the distance-fog blend factor.

#### UNIFORM_MATERIAL_RADIANCE_MIX

```java
public static final  String UNIFORM_MATERIAL_RADIANCE_MIX
```

Scalar multiplier for sampled radiance; values at or below zero skip that contribution.

#### UNIFORM_TEXTURE

```java
public static final  String UNIFORM_TEXTURE
```

Diffuse sampler name, initialized to texture unit zero.

#### UNIFORM_LIGHT_TEXTURE

```java
public static final  String UNIFORM_LIGHT_TEXTURE
```

XY radiance sampler name, initialized to texture unit one.

#### UNIFORM_LIGHT_WORLD_MIN

```java
public static final  String UNIFORM_LIGHT_WORLD_MIN
```

World XY minimum used as the origin of radiance texture coordinates.

#### UNIFORM_LIGHT_WORLD_SIZE

```java
public static final  String UNIFORM_LIGHT_WORLD_SIZE
```

World XY extents dividing radiance coordinates; callers must supply nonzero sizes.

#### UNIFORM_RADIANCE_STRENGTH

```java
public static final  String UNIFORM_RADIANCE_STRENGTH
```

Global scalar multiplying the sampled radiance contribution.

#### UNIFORM_APPLY_RADIANCE

```java
public static final  String UNIFORM_APPLY_RADIANCE
```

Integer enable uniform; radiance sampling occurs only when its value equals one.

#### Constructor

```java
public Billboard3DShader()
```

Compiles the bundled shader resources, records attribute bindings and reloads the program. Temporarily binds it to assign diffuse and radiance sampler units, then unbinds rather than restoring the previous program. No texture objects are created.

**Throws `IllegalStateException`:** if shader compilation or program linking fails

</details>

<a id="type-blurshader"></a>

### BlurShader

[Source](../../src/main/java/valthorne/graphics/shader/BlurShader.java#L48)

Simple 3x3 box blur shader (fixed-function friendly) built on top of `Shader`.

##### What this shader does

- Samples a 3x3 neighborhood around the current fragment (9 taps).

- Averages those samples to produce a soft blur.

- Multiplies the result by the incoming vertex color (`gl_Color`).

##### How blur radius works

`u_radiusPx` is expressed in "pixel units" but becomes meaningful only when paired with
`u_texelSize`. The shader computes:

```java
vec2 o = u_texelSize * max(0.0, u_radiusPx);
```

Where `u_texelSize` should be `(1/textureWidth, 1/textureHeight)` for the bound texture.
Larger radius values increase the sampling offset distance.

##### Usage

```java
BlurShader blur = new BlurShader();
Texture texture = ...;

// Before drawing your textured quads:
blur.apply(texture, 2f);
```

##### Notes

- This is a **box blur**, not a gaussian blur.

- Works best on UI panels / sprites where a cheap blur is acceptable.

- Assumes the texture is bound to texture unit 0.

<details>
<summary>BlurShader operation reference (3 declarations)</summary>

#### Constructor

```java
public BlurShader()
```

Creates a new blur shader using the built-in GLSL sources.

#### apply

```java
public void apply(Sprite sprite, float radiusPx)
```

Binds the blur effect using the sprite's backing texture dimensions, draws
the sprite immediately, and unbinds the program on normal completion. Requires
a current OpenGL context. The previous shader is not restored, and exceptions
can leave this program bound.

- **`sprite`** — sprite with a valid backing texture
- **`radiusPx`** — blur sampling extent in texture pixels

#### bind

```java
public void bind(float textureWidth, float textureHeight, float radiusPx)
```

Applies the blur shader with the specified texture dimensions and blur radius.

This method sets up the shader uniforms for texture size, texel size, and blur radius,
and ensures the shader is bound before these values are applied.

- **`textureWidth`** — the width of the texture in pixels
- **`textureHeight`** — the height of the texture in pixels
- **`radiusPx`** — the blur radius in pixels

</details>

<a id="type-burnshader"></a>

### BurnShader

[Source](../../src/main/java/valthorne/graphics/shader/BurnShader.java#L52)

"Burn away" / dissolve shader built on top of `Shader`.

##### What this shader does

- Samples the base texture at `v_uv` and tints it by `gl_Color`.

- Generates animated value-noise in UV space using `u_time`.

- Discards pixels where noise is below `u_threshold` (dissolve/burn-away).

- Adds a colored burn edge using `u_burnColor` and a small smoothstep band.

##### Key uniforms

- `u_texture`: sampler2D bound to texture unit 0.

- `u_time`: time in seconds; drives noise animation.

- `u_threshold`: 0..1 dissolve threshold. Higher values burn away more of the sprite.

- `u_burnColor`: RGB edge/glow color and an alpha component (alpha is currently not used for output alpha).

##### Threshold behavior

The fragment computes noise `n`. If `n < threshold` the fragment is discarded.
The edge band is `smoothstep(th, th + 0.10, n)` which creates a ~10% wide transition
in noise-space used to blend between burn color and original color.

##### Usage

```java
BurnShader burn = new BurnShader();

// Example: burn away from 0 -> 1 over time
float t = (float)elapsedSeconds;
float threshold = Math.min(1f, t * 0.25f);

burn.bind(t, threshold, 1f, 0.35f, 0.05f, 1f);
// draw your textured quad(s)...
burn.unbind(); // optional
```

##### Notes

- Fragments with near-zero alpha (`<= 0.001`) are preserved and not discarded.

- The burn effect is UV-based; different UV scaling changes the noise "grain".

- This shader uses `discard`, so sorting / blending behavior depends on your pipeline.

<details>
<summary>BurnShader operation reference (2 declarations)</summary>

#### Constructor

```java
public BurnShader()
```

Creates a new burn shader using the built-in GLSL sources.

#### bind

```java
public void bind(float timeSeconds, float threshold, float burnR, float burnG, float burnB, float burnA)
```

Binds the shader and sets burn uniforms for the current draw sequence.

This method:

- Binds the program via `bind()`.

- Sets `u_texture` to texture unit 0.

- Sets `u_time` in seconds (drives noise animation).

- Sets `u_threshold` (0..1) controlling how much is burned away.

- Sets `u_burnColor` used for the burn edge color and glow contribution.

- **`timeSeconds`** — time in seconds used to animate the noise field
- **`threshold`** — dissolve threshold in range `[0..1]` (higher = more burned away)
- **`burnR`** — burn edge red component
- **`burnG`** — burn edge green component
- **`burnB`** — burn edge blue component
- **`burnA`** — burn edge alpha component (currently not used for output alpha)

</details>

<a id="type-computeshader"></a>

### ComputeShader

[Source](../../src/main/java/valthorne/graphics/shader/ComputeShader.java#L48)

Minimal wrapper for an OpenGL compute shader program (GL 4.3+).

Features:
- Compile/link a single GL_COMPUTE_SHADER stage
- Scalar and vector uniform setters
- Image and SSBO binding helpers
- Dispatch and memory barrier helpers

Notes:
- Call `dispose()` to delete GL resources when finished.
- Check availability via `isComputeSupported()` before constructing.

```java
ComputeShader shader = new ComputeShader(source);
shader.bind();
shader.setUniform1i("u_count", count);
shader.dispatch(groups, 1, 1);
ComputeShader.memoryBarrierAll();
shader.unbind();
shader.dispose();
```

All OpenGL operations require the appropriate current context. Uniform setters
use glUniform and require this program to be bound; dispatch also uses the currently
bound program. Binding helpers change global state and do not restore previous
bindings. Created storage buffers belong to the caller and are not deleted when
this shader is disposed.

<details>
<summary>ComputeShader operation reference (18 declarations)</summary>

#### Constructor

```java
public ComputeShader(String computeSource)
```

Checks compute support and compiles a single compute stage into a linked program.
Compilation and linking failures clean up resources allocated by those failed steps.
The new program is not automatically bound.

- **`computeSource`** — GLSL compute-stage source

**Throws `NullPointerException`:** if source is null after the support check

**Throws `IllegalStateException`:** if support is absent or compilation/linking fails

#### isComputeSupported

```java
public static boolean isComputeSupported()
```

Inspects the current context's version string, then falls back to checking both
compute-shader and shader-storage extension names. This string-based probe is
not an exhaustive capabilities check and requires an initialized current context.

**Returns:** whether the version or extension probe reports support

#### memoryBarrierAll

```java
public static void memoryBarrierAll()
```

Issues image-access, shader-storage, and texture-fetch barrier bits for dependent
GPU operations. Despite the name, this is not GL_ALL_BARRIER_BITS and does not
wait for completion on the CPU.

#### memoryBarrierImage

```java
public static void memoryBarrierImage()
```

Issues only the shader-image-access barrier bit. Choose additional barrier types
when later operations consume the output through other access paths.

#### bindImage2D

```java
public static void bindImage2D(int texId, int unit, int access, int internalFormat)
```

Binds mip level zero of a two-dimensional texture as a nonlayered image. Changes
the indexed image binding and leaves validation of format/access compatibility to OpenGL.

- **`texId`** — texture name, or zero to unbind
- **`unit`** — image binding index
- **`access`** — OpenGL read/write access constant
- **`internalFormat`** — image format compatible with the texture

#### createSSBO

```java
public static int createSSBO(long size, int usage)
```

Allocates uninitialized shader-storage buffer bytes and unbinds the generic target
to zero afterward. The caller owns the returned buffer and must delete it.

- **`size`** — storage size in bytes
- **`usage`** — OpenGL buffer usage hint

**Returns:** newly allocated buffer name

#### bindSSBO

```java
public static void bindSSBO(int ssboId, int binding)
```

Binds a whole storage buffer at an indexed shader-storage binding point. Does not
allocate storage or transfer ownership.

- **`ssboId`** — buffer name, or zero to clear the binding
- **`binding`** — shader-storage binding index

#### updateSSBO

```java
public static void updateSSBO(int ssboId, long offset, java.nio.ByteBuffer data)
```

Uploads the buffer's remaining bytes into existing storage at a byte offset, then
unbinds the generic storage target to zero. This does not resize the destination
or issue a memory barrier; the caller supplies suitable buffer bounds.

- **`ssboId`** — existing storage buffer name
- **`offset`** — destination byte offset
- **`data`** — source bytes from position through limit

#### bind

```java
public void bind()
```

Selects this program as the current OpenGL program. Does not save the previously
bound program; after disposal the stored zero name unbinds instead.

#### unbind

```java
public void unbind()
```

Binds program zero, regardless of which program is currently active. This does
not restore a program that was active before bind.

#### dispatch

```java
public void dispatch(int groupsX, int groupsY, int groupsZ)
```

Dispatches workgroups using the currently bound compute program. Counts are groups,
not individual shader invocations. Does not bind this instance or insert a barrier.

- **`groupsX`** — workgroup count along X
- **`groupsY`** — workgroup count along Y
- **`groupsZ`** — workgroup count along Z

#### dispose

```java
public void dispose()
```

Deletes the owned program and compute stage, first unbinding if this program is
active. Clears uniform locations and zeros names, making repeated disposal a no-op.
Buffers and textures used by the shader remain caller-owned.

#### setUniform1i

```java
public void setUniform1i(String name, int v0)
```

Writes a 1-component int uniform using a cached location from this program.
Bind this shader first: glUniform writes the active program. Inactive or missing
uniforms have location -1 and are ignored by OpenGL.

- **`name`** — GLSL uniform name
- **`v0`** — scalar value

#### setUniform1f

```java
public void setUniform1f(String name, float v0)
```

Writes a 1-component float uniform using a cached location from this program.
Bind this shader first: glUniform writes the active program. Inactive or missing
uniforms have location -1 and are ignored by OpenGL.

- **`name`** — GLSL uniform name
- **`v0`** — scalar value

#### setUniform2f

```java
public void setUniform2f(String name, float x, float y)
```

Writes a 2-component float uniform using a cached location from this program.
Bind this shader first: glUniform writes the active program. Inactive or missing
uniforms have location -1 and are ignored by OpenGL.

- **`name`** — GLSL uniform name
- **`x`** — X component
- **`y`** — Y component

#### setUniform3f

```java
public void setUniform3f(String name, float x, float y, float z)
```

Writes a 3-component float uniform using a cached location from this program.
Bind this shader first: glUniform writes the active program. Inactive or missing
uniforms have location -1 and are ignored by OpenGL.

- **`name`** — GLSL uniform name
- **`x`** — X component
- **`y`** — Y component
- **`z`** — Z component

#### setUniform4f

```java
public void setUniform4f(String name, float x, float y, float z, float w)
```

Writes a 4-component float uniform using a cached location from this program.
Bind this shader first: glUniform writes the active program. Inactive or missing
uniforms have location -1 and are ignored by OpenGL.

- **`name`** — GLSL uniform name
- **`x`** — X component
- **`y`** — Y component
- **`z`** — Z component
- **`w`** — W component

#### setUniform2i

```java
public void setUniform2i(String name, int x, int y)
```

Writes a 2-component int uniform using a cached location from this program.
Bind this shader first: glUniform writes the active program. Inactive or missing
uniforms have location -1 and are ignored by OpenGL.

- **`name`** — GLSL uniform name
- **`x`** — X component
- **`y`** — Y component

</details>

<a id="type-depthshader3d"></a>

### DepthShader3D

[Source](../../src/main/java/valthorne/graphics/shader/DepthShader3D.java#L22)

GLSL 3.30 depth-pass shader that preserves alpha-cutout silhouettes without
evaluating lighting or writing a color output. Surviving fragments use normal
rasterized depth; the caller supplies the depth attachment, viewport, depth-test
and depth-write state. MeshBatch3D uses this program during shadow rendering.

Vertex inputs are position at location zero, color at location two and UV
at location three. The u_mvp matrix maps supplied positions to clip space.
Fragment alpha multiplies interpolated vertex alpha by u_materialTint alpha,
and additionally samples u_texture alpha only when u_hasTexture equals one.
Fragments at or below u_alphaCutoff are discarded, including equality.

Callers must bind the program, assign its uniforms and provide compatible
vertex attributes before drawing. Texture selection and ownership remain with
the caller. Construction compiles native shader resources, so use a compatible
current OpenGL context and release the program through Shader's disposal lifecycle.

<details>
<summary>DepthShader3D operation reference (1 declarations)</summary>

#### Constructor

```java
public DepthShader3D()
```

Compiles and links the bundled vertex and fragment stages through Shader.
This creates a program immediately; it does not configure a render target,
upload draw-specific uniform values or submit geometry. A current OpenGL
context supporting the declared shader version is required, and compilation
or linking failures follow the base constructor's error handling.

</details>

<a id="type-flashshader"></a>

### FlashShader

[Source](../../src/main/java/valthorne/graphics/shader/FlashShader.java#L46)

Time-based flash shader.

##### What this shader does

- Samples `u_texture` at `v_uv` and tints by `gl_Color`.

- Linearly mixes the sampled RGB toward `u_flashColor.rgb` by `u_amount`.

- Preserves original alpha (flash affects color only).

##### Time-driven behavior

This class has no internal state. The flash repeats purely from `timeSeconds` and
`durationSeconds`. Each cycle computes a 0..1 ramp and converts it into a fade-out
amount that starts strong and decays to 0.

##### Uniforms

- `u_texture`: sampler2D bound to texture unit 0.

- `u_flashColor`: RGB flash color (alpha is unused by the shader).

- `u_amount`: 0..1 mix amount (0 = no flash, 1 = full flash color).

##### Usage

```java
FlashShader flash = new FlashShader();

// Repeating flash (0.5 seconds per cycle).
flash.apply(JGL.getTime(), 0.5f, 1f, 0.2f, 0.2f, 1.0f);
// draw your quad(s)...
flash.unbind(); // optional
```

##### Notes

- If `u_amount <= 0` or sampled alpha is near zero, the shader returns the base color.

- Make sure `durationSeconds > 0` to avoid a division-by-zero / NaN phase.

<details>
<summary>FlashShader operation reference (2 declarations)</summary>

#### Constructor

```java
public FlashShader()
```

Creates a new `FlashShader` using the built-in GLSL sources.

#### apply

```java
public void apply(float timeSeconds, float durationSeconds, float r, float g, float b, float a)
```

Applies a repeating, time-based flash and binds this shader.

Computation:

- `phase = (timeSeconds % durationSeconds) / durationSeconds` gives a repeating 0..1 ramp.

- `amount = 1 - phase` turns that into a fade-out (starts at 1, ends at 0).

- `amount *= amount` applies a simple ease curve (stronger at the start).

This method sets:

- `u_texture = 0`

- `u_amount = amount`

- `u_flashColor = (r,g,b,1)`

- **`timeSeconds`** — current time in seconds (e.g., `JGL.getTime()`)
- **`durationSeconds`** — length of one flash cycle in seconds (must be `> 0`)
- **`r`** — flash red component
- **`g`** — flash green component
- **`b`** — flash blue component
- **`a`** — flash alpha component

**Throws `IllegalArgumentException`:** if `durationSeconds <= 0`

</details>

<a id="type-glowshader"></a>

### GlowShader

[Source](../../src/main/java/valthorne/graphics/shader/GlowShader.java#L52)

Soft glow around a sprite using alpha falloff sampling.

##### What this shader does

- Samples the sprite normally for opaque pixels (alpha &gt; ~0.001).

- For transparent pixels, samples nearby alpha at multiple radii.

- Converts nearby alpha into a glow strength and outputs `u_glowColor` with scaled alpha.

##### How glow is computed

- Three radii are used: `r1 = radius*0.35`, `r2 = radius*0.70`, `r3 = radius` (each clamped to &gt;= 1px).

- At each radius, the shader samples 8 directions (4 cardinal + 4 diagonals).

- Samples are weighted by radius: r1 = 1.00, r2 = 0.75, r3 = 0.45.

- The sum is normalized, shaped (`pow(glow, 1.8)`), then multiplied by `u_intensity`.

##### Important notes

- `u_texelSize` must be `(1/textureWidth, 1/textureHeight)` or the radius will be wrong.

- `u_radiusPx` is measured in **source texture pixels**, not screen pixels.

- Only transparent pixels output glow. Sprite pixels output the sprite normally.

- This is a fixed sample-count glow (24 samples + center). It is stable and predictable.

##### Uniforms

- `u_texture` (sampler2D): texture unit index (use 0).

- `u_texelSize` (vec2): texel size (`1/width`, `1/height`).

- `u_radiusPx` (float): glow radius in source texture pixels.

- `u_intensity` (float): glow strength multiplier (typical range 0..3).

- `u_glowColor` (vec4): glow RGBA color (alpha is scaled by computed glow).

##### Usage

```java
GlowShader glow = new GlowShader();

Texture texture = ...;

glow.apply(texture, 6f, 1.25f, 1f, 0.8f, 0.2f, 1f); // warm glow
```

<details>
<summary>GlowShader operation reference (3 declarations)</summary>

#### Constructor

```java
public GlowShader()
```

Creates a new `GlowShader` using the built-in GLSL sources.

#### apply

```java
public void apply(Sprite sprite, float radiusPx, float intensity, float r, float g, float b, float a)
```

Binds the glow effect using the sprite's backing texture dimensions, draws
the sprite immediately, and unbinds the program on normal completion. Requires
a current OpenGL context. The previous shader is not restored, and exceptions
can leave this program bound.

- **`sprite`** — sprite with a valid backing texture
- **`radiusPx`** — glow sampling extent in texture pixels
- **`intensity`** — glow strength multiplier
- **`r`** — glow red component
- **`g`** — glow green component
- **`b`** — glow blue component
- **`a`** — glow alpha component

#### bind

```java
public void bind(float textureWidth, float textureHeight, float radiusPx, float intensity, float r, float g, float b, float a)
```

Configures and binds this shader program for rendering with a soft glow effect.

- **`textureWidth`** — the width of the texture in pixels
- **`textureHeight`** — the height of the texture in pixels
- **`radiusPx`** — the glow radius in texture pixels (>= 0)
- **`intensity`** — the glow strength multiplier (>= 0 recommended)
- **`r`** — the red component of the glow color
- **`g`** — the green component of the glow color
- **`b`** — the blue component of the glow color
- **`a`** — the alpha component of the glow color

</details>

<a id="type-lightingshader3d"></a>

### LightingShader3D

[Source](../../src/main/java/valthorne/graphics/shader/LightingShader3D.java#L16)

Specialized forward-lighting shader using tiled point-light lists and GGX
material shading. Shares the world-space mesh vertex contract with Mesh3DShader.
The lighting pipeline interprets albedo/tint as sRGB and computes lighting in
linear space, with hemisphere environment, filmic presentation, and filtered
directional shadows.

Construction requires the current OpenGL context. MeshBatch3D and Lighting3D
supply material uniforms, light buffers, tile lists, and texture bindings before
drawing. Program lifetime follows the Shader base class.

<details>
<summary>LightingShader3D operation reference (1 declarations)</summary>

#### Constructor

```java
public LightingShader3D()
```

Compiles the shared mesh vertex stage with the bundled tiled-lighting fragment
stage. Draw-specific light buffers, samplers, and uniforms are configured by
the rendering pipeline before use.

</details>

<a id="type-mesh3dshader"></a>

### Mesh3DShader

[Source](../../src/main/java/valthorne/graphics/shader/Mesh3DShader.java#L17)

Compatibility raster shader for world-space 3D mesh vertices. Supports vertex
color and albedo tinting, directional and up to eight point lights, directional
depth shadows, emissive color, projected 2D radiance, alpha cutout, and distance
fog. MeshBatch3D supplies world-space positions/normals and uploads the draw state;
the vertex stage applies only the combined camera matrix.

Construction compiles bundled GLSL on the current OpenGL context and establishes
sampler units zero for projected radiance, one for albedo, and two for shadows.
Other material/camera uniforms must be configured before drawing. The Shader
base class owns program disposal; borrowed textures remain caller-owned.

<details>
<summary>Mesh3DShader operation reference (20 declarations)</summary>

#### ATTR_POSITION

```java
public static final  int ATTR_POSITION
```

Attribute location for three-float world-space position.

#### ATTR_NORMAL

```java
public static final  int ATTR_NORMAL
```

Attribute location for three-float world-space normal.

#### ATTR_COLOR

```java
public static final  int ATTR_COLOR
```

Attribute location for four-float RGBA vertex color.

#### ATTR_UV

```java
public static final  int ATTR_UV
```

Attribute location for two-float albedo texture coordinates.

#### UNIFORM_MVP

```java
public static final  String UNIFORM_MVP
```

Combined camera matrix mapping world positions to clip space.

#### UNIFORM_LIGHT_DIR

```java
public static final  String UNIFORM_LIGHT_DIR
```

World-space directional-light vector, normalized by the fragment shader.

#### UNIFORM_CAMERA_POS

```java
public static final  String UNIFORM_CAMERA_POS
```

World-space camera position used for view-dependent shading and fog distance.

#### UNIFORM_FOG_COLOR

```java
public static final  String UNIFORM_FOG_COLOR
```

RGBA fog color; the fragment stage uses its RGB components.

#### UNIFORM_MATERIAL_TINT

```java
public static final  String UNIFORM_MATERIAL_TINT
```

RGBA multiplier applied to vertex and optional albedo texture color.

#### UNIFORM_MATERIAL_EMISSIVE

```java
public static final  String UNIFORM_MATERIAL_EMISSIVE
```

Emissive RGB with alpha used as its additive strength.

#### UNIFORM_MATERIAL_LIGHTING_MIX

```java
public static final  String UNIFORM_MATERIAL_LIGHTING_MIX
```

Blend amount between unlit base color and compatibility lighting.

#### UNIFORM_MATERIAL_FOG_MIX

```java
public static final  String UNIFORM_MATERIAL_FOG_MIX
```

Material multiplier for distance-fog contribution.

#### UNIFORM_MATERIAL_RADIANCE_MIX

```java
public static final  String UNIFORM_MATERIAL_RADIANCE_MIX
```

Material multiplier for projected 2D radiance contribution.

#### UNIFORM_LIGHT_TEXTURE

```java
public static final  String UNIFORM_LIGHT_TEXTURE
```

Projected world-light sampler assigned to texture unit zero.

#### UNIFORM_LIGHT_WORLD_MIN

```java
public static final  String UNIFORM_LIGHT_WORLD_MIN
```

World XY origin used to map fragments into the light texture.

#### UNIFORM_LIGHT_WORLD_SIZE

```java
public static final  String UNIFORM_LIGHT_WORLD_SIZE
```

World XY extent used to normalize light-texture coordinates.

#### UNIFORM_RADIANCE_STRENGTH

```java
public static final  String UNIFORM_RADIANCE_STRENGTH
```

Global strength of the projected radiance contribution.

#### UNIFORM_APPLY_RADIANCE

```java
public static final  String UNIFORM_APPLY_RADIANCE
```

Integer switch enabling projected radiance when equal to one.

#### Constructor

```java
public Mesh3DShader()
```

Loads and compiles the bundled mesh stages, binds the named position/normal/
color attributes, relinks, and initializes sampler-unit uniforms. The UV location
is declared explicitly in the vertex source. Leaves the shader unbound.

#### vertexSource

```java
public static String vertexSource()
```

Loads the shared world-space mesh vertex stage from bundled resources.
Also used by specialized 3D lighting shaders.

**Returns:** GLSL vertex source

**Throws `IllegalStateException`:** if the bundled resource is missing

</details>

<a id="type-outlineshader"></a>

### OutlineShader

[Source](../../src/main/java/valthorne/graphics/shader/OutlineShader.java#L49)

Draws a crisp outline around non-transparent pixels.

##### What this shader does

- Samples the center pixel alpha from `u_texture`.

- If the center pixel is opaque, renders the sprite normally (texture * vertex color).

- If the center pixel is transparent, samples neighboring alpha values.

- If any neighbor is opaque, outputs `u_outlineColor` to create an outline.

##### Best use

- Works best on sprites with clean alpha edges (hard-ish cutouts).

- `u_texelSize` must be `(1/textureWidth, 1/textureHeight)` for correct thickness.

- `u_thicknessPx` is measured in **source texture pixels**, not screen pixels.

##### Uniforms

- `u_texture` (sampler2D): texture unit index (use 0).

- `u_texelSize` (vec2): texel size (`1/width`, `1/height`).

- `u_thicknessPx` (float): outline thickness in source-texture pixels.

- `u_outlineColor` (vec4): outline RGBA color.

##### Output rules

- If center alpha &gt; ~0.001: `gl_FragColor = center * v_color`.

- Else if any neighbor alpha &gt; ~0.001: `gl_FragColor = u_outlineColor`.

- Else: transparent.

##### Usage

```java
OutlineShader outline = new OutlineShader();
Texture texture = ...;

outline.apply(texture, 1f, 0f, 0f, 0f, 1f); // 1px black outline
```

<details>
<summary>OutlineShader operation reference (3 declarations)</summary>

#### Constructor

```java
public OutlineShader()
```

Compiles and links the packaged outline fragment program with the shared
textured-quad vertex program. A current OpenGL context is required; dispose
the shader when it is no longer needed to release its native program.

#### apply

```java
public void apply(Sprite sprite, float thicknessPx, float r, float g, float b, float a)
```

Binds the outline effect using the sprite's backing texture dimensions, draws
the sprite immediately, and unbinds the program on normal completion. Requires
a current OpenGL context. The previous shader is not restored, and exceptions
can leave this program bound.

- **`sprite`** — sprite with a valid backing texture
- **`thicknessPx`** — outline sampling extent in texture pixels
- **`r`** — outline red component
- **`g`** — outline green component
- **`b`** — outline blue component
- **`a`** — outline alpha component

#### bind

```java
public void bind(float textureWidth, float textureHeight, float thicknessPx, float r, float g, float b, float a)
```

Binds the shader program and sets uniforms for texture dimensions, outline thickness,
and outline color. This method calculates the texel size based on the given texture
dimensions and updates the relevant uniforms using the given parameters.

- **`textureWidth`** — the width of the texture in pixels
- **`textureHeight`** — the height of the texture in pixels
- **`thicknessPx`** — thickness of the outline in pixels
- **`r`** — red component of the outline color (0.0 to 1.0)
- **`g`** — green component of the outline color (0.0 to 1.0)
- **`b`** — blue component of the outline color (0.0 to 1.0)
- **`a`** — alpha component of the outline color (0.0 to 1.0)

</details>

<a id="type-reflectionshader"></a>

### ReflectionShader

[Source](../../src/main/java/valthorne/graphics/shader/ReflectionShader.java#L28)

Renders a vertically mirrored, tinted sprite reflection with a fading alpha
and optional horizontal ripple. Sampling parameters use backing-texture pixels.
The shader owns its OpenGL program and requires a current context for creation,
drawing, and disposal.

Applying the effect temporarily moves and resizes the sprite below its original
bounds, then restores those bounds after successful drawing. Failures can leave
bounds changed or this program bound. Texture unit zero is used, and the previous
shader program is not restored.

```java
ReflectionShader reflection = new ReflectionShader();
// During rendering, with playerSprite already initialized:
playerSprite.draw();
reflection.apply(playerSprite, elapsedSeconds, 0.35f, 0.65f,
        0.6f, 0.75f, 1f, 2f, 18f, 2.5f);
// During graphics shutdown:
reflection.dispose();
```

<details>
<summary>ReflectionShader operation reference (2 declarations)</summary>

#### Constructor

```java
public ReflectionShader()
```

Creates a reflection shader using built-in GLSL 120 sources.

This compiles and links the shader program immediately via `Shader#Shader(String, String)`.

#### apply

```java
public void apply(Sprite sprite, float timeSeconds, float amount, float alpha, float tintR, float tintG, float tintB, float rippleAmpPx, float rippleFreq, float rippleSpeed)
```

Draws a vertically reflected copy directly below the sprite using a clamped
fraction of its height. Temporarily changes sprite bounds and restores them
only after successful drawing. A nonpositive clamped amount skips drawing;
exceptions can leave the bounds changed or the shader bound. The prior shader
program is not restored.

- **`sprite`** — sprite with a valid texture
- **`timeSeconds`** — animation time in seconds
- **`amount`** — fraction of sprite height to reflect, clamped to zero through one
- **`alpha`** — reflection opacity multiplier
- **`tintR`** — red reflection tint
- **`tintG`** — green reflection tint
- **`tintB`** — blue reflection tint
- **`rippleAmpPx`** — ripple displacement amplitude in texture pixels
- **`rippleFreq`** — spatial ripple frequency supplied to the shader
- **`rippleSpeed`** — temporal ripple speed supplied to the shader

**Throws `NullPointerException`:** if sprite is null

</details>

<a id="type-shader"></a>

### Shader

[Source](../../src/main/java/valthorne/graphics/shader/Shader.java#L58)

OpenGL shader program wrapper that compiles, links, validates, and manages a GLSL
vertex/fragment shader pair, with hot-reload support and cached lookups.

##### Example

```java
// Build
Shader shader = new Shader(VERT_SRC, FRAG_SRC);

// Optional: lock attribute indices BEFORE the first link (or call reload() after changing bindings)
shader.bindAttribLocation(0, "a_pos");
shader.bindAttribLocation(1, "a_uv");
shader.reload();

// Use
shader.bind();
shader.setUniform1i("u_tex0", 0);
shader.setUniform2f("u_resolution", Window.getWidth(), Window.getHeight());
shader.unbind();

// Cleanup
shader.dispose();
```

##### Features

- **Compile + link** of vertex + fragment shader sources into one program.

- **Hot reload** using stored sources or new sources via `reload()` / `reload(String, String)`.

- **Uniform caching** for `glGetUniformLocation` calls.

- **Attribute caching** for `glGetAttribLocation` calls.

- **Uniform block caching** for `glGetUniformBlockIndex` calls.

- **Explicit attribute bindings** via `bindAttribLocation(int, String)` (applied at link time).

##### Notes

- Attribute bindings must be set **before linking**. If you call `bindAttribLocation(int, String)`
after the program is already built, call `reload()` to relink and apply them.

- Uniforms/attributes may be optimized out by the GLSL compiler; their locations can be `-1`.

- `dispose()` deletes the program and attached shaders, and clears all caches.

<details>
<summary>Shader operation reference (20 declarations)</summary>

#### Constructor

```java
public Shader(String vertexSource, String fragmentSource)
```

Creates and builds a shader program from the provided GLSL sources.

This constructor compiles both stages and links them into a program immediately.

If you need explicit attribute locations, call `bindAttribLocation(int, String)`
**before** linking. Since this constructor links immediately, the typical pattern is:

```java
Shader s = new Shader(v, f);
s.bindAttribLocation(0, "a_pos");
s.bindAttribLocation(1, "a_uv");
s.reload(); // apply bindings by relinking
```

- **`vertexSource`** — GLSL vertex shader source (must not be null)
- **`fragmentSource`** — GLSL fragment shader source (must not be null)

**Throws `NullPointerException`:** if either source is null

**Throws `IllegalStateException`:** if compilation or linking fails

#### bind

```java
public void bind()
```

Binds this shader program.

This is a direct wrapper over `glUseProgram(programID)`.

Call this before setting uniforms and issuing draw calls that depend on this program.

#### unbind

```java
public void unbind()
```

Unbinds any active shader program.

This is a direct wrapper over `glUseProgram(0)`.

Unbinding is optional in many engines, but it can be useful for debugging state leaks
or when mixing fixed-function pipeline behavior with shaders.

#### reload

```java
public boolean reload()
```

Hot-reloads using the last known vertex/fragment sources.

If rebuilding succeeds, the program ids are replaced and uniform/attribute caches are cleared.

If rebuilding fails, the old program remains alive and this method returns false.

**Returns:** true if reload succeeded, false otherwise

#### reload

```java
public boolean reload(String newVertexSource, String newFragmentSource)
```

Hot-reloads using new sources.

This method updates the stored sources **only if** the rebuild succeeds.

If the build fails, it restores the old sources and keeps the old program alive,
so you can retry or fall back without losing a working program.

- **`newVertexSource`** — new vertex shader source (must not be null)
- **`newFragmentSource`** — new fragment shader source (must not be null)

**Returns:** true if reload succeeded, false if it failed

**Throws `NullPointerException`:** if either source is null

#### bindAttribLocation

```java
public void bindAttribLocation(int index, String name)
```

Requests an explicit attribute binding (name -> index).

This is applied during `buildProgram()` (before linking) via `glBindAttribLocation`.

If the program has already been linked, call `reload()` to relink and apply the new binding.

This method also clears any cached attribute location for `name`, since a relink can change it.

- **`index`** — attribute index you want this name to occupy
- **`name`** — attribute name as declared in GLSL (must not be null)

**Throws `NullPointerException`:** if name is null

#### getAttribLocation

```java
public int getAttribLocation(String name)
```

Returns the attribute location for the given attribute name.

This uses an internal cache to avoid repeated `glGetAttribLocation` calls.

Drivers may optimize out unused attributes, in which case OpenGL returns `-1`.

- **`name`** — attribute name in GLSL (must not be null)

**Returns:** attribute location, or -1 if not found/optimized out

**Throws `NullPointerException`:** if name is null

#### setUniform1i

```java
public Shader setUniform1i(String name, int v)
```

Sets an `int` uniform (1 component).

This method looks up the uniform location (cached) and calls `glUniform1i` if the location is valid.

If the uniform is optimized out (location `-1`), this method does nothing.

- **`name`** — uniform name (must not be null)
- **`v`** — value

**Returns:** this shader for chaining

**Throws `NullPointerException`:** if name is null

#### setUniform1f

```java
public Shader setUniform1f(String name, float v)
```

Sets a `float` uniform (1 component).

This method looks up the uniform location (cached) and calls `glUniform1f` if the location is valid.

If the uniform is optimized out (location `-1`), this method does nothing.

- **`name`** — uniform name (must not be null)
- **`v`** — value

**Returns:** this shader for chaining

**Throws `NullPointerException`:** if name is null

#### setUniform2f

```java
public Shader setUniform2f(String name, float x, float y)
```

Sets a `vec2` uniform.

This method looks up the uniform location (cached) and calls `glUniform2f` if the location is valid.

If the uniform is optimized out (location `-1`), this method does nothing.

- **`name`** — uniform name (must not be null)
- **`x`** — x component
- **`y`** — y component

**Returns:** this shader for chaining

**Throws `NullPointerException`:** if name is null

#### setUniform3f

```java
public void setUniform3f(String name, float x, float y, float z)
```

Sets a `vec3` uniform.

This method looks up the uniform location (cached) and calls `glUniform3f` if the location is valid.

If the uniform is optimized out (location `-1`), this method does nothing.

- **`name`** — uniform name (must not be null)
- **`x`** — x component
- **`y`** — y component
- **`z`** — z component

**Throws `NullPointerException`:** if name is null

#### setUniform4f

```java
public Shader setUniform4f(String name, float x, float y, float z, float w)
```

Sets a `vec4` uniform.

This method looks up the uniform location (cached) and calls `glUniform4f` if the location is valid.

If the uniform is optimized out (location `-1`), this method does nothing.

- **`name`** — uniform name (must not be null)
- **`x`** — x component
- **`y`** — y component
- **`z`** — z component
- **`w`** — w component

**Returns:** this shader for chaining

**Throws `NullPointerException`:** if name is null

#### setUniformMatrix4

```java
public Shader setUniformMatrix4(String name, float[] values)
```

Sets a `mat4` uniform from a column-major float array.

This is primarily used by renderers that manage projection or transform
matrices on the Java side instead of relying on OpenGL's fixed-function
matrix stack.

If the uniform is optimized out (location `-1`), this method does
nothing.

- **`name`** — uniform name (must not be null)
- **`values`** — matrix values in column-major order (must contain at least 16 floats)

**Returns:** this shader for chaining

**Throws `NullPointerException`:** if `name` or `values` is null

**Throws `IllegalArgumentException`:** if `values.length < 16`

#### getUniformBlockIndex

```java
public int getUniformBlockIndex(String blockName)
```

Gets the uniform block index for a named uniform block.

This uses an internal cache to avoid repeated `glGetUniformBlockIndex` calls.

If the block is not present (or was optimized out), OpenGL may return `GL_INVALID_INDEX`.
This method normalizes that to `-1`.

- **`blockName`** — uniform block name in GLSL (must not be null)

**Returns:** block index, or -1 if not found/optimized out

**Throws `NullPointerException`:** if blockName is null

#### bindUniformBlock

```java
public boolean bindUniformBlock(String blockName, int bindingPoint)
```

Binds a named uniform block to a binding point.

This is a convenience wrapper for:

- `getUniformBlockIndex(String)` to resolve the block index

- `glUniformBlockBinding(programID, blockIndex, bindingPoint)`

Typical usage is to bind the block to a fixed binding point, then bind your UBO to the same point:

```java
shader.bindUniformBlock("Globals", 0);
glBindBufferBase(GL_UNIFORM_BUFFER, 0, uboId);
```

- **`blockName`** — uniform block name in GLSL (must not be null)
- **`bindingPoint`** — binding point index used with glBindBufferBase

**Returns:** true if the block exists and was bound, false if not found/optimized out

**Throws `NullPointerException`:** if blockName is null

#### bindUniformBlock

```java
public void bindUniformBlock(int blockIndex, int bindingPoint)
```

Binds a uniform block (by index) to a binding point.

This is useful when you already cached the block index yourself.

If `blockIndex < 0`, this method does nothing.

- **`blockIndex`** — uniform block index (-1 does nothing)
- **`bindingPoint`** — binding point index

#### getProgramID

```java
public int getProgramID()
```

Returns the OpenGL program id.

Useful for advanced interop (manual uniform setting, pipeline debugging, etc.).

**Returns:** program id (0 if disposed or never built)

#### getVertexSource

```java
public String getVertexSource()
```

Returns the last stored vertex shader source.

This is the source used when calling `reload()`.

**Returns:** vertex shader source (may be null only if you deliberately pass null via reflection, etc.)

#### getFragmentSource

```java
public String getFragmentSource()
```

Returns the last stored fragment shader source.

This is the source used when calling `reload()`.

**Returns:** fragment shader source (may be null only if you deliberately pass null via reflection, etc.)

#### dispose

```java
public void dispose()
```

Deletes the program and shader objects and clears all caches/bindings.

This method is safe to call multiple times.

It will unbind the program first to avoid leaving OpenGL bound to a deleted id.

</details>

<a id="type-shadersources"></a>

### ShaderSources

[Source](../../src/main/java/valthorne/graphics/shader/ShaderSources.java#L16)

Reads bundled UTF-8 GLSL stages and templates below /valthorne/shaders/.
Uses classpath streams so the same paths work from development resources and
packaged JARs. Each call reads fresh bytes and closes its stream; source caching,
preprocessing, compilation, and OpenGL access belong to callers.

<details>
<summary>ShaderSources operation reference (1 declarations)</summary>

#### load

```java
public static String load(String name)
```

Reads a complete bundled shader as UTF-8, closing the resource stream even
when reading fails. Concatenates name with the fixed resource prefix without
filesystem access or path normalization.

- **`name`** — nonnull path relative to /valthorne/shaders/, including extension

**Returns:** complete shader source

**Throws `NullPointerException`:** if name is null

**Throws `IllegalStateException`:** if the resource is missing

**Throws `UncheckedIOException`:** if reading or closing the resource fails

</details>

<a id="type-shapeshader"></a>

### ShapeShader

[Source](../../src/main/java/valthorne/graphics/shader/ShapeShader.java#L29)

A simple, general-purpose shader for drawing `Shape` polygons with explicit
vertex buffers and projection uniforms.

Filled polygons are rendered as triangle fans built from the shape centroid and
border paths are rendered as line loops when requested.

<details>
<summary>ShapeShader operation reference (4 declarations)</summary>

#### Constructor

```java
public ShapeShader()
```

Compiles the bundled polygon shader and allocates an owned VAO/VBO with
two-float position attributes. Requires a current GL context and leaves array
bindings at zero.

#### draw

```java
public void draw(Shape shape)
```

Draws a shape's centroid triangle fan and optional positive-width line-loop
border using the current Window projection. Skips null shapes and boundaries
with fewer than three points. Fan filling assumes a suitable polygon and does
not tessellate arbitrary concavity. Leaves the shader and vertex array unbound;
border drawing changes GL line width.

- **`shape`** — borrowed polygon to draw

#### drawAll

```java
public void drawAll(Collection<? extends Shape> shapes)
```

Draws valid shapes in collection order under one shader bind. Each shape still
uploads and draws its own fan and optional border. Null collection, empty input,
null entries, or fewer than three points are skipped.

- **`shapes`** — borrowed polygon collection

#### dispose

```java
    public void dispose()
```

Disposes the shader program and owned vertex-array/buffer names. Requires
the GL context. This override does not clear its numeric object names, so
callers should dispose the instance once.

</details>

<a id="type-texturedquadshader"></a>

### TexturedQuadShader

[Source](../../src/main/java/valthorne/graphics/shader/TexturedQuadShader.java#L13)

Shared shader base for textured quad rendering with explicit attributes and uniforms.

This contract is used by immediate quad renderers such as `Sprite.draw()`,
framebuffer blits, and standalone sprite effect shaders that should work on modern
OpenGL core profiles without relying on fixed-function state.

<details>
<summary>TexturedQuadShader operation reference (10 declarations)</summary>

#### ATTR_POSITION

```java
public static final  int ATTR_POSITION
```

Attribute location for two-component quad position.

#### ATTR_UV

```java
public static final  int ATTR_UV
```

Attribute location for two-component texture coordinates.

#### ATTR_COLOR

```java
public static final  int ATTR_COLOR
```

Attribute location for RGBA vertex tint.

#### UNIFORM_MVP

```java
public static final  String UNIFORM_MVP
```

Uniform name for the quad model/view/projection matrix.

#### UNIFORM_TEXTURE

```java
public static final  String UNIFORM_TEXTURE
```

Uniform name for the sampled 2D texture.

#### Constructor

```java
public TexturedQuadShader()
```

Compiles the default bundled textured-quad vertex and fragment stages.
Requires a current OpenGL context; drawing code supplies uniforms and textures.

#### Constructor

```java
public TexturedQuadShader(String fragmentSource)
```

Compiles a custom fragment stage with the default vertex contract.

- **`fragmentSource`** — GLSL fragment source, or null/blank for the default

#### Constructor

```java
public TexturedQuadShader(String vertexSource, String fragmentSource)
```

Compiles caller-supplied stages, independently substituting defaults for
null or blank arguments. Custom stages must match the drawing helper's
position, UV, color, sampler, and projection contract.

- **`vertexSource`** — custom vertex GLSL, or null/blank
- **`fragmentSource`** — custom fragment GLSL, or null/blank

#### defaultVertexSource

```java
protected static String defaultVertexSource()
```

Returns the cached bundled vertex source without another resource read.

**Returns:** default vertex GLSL

#### defaultFragmentSource

```java
protected static String defaultFragmentSource()
```

Returns the cached bundled fragment source without another resource read.

**Returns:** default fragment GLSL

</details>

<a id="type-watershader"></a>

### WaterShader

[Source](../../src/main/java/valthorne/graphics/shader/WaterShader.java#L58)

Simple UV-distortion "water" shader (wobble / ripple) for 2D sprites.

##### What this shader does

- Samples the base texture normally, but perturbs the UV coordinates over time.

- Uses two sine waves:

- A horizontal wave that offsets `uv.y` based on `v_uv.x`.

- A vertical wave that offsets `uv.x` based on `v_uv.y`.

- The result is a continuous animated ripple that looks like water distortion.

##### Coordinate / unit notes

- `u_texelSize` must be `(1/textureWidth, 1/textureHeight)`.

- `u_amp` is in **pixels**. The shader converts it to UV units via `u_texelSize * u_amp`.

- `u_freq` is frequency in UV space (larger = more ripples).

- `u_speed` scales how fast the ripples move over time.

##### Uniforms

- `u_texture` (sampler2D): texture unit index (use 0).

- `u_texelSize` (vec2): texel size (`1/width`, `1/height`).

- `u_time` (float): time in seconds (e.g., `JGL.getTime()`).

- `u_amp` (float): distortion amplitude in pixels.

- `u_freq` (float): ripple frequency.

- `u_speed` (float): ripple speed multiplier.

##### Visual behavior

- Amplitude too high can cause UVs to sample outside the sprite and smear/warp edges.

- Because this uses raw `texture2D` sampling, you may see edge bleed unless your atlas
has padding or you clamp regions appropriately.

##### Usage

```java
WaterShader water = new WaterShader();
Texture waterTexture = ...;

water.apply(waterTexture, JGL.getTime(), 3f, 18f, 2.0f);
// draw sprite(s)...
water.unbind(); // optional
```

<details>
<summary>WaterShader operation reference (3 declarations)</summary>

#### FRAG_SRC

```java
public static final  String FRAG_SRC
```

Fragment source loaded from the packaged water effect shader resource.

#### Constructor

```java
public WaterShader()
```

Creates a new `WaterShader` using the built-in GLSL sources.

#### apply

```java
public void apply(Sprite sprite, float timeSeconds, float ampPx, float freq, float speed)
```

Binds the water program and configures distortion uniforms from the sprite's
backing texture dimensions. Leaves the shader bound for a subsequent draw;
this method does not itself render the sprite or unbind the program. Requires
a current OpenGL context and a sprite with a valid, nonzero-size texture.

- **`sprite`** — sprite supplying texture dimensions
- **`timeSeconds`** — animation time in seconds
- **`ampPx`** — distortion amplitude in texture pixels
- **`freq`** — spatial wave frequency supplied to the shader
- **`speed`** — temporal wave speed supplied to the shader

</details>

## Related guides

- [Textures, sprites, atlases, and batching](textures.md)
- [3D models, materials, scenes, and billboards](models.md)
- [Screen-space radiance cascades](radiance-cascades.md)
