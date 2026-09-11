# FPS arena environment assets

These are adapted **downloaded 3D meshes**, with authored UVs, normals and material
groups, from [Poly Haven](https://polyhaven.com/). All six source assets are
**CC0 1.0 Universal**. They are freely available from their source pages below.
Poly Haven's [asset license](https://polyhaven.com/license) explicitly permits
modification, commercial use and redistribution. Author credits are retained
voluntarily. License and source pages were checked on 2026-09-09.

| Source asset and author | Packaged models | Adaptation |
| --- | --- | --- |
| [Modular Factory Facade](https://polyhaven.com/a/modular_factory_facade), James Ray Cock | `factory_wall`, `factory_beam`, `factory_pillar`, `factory_backing` | Selected wall and window together, cornice profile, corner pier, and solid wall panel from the authored kit. |
| [Modular Fire Escape](https://polyhaven.com/a/modular_fire_escape), Juniix | `steel_deck`, `steel_platform`, `steel_stairs` | Selected platform and stair meshes. The thin deck excludes deep mounting braces; stair wall ties are trimmed. |
| [Concrete Road Barrier 02](https://polyhaven.com/a/concrete_road_barrier_02), Amal Kumar | `concrete_barrier` | Full downloadable glTF mesh, resized to arena cover dimensions. |
| [Wooden Crate 02](https://polyhaven.com/a/wooden_crate_02), James Ray Cock (modeling), Jurita Burger (graphic design) | `wooden_crate` | Crate and lid retained together, centered for physics. |
| [Barrel_01](https://polyhaven.com/a/Barrel_01), Jorge Camacho | `oil_barrel` | Full oil-drum mesh and explosive-marked red albedo, centered for physics. |
| [Modular Industrial Pipes 01](https://polyhaven.com/a/modular_industrial_pipes_01), Jorge Camacho | `industrial_pipe` | Selected long, flanged straight pipe, oriented along X. |

`SOURCES.json` records each exact download URL, original MD5, verified SHA-256,
source node selection, axis transform, scale, triangle count and material count.
`SHA256SUMS.txt` verifies the files distributed here. All distributed albedo maps
are the original 1024x1024 JPEGs. The engine's OBJ/MTL path consumes albedo maps;
source normal, roughness and metallic maps are not imported. The loader supplies
uniform roughness and metallic values per model, and keeps the authored colors.

Geometry was converted from glTF to OBJ with node transforms baked, an explicit
Y/Z axis swap, corrected triangle winding, inverse-transpose transformed normals,
and `v_OBJ = 1 - v_glTF` texture coordinates. There is no runtime download or mesh
conversion. See `tools/assets/convert_fps_environment.py` in the repository for the
conversion recipe and verified-download step.

## Coordinates and ownership

All dimensions below are full extents in meters. Every mesh is Z-up and centered
in X/Y, with yaw baked to zero. Structural meshes start at Z=0. **The crate and
barrel instead have their geometric bounds centered in all three axes**, matching
the body-centered convention used by Jolt model binding.

| Accessor | Dimensions X x Y x Z | Triangles |
| --- | --- | ---: |
| `wall()` | 4 x 0.4 x 4 | 2,278 |
| `floor()`, `ceiling()` | 4 x 4 x 0.18 | 892, shared once |
| `beam()` | 4 x 0.3 x 0.24 | 144 |
| `pillar()` | 0.45 x 0.45 x 4 | 576 |
| `platform()` | 6 x 6 x 1.4 | 956 |
| `ramp()` | 6 x 6 x 1.4, stairs ascend toward +Y | 5,674 |
| `cover()` | 3.2 x 0.9 x 1.5 | 23,822 |
| `crate()` | 0.5291 x 1.1663 x 0.4640 | 5,176 |
| `barrel()` | 0.5630 x 0.5630 x 0.8800 | 2,682 |
| `pipe()` | 4 x 0.25 x 0.25 | 648 |
| `backing()` | 4 x 4 x 0.08 | 4 |

The deck is a genuinely open metal grate. Place `backing()` beneath floor decks
and above ceiling decks to provide an opaque interior shell. The underlay uses
two opposing copies of the downloaded solid factory panel, rotated horizontally
and separated by 8cm. The converter verifies complete rectangular coverage,
coplanarity, uniform normals and affine UVs, then retains the four original corner
vertices/UVs. This removes redundant tessellation without changing the surface or
texture mapping. Adjacent
underlay modules meet at their edges; their outside perimeter is enclosed by the
arena walls. No runtime procedural primitive is substituted for either surface.

`FpsEnvironmentModels` owns the meshes and their texture resources. Instances
borrow them; create one catalog per arena and close it after renderer use.
`floor()` and `ceiling()` return the same entry to share their geometry and decoded
texture. Collision shapes remain simple proxies maintained by the game helper.
