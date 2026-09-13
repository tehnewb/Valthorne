package valthorne.website.content;

import java.util.List;

import static valthorne.website.content.Section.section;
import static valthorne.website.content.Card.card;

/**
 * Canonical website copy, routes, feature media, and integration examples.
 *
 * <p>Both the Java browser application and the static semantic exporter read
 * these immutable values. Edit this class to update page content; there is no
 * separately maintained JSON document or HTML template.</p>
 */
public final class WebsiteContent {
    /** Published engine dependency shown by the installation guide. */
    public static final String VERSION = "2.0.0";
    private static final List<Page> PAGES = List.of(index(), engine(), examples(), docs(), start(), lab(), about());
    private WebsiteContent() { }

    /** Returns every public route in navigation and sitemap order. */
    public static List<Page> pages() { return PAGES; }

    /** Returns a known route, rejecting unknown IDs instead of silently showing another page. */
    public static Page page(String id) {
        for (Page page : PAGES) if (page.id().equals(id)) return page;
        throw new IllegalArgumentException("Unknown website page: " + id);
    }

    /** Content for the index route. */
    private static Page index() {
        return Page.page("index")
            .title("Build games.\nWrite Java.")
            .eyebrow("VALTHORNE GAME ENGINE")
            .description("2D and 3D rendering, physics, audio, and UI in one open-source Java library. Add Valthorne to your project and work directly with the systems that power your game.")
            .heroImage("lighting-studio.png")
            .heroCaption("Lighting studio · Valthorne desktop demo")
            .sections(
                section("A toolkit for game development.")
                    .description("Start with a Java application. Bring in rendering, simulation, and interface systems as your game takes shape.")
                    .layout("cards")
                    .cards(
                        card("2D and 3D graphics")
                            .text("Build scenes with sprites, models, materials, cameras, particles, and lighting.")
                            .href("engine.html")
                            .label("Explore the engine")
                            .build(),
                        card("Physics and interaction")
                            .text("Add Jolt rigid bodies, collision shapes, joints, and raycasts to your simulation.")
                            .href("docs.html?q=physics")
                            .label("Read the physics guides")
                            .build(),
                        card("UI, audio, and assets")
                            .text("Create interfaces, play and stream sound, and manage the resources your game needs.")
                            .href("docs.html")
                            .label("Browse the documentation")
                            .build())
                    .build(),
                section("See the systems at work.")
                    .description("Explore real Valthorne applications. Each demo includes its source, resources, and a guide to the controls.")
                    .layout("showcase")
                    .cards(
                        card("Physics studio")
                            .text("Inspect a 3D scene, manipulate objects, and explore rigid-body interactions.")
                            .image("physics-studio.png")
                            .href("examples.html?q=physics")
                            .label("Explore physics demos")
                            .build(),
                        card("Interface demo")
                            .text("Explore layouts, controls, and text editing in an interactive UI example.")
                            .image("ui.png")
                            .href("examples.html?q=ui")
                            .label("Explore the UI demo")
                            .build())
                    .build(),
                section("Add Valthorne. Start building.")
                    .description("Use your existing Java tools and project structure. The integration guide covers Gradle and Maven, native dependencies, and your first application.")
                    .layout("feature")
                    .image("lighting2d.png")
                    .imageCaption("2D lighting · Valthorne desktop demo")
                    .cards(
                        card("One dependency, Java 25")
                            .text("io.github.tehnewb:Valthorne:2.0.0\nNative dependencies are included automatically.")
                            .href("start.html")
                            .label("Get started")
                            .build(),
                        card("Source you can work with")
                            .text("Read the implementation, follow development, and use the engine under Apache License 2.0.")
                            .href("https://github.com/tehnewb/Valthorne")
                            .label("View the source")
                            .build())
                    .build())
            .build();
    }

