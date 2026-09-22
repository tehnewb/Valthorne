# Publishing Valthorne

The stable version is `2.2.0` in `gradle.properties`. Normal builds and verification
need no signing/upload secrets. Tests, integration-test folders, benchmarks and
the engine's `src/examples/` directory are excluded from Git. Public examples are
maintained in the separate [companion project](https://github.com/tehnewb/Valthorne-examples).

## Validate the release revision

1. Review and commit the intended engine sources, library resources, documentation,
   build scripts and wrapper. Do not add local test/example folders or credentials.
2. Run with JDK 25:

   ```sh
   ./gradlew clean verifyRelease
   ./gradlew verifyGraphicsConsumer
   ```

   Windows PowerShell uses `./gradlew.bat`. The first command checks all publication
   artifacts and generates two disposable external consumers under ignored
   `build/release-consumer`: Gradle metadata and POM-only dependency resolution.
   Both compile the API, check packaged resources, load LWJGL/Yoga and step Jolt.
   The second opens a real OpenGL 3.3 context, creates a texture-batch shader and
   checks rendered pixels. It needs a display and driver; silent CI sets
   `ALSOFT_DRIVERS=null` only for that process.
3. Pass CI on the same commit. The matrix builds on Windows, Linux and macOS;
   Linux uses Xvfb/Mesa for graphics. Both macOS architectures check native windows
   with the first-thread JVM launcher; their hosted VMs cannot supply an NSGL
   pixel format. Mac rendering needs a separate hardware run of
   `verifyGraphicsConsumer` and must not be inferred from a passing CI window check.
   No test folders are copied into CI. Optional local `test`, `graphicsTest`,
   `verify3D`, `verifyPhysics3D`, `verifyLighting` and `verifyUI` suites provide
   additional regression coverage when their ignored source files are available.
4. Inspect `build/docs/javadoc/` and
   `build/release-repository/io/github/tehnewb/Valthorne/2.2.0/`. Library, sources
   and Javadoc JARs must contain no examples or test folders. Preserve runtime
   shaders, fonts, Filament materials/environment, and license notices.
5. Record the exact validated commit and platform outcomes. See the
   [validation history](release-validation.md). Check target installers and any
   feature-specific graphics paths beyond the consumer smoke before distributing
   an application. Filament remains Windows x64 only; compute requires OpenGL 4.3.

## Signing and publication

The build uses [Vanniktech Maven Publish](https://vanniktech.github.io/gradle-maven-publish-plugin/central/).
Keep `mavenCentralUsername` and `mavenCentralPassword` in the private user Gradle
home or use `ORG_GRADLE_PROJECT_` environment variables. Signing supports the
standard `signing.keyId`, `signing.password`, and `signing.secretKeyRingFile`
properties, or `signingInMemoryKey` / `signingInMemoryKeyPassword` (and optional
`signingInMemoryKeyId`). Never store actual values in this repository.

Verify signing without uploading:

```sh
./gradlew signMavenPublication -PreleaseSigning=true
```

After CI passes for the release commit, upload and release deliberately:

```sh
./gradlew publishAndReleaseToMavenCentral -PreleaseSigning=true
```

Alternatively `publishToMavenCentral -PreleaseSigning=true` uploads a deployment
for manual publication in the Central Portal. Neither task is part of CI or
`verifyRelease`. Maven Central versions are immutable; publish changed code under
a new version. Publication may take time to propagate to Maven Central mirrors.

After publication, resolve `io.github.tehnewb:Valthorne:2.2.0` from Maven Central
in an independent consumer, verify the artifact/signature files, and tag the
validated commit `v2.2.0`. Include the JARs, sources, Javadoc and checksums in the
GitHub release. Future development must use a new version.
