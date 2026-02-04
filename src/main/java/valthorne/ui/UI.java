package valthorne.ui;

import valthorne.Keyboard;
import valthorne.Mouse;
import valthorne.Window;
import valthorne.event.events.*;
import valthorne.event.listeners.KeyListener;
import valthorne.event.listeners.MouseListener;
import valthorne.event.listeners.MouseScrollListener;
import valthorne.event.listeners.WindowResizeListener;
import valthorne.graphics.DrawFunction;
import valthorne.math.Vector2f;
import valthorne.viewport.Viewport;

/**
 * Represents the main user interface (UI) container for managing and rendering hierarchical
 * UI elements.
 */
public class UI extends ElementContainer {

    private final DrawFunction draw = super::draw;

    private Element focused; // The currently focused UI element
    private Element pressed; // The UI element that is currently being pressed
    private Element hovered; // The UI element that the mouse is currently hovering over
    private Viewport viewport;

    // Reused traversal state
    private Element found;
    private boolean seenFrom;

    public UI() {
        Keyboard.addKeyListener(new UIKeyListener());
        Mouse.addMouseListener(new UIMouseListener());
        Mouse.addScrollListener(new UIScrollListener());
        Window.addWindowResizeListener(new UIWindowListener());
    }

    @Override
    public Element findElementAt(float x, float y, byte flags) {
        if (viewport == null) {
            return super.findElementAt(x - this.getX(), y - this.getY(), flags);
        }

        // Current API (may allocate if screenToWorld returns a new Vector2f)
        Vector2f world = viewport.screenToWorld(x, y);
        if (world == null) return null;

        return super.findElementAt(world.getX(), world.getY(), flags);
    }

    @Override
    public void draw() {
        if (viewport != null) {
            viewport.render(draw);
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

    public void focusNext() {
        Element next = findNextFocusable(focused);
        setFocusTo(next);
    }

    private Element findNextFocusable(Element from) {
        if (from == null) return findFirstFocusable(this);

        // reset traversal state
        found = null;
        seenFrom = false;

        traverseNextFocusable(this, from);

        return (found != null) ? found : findFirstFocusable(this);
    }

    private void traverseNextFocusable(Element element, Element from) {
        if (element == null || found != null) return;

        // Once we've seen "from", the next focusable becomes our answer.
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

    private Element findFirstFocusable(Element root) {
        found = null;
        traverseFirstFocusable(root);
        return found;
    }

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

    private boolean isElementFocusableNow(Element e) {
        return e != null && e.isFocusable() && !e.isHidden() && !e.isDisabled();
    }

    public Viewport getViewport() {
        return viewport;
    }

    public void setViewport(Viewport viewport) {
        this.viewport = viewport;
    }

    public Element getFocused() {
        return focused;
    }

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

    private class UIMouseListener implements MouseListener {

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
            Element target = findElementAt(event.getX(), event.getY(), Element.CLICKABLE);

            if (target != hovered && hovered != null) {
                hovered.setHovered(false);
            }

            if (target == null) return;

            hovered = target;
            target.setHovered(true);
            target.onMouseMove(event);
        }
    }

    private class UIScrollListener implements MouseScrollListener {

        @Override
        public void mouseScrolled(MouseScrollEvent event) {
            Element target = findElementAt(Mouse.getX(), Mouse.getY(), Element.SCROLLABLE);
            if (target == null) return;
            target.onMouseScroll(event);
        }
    }

    private class UIWindowListener implements WindowResizeListener {

        @Override
        public void windowResized(WindowResizeEvent event) {
            // No BiConsumer allocation; just recurse
            propagateResize(UI.this, event);
        }
    }

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
