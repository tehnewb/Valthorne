# Physics Studio furniture models

The imported-model drop selector includes three lightweight Wavefront OBJ models from [Kenney's Furniture Kit](https://kenney.nl/assets/furniture-kit). They remain available for spawning; the default Design gallery displays the [realistic Poly Haven vase, crate and bust](physics-studio-realistic-models.md). Kenney labels the furniture pack **Creative Commons Zero (CC0)** on the official download page and in its bundled license. The original license is included at `src/examples/resources/valthorne/physics-studio/models/kenney-LICENSE.txt`. The [CC0 dedication](https://creativecommons.org/publicdomain/zero/1.0/) permits reuse and modification; attribution is retained here for provenance.

Downloaded on 2026-09-09 from [the official Kenney archive](https://kenney.nl/media/pages/assets/furniture-kit/440e0608a4-1677580847/kenney_furniture-kit.zip). The archive is 5,130,729 bytes. Its SHA-256 is `e67652d0932cee41683f74711c03d3e192a2af9979ef8e6b237711f5482d46b0`. The bundled license identifies Furniture Kit 2.0; the website's update list labels its 2018 release 1.0. The hash identifies the exact downloaded archive despite that version-label difference.

Only six OBJ/MTL files and the license are distributed: 82,990 bytes total. The OBJ and MTL files are unchanged from `Models/OBJ format/` in the archive. The license is unchanged apart from its filename. No textures are required; the authored wood, metal and fabric colors are retained from the MTL material groups.

| Display name | Original model | Baked height | Materials |
| --- | --- | --- | --- |
| Design sofa | `loungeDesignSofa.obj` | 1.6 units | Blue fabric and metal |
| Lounge chair | `loungeChairRelax.obj` | 1.8 units | Coral fabric, wood and metal |
| Desk chair | `chairDesk.obj` | 2.0 units | Coral fabric and dark metal |

`PhysicsStudioModels` loads all six assets from the example classpath, with the three realistic exhibits first and these three furniture models afterward. It uniformly scales source vertices before OBJ loading, then uses the loader's Y-up to Z-up conversion, corrected triangle winding, XY centering and base-at-zero placement. Geometry is normalized once, including every material part. Every load verifies dimensions, centered/grounded placement, material coverage and finite unit surface normals. `Entry.width()`, `depth()` and `height()` report full normalized extents. No additional model-instance scale is required.

Use `CollisionShape3D.mesh(entry.model())` for a static display whose collision follows the detailed geometry. A dynamic spawn may use `CollisionShape3D.convexHull(entry.model())`, which fills concave gaps beneath and between furniture parts. This is an intentional collision approximation, not an articulated chair or deformable cushion simulation. Both shapes read the same baked model geometry; do not apply a visual-only scale afterward. Physics remains handled by Jolt.

Select one of these models under **Imported models**, then use **Drop selected model** to create a dynamic copy with a convex collider. **Focus selected exhibit** applies to the first three realistic exhibits in the Design gallery, so it is disabled when furniture is selected. **Reset camera** restores the overview.

The shared gallery owns its loaded OBJ resources. Reuse each entry for multiple instances and close the gallery on the render thread after rendering has stopped. These furniture models have no textures, but the same resource owner also manages the realistic models' uploaded textures. Run `verifyPhysicsStudioAssets`, or the `PhysicsStudioModels` main class with the examples classpath, for CPU-only asset verification. The combined resources comprise approximately 8.24 MB of realistic assets plus these 83 KB of furniture.

## File hashes

| File | SHA-256 |
| --- | --- |
| `chairDesk.mtl` | `6d45d00e9569fe5b1abc4c9fb1caedc99cb7b519cdcd09a1377ecd8cb79f6f53` |
| `chairDesk.obj` | `2e4448333f39ceda9f3e90f02236463e34fbae80d470084ae2009541c16c89c6` |
| `kenney-LICENSE.txt` | `bc0de1a0742cb490f9ed4bae0bd284286ebb27e23149b9917c7d8c0d590b8b29` |
| `loungeChairRelax.mtl` | `18df9acd06513dae5daeac35bd2d288eaf4bc0f04317c8473e98e90c2f9c7c1e` |
| `loungeChairRelax.obj` | `93e00e571dd42662f99cc6d3cb9f8029979e3ca5da912bccbbaa0304e5882a7d` |
| `loungeDesignSofa.mtl` | `a493fa76745d752d4f10d93e1b07f2558a56a79e6a05536c4b8815a0f16391da` |
| `loungeDesignSofa.obj` | `0f5b9820b616db3f9ce4ff018eeb4088343c1fc32216df87dc34b9e0d74fea49` |
