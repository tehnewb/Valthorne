# Third-party notices

Valthorne source is licensed under [Apache-2.0](LICENSE). Dependencies retain
their own licenses; the library JAR does not repackage dependency classes or native
binaries. Distribute the licenses accompanying those dependencies with your game.

| Component | Purpose | Upstream license / notices |
| --- | --- | --- |
| LWJGL and its native modules | OpenGL, GLFW, OpenAL, STB, Yoga, NanoVG | [LWJGL license and bundled native notices](https://www.lwjgl.org/license) |
| bgfx through LWJGL | Batched 2D shapes and instanced 3D geometry | [BSD-2-Clause](https://github.com/bkaradzic/bgfx/blob/master/LICENSE); retain LWJGL's bundled native notices |
| JOML and JOML Primitives | Public math API | [MIT](https://github.com/JOML-CI/JOML/blob/main/LICENSE) |
| Jolt JNI / Jolt Physics | Planar 2D physics using the Jolt 3D solver | [Jolt JNI](https://github.com/stephengold/jolt-jni/blob/master/LICENSE) / [Jolt MIT license](https://github.com/jrouwe/JoltPhysics/blob/master/LICENSE) |
| Filament / community FFM bindings | Windows x64 renderer | Apache-2.0; copies in `src/main/resources/META-INF/licenses/` |
| MP3SPI, JLayer (transitive), Tritonus Share | MP3 decoding and Java Sound support | LGPL; retain the license and source availability information from their distributions |
| Apache Commons Compress | Archive support | Apache-2.0 |

## Assets included in the library

- **JetBrains Mono Regular 2.304**: Copyright 2020 The JetBrains Mono Project
  Authors (https://github.com/JetBrains/JetBrainsMono). The unmodified font is
  licensed separately under the SIL Open Font License 1.1, not Apache-2.0.
  The font, [copyright notice](src/main/resources/valthorne/fonts/jetbrains-mono/COPYRIGHT.txt),
  [complete OFL.txt](src/main/resources/valthorne/fonts/jetbrains-mono/OFL.txt),
  authorship, and version/source provenance ship together at
  `valthorne/fonts/jetbrains-mono/` in the runtime and source JARs distributed
  through Maven and GitHub releases. Retain the copyright and complete OFL
  when redistributing the font in a game or other bundle.
- **Filament studio environment**: derived from Filament's `studio_small_02_2k.hdr`,
  originally HDRI Haven / Poly Haven, CC0. The dedication is in
  `META-INF/licenses/filament-environment-CC0.html`; generation is documented in
- Shader sources and Filament material sources/packages in this repository use
  the repository license unless an individual file states otherwise.