    /** Content for the engine route. */
    private static Page engine() {
        return Page.page("engine")
            .title("The engine behind your game.")
            .eyebrow("ENGINE")
            .description("A Java library for rendering, simulation, interfaces, and application infrastructure. Work directly with each system through an explicit application lifecycle.")
            .sections(
                section("Render in 2D and 3D.")
                    .description("From sprite-based scenes to lit 3D environments, choose the rendering tools that fit your project.")
                    .layout("feature")
                    .image("lighting-studio.png")
                    .imageCaption("Lighting studio · Materials, lights, and a 3D scene")
                    .cards(
                        card("2D graphics")
                            .text("Texture batching, sprites, TrueType fonts, Slug vector fonts, animation, Tiled maps, viewport scaling, and particle emitters.")
                            .href("docs.html?q=2d")
                            .label("Read the graphics guides")
                            .build(),
                        card("3D rendering")
                            .text("Models and materials, scene graphs, billboards, perspective and orthographic cameras, raster lighting, shadow maps, and an optional Filament renderer.")
                            .href("docs.html?q=3d")
                            .label("Read the 3D guides")
                            .build(),
                        card("Lighting and effects")
                            .text("Batched 2D lights, raycast shadows, radiance cascades, custom shaders, and optional path tracing. Availability depends on the graphics backend.")
                            .href("docs.html?q=lighting")
                            .label("Explore lighting")
                            .build())
                    .build(),
                section("Connect every part of the game.")
                    .description("Combine physics, interfaces, sound, and assets within the same application.")
                    .layout("feature")
                    .image("physics-studio.png")
                    .imageCaption("Physics studio · Interactive rigid-body simulation")
                    .cards(
                        card("Physics and motion")
                            .text("Jolt 3D physics provides rigid bodies, collision layers, contacts, shapes, distance joints, and raycasts. Particle emitters can interact with physics.")
                            .href("docs.html?q=physics")
                            .label("Physics documentation")
                            .build(),
                        card("Interface and input")
                            .text("Yoga layouts, texture and NanoVG controls, virtual lists and tables, shared editing behavior, themes, keyboard and mouse routing.")
                            .href("docs.html?q=ui")
                            .label("UI documentation")
                            .build(),
                        card("Sound and assets")
                            .text("WAV, MP3, and Vorbis sound; playback, queues, streaming, and attenuation. Asset loaders support asynchronous preparation and explicit disposal.")
                            .href("docs.html?q=audio")
                            .label("Audio documentation")
                            .build(),
                        card("Application foundations")
                            .text("Events, scenes, state machines, scheduling, plugins, diagnostics, collections, pooling, binary buffers, file utilities, compression, and hashing.")
                            .href("docs.html")
                            .label("Browse every system")
                            .build())
                    .build(),
                section("Plan for your platform.")
                    .layout("rows")
                    .description("The stable desktop library is 2.0.0. The browser target is in active development and does not provide every desktop API or native integration.")
                    .cards(
                        card("Windows, Linux, macOS")
                            .text("Java 25 with a matching CPU architecture and OpenGL 3.3 core for standard rendering. macOS needs -XstartOnFirstThread; Linux needs a graphical session and the documented native system libraries.")
                            .href("https://github.com/tehnewb/Valthorne/blob/main/docs/platforms.md")
                            .label("Desktop requirements")
                            .build(),
                        card("Browser · in development")
                            .text("TeaVM compiles Java to JavaScript. Browser backends support UI and graphics, with WebGL 2 and optional WebAssembly libraries for systems such as Yoga, Filament, and Jolt. Cross-browser validation is ongoing.")
                            .href("lab.html")
                            .label("See Valthorne in your browser")
                            .build(),
                        card("Optional renderer limits")
                            .text("The packaged desktop Filament path targets Windows x64. Compute-based desktop effects require OpenGL 4.3 and are unavailable on macOS OpenGL. Android is future work.")
                            .href("https://github.com/tehnewb/Valthorne/blob/main/docs/platforms.md")
                            .label("Check backend support")
                            .build())
                    .build())
            .build();
    }

    /** Content for the examples route. */
    private static Page examples() {
        return Page.page("examples")
            .title("Download the demos.")
            .eyebrow("DEMOS")
            .description("Ten examples covering graphics, physics, audio, UI, and a playable FPS arena. Windows x64 downloads include Java. Extract a ZIP, open Start.bat, and explore.")
            .sections(
                section("Choose a demo.")
                    .description("Search by name or system. Every download includes a controls guide and source; running it does not require Gradle.")
                    .catalog("examples")
                    .cards(DemoCatalog.cards())
                    .build(),
                section("Packages and source.")
                    .layout("rows")
                    .cards(
                        card("The complete collection")
                            .text("Get all examples together, including the bundled Windows x64 Java runtime.")
                            .href("https://github.com/tehnewb/Valthorne-examples/releases/download/v2.0.1/Valthorne-examples-windows-x64-2.0.1.zip")
                            .label("Download all demos")
                            .build(),
                        card("Other operating systems")
                            .text("The compiled universal package requires Java 25 and a supported native graphics environment. Follow the release instructions for your platform.")
                            .href("https://github.com/tehnewb/Valthorne-examples/releases/tag/v2.0.1")
                            .label("View all release packages")
                            .build(),
                        card("Learn from the source")
                            .text("The examples live in their own repository, with resources, per-demo guides, launch instructions, and asset credits.")
                            .href("https://github.com/tehnewb/Valthorne-examples")
                            .label("Browse example source")
                            .build())
                    .build())
            .build();
    }

