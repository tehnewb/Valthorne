package valthorne.scene;

import valthorne.event.events.KeyPressEvent;
import valthorne.event.events.KeyReleaseEvent;
import valthorne.event.listeners.KeyListener;

/**
 * <h1>SceneKeyListener</h1>
 *
 * <p>
 * {@code SceneKeyListener} is a small forwarding adapter that connects the engine's global key input
 * system to a specific {@link Scene}. Instead of forcing scenes to implement the listener interface
 * directly, this wrapper receives keyboard events and delegates them to the scene's high-level
 * scene methods.
 * </p>
 *
 * <p>
 * This keeps the scene API clean while still allowing scene input registration through the engine's
 * listener system.
 * </p>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * Scene scene = new GameplayScene();
 * SceneKeyListener listener = new SceneKeyListener(scene);
 *
 * Keyboard.addKeyListener(listener);
 * }</pre>
 *
 * @author Albert Beaupre
 * @since March 7th, 2026
 */
public class SceneKeyListener implements KeyListener {

    private Scene scene; // The scene that receives forwarded key events.

    /**
     * Creates a new key listener that forwards events to the supplied scene.
     *
     * @param scene the scene that should receive key events
     */
    public SceneKeyListener(Scene scene) {
        this.scene = scene;
    }

    /**
     * Forwards a key press event to the current scene.
     *
     * @param event the key press event to forward
     */
    @Override
    public void keyPressed(KeyPressEvent event) {
        if (scene != null) {
            scene.keyPressed(event);
        }
    }

    /**
     * Forwards a key release event to the current scene.
     *
     * @param event the key release event to forward
     */
    @Override
    public void keyReleased(KeyReleaseEvent event) {
        if (scene != null) {
            scene.keyReleased(event);
        }
    }

    /**
     * Returns the scene currently receiving forwarded key events.
     *
     * @return the current target scene
     */
    public Scene getScene() {
        return scene;
    }

    /**
     * Changes which scene receives forwarded key events.
     *
     * @param scene the new target scene
     */
    public void setScene(Scene scene) {
        this.scene = scene;
    }
}