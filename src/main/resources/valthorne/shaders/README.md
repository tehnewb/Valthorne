# Shader resources

All Valthorne shader source lives here and is included in the library JAR.
Java code loads UTF-8 source with `ShaderSources.load("effects/blur.frag")`,
using paths relative to `/valthorne/shaders/` on the classpath.

Names use lowercase words separated by hyphens. Complete stages use `.vert`
(vertex), `.frag` (fragment), or `.comp` (compute). Shared snippets and templates
use `.glsl`; example fragment bodies use `.frag.glsl` because the batch preamble
must be prepended before compilation.

| Folder              | Contents                                                                                      |
|---------------------|-----------------------------------------------------------------------------------------------|
| `core`              | Shapes and textured quads                                                                     |
| `effects`           | Blur, burn, flash, glow, outline, reflection, and water; share the textured-quad vertex stage |
| `font`              | Slug vector-font vertex and fragment stages                                                   |
| `lighting`          | Ray lights, light-map compositing, and irradiance baking                                      |
| `lighting2d`        | Instanced 2D lights and fullscreen compositing                                                |
| `model3d`           | Meshes, billboards, depth passes, and forward lighting; lighting shares the mesh vertex stage |
| `particle`          | Particle vertex and fragment stages                                                           |
| `pathtrace`         | Path tracing, denoising, and presentation                                                     |
| `radiance`          | Radiance cascade tracing, extension, merging, and resolve passes                              |
| `texture`           | Instanced texture-batch stages and custom-fragment preamble                                   |
| `texture/templates` | Sampler declarations and selection branches expanded for the requested texture-unit count     |
| `examples`          | Custom texture-batch fragment bodies from the demo and API documentation                      |

`texture/batch.frag` and `texture/batch-preamble.glsl` are templates, expanded by
`TextureBatchContract`. `${SAMPLERS}` and `${SELECTION}` insert declarations and
branches; `${INDEX}` in the smaller templates inserts each texture-unit index.
Use the contract's builder methods to obtain complete GLSL for these resources.

The outline demo body retains its original legacy `gl_FragColor` output. The
production outline effect is `effects/outline.frag` and uses a core-profile output;
use that effect with the engine's default core context.

The standard renderers target OpenGL 3.3 core. `pathtrace` and `radiance` compute
stages require OpenGL 4.3 and are unavailable on macOS OpenGL. These text resources
are runtime inputs, not generated build output: keep them in version control and
preserve their classpath paths when packaging a game. Release artifact verification
checks shader inclusion. See the repository's `docs/platforms.md` and
`docs/getting-started.md` for launch requirements.