    /** Content for the docs route. */
    private static Page docs() {
        return Page.page("docs")
            .title("Documentation.")
            .eyebrow("LEARN VALTHORNE")
            .description("Install the engine, understand its application lifecycle, and find practical guides for the systems in your game.")
            .sections(
                section("Start here.")
                    .layout("cards")
                    .cards(
                        card("Get started")
                            .text("Add the library, configure Java 25, and write your first application.")
                            .href("start.html")
                            .label("Installation guide")
                            .build(),
                        card("Migrating from 1.4.6")
                            .text("Version 2.0.0 changes public math types to JOML. Review the migration before updating an existing application.")
                            .href("https://github.com/tehnewb/Valthorne/blob/main/docs/joml-migration.md")
                            .label("Migration notes")
                            .build(),
                        card("Platform requirements")
                            .text("Check your operating system, CPU architecture, graphics backend, and runtime flags.")
                            .href("https://github.com/tehnewb/Valthorne/blob/main/docs/platforms.md")
                            .label("Platform guide")
                            .build())
                    .build(),
                section("System guides.")
                    .description("Search the manual by system or topic. Guides open on GitHub; match the documentation to your dependency version, since main may include ongoing development.")
                    .catalog("guides")
                    .cards(GuideCatalog.cards())
                    .build())
            .build();
    }

    /** Content for the start route. */
    private static Page start() {
        return Page.page("start")
            .title("Add Valthorne to your project.")
            .eyebrow("GET STARTED")
            .description("Install the Java 25 library, configure your application, and render your first frame. Standard desktop rendering requires an OpenGL 3.3 core graphics driver.")
            .sections(
                section("01 / Add Valthorne")
                    .layout("rows")
                    .filename("build.gradle")
                    .description("Native dependencies are included automatically. Use the complete integration guide for equivalent Kotlin and Maven configuration.")
                    .code("plugins { id 'application' }\nrepositories { mavenCentral() }\ndependencies {\n    implementation 'io.github.tehnewb:Valthorne:2.0.0'\n}\njava { toolchain { languageVersion = JavaLanguageVersion.of(25) } }\napplication {\n    mainClass = 'game.Main'\n    applicationDefaultJvmArgs = ['--enable-native-access=ALL-UNNAMED']\n    if (System.getProperty('os.name').toLowerCase(java.util.Locale.ROOT).contains('mac')) {\n        applicationDefaultJvmArgs += '-XstartOnFirstThread'\n    }\n}")
                    .cards(
                        card("Gradle, Kotlin, or Maven")
                            .text("The complete guide includes both Gradle DSLs, Maven configuration, native access, and launch instructions.")
                            .href("https://github.com/tehnewb/Valthorne/blob/main/docs/getting-started.md")
                            .label("Open the integration guide")
                            .build())
                    .build(),
                section("02 / Create an application")
                    .layout("rows")
                    .filename("game/Main.java")
                    .description("Save this as src/main/java/game/Main.java. Allocate resources in init, advance the game in update, draw in render, and release owned resources in dispose.")
                    .code("package game;\n\nimport valthorne.Application;\nimport valthorne.JGL;\nimport valthorne.Window;\nimport valthorne.graphics.Color;\n\npublic final class Main implements Application {\n    private final Color background = new Color(0xFF101211);\n\n    public static void main(String[] args) {\n        JGL.init(new Main(), \"My Game\", 960, 540);\n    }\n\n    public void init() { }\n    public void update(float delta) { }\n    public void render() { Window.clear(background); }\n    public void dispose() { }\n}")
                    .build(),
                section("03 / Run and extend your application")
                    .layout("rows")
                    .cards(
                        card("Run the application")
                            .text("Use the Gradle wrapper from the repository. Run ./gradlew run, or ./gradlew.bat run in Windows PowerShell. Keep the documented native access and macOS flags.")
                            .href("https://github.com/tehnewb/Valthorne/blob/main/docs/getting-started.md")
                            .label("Full launch instructions")
                            .build(),
                        card("Build your first scene")
                            .text("Add a texture, a camera, and input. Keep resource ownership explicit and dispose graphics resources while the graphics context is alive.")
                            .href("docs.html?q=runtime")
                            .label("Understand the lifecycle")
                            .build(),
                        card("Start from an example")
                            .text("Explore the starter application or a focused graphics, physics, audio, or UI demo in the examples repository.")
                            .href("examples.html")
                            .label("Choose an example")
                            .build())
                    .build())
            .build();
    }

    /** Content for the lab route. */
    private static Page lab() {
        return Page.page("lab")
            .title("Interactive geometry lab.")
            .eyebrow("BROWSER EXAMPLE")
            .description("Explore a procedural 3D object drawn with Valthorne Canvas2D. Drag to rotate, use the arrow keys, or pause the animation to inspect the geometry.")
            .sections(
                section("Geometry, projection, and shading.")
                    .description("Java calculates the geometry, perspective, and face shading; Valthorne Canvas2D paints the result. This is a UI rendering example. Download the desktop demos to explore the engine's 3D renderers and physics.")
                    .lab(true)
                    .build(),
                section("Inside the browser application.")
                    .layout("rows")
                    .cards(
                        card("Valthorne application lifecycle")
                            .text("JGL runs the application lifecycle. UIRoot manages the drawing context, a NanoContainer paints the site, and Canvas2D draws the layout, typography, and geometry.")
                            .href("https://github.com/tehnewb/Valthorne/tree/main/website")
                            .label("View the website module")
                            .build(),
                        card("Native browser interaction")
                            .text("HTML provides native links, scrolling, keyboard focus, editable search, and copyable code. The complete page is also available in Text version.")
                            .href("?view=text")
                            .label("Read the text version")
                            .build(),
                        card("Motion controls")
                            .text("Rotation stops when the scene is offscreen or the tab is hidden. Use Pause motion to stop animation across the site. System reduced-motion preferences are respected automatically.")
                            .href("about.html")
                            .label("About the project")
                            .build())
                    .build())
            .build();
    }

    /** Content for the about route. */
    private static Page about() {
        return Page.page("about")
            .title("An open-source Java engine.")
            .eyebrow("ABOUT VALTHORNE")
            .description("Valthorne is a game engine library by Albert Beaupre. The source, documentation, and examples are available for you to inspect, use, and improve.")
            .sections(
                section("Follow development. Get involved.")
                    .layout("rows")
                    .cards(
                        card("Explore the source")
                            .text("Inspect the engine, read its system documentation, and follow development on GitHub.")
                            .href("https://github.com/tehnewb/Valthorne")
                            .label("Engine repository")
                            .build(),
                        card("Report an issue")
                            .text("Include your engine version, OS, Java version, graphics hardware, and a small reproduction when possible.")
                            .href("https://github.com/tehnewb/Valthorne/issues")
                            .label("Issue tracker")
                            .build(),
                        card("Contribute")
                            .text("Read the contribution guide before proposing a change. Clear documentation and reproducible reports are useful contributions too.")
                            .href("https://github.com/tehnewb/Valthorne/blob/main/CONTRIBUTING.md")
                            .label("Contribution guide")
                            .build(),
                        card("Join the conversation")
                            .text("Discuss the engine, ask questions, and share what you are building with the community.")
                            .href("https://discord.gg/APqcDzppDv")
                            .label("Join Discord")
                            .build(),
                        card("Apache License 2.0")
                            .text("Engine code is distributed under Apache-2.0. Bundled dependencies, fonts, and example assets retain their respective licenses and notices.")
                            .href("https://github.com/tehnewb/Valthorne/blob/main/LICENSE")
                            .label("Read the license")
                            .build(),
                        card("Releases and changes")
                            .text("The stable desktop library is 2.0.0; the downloadable demo collection is 2.0.1. Browser support continues to evolve in the development checkout.")
                            .href("https://github.com/tehnewb/Valthorne/releases")
                            .label("View engine releases")
                            .build())
                    .build())
            .build();
    }
}
