package valthorne.website.content;

import java.util.List;
import static valthorne.website.content.Card.card;

/** Downloadable examples live in their own release repository, outside the library and website. */
public final class DemoCatalog {
    /** Version of the packaged demos, independent of the engine dependency version. */
    public static final String VERSION = "2.0.1";
    private static final String REPOSITORY = "https://github.com/tehnewb/Valthorne-examples";
    private DemoCatalog() { }

    /** Returns all ten demos in the public collection order. */
    public static List<Card> cards() {
        return List.of(
            demo("starter", "Application starter", "systems", "", "A minimal application with lifecycle callbacks and a clean starting point."),
            demo("scene", "3D scene", "3d", "scene.png", "Explore models, materials, cameras, and scene composition."),
            demo("physics", "Physics playground", "3d", "physics.png", "Drop and interact with rigid bodies powered by Jolt."),
            demo("lighting2d", "2D lighting", "2d", "lighting2d.png", "Explore colored lights, occlusion, and real-time 2D shadows."),
            demo("ui", "UI gallery", "systems", "ui.png", "Explore layouts, controls, themes, and text editing."),
            demo("audio", "Audio studio", "systems", "audio.png", "Experiment with sound playback, controls, and queues."),
            demo("lighting-studio", "Lighting studio", "3d", "lighting-studio.png", "Inspect a lit 3D environment and adjust the scene."),
            demo("path-tracing", "Path tracing", "3d", "path-tracing.png", "Explore the optional path-tracing renderer and its settings."),
            demo("physics-studio", "Physics studio", "3d", "physics-studio.png", "Manipulate objects and examine an interactive physics scene."),
            demo("fps", "FPS arena", "3d", "", "Explore a playable first-person scene with physics, shooting, and effects."));
    }

    /** Keeps platform labels, source URLs, and release filenames consistent for every package. */
    private static Card demo(String id, String title, String category, String image, String text) {
        return card(title).category(category).image(image)
                .text(text + " Windows x64; Java included.")
                .label("Download ZIP")
                .href(REPOSITORY + "/releases/download/v" + VERSION + "/Valthorne-demo-" + id + "-windows-x64-" + VERSION + ".zip")
                .guide(REPOSITORY + "/blob/main/docs/" + id + ".md").build();
    }
}
