package valthorne.scene;

import valthorne.Application;

/**
 * <h1>GameScreen</h1>
 *
 * <p>
 * {@code GameScreen} is a lightweight application-level scene controller that bridges your engine's
 * {@link Application} lifecycle with a currently active {@link Scene}. It is responsible for forwarding
 * initialization, rendering, updating, and disposal calls to whichever scene is currently active.
 * </p>
 *
 * <p>
 * The class is intentionally simple. It does not implement scene stacking, transitions, fades, or
 * background loading on its own. Instead, it focuses on one job: keeping exactly one active
 * {@link Scene} running at a time and making it easy to replace that scene during runtime.
 * </p>
 *
 * <h2>Lifecycle behavior</h2>
 * <ul>
 *     <li>{@link #init()} initializes the current scene if one exists.</li>
 *     <li>{@link #render()} wraps scene rendering in the scene's own {@code TextureBatch} begin/end cycle.</li>
 *     <li>{@link #update(float)} skips updates while the scene is paused.</li>
 *     <li>{@link #setScene(Scene)} disposes the previous scene before switching to the next one.</li>
 *     <li>{@link #dispose()} disposes the currently active scene if one exists.</li>
 * </ul>
 *
 * <h2>Why this class exists</h2>
 * <p>
 * Your engine already has an application entry point and a scene abstraction. {@code GameScreen} gives
 * those two systems a dedicated coordinator so your game code can stay clean. Instead of putting scene
 * switching logic inside the application itself, you centralize it here.
 * </p>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * Scene menuScene = new MainMenuScene();
 * GameScreen gameScreen = new GameScreen(menuScene);
 *
 * JGL.init(gameScreen, "My Game", 800, 600);
 * }</pre>
 *
 * @author Albert Beaupre
 * @since March 7th, 2026
 */
public class GameScreen implements Application {

    private Scene currentScene; // The currently active scene controlled by this game screen.

    /**
     * Creates a new {@code GameScreen} with an optional initial scene.
     *
     * <p>
     * The supplied scene is only stored here. It is not initialized automatically by the constructor.
     * Initialization still happens through the normal {@link #init()} lifecycle call.
     * </p>
     *
     * @param initialScene the first scene to control, or null if no scene should be active yet
     */
    public GameScreen(Scene initialScene) {
        this.currentScene = initialScene;
    }

    /**
     * Initializes the currently active scene if one exists.
     *
     * <p>
     * Before calling the scene's own {@link Scene#init()} method, this method first calls
     * {@link Scene#initializeFields()} so the scene has its runtime infrastructure prepared,
     * including its batch, viewport, UI, and listeners.
     * </p>
     */
    @Override
    public void init() {
        if (currentScene == null) {
            return;
        }

        this.currentScene.initializeFields();
        this.currentScene.init();
    }

    /**
     * Renders the currently active scene if one exists.
     *
     * <p>
     * Rendering is wrapped in the scene's internal {@link valthorne.graphics.texture.TextureBatch}
     * begin/end calls so scene implementations only need to focus on issuing draw commands inside
     * {@link Scene#render(valthorne.graphics.texture.TextureBatch)}.
     * </p>
     */
    @Override
    public void render() {
        if (currentScene == null) {
            return;
        }

        this.currentScene.drawScene();
    }

    /**
     * Updates the currently active scene if one exists and is not paused.
     *
     * <p>
     * When a scene is paused, update logic is skipped completely. This allows rendering to continue
     * while preventing game logic, animations, or simulation state from advancing.
     * </p>
     *
     * @param delta the elapsed time in seconds since the previous update
     */
    @Override
    public void update(float delta) {
        if (currentScene == null) {
            return;
        }

        if (currentScene.isPaused()) {
            return;
        }

        this.currentScene.updateScene(delta);
    }

    /**
     * Disposes the currently active scene if one exists.
     *
     * <p>
     * This is typically called when the application is shutting down. Disposal is delegated entirely
     * to the scene itself.
     * </p>
     */
    @Override
    public void dispose() {
        if (currentScene == null) {
            return;
        }

        this.currentScene.disposeScene();
    }

    /**
     * Replaces the current scene with a new one.
     *
     * <p>
     * If a scene is already active, it is disposed before the new scene is assigned. If the provided
     * scene is null, the current scene is simply cleared and no new scene is initialized.
     * </p>
     *
     * <p>
     * Unlike {@link #init()}, this method directly calls {@link Scene#initializeFields()} followed by
     * {@link Scene#init()} for the incoming scene so the scene is immediately ready for use after
     * the switch.
     * </p>
     *
     * @param scene the new scene to activate, or null to clear the current scene
     */
    public void setScene(Scene scene) {
        if (currentScene != null) {
            currentScene.dispose();
        }

        currentScene = scene;

        if (scene == null) {
            return;
        }

        scene.initializeFields();
        scene.init();
    }

    /**
     * Returns the currently active scene.
     *
     * @return the active scene, or null if no scene is currently assigned
     */
    public Scene getCurrentScene() {
        return currentScene;
    }
}