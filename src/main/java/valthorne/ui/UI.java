package valthorne.ui;

import valthorne.Keyboard;
import valthorne.Mouse;
import valthorne.Window;
import valthorne.event.events.*;
import valthorne.event.listeners.KeyListener;
import valthorne.event.listeners.MouseListener;
import valthorne.event.listeners.MouseScrollListener;
import valthorne.event.listeners.WindowResizeListener;
import valthorne.math.Vector2f;
import valthorne.viewport.Viewport;

/**
 * The UI class represents the root container for a user interface, managing its child elements,
 * handling user input events (keyboard, mouse, scroll), and maintaining focus, hover, and press states.
 * This class extends the ElementContainer to provide support for hierarchical UI elements.
 * <p>
 * The UI serves as the central component of the system, handling user interaction and delegating
 * input events to the appropriate child elements based on their states and positions.
 *
 * @author Albert Beaupre
 * @since December 22nd, 2025
 */
public class UI extends ElementContainer {

    private Element focused; // The currently focused UI element
    private Element pressed; // The UI element that is currently being pressed
    private Element hovered; // The UI element that the mouse is currently hovering over

    private Viewport viewport;

    /**
     * Constructor for the UI class.
     * Initializes the user interface by adding event listeners for keyboard actions,
     * mouse clicks, and mouse scroll events.
     * It associates the following listeners:
     * - Key events handled by UIKeyListener.
     * - Mouse click events handled by UIMouseListener.
     * - Mouse scroll events handled by UIScrollListener.
     */
    public UI() {
        Keyboard.addKeyListener(new UIKeyListener());
        Mouse.addMouseListener(new UIMouseListener());
        Mouse.addScrollListener(new UIScrollListener());
        Window.addWindowResizeListener(new UIWindowListener());
    }

    @Override
    public Element findElementAt(float x, float y) {
        if (viewport == null) return super.findElementAt(x - this.getX(), y - this.getY());

        Vector2f world = viewport.screenToWorld(x, y);
        if (world == null) return null;

        return super.findElementAt(world.getX(), world.getY());
    }

    @Override
    public void draw() {
        if (viewport != null) {
            viewport.render(super::draw);
        } else {
            super.draw();
        }
    }

    @Override
    protected void onAdd(Element element) {

    }

    @Override
    protected void onRemove(Element element) {

    }

    /**
     * Sets the viewport for the user interface. The viewport defines the visible
     * area of the user interface and impacts the rendering and interaction of elements
     * within the UI. Assigning a new viewport replaces the currently active one.
     *
     * @param viewport the {@code Viewport} instance to set as the current viewport.
     *                 It cannot be null to avoid rendering issues.
     */
    public void setViewport(Viewport viewport) {
        this.viewport = viewport;
        this.x = viewport.getX();
        this.y = viewport.getY();
        this.width = viewport.getWidth();
        this.height = viewport.getHeight();
    }

    public Viewport getViewport() {
        return viewport;
    }

    /**
     * Updates the currently focused element in the user interface.
     * Removes focus from the previously focused element, if any,
     * and assigns focus to the provided element if it is focusable.
     * If the provided element is null or not focusable, no element will be focused.
     *
     * @param next the element to set as the currently focused element.
     *             Can be null to clear the focus. Must be focusable for the focus to be applied.
     */
    public void setFocusTo(Element next) {
        if (focused == next) return;

        if (focused != null) focused.setFocused(false);

        focused = (next != null && next.isFocusable()) ? next : null;

        if (focused != null) focused.setFocused(true);
    }

    /**
     * Retrieves the currently focused element in the user interface.
     *
     * @return the currently focused {@link Element}, or null if no element is focused.
     */
    public Element getFocused() {
        return focused;
    }

    /**
     * Retrieves the parent element of this element.
     *
     * @return the parent element, or null if this element has no parent.
     */
    public Element getParent() {
        return this;
    }

    @Override
    public UI getUI() {
        return this;
    }

    /**
     * The UIKeyListener class is a private implementation of the KeyListener interface
     * within the context of the UI class. It provides specific handling behavior for
     * key events (key press and key release) associated with the currently focused
     * element in the user interface.
     * <p>
     * This listener ensures that only the focused element receives key event notifications.
     * When a key press or release event is triggered, the event is forwarded to the
     * corresponding methods of the focused element if one exists.
     * <p>
     * Methods:
     * - keyPressed(KeyPressEvent event): Handles and forwards the key press event to the
     * focused element's onKeyPress method.
     * - keyReleased(KeyReleaseEvent event): Handles and forwards the key release event
     * to the focused element's onKeyRelease method.
     */
    private class UIKeyListener implements KeyListener {

