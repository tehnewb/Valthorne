package valthorne.scene;

/**
 * The Scene class represents an abstract lifecycle framework for managing
 * various game or application scenes. It provides a structure for
 * initialization, rendering, updating, and disposing of resources necessary
 * for individual scenes.
 * <p>
 * Subclasses must implement the abstract methods to provide specific
 * behavior for their respective scenes. The lifecycle methods are typically
 * managed by a controller class, such as GameScreen, which delegates these
 * operations to the active Scene.
 * <p>
 * The Scene class also supports a paused state, enabling scenes to be
 * temporarily suspended and preventing updates while paused.
 *
 * @author Albert Beaupre
 * @since February 13th, 2026
 */
public abstract class Scene {

    private boolean paused;

    /**
     * Initializes the scene. This method is called to set up the necessary resources
     * and prepare the scene for operation. Subclasses must implement this method to
     * define the specific initialization logic required for the scene they represent.
     * <p>
     * Typical operations performed in this method may include loading assets,
     * registering event listeners, and setting up the initial state of the scene.
     * <p>
     * This method is invoked by the controller, such as the {@code GameScreen},
     * during its lifecycle management of the active {@code Scene}.
     */
    public abstract void init();

    /**
     * Renders the current state of the scene. This method is responsible for
     * drawing all visual elements associated with the scene to the screen.
     * Subclasses must implement this method to define specific rendering behavior
     * for the scene they represent.
     * <p>
     * This method is typically invoked by a controller, such as {@code GameScreen},
     * as part of the application's rendering lifecycle.
     */
    public abstract void render();

    /**
     * Updates the state of the scene. This method is responsible for performing
     * the scene's time-dependent logic, such as animations, game logic, or any
     * other updates related to the current state of the scene.
     *
     * @param delta the time in seconds since the last update call. This value
     *              should be used to perform time-based calculations to ensure
     *              consistency across systems with varying frame rates.
     */
    public abstract void update(float delta);

    /**
     * Releases all resources held by the scene and performs any necessary cleanup.
     * This method is typically called when the scene is no longer needed or when
     * transitioning to a different scene. It ensures that any resources, such as
     * textures, sound files, or memory allocations, are properly disposed of to
     * prevent resource leaks.
     * <p>
     * Subclasses must implement this method to define the specific disposal logic
     * required for their resources or dependencies. This method is primarily
     * invoked by a controller, such as the {@code GameScreen}, during the disposal
     * of the current active scene.
     */
    public abstract void dispose();

    /**
     * Checks whether the scene is currently paused.
     *
     * @return true if the scene is paused, false otherwise
     */
    public boolean isPaused() {
        return paused;
    }

    /**
     * Sets the paused state of the scene. When the scene is paused,
     * its update logic is temporarily disabled, allowing the rendering to
     * continue while preventing time-dependent operations.
     *
     * @param paused true to pause the scene, false to resume it
     */
    public void setPaused(boolean paused) {
        this.paused = paused;
    }
}
