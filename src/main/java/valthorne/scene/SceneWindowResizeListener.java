package valthorne.scene;

import valthorne.event.events.WindowResizeEvent;
import valthorne.event.listeners.WindowResizeListener;

/**
 * <h1>SceneWindowResizeListener</h1>
 *
 * <p>
 * {@code SceneWindowResizeListener} is a forwarding adapter that routes window resize events into
 * a specific {@link Scene}. This allows a scene to react to window size changes through its own
 * {@link Scene#windowResized(WindowResizeEvent)} method while keeping the listener registration logic
 * separate from scene implementations.
 * </p>
 *
 * <h2>Typical use</h2>
 * <p>
 * Scenes commonly use resize events to update their {@link valthorne.viewport.Viewport}, reposition
 * cameras, rebuild layout, or refresh screen-space UI.
 * </p>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * SceneWindowResizeListener listener = new SceneWindowResizeListener(scene);
 * Window.addWindowResizeListener(listener);
 * }</pre>
 *
 * @author Albert Beaupre
 * @since March 7th, 2026
 */
public class SceneWindowResizeListener implements WindowResizeListener {

    private Scene scene; // The scene that receives forwarded resize events.

    /**
     * Creates a new resize listener that forwards events to the supplied scene.
     *
     * @param scene the target scene that should receive resize notifications
     */
    public SceneWindowResizeListener(Scene scene) {
        this.scene = scene;
    }

    /**
     * Forwards a window resize event to the current scene.
     *
     * @param event the resize event to forward
     */
    @Override
    public void windowResized(WindowResizeEvent event) {
        if (scene != null) {
            scene.windowResized(event);
        }
    }

    /**
     * Returns the scene currently receiving forwarded resize events.
     *
     * @return the current target scene
     */
    public Scene getScene() {
        return scene;
    }

    /**
     * Changes which scene receives forwarded resize events.
     *
     * @param scene the new target scene
     */
    public void setScene(Scene scene) {
        this.scene = scene;
    }
}