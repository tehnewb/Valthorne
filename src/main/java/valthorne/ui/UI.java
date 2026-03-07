package valthorne.ui;

import valthorne.Keyboard;
import valthorne.Mouse;
import valthorne.Window;
import valthorne.event.events.*;
import valthorne.event.listeners.KeyListener;
import valthorne.event.listeners.MouseListener;
import valthorne.event.listeners.MouseScrollListener;
import valthorne.event.listeners.WindowResizeListener;
import valthorne.graphics.texture.TextureBatch;
import valthorne.math.Vector2f;
import valthorne.viewport.Viewport;

/**
 * Root user-interface container responsible for input routing, focus management, hover/press state,
 * viewport-aware hit testing, and recursive drawing of the entire UI tree.
 *
 * <p>
 * This class acts as the top-level owner of all UI elements added beneath it. It extends
 * {@link ElementContainer}, so it behaves like a normal container element, but it also adds the
 * extra responsibilities that only a root UI object should have:
 * </p>
 *
 * <ul>
 *     <li>Registers and removes global keyboard, mouse, scroll, and window resize listeners.</li>
 *     <li>Tracks which element is focused, hovered, or currently pressed.</li>
 *     <li>Converts screen-space mouse coordinates into world/UI coordinates when a {@link Viewport} is used.</li>
 *     <li>Routes input events only to the correct target element.</li>
 *     <li>Implements focus traversal using the Tab key.</li>
 *     <li>Recursively propagates window resize events and layout refreshes.</li>
 * </ul>
 *
 * <h2>Viewport behavior</h2>
 * <p>
 * When no viewport is assigned, hit detection uses the coordinates directly relative to the UI root.
 * When a viewport is assigned, hit testing converts incoming screen coordinates through
 * {@link Viewport#screenToWorld(float, float)} so the UI can be rendered and interacted with inside
 * a transformed camera or viewport space.
 * </p>
 *
 * <h2>Focus behavior</h2>
 * <p>
 * The UI keeps exactly one focused element at a time, or no focused element at all. Calling
 * {@link #setFocusTo(Element)} automatically clears focus from the previously focused element,
 * validates whether the new element is actually focusable, and then applies focus to it.
 * Pressing Tab moves to the next currently valid focus target in a depth-first traversal order.
 * </p>
 *
 * <h2>Press and hover behavior</h2>
 * <p>
 * Mouse press handling determines the clickable target under the cursor, assigns focus when
 * appropriate, marks that element as pressed, and forwards the press event. Hover handling updates
 * hover state as the cursor moves between clickable elements. Release handling clears the pressed
 * state and forwards the release event back to the element that originally received the press.
 * </p>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * UI ui = new UI();
 *
 * ui.setViewport(gameViewport);
 *
 * Panel rootPanel = new Panel();
 * rootPanel.setPosition(20f, 20f);
 * rootPanel.setSize(500f, 300f);
 *
 * TextField username = new TextField("Username", style, field -> {
 *     System.out.println(field.getText());
 * });
 * username.setPosition(40f, 40f);
 * username.setSize(220f, 42f);
 *
 * Button login = new Button("Login", buttonStyle, b -> {
 *     System.out.println("Clicked login");
 * });
 * login.setPosition(40f, 100f);
 * login.setSize(140f, 40f);
 *
 * rootPanel.add(username);
 * rootPanel.add(login);
 * ui.add(rootPanel);
 *
 * batch.begin();
 * ui.draw(batch);
 * batch.end();
 *
 * // Later during shutdown:
 * ui.dispose();
 * }</pre>
 *
 * @author Albert Beaupre
 * @since December 22nd, 2025
 */
public class UI extends ElementContainer {

    private Element focused; // The element that currently owns keyboard focus.
    private Element pressed; // The element that received the active mouse press.
    private Element hovered; // The element currently under the mouse cursor.
    private Viewport viewport; // Optional viewport used for drawing and screen-to-world hit testing.

    private Element found; // Reused traversal result slot for focus searches.
    private boolean seenFrom; // Reused traversal flag marking when the current focus node was encountered.

    private final UIKeyListener keyListener = new UIKeyListener(); // Keyboard listener registered against the global keyboard input system.
    private final UIMouseListener mouseListener = new UIMouseListener(); // Mouse listener registered against the global mouse input system.
    private final UIScrollListener scrollListener = new UIScrollListener(); // Scroll listener registered against the global mouse scroll input system.
    private final UIWindowListener windowListener = new UIWindowListener(); // Window resize listener registered against the global window system.

