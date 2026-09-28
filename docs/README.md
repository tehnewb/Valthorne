# Valthorne documentation

These guides describe the `2.4.0` source checkout. Published artifacts may
expose a different API revision.

- [Start a Java project](getting-started.md): complete Gradle/Groovy, Kotlin and Maven setup.
- [Platform matrix](platforms.md): supported desktop targets, native libraries and renderer limits.
- [System manual](systems/README.md): APIs, practical examples, lifecycle and ownership.
- [JOML migration](joml-migration.md): changes from the old engine math types.
- [Release procedure](releasing.md): build, consumer checks, signing and deliberate publication.
- [Performance ledger](performance-program.md): measurements and remaining coverage.
- [Historical benchmark evidence](benchmarks/README.md): source snapshots and measurement scope.

Build the API reference using `./gradlew javadoc`; open `build/docs/javadoc/index.html`.
Windows users should use `./gradlew.bat`. See [contributing](../CONTRIBUTING.md) for
verification and documentation conventions.
