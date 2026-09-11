# Third-party notices

Valthorne source is licensed under [Apache-2.0](LICENSE). Dependencies retain
their own licenses; the library JAR does not repackage dependency classes or native
binaries. Distribute the licenses accompanying those dependencies with your game.

| Component | Purpose | Upstream license / notices |
| --- | --- | --- |
| LWJGL and its native modules | OpenGL, GLFW, OpenAL, STB, Yoga, NanoVG | [LWJGL license and bundled native notices](https://www.lwjgl.org/license) |
| JOML and JOML Primitives | Public math API | [MIT](https://github.com/JOML-CI/JOML/blob/main/LICENSE) |
| Jolt JNI / Jolt Physics | 3D physics | [Jolt JNI](https://github.com/stephengold/jolt-jni/blob/master/LICENSE) / [Jolt MIT license](https://github.com/jrouwe/JoltPhysics/blob/master/LICENSE) |
| Filament / community FFM bindings | Windows x64 renderer | Apache-2.0; copies in `src/main/resources/META-INF/licenses/` |
| MP3SPI, JLayer (transitive), Tritonus Share | MP3 decoding and Java Sound support | LGPL; retain the license and source availability information from their distributions |
| Apache Commons Compress | Archive support | Apache-2.0 |

## Assets included in the library

- **Atkinson Hyperlegible Regular**: SIL Open Font License 1.1. The font and
  `ui/OFL-AtkinsonHyperlegible.txt` ship together in the JAR.
- **Filament studio environment**: derived from Filament's `studio_small_02_2k.hdr`,
  originally HDRI Haven / Poly Haven, CC0. The dedication is in
  `META-INF/licenses/filament-environment-CC0.html`; generation is documented in
  [the Filament guide](docs/filament.md).
- Shader sources and Filament material sources/packages in this repository use
  the repository license unless an individual file states otherwise.

## Example-only assets

Examples and their assets are available in the separate [examples project](https://github.com/tehnewb/Valthorne-examples),
with its own [notices and provenance](https://github.com/tehnewb/Valthorne-examples/blob/main/THIRD_PARTY_NOTICES.md).
They are excluded from all engine library publication artifacts.
The following paths describe optional local files
and are not part of a fresh checkout. When
redistributing an example, preserve its resource directories and notices:

- Kenney furniture: `src/examples/resources/valthorne/physics-studio/models/kenney-LICENSE.txt`.
- Poly Haven gallery: `src/examples/resources/valthorne/physics-studio/realistic/source-manifest.json`
  and `CC0-1.0.txt`.
- FPS combat models: `src/examples/resources/valthorne/fps-arena/combat/source-manifest.json`
  and `licenses/`.
- FPS environment: `src/examples/resources/valthorne/fps-arena/environment/ATTRIBUTION.md`,
  `SOURCES.json`, `SHA256SUMS.txt`, and `CC0-1.0.txt`.

Those manifests record authors, source URLs, conversions, and license information.
JUnit and JMH are development dependencies and are not published as engine dependencies.
