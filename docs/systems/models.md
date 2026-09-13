# 3D models, materials, scenes, and billboards

Author: Albert Beaupre

[System manual](README.md)

## Purpose

The 3D model system separates reusable geometry from instances, materials, scene organization, and rendering. Model3D is shared mesh data; ModelInstance3D adds transform state. SceneNode3D builds a hierarchy, Scene3D collects scene content, and render batches submit geometry with the required pass and state.

## Features and when to use them

| Feature | Purpose |
| --- | --- |
| Geometry creation | ModelBuilder3D creates common meshes; OBJ/MTL loading imports grouped materials and geometry. |
| Instances and hierarchy | Instances and parent transforms let multiple objects share geometry. |
| Materials and passes | Material3D selects appearance inputs and opaque/transparent behavior. |
| Procedural geometry | ProceduralRenderable3D emits geometry through a callback without requiring a fixed imported mesh. |
| Billboards | Billboard sprites orient textured quads toward a camera using a selected facing mode. |
| Picking and bounds | World bounds and pick results connect camera rays to scene selection. |
| Conservative camera culling | Frustum and opaque-triangle tests avoid eligible submissions while shadow passes retain their required geometry. |

## Getting started

1. Create or load shared geometry and its backing textures.
2. Create separate instances for objects that need independent transforms or materials.
3. Attach instances to a scene or hierarchy and update transforms before rendering.
4. Submit using the chosen renderer and camera, preserving ordering for transparency and overlays.

## Ownership and lifecycle

Scene membership does not automatically imply resource ownership. Dispose shared geometry/textures only after every dependent instance stops using them. Accessors for transforms, materials, or bounds may return live state rather than snapshots.

## Important behavior

- Changing mutable pose data can require rebuilding derived transforms/bounds.
- Use inverse-transpose normal handling for nonuniform scale; singular transforms are invalid for those operations.
- Keep billboard orientation and geometry axis conventions consistent with the camera.
- ModelBatch3D uses earlier accepted opaque submissions as occluders for later model candidates. Submit large untextured opaque walls first; disabling general culling also bypasses occlusion. Procedural renderables and billboards do not use this model occlusion test.

## Components and examples

The sections below explain each component and its declared public or protected operations. Expand an operation reference for signatures, parameters, results, and edge cases. Inherited operations are documented with their base type. Examples embedded in component descriptions are integration fragments: supply the surrounding resources and application variables they name.