        @Override
        public void keyPressed(KeyPressEvent event) {
            if (focused != null) focused.onKeyPress(event);
        }

        @Override
        public void keyReleased(KeyReleaseEvent event) {
            if (focused != null) focused.onKeyRelease(event);
        }
    }

    /**
     * The UIMouseListener class is a private inner class responsible for handling mouse events
     * and providing interactivity to elements in the user interface. It implements the MouseListener
     * interface to process mouse press, release, drag, and move events.
     * <p>
     * This class offers the following main functionalities:
     * - Maintains and manages the state of the pressed and hovered elements when interacting
     * with the user interface.
     * - Updates the focus state of UI elements based on mouse interactions.
     * - Triggers element-specific behavior during mouse events such as mouse presses, releases,
     * drags, and moves by invoking corresponding handlers on the affected elements.
     * <p>
     * Behavior of the implemented methods:
     * - mousePressed: Handles behavior when a mouse button is pressed. It sets focus to the
     * element at the mouse position, updates its pressed state, and calls its onMousePress method.
     * - mouseReleased: Handles behavior when a mouse button is released. It resets the pressed
     * element's pressed state and invokes its onMouseRelease method.
     * - mouseDragged: Handles behavior when the mouse is dragged. Passes the drag event to the pressed
     * element's onMouseDrag handler if the element is pressed.
     * - mouseMoved: Handles behavior when the mouse moves. Updates the hovered state of UI elements,
     * invoking the onMouseMove method for both the previously hovered and newly hovered elements.
     * <p>
     * This class interacts with the UI class to update focus and with the Element class to
     * manage press, hover, and state changes, ensuring consistent behavior and interactivity
     * of UI elements during mouse interactions.
     */
    private class UIMouseListener implements MouseListener {

        @Override
        public void mousePressed(MousePressEvent event) {
            Element target = findElementAt(event.getX(), event.getY());

            if (target != null) {
                if (target.isFocusable()) setFocusTo(target);

                pressed = target;
                pressed.setPressed(true);
                pressed.onMousePress(event);
            } else {
                setFocusTo(null);
            }
        }

        @Override
        public void mouseReleased(MouseReleaseEvent event) {
            if (pressed != null) {
                pressed.setPressed(false);
                pressed.onMouseRelease(event);
                pressed = null;
            }
        }

        @Override
        public void mouseDragged(MouseDragEvent event) {
            if (pressed != null) pressed.onMouseDrag(event);
        }

        @Override
        public void mouseMoved(MouseMoveEvent event) {
            Element target = findElementAt(event.getX(), event.getY());

            if (target != hovered && hovered != null) {
                hovered.setHovered(false);
            }

            if (target == null) return;

            hovered = target;
            target.setHovered(true);
            target.onMouseMove(event);
        }
    }

    /**
     * The UIScrollListener class is a private implementation of the MouseScrollListener interface
     * that listens for mouse scroll events and delegates handling them to the appropriate UI element.
     * It is responsible for identifying the element located at the mouse pointer's current position
     * and invoking the onMouseScroll method for that element.
     * <p>
     * This listener is typically used within the UI class to handle user interactions involving
     * mouse scroll actions, such as scrolling through elements or triggering scroll-related behaviors.
     */
    private class UIScrollListener implements MouseScrollListener {

        @Override
        public void mouseScrolled(MouseScrollEvent event) {
            Element target = findElementAt(Mouse.getX(), Mouse.getY());
            if (target == null) return;

            target.onMouseScroll(event);
        }
    }

    /**
     * The UIWindowListener class implements the {@link WindowResizeListener} interface
     * and is responsible for handling window resize events and propagating the resize
     * information throughout the user interface hierarchy starting from the root {@code UI} element.
     * <p>
     * It listens for the {@code WindowResizeEvent} and recursively notifies all relevant
     * child elements about the resize via the {@code propagateResize} method.
     * <p>
     * This class helps ensure that the UI updates consistently in response to changes
     * in the window's size, allowing each element and its descendants to handle the
     * event appropriately.
     */
    private class UIWindowListener implements WindowResizeListener {

        @Override
        public void windowResized(WindowResizeEvent event) {
            propagateResize(UI.this, event);
        }

        private void propagateResize(Element element, WindowResizeEvent event) {
            if (element == null) return;

            element.onWindowResize(event);

            if (element instanceof ElementContainer container) {
                for (int i = 0; i < container.size; i++) {
                    Element child = container.elements[i];
                    if (child != null) {
                        propagateResize(child, event);
                    }
                }
            }
        }
    }
}
