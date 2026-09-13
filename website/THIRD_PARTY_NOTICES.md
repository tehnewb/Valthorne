# Website distribution notices

The website source, generated landscape artwork, and Valthorne browser backend
snapshots use the repository's Apache License 2.0
(`runtime/LICENSE-Valthorne.txt`). The generated `runtime/valthorne.js` contains
the compiled Java website, reachable Valthorne classes, TeaVM runtime and
class-library code, and reachable JOML math code.

| Component | Version | License copy | Source |
| --- | --- | --- | --- |
| TeaVM | 0.15.0 | `licenses/TeaVM-Apache-2.0.txt` | https://github.com/konsoletyper/teavm |
| JOML | 1.10.8 | `licenses/JOML-MIT.txt` | https://github.com/JOML-CI/JOML/tree/1.10.8 |
| Yoga | 3.2.1 | `licenses/Yoga-MIT.txt` | https://github.com/facebook/yoga/tree/v3.2.1 |
| OpenType.js | 1.3.4 | `runtime/vendor/opentype/LICENSE` | https://github.com/opentypejs/opentype.js |
| Atkinson Hyperlegible Regular | bundled engine font | `runtime/ui/OFL-AtkinsonHyperlegible.txt` | https://www.brailleinstitute.org/freefont/ |
| Urbanist | normal variable, weights 100–900 | `assets/fonts/OFL-Urbanist.txt` | https://github.com/google/fonts/tree/8b0a1d0f5983c89bc2b93f1b5fb55f9e252744b5/ofl/urbanist |

`WorldArtwork.java` generates the original `assets/world.svg` illustration.
It contains no third-party imagery, models, textures, or remote references.
The original logo and banner are copied without alterations from this
repository's `images/logo-transparent.png` and `images/banner.png`.

Playwright is an Apache-2.0 development dependency used for verification. It is
not included in the deployed site. The website does not ship the full Filament,
Jolt, MP3, or Vorbis runtime distributions.
