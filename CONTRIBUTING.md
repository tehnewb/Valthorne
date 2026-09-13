# Contributing to Valthorne

Thanks for taking the time to contribute.

## Getting Started

Use JDK 25 and the checked-in Gradle wrapper (9.3.1). No native compiler, signing
key or publication account is needed. See [setup](docs/getting-started.md),
[platform requirements](docs/platforms.md), and the [public example catalog](docs/examples.md).

Please search existing issues and pull requests before opening a new one. If you are not sure whether an idea belongs in an issue yet, start a discussion first.

If you want a place to jump in, look for labels such as `good first issue` and `help wanted`.

## Reporting Issues

Bug reports and feature requests are both welcome. Helpful reports usually include:

* A short summary of the problem or request
* Steps to reproduce
* Expected behavior
* Actual behavior
* Screenshots, logs, or sample code when they make the issue clearer

## Pull Requests

Pull requests are the fastest way to get improvements merged. A good PR usually:

* Focuses on one change or one closely related set of changes
* Explains what changed and why
* References related issues when applicable
* Includes tests or verification notes for behavior changes

Before opening a pull request, please run:

```bash
./gradlew build
./gradlew verifyRelease
```

Use `./gradlew.bat` in Windows PowerShell. `build` compiles the library, sources and
Javadoc. Tests, integration-test folders, benchmarks, examples and their resources
are ignored by Git in this engine checkout. They are not required in a fresh checkout.
Public demos are maintained in the separate [examples project](https://github.com/tehnewb/Valthorne-examples).
`verifyRelease` additionally checks the published artifacts and separate consumers.
For rendering changes, run `./gradlew verifyGraphicsConsumer` with a compatible OpenGL driver
and display. Focused `verify3D`, `verifyLighting`, and `verifyUI` tasks also include
graphics tests. Filament requires Windows x64; compute rendering requires OpenGL 4.3.

Keep optional local JUnit tests under the ignored `src/test/java/valthorne/`. Tag tests that create an
OpenGL context with `@Tag("graphics")`; do not silently skip driver failures on
supported graphics hosts. Local default-package demos and `src/test/java/games`
use private assets and are excluded from the maintained source sets.

## Repository ownership

Keep engine code and runtime resources under `src/main`. Release and generated
consumer checks live in `gradle/release.gradle` and `gradle/consumer-check.gradle`;
optional local benchmark configuration lives in `gradle/benchmarks.gradle`.

Make runnable demo and demo-resource changes in
[Valthorne-examples](https://github.com/tehnewb/Valthorne-examples), not the legacy
`src/examples` directory. With both repositories cloned alongside each other, run
the following from the examples repository to test unpublished engine changes:

```sh
./gradlew "-PvalthorneDir=../Valthorne" runMinimalExample --args="--smoke"
```

Use `./gradlew.bat` in PowerShell. Other launcher names are listed in the
[example catalog](docs/examples.md). Keep example packages, documentation, and
resource notices together in that repository.

Make website changes in [Valthorne-website](https://github.com/tehnewb/Valthorne-website).
Its Pages workflow deploys the site; this engine repository only retains a redirect
for the former website address.

Keep benchmark outputs produced during development under `build/`. Curated reports
under `docs/benchmarks` retain their source hashes and measured-environment notes;
do not rewrite historical results to describe an unmeasured revision.

## Reviews

Reviews primarily focus on correctness, maintainability, consistency with the rest of the engine, and whether the change is covered well enough by tests or verification.

## Documentation

Documentation improvements are always useful. Fixes for typos, missing examples, unclear wording, or outdated setup instructions are all welcome.

Java documentation must follow these conventions:

* Never write a block comment on a single line. Put the opening delimiter, content, and closing delimiter on separate lines.
* Document every static field with a multiline block comment, preferably Javadoc.
* Document every non-static instance field with a one-line `//` comment to the right of its declaration.
* This field rule applies only to non-static fields declared in a class body. Do not add trailing comments to record components or method/constructor parameters; document those with Javadoc `@param` tags instead.
* Give every class in-depth Javadoc.
* Include a practical usage example in the class-level Javadoc at the top of every class that encompasses a large system.
* Give every method and constructor in-depth, relevant documentation, including private methods and overrides. Explain its contract, parameters, return values, exceptions, side effects, and edge cases where applicable.

Base documentation on the implementation and its actual use. Explain relevant units, coordinate spaces, ownership, lifecycle, and ordering requirements without filler or unsupported guarantees.

## Getting Help

If you need help, open a discussion or ask in the community spaces linked from the repository. Questions are welcome.
