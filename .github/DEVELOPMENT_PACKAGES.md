# Team development builds

The **Publish development package** workflow publishes to
`https://maven.pkg.github.com/tehnewb/Valthorne` on pushes to `main`.
It can also be run manually from Actions; select the branch to build in the
**Run workflow** menu. The workflow must exist on that branch.

Each build uses `<project-version>-dev.<full-commit-sha>.<run-id>.<attempt>`.
The complete dependency coordinate appears in the run's summary. Reruns get a
new coordinate rather than replacing a package; teammates must use the same
exact coordinate to share an identical build. No such version is implied to
exist until its publishing run succeeds.

GitHub's Maven registry requires lowercase artifact IDs, so development builds
use `io.github.tehnewb:valthorne`. Central releases keep
`io.github.tehnewb:Valthorne`. Replace the dependency when switching channels;
do not include both coordinates together, since both contain the same classes.

This does not change `gradle.properties`, create a release request, add version
labels, sign a Central release, or publish to Maven Central. Existing Central
release labels and workflows continue independently. A merge labeled for a
Central release can therefore run both workflows; omit release-triggering
labels when only a development build is wanted.

## Teammate authentication

GitHub's Maven registry requires authentication even for public packages.
Each developer creates a **classic personal access token** with
`read:packages` and an account allowed to read the repository/package.
Authorize organizational SSO if required. Do not give consumers
`write:packages` or put tokens in source files.

Store credentials in the user's Gradle properties file,
`~/.gradle/gradle.properties` (`%USERPROFILE%\.gradle\gradle.properties`
on Windows), outside the project and Git:

```properties
githubUser=YOUR_GITHUB_USERNAME
githubToken=YOUR_CLASSIC_PAT_WITH_READ_PACKAGES
```

## Consuming application

Add the following to the application's Groovy `build.gradle`:

```groovy
repositories {
    mavenCentral()
    maven {
        url = uri('https://maven.pkg.github.com/tehnewb/Valthorne')
        credentials {
            username = providers.gradleProperty('githubUser').get()
            password = providers.gradleProperty('githubToken').get()
        }
        content {
            includeModule('io.github.tehnewb', 'valthorne')
        }
    }
}

dependencies {
    // Replace VERSION_FROM_RUN_SUMMARY with the exact successful build version.
    implementation 'io.github.tehnewb:valthorne:VERSION_FROM_RUN_SUMMARY'
}
```

Use JDK 25 and the usual Valthorne native runtime options. Maven Central still
resolves third-party dependencies; Valthorne itself comes from GitHub Packages.
Do not use dynamic versions such as `+` when the team needs identical builds.

## Publishing and access

Actions publishes with its own `GITHUB_TOKEN` and job-scoped
`contents:read` / `packages:write` permissions. No publishing PAT or Maven
Central/signing secrets are used. The package is associated with this
repository; verify package/repository read access for teammates after its first
publication. GitHub Packages storage and transfer allowances apply.

The `githubPackages` Gradle mode requires the development version format above
and omits Maven Central repository configuration. Ordinary release builds keep
their existing Central configuration. The workflow explicitly calls only
`publishAllPublicationsToGithubPackagesRepository`.

For another repository's GitHub Actions to consume the package, grant that
repository package access first and give its consuming job `packages:read`.
Its `GITHUB_TOKEN` can then supply the repository credentials; local developers
continue to use their personal read-only tokens.

[GitHub Gradle registry documentation](https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-gradle-registry)
and [Maven registry naming requirements](https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-apache-maven-registry)
describe authentication, access, and the lowercase artifact ID restriction.
