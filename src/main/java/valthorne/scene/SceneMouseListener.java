package valthorne.scene;

import valthorne.event.events.MouseDragEvent;
import valthorne.event.events.MouseMoveEvent;
import valthorne.event.events.MousePressEvent;
import valthorne.event.events.MouseReleaseEvent;
import valthorne.event.listeners.MouseListener;

/**
 * <h1>SceneMouseListener</h1>
 *
 * <p>
 * {@code SceneMouseListener} is a forwarding adapter that routes mouse button, drag, and move events
 * from the engine input system into a specific {@link Scene}. This lets scenes expose clean scene-level
 * mouse hooks without implementing engine listener interfaces directly.
 * </p>
 *
 * <h2>Forwarded events</h2>
 * <ul>
 *     <li>{@link #mousePressed(MousePressEvent)}</li>
 *     <li>{@link #mouseReleased(MouseReleaseEvent)}</li>
 *     <li>{@link #mouseDragged(MouseDragEvent)}</li>
 *     <li>{@link #mouseMoved(MouseMoveEvent)}</li>
 * </ul>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * SceneMouseListener listener = new SceneMouseListener(scene);
 * Mouse.addMouseListener(listener);
 * }</pre>
 *
 * @author Albert Beaupre
 * @since March 7th, 2026
 */
public class SceneMouseListener implements MouseListener {

    private Scene scene; // The scene that receives forwarded mouse events.

    /**
     * Creates a new mouse listener that forwards events to the supplied scene.
     *
     * @param scene the target scene that should receive mouse input
     */
    public SceneMouseListener(Scene scene) {
        this.scene = scene;
    }

    /**
     * Forwards a mouse press event to the current scene.
     *
     * @param event the mouse press event to forward
     */
    @Override
    public void mousePressed(MousePressEvent event) {
        if (scene != null) {
            scene.mousePressed(event);
        }
    }

    /**
     * Forwards a mouse release event to the current scene.
     *
     * @param event the mouse release event to forward
     */
    @Override
    public void mouseReleased(MouseReleaseEvent event) {
        if (scene != null) {
            scene.mouseReleased(event);
        }
    }

    /**
     * Forwards a mouse drag event to the current scene.
     *
     * @param event the mouse drag event to forward
     */
    @Override
    public void mouseDragged(MouseDragEvent event) {
        if (scene != null) {
            scene.mouseDragged(event);
        }
    }

    /**
     * Forwards a mouse move event to the current scene.
     *
     * @param event the mouse move event to forward
     */
    @Override
    public void mouseMoved(MouseMoveEvent event) {
        if (scene != null) {
            scene.mouseMoved(event);
        }
    }

    /**
     * Returns the scene currently receiving forwarded mouse events.
     *
     * @return the current target scene
     */
    public Scene getScene() {
        return scene;
    }

    /**
     * Changes which scene receives forwarded mouse events.
     *
     * @param scene the new target scene
     */
    public void setScene(Scene scene) {
        this.scene = scene;
    }
}