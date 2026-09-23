# Publishing Valthorne

The stable version is `2.4.0` in `gradle.properties`. Normal builds and verification
need no signing/upload secrets. Runnable examples live in the separate
[companion project](https://github.com/tehnewb/Valthorne-examples).

## Validate the release revision

1. Review the intended source, resource, documentation, and build changes.
2. Run with JDK 25: `./gradlew clean verifyRelease` (`./gradlew.bat` on Windows).
   This checks compilation, Javadoc, packaged resources, licenses, and publication metadata.
3. Pass the packaging CI matrix on Windows, Linux, and macOS.
4. Inspect `build/docs/javadoc/` and the artifacts under `build/release-repository/`.
5. Record manual application and graphics verification separately. The previous
   automated test suites, generated consumer checks, and browser test runners have
   been removed; packaging CI does not establish runtime correctness. Earlier
   results remain in the [validation history](release-validation.md).

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

After publication, resolve `io.github.tehnewb:Valthorne:2.4.0` from Maven Central
in an independent consumer, verify the artifact/signature files, and tag the
validated commit `v2.4.0`. Include the JARs, sources, Javadoc and checksums in the
GitHub release. Future development must use a new version.
