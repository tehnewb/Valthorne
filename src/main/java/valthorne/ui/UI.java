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

import java.util.function.BiConsumer;

/**
 * Represents the main user interface (UI) container for managing and rendering hierarchical
 * UI elements. Provides support for focusing elements, handling mouse and keyboard events,
 * and rendering with optional viewport integration. This class serves as the root container
 * for all UI elements and ensures proper event propagation and element state management.
 * <p>
 * The {@code UI} class integrates with the application's input and window systems to
 * interact with user input and respond to window resizing events. It provides methods
 * to control focus, handle input events, and manage the viewport for rendering.
 * <p>
 * Inherits from {@code ElementContainer}, enabling it to hold child elements and manage their layout.
 */
public class UI extends ElementContainer {

    private Element focused; // The currently focused UI element
    private Element pressed; // The UI element that is currently being pressed
    private Element hovered; // The UI element that the mouse is currently hovering over
    private Viewport viewport;

    /**
     * Constructs a new UI instance and initializes input listeners for handling user interactions.
     * It sets up listeners for keyboard input, mouse clicks, mouse scroll actions, and window resizing events.
     */
    public UI() {
        Keyboard.addKeyListener(new UIKeyListener());
        Mouse.addMouseListener(new UIMouseListener());
        Mouse.addScrollListener(new UIScrollListener());
        Window.addWindowResizeListener(new UIWindowListener());
    }

    @Override
    public Element findElementAt(float x, float y, boolean click) {
        if (viewport == null) return super.findElementAt(x - this.getX(), y - this.getY(), click);

        Vector2f world = viewport.screenToWorld(x, y);
        if (world == null) return null;

        return super.findElementAt(world.getX(), world.getY(), click);
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
        element.layout();
    }

    @Override
    protected void onRemove(Element element) {
        element.layout();
    }

    /**
     * Assigns focus to the specified element if it is focusable, visible, and not disabled.
     * If the given element is null or does not meet these criteria, no element is focused.
     * Removes focus from the previously focused element, if any.
     *
     * @param next the element to set focus to; may be null
     */
    public void setFocusTo(Element next) {
        if (focused == next) return;

        if (focused != null) focused.setFocused(false);

        focused = (next != null && next.isFocusable() && !next.isHidden() && !next.isDisabled()) ? next : null;

        if (focused != null) focused.setFocused(true);
    }

    @Override
    public Element getParent() {
        return this;
    }

    @Override
    public UI getUI() {
        return this;
    }

    /**
     * Focuses the next focusable element in UI tree order (depth-first).
     * If nothing is focused, focuses the first focusable element.
     */
    public void focusNext() {
        Element next = findNextFocusable(focused);
        setFocusTo(next);
    }

    /**
     * Finds the next focusable element after {@code from} in depth-first order.
     * Wraps around to the first focusable element if needed.
     */
    private Element findNextFocusable(Element from) {
        if (from == null) return findFirstFocusable(this);

        Element[] found = new Element[]{null};
        boolean[] seenFrom = new boolean[]{false};

        new BiConsumer<Element, Void>() {
            @Override
            public void accept(Element element, Void ignored) {
                if (element == null || found[0] != null) return;

                // Once we've seen "from", the next focusable becomes our answer.
                if (seenFrom[0]) {
                    if (isElementFocusableNow(element)) {
                        found[0] = element;
                        return;
                    }
                }

                if (element == from) {
                    seenFrom[0] = true;
                }

                if (element instanceof ElementContainer container) {
                    for (int i = 0; i < container.size; i++) {
                        Element child = container.elements[i];
                        if (child != null) this.accept(child, null);
                        if (found[0] != null) return;
                    }
                }
            }
        }.accept(this, null);

        if (found[0] == null) return findFirstFocusable(this);

        return found[0];
    }

    /**
     * Finds and returns the first focusable element within the specified root element.
     * The method traverses the UI tree in depth-first order and identifies the first element
     * that can currently accept focus.
     *
     * @param root the root element to start searching for a focusable element; must not be null
     * @return the first focusable element found within the root element, or null if no focusable element exists
     */
    private Element findFirstFocusable(Element root) {
        Element[] found = new Element[]{null};

        new BiConsumer<Element, Void>() {
            @Override
            public void accept(Element element, Void ignored) {
                if (element == null || found[0] != null) return;

                if (isElementFocusableNow(element)) {
                    found[0] = element;
                    return;
                }

                if (element instanceof ElementContainer container) {
                    for (int i = 0; i < container.size; i++) {
                        Element child = container.elements[i];
                        if (child != null) this.accept(child, null);
                        if (found[0] != null) return;
                    }
                }
            }
        }.accept(root, null);

        return found[0];
    }

    /**
     * Checks if the provided element is currently focusable.
     * An element is considered focusable if it is not null, is focusable, is not hidden,
     * and is not disabled.
     *
     * @param e the element to check for focusability; may be null
     * @return true if the element is currently focusable, false otherwise
     */
    private boolean isElementFocusableNow(Element e) {
        return e != null && e.isFocusable() && !e.isHidden() && !e.isDisabled();
    }

