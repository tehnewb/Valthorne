# FPS arena combat models

The FPS arena uses downloaded, textured models for its rifle, first-person arms, robot targets,
grenades and ammunition magazines. These are authored game assets with realistic proportions and
surface detail; the SENTRY-2 target is a fictional industrial robot. They are not scans of an actual
robot or a complete animated character system.

All four source releases explicitly use **CC0 1.0**. Attribution is retained here and in the asset
manifest even though CC0 does not require it. Downloads were retrieved from the public author or
publisher uploads on OpenGameArt on **2026-09-09**, without requiring accounts.

| In-game role | Author and original source | Packaged geometry | Original base-color texture |
| --- | --- | ---: | --- |
| M4A1 rifle | nisu / 3DModelsCC0, [M4A1 Assault Rifle](https://opengameart.org/content/m4a1-assault-rifle) | 9,213 triangles | 2048 × 2048 PNG |
| Ammunition magazine | Separate `Magazine` object from the same M4A1 release | 716 triangles | Same M4A1 atlas |
| Industrial target robot | Quandtum, [SENTRY-2](https://opengameart.org/content/sentry-2-sentry-bot-mark-2) | 2,196 triangles | 2048 × 2048 JPEG |
| Mk2 grenade | LonesomeDucky, [Mk2 Grenade](https://opengameart.org/content/mk2-grenade) | 2,568 triangles | 1024 × 1024 PNG |
| First-person arms and hands | para; base mesh and texture by the MakeHuman team, [fps arms](https://opengameart.org/content/fps-arms-rigged-only) | 8,152 triangles | 1024 × 1024 PNG |

The arms release credits MakeHuman for its base geometry and skin texture. MakeHuman separately
publishes its [core assets under CC0](https://static.makehumancommunity.org/about/license.html).
The SENTRY-2 source credits Plaintextures and Goodtextures for texture samples; its author page
includes the texture provider's permission discussion, and its original CC0 readme is bundled.

The five models total **22,845 triangles**. Runtime geometry, four original image files and license
files occupy **8,877,005 bytes**, plus the small manifest. The larger source archives, Blender files,
unused maps, alternate retro grenade, previews and conversion tools are excluded from runtime
resources. No runtime download is performed.

## Coordinates and placement

`FpsCombatModels` loads the assets from `valthorne/fps-arena/combat/` with the OBJ loader's legacy
axis conversion disabled. All coordinates are already baked to **meters, Z-up and centered XYZ**.
That center convention matches the rigid-body binding convention and avoids visual offsets when
Jolt synchronizes a body. Rifle barrels and the robot's front face point along **+Y**.

| Entry | Width X | Depth Y | Height Z |
| --- | ---: | ---: | ---: |
| `rifle()` | 0.066653 m | 0.900000 m | 0.279082 m |
| `drone()` | 1.274157 m | 1.260231 m | 1.700000 m |
| `grenade()` | 0.109851 m | 0.091968 m | 0.180000 m |
| `ammo()` | 0.021577 m | 0.091439 m | 0.193195 m |
| `arms()` | 0.528702 m | 0.804268 m | 0.367552 m |

Each entry exposes `model()`, an independent instance `material()`, dimensions and identity
`rotation()` metadata. Geometry and texture ownership stays with the library. Instances borrow
these resources; close the library after instances stop rendering, on the render thread if its
textures have been uploaded.

The arms were posed using their downloaded rig around the M4A1 pistol grip and handguard, then
baked to a static mesh. Their centered origin differs from the rifle origin. Add
`arms().attachmentOffset(destination)` in rifle-local space before applying the shared rifle
rotation and scale. Its value is `(-0.123832673, -0.214057371, -0.080805525)` meters. This restores
the common grip frame and supports moving the arms and rifle together during sway or reload
presentation. The packaged OBJ does not contain skeletal animation.

## Conversion and verification

The source FBX, Blender and GLB files were converted with the official **Blender 4.5.3 LTS** portable
distribution, with source script execution disabled. Object transforms, the robot's facing
rotation and the arms' pose were baked before triangulation. Hidden rig controls, preview cameras
and lights were excluded. All visible rifle components, including its magazine, remain present.

The M4A1 source contained 23 zero-area triangles, including 20 in its magazine. These were omitted.
Seven remaining rifle corners had zero authored normals; their normals were reconstructed from
their nondegenerate face winding. Other authored normals, UVs and texture bytes were retained.
Indices deduplicate identical position/UV/normal combinations without collapsing texture seams.

Textures retain their original resolutions and file bytes. Blender's imports produce standard OBJ
UVs; no additional runtime UV inversion is needed. Filament materials use `flipUV: false`, matching
the engine's OBJ/image upload convention. The OBJ material groups use the diffuse atlas and runtime
roughness/metallic settings. Normal, roughness and metallic texture maps in the original releases
are not currently consumed by this OBJ material path.

`FpsCombatModels.main()` provides CPU-only checks for centered finite bounds, nonempty complete
material groups, finite vertices and unit normals. All five entries pass these checks. Renderer and
gameplay validation is performed by the FPS arena verification tasks.

This archived asset description records the 2026-09-09 conversion. See the
preserved [`combat-source-manifest.json`](combat-source-manifest.json)
for exact public download URLs, archive sizes and SHA-256 values, generated-file hashes, original
texture metadata, conversion bounds, and source object names. The bundled
[`CC0 legal text`](https://github.com/tehnewb/Valthorne-examples/blob/main/src/main/resources/valthorne/fps-arena/combat/licenses/CC0-1.0.txt), now maintained with the companion assets, applies
to these asset releases; the engine's own source license is unchanged.