- [`BillboardBatch3D`](#type-billboardbatch3d)
- [`BillboardMode3D`](#type-billboardmode3d)
- [`BillboardRenderable3D`](#type-billboardrenderable3d)
- [`BillboardSprite3D`](#type-billboardsprite3d)
- [`Material3D`](#type-material3d)
- [`MeshBatch3D`](#type-meshbatch3d)
- [`MeshRenderable3D`](#type-meshrenderable3d)
- [`MeshRenderState3D`](#type-meshrenderstate3d)
- [`Model3D`](#type-model3d)
- [`Model3D.Triangle`](#type-model3d-triangle)
- [`ModelBatch3D`](#type-modelbatch3d)
- [`ModelBuilder3D`](#type-modelbuilder3d)
- [`ModelInstance3D`](#type-modelinstance3d)
- [`ModelLoader`](#type-modelloader)
- [`ModelParameters`](#type-modelparameters)
- [`ObjModel3D`](#type-objmodel3d)
- [`ObjModel3D.Resolver`](#type-objmodel3d-resolver)
- [`ObjModel3D.Part`](#type-objmodel3d-part)
- [`PickResult3D`](#type-pickresult3d)
- [`ProceduralMeshEmitter3D`](#type-proceduralmeshemitter3d)
- [`ProceduralRenderable3D`](#type-proceduralrenderable3d)
- [`Renderable3D`](#type-renderable3d)
- [`RenderPass3D`](#type-renderpass3d)
- [`RenderStateSnapshot3D`](#type-renderstatesnapshot3d)
- [`Scene3D`](#type-scene3d)
- [`SceneNode3D`](#type-scenenode3d)

<a id="type-billboardbatch3d"></a>

### BillboardBatch3D

[Source](../../src/main/java/valthorne/graphics/model/BillboardBatch3D.java#L54)

Accumulates camera-facing textured quads as world-space triangles and streams
them through one owned OpenGL vertex buffer. A batch uses one borrowed texture
and one render-state material; texture regions may vary as long as they refer
to that same texture object. Each vertex contains XYZ position, UV, and RGBA.

Call begin with the texture, append sprites with draw using the current camera,
then render with a matching camera state. Spherical billboards use camera right
and up; cylindrical billboards remain aligned with world Z. Geometry is computed
when appended, so rebuild it after camera changes. This class does not cull or
sort sprites; submit translucent sprites in the desired draw order.

Construction, rendering, and disposal require a current OpenGL context.
Rendering restores captured GL state; construction leaves array bindings at zero.
The shader, vertex array, and GPU buffer are owned; textures, sprites, cameras,
and materials remain borrowed. Shared scratch vectors preclude concurrent use.

```java
BillboardBatch3D batch = new BillboardBatch3D();
try {
    batch.begin(sprite.getTextureRegion().getTexture());
    batch.draw(sprite, camera);
    batch.render(new MeshRenderState3D().setCamera(camera));
} finally {
    batch.dispose();
}
```

<details>
<summary>BillboardBatch3D operation reference (6 declarations)</summary>

#### Constructor

```java
public BillboardBatch3D()
```

Creates an empty batch with an initial capacity of 98,304 floats in CPU and
GPU storage, compiling the billboard shader on the current GL context.

#### Constructor

```java
public BillboardBatch3D(int initialCapacityFloats)
```

Allocates CPU/GPU storage and configures the nine-float vertex layout.
Capacity grows as sprites are appended and is measured in floats, not bytes
or sprite count.

- **`initialCapacityFloats`** — capacity of at least 54 floats for one billboard

**Throws `IllegalArgumentException`:** if capacity cannot hold one billboard

#### begin

```java
public void begin(Texture texture)
```

Selects a borrowed texture and clears previously accumulated CPU vertices.
Does not draw or dispose the previous texture. Call again when changing
texture groups or rebuilding camera-dependent geometry.

- **`texture`** — nonnull shared texture for subsequent sprites

**Throws `NullPointerException`:** if texture is null

**Throws `IllegalStateException`:** if this batch is disposed

#### draw

```java
public void draw(BillboardSprite3D billboard, Camera3D camera)
```

Appends two triangles using the sprite's size, anchor, color, region UVs,
position, and billboard mode. Copies values immediately without retaining
the sprite. Null sprite/camera/region values emit nothing; valid sprites
require begin and an identical texture reference. Does not check visibility.

- **`billboard`** — sprite to append
- **`camera`** — camera whose current orientation determines the quad basis

**Throws `IllegalStateException`:** if no texture has been selected

**Throws `IllegalArgumentException`:** if the sprite region uses a different texture object

#### render

```java
public void render(MeshRenderState3D state)
```

Streams and draws all accumulated vertices without clearing them. A batch
with no vertices or no selected texture returns without using the state.
Restores the bindings and capabilities covered by the render snapshot on
success or failure.

- **`state`** — material, camera, fog, and radiance configuration

**Throws `IllegalStateException`:** if disposed or a nonempty draw has no camera

**Throws `NullPointerException`:** if state is null for a nonempty draw

#### dispose

```java
public void dispose()
```

Marks the batch disposed and deletes its shader, vertex buffer, and vertex
array. Repeated calls have no effect. Call with the appropriate context current;
the borrowed texture is not disposed.

</details>

<a id="type-billboardmode3d"></a>

### BillboardMode3D

[Source](../../src/main/java/valthorne/graphics/model/BillboardMode3D.java#L16)

Controls the camera-derived basis used to expand a `BillboardSprite3D`
into a world-space quad. `BillboardBatch3D` applies the selected basis
to the sprite's width, height and anchor offsets around its position.
Orientation follows camera axes rather than a direction computed separately
from each sprite toward the camera's position.

The cylindrical mode preserves the engine's Z-up vertical axis. The
spherical mode follows both camera right and camera up, including camera roll.
Sprite bounds are computed conservatively for the selected orientation mode.

<details>
<summary>BillboardMode3D operation reference (2 declarations)</summary>

#### CYLINDRICAL

```java
public static final  BillboardMode3D CYLINDRICAL
```

Keeps the quad upright along world +Z and projects camera right onto the
XY plane. If that horizontal vector degenerates, the batch tries a vector
perpendicular to the camera's horizontal direction, then world +X.
This is the default mode for `BillboardSprite3D`.

#### SPHERICAL

```java
public static final  BillboardMode3D SPHERICAL
```

Uses normalized camera right and up vectors directly, allowing the quad
to tilt and roll with the view. Suitable for freely camera-facing particles.

</details>

<a id="type-billboardrenderable3d"></a>

### BillboardRenderable3D

[Source](../../src/main/java/valthorne/graphics/model/BillboardRenderable3D.java#L19)

Defines textured billboard emission for a `Renderable3D`. The model
batch uses the region's texture to group compatible submissions and passes
the active camera when emitting geometry. Implementations supply their own
position, dimensions and orientation behavior through the emission callback.

A null region or a region without a texture causes `ModelBatch3D`
to reject the submission. Keep the region and backing texture stable until
batch end because the renderer reads them again while grouping queued objects.
The interface does not allocate, upload or dispose textures.

<details>
<summary>BillboardRenderable3D operation reference (2 declarations)</summary>

#### getTextureRegion

```java
TextureRegion getTextureRegion()
```

Supplies the region whose backing texture is used for this submission's
billboard batch. The renderer borrows this object and does not transfer
texture ownership. Returning null marks the billboard as lacking drawable
texture data for model-batch submission.

**Returns:** the active texture region, or null when no region is assigned

#### emit

```java
void emit(BillboardBatch3D billboardBatch, Camera3D camera)
```

Appends billboard geometry to a batch prepared for the texture returned
by `getTextureRegion()`. Use the supplied camera to establish the
intended facing basis. Do not clear, reinitialize, render or dispose the
shared destination; the model batch owns those operations.

- **`billboardBatch`** — the nonnull destination for compatible billboard vertices
- **`camera`** — the active camera used to orient this object's geometry

</details>

<a id="type-billboardsprite3d"></a>

### BillboardSprite3D

[Source](../../src/main/java/valthorne/graphics/model/BillboardSprite3D.java#L29)

Camera-facing textured quad positioned by an anchor in 3D world space.
Defaults are unit width and height, a bottom-center anchor (0.5, 0), white
color, cylindrical facing around Z, visibility enabled and no texture region.
The initial material uses the translucent pass with depth writes disabled.

Size is in world units and anchor coordinates are fractions of those
dimensions; anchors outside zero through one are permitted. Cylindrical mode
stays upright along Z, while other modes use the batch's camera-facing basis.
Bounds conservatively enclose supported orientations rather than just the
current camera-facing quad.

Position and color getters expose mutable owned values. Region and material
references are borrowed, shared and never disposed by this sprite. Bounds are
rebuilt into reused storage on each query, including changes made through
the position vector. Use on the rendering thread without concurrent mutation.

<details>
<summary>BillboardSprite3D operation reference (23 declarations)</summary>

#### set

```java
public BillboardSprite3D set(BillboardSprite3D other)
```

Copies position, color and scalar settings from another sprite while sharing its material and region. Cached bounds are not copied; a later query rebuilds them. Self-assignment is supported.

- **`other`** — the nonnull source sprite

**Returns:** this sprite

**Throws `NullPointerException`:** if other is null

#### getMaterial

```java
    public Material3D getMaterial()
```

Returns the live material reference used for pass and rendering configuration. Changes affect every sprite sharing that material.

**Returns:** the nonnull borrowed material

#### setMaterial

```java
public BillboardSprite3D setMaterial(Material3D material)
```

Retains a replacement material without copying it or changing any of its settings. The old material is not disposed.

- **`material`** — the nonnull shared material

**Returns:** this sprite

**Throws `NullPointerException`:** if material is null

#### getTextureRegion

```java
    public TextureRegion getTextureRegion()
```

Returns the live region reference used for texture selection and UV coordinates.

**Returns:** the borrowed region, or null when unset

#### setTextureRegion

```java
public BillboardSprite3D setTextureRegion(TextureRegion region)
```

Retains an optional region without changing world dimensions. A null region makes visibility queries fail and produces empty bounds.

- **`region`** — the borrowed texture region, or null

**Returns:** this sprite

#### getPosition

```java
public Vector3f getPosition()
```

Exposes the mutable world anchor position. Direct changes are observed by drawing and the next bounds query.

**Returns:** the owned live position vector

#### setPosition

```java
public BillboardSprite3D setPosition(float x, float y, float z)
```

Assigns the world anchor position without finiteness validation. Supply finite values for meaningful bounds and rendering.

- **`x`** — the world X coordinate
- **`y`** — the world Y coordinate
- **`z`** — the world Z coordinate

**Returns:** this sprite

#### getWidth

```java
public float getWidth()
```

Reads horizontal size in the billboard's local facing basis, independently of texture pixel dimensions.

**Returns:** the configured world-unit width

#### getHeight

```java
public float getHeight()
```

Reads vertical size in the billboard's local facing basis, independently of texture pixel dimensions.

**Returns:** the configured world-unit height

#### setSize

```java
public BillboardSprite3D setSize(float width, float height)
```

Assigns quad dimensions after rejecting values at or below zero. NaN and positive infinity are not explicitly rejected; callers should supply finite positive extents.

- **`width`** — the horizontal extent in world units
- **`height`** — the vertical extent in world units

**Returns:** this sprite

**Throws `IllegalArgumentException`:** if either extent compares at or below zero

#### getAnchorX

```java
public float getAnchorX()
```

Reads the horizontal anchor fraction: zero aligns the left edge and one the right edge with the world position.

**Returns:** the stored horizontal fraction, not clamped

#### getAnchorY

```java
public float getAnchorY()
```

Reads the vertical anchor fraction: zero aligns the bottom edge and one the top edge with the world position.

**Returns:** the stored vertical fraction, not clamped

#### setAnchor

```java
public BillboardSprite3D setAnchor(float anchorX, float anchorY)
```

Assigns anchor fractions without clamping or validation. Local edges are computed by subtracting anchor times size, allowing the pivot to lie outside the quad.

- **`anchorX`** — the horizontal anchor fraction
- **`anchorY`** — the vertical anchor fraction

**Returns:** this sprite

#### getMode

```java
public BillboardMode3D getMode()
```

Returns the orientation policy used by the batch and conservative bounds calculation.

**Returns:** the nonnull billboard mode

#### setMode

```java
public BillboardSprite3D setMode(BillboardMode3D mode)
```

Changes orientation policy without changing anchor, position or dimensions. Bounds reflect it on the next query.

- **`mode`** — the nonnull facing mode

**Returns:** this sprite

**Throws `NullPointerException`:** if mode is null

#### getColor

```java
public Color getColor()
```

Exposes the mutable per-sprite color, independently of the material's tint. Direct changes affect later draws.

**Returns:** the owned live color

#### setColor

```java
public BillboardSprite3D setColor(Color color)
```

Copies color components into owned storage without retaining the source object.

- **`color`** — the nonnull source color

**Returns:** this sprite

**Throws `NullPointerException`:** if color is null

#### isRenderableVisible

```java
public boolean isRenderableVisible()
```

Reports only the explicit flag, without requiring a region or testing the camera frustum.

**Returns:** whether the sprite is explicitly enabled for visibility

#### setVisible

```java
public BillboardSprite3D setVisible(boolean visible)
```

Changes the explicit visibility flag without clearing geometry or material state.

- **`visible`** — the desired visibility flag

**Returns:** this sprite

#### getWorldBounds

```java
public AABBf getWorldBounds()
```

Rebuilds and returns conservative world bounds in reused storage. Missing region produces empty bounds. Copy the result if it must survive later queries; do not treat it as independently owned.

**Returns:** the borrowed, freshly rebuilt bounds

#### isVisible

```java
    public boolean isVisible(Camera3D camera)
```

Rejects an explicitly hidden sprite, null camera or missing region, then tests the camera's current frustum. Does not rebuild camera matrices.

- **`camera`** — the camera supplying current frustum planes, possibly null

**Returns:** whether the sprite is potentially visible

#### isVisible

```java
public boolean isVisible(FrustumIntersection frustum)
```

Tests conservative bounds with padding against the supplied frustum. Hidden sprites, null frusta and missing regions return false. A positive result need not mean exact quad intersection.

- **`frustum`** — the current frustum, possibly null

**Returns:** whether padded bounds survive culling

#### emit

```java
    public void emit(BillboardBatch3D billboardBatch, Camera3D camera)
```

Delegates geometry emission to the batch without checking this sprite's visible flag. The batch skips a null camera or missing region; otherwise it requires an active batch with the region's texture.

- **`billboardBatch`** — the nonnull destination batch
- **`camera`** — the facing camera, or null to skip

**Throws `NullPointerException`:** if billboardBatch is null

**Throws `IllegalStateException`:** if drawing requires a batch that has not begun

**Throws `IllegalArgumentException`:** if the region texture differs from the active batch texture

</details>

<a id="type-material3d"></a>

### Material3D

[Source](../../src/main/java/valthorne/graphics/model/Material3D.java#L34)

Mutable surface and render-state configuration shared by the built-in 3D
pipelines. Individual renderers consume different subsets: raster batches use
pass, tint, texture and state settings, while the path tracer also reads
dielectric transmission, index of refraction and the HDR emission multiplier.
Assigning values does not itself bind GL state or update previously captured scene data.

Defaults are opaque pass, white tint, transparent emission, all mix factors
one, depth test/write enabled, back-face culling disabled, no texture, alpha
cutoff 0.001, shadow casting/receiving enabled, roughness 0.65, metallic and
transmission zero, index of refraction 1.5, emission strength one, and Filament's
implicit emission light enabled.

```java
Material3D material = new Material3D()
        .setRenderPass(RenderPass3D.TRANSLUCENT)
        .setTint(new Color(1f, 1f, 1f, 0.5f))
        .setDepthWrite(false);
```

Tint and emissive colors are owned mutable objects exposed directly by
getters. Textures are borrowed and never disposed here; copies share the texture
but have independent color storage. Sharing a material shares all later mutations.
Set render pass before an explicit depth-write override, because pass assignment
resets that flag. Instances provide no synchronization or change notifications.

<details>
<summary>Material3D operation reference (41 declarations)</summary>

#### getTransmission

```java
public float getTransmission()
```

Reads the path-tracer dielectric transmission fraction. Zero means no transmitted contribution; this does not imply any raster blending mode.

**Returns:** the transmission value in zero through one

#### setTransmission

```java
public Material3D setTransmission(float value)
```

Sets dielectric transmission without changing render pass, tint alpha or depth policy. Invalid values leave the previous setting unchanged.

- **`value`** — the finite fraction in zero through one

**Returns:** this material

**Throws `IllegalArgumentException`:** if value is non-finite or outside zero through one

#### getIndexOfRefraction

```java
public float getIndexOfRefraction()
```

Reads the dielectric index used for refraction independently of the transmission amount.

**Returns:** the finite index, at least one

#### setIndexOfRefraction

```java
public Material3D setIndexOfRefraction(float value)
```

Assigns a dielectric index without enabling transmission automatically.

- **`value`** — the finite refraction index, at least one

**Returns:** this material

**Throws `IllegalArgumentException`:** if value is non-finite or below one

#### getEmissionStrength

```java
public float getEmissionStrength()
```

Reads the separate HDR emission multiplier, which the path-tracing scene multiplies by emissive alpha.

**Returns:** the finite nonnegative multiplier

#### setEmissionStrength

```java
public Material3D setEmissionStrength(float value)
```

Sets the separate HDR emission multiplier without rewriting emissive RGB or alpha. Renderer support for this parameter is independent of packed emissive color.

- **`value`** — the finite nonnegative emission multiplier

**Returns:** this material

**Throws `IllegalArgumentException`:** if value is non-finite or negative

#### isEmissionLightEnabled

```java
public boolean isEmissionLightEnabled()
```

Reports whether Filament creates its legacy point light from this mesh's
emission. Defaults to true; visible emissive radiance is independent.

#### setEmissionLightEnabled

```java
public Material3D setEmissionLightEnabled(boolean enabled)
```

Enables Filament's implicit mesh point light without changing visible glow.
Disable when an explicit scene or particle light supplies illumination, so
an emissive mesh does not create an additional light or shadow map.
Path-traced emission remains physically emissive regardless of this flag.

- **`enabled`** — whether Filament derives a point light from mesh emission

**Returns:** this material

#### getRoughness

```java
public float getRoughness()
```

Reads the surface roughness consumed by supporting lighting and path-tracing shaders.

**Returns:** the roughness in zero through one

#### setRoughness

```java
public Material3D setRoughness(float value)
```

Assigns normalized roughness; no other material properties are inferred or changed.

- **`value`** — the finite roughness in zero through one

**Returns:** this material

**Throws `IllegalArgumentException`:** if value is non-finite or outside zero through one

#### getMetallic

```java
public float getMetallic()
```

Reads the metallic surface fraction without changing other surface parameters.

**Returns:** the metallic fraction in zero through one

#### setMetallic

```java
public Material3D setMetallic(float value)
```

Assigns the metallic fraction independently of tint, roughness and transmission.

- **`value`** — the finite metallic fraction in zero through one

**Returns:** this material

**Throws `IllegalArgumentException`:** if value is non-finite or outside zero through one

#### isCastsShadow

```java
public boolean isCastsShadow()
```

Reads permission to submit this material to shadow rendering. The render pipeline can impose additional restrictions, such as opaque-pass membership.

**Returns:** the shadow-casting flag

#### setCastsShadow

```java
public Material3D setCastsShadow(boolean enabled)
```

Changes shadow-casting permission without changing render pass or scene membership.

- **`enabled`** — whether shadow submission is permitted

**Returns:** this material

#### isReceivesShadow

```java
public boolean isReceivesShadow()
```

Reads whether supporting raster lighting should apply an available shadow map.

**Returns:** the shadow-receiving flag

#### setReceivesShadow

```java
public Material3D setReceivesShadow(boolean enabled)
```

Changes shadow-receiving permission without enabling lighting or creating a shadow map.

- **`enabled`** — whether available shadow maps may affect this material

**Returns:** this material

#### getTexture

```java
public Texture getTexture()
```

Returns the borrowed diffuse texture directly. The material does not manage its native lifetime.

**Returns:** the assigned texture, or null when untextured

#### setTexture

```java
public Material3D setTexture(Texture texture)
```

Retains an optional diffuse texture without copying, uploading or disposing either the new or previous resource.

- **`texture`** — the borrowed texture, or null

**Returns:** this material

#### getAlphaCutoff

```java
public float getAlphaCutoff()
```

Reads the threshold used by supporting shaders to discard combined texture, vertex and tint alpha at or below the cutoff.

**Returns:** the finite cutoff in zero through one

#### setAlphaCutoff

```java
public Material3D setAlphaCutoff(float cutoff)
```

Sets the alpha-discard threshold without changing render pass or enabling blending.

- **`cutoff`** — the finite threshold in zero through one

**Returns:** this material

**Throws `IllegalArgumentException`:** if cutoff is non-finite or outside zero through one

#### getRenderPass

```java
public RenderPass3D getRenderPass()
```

Reads the pass used for ordering and batch render-state selection.

**Returns:** the nonnull configured pass

#### setRenderPass

```java
public Material3D setRenderPass(RenderPass3D renderPass)
```

Assigns the pass and unconditionally resets depthWrite to true only for OPAQUE. Even reassigning the same pass replaces an earlier explicit depth-write override.
Filament uses true alpha compositing for TRANSLUCENT; transmission on an
opaque-pass material independently selects its refractive glass pipeline.

- **`renderPass`** — the nonnull desired pass

**Returns:** this material

**Throws `NullPointerException`:** if renderPass is null

#### getTint

```java
public Color getTint()
```

Exposes the owned mutable RGBA tint; direct changes affect later consumers of this material.

**Returns:** the live tint color

#### setTint

```java
public Material3D setTint(Color tint)
```

Copies color components into owned tint storage without retaining the argument.

- **`tint`** — the nonnull source tint

**Returns:** this material

**Throws `NullPointerException`:** if tint is null

#### getEmissive

```java
public Color getEmissive()
```

Exposes the owned emission color. Its alpha is an emission weight, separate from base opacity and emissionStrength.

**Returns:** the live emissive color

#### setEmissive

```java
public Material3D setEmissive(Color emissive)
```

Copies emission color components without changing the separate HDR multiplier.

- **`emissive`** — the nonnull source emission color

**Returns:** this material

**Throws `NullPointerException`:** if emissive is null

#### setEmissive

```java
public Material3D setEmissive(float r, float g, float b, float strength)
```

Delegates component assignment to Color.set, storing strength in emissive alpha. This parameter is not the separate emissionStrength field; Color controls component storage semantics.

- **`r`** — the red emission component
- **`g`** — the green emission component
- **`b`** — the blue emission component
- **`strength`** — the emission weight stored in alpha

**Returns:** this material

#### getLightingMix

```java
public float getLightingMix()
```

Reads the lighting mix. Normally within zero through one, but NaN is preserved by the setter.

**Returns:** the stored lighting mix

#### setLightingMix

```java
public Material3D setLightingMix(float lightingMix)
```

Sets the lighting contribution mix using clamp01. Out-of-range values and infinities clamp to endpoints; NaN is preserved rather than rejected.

- **`lightingMix`** — the desired mix factor

**Returns:** this material

#### getFogMix

```java
public float getFogMix()
```

Reads the fog mix used by supporting shaders. The value may be NaN if supplied to its setter.

**Returns:** the stored fog mix

#### setFogMix

```java
public Material3D setFogMix(float fogMix)
```

Sets the fog contribution mix using clamp01. Out-of-range values and infinities clamp to endpoints; NaN is preserved rather than rejected.

- **`fogMix`** — the desired mix factor

**Returns:** this material

#### getRadianceMix

```java
public float getRadianceMix()
```

Reads the optional radiance mix. The value may be NaN if supplied to its setter.

**Returns:** the stored radiance mix

#### setRadianceMix

```java
public Material3D setRadianceMix(float radianceMix)
```

Sets the radiance contribution mix using clamp01. Out-of-range values and infinities clamp to endpoints; NaN is preserved rather than rejected.

- **`radianceMix`** — the desired mix factor

**Returns:** this material

#### isDepthTest

```java
public boolean isDepthTest()
```

Reports the requested depth-test flag without querying current GL state.

**Returns:** whether depth testing is requested

#### setDepthTest

```java
public Material3D setDepthTest(boolean depthTest)
```

Sets the depth-test request independently of depth writes and render pass.

- **`depthTest`** — whether depth testing is requested

**Returns:** this material

#### isDepthWrite

```java
public boolean isDepthWrite()
```

Reports the requested depth-write flag, which may have been reset by the last pass assignment.

**Returns:** whether depth writes are requested

#### setDepthWrite

```java
public Material3D setDepthWrite(boolean depthWrite)
```

Overrides depth-write policy without changing the pass. A later setRenderPass call resets this override.

- **`depthWrite`** — whether depth-buffer writes are requested

**Returns:** this material

#### isCullBackFaces

```java
public boolean isCullBackFaces()
```

Reads the back-face culling request without changing current GL state.

**Returns:** whether back-face culling is requested

#### setCullBackFaces

```java
public Material3D setCullBackFaces(boolean cullBackFaces)
```

Sets the raster back-face culling request without changing mesh winding or geometry.

- **`cullBackFaces`** — whether back faces should be culled

**Returns:** this material

#### copy

```java
public Material3D copy()
```

Copies every current setting into a new material with independent tint and emissive storage. Texture is shared by reference. Depth-write is copied after render-pass assignment so an explicit override is preserved.

**Returns:** a new independently mutable material sharing only the texture resource

#### set

```java
public Material3D set(Material3D source)
```

Copies all settings into this material without replacing its owned colors.
The texture remains borrowed. Self-assignment is supported.

- **`source`** — material to copy

**Returns:** this material

**Throws `NullPointerException`:** if source is null

</details>

<a id="type-meshbatch3d"></a>

### MeshBatch3D

[Source](../../src/main/java/valthorne/graphics/model/MeshBatch3D.java#L63)

Accumulates world-space triangles in a growable CPU buffer and submits them
through an owned OpenGL vertex array and buffer. Each vertex stores position,
normal, RGBA color, and UV coordinates as twelve floats. Primitive helpers use
flat face normals and zero UVs; model emission preserves source normals and UVs
while applying the instance transform.

Use `begin()` to start CPU geometry, then `render(MeshRenderState3D)`
to stream it immediately, or `upload()` followed by repeated
`renderUploaded(MeshRenderState3D)` calls for retained geometry. Both
paths share the same GPU buffer: streaming overwrites retained data, so upload
again before resuming retained rendering. A material applies to the entire draw;
use separate batches or the model batch to group incompatible materials.

Construction, upload, rendering, and disposal require a current OpenGL context.
Public rendering and upload methods restore the state covered by
`RenderStateSnapshot3D`; construction leaves array bindings at zero.
The batch owns its shaders and GPU objects, but borrows models, textures,
materials, cameras, and lighting. Mutable scratch storage makes it unsuitable
for concurrent use.

```java
MeshBatch3D mesh = new MeshBatch3D();
try {
    mesh.begin();
    mesh.box(0, 0, 1, 2, 2, 2, 0, color);
    mesh.upload();
    mesh.renderUploaded(new MeshRenderState3D().setCamera(camera));
} finally {
    mesh.dispose();
}
```

<details>
<summary>MeshBatch3D operation reference (21 declarations)</summary>

#### Constructor

```java
public MeshBatch3D()
```

Creates an empty batch with storage for 1,048,576 floats on both CPU and GPU.
Compiles the compatibility shader immediately; lighting and depth shaders are
created lazily when their rendering paths are first selected.

#### Constructor

```java
public MeshBatch3D(int initialCapacityFloats)
```

Allocates CPU and GPU storage and configures position, normal, color, and UV
vertex attributes. Capacity is expressed in floats, not vertices or bytes,
and grows automatically as geometry is appended.

- **`initialCapacityFloats`** — initial capacity of at least 36 floats

**Throws `IllegalArgumentException`:** if capacity cannot hold one triangle

#### begin

```java
public void begin()
```

Clears accumulated CPU geometry and resets its vertex count. Preserves GPU
data and the retained uploaded count, allowing previously uploaded geometry
to remain available until a subsequent upload or streaming render overwrites it.

**Throws `IllegalStateException`:** if this batch is disposed

#### clear

```java
public void clear()
```

Clears CPU geometry and sets the retained draw count to zero. GPU storage is
kept allocated but will no longer be drawn by renderUploaded until another upload.

**Throws `IllegalStateException`:** if this batch is disposed

#### getVertexCount

```java
public int getVertexCount()
```

Returns the number of vertices currently accumulated on the CPU. Rendering
and uploading do not reset this count.

**Returns:** CPU vertex count

#### getUploadedVertexCount

```java
public int getUploadedVertexCount()
```

Returns the retained draw count recorded by the last upload, or zero after
clear. Streaming rendering does not update this count even though it overwrites
the same GPU buffer.

**Returns:** retained vertex count

#### triangle

```java
public void triangle(Vector3f a, Vector3f b, Vector3f c, Color color)
```

Appends a flat-shaded triangle with normal `(b-a) cross (c-a)` and
the same copied color at all vertices. Skips triangles whose cross-product
length is at most 0.00001. UV coordinates default to zero.

- **`a`** — first world-space vertex
- **`b`** — second world-space vertex
- **`c`** — third world-space vertex
- **`color`** — nonnull vertex color copied into the buffer

**Throws `NullPointerException`:** if a vertex or color is null

#### quad

```java
public void quad(Vector3f a, Vector3f b, Vector3f c, Vector3f d, Color color)
```

Appends triangles (a,b,c) and (a,c,d), preserving the supplied winding.
Each triangle independently computes a face normal and may be skipped if
degenerate; nonplanar quads therefore have two flat-shaded faces.

- **`a`** — first world-space corner
- **`b`** — second world-space corner
- **`c`** — third world-space corner
- **`d`** — fourth world-space corner
- **`color`** — color copied to each emitted vertex

**Throws `NullPointerException`:** if a corner or color is null

#### box

```java
public void box(float centerX, float centerY, float centerZ,
                    float sizeX, float sizeY, float sizeZ,
                    float rotationY, Color color)
```

Appends the six flat-shaded faces of a centered box, rotated about the Y axis.
Dimensions are full extents; this helper does not validate their sign or
finiteness. Negative sizes can reverse winding.

- **`centerX`** — world center X
- **`centerY`** — world center Y
- **`centerZ`** — world center Z
- **`sizeX`** — full local X extent
- **`sizeY`** — full local Y extent
- **`sizeZ`** — full local Z extent
- **`rotationY`** — Y-axis rotation in radians
- **`color`** — copied face color

#### capsule

```java
public void capsule(float centerX, float centerY, float baseZ,
                        float radius, float height,
                        int segments, int stacks,
                        Color color)
```

Appends a Z-aligned capsule consisting of cylinder walls and two hemispheres.
The cylindrical portion has length max(0, height - 2 * radius); requesting a
shorter total height still produces a sphere of diameter 2 * radius. Geometry
uses flat normals. Supply positive dimensions and suitable subdivision counts;
this helper does not validate them.

- **`centerX`** — capsule axis X
- **`centerY`** — capsule axis Y
- **`baseZ`** — bottom of the lower hemisphere
- **`radius`** — circular radius
- **`height`** — requested overall height
- **`segments`** — circumferential divisions, normally at least 3
- **`stacks`** — latitude divisions per hemisphere, normally at least 1
- **`color`** — copied vertex color

#### sphere

```java
public void sphere(float centerX, float centerY, float centerZ,
                       float radius, int segments, int stacks,
                       Color color)
```

Appends two Z-oriented hemispheres forming a flat-shaded sphere. Degenerate
pole triangles are skipped by the triangle helper. Subdivision counts and
radius are not validated; nonpositive counts emit no corresponding faces.

- **`centerX`** — world center X
- **`centerY`** — world center Y
- **`centerZ`** — world center Z
- **`radius`** — sphere radius
- **`segments`** — circumferential divisions, normally at least 3
- **`stacks`** — latitude divisions per hemisphere
- **`color`** — copied vertex color

#### cylinder

```java
public void cylinder(float centerX, float centerY, float centerZ,
                         float radius, float height, int segments,
                         Color color)
```

Appends a Z-aligned cylinder with side walls and oppositely wound end caps.
Height extends equally above and below the center. Dimensions and subdivision
counts are not validated.

- **`centerX`** — axis X
- **`centerY`** — axis Y
- **`centerZ`** — vertical midpoint
- **`radius`** — circular radius
- **`height`** — full cylinder height
- **`segments`** — circumferential divisions, normally at least 3
- **`color`** — copied vertex color

#### cylinderWalls

```java
public void cylinderWalls(float centerX, float centerY, float centerZ,
                              float radius, float height, int segments,
                              Color color)
```

Appends only the side walls of a centered Z-aligned cylinder. Leaves both
ends open and computes a separate flat normal for each wall segment.

- **`centerX`** — axis X
- **`centerY`** — axis Y
- **`centerZ`** — vertical midpoint
- **`radius`** — circular radius
- **`height`** — full wall height
- **`segments`** — circumferential divisions
- **`color`** — copied vertex color

#### disc

```java
public void disc(float centerX, float centerY, float z,
                     float radius, boolean topFace, int segments,
                     Color color)
```

Appends an XY-plane triangle fan at the supplied Z coordinate. With positive
radius and ordinary subdivision counts, topFace selects a positive-Z normal;
false reverses the winding. Nonpositive segment counts emit nothing.

- **`centerX`** — disc center X
- **`centerY`** — disc center Y
- **`z`** — plane height
- **`radius`** — disc radius
- **`topFace`** — whether the face points toward positive Z
- **`segments`** — fan triangle count, normally at least 3
- **`color`** — copied vertex color

#### model

```java
public void model(Model3D model, float worldX, float worldY, float worldZ, float scale, float yawRadians)
```

Appends a model through a reusable instance with uniform scale and Z-axis yaw.
Copies vertex data into the batch; neither the model nor its resources are
retained by the emitted geometry. A null model emits nothing.

- **`model`** — source model
- **`worldX`** — world translation X
- **`worldY`** — world translation Y
- **`worldZ`** — world translation Z
- **`scale`** — nonzero uniform scale
- **`yawRadians`** — Z-axis rotation in radians

**Throws `IllegalArgumentException`:** if scale is zero or transform evaluation rejects nonfinite components

#### model

```java
public void model(ModelInstance3D instance)
```

Appends transformed model triangles without visibility testing. Uses the world
matrix for positions and its inverse transpose for normals, preserving source
UVs and colors. Reflected transforms swap the second and third vertices to
preserve front-face winding. A null instance or missing model emits nothing.

- **`instance`** — borrowed source instance

**Throws `IllegalArgumentException`:** if the instance transform is invalid

**Throws `IllegalStateException`:** if the linear world transform is effectively singular

#### modelIfVisible

```java
public boolean modelIfVisible(ModelInstance3D instance, Camera3D camera)
```

Checks the instance against the camera before appending its geometry. Camera
matrices must already be current. A null instance or camera is rejected.

- **`instance`** — candidate model instance
- **`camera`** — camera used for visibility testing

**Returns:** true if visibility passed and model emission was invoked

#### upload

```java
public void upload()
```

Copies current CPU geometry into the shared GPU buffer and records its vertex
count for retained rendering. Empty geometry clears the retained count without
releasing storage. Preserves CPU data and restores the render snapshot's GL
state even on failure.

**Throws `IllegalStateException`:** if this batch is disposed

#### renderUploaded

```java
public void renderUploaded(MeshRenderState3D state)
```

Draws the retained vertex count from the shared GPU buffer without uploading
CPU geometry. Prepares configured lighting even for an empty retained draw,
then applies the material and selected shader. Restores captured GL state
on return or failure.

- **`state`** — nonnull camera, material, and lighting configuration

**Throws `IllegalStateException`:** if disposed or a nonempty draw has no camera

**Throws `NullPointerException`:** if state is null

#### render

```java
public void render(MeshRenderState3D state)
```

Uploads and draws the current CPU triangles without clearing them. Prepares
configured lighting and restores captured GL state on return or failure.
This overwrites retained GPU data but does not change the retained draw count;
call upload again before returning to retained rendering.

- **`state`** — nonnull camera, material, and lighting configuration

**Throws `IllegalStateException`:** if disposed or a nonempty draw has no camera

**Throws `NullPointerException`:** if state is null

#### dispose

```java
public void dispose()
```

Marks the batch disposed and releases its owned shaders, vertex buffer, and
vertex array with the OpenGL context current. Repeated calls do nothing.
Borrowed render resources are not released; CPU storage remains referenced
until the batch itself becomes unreachable.

</details>

<a id="type-meshrenderable3d"></a>

### MeshRenderable3D

[Source](../../src/main/java/valthorne/graphics/model/MeshRenderable3D.java#L16)

Defines the mesh-emission backend of a `Renderable3D`. Implementations
append world-space triangle vertices to a batch supplied by `ModelBatch3D`;
the model batch then renders accumulated geometry using the submission's material.
Object transforms must be applied by the implementation before vertices are emitted.

Emission is deferred until model-batch end. The callback supplies geometry,
while the owning batch controls clearing, drawing and resource lifetime.
Implementations should not change the shared batch's lifecycle or issue an
independent render while appending geometry.

<details>
<summary>MeshRenderable3D operation reference (1 declarations)</summary>

#### emit

```java
void emit(MeshBatch3D meshBatch)
```

Appends this object's world-space triangles to the prepared mesh batch.
Existing vertices may belong to other compatible submissions and must not
be cleared. Supply transformed normals and texture coordinates when using
an emission overload that accepts them. This method does not transfer
ownership of the batch or require the implementation to render it.

- **`meshBatch`** — the nonnull destination prepared by the owning renderer

</details>

<a id="type-meshrenderstate3d"></a>

### MeshRenderState3D

[Source](../../src/main/java/valthorne/graphics/model/MeshRenderState3D.java#L20)

Mutable draw configuration combining camera, material, compatibility lighting,
fog, shadow-map references, and optional screen-space radiance mapping. Cameras,
materials, lights, and rendering systems are borrowed; colors and light direction
are copied by setters but exposed as live owned values by getters.

This container does not allocate GPU resources, prepare lights, rebuild cameras,
or draw. The batch consumes its current values. Do not mutate it during deferred
frame emission. Compatibility point lights are capped at eight, independently of
the optional tiled lighting system.

<details>
<summary>MeshRenderState3D operation reference (37 declarations)</summary>

#### MAX_POINT_LIGHTS

```java
public static final  int MAX_POINT_LIGHTS
```

Maximum number of lights retained by the compatibility point-light list.

#### getLighting

```java
public valthorne.graphics.lighting3d.Lighting3D getLighting()
```

Returns the borrowed tiled lighting system without preparing or copying it.
A null reference indicates that this component has not been supplied.

**Returns:** borrowed tiled lighting system, or null

#### setLighting

```java
public MeshRenderState3D setLighting(valthorne.graphics.lighting3d.Lighting3D lighting)
```

Retains the borrowed tiled lighting system without taking disposal responsibility.
Null clears the reference; this setter does not perform rendering or resource setup.

- **`lighting`** — replacement reference, or null

**Returns:** this state

#### getShadowMap

```java
public ShadowMap3D getShadowMap()
```

Returns the borrowed shadow map without preparing or copying it.
A null reference indicates that this component has not been supplied.

**Returns:** borrowed shadow map, or null

#### setShadowMap

```java
public MeshRenderState3D setShadowMap(ShadowMap3D shadowMap)
```

Retains the borrowed shadow map without taking disposal responsibility.
Null clears the reference; this setter does not perform rendering or resource setup.

- **`shadowMap`** — replacement reference, or null

**Returns:** this state

#### getAmbientLight

```java
public Color getAmbientLight()
```

Returns the live owned ambient light color. Mutations affect later draws using this
state; copy it when retaining an independent value.

**Returns:** mutable ambient light color

#### setAmbientLight

```java
public MeshRenderState3D setAmbientLight(Color color)
```

Copies the ambient light color into owned storage without retaining the input.
Does not clamp components or upload any shader uniforms.

- **`color`** — replacement color

**Returns:** this state

#### getDirectionalLight

```java
public Color getDirectionalLight()
```

Returns the live owned directional light color. Mutations affect later draws using this
state; copy it when retaining an independent value.

**Returns:** mutable directional light color

#### setDirectionalLight

```java
public MeshRenderState3D setDirectionalLight(Color color)
```

Copies the directional light color into owned storage without retaining the input.
Does not clamp components or upload any shader uniforms.

- **`color`** — replacement color

**Returns:** this state

#### getPointLights

```java
public java.util.List<PointLight3D> getPointLights()
```

Returns an unmodifiable live list of compatibility lights. Light objects remain
shared and mutable; the list does not snapshot their values.

**Returns:** live light-list view

#### addLight

```java
public MeshRenderState3D addLight(PointLight3D light)
```

Appends a borrowed light, allowing repeated references, up to the compatibility
limit. Does not transfer ownership or configure the tiled lighting system.

- **`light`** — light to append

**Returns:** this state

**Throws `NullPointerException`:** if light is null

**Throws `IllegalStateException`:** if eight lights are already present

#### removeLight

```java
public boolean removeLight(PointLight3D light)
```

Removes the first matching light reference according to list equality semantics.
Does not dispose the removed light.

- **`light`** — light to remove

**Returns:** true if an entry was removed

#### clearLights

```java
public void clearLights()
```

Removes all compatibility light references without disposing them or changing
the optional tiled lighting system.

#### getFogStart

```java
public float getFogStart()
```

Returns the stored fog start distance without changing or validating draw state.

**Returns:** fog start distance

#### getFogEnd

```java
public float getFogEnd()
```

Returns the stored fog end distance without changing or validating draw state.

**Returns:** fog end distance

#### getFogAmount

```java
public float getFogAmount()
```

Returns the stored maximum fog blend fraction without changing or validating draw state.

**Returns:** maximum fog blend fraction

#### setFog

```java
public MeshRenderState3D setFog(float start, float end, float amount)
```

Stores validated fog distances and maximum blend fraction. Color is configured
separately; these values are consumed by subsequent draws.

- **`start`** — finite nonnegative start distance in world units
- **`end`** — finite end distance greater than start
- **`amount`** — finite maximum blend fraction from zero through one

**Returns:** this state

**Throws `IllegalArgumentException`:** if distances or amount violate their constraints

#### getCamera

```java
public Camera3D getCamera()
```

Returns the borrowed camera without preparing or copying it.
A null reference indicates that this component has not been supplied.

**Returns:** borrowed camera, or null

#### setCamera

```java
public MeshRenderState3D setCamera(Camera3D camera)
```

Retains the borrowed camera without taking disposal responsibility.
Null clears the reference; this setter does not perform rendering or resource setup.

- **`camera`** — replacement reference, or null

**Returns:** this state

#### getLightDirection

```java
public Vector3f getLightDirection()
```

Returns the live owned directional-light vector. It is not normalized by this
container; consumers apply their own interpretation.

**Returns:** mutable light direction

#### setLightDirection

```java
public MeshRenderState3D setLightDirection(Vector3f direction)
```

Copies a light-direction vector without normalizing or validating its components.
The supplied vector is not retained.

- **`direction`** — vector to copy

**Returns:** this state

**Throws `NullPointerException`:** if direction is null

#### setLightDirection

```java
public MeshRenderState3D setLightDirection(float x, float y, float z)
```

Stores light-direction components directly without normalization or finiteness checks.

- **`x`** — X component
- **`y`** — Y component
- **`z`** — Z component

**Returns:** this state

#### getFogColor

```java
public Color getFogColor()
```

Returns the live owned fog color. Mutations affect later draws using this
state; copy it when retaining an independent value.

**Returns:** mutable fog color

#### setFogColor

```java
public MeshRenderState3D setFogColor(Color fogColor)
```

Copies the fog color into owned storage without retaining the input.
Does not clamp components or upload any shader uniforms.

- **`fogColor`** — replacement color

**Returns:** this state

#### getMaterial

```java
public Material3D getMaterial()
```

Returns the borrowed material without preparing or copying it.
A null reference indicates that this component has not been supplied.

**Returns:** borrowed material, or null

#### setMaterial

```java
public MeshRenderState3D setMaterial(Material3D material)
```

Retains the borrowed material without taking disposal responsibility.
Null clears the reference; this setter does not perform rendering or resource setup.

- **`material`** — replacement reference, or null

**Returns:** this state

#### getLightTextureId

```java
public int getLightTextureId()
```

Returns the stored radiance texture name without changing or validating draw state.

**Returns:** radiance texture name

#### setLightTextureId

```java
public MeshRenderState3D setLightTextureId(int lightTextureId)
```

Stores a borrowed radiance texture name, clamping negative integers to zero.
Does not validate the OpenGL name, bind it, or manage its lifetime.

- **`lightTextureId`** — texture name, or zero for none

**Returns:** this state

#### isApplyRadiance

```java
public boolean isApplyRadiance()
```

Reports whether draws should apply the configured radiance contribution.
This flag does not imply that a valid texture has been supplied.

**Returns:** radiance enablement

#### setApplyRadiance

```java
public MeshRenderState3D setApplyRadiance(boolean applyRadiance)
```

Changes radiance enablement without allocating or clearing the referenced texture.

- **`applyRadiance`** — whether to request radiance contribution

**Returns:** this state

#### getLightWorldMinX

```java
public float getLightWorldMinX()
```

Returns the stored radiance rectangle minimum X without changing or validating draw state.

**Returns:** radiance rectangle minimum X

#### getLightWorldMinY

```java
public float getLightWorldMinY()
```

Returns the stored radiance rectangle minimum Y without changing or validating draw state.

**Returns:** radiance rectangle minimum Y

#### getLightWorldSizeX

```java
public float getLightWorldSizeX()
```

Returns the stored radiance rectangle X extent without changing or validating draw state.

**Returns:** radiance rectangle X extent

#### getLightWorldSizeY

```java
public float getLightWorldSizeY()
```

Returns the stored radiance rectangle Y extent without changing or validating draw state.

**Returns:** radiance rectangle Y extent

#### setLightWorldBounds

```java
public MeshRenderState3D setLightWorldBounds(float minX, float minY, float sizeX, float sizeY)
```

Stores the world XY rectangle used to map radiance texture coordinates. Rejects
nonpositive extents but does not separately validate finiteness or origin values.

- **`minX`** — world-space left coordinate
- **`minY`** — world-space lower coordinate
- **`sizeX`** — positive world-space X extent
- **`sizeY`** — positive world-space Y extent

**Returns:** this state

**Throws `IllegalArgumentException`:** if either extent is nonpositive

#### getRadianceStrength

```java
public float getRadianceStrength()
```

Returns the stored radiance contribution multiplier without changing or validating draw state.

**Returns:** radiance contribution multiplier

#### setRadianceStrength

```java
public MeshRenderState3D setRadianceStrength(float radianceStrength)
```

Stores the radiance multiplier with negative values clamped to zero. NaN and
positive infinity are not rejected by this setter.

- **`radianceStrength`** — requested multiplier

**Returns:** this state

</details>

<a id="type-model3d"></a>

### Model3D

[Source](../../src/main/java/valthorne/graphics/model/Model3D.java#L20)

CPU-side triangle geometry conventionally expressed in Z-up local space. The
constructor copies triangles and calculates an axis-aligned local bound; no GPU
resources are allocated. Public accessors protect positions, attributes, and bounds
with copies, while package-internal rendering code reads trusted shared storage.

The model imposes no automatic axis conversion or unit scale and does not reject
degenerate or non-finite geometry. An empty triangle array is accepted. Materials,
world transforms, and instance visibility belong to separate rendering objects.

<details>
<summary>Model3D operation reference (4 declarations)</summary>

#### Constructor

```java
public Model3D(Triangle[] triangles)
```

Copies each triangle and accumulates bounds from its three positions. The source
array and triangle objects are not retained, and an empty model is allowed.

- **`triangles`** — ordered triangle data to snapshot

**Throws `NullPointerException`:** if the array or any triangle is null

#### getTriangles

```java
public Triangle[] getTriangles()
```

Returns a new array containing the model's protected triangle objects. Changing
array membership does not affect the model; triangle public accessors return copies.

**Returns:** shallow array copy of triangle snapshots

#### getTriangleCount

```java
public int getTriangleCount()
```

Returns geometry size without copying or traversing the triangle array.

**Returns:** number of stored triangles

#### getLocalBounds

```java
public AABBf getLocalBounds()
```

Returns an independent copy of bounds computed at construction. It remains in
model-local coordinates and includes no instance transform.

**Returns:** copied local axis-aligned bounds

</details>

<a id="type-model3d-triangle"></a>

### Model3D.Triangle

[Source](../../src/main/java/valthorne/graphics/model/Model3D.java#L100)

Stores three copied positions, one copied color, UV coordinates, and vertex normals.
Missing individual normals use the normalized cross product of the two face edges;
supplied normals are copied and normalized independently. Degenerate faces can
retain zero normals. Public getters allocate copies; package readers must not mutate.

<details>
<summary>Model3D.Triangle operation reference (12 declarations)</summary>

#### Constructor

```java
public Triangle(Vector3f a, Vector3f b, Vector3f c, Color color)
```

Copies a colored face with zero UVs and a generated flat normal at each vertex.
The cross-product winding determines the normal direction.

- **`a`** — first local position
- **`b`** — second local position
- **`c`** — third local position
- **`color`** — triangle color

**Throws `NullPointerException`:** if a position or color is null

#### Constructor

```java
public Triangle(Vector3f a, Vector3f b, Vector3f c, Color color,
                        Vector2f uvA, Vector2f uvB, Vector2f uvC,
                        Vector3f normalA, Vector3f normalB, Vector3f normalC)
```

Copies all supplied attributes. Each null normal independently uses the face normal;
non-null normals are normalized without finiteness or degeneracy checks. UVs must
be non-null and are copied without clamping.

- **`a`** — first local position
- **`b`** — second local position
- **`c`** — third local position
- **`color`** — triangle color
- **`uvA`** — first texture coordinate
- **`uvB`** — second texture coordinate
- **`uvC`** — third texture coordinate
- **`normalA`** — first normal, or null for flat shading
- **`normalB`** — second normal, or null for flat shading
- **`normalC`** — third normal, or null for flat shading

**Throws `NullPointerException`:** if a position, color, or UV is null

#### getUvA

```java
public Vector2f getUvA()
```

Returns an independent copy of vertex A's texture coordinates. Values are
stored as supplied and need not lie within zero through one.

**Returns:** copied UV coordinate

#### getUvB

```java
public Vector2f getUvB()
```

Returns an independent copy of vertex B's texture coordinates. Values are
stored as supplied and need not lie within zero through one.

**Returns:** copied UV coordinate

#### getUvC

```java
public Vector2f getUvC()
```

Returns an independent copy of vertex C's texture coordinates. Values are
stored as supplied and need not lie within zero through one.

**Returns:** copied UV coordinate

#### getNormalA

```java
public Vector3f getNormalA()
```

Returns an independent copy of vertex A's stored local-space normal.
Degenerate or non-finite source data is not repaired by this accessor.

**Returns:** copied normal

#### getNormalB

```java
public Vector3f getNormalB()
```

Returns an independent copy of vertex B's stored local-space normal.
Degenerate or non-finite source data is not repaired by this accessor.

**Returns:** copied normal

#### getNormalC

```java
public Vector3f getNormalC()
```

Returns an independent copy of vertex C's stored local-space normal.
Degenerate or non-finite source data is not repaired by this accessor.

**Returns:** copied normal

#### getA

```java
public Vector3f getA()
```

Returns an independent copy of vertex A's local-space position.

**Returns:** copied position

#### getB

```java
public Vector3f getB()
```

Returns an independent copy of vertex B's local-space position.

**Returns:** copied position

#### getC

```java
public Vector3f getC()
```

Returns an independent copy of vertex C's local-space position.

**Returns:** copied position

#### getColor

```java
public Color getColor()
```

Copies the triangle's color so callers can modify it without altering geometry.

**Returns:** independent color value

</details>

<a id="type-modelbatch3d"></a>

### ModelBatch3D

[Source](../../src/main/java/valthorne/graphics/model/ModelBatch3D.java#L40)

Frame submission batch with frustum/opaque-triangle occlusion culling, material batching
and shared mesh/billboard transparency ordering. Submit opaque walls first to improve occlusion coverage.
Built-in instances and materials are snapshotted at submission. Custom renderables must remain stable until end.
Owns the supplied mesh and billboard batches, but never owns submitted models or textures.
Opaque submissions group by material identity, while other passes sort back to front
across mesh and billboard submissions. State and camera references remain live during
a frame; do not mutate them between begin and end.

```java
ModelBatch3D batch = new ModelBatch3D();
batch.begin(state);
try {
    batch.submit(instance);
    batch.end();
} finally {
    batch.cancel();
}
// After the batch is no longer needed:
batch.close();
```

Construction and rendering use GPU resources on the owning graphics thread.
Material snapshots are cached by source identity per frame, so the first submission
using a material determines the snapshot reused by later submissions of that same
source. Custom renderable geometry remains borrowed until end or cancellation.

<details>
<summary>ModelBatch3D operation reference (23 declarations)</summary>

#### setOcclusionCullingEnabled

```java
public ModelBatch3D setOcclusionCullingEnabled(boolean enabled)
```

Enables rejection behind previously submitted opaque triangles. Submit large
opaque walls first for best coverage. Shadow passes always bypass occlusion.

- **`enabled`** — whether camera-pass occlusion is enabled

**Returns:** this batch

#### getOccludedCount

```java
public int getOccludedCount()
```

Reads the current or most recently completed pass's model submissions rejected by opaque-triangle coverage. The counter resets at begin and excludes ordinary frustum rejections.

**Returns:** camera-pass submissions rejected by occlusion in the last frame

#### Constructor

```java
public ModelBatch3D()
```

Allocates default owned mesh and billboard backends. Construct on the graphics
thread and dispose this batch when all frame submissions are finished.

#### Constructor

```java
public ModelBatch3D(int initialCapacityFloats)
```

Allocates an owned mesh backend with the requested float capacity and a default
billboard backend. Capacity units describe float storage rather than vertex count.

- **`initialCapacityFloats`** — initial mesh float-buffer capacity

#### Constructor

```java
public ModelBatch3D(MeshBatch3D meshBatch)
```

Takes ownership of a mesh backend and allocates a default billboard backend.
The supplied backend is disposed with this batch.

- **`meshBatch`** — non-null mesh backend

#### Constructor

```java
public ModelBatch3D(MeshBatch3D meshBatch, BillboardBatch3D billboardBatch)
```

Retains and takes disposal responsibility for both supplied rendering backends.
They must not be used independently while this batch is emitting a frame.

- **`meshBatch`** — owned mesh backend
- **`billboardBatch`** — owned billboard backend

**Throws `NullPointerException`:** if either backend is null

#### begin

```java
public void begin(MeshRenderState3D state)
```

Starts a frame, rebuilds the state's camera with its retained viewport dimensions,
prepares non-shadow lighting, clears submissions and material snapshots, and resets
statistics. Does not snapshot the state or camera themselves.

- **`state`** — live render state containing a camera

**Throws `IllegalStateException`:** if disposed or already active

**Throws `IllegalArgumentException`:** if state or its camera is null

#### submit

```java
public boolean submit(ModelInstance3D instance)
```

Submits a model instance through the common renderable path, including culling,
snapshotting, and OBJ part expansion. Requires an active frame.

- **`instance`** — instance to submit, or null to count as culled

**Returns:** whether the submission was accepted

#### submit

```java
public boolean submit(Renderable3D renderable)
```

Counts and tests a renderable for visibility and optional frustum culling. Rejects
missing model or billboard texture data. Copies built-in instances and sprites;
custom mesh/billboard implementations remain live until end. OBJ submission uploads
its textures and expands parts with combined materials. Shadow filtering can leave
an accepted submission with no queued draw, so true does not guarantee GPU output.

- **`renderable`** — supported mesh or billboard, or null

**Returns:** true when accepted by submission checks, false when culled

**Throws `IllegalStateException`:** if no frame is active

**Throws `IllegalArgumentException`:** if a visible renderable has an unsupported type

#### submit

```java
public int submit(SceneNode3D node)
```

Traverses a scene node through its submission logic, allowing whole-subtree culling.
The node and its resources remain caller-owned.

- **`node`** — scene subtree root

**Returns:** accepted submissions reported by the subtree

**Throws `NullPointerException`:** if node is null

**Throws `IllegalStateException`:** if no frame is active

#### submit

```java
public int submit(Iterable<? extends Renderable3D> renderables)
```

Submits each value in iteration order and counts accepted values. Iteration or
submission failures leave earlier entries queued; cancel if abandoning the frame.

- **`renderables`** — values to submit

**Returns:** number accepted by the common submission path

**Throws `IllegalStateException`:** if no frame is active

#### end

```java
public void end()
```

Orders queued entries by pass, then material order for opaque content or descending
depth for other passes. Emits compatible material/backend/texture runs. Restores
captured graphics state and the prior state material, and clears the active frame
in finally even when emission fails. Does not dispose submitted resources.

**Throws `IllegalStateException`:** if no frame is active

#### cancel

```java
public void cancel()
```

Discards queued submissions and material snapshots and clears active references.
Safe when no frame is active. Retains counters for inspection and does not draw.

#### getSubmittedCount

```java
public int getSubmittedCount()
```

Returns attempted submissions, including entries counted by subtree rejection.
Resets at begin and is retained after end or cancel.

**Returns:** submitted count

#### getVisibleCount

```java
public int getVisibleCount()
```

Returns submissions accepted before shadow filtering and OBJ part expansion.
This is not a draw-call or emitted-part count.

**Returns:** accepted submission count

#### getCulledCount

```java
public int getCulledCount()
```

Returns rejected submissions, including null/invisible/missing-data entries and
models counted by whole-subtree rejection.

**Returns:** culled submission count

#### getCulledSubtreeCount

```java
public int getCulledSubtreeCount()
```

Returns hierarchy branches rejected by bounds before individual submission.
Resets at the start of each frame.

**Returns:** rejected subtree count

#### getMeshBatch

```java
public MeshBatch3D getMeshBatch()
```

Returns the owned mesh backend for inspection. Do not dispose it separately or
modify it during this batch's emission.

**Returns:** owned mesh backend

#### getBillboardBatch

```java
public BillboardBatch3D getBillboardBatch()
```

Returns the owned billboard backend. Its lifecycle ends when this batch is disposed.

**Returns:** owned billboard backend

#### isCullingEnabled

```java
public boolean isCullingEnabled()
```

Reports whether frustum checks are enabled; logical visibility and missing-data
checks still apply when frustum culling is disabled.

**Returns:** current frustum-culling flag

#### setCullingEnabled

```java
public ModelBatch3D setCullingEnabled(boolean enabled)
```

Changes frustum-culling behavior for subsequent submissions and subtree tests.
Already queued entries are not reevaluated.

- **`enabled`** — whether to test bounds against the camera frustum

**Returns:** this batch

#### dispose

```java
public void dispose()
```

Cancels pending work and disposes both owned backends. Repeated completed disposal
is a no-op; submitted models, materials, and textures are never disposed here.

#### close

```java
    public void close()
```

Delegates to dispose, releasing owned backends and cancelling pending submissions.
Use on the owning graphics thread.

</details>

<a id="type-modelbatch3d-submission"></a>

### ModelBatch3D.Submission — internal support type

[Source](../../src/main/java/valthorne/graphics/model/ModelBatch3D.java#L448)

Retains one stable renderable reference and captured material/order values for
deferred frame emission. Texture resources remain shared with their owners.

The entry combines borrowed geometry with the material state captured for this frame.
Sorting uses the captured depth and material order, so those keys do not change if
external scene state changes before queued emission.

- **`renderable`** — submitted geometry provider
- **`material`** — per-frame material snapshot
- **`depth`** — captured camera sort depth
- **`materialOrder`** — encounter order used for opaque grouping

<a id="type-modelbuilder3d"></a>

### ModelBuilder3D

[Source](../../src/main/java/valthorne/graphics/model/ModelBuilder3D.java#L29)

Builds CPU triangle models from explicit faces or centered Z-up primitives.
Primitive dimensions are finite and positive; outward faces use counterclockwise
winding. Triangle constructors copy position and attribute data, and build creates
a further model snapshot without clearing this reusable builder.

```java
Model3D floor = ModelBuilder3D.plane(10, 10);
Model3D column = ModelBuilder3D.cylinder(0.5f, 3, 24);
ModelBuilder3D builder = new ModelBuilder3D();
builder.triangle(new Vector3f(), new Vector3f(1, 0, 0),
        new Vector3f(0, 1, 0), Color.WHITE);
Model3D custom = builder.build();
```

No graphics context or GPU allocation is required. The builder is mutable and
unsynchronized; completed models provide protected geometry access.

<details>
<summary>ModelBuilder3D operation reference (9 declarations)</summary>

#### plane

```java
public static Model3D plane(float width, float depth)
```

Creates a centered XY rectangle at Z zero as two white triangles facing positive Z.
UV coordinates cover the unit square; dimensions are full extents.

- **`width`** — finite positive X extent
- **`depth`** — finite positive Y extent

**Returns:** new two-triangle CPU model

**Throws `IllegalArgumentException`:** if either dimension is invalid

#### box

```java
public static Model3D box(float width, float depth, float height)
```

Creates a centered box with twelve white triangles and flat outward face normals.
Each face receives unit-square UVs independently.

- **`width`** — finite positive X extent
- **`depth`** — finite positive Y extent
- **`height`** — finite positive Z extent

**Returns:** new closed box model

**Throws `IllegalArgumentException`:** if a dimension is invalid

#### sphere

```java
public static Model3D sphere(float radius, int segments, int stacks)
```

Creates a centered latitude/longitude sphere with radial vertex normals and UVs
that span longitude and latitude. Omits degenerate pole triangles, yielding
2 * segments * (stacks - 1) triangles for ordinary counts.

- **`radius`** — finite positive radius
- **`segments`** — longitude divisions, at least three
- **`stacks`** — latitude divisions, at least two

**Returns:** new sphere model

**Throws `IllegalArgumentException`:** if radius or subdivision counts are invalid

#### cylinder

```java
public static Model3D cylinder(float radius, float height, int segments)
```

Creates a centered closed Z-axis cylinder with smooth radial side normals and
flat end caps. Side UVs wrap around the circumference; cap UVs map the circular
cross-section into a unit square. Each segment contributes four triangles.

- **`radius`** — finite positive radius
- **`height`** — finite positive full Z extent
- **`segments`** — circumference divisions, at least three

**Returns:** new cylinder model

**Throws `IllegalArgumentException`:** if dimensions or segment count are invalid

#### triangle

```java
public ModelBuilder3D triangle(Vector3f a, Vector3f b, Vector3f c, Color color)
```

Appends a copied colored triangle with zero UVs and generated flat normals.
No degeneracy or winding correction is performed.

- **`a`** — first position
- **`b`** — second position
- **`c`** — third position
- **`color`** — face color

**Returns:** this builder

**Throws `NullPointerException`:** if a position or color is null

#### triangle

```java
public ModelBuilder3D triangle(Model3D.Triangle triangle)
```

Appends an existing protected triangle object. The completed model copies it
during build; callers need not transfer resource ownership.

- **`triangle`** — attributed face to append

**Returns:** this builder

**Throws `NullPointerException`:** if triangle is null

#### quad

```java
public ModelBuilder3D quad(Vector3f a, Vector3f b, Vector3f c, Vector3f d, Color color)
```

Appends triangles ABC and ACD with unit-square UVs and generated face normals.
Supply ordered coplanar corners for a conventional quad; convexity, planarity,
and winding are not validated or corrected.

- **`a`** — first corner at UV (0,0)
- **`b`** — second corner at UV (1,0)
- **`c`** — third corner at UV (1,1)
- **`d`** — fourth corner at UV (0,1)
- **`color`** — color copied into both triangles

**Returns:** this builder

**Throws `NullPointerException`:** if a corner or color is null

#### build

```java
public Model3D build()
```

Copies accumulated faces into a new CPU model and computes its local bounds.
The builder retains its faces for subsequent builds or further additions.

**Returns:** independent model snapshot, possibly empty

#### clear

```java
public void clear()
```

Removes accumulated faces without affecting models already built. No graphics
resources are owned or released by this operation.

</details>

<a id="type-modelinstance3d"></a>

### ModelInstance3D

[Source](../../src/main/java/valthorne/graphics/model/ModelInstance3D.java#L32)

Places borrowed model geometry using local translation, rotation, scale, and a
copied parent transform. World matrices and bounds are computed lazily; direct
edits to the exposed position and scale vectors are detected on the next lookup.
Euler rotation applies X, then Y, then Z in conventional Z-up space; quaternion
rotation can replace that mode. Materials and models remain shared references.

```java
ModelInstance3D instance = new ModelInstance3D()
        .setModel(ModelBuilder3D.box(1, 1, 1))
        .setPosition(3, 0, 0)
        .setRotation(0, 0, 0.5f);
AABBf bounds = instance.getWorldBounds();
```

Use on one thread while rendering or querying. Bounds access returns live cached
storage that callers must not mutate. Transform validation is partly deferred:
non-finite local values and zero scale introduced through live vectors fail when
the world transform is next evaluated. This instance owns no GPU resources.

<details>
<summary>ModelInstance3D operation reference (27 declarations)</summary>

#### intersect

```java
public float intersect(org.joml.primitives.Rayf ray)
```

Returns the closest world-transformed triangle intersection after a bounds test.
Invisible or missing models return positive infinity. The result is a ray parameter,
equal to world distance for a normalized direction. Allocates three scratch vectors
per query and does not test texture alpha or material visibility.

- **`ray`** — world-space query ray

**Returns:** nearest intersection parameter, or positive infinity when none

#### setRotation

```java
public ModelInstance3D setRotation(Quaternionf rotation)
```

Copies and normalizes a finite, nonzero quaternion and invalidates the world matrix. Stored Euler values
are preserved but are not used to construct rotation while the override is active.

- **`rotation`** — quaternion to copy

**Returns:** this instance

**Throws `IllegalArgumentException`:** if the quaternion is non-finite or zero

#### setRotation

```java
public ModelInstance3D setRotation(float x, float y, float z)
```

Switches to Euler mode and stores X, Y, then Z rotation angles. Finite-value checks
are deferred to world-transform evaluation.

- **`x`** — X rotation in radians
- **`y`** — Y rotation in radians
- **`z`** — Z/yaw rotation in radians

**Returns:** this instance

#### setParentTransform

```java
public ModelInstance3D setParentTransform(Matrix4f parent)
```

Copies a changed parent matrix and invalidates the world transform. Equal matrix
contents are a no-op. The supplied matrix should be a usable affine transform;
this setter does not validate singularity or finiteness.

- **`parent`** — parent transform to copy

**Returns:** this instance

**Throws `NullPointerException`:** if parent is null

#### getWorldTransform

```java
public Matrix4f getWorldTransform(Matrix4f out)
```

Refreshes lazy transform state and copies the world matrix into caller storage.
Direct edits to position and scale are incorporated before copying.

- **`out`** — destination matrix

**Returns:** out

**Throws `IllegalArgumentException`:** if local transform values are invalid

#### transformNormal

```java
public Vector3f transformNormal(Vector3f normal, Vector3f out)
```

Refreshes transform state and applies the world matrix's normal transformation.
Uses the matrix helper's inverse-transpose and normalization behavior, rather
than transforming a normal as a position.

- **`normal`** — local-space normal
- **`out`** — destination world-space normal

**Returns:** out

#### getModel

```java
public Model3D getModel()
```

Returns the borrowed model reference without copying geometry.

**Returns:** current model, or null

#### setModel

```java
public ModelInstance3D setModel(Model3D model)
```

Replaces borrowed geometry and invalidates world bounds only when identity changes.
Does not dispose the old model or alter placement and material.

- **`model`** — replacement model, or null to remove geometry

**Returns:** this instance

#### set

```java
public ModelInstance3D set(ModelInstance3D other)
```

Copies placement, visibility, parent transform, and quaternion state while sharing
the other instance's model and material. Invalidates both derived caches; this
is a placement snapshot rather than a deep resource copy.

- **`other`** — instance to copy

**Returns:** this instance

**Throws `NullPointerException`:** if other is null

#### getMaterial

```java
    public Material3D getMaterial()
```

Returns the live shared material. Changes to it can affect other instances using
the same reference; frame batching may snapshot it separately.

**Returns:** current material

#### setMaterial

```java
public ModelInstance3D setMaterial(Material3D material)
```

Retains a non-null material reference without copying or disposing either material.
Does not affect transform or bounds caches.

- **`material`** — shared replacement material

**Returns:** this instance

**Throws `NullPointerException`:** if material is null

#### getPosition

```java
public Vector3f getPosition()
```

Returns the live local translation vector. Direct changes are detected when derived
transform state is next evaluated.

**Returns:** owned mutable position

#### setPosition

```java
public ModelInstance3D setPosition(Vector3f position)
```

Copies the supplied local translation, retaining no vector reference. Value
finiteness is checked when the transform is next evaluated.

- **`position`** — local translation to copy

**Returns:** this instance

**Throws `NullPointerException`:** if position is null

#### setPosition

```java
public ModelInstance3D setPosition(float x, float y, float z)
```

Stores local translation and marks world bounds dirty. Matrix refresh remains lazy
and detects the changed components on its next evaluation.

- **`x`** — local X translation
- **`y`** — local Y translation
- **`z`** — local Z translation

**Returns:** this instance

#### getScale

```java
public Vector3f getScale()
```

Returns the live per-axis local scale. Direct edits are detected and validated
when transform state is evaluated; zero components are invalid.

**Returns:** owned mutable scale

#### setScale

```java
public ModelInstance3D setScale(float uniformScale)
```

Assigns the same local scale to all axes. Negative values are supported, zero
is rejected immediately, and finiteness is checked during transform evaluation.

- **`uniformScale`** — common nonzero scale

**Returns:** this instance

**Throws `IllegalArgumentException`:** if scale is zero

#### setScale

```java
public ModelInstance3D setScale(float x, float y, float z)
```

Stores nonzero per-axis local scale and invalidates bounds. Negative scale is
allowed; non-finite values fail on subsequent world-transform evaluation.

- **`x`** — X scale
- **`y`** — Y scale
- **`z`** — Z scale

**Returns:** this instance

**Throws `IllegalArgumentException`:** if any component is zero

#### getYawRadians

```java
public float getYawRadians()
```

Returns the stored Euler Z angle. It does not extract an angle from an active
quaternion override and may therefore be inactive configuration.

**Returns:** stored yaw in radians

#### setYawRadians

```java
public ModelInstance3D setYawRadians(float yawRadians)
```

Switches to Euler mode and replaces Z rotation while preserving stored X/Y angles.
Invalidates matrix and bounds; finite-value validation is deferred.

- **`yawRadians`** — Z rotation in radians

**Returns:** this instance

#### isRenderableVisible

```java
public boolean isRenderableVisible()
```

Returns logical visibility without checking model presence, bounds, or a camera.

**Returns:** instance visibility flag

#### setVisible

```java
public ModelInstance3D setVisible(boolean visible)
```

Changes logical visibility for later submission and intersection checks. Geometry
and cached transforms are retained.

- **`visible`** — whether the instance is eligible to render

**Returns:** this instance

#### getWorldBounds

```java
public AABBf getWorldBounds()
```

Refreshes transform and, when dirty, encloses the eight transformed corners of
the model's local bounds. Returns owned cached storage that must not be modified.
Missing or invalid local geometry leaves empty bounds.

**Returns:** live world-space bounds

#### isVisible

```java
    public boolean isVisible(Camera3D camera)
```

Tests logical visibility and model/camera presence, then uses the camera's current
frustum with conservative bounds padding. Does not rebuild the camera itself.

- **`camera`** — camera with current frustum, or null

**Returns:** whether the instance passes the visibility test

#### isVisible

```java
public boolean isVisible(FrustumIntersection frustum)
```

Tests world bounds using conservative padding after checking logical visibility
and model/frustum presence. Null frustums return false rather than disabling culling.

- **`frustum`** — world-space culling frustum, or null

**Returns:** whether padded bounds intersect the frustum

#### emit

```java
    public void emit(MeshBatch3D meshBatch)
```

Delegates geometry emission to the supplied mesh batch. This method does not
independently cull the instance or manage the batch lifecycle.

- **`meshBatch`** — destination batch

**Throws `NullPointerException`:** if meshBatch is null

#### transform

```java
public Vector3f transform(Vector3f localPoint, Vector3f out)
```

Transforms a local point using the current lazy world matrix. Input and output
may alias because components are captured before writing.

- **`localPoint`** — source local-space point
- **`out`** — destination world-space point

**Returns:** out

**Throws `NullPointerException`:** if either vector is null

#### transform

```java
public Vector3f transform(float localX, float localY, float localZ, Vector3f out)
```

Refreshes the world transform and applies it to supplied local coordinates using
caller-owned output storage.

- **`localX`** — local X coordinate
- **`localY`** — local Y coordinate
- **`localZ`** — local Z coordinate
- **`out`** — destination world-space point

**Returns:** out

**Throws `NullPointerException`:** if out is null

</details>

<a id="type-modelloader"></a>

### ModelLoader

[Source](../../src/main/java/valthorne/graphics/model/ModelLoader.java#L25)

Adapts `ModelParameters` to CPU-side OBJ loading for the asset system.
Each invocation delegates to `ObjModel3D#load(String, ObjModel3D.Resolver, boolean)`
to parse geometry and material definitions and decode referenced diffuse images.
Loading requires no OpenGL context; GPU textures are uploaded separately when
the model is submitted to a supported batch or explicitly uploaded on the render thread.

This loader holds no cache or mutable fields. Direct calls create separate
model instances; `valthorne.asset.Assets` handles asset-key lookup and
asynchronous scheduling when used as the entry point. Concurrency safety of
a custom resolver remains the caller's responsibility.

The returned model owns decoded image resources and any subsequently
uploaded textures. Arrange for `ObjModel3D#dispose()` when those resources
are no longer needed; after upload, disposal must occur on the GL thread.
Neither parameter construction nor loading automatically uploads textures.

<details>
<summary>ModelLoader operation reference (1 declarations)</summary>

#### load

```java
    public ObjModel3D load(ModelParameters parameters)
```

Loads one OBJ and its referenced resources using the supplied resolver and
coordinate-conversion setting. This call is synchronous; use the asset
manager's asynchronous API when loading should occur on a worker thread.
The parameter key is not used by this method, and no result is cached here.

Resolver I/O errors are wrapped by the OBJ loader. Invalid OBJ content
can produce an argument error, with source and line information for errors
encountered while parsing an OBJ statement. Other resolver or decoder
runtime failures propagate. The OBJ loader releases images it has decoded
if loading fails.

- **`parameters`** — the nonnull source, resolver and conversion configuration

**Returns:** a newly loaded model owning its decoded CPU image resources, with
GPU texture upload deferred

**Throws `NullPointerException`:** if parameters is null

**Throws `java.io.UncheckedIOException`:** if the resolver throws an I/O exception

**Throws `IllegalArgumentException`:** if OBJ parsing rejects the content or no
triangles are produced

</details>

<a id="type-modelparameters"></a>

### ModelParameters

[Source](../../src/main/java/valthorne/graphics/model/ModelParameters.java#L49)

Describes an OBJ asset's source, asset-manager identity, byte resolver, and
optional coordinate conversion without performing any resource reads.
`ModelLoader` consumes these parameters to load geometry, material
definitions and decoded diffuse images on the CPU.

##### Source and Asset Identity

The source identifies the OBJ file for the resolver; the key identifies
the resulting asset in `valthorne.asset.Assets`. They need not be equal.
Filesystem factories normalize the source to an absolute path while preserving
the supplied key. Classpath sources are retained as supplied. The resolver is
also used for referenced MTL files and diffuse images, with dependency names
resolved relative to the referring file by `ObjModel3D`.

##### Coordinate Conversion

Factories preserve OBJ coordinates by default. Enabling conversion centers
the input X/Z bounds, subtracts the minimum input Y, then swaps Y and Z to
produce a Z-up model grounded at zero. The loader also adjusts face ordering
and normals for that axis swap. No unit rescaling is performed.

```java
ModelParameters parameters = ModelParameters.fromPath("models/tree.obj", "tree")
        .withConversion(true);
ObjModel3D model = new ModelLoader().load(parameters);
// Upload textures later on the render thread, and dispose the model when done.
```

The record is immutable, but retains the resolver by reference. A custom
resolver used for asynchronous loads must support the access pattern of those
loads. Constructing parameters neither checks resource existence nor uploads
GPU textures. Use distinct asset keys for variants that must coexist.

- **`source`** — the nonblank OBJ location understood by the resolver
- **`key`** — the nonblank identity used by the asset manager
- **`resolver`** — the nonnull byte reader shared by the OBJ and its dependencies
- **`convertAndGround`** — whether to center, ground and convert Y-up input to Z-up

<details>
<summary>ModelParameters operation reference (5 declarations)</summary>

#### Constructor

```java
public ModelParameters
```

Validates and retains the supplied loading configuration. Source and key
must contain non-whitespace characters; accepted strings are not trimmed or
normalized here. The resolver is retained without being invoked.

- **`source`** — the OBJ source name
- **`key`** — the asset-manager identity
- **`resolver`** — the byte reader for the source and referenced resources
- **`convertAndGround`** — whether loading should apply coordinate conversion

**Throws `IllegalArgumentException`:** if source or key is null or blank

**Throws `NullPointerException`:** if the resolver is null after string validation

#### fromPath

```java
public static ModelParameters fromPath(String path)
```

Creates filesystem parameters using the original path string as the asset
key. The source becomes an absolute normalized path, but the key retains
its original spelling. Conversion is disabled and no file is read yet.

- **`path`** — the filesystem OBJ path and nonblank asset key

**Returns:** a new configuration preserving OBJ coordinates

**Throws `NullPointerException`:** if path is null

**Throws `java.nio.file.InvalidPathException`:** if path cannot be parsed

**Throws `IllegalArgumentException`:** if path is blank and therefore an invalid key

#### fromPath

```java
public static ModelParameters fromPath(String path, String key)
```

Creates filesystem parameters with an explicit asset key. Relative paths
are resolved to absolute paths at construction and normalized without
resolving symbolic links or verifying file existence. The resolver reads
each requested file fully through `Files#readAllBytes(Path)` when
loading occurs. Coordinate conversion is initially disabled.

- **`path`** — the OBJ filesystem path
- **`key`** — the nonblank asset identity, independent of path normalization

**Returns:** a new filesystem-backed configuration

**Throws `NullPointerException`:** if path is null

**Throws `java.nio.file.InvalidPathException`:** if path cannot be parsed

**Throws `IllegalArgumentException`:** if key is null or blank

#### fromClasspath

```java
public static ModelParameters fromClasspath(String resource, String key)
```

Creates parameters whose resolver reads from the classpath through
`ValthorneFiles#readBytes(String)`. Keep referenced MTL files and
images at paths compatible with their relative references. The resource
name is stored unchanged; existence is checked only during loading.
Coordinate conversion is initially disabled.

- **`resource`** — the nonblank OBJ path relative to the classpath root
- **`key`** — the nonblank asset identity

**Returns:** a new classpath-backed configuration

**Throws `IllegalArgumentException`:** if resource or key is null or blank

#### withConversion

```java
public ModelParameters withConversion(boolean convertAndGround)
```

Creates a configuration with the requested coordinate-conversion flag,
preserving the source, key and same resolver instance. The original record
is unchanged, and a new record is returned even if the flag already matches.
This does not reload an existing asset or assign a new cache identity.

- **`convertAndGround`** — whether loading should center and ground Y-up input and swap its Y/Z axes to produce Z-up coordinates

**Returns:** a new configuration sharing this record's source, key and resolver

</details>

<a id="type-objmodel3d"></a>

### ObjModel3D

[Source](../../src/main/java/valthorne/graphics/model/ObjModel3D.java#L46)

Loads Wavefront OBJ triangle geometry with UVs, explicit normals, optional
vertex colors, and a subset of MTL diffuse materials. Convex polygon faces are
triangulated as fans; concave polygons are not tessellated. Positive and relative
negative indices are supported. Object, group, and smoothing declarations do
not alter geometry grouping, which follows material names.

Loading decodes texture images on the CPU without allocating OpenGL textures.
First submission through a supporting renderer uploads them lazily. The model
owns decoded images and uploaded textures, while part materials expose those
textures as borrowed references. Dispose on the GL thread after upload; before
upload, disposal releases CPU image data only.

The convenience loader converts Y-up positions to Z-up, centers the X/Y footprint,
and places the lowest point at Z zero. Disable that option to retain source
coordinates. File references are resolved relative to their declaring OBJ or
MTL path; a custom resolver can supply bytes from another storage source.

```java
ObjModel3D model = ObjModel3D.load("assets/scene.obj", false);
try {
    model.uploadTextures(); // With the rendering context current.
    // Submit the model through the scene's model rendering path.
} finally {
    model.dispose();
}
```

<details>
<summary>ObjModel3D operation reference (6 declarations)</summary>

#### load

```java
public static ObjModel3D load(String path)
```

Loads a filesystem OBJ with Y-up to Z-up conversion, horizontal centering,
and grounding enabled. Geometry and image decoding are CPU-only.

- **`path`** — OBJ filesystem path

**Returns:** newly owned model

**Throws `UncheckedIOException`:** if an OBJ, material, or image file cannot be read

**Throws `IllegalArgumentException`:** if input geometry or supported material data is malformed

#### load

```java
public static ObjModel3D load(String path, boolean convertAndGround)
```

Loads an OBJ from a normalized absolute filesystem path and resolves its
dependencies relative to their declaring files.

- **`path`** — OBJ filesystem path
- **`convertAndGround`** — whether to swap Y/Z, center horizontally, and ground the model

**Returns:** newly owned model

**Throws `UncheckedIOException`:** if a required file cannot be read

**Throws `IllegalArgumentException`:** if supported input data is malformed

#### load

```java
public static ObjModel3D load(String source, Resolver resolver, boolean convertAndGround)
```

Parses UTF-8 OBJ/MTL data through a caller-supplied byte resolver. Groups faces
by material, averages each triangle's source vertex colors, and supplies zero
UVs or generated face normals where references are omitted. Decodes each distinct
diffuse image once with vertical flipping enabled.

Supports MTL Kd, d, Tr, and map_Kd; texture-map options are rejected and other
directives are ignored. Failure disposes images decoded so far. OBJ parse errors
include the source path and line number.

- **`source`** — logical path used for loading and relative dependency resolution
- **`resolver`** — nonnull provider of source and dependency bytes
- **`convertAndGround`** — whether to convert axes and reposition the model

**Returns:** newly owned geometry and image resources

**Throws `UncheckedIOException`:** if the resolver reports a read failure

**Throws `IllegalArgumentException`:** if references, numbers, faces, or supported materials are invalid

#### getParts

```java
public List<Part> getParts()
```

Returns the unmodifiable material-group list. Its models and materials remain
mutable borrowed objects; uploaded textures are owned by this parent model.

**Returns:** material parts in first face-group encounter order

#### uploadTextures

```java
public void uploadTextures()
```

Uploads missing decoded images and binds the resulting owned textures to
part materials. Repeated calls reuse uploads and restore the original material
texture assignments, even if a caller changed those assignments. New uploads
run under a render-state snapshot; a fully uploaded model only reapplies Java
material references.

**Throws `IllegalStateException`:** if this model has been disposed

#### dispose

```java
public void dispose()
```

Releases all uploaded textures and decoded images, then marks the model
disposed. Repeated calls after successful disposal have no effect. Geometry
and part references remain accessible, but their former texture resources
must no longer be used. Requires the GL context if textures were uploaded.

</details>

<a id="type-objmodel3d-resolver"></a>

### ObjModel3D.Resolver

[Source](../../src/main/java/valthorne/graphics/model/ObjModel3D.java#L430)

Supplies owned byte arrays for logical OBJ, MTL, and image paths. The loader
passes normalized relative dependencies through the same resolver, allowing
filesystem, archive, or application-managed asset storage.

<details>
<summary>ObjModel3D.Resolver operation reference (1 declarations)</summary>

#### read

```java
byte[] read(String path) throws IOException
```

Reads the complete bytes for a logical resource. The loader decodes OBJ/MTL
bytes as UTF-8 and passes image bytes to the texture decoder.

- **`path`** — logical resource path

**Returns:** nonnull complete resource bytes

**Throws `IOException`:** if the resource cannot be read

</details>

<a id="type-objmodel3d-part"></a>

### ObjModel3D.Part

[Source](../../src/main/java/valthorne/graphics/model/ObjModel3D.java#L455)

A material group from the loaded OBJ. Components are retained by reference;
the parent model owns any textures it later assigns to the material.

The record groups geometry and material without adding an independent disposal operation.
Changing the material affects consumers of that same part; retaining a part does not
extend the lifetime of textures owned by the parent model.

- **`model`** — geometry for faces using this material group
- **`material`** — mutable diffuse material for the group

<a id="type-objmodel3d-ref"></a>

### ObjModel3D.Ref — internal support type

[Source](../../src/main/java/valthorne/graphics/model/ObjModel3D.java#L470)

Resolved zero-based vertex references for one OBJ face corner.

Indices address the separately parsed position, texture-coordinate, and normal lists.
Omitted texture coordinates or normals use minus one; this record stores indices only
and does not copy vertex data.

- **`p`** — required position index
- **`t`** — UV index, or -1 when omitted
- **`n`** — normal index, or -1 when omitted

<a id="type-objmodel3d-info"></a>

### ObjModel3D.Info — internal support type

[Source](../../src/main/java/valthorne/graphics/model/ObjModel3D.java#L479)

Mutable CPU material information collected from an MTL library before part
materials and decoded images are created. Unspecified diffuse color starts
opaque white and an unspecified texture remains absent.

<a id="type-pickresult3d"></a>

### PickResult3D

[Source](../../src/main/java/valthorne/graphics/model/PickResult3D.java#L34)

Describes a world-space model-triangle hit returned by
`Scene3D#pick(org.joml.primitives.Rayf)`. Scene picking selects the closest
finite hit among the tested model instances and visible node hierarchies;
this record itself performs no intersection or validation.

##### Instance and Node Identity

For a loose scene instance, `instance` is that live object and
`node` is null. For a scene-node hit, `node` is the original node
and `instance` is a temporary instance with the node's world transform
captured at query time. The temporary instance still shares model and material
references; it is not a deep copy of the node's resources.

##### Coordinates and Ownership

The position is computed as ray origin plus direction multiplied by distance.
Distance is the ray parameter and equals world-space distance when direction
is normalized. The scene creates a new position vector, but this record retains
it directly and its generated accessor returns that same mutable vector.

Record components are fixed references, not an immutable object graph.
Copy the position or instance state when a durable snapshot is needed. Direct
construction accepts null references and arbitrary distances without checks.

- **`instance`** — the hit loose instance or a transform snapshot for a node hit
- **`node`** — the original scene node, or null for a loose-instance hit
- **`distance`** — the intersection parameter along the query ray
- **`position`** — the mutable world-space hit point retained without copying

<a id="type-proceduralmeshemitter3d"></a>

### ProceduralMeshEmitter3D

[Source](../../src/main/java/valthorne/graphics/model/ProceduralMeshEmitter3D.java#L17)

Supplies geometry on demand for a `ProceduralRenderable3D`. This callback
is invoked from the renderable's mesh-emission method and receives both the
shared destination and the owning renderable's current transform state.
The callback itself is responsible for generating and transforming vertices.

Use the owner's transform helpers to convert local points and normals to
world space before appending triangles. Keep its local bounds consistent with
the generated geometry so culling and transparent sorting remain meaningful.
The emitter does not control the batch lifetime and may be invoked again on
later frames or separate rendering passes.

<details>
<summary>ProceduralMeshEmitter3D operation reference (1 declarations)</summary>

#### emit

```java
void emit(MeshBatch3D meshBatch, ProceduralRenderable3D renderable)
```

Generates and appends world-space geometry for the supplied owner. The
destination may already contain triangles from other compatible objects;
preserve them and leave rendering and disposal to the caller. Reading the
owner allows a reusable emitter to honor each instance's current transform.

- **`meshBatch`** — the prepared destination batch, borrowed for this callback
- **`renderable`** — the procedural owner whose geometry is being emitted

</details>

<a id="type-proceduralrenderable3d"></a>

### ProceduralRenderable3D

[Source](../../src/main/java/valthorne/graphics/model/ProceduralRenderable3D.java#L33)

A procedurally emitted mesh with a material, affine transform, and conservative
frustum culling. The emitter supplies world-space vertices; use this object's
point and normal transformation helpers when the generated geometry starts in
local coordinates. Set local bounds large enough to enclose that geometry.
An absent emitter, invalid bounds, or a cleared visibility flag prevents normal
visibility checks from accepting the renderable.

Position, scale, and local bounds are exposed as live mutable objects. Transform
evaluation notices direct position and scale edits, and world bounds are rebuilt
from all eight local corners when requested. Material and emitter references
are borrowed. This class owns no GPU resources and is not thread-safe.

```java
ProceduralRenderable3D surface = new ProceduralRenderable3D()
        .setEmitter(emitter)
        .setLocalBounds(-1, -1, 0, 1, 1, 1)
        .setPosition(4, 2, 0)
        .setScale(2);
Vector3f worldPoint = surface.transform(new Vector3f(0, 0, 1), new Vector3f());
```

<details>
<summary>ProceduralRenderable3D operation reference (27 declarations)</summary>

#### setRotation

```java
public ProceduralRenderable3D setRotation(Quaternionf rotation)
```

Copies and normalizes an orientation, switching subsequent transform evaluation
to quaternion mode. The caller may freely modify the supplied quaternion
after this call. Existing Euler values remain stored but are ignored until
an Euler setter is used.

- **`rotation`** — finite, nonzero quaternion

**Returns:** this renderable

**Throws `NullPointerException`:** if rotation is null

**Throws `IllegalArgumentException`:** if the quaternion is zero or nonfinite

#### setRotation

```java
public ProceduralRenderable3D setRotation(float x, float y, float z)
```

Switches to Euler orientation in Z-up space. Local points are rotated around
X, then Y, then Z before translation and the parent transform. Finite-value
validation occurs when the world transform is evaluated.

- **`x`** — X-axis rotation in radians
- **`y`** — Y-axis rotation in radians
- **`z`** — Z-axis yaw in radians

**Returns:** this renderable

#### setParentTransform

```java
public ProceduralRenderable3D setParentTransform(Matrix4f parent)
```

Copies the parent affine transform and invalidates the local transform cache.
The world matrix is parent times translation times rotation times scale.
The parent matrix is not validated here; callers must supply a usable affine
transform.

- **`parent`** — nonnull parent transform; later caller edits have no effect

**Returns:** this renderable

**Throws `NullPointerException`:** if parent is null

#### getWorldTransform

```java
public Matrix4f getWorldTransform(Matrix4f out)
```

Writes the current world matrix into a caller-owned destination. Evaluates
pending setter changes and direct edits to the position or scale vectors
before copying the matrix.

- **`out`** — nonnull destination matrix

**Returns:** out

**Throws `NullPointerException`:** if out is null

**Throws `IllegalArgumentException`:** if local transform components are nonfinite or scale contains zero

#### transformNormal

```java
public Vector3f transformNormal(Vector3f normal, Vector3f out)
```

Transforms a local normal using the inverse transpose of the world matrix's
linear part and normalizes any nonzero result. This preserves normal direction
under nonuniform scaling; a zero input normal remains zero.

- **`normal`** — local-space normal, which may also be out
- **`out`** — destination for the world-space normal

**Returns:** out

**Throws `NullPointerException`:** if either vector is null

**Throws `IllegalArgumentException`:** if the local transform is invalid

**Throws `IllegalStateException`:** if the world linear transform is effectively singular

#### getMaterial

```java
    public Material3D getMaterial()
```

Returns the live material used for mesh submission. Callers may mutate it;
neither reading nor replacing it transfers resource ownership.

**Returns:** current nonnull material

#### setMaterial

```java
public ProceduralRenderable3D setMaterial(Material3D material)
```

Replaces the material reference without copying or disposing either material.

- **`material`** — nonnull material to borrow for future submissions

**Returns:** this renderable

**Throws `NullPointerException`:** if material is null

#### getEmitter

```java
public ProceduralMeshEmitter3D getEmitter()
```

Returns the callback currently responsible for generating world-space geometry.

**Returns:** borrowed emitter, or null when geometry generation is disabled

#### setEmitter

```java
public ProceduralRenderable3D setEmitter(ProceduralMeshEmitter3D emitter)
```

Replaces the geometry callback without modifying the local bounds. Update those
bounds separately if the new geometry has a different extent.

- **`emitter`** — callback to borrow, or null to disable emission

**Returns:** this renderable

#### getPosition

```java
public Vector3f getPosition()
```

Returns the live translation vector in parent coordinates. Direct mutations
are detected on the next world-transform evaluation and must remain finite.

**Returns:** mutable position vector

#### setPosition

```java
public ProceduralRenderable3D setPosition(float x, float y, float z)
```

Stores translation in parent coordinates. Nonfinite inputs are rejected later
when the world transform is evaluated.

- **`x`** — X translation
- **`y`** — Y translation
- **`z`** — Z translation

**Returns:** this renderable

#### getScale

```java
public Vector3f getScale()
```

Returns the live local scale vector. Direct edits are detected during transform
evaluation; every component must be finite and nonzero. Negative scale is
supported.

**Returns:** mutable scale vector

#### setScale

```java
public ProceduralRenderable3D setScale(float uniformScale)
```

Applies the same local scale on all three axes. Negative values reflect all
axes; a nonfinite value is rejected during later transform evaluation.

- **`uniformScale`** — nonzero scale factor

**Returns:** this renderable

**Throws `IllegalArgumentException`:** if uniformScale is zero

#### setScale

```java
public ProceduralRenderable3D setScale(float x, float y, float z)
```

Stores per-axis local scale, allowing reflection through negative components.
Zero components are rejected immediately; finite-value checks occur when
the world transform is evaluated.

- **`x`** — nonzero X scale
- **`y`** — nonzero Y scale
- **`z`** — nonzero Z scale

**Returns:** this renderable

**Throws `IllegalArgumentException`:** if any component is zero

#### getYawRadians

```java
public float getYawRadians()
```

Returns the stored Euler Z angle. In quaternion mode this is the last Euler
value, not an angle extracted from the active quaternion.

**Returns:** stored yaw in radians

#### setYawRadians

```java
public ProceduralRenderable3D setYawRadians(float yawRadians)
```

Switches to Euler mode and replaces yaw while retaining the stored X and Y
Euler rotations. Finite-value validation occurs during transform evaluation.

- **`yawRadians`** — Z-axis rotation in radians

**Returns:** this renderable

#### isRenderableVisible

```java
public boolean isRenderableVisible()
```

Returns the explicit visibility flag without testing the emitter, bounds, or
camera frustum.

**Returns:** whether application visibility is enabled

#### setVisible

```java
public ProceduralRenderable3D setVisible(boolean visible)
```

Sets the application visibility flag used by both frustum-check overloads.
Direct calls to `emit(MeshBatch3D)` do not consult this flag.

- **`visible`** — whether visibility checks may accept this renderable

**Returns:** this renderable

#### getLocalBounds

```java
public AABBf getLocalBounds()
```

Returns the live local-space bounds used for culling. Direct edits are included
when world bounds are next requested. Keep the bounds consistent with emitted
geometry; initially they are empty.

**Returns:** mutable local bounding box

#### setLocalBounds

```java
public ProceduralRenderable3D setLocalBounds(AABBf bounds)
```

Copies local bounds without reordering their endpoints. Invalid or empty bounds
cause visibility checks to reject this renderable.

- **`bounds`** — nonnull bounds to copy

**Returns:** this renderable

**Throws `NullPointerException`:** if bounds is null

#### setLocalBounds

```java
public ProceduralRenderable3D setLocalBounds(float minX, float minY, float minZ, float maxX, float maxY, float maxZ)
```

Sets local bounds and corrects reversed endpoints independently on each axis.
The supplied coordinates should enclose all geometry generated by the emitter.

- **`minX`** — first X endpoint
- **`minY`** — first Y endpoint
- **`minZ`** — first Z endpoint
- **`maxX`** — second X endpoint
- **`maxY`** — second Y endpoint
- **`maxZ`** — second Z endpoint

**Returns:** this renderable

#### getWorldBounds

```java
public AABBf getWorldBounds()
```

Rebuilds the world-space axis-aligned box from the eight transformed local
corners. Returns a live internal result that is overwritten on the next call;
copy it if a stable snapshot is needed. Invalid local bounds produce an empty
world box.

**Returns:** reusable world-space bounds

**Throws `IllegalArgumentException`:** if the local transform is invalid

#### isVisible

```java
    public boolean isVisible(Camera3D camera)
```

Tests application visibility, emitter availability, valid local bounds, and
intersection with the supplied camera's current frustum. The camera is not
rebuilt here; the caller must keep its matrices current.

- **`camera`** — camera whose frustum is tested, or null to reject visibility

**Returns:** true if conservative culling accepts the renderable

**Throws `IllegalArgumentException`:** if evaluating the local transform fails

#### isVisible

```java
public boolean isVisible(FrustumIntersection frustum)
```

Tests the transformed bounds against a frustum after expanding them by the
larger of two world units or five percent of their largest extent. Returns
false for a null frustum, missing emitter, disabled visibility, or invalid bounds.

- **`frustum`** — current clipping frustum

**Returns:** whether the padded world bounds intersect the frustum

**Throws `IllegalArgumentException`:** if evaluating the local transform fails

#### emit

```java
    public void emit(MeshBatch3D meshBatch)
```

Invokes the current emitter with the borrowed destination batch and this owner.
Does nothing when no emitter is installed. This method does not perform
visibility checks, transform generated vertices, or manage the batch's lifetime;
the rendering pipeline and callback handle those responsibilities.

- **`meshBatch`** — prepared, nonnull mesh destination

**Throws `NullPointerException`:** if meshBatch is null

#### transform

```java
public Vector3f transform(Vector3f localPoint, Vector3f out)
```

Transforms a local point into world space, including translation and the parent
matrix. The input and destination may be the same vector.

- **`localPoint`** — nonnull local-space point
- **`out`** — nonnull destination

**Returns:** out

**Throws `NullPointerException`:** if either vector is null

**Throws `IllegalArgumentException`:** if the local transform is invalid

#### transform

```java
public Vector3f transform(float localX, float localY, float localZ, Vector3f out)
```

Transforms local coordinates as a position using the current world matrix.
Updates the transform cache before writing the caller's destination.

- **`localX`** — local X coordinate
- **`localY`** — local Y coordinate
- **`localZ`** — local Z coordinate
- **`out`** — nonnull destination vector

**Returns:** out

**Throws `NullPointerException`:** if out is null

**Throws `IllegalArgumentException`:** if the local transform is invalid

</details>

<a id="type-renderable3d"></a>

### Renderable3D

[Source](../../src/main/java/valthorne/graphics/model/Renderable3D.java#L25)

Supplies material, visibility and sorting information to `ModelBatch3D`.
A directly submitted implementation must also implement `MeshRenderable3D`
or `BillboardRenderable3D` to define how geometry is emitted; this base
interface alone is not a supported drawing backend.

Submission always checks explicit visibility. Camera-dependent visibility
is queried only when frustum culling is enabled, so implementations must keep
an application's hide/show flag in `isRenderableVisible()` instead of
relying solely on `isVisible(Camera3D)`.

Bounds and sort depth use world coordinates and the active camera's forward
direction. Custom renderables may be retained by reference until batch end;
keep their geometry and texture selection stable between submission and emission.
Resource ownership remains with the implementation or its caller.

<details>
<summary>Renderable3D operation reference (5 declarations)</summary>

#### getMaterial

```java
Material3D getMaterial()
```

Provides the rendering settings used to classify and draw this object.
The model batch substitutes its default material when this method returns
null and caches a copy per material identity during a batch. Do not rely
on later material mutations changing an already queued submission.

**Returns:** this object's material, or null to use the model batch's default

#### isVisible

```java
boolean isVisible(Camera3D camera)
```

Evaluates camera-dependent visibility, normally by testing current world
bounds against the camera frustum. The model batch passes its active camera
and skips this method entirely when culling is disabled. Implementations
may also reject missing geometry or other conditions that prevent drawing.

- **`camera`** — the active, rebuilt camera used for the visibility test

**Returns:** whether the object should survive camera-dependent culling

#### isRenderableVisible

```java
default boolean isRenderableVisible()
```

Reports explicit application-controlled visibility independently of the
camera. The default permits rendering; implementations with a hide/show
flag should return that flag so hiding works even with culling disabled.

**Returns:** whether this object is eligible for submission before culling

#### getWorldBounds

```java
default AABBf getWorldBounds()
```

Provides optional current axis-aligned world bounds. The default has no
bounds; the default depth calculation uses the center of valid bounds.
An implementation may return internal mutable storage, so callers should
treat the result as borrowed and copy it before retaining or modifying it.

**Returns:** world-space bounds, or null when unavailable

#### getSortDepth

```java
default float getSortDepth(Camera3D camera)
```

Computes signed depth as the dot product of the camera's forward direction
and the vector from its position to the bounds center. With a normalized
direction, the result is measured in world units along the view axis;
positive values are in front of the camera, not radial distances.

Missing or invalid bounds return zero without accessing the camera.
Otherwise this default allocates a temporary center vector. Override it
for geometry without bounds or an allocation-free implementation. The model
batch sorts translucent and additive objects from larger to smaller depth.

- **`camera`** — the active camera providing position and forward direction

**Returns:** signed center depth, or zero when valid bounds are unavailable

**Throws `NullPointerException`:** if camera is null and valid bounds are available

</details>

<a id="type-renderpass3d"></a>

### RenderPass3D

[Source](../../src/main/java/valthorne/graphics/model/RenderPass3D.java#L18)

Selects the ordering and blending category of a `Material3D`.
`ModelBatch3D` processes these categories in declaration order: opaque
submissions first, then translucent submissions, then additive submissions.
Opaque objects are grouped by material order; the other passes are sorted by
descending `Renderable3D#getSortDepth(valthorne.camera.Camera3D)`.

Calling `Material3D#setRenderPass(RenderPass3D)` also enables depth
writes for opaque materials and disables them for the other categories.
Depth testing and subsequent explicit depth-write changes remain separate
material settings. These categories do not provide per-triangle transparency
sorting or order-independent transparency.

<details>
<summary>RenderPass3D operation reference (3 declarations)</summary>

#### OPAQUE

```java
public static final  RenderPass3D OPAQUE
```

Draws without blending before transparent passes. Material grouping avoids
unnecessary state changes; depth testing resolves surface visibility when enabled.

#### TRANSLUCENT

```java
public static final  RenderPass3D TRANSLUCENT
```

Uses source-alpha/one-minus-source-alpha blending after opaque geometry.
Object-level back-to-front sorting approximates transparent compositing.

#### ADDITIVE

```java
public static final  RenderPass3D ADDITIVE
```

Uses source-alpha/one blending after the translucent pass, adding the
alpha-weighted source color to the destination for effects such as glow.

</details>

<a id="type-renderstatesnapshot3d"></a>

### RenderStateSnapshot3D

[Source](../../src/main/java/valthorne/graphics/model/RenderStateSnapshot3D.java#L30)

Captures a selected set of OpenGL state for restoration around 3D rendering.
Construction reads the current context immediately; `close()` writes
the captured values back. Use both operations on the same thread with the same
OpenGL context current, and close nested snapshots in reverse order.

```java
try (RenderStateSnapshot3D state = new RenderStateSnapshot3D()) {
    // Issue rendering commands that modify the state covered by this snapshot.
}
```

Covered state includes depth testing and writes, blending factors and equations,
face culling and winding, framebuffer sRGB enablement, shader program, vertex
array and array-buffer bindings, selected texture bindings, and the sampler on
unit two. Texture coverage is 2D bindings on units zero through two and buffer
textures on units three and four. The active texture selector is restored too.

This is not a complete OpenGL state snapshot. Framebuffer bindings, viewport,
scissor, stencil, and unlisted texture or sampler bindings remain the caller's
responsibility. The snapshot owns no GPU objects and does not keep captured
object names alive; avoid deleting those objects before restoration.

<details>
<summary>RenderStateSnapshot3D operation reference (2 declarations)</summary>

#### Constructor

```java
public RenderStateSnapshot3D()
```

Captures current state through field initializers and queries the selected
per-unit texture and sampler bindings. Temporarily selects units zero
through four, then returns to the original active texture unit.

A compatible OpenGL context must already be current. Construction does
not create GPU objects or perform drawing, and there is no deferred capture.

#### close

```java
    public void close()
```

Restores every captured setting and binding, finishing with the original
active texture selector. Uncaptured state is untouched. This method does
not dispose the referenced GPU objects or mark the snapshot as closed.

Each call reapplies the original values, so a second close can overwrite
changes made since the first. Keep the original context current and captured
resources alive for every restoration.

</details>

<a id="type-scene3d"></a>

### Scene3D

[Source](../../src/main/java/valthorne/graphics/model/Scene3D.java#L34)

Groups loose renderables and root-node hierarchies for submission and model
picking. Membership is stored in insertion order, with loose renderables
traversed before root nodes. The rendering batch remains responsible for
culling, material grouping and final pass ordering.

##### Usage

```java
Scene3D scene = new Scene3D();
scene.add(instance);
scene.addNode(rootNode);
boolean submittedVisibleObjects = scene.render(batch, renderState);
PickResult3D hit = scene.pick(ray);
```

This scene borrows objects and does not dispose them when removed or cleared.
Loose renderables may be added more than once; root-node membership is checked
for duplicates. Adding a root does not transfer hierarchy ownership or prevent
later reparenting, so callers must maintain valid scene-root membership.

Picking tests model triangles and node visibility, independently of rendering
materials or a camera frustum. Billboards and custom procedural emitters are
not picked by this class. All stored objects remain mutable; use a consistent
owning thread and avoid changing membership during traversal.

<details>
<summary>Scene3D operation reference (15 declarations)</summary>

#### render

```java
public void render(FilamentRenderer3D renderer,valthorne.camera.Camera3D camera)
```

Renders this scene through Filament into the current OpenGL viewport.

#### addLight

```java
public void addLight(PointLight3D light)
```

Adds a borrowed explicit Filament point light once, independently of mesh membership.

#### removeLight

```java
public boolean removeLight(PointLight3D light)
```

Removes an explicit light without disposing it or changing its parameters.

#### getLights

```java
public List<PointLight3D> getLights()
```

Cached, read-only live view of explicit point lights, excluding emissive mesh lights.

#### pick

```java
public PickResult3D pick(org.joml.primitives.Rayf ray)
```

Finds the closest finite forward model-triangle hit. Loose model instances
are tested first, followed by depth-first node traversal. Hidden nodes skip
their entire subtree; loose instances use their own intersection visibility
check. Equal-distance hits retain the first result encountered.

The ray and model transforms must use the same world space. Distance is
the ray parameter, equivalent to world distance for a normalized direction.
Texture transparency and material render-pass settings do not filter hits.

- **`ray`** — the nonnull world-space query ray

**Returns:** the nearest model hit, or null when no eligible triangle is hit

**Throws `NullPointerException`:** if ray is null

#### add

```java
public void add(Renderable3D renderable)
```

Appends a loose renderable without copying it or checking for duplicates.
Backend compatibility is checked later by the model batch at submission.

- **`renderable`** — the nonnull object to append

**Throws `NullPointerException`:** if renderable is null

#### remove

```java
public boolean remove(Renderable3D renderable)
```

Removes the first matching loose entry using list equality semantics.
Other duplicates remain, and no model or GPU resources are disposed.

- **`renderable`** — the entry to remove; null matches no normally added entry

**Returns:** whether a matching entry was removed

#### addNode

```java
public void addNode(SceneNode3D node)
```

Adds a parentless hierarchy root unless it is already present. The parent
check occurs before the duplicate check. Descendants stay attached to their
existing hierarchy; this method does not copy or reparent anything.

- **`node`** — the nonnull parentless root to register

**Throws `NullPointerException`:** if node is null

**Throws `IllegalArgumentException`:** if node currently has a parent

#### removeNode

```java
public boolean removeNode(SceneNode3D node)
```

Removes a registered hierarchy root without changing its children or
disposing resources. This only changes membership in this scene.

- **`node`** — the root to remove

**Returns:** whether the root was present and removed

#### clear

```java
public void clear()
```

Removes all loose entries and registered roots. Referenced objects remain
alive, node hierarchies remain intact, and native resources are not disposed.

#### size

```java
public int size()
```

Counts top-level membership entries, including duplicate loose renderables.
Descendants, model parts, visibility and eventual draw calls are not counted.

**Returns:** loose-entry count plus registered-root count

#### getRenderables

```java
public List<Renderable3D> getRenderables()
```

Returns an unmodifiable live view of loose membership in insertion order.
Later scene additions and removals appear in the view; contained objects
are still mutable. Copy the list when a stable membership snapshot is needed.

**Returns:** a read-only list view backed by this scene's loose entries

#### getNodes

```java
public List<SceneNode3D> getNodes()
```

Returns an unmodifiable live view of registered roots in insertion order.
This does not freeze node hierarchies or prevent later scene membership changes.

**Returns:** a read-only list view backed by this scene's root entries

#### submit

```java
public int submit(ModelBatch3D batch)
```

Queues loose renderables followed by each root hierarchy into an already
begun model batch. The batch decides visibility and supported backends.
This method neither begins nor ends the batch, enabling several scenes
to share one caller-managed submission interval.

If submission fails, earlier accepted entries remain queued until the
caller cancels or otherwise manages the batch. Use `render` when
this scene should own the begin/end/cancel sequence.

- **`batch`** — the nonnull batch already begun with an active render state

**Returns:** the sum of accepted submissions reported by the batch and node traversal

**Throws `NullPointerException`:** if batch is null

**Throws `IllegalStateException`:** if the batch rejects submission outside its active interval

#### render

```java
public boolean render(ModelBatch3D batch, MeshRenderState3D state)
```

Begins the batch, submits this scene and renders queued work through batch
end. After a successful begin, cancellation always runs in cleanup, including
when scene traversal or drawing throws. The scene and its objects are retained.

The result reflects the batch's visible-submission count, not a pixel
readback or proof that any fragment reached the framebuffer. Rendering
requires the graphics context expected by the batch to be current.

- **`batch`** — the nonnull batch whose lifecycle is managed for this call
- **`state`** — the nonnull render state and active camera configuration

**Returns:** whether the completed batch reports any visible submissions

**Throws `NullPointerException`:** if batch or state is null

</details>

<a id="type-scenenode3d"></a>

### SceneNode3D

[Source](../../src/main/java/valthorne/graphics/model/SceneNode3D.java#L32)

Hierarchical placement node with optional borrowed model geometry and material.
Child transforms compose with parent matrices, preserving shear from rotated
descendants of nonuniformly scaled parents. Visibility gates an entire subtree.
No GPU resources are owned and removing a child does not dispose its model.

```java
SceneNode3D assembly = new SceneNode3D().setPosition(3, 0, 0);
assembly.addChild(new SceneNode3D()
        .setModel(ModelBuilder3D.box(1, 1, 1)).setPosition(0, 0, 2));
batch.begin(state);
assembly.submit(batch);
batch.end();
```

Submission prepares world transforms and aggregate bounds before traversing
visible model nodes. Internal matrices and scratch instances are reused, so graph
mutation and traversal must remain on one thread and must not be reentrant.

<details>
<summary>SceneNode3D operation reference (22 declarations)</summary>

#### setRotation

```java
public SceneNode3D setRotation(Quaternionf rotation)
```

Copies and normalizes a finite, nonzero quaternion for transform composition. Stored Euler
angles remain unchanged but inactive until Euler mode is selected.

- **`rotation`** — local orientation to copy

**Returns:** this node

**Throws `IllegalArgumentException`:** if the quaternion is non-finite or zero

#### setRotation

```java
public SceneNode3D setRotation(float x, float y, float z)
```

Selects Euler mode and stores rotations applied X, then Y, then Z to local points.
Angles are stored without finiteness validation.

- **`x`** — X angle in radians
- **`y`** — Y angle in radians
- **`z`** — Z angle in radians

**Returns:** this node

#### getParent

```java
public SceneNode3D getParent()
```

Returns the current hierarchy parent without changing attachment.

**Returns:** parent node, or null

#### getWorldTransform

```java
public Matrix4f getWorldTransform(Matrix4f out)
```

Recursively composes parent and local translation/rotation/scale into caller
storage, preserving affine shear. Recomputes from live vectors on each call and
does not normalize or decompose the result.

- **`out`** — destination matrix distinct from internal scratch storage

**Returns:** out

#### getModel

```java
public Model3D getModel()
```

Returns this node's borrowed geometry without traversing descendants.

**Returns:** model, or null for a grouping-only node

#### setModel

```java
public SceneNode3D setModel(Model3D model)
```

Changes borrowed geometry for future preparation without altering children or
disposing the previous model.

- **`model`** — replacement model, or null

**Returns:** this node

#### getMaterial

```java
public Material3D getMaterial()
```

Returns the shared material used by this node's model. Child materials are independent.

**Returns:** live material reference

#### setMaterial

```java
public SceneNode3D setMaterial(Material3D material)
```

Retains a non-null material reference without copying it or propagating it to children.

- **`material`** — shared replacement material

**Returns:** this node

**Throws `NullPointerException`:** if material is null

#### getPosition

```java
public Vector3f getPosition()
```

Returns the live local translation vector. Changes are reflected during the next
world-transform query or traversal.

**Returns:** mutable local position

#### setPosition

```java
public SceneNode3D setPosition(float x, float y, float z)
```

Stores local translation without validating finiteness or changing parent links.

- **`x`** — local X translation
- **`y`** — local Y translation
- **`z`** — local Z translation

**Returns:** this node

#### getScale

```java
public Vector3f getScale()
```

Returns the live local scale vector. Direct edits bypass the setter's zero check;
callers must maintain a usable transform.

**Returns:** mutable local scale

#### setScale

```java
public SceneNode3D setScale(float uniformScale)
```

Sets every scale axis to one nonzero value. Negative scale is allowed; non-finite
values are not explicitly rejected here.

- **`uniformScale`** — common local scale

**Returns:** this node

**Throws `IllegalArgumentException`:** if scale is zero

#### setScale

```java
public SceneNode3D setScale(float x, float y, float z)
```

Stores nonzero local scale components. Negative scale and resulting parent-child
shear are preserved; finiteness is not checked by this setter.

- **`x`** — X scale
- **`y`** — Y scale
- **`z`** — Z scale

**Returns:** this node

**Throws `IllegalArgumentException`:** if any scale component is zero

#### getYawRadians

```java
public float getYawRadians()
```

Returns the stored Euler Z angle, which may be inactive under quaternion rotation.
Does not derive yaw from the world matrix.

**Returns:** stored yaw in radians

#### setYawRadians

```java
public SceneNode3D setYawRadians(float yawRadians)
```

Selects Euler mode and changes Z rotation while preserving stored X/Y angles.

- **`yawRadians`** — local Z angle in radians

**Returns:** this node

#### isVisible

```java
public boolean isVisible()
```

Returns this node's own visibility flag. Ancestor visibility is checked separately
when submitting a subtree.

**Returns:** local visibility flag

#### setVisible

```java
public SceneNode3D setVisible(boolean visible)
```

Changes whether traversal includes this node and its descendants. Geometry and
relationships remain retained while hidden.

- **`visible`** — subtree visibility flag

**Returns:** this node

#### addChild

```java
public SceneNode3D addChild(SceneNode3D child)
```

Appends a child, detaching it from its old parent when necessary. Rejects cycles;
an already-attached child is a no-op. Local transforms remain unchanged, so
reparenting can change world placement.

- **`child`** — node to attach

**Returns:** this parent

**Throws `NullPointerException`:** if child is null

**Throws `IllegalArgumentException`:** if attachment would create a cycle

#### removeChild

```java
public boolean removeChild(SceneNode3D child)
```

Detaches a direct child and clears its parent reference. Does not dispose resources
or preserve world placement by adjusting its local transform.

- **`child`** — child to detach

**Returns:** true if removed, false if not a direct child

#### clearChildren

```java
public void clearChildren()
```

Detaches all direct children by clearing their parent references and this list.
Descendant relationships, models, and materials remain intact.

#### getChildren

```java
public List<SceneNode3D> getChildren()
```

Returns an unmodifiable live view in insertion order. Child nodes remain mutable
and later attachment changes are reflected in the view.

**Returns:** live child-list view

#### submit

```java
public int submit(ModelBatch3D batch)
```

Checks this node and its ancestors for visibility, then prepares world transforms
and aggregate bounds before submitting visible geometry. Requires an active batch
when drawable content is traversed. Prepared instances are reused internally.

- **`batch`** — active destination batch

**Returns:** accepted model-node count

**Throws `NullPointerException`:** if batch is null

</details>

## Related guides

- [Cameras and picking](cameras.md)
- [Asset loading and caching](assets.md)
- [Raster 3D lighting and shadow maps](lighting-3d.md)
- [Filament rendering](filament.md)
- [Jolt rigid-body physics](physics.md)
- [Conservative 3D visibility and occlusion](culling.md)
- [Existing 3D guide](../3D.md)
