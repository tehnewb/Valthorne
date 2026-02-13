package valthorne.scene;

import valthorne.Application;

/**
 * The GameScreen class implements the Application interface and acts as a controller
 * for managing the current Scene in the application's lifecycle. It facilitates
 * initializing, rendering, updating, and disposing of the active Scene.
 * <p>
 * This class provides a mechanism to switch between different scenes during the
 * application's runtime by setting a new Scene using the setScene method.
 * The previously active scene is properly disposed before transitioning to
 * the new scene.
 * <p>
 * The behavior of the GameScreen is highly dependent on the current Scene.
 * If no Scene is set, the lifecycle methods will do nothing.
 *
 * @author Albert Beaupre
 * @since February 13th, 2026
 */
public class GameScreen implements Application {

    private Scene currentScene;

    @Override
    public void init() {
        if (currentScene == null)
            return;

        this.currentScene.init();
    }

    @Override
    public void render() {
        if (currentScene == null)
            return;

        this.currentScene.render();
    }

    @Override
    public void update(float delta) {
        if (currentScene == null)
            return;

        if (currentScene.isPaused())
            return;

        this.currentScene.update(delta);
    }

    @Override
    public void dispose() {
        if (currentScene == null)
            return;

        this.currentScene.dispose();
    }

    /**
     * Sets the current active scene for the application. If a scene is already active,
     * it will be disposed of before transitioning to the new scene.
     * If the provided scene is null, the current scene will be cleared without initialization.
     *
     * @param scene the new scene to set as the active scene. If null, the current scene
     *              will be disposed of and no new scene will be initialized.
     */
    public void setScene(Scene scene) {
        if (currentScene != null)
            currentScene.dispose();

        currentScene = scene;
        if (scene == null)
            return;
        scene.init();
    }

}
