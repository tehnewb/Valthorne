# Release validation history

## 2.0.0 release candidate

Recorded 2026-09-10 (local time; CI timestamps are September 11 UTC) for
`4a2b8fd095058e9c41ae83082e54d874c8cd7aaf` on `main`. See the
[CI run](https://github.com/tehnewb/Valthorne/actions/runs/34550486582) for individual
job outcomes. The release tag identifies the final revision; later documentation
updates must also pass CI before publication.

| Check | Outcome |
| --- | --- |
| Clean Git archive on Windows x64, JDK 25.0.2 | `verifyRelease`, `verifyGraphicsConsumer`, and `verifyWindowConsumer` passed; 16 tasks executed |
| Library, sources, Javadoc and metadata | Validated; runtime resources and notices present |
| Repository exclusions | No example, test, benchmark or integration-test source folders in the archive |
| Windows x64 / Linux x64 / macOS Intel and ARM64 | Builds and separate Gradle metadata/POM-only consumers passed; LWJGL, Yoga and Jolt natives loaded |
| Linux graphics | Xvfb/Mesa context, texture-batch shader and framebuffer pixel check passed |
| macOS native windows | Creation, event polling, close state and first-thread launch passed on both architectures |
| macOS graphics | Unverified: both hosted architectures lack a usable NSGL OpenGL pixel format, including with plain GLFW defaults and a core context |
| Signing | Library, source and Javadoc detached signatures verified against the published public key |
| Documentation | Local links validated in 97 Markdown documents |

The generated consumer applications live only under ignored `build/` output.
They do not depend on the local test or example folders. The macOS window check
reports OpenGL availability separately and does not claim rendering coverage.
`verifyGraphicsConsumer` remains strict and must be run on a graphics-capable Mac
before relying on that platform for game distribution.

Signing key fingerprint: `D9A2478E33B55EAA82363D9C2238610E5152FDD3`.
These checks precede Central publication. The GitHub release records the final
commit, CI run and downloadable artifacts. Filament is Windows x64 only; compute
effects require OpenGL 4.3. Windows ARM64, Linux ARM64/ARM32 and application
installers require separate target-hardware validation. See [platform support](platforms.md).

## Historical snapshot checks

The remaining record describes earlier preparation snapshots and their local-only
examples/tests. Its statements about unpublished artifacts and CI not being run
apply to those snapshots, not to the candidate checks above.

### Follow-up: exclude local examples

At the user's request, all `src/examples/` source and resources are now ignored by
Git and excluded from normal builds and CI. Local files remain on disk. Example
launchers are optional, and publication checks reject example content in library,
source and Javadoc artifacts.

`verifyRelease` passed in a new isolated copy of 871 Git-eligible files with no
`src/examples/` directory. All 15 release tasks executed, both external consumers
passed, and links in 96 documentation files validated. No example compilation or
resource-processing task ran. The earlier report and hashes below describe the
previous validation snapshot, which still included local examples.

Recorded **2026-09-10**, for the `2.0.0-SNAPSHOT` working-tree snapshot prepared in
this session. Later concurrent engine edits need to rerun these checks before
publication. This is a local validation record, not a published release or a report
of remote CI results.

### Environment and isolation

- Windows 11 x86-64, Oracle JDK 25.0.2, Gradle wrapper 9.3.1.
- NVIDIA GeForce RTX 5070; the graphics tests used the installed native driver.
- A separate copy of **953 Git-eligible files** was built from scratch, excluding
  ignored project build output, caches, private assets, local prototype demos and
  credential files. The user's Gradle dependency cache remained available.
- The tested source/configuration hashes are in
  [release-validation/sources.sha256](release-validation/sources.sha256).

### Results

| Check | Result |
| --- | --- |
| `verifyRelease` from the isolated copy | Passed; all 17 tasks executed |
| Library and maintained examples compilation | Passed with Java 25 |
| Standard headless/native test suite | 140 tests, zero failures/errors; two optional local MP3/OGG fixture tests skipped |
| `graphicsTest` | 69 tests, zero failures/errors/skips |
| Library, sources, Javadoc and publication metadata | Passed; required shaders, font, Filament materials, environment and notices present |
| Separate Gradle metadata consumer | Compiled and ran; LWJGL/Yoga/Jolt natives loaded |
| Separate POM-only consumer | Compiled and ran; LWJGL/Yoga/Jolt natives loaded |
| Physics studio assets and light persistence | Passed, including 83 isolated persistence checks |
| FPS combat and environment assets | Passed |
| Native FPS gameplay | 1,425 checks passed |
| `runMinimalExample --args="--smoke"` | Passed |
| Local Markdown link validation | Passed across 94 documents at validation time |
| Documented Groovy and Kotlin application builds | Both compiled the documented Java starter against the local publication |

The POM consumer uses Gradle's POM-only resolver; a Maven CLI process was not run.
The documented Maven plugin versions were checked for availability. Dependency
launches emit upstream Gradle/JOML native-access/Unsafe warnings on this JDK; those
warnings did not fail validation and are not hidden by unsupported suppression flags.

### Scope still requiring target-host checks

The new CI workflow configures Windows, Linux and macOS build/consumer jobs and a
Linux Xvfb/Mesa graphics job. Those remote jobs were **not executed from this session**.
Windows ARM64, Linux ARM64/ARM32, macOS Intel and target-specific installers were
not tested here. Filament remains Windows x64 only, and compute effects require
OpenGL 4.3. See [the platform matrix](platforms.md).

No artifacts were uploaded, no release tag was created, and no Maven Central
publication was performed. Use [the release procedure](releasing.md) to validate
the final source revision and publish deliberately.
