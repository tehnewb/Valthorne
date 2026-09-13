# Realistic textured models

The Physics Studio gallery uses three realistic models from Poly Haven, downloaded from its official asset API on 2026-09-09. Each includes its original 2048×2048 JPEG diffuse texture and detailed geometry. The previous Kenney furniture remains a separate lightweight asset set; these models supply the patterned ceramics, worn surfaces and sculpted detail for inspecting lighting.

| Model | Author | Exported triangles | Studio dimensions, width × depth × height |
| --- | --- | ---: | --- |
| [Antique Ceramic Vase 01](https://polyhaven.com/a/antique_ceramic_vase_01) | James Ray Cock | 9,408 | 1.276 × 1.276 × 2.200 |
| [Old Military Crate](https://polyhaven.com/a/old_military_crate), closed variant | Jack Mava | 10,476 | 4.335 × 2.858 × 1.600 |
| [Marble Bust 01](https://polyhaven.com/a/marble_bust_01) | Rico Cilliers | 17,456 | 1.267 × 1.397 × 2.400 |

The vase retains its blue floral pattern and crackled finish; the crate retains worn green paint, wood, latches and fabric detail; the bust retains carved facial/hair geometry and stone discoloration. Their detailed shapes and diffuse maps work with the existing OBJ loader. The current renderer uses uniform roughness of 0.30 for the vase, 0.80 for the crate and 0.55 for the bust, with trilinear mipmap filtering for textures. Normal, roughness, metallic and displacement texture maps from the original assets are not part of this conversion.

## License and provenance

Poly Haven identifies all three asset pages as CC0 and explains redistribution rights in its [asset license](https://polyhaven.com/license). The **CC0 1.0 Universal** legal text is included as `realistic/CC0-1.0.txt`, downloaded unchanged from [Creative Commons](https://creativecommons.org/publicdomain/zero/1.0/legalcode.txt). Author credit is retained for provenance.

The runtime files live in the examples repository under [`src/main/resources/valthorne/physics-studio/realistic/`](https://github.com/tehnewb/Valthorne-examples/tree/main/src/main/resources/valthorne/physics-studio/realistic). They consist of three OBJ files, three MTL files, three original JPEG textures, the CC0 text and `source-manifest.json`, totaling 8,240,888 bytes (approximately 8.24 MB). The separate Kenney furniture drop options add 83 KB. No Blender installation, network download or conversion step is needed to run the example.

The manifest records each source download URL, byte length, provider MD5 and independently calculated SHA-256, plus the hashes of every converted OBJ/MTL and unchanged JPEG. All downloaded source checksums matched the official API responses:

- [Vase file metadata](https://api.polyhaven.com/files/antique_ceramic_vase_01)
- [Crate file metadata](https://api.polyhaven.com/files/old_military_crate)
- [Bust file metadata](https://api.polyhaven.com/files/marble_bust_01)

## Coordinate and texture conversion

The official 2K glTF exports were converted offline into indexed OBJ geometry. All node transforms were applied to positions; normals received the corresponding inverse-transpose transform and normalization. OBJ coordinates remain in the source's **Y-up, meter-based** coordinate system. The studio loader uniformly scales each model to its display height, converts Y-up to Z-up, reverses triangle winding for that axis swap, centers XY and grounds the base at Z=0. Visual geometry and collision geometry use the same resulting coordinates. The bust's exhibit instance and physics body both receive a 180-degree yaw to face the viewer; this rotation does not change the source OBJ or collision alignment.

In the Design gallery, select one of these first three imported models and use **Focus selected exhibit** to inspect it closely. **Reset camera** restores the overview. The Kenney furniture models remain available for dropping and do not have static exhibits to focus.

OBJ texture coordinates use `v = 1 − glTF_v` to account for glTF's top-left texture origin and the existing OBJ loader's vertically flipped image upload. Every exported UV pair was compared against its source accessor; all matched within decimal-rounding error below 1e-8. None of these glTF assets uses a texture-coordinate transform. The JPEG bytes are unchanged. MTL files reference these textures with `map_Kd` and preserve base-color factors. The Filament adapter's materials use `flipUV: false` to preserve these OBJ coordinates: [Filament otherwise flips V by default](https://google.github.io/filament/main/materials.html), which would flip this pipeline a second time.

The crate glTF contains separate open and closed copies. This conversion exports only the five nodes ending in `_a`, forming one closed crate with its original lid, loop, latch, cloth and container. The open `_b` copy is excluded. No triangle simplification was applied to any exported model.

Conversion checks verified valid indices, finite positions and unit normals, all three 2048×2048 textures, and no degenerate triangles. The source crate contains one small triangle whose averaged smooth vertex normal points opposite its geometric face normal; its original geometry and normals are retained. This is recorded in the manifest. The vase and bust have no such winding/normal discrepancies.

Gallery exhibits use static triangle-mesh collision. Dropped copies use convex-hull collision, which fills concave gaps and the vase opening. They are rigid-body test objects: the vase does not break, the crate lid is not articulated, and the bust is not deformable. Jolt continues to handle simulation; Filament handles rendering.