    /**
     * Creates the root UI object, registers all required global listeners, and initializes the
     * root layout to fill the full parent space.
     *
     * <p>
     * The root UI uses percentage width and height of {@code 1f}, which means it is configured
     * to cover the full available area by default. This makes it suitable as the single top-level
     * container for a full interface tree.
     * </p>
     */
    public UI() {
        Keyboard.addKeyListener(keyListener);
        Mouse.addMouseListener(mouseListener);
        Mouse.addScrollListener(scrollListener);
        Window.addWindowResizeListener(windowListener);

        setLayout(Layout.of().height(Value.percentage(1f)).width(Value.percentage(1f)).x(Value.pixels(0)).y(Value.pixels(0)));
    }

    /**
     * Finds the topmost matching element at the supplied coordinates.
     *
     * <p>
     * If no viewport is assigned, the incoming coordinates are treated as already being in the
     * UI's working coordinate space and are adjusted relative to the UI root position before
     * delegating to the inherited container hit-testing logic.
     * </p>
     *
     * <p>
     * If a viewport is assigned, the method first converts the screen coordinates into world/UI
     * coordinates through the viewport. If that conversion fails and returns {@code null},
     * this method returns {@code null} as well.
     * </p>
     *
     * @param x     input x coordinate, typically in screen space when a viewport is active
     * @param y     input y coordinate, typically in screen space when a viewport is active
     * @param flags hit-test flags used to filter the kinds of elements that should match
     * @return the matching element at the coordinate, or {@code null} if nothing matches
     */
    @Override
    public Element findElementAt(float x, float y, byte flags) {
        if (viewport == null) {
            return super.findElementAt(x - this.getX(), y - this.getY(), flags);
        }

        Vector2f world = viewport.screenToWorld(x, y);
        if (world == null) return null;

        return super.findElementAt(world.getX(), world.getY(), flags);
    }

    /**
     * Draws the entire UI tree using the supplied batch.
     *
     * <p>
     * When a viewport is assigned, the viewport is bound before drawing begins and unbound
     * immediately after the UI tree finishes drawing. This allows the UI to render correctly
     * inside custom viewport or camera transforms without requiring callers to manage viewport
     * binding themselves.
     * </p>
     *
     * <p>
     * When no viewport is assigned, the method simply delegates directly to the inherited
     * container draw logic.
     * </p>
     *
     * @param batch batch used to draw all UI elements
     */
    @Override
    public void draw(TextureBatch batch) {
        if (viewport != null) {
            viewport.bind();
            super.draw(batch);
            viewport.unbind();
        } else {
            super.draw(batch);
        }
    }

    /**
     * Handles logic that should occur when a child element is added to this UI tree.
     *
     * <p>
     * Newly added elements are immediately laid out so their computed size and position
     * are valid before the next draw or input pass.
     * </p>
     *
     * @param element newly added element
     */
    @Override
    protected void onAdd(Element element) {
        element.layout();
    }

    /**
     * Handles logic that should occur when a child element is removed from this UI tree.
     *
     * <p>
     * The removed element is laid out once more so any dependent state can refresh consistently
     * with the surrounding layout system.
     * </p>
     *
     * @param element removed element
     */
    @Override
    protected void onRemove(Element element) {
        element.layout();
    }

    /**
     * Changes the currently focused element.
     *
     * <p>
     * Focus is only assigned to elements that are currently valid focus targets. An element must
     * be non-null, focusable, visible, and enabled. If the requested target does not satisfy those
     * requirements, focus is cleared instead.
     * </p>
     *
     * <p>
     * If a previously focused element exists, its focused state is always cleared before the new
     * focus target is applied.
     * </p>
     *
     * @param next the requested focus target, or {@code null} to clear focus
     */
    public void setFocusTo(Element next) {
        if (focused == next) return;

        if (focused != null) focused.setFocused(false);

        focused = (next != null && next.isFocusable() && !next.isHidden() && !next.isDisabled()) ? next : null;

        if (focused != null) focused.setFocused(true);
    }

    /**
     * Returns this UI as its own parent.
     *
     * <p>
     * The root UI is the top of the UI tree, so parent lookups resolve back to itself.
     * </p>
     *
     * @return this UI instance
     */
    @Override
    public Element getParent() {
        return this;
    }

    /**
     * Returns this UI root.
     *
     * <p>
     * For any element tree rooted under this object, the UI root is this instance.
     * </p>
     *
     * @return this UI instance
     */
    @Override
    public UI getUI() {
        return this;
    }

    /**
     * Removes all global listeners previously registered by this UI.
     *
     * <p>
     * This method should be called when the UI is no longer needed. It prevents stale listeners
     * from continuing to receive input and avoids memory leaks or duplicate event routing when
     * a new UI is later created.
     * </p>
     */
    public void dispose() {
        Keyboard.removeKeyListener(keyListener);
        Mouse.removeMouseListener(mouseListener);
        Mouse.removeScrollListener(scrollListener);
        Window.removeWindowResizeListener(windowListener);
    }

