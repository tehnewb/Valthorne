# Add Valthorne to a Java project

This guide targets **2.0.0**. Use documentation matching your dependency version;
2.0.0 changes public math types from 1.4.6. See [migration notes](joml-migration.md).

## Requirements

- JDK **25**, for both compilation and execution. Set `JAVA_HOME` to its installation.
- A desktop graphics driver with OpenGL **3.3 core** for the standard renderers.
- Windows, Linux, or macOS, using a JVM matching the target CPU architecture.
- Internet access for the initial dependency download. No native compiler is required.

See [platform support](platforms.md) for CPU targets, optional renderer limits,
Linux display/audio requirements, and macOS launch flags.

## Optional: install this checkout locally

From the Valthorne repository:

```sh
./gradlew publishToMavenLocal
```

In Windows PowerShell use `./gradlew.bat publishToMavenLocal`. This produces the
library, source, and Javadoc artifacts without publication credentials. It does
not upload to Maven Central. Use `./gradlew verifyRelease` to validate the library
and its external consumer classpaths before distributing a build.

## Gradle with Groovy

Use Gradle 9.3.1 (the repository wrapper) and create a `build.gradle`:

```groovy
plugins { id 'application' }
repositories {
    mavenCentral()
}
dependencies {
    implementation 'io.github.tehnewb:Valthorne:2.0.0'
}
java { toolchain { languageVersion = JavaLanguageVersion.of(25) } }
application {
    mainClass = 'game.Main'
    applicationDefaultJvmArgs = ['--enable-native-access=ALL-UNNAMED']
    if (System.getProperty('os.name').toLowerCase(java.util.Locale.ROOT).contains('mac')) {
        applicationDefaultJvmArgs += '-XstartOnFirstThread'
    }
}
```

Set `rootProject.name = 'my-game'` in `settings.gradle`. Copy Valthorne's
`gradlew`, `gradlew.bat`, and `gradle/wrapper/` into your application, then run
`./gradlew run` (`./gradlew.bat run` on Windows). No separate native classifier
selection is necessary. Add `mavenLocal()` before `mavenCentral()` only when using the optional local installation.

## Gradle with Kotlin

The equivalent `build.gradle.kts` is:

```kotlin
import java.util.Locale

plugins { application }
repositories {
    mavenCentral()
}
dependencies {
    implementation("io.github.tehnewb:Valthorne:2.0.0")
}
java { toolchain { languageVersion.set(JavaLanguageVersion.of(25)) } }
application {
    mainClass.set("game.Main")
    applicationDefaultJvmArgs = buildList {
        add("--enable-native-access=ALL-UNNAMED")
        if (System.getProperty("os.name").lowercase(Locale.ROOT).contains("mac")) {
            add("-XstartOnFirstThread")
        }
    }
}
```

Set `rootProject.name = "my-game"` in `settings.gradle.kts`.

## Maven

Use JDK 25 and this `pom.xml`; Maven automatically searches the local repository
when using the optional local installation, and Maven Central for published releases:

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>example</groupId>
    <artifactId>my-game</artifactId>
    <version>1.0-SNAPSHOT</version>
    <properties>
        <maven.compiler.release>25</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>
    <dependencies>
        <dependency>
            <groupId>io.github.tehnewb</groupId>
            <artifactId>Valthorne</artifactId>
            <version>2.0.0</version>
        </dependency>
    </dependencies>
    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.14.1</version>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-dependency-plugin</artifactId>
                <version>3.8.1</version>
            </plugin>
        </plugins>
    </build>
</project>
```

Build with `mvn package dependency:copy-dependencies`, then launch from the project directory:

```sh
# Linux
java --enable-native-access=ALL-UNNAMED -cp 'target/classes:target/dependency/*' game.Main
# macOS
java --enable-native-access=ALL-UNNAMED -XstartOnFirstThread -cp 'target/classes:target/dependency/*' game.Main
```

```powershell
# Windows: the classpath separator is a semicolon.
java --enable-native-access=ALL-UNNAMED -cp 'target/classes;target/dependency/*' game.Main
```

## Your first application

Save this as `src/main/java/game/Main.java` in your application project:

```java
package game;

import valthorne.Application;
import valthorne.JGL;
import valthorne.Keyboard;
import valthorne.Window;
import valthorne.graphics.Color;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE;

public final class Main implements Application {
    private final Color background = new Color(.055f, .075f, .12f, 1);

    public static void main(String[] args) {
        JGL.init(new Main(), "My Game", 960, 540);
    }

    public void init() { /* Create textures, batches, and other resources here. */ }
    public void update(float delta) {
        if (Keyboard.isKeyDown(GLFW_KEY_ESCAPE)) Window.requestClose();
    }
    public void render() { Window.clear(background); }
    public void dispose() { /* Release resources owned by this application here. */ }
}
```

Create graphics resources in `init`,
use them on the context thread, and dispose of them before the context closes.
`delta` is seconds. `JGL.init` runs the event loop synchronously; call it from
your application's main thread. Keep application assets in `src/main/resources`
and load them from the classpath, so they still work in packaged distributions.

## IDEs and distributing a game

Import the project as Gradle/Maven with JDK 25. In a direct IDE Application launch,
set the same VM options shown above. Gradle's settings in Valthorne do not transfer
automatically into a consuming application's launcher. Use the normal classpath;
JPMS/module-path packaging is not validated by this release preparation.

Runnable demos and their assets are available in the separate
[examples project](https://github.com/tehnewb/Valthorne-examples); see the [catalog](examples.md).
They consume the published engine and are excluded from its library artifacts.
A fresh engine checkout builds and validates without them.

Gradle `installDist`/`distZip` retain separate dependency/native JARs. Build platform
installers on each target OS and include a Java 25 runtime if users will not supply
one. The default dependency includes native binaries for all listed targets, so
the application can move between them. Native selection uses the running JVM's
architecture. If assembling a single shaded JAR, retain native resources, shaders,
fonts, notices, and merge `META-INF/services` (including Java Sound providers).
The standard separate-JAR distribution is what the release consumer checks exercise.