    /**
     * Retrieves the current viewport associated with this UI instance.
     * The viewport defines the rendering area and is used to manage
     * the visible portion of the UI.
     *
     * @return the current {@link Viewport} of this UI instance
     */
    public Viewport getViewport() {
        return viewport;
    }

    /**
     * Sets the viewport for this UI instance. The viewport defines the rendering area
     * and is used to manage the visible portion of the UI.
     *
     * @param viewport the {@link Viewport} to be assigned to this UI instance; must not be null
     */
    public void setViewport(Viewport viewport) {
        this.viewport = viewport;
    }

    /**
     * Retrieves the element that is currently focused within the UI.
     * If no element is focused, this method returns null.
     *
     * @return the currently focused element, or null if no element is focused
     */
    public Element getFocused() {
        return focused;
    }

    /**
     * The {@code UIKeyListener} class is an internal implementation of the {@code KeyListener}
     * interface for handling keyboard events within the {@code UI} class. It manages user interactions
     * with the keyboard by responding to key press and key release events.
     * <p>
     * This class primarily handles focus traversal using the {@code Tab} key and delegates
     * specific key-related behavior to the currently focused UI element, if one exists.
     */
    private class UIKeyListener implements KeyListener {

        @Override
        public void keyPressed(KeyPressEvent event) {
            if (event.getKey() == Keyboard.TAB) {
                focusNext();
                return;
            }

            if (focused != null) focused.onKeyPress(event);
        }

        @Override
        public void keyReleased(KeyReleaseEvent event) {
            if (focused != null) focused.onKeyRelease(event);
        }
    }

    /**
     * The {@code UIMouseListener} class implements the {@code MouseListener} interface and is
     * responsible for handling mouse-related interactions within the {@code UI} context. It
     * processes mouse events such as press, release, drag, and move, and performs actions on
     * the corresponding {@code Element} instances within the UI.
     * <p>
     * This class facilitates user interaction by determining the appropriate {@code Element}
     * based on event coordinates and invoking relevant behaviors like focusing, pressing,
     * hovering, and handling custom event logic.
     * <p>
     * Mouse Events:
     * <p>
     * 1. {@code mousePressed(MousePressEvent event)}: Handles mouse press events by locating the
     * target element and initiating focus and pressed state logic.
     * <p>
     * 2. {@code mouseReleased(MouseReleaseEvent event)}: Handles mouse release events and resets
     * the pressed state.
     * <p>
     * 3. {@code mouseDragged(MouseDragEvent event)}: Handles mouse drag events and forwards the
     * event to the pressed element.
     * <p>
     * 4. {@code mouseMoved(MouseMoveEvent event)}: Handles mouse move events by updating the
     * hovered state and invoking hover-related behavior on the target element.
     */
    private class UIMouseListener implements MouseListener {

        @Override
        public void mousePressed(MousePressEvent event) {
            Element target = findElementAt(event.getX(), event.getY(), true);

            if (target != null && !target.isClickThrough()) {
                if (target.isFocusable()) {
                    setFocusTo(target);
                } else {
                    setFocusTo(null);
                }

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
            Element target = findElementAt(event.getX(), event.getY(), true);

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
     * The {@code UIScrollListener} class is a private implementation of the {@code MouseScrollListener} interface
     * designed for handling mouse scroll events within the UI system. It listens for scroll events and delegates
     * the handling to the appropriate target UI element.
     * <p>
     * This implementation is responsible for locating the UI element at the position of the mouse cursor when a
     * scroll action occurs and invoking the element's {@code onMouseScroll} method to handle the event.
     * If no element is found at the mouse's location, the event is ignored.
     * <p>
     * It provides an essential mechanism for enabling mouse scroll interactions across the UI, ensuring that
     * specific elements can respond to user inputs like scrolling with a mouse wheel.
     */
    private class UIScrollListener implements MouseScrollListener {

        @Override
        public void mouseScrolled(MouseScrollEvent event) {
            Element target = findElementAt(Mouse.getX(), Mouse.getY(), false);
            if (target == null) return;

            target.onMouseScroll(event);
        }
    }

    /**
     * The UIWindowListener class is a private inner class of the UI class that handles
     * window resize events. It implements the WindowResizeListener interface to
     * process WindowResizeEvent instances when the window size changes.
     * <p>
     * This class propagates the resize events to the UI and its child elements
     * for re-layout and processing. It ensures the proper cascading of the resize
     * logic across all nested Element and ElementContainer instances within the UI tree.
     * <p>
     * Upon receiving a WindowResizeEvent, the listener updates the UI and recursively
     * invokes resize behavior for each element within any nested containers.
     */
    private class UIWindowListener implements WindowResizeListener {

        @Override
        public void windowResized(WindowResizeEvent event) {
            new BiConsumer<Element, WindowResizeEvent>() {
                @Override
                public void accept(Element element, WindowResizeEvent e) {
                    if (element == null) return;

                    element.onWindowResize(e);
                    element.layout();

                    if (element instanceof ElementContainer container) {
                        for (int i = 0; i < container.size; i++) {
                            Element child = container.elements[i];
                            if (child != null) this.accept(child, e);
                        }
                    }
                }
            }.accept(UI.this, event);
        }
    }
}