    /**
     * Moves keyboard focus to the next currently valid focusable element.
     *
     * <p>
     * If there is no current focus, the first valid focusable element in traversal order is used.
     * If traversal reaches the end without finding another element, the search wraps and the first
     * valid focusable element is used instead.
     * </p>
     */
    public void focusNext() {
        Element next = findNextFocusable(focused);
        setFocusTo(next);
    }

    /**
     * Finds the next valid focusable element after the supplied element.
     *
     * <p>
     * When {@code from} is {@code null}, this method simply returns the first valid focusable element.
     * Otherwise it performs a depth-first traversal, marks when the source element has been seen, and
     * then returns the first valid focusable element encountered after it.
     * </p>
     *
     * <p>
     * If no later focusable element exists, the search wraps to the first valid focusable element
     * in the tree.
     * </p>
     *
     * @param from current focus source
     * @return next valid focusable element, or {@code null} if none exist
     */
    private Element findNextFocusable(Element from) {
        if (from == null) return findFirstFocusable(this);

        found = null;
        seenFrom = false;

        traverseNextFocusable(this, from);

        return (found != null) ? found : findFirstFocusable(this);
    }

    /**
     * Recursively traverses the UI tree to locate the next valid focusable element after a source node.
     *
     * <p>
     * Once the traversal encounters {@code from}, the next element encountered that satisfies
     * {@link #isElementFocusableNow(Element)} becomes the result.
     * </p>
     *
     * @param element current traversal node
     * @param from    source node after which the next focusable element should be found
     */
    private void traverseNextFocusable(Element element, Element from) {
        if (element == null || found != null) return;

        if (seenFrom && isElementFocusableNow(element)) {
            found = element;
            return;
        }

        if (element == from) {
            seenFrom = true;
        }

        if (element instanceof ElementContainer container) {
            for (int i = 0; i < container.size; i++) {
                Element child = container.elements[i];
                if (child != null) traverseNextFocusable(child, from);
                if (found != null) return;
            }
        }
    }

    /**
     * Finds the first valid focusable element in the UI tree.
     *
     * <p>
     * This method resets the shared traversal state and then performs a depth-first search
     * starting from the supplied root.
     * </p>
     *
     * @param root traversal root
     * @return first valid focusable element, or {@code null} if none exist
     */
    private Element findFirstFocusable(Element root) {
        found = null;
        traverseFirstFocusable(root);
        return found;
    }

    /**
     * Recursively traverses the UI tree to locate the first valid focusable element.
     *
     * @param element current traversal node
     */
    private void traverseFirstFocusable(Element element) {
        if (element == null || found != null) return;

        if (isElementFocusableNow(element)) {
            found = element;
            return;
        }

        if (element instanceof ElementContainer container) {
            for (int i = 0; i < container.size; i++) {
                Element child = container.elements[i];
                if (child != null) traverseFirstFocusable(child);
                if (found != null) return;
            }
        }
    }

    /**
     * Returns whether an element is currently eligible to receive focus.
     *
     * <p>
     * An element is considered focusable now only when it exists, explicitly supports focus,
     * is not hidden, and is not disabled.
     * </p>
     *
     * @param e element to validate
     * @return true when the element can currently receive focus
     */
    private boolean isElementFocusableNow(Element e) {
        return e != null && e.isFocusable() && !e.isHidden() && !e.isDisabled();
    }

    /**
     * Returns the currently assigned viewport.
     *
     * @return current viewport, or {@code null} when no viewport is assigned
     */
    public Viewport getViewport() {
        return viewport;
    }

    /**
     * Assigns the viewport used for UI drawing and hit testing.
     *
     * <p>
     * When a viewport is set, drawing is wrapped in viewport bind/unbind calls and hit testing
     * converts incoming screen coordinates through the viewport before searching the UI tree.
     * </p>
     *
     * @param viewport viewport to use, or {@code null} to disable viewport handling
     */
    public void setViewport(Viewport viewport) {
        this.viewport = viewport;
    }

    /**
     * Returns the element that currently owns keyboard focus.
     *
     * @return currently focused element, or {@code null} when nothing is focused
     */
    public Element getFocused() {
        return focused;
    }

    /**
     * Keyboard listener responsible for focus traversal and focused-element key routing.
     *
     * <p>
     * Tab advances focus through the UI tree. All other key presses and releases are forwarded
     * only to the currently focused element, if one exists.
     * </p>
     */
    private class UIKeyListener implements KeyListener {

