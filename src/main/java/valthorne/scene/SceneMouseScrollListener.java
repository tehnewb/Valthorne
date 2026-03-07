package valthorne.scene;

import valthorne.event.events.MouseScrollEvent;
import valthorne.event.listeners.MouseScrollListener;

/**
 * <h1>SceneMouseScrollListener</h1>
 *
 * <p>
 * {@code SceneMouseScrollListener} is a simple forwarding wrapper that routes mouse wheel events
 * from the engine input system into a specific {@link Scene}. It exists so scenes can react to
 * scroll input through scene-level methods rather than implementing the engine listener contract
 * directly.
 * </p>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * SceneMouseScrollListener listener = new SceneMouseScrollListener(scene);
 * Mouse.addScrollListener(listener);
 * }</pre>
 *
 * @author Albert Beaupre
 * @since March 7th, 2026
 */
public class SceneMouseScrollListener implements MouseScrollListener {

    private Scene scene; // The scene that receives forwarded scroll events.

    /**
     * Creates a new scroll listener that forwards events to the supplied scene.
     *
     * @param scene the target scene that should receive mouse scroll input
     */
    public SceneMouseScrollListener(Scene scene) {
        this.scene = scene;
    }

    /**
     * Forwards a mouse scroll event to the current scene.
     *
     * @param event the mouse scroll event to forward
     */
    @Override
    public void mouseScrolled(MouseScrollEvent event) {
        if (scene != null) {
            scene.mouseScrolled(event);
        }
    }

    /**
     * Returns the scene currently receiving forwarded scroll events.
     *
     * @return the current target scene
     */
    public Scene getScene() {
        return scene;
    }

    /**
     * Changes which scene receives forwarded scroll events.
     *
     * @param scene the new target scene
     */
    public void setScene(Scene scene) {
        this.scene = scene;
    }
}