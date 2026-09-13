package valthorne.website.content;

import java.util.List;

/**
 * The single source of product copy and destinations for Valthorne's landing page.
 *
 * <p>The engine renderer and the accessible HTML exporter consume the same
 * immutable Java values. This page introduces the library and points readers to
 * its maintained documentation, source, and community.</p>
 */
public final class LandingContent {
    public static final String TITLE = "Make it yours.";
    public static final String DESCRIPTION = "Valthorne gives Java developers the rendering, physics, audio, and interface tools to build their own games.";
    public static final String EYEBROW = "OPEN-SOURCE JAVA GAME ENGINE";
    public static final String VERSION = "2.0.0";
    public static final String REPOSITORY = "https://github.com/tehnewb/Valthorne";
    public static final String DOCUMENTATION = REPOSITORY + "/blob/main/docs/README.md";
    /** The README's published installation heading is 'Start a game'. */
    public static final String GET_STARTED = REPOSITORY + "/blob/main/README.md#start-a-game";
    public static final String COMMUNITY = "https://discord.gg/APqcDzppDv";
    public static final String LICENSE = REPOSITORY + "/blob/main/LICENSE";

    public static final String INTRO_TITLE = "One engine.\nYour entire vision.";
    public static final String INTRO_TEXT = "A connected set of rendering, simulation, sound, and interface systems. Use the pieces you need, bring them together, and stay in control of your code.";
    public static final String RESOURCES_TITLE = "Keep moving forward.";
    public static final String RESOURCES_TEXT = "Find your next step in the documentation, get closer to the source, or connect with the community.";
    public static final String CTA_TITLE = "Your next game\nstarts here.";
    public static final String CTA_TEXT = "Start with a Java project. Add Valthorne. Build what comes next.";
    public static final String FOOTER_TEXT = "Created by Albert Beaupre. Open source under Apache-2.0.";

    /** A numbered capability, its concise benefit, and the relevant system guide. */
    public record Feature(String number, String title, String text, String href) { }

    /** A maintained external resource with a descriptive link label. */
    public record Resource(String title, String text, String href, String label) { }

    /** Capabilities are presented as open rows, in the order a developer builds a game. */
    public static final List<Feature> FEATURES = List.of(
            new Feature("01 / RENDERING", "Build in 2D. Think in 3D.",
                    "Shape your game with sprites, models, cameras, materials, particles, and lighting. Move from a first frame to a world with depth.",
                    REPOSITORY + "/blob/main/docs/systems/models.md"),
            new Feature("02 / RUNTIME", "Bring your world to life.",
                    "Connect physics, audio, input, and UI through a clear application lifecycle. Make every interaction part of the same experience.",
                    REPOSITORY + "/blob/main/docs/systems/runtime.md"),
            new Feature("03 / WORKFLOW", "Stay in your element.",
                    "Keep your Java tools and project structure. Add the library through Gradle or Maven, with source you can inspect and extend.",
                    REPOSITORY + "/blob/main/docs/getting-started.md")
    );

    /** The landing page links directly to the project's established sources of information. */
    public static final List<Resource> RESOURCES = List.of(
            new Resource("Documentation", "From installation to individual systems, find the details that move your project forward.",
                    DOCUMENTATION, "Read the documentation"),
            new Resource("Source code", "Understand how the engine works, follow its development, and make it your own.",
                    REPOSITORY, "Explore the source"),
            new Resource("Community", "Ask questions, exchange ideas, and connect with other developers building with Valthorne.",
                    COMMUNITY, "Join the conversation")
    );

    private LandingContent() { }
}