        /**
         * Handles a key press event.
         *
         * <p>
         * Pressing Tab moves focus to the next valid focus target. Any other key press is forwarded
         * to the currently focused element.
         * </p>
         *
         * @param event key press event
         */
        @Override
        public void keyPressed(KeyPressEvent event) {
            if (event.getKey() == Keyboard.TAB) {
                focusNext();
                return;
            }
            if (focused != null) focused.onKeyPress(event);
        }

        /**
         * Handles a key release event.
         *
         * <p>
         * Key releases are only forwarded to the currently focused element.
         * </p>
         *
         * @param event key release event
         */
        @Override
        public void keyReleased(KeyReleaseEvent event) {
            if (focused != null) focused.onKeyRelease(event);
        }
    }

    /**
     * Mouse listener responsible for click targeting, focus assignment, drag forwarding,
     * release forwarding, and hover updates.
     */
    private class UIMouseListener implements MouseListener {

        /**
         * Handles mouse press targeting and focus updates.
         *
         * <p>
         * The method finds the clickable element under the mouse. If one is found and it is not
         * click-through, that element may become focused, is marked as pressed, and receives the
         * mouse press event. If no valid target is found, focus is cleared.
         * </p>
         *
         * @param event mouse press event
         */
        @Override
        public void mousePressed(MousePressEvent event) {
            Element target = findElementAt(event.getX(), event.getY(), Element.CLICKABLE);

            if (target != null && !target.isClickThrough()) {
                if (target.isFocusable()) setFocusTo(target);
                else setFocusTo(null);

                pressed = target;
                pressed.setPressed(true);
                pressed.onMousePress(event);
            } else {
                setFocusTo(null);
            }
        }

        /**
         * Handles mouse release forwarding back to the originally pressed element.
         *
         * <p>
         * This preserves standard press-release behavior even if the cursor has moved since the
         * original press.
         * </p>
         *
         * @param event mouse release event
         */
        @Override
        public void mouseReleased(MouseReleaseEvent event) {
            if (pressed != null) {
                pressed.setPressed(false);
                pressed.onMouseRelease(event);
                pressed = null;
            }
        }

        /**
         * Handles mouse drag forwarding.
         *
         * <p>
         * Drag events are routed only to the element that originally received the press.
         * </p>
         *
         * @param event mouse drag event
         */
        @Override
        public void mouseDragged(MouseDragEvent event) {
            if (pressed != null) pressed.onMouseDrag(event);
        }

        /**
         * Handles hover transitions and mouse-move forwarding.
         *
         * <p>
         * If the hovered element changes, the previously hovered element has its hover state cleared.
         * The new clickable target, if any, becomes hovered and receives the mouse move event.
         * </p>
         *
         * @param event mouse move event
         */
        @Override
        public void mouseMoved(MouseMoveEvent event) {
            Element target = findElementAt(event.getX(), event.getY(), Element.CLICKABLE);

            if (target != hovered && hovered != null) {
                hovered.setHovered(false);
            }

            if (target == null) {
                hovered = null;
                return;
            }

            hovered = target;
            target.setHovered(true);
            target.onMouseMove(event);
        }
    }

    /**
     * Scroll listener responsible for routing scroll events to the scrollable element under the cursor.
     */
    private class UIScrollListener implements MouseScrollListener {

        /**
         * Handles mouse-wheel scrolling.
         *
         * <p>
         * The method searches for the scrollable element at the current mouse position and forwards
         * the scroll event only to that target if one exists.
         * </p>
         *
         * @param event scroll event
         */
        @Override
        public void mouseScrolled(MouseScrollEvent event) {
            Element target = findElementAt(Mouse.getX(), Mouse.getY(), Element.SCROLLABLE);
            if (target == null) return;
            target.onMouseScroll(event);
        }
    }

    /**
     * Window resize listener responsible for recursively propagating resize notifications and layout updates.
     */
    private class UIWindowListener implements WindowResizeListener {

        /**
         * Handles window resize events by propagating them through the entire UI tree.
         *
         * @param event resize event
         */
        @Override
        public void windowResized(WindowResizeEvent event) {
            propagateResize(UI.this, event);
        }
    }

    /**
     * Recursively propagates a window resize event through the UI tree.
     *
     * <p>
     * Each element first receives the resize callback and is then laid out again. If the element
     * is a container, all of its children are processed recursively in order.
     * </p>
     *
     * @param element current traversal element
     * @param e       resize event being propagated
     */
    private void propagateResize(Element element, WindowResizeEvent e) {
        if (element == null) return;

        element.onWindowResize(e);
        element.layout();

        if (element instanceof ElementContainer container) {
            for (int i = 0; i < container.size; i++) {
                Element child = container.elements[i];
                if (child != null) propagateResize(child, e);
            }
        }
    }
}