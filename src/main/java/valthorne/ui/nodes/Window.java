package valthorne.ui.nodes;

import valthorne.Keyboard;
import valthorne.Mouse;
import valthorne.event.events.KeyPressEvent;
import valthorne.event.events.MouseDragEvent;
import valthorne.event.events.MousePressEvent;
import valthorne.event.events.MouseReleaseEvent;
import valthorne.ui.UIInputEvent;
import valthorne.ui.UINode;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Titled floating UI container with independently configurable movement and resizing.
 * This is an in-application widget, distinct from the native {@link valthorne.Window}.
 * Drag its title bar to move it, or any edge/corner to resize. Pointer capture keeps
 * gestures active outside the original bounds. Clicking a descendant raises the whole
 * window without detaching its controls or disrupting their focus. The top-right X
 * closes the UI window, preserving its content for a later call to open().
 * <pre>{@code
 * valthorne.ui.nodes.Window tools = new valthorne.ui.nodes.Window("Tools")
 *         .bounds(40, 60, 420, 300)
 *         .draggable(true).resizable(true)
 *         .minimumSize(240, 160);
 * tools.getContentPane().add(new Button("Apply"));
 * root.add(tools);
 * }</pre>
 * Windows use absolute, parent-local, top-left coordinates. Their clipped content
 * pane follows the client area as the outer frame changes. Use the content pane or
 * {@link #content(UINode)} rather than adding children directly to the window.
 * The defaults are a 360-by-260 frame at (0,0), movement/resizing enabled, and a
 * 160-by-100 minimum. Programmatic bounds changes are silent; interactive changes
 * notify once per changed frame. Configure appearance through ProfessionalTheme's
 * window-frame, window-title, window-close, and window-grip named styles. Use on the UI thread.
 * @author Albert Beaupre
 */
public class Window extends Panel {
    /**
     * Immutable outer-frame geometry in parent-local layout units.
     * @param x left position
     * @param y top position
     * @param width outer width, including resize borders
     * @param height outer height, including title and resize borders
     * @author Albert Beaupre
     */
    public record Frame(float x, float y, float width, float height) {}

    /**
     * Width of the pointer-sensitive edge and corner regions in logical layout units.
     */
    private static final float BORDER = 6;
    /**
     * Length of each corner's two border arms, providing a practical diagonal grab area.
     */
    private static final float CORNER_REACH = 16;
    /**
     * Height reserved for the title bar, independent of the content's preferred size.
     */
    private static final float TITLE_HEIGHT = 32;
    /**
     * Width reserved at the right of the title bar for the close action.
     */
    private static final float CLOSE_WIDTH = 36;

    private final Grip titleBar = new Grip(0, 0); // Move handle containing the title label.
    private final ScrollPanel titleViewport = new ScrollPanel(); // Clips long captions without intercepting title dragging.
    private final Button closeButton = new Button("X"); // Independent title-bar close control, above the move handle.
    private final ScrollPanel viewport = new ScrollPanel(); // Clips client controls to the current body area.
    private final Panel contentPane = new Panel(); // User-owned control hierarchy hosted by this window.
    private final Grip[] grips = new Grip[8]; // Four edges and four corners, above the client area in hit order.
    private boolean draggable = true; // Whether title-bar pointer and keyboard movement are enabled.
    private boolean resizable = true; // Whether edge/corner pointer and keyboard resizing are enabled.
    private boolean keepWithinParent = true; // Whether user movement/resizing clamps to parent bounds when possible.
    private float minimumWidth = 160, minimumHeight = 100; // Minimum interactive and programmatic outer dimensions.
    private float maximumWidth = Float.MAX_VALUE, maximumHeight = Float.MAX_VALUE; // Inclusive configured outer-size ceilings.
    private Frame frame = new Frame(0, 0, 360, 260); // Last requested outer geometry, used while detached.
    private Consumer<Frame> changed = value -> {}; // Listener for committed user movement and resizing.
    private Runnable closed = () -> {}; // Notification after closing, with visibility and subtree input already cleared.
    private Grip active; // Current gesture owner, cleared on release, cancellation, or policy changes.
    private float layoutWidth = -1, layoutHeight = -1; // Cached dimensions for internal chrome layout.

    /**
     * Creates an empty titled window with default movement, sizing, and containment policies.
     * @param title nonnull title text, which may be empty
     */
    public Window(String title) {
        setStyleName("window-frame");
        getLayout().absolute().left(0).top(0).width(360).height(260).noGrow().noShrink().minSize(160, 100);
        titleBar.text(Objects.requireNonNull(title)); titleBar.setStyleName("window-title");
        titleBar.getLayout().itemsStart().paddingLeft(10);
        titleBar.remove(titleBar.getLabel());
        titleViewport.horizontal(false).vertical(false).horizontalBar(false).verticalBar(false); titleViewport.setClickable(false);
        titleBar.getLabel().getLayout().marginTop(8).marginLeft(10);
        titleViewport.setContent(titleBar.getLabel()); titleViewport.getLayout().absolute().left(0).top(0).widthPercent(100).heightPercent(100);
        titleBar.add(titleViewport);
        closeButton.setStyleName("window-close"); closeButton.action(button -> close());
        viewport.horizontal(false).vertical(false).horizontalBar(false).verticalBar(false);
        contentPane.getLayout().column().widthPercent(100).heightPercent(100).padding(8);
        viewport.setContent(contentPane);
        super.add(viewport, titleBar, closeButton);
        int[][] directions = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}, {-1, -1}, {1, -1}, {-1, 1}, {1, 1}};
        for (int i = 0; i < grips.length; i++) {
            grips[i] = new Grip(directions[i][0], directions[i][1]); grips[i].setStyleName("window-grip"); super.add(grips[i]);
        }
    }

    /**
     * Replaces the title without changing frame geometry or notifying the move/resize listener.
     * @param title nonnull replacement caption
     * @return this window
     */
    public Window title(String title) { titleBar.text(Objects.requireNonNull(title)); return this; }

    /**
     * Reads the displayed caption without transferring label ownership.
     * @return current title
     */
    public String getTitle() { return titleBar.getText(); }

    /**
     * Closes a visible, enabled UI window without destroying its contents or closing the
     * native application window. Input owned by this subtree is cancelled before the
     * listener runs. Repeated closes are silent until the window is shown again.
     * @return this window
     */
    public Window close() {
        if (!isVisible() || isDisabled()) return this;
        cancelGesture(); setVisible(false);
        if (getRoot() != null) getRoot().cancelInput(this);
        closed.run(); return this;
    }

    /**
     * Shows and raises the existing window with its saved frame and content. If attached
     * and enabled, focuses its title bar after layout; does not emit a close notification.
     * @return this window
     */
    public Window open() {
        setVisible(true); bringToFront();
        if (getRoot() != null && isEnabled()) { getRoot().layout(); getRoot().setFocusTo(titleBar); }
        return this;
    }

    /**
     * Replaces the listener called after X or close() hides a previously visible window.
     * The callback may remove the window or reopen it; application exceptions propagate.
     * Direct setVisible calls do not invoke this listener.
     * @param listener nonnull synchronous close notification
     * @return this window
     */
    public Window onClose(Runnable listener) { closed = Objects.requireNonNull(listener); return this; }

    /**
     * Exposes the owned X button for styling and keyboard focus without transferring ownership.
     * @return top-right close control
     */
    public Button getCloseButton() { return closeButton; }

    /**
     * Independently enables or disables title-bar dragging and keyboard movement.
     * Disabling movement cancels an active move gesture at its current position.
     * @param enabled whether the window may be moved interactively
     * @return this window
     */
    public Window draggable(boolean enabled) {
        draggable = enabled; if (!enabled && active == titleBar) cancelGesture(); return this;
    }

    /**
     * Reports the title-bar movement policy; it does not indicate an active gesture.
     * @return whether user movement is allowed
     */
    public boolean isDraggable() { return draggable; }

    /**
     * Independently enables or disables edge/corner resizing and their focus targets.
     * The reserved border stays in place, so toggling this policy does not shift content.
     * @param enabled whether the frame may be resized interactively
     * @return this window
     */
    public Window resizable(boolean enabled) {
        resizable = enabled; if (!enabled && active != titleBar) cancelGesture();
        for (Grip grip : grips) { grip.setVisible(enabled); grip.setFocusable(enabled); }
        return this;
    }

    /**
     * Reads whether resize handles accept pointer and keyboard changes.
     * @return whether user resizing is enabled
     */
    public boolean isResizable() { return resizable; }

    /**
     * Configures containment for subsequent user gestures. Minimum size takes priority
     * when the parent is smaller than the configured minimum; the frame then anchors at zero.
     * Programmatic bounds remain unrestricted to support saved layouts and animations.
     * @param enabled whether user geometry should remain inside the parent when possible
     * @return this window
     */
    public Window keepWithinParent(boolean enabled) { keepWithinParent = enabled; cancelGesture(); return this; }

    /**
     * Sets finite positive minimum dimensions and clamps the current frame silently.
     * Width must accommodate borders; height must accommodate title and borders.
     * @param width minimum outer width, at least 40
     * @param height minimum outer height, at least 48
     * @return this window
     * @throws IllegalArgumentException for invalid dimensions or minima above current maxima
     */
    public Window minimumSize(float width, float height) {
        validateSize(width, height);
        if (width > maximumWidth || height > maximumHeight) throw new IllegalArgumentException("Minimum exceeds maximum");
        Frame current = getFrame(); minimumWidth = width; minimumHeight = height;
        getLayout().minSize(width, height); return bounds(current.x(), current.y(), current.width(), current.height());
    }

    /**
     * Sets finite positive maximum dimensions and clamps the current frame silently.
     * @param width maximum outer width, at least the current minimum
     * @param height maximum outer height, at least the current minimum
     * @return this window
     * @throws IllegalArgumentException for invalid dimensions or maxima below current minima
     */
    public Window maximumSize(float width, float height) {
        validateSize(width, height);
        if (width < minimumWidth || height < minimumHeight) throw new IllegalArgumentException("Maximum is below minimum");
        Frame current = getFrame(); maximumWidth = width; maximumHeight = height;
        getLayout().maxWidth(width).maxHeight(height); return bounds(current.x(), current.y(), current.width(), current.height());
    }

    /**
     * Rejects nonfinite sizes and sizes too small to contain basic window chrome.
     * @param width proposed outer width
     * @param height proposed outer height
     */
    private static void validateSize(float width, float height) {
        if (!Float.isFinite(width) || !Float.isFinite(height) || width < 40 || height < 48)
            throw new IllegalArgumentException("Window dimensions must be finite and at least 40 by 48");
    }

    /**
     * Silently sets absolute top-left bounds, clamping size to the configured limits.
     * Cancels any active gesture so its old pointer anchor cannot overwrite the new frame.
     * @param x finite parent-local left position
     * @param y finite parent-local top position
     * @param width finite requested width, at least 40 before configured limits apply
     * @param height finite requested height, at least 48 before configured limits apply
     * @return this window
     * @throws IllegalArgumentException for nonfinite positions or invalid sizes
     */
    public Window bounds(float x, float y, float width, float height) {
        validateSize(width, height);
        if (!Float.isFinite(x) || !Float.isFinite(y)) throw new IllegalArgumentException("Window position must be finite");
        cancelGesture(); install(new Frame(x, y, Math.clamp(width, minimumWidth, maximumWidth), Math.clamp(height, minimumHeight, maximumHeight)));
        return this;
    }

    /**
     * Reads the latest known bounds: requested geometry before layout, then computed
     * geometry after layout. Direct layout changes become visible here after layout runs.
     * @return immutable outer frame
     */
    public Frame getFrame() { return frame; }

    /**
     * Exposes the owned client panel for arranging arbitrary child controls. Content is
     * clipped at the body boundary; add an explicit ScrollPanel when scrolling is desired.
     * @return client content panel
     */
    public Panel getContentPane() { return contentPane; }

    /**
     * Replaces client controls with one supplied unattached node. The existing children
     * follow normal container removal/destruction; the new child keeps its own layout.
     * @param node nonnull unattached client control
     * @return this window
     */
    public Window content(UINode node) {
        Objects.requireNonNull(node);
        if (node.getParent() != null) throw new IllegalArgumentException("Content must be unattached");
        contentPane.clear(); contentPane.add(node); return this;
    }

    /**
     * Exposes the title bar as a keyboard-focus and style target without reparenting it.
     * @return owned title handle
     */
    public Button getTitleBar() { return titleBar; }

    /**
     * Registers user-frame notifications. A listener sees committed bounds and may
     * modify the window; exceptions propagate. Silent setters never emit notifications.
     * @param listener nonnull movement/resizing consumer
     * @return this window
     */
    public Window onChange(Consumer<Frame> listener) { changed = Objects.requireNonNull(listener); return this; }

    /**
     * Raises this window among its siblings without losing child focus or pointer capture.
     * A detached window has no stacking order and ignores this operation.
     * @return this window
     */
    public Window bringToFront() { if (getParent() != null) getParent().bringToFront(this); return this; }

    /**
     * Commits geometry to absolute Yoga layout without invoking application callbacks.
     * @param next validated and constrained frame
     */
    private void install(Frame next) {
        frame = next; getLayout().absolute().left(next.x()).top(next.y()).width(next.width()).height(next.height());
    }

    /**
     * Stops local gesture state. Root capture is released by the normal release/cancel
     * path, so this does not cancel unrelated input owned by another control.
     */
    private void cancelGesture() { if (active != null) { active.setDragging(false); active = null; } }

    /**
     * Samples computed frame geometry when attached, preserving the detached API state otherwise.
     * @return outer frame at the start of a user operation
     */
    private Frame computedFrame() { return getRoot() == null ? frame : new Frame(getX(), getY(), getWidth(), getHeight()); }

    /**
     * Applies a pointer/keyboard delta to an anchored frame, preserving the opposite edge
     * during resizing. Parent bounds constrain user operations after size limits.
     * @param origin original frame
     * @param horizontal -1 for left, 1 for right, or 0 for no horizontal resizing
     * @param vertical -1 for top, 1 for bottom, or 0 for no vertical resizing
     * @param dx horizontal pointer displacement in layout units
     * @param dy vertical pointer displacement in top-down layout units
     */
    private void transform(Frame origin, int horizontal, int vertical, float dx, float dy) {
        float x = origin.x(), y = origin.y(), width = origin.width(), height = origin.height();
        boolean moving = horizontal == 0 && vertical == 0;
        if (moving) { x += dx; y += dy; }
        else {
            if (horizontal != 0) { width = Math.clamp(width + horizontal * dx, minimumWidth, maximumWidth); if (horizontal < 0) x += origin.width() - width; }
            if (vertical != 0) { height = Math.clamp(height + vertical * dy, minimumHeight, maximumHeight); if (vertical < 0) y += origin.height() - height; }
        }
        if (keepWithinParent && getParent() != null) {
            float availableWidth = getParent().getWidth(), availableHeight = getParent().getHeight();
            if (!moving) {
                if (horizontal < 0 && x < 0) { width = Math.max(minimumWidth, width + x); x = origin.x() + origin.width() - width; }
                if (vertical < 0 && y < 0) { height = Math.max(minimumHeight, height + y); y = origin.y() + origin.height() - height; }
                if (horizontal > 0) width = Math.max(minimumWidth, Math.min(width, availableWidth - x));
                if (vertical > 0) height = Math.max(minimumHeight, Math.min(height, availableHeight - y));
            }
            x = Math.clamp(x, 0, Math.max(0, availableWidth - width));
            y = Math.clamp(y, 0, Math.max(0, availableHeight - height));
        }
        Frame next = new Frame(x, y, width, height);
        if (!next.equals(frame)) { install(next); changed.accept(next); }
    }

    /**
     * Repositions the title, clipped body, and eight resize zones when outer size changes.
     * Edges stay outside the content and corners win hit testing where zones meet.
     */
    @Override protected void afterLayout() {
        super.afterLayout(); float width = getWidth(), height = getHeight();
        frame = new Frame(getX(), getY(), width, height);
        if (width == layoutWidth && height == layoutHeight) return;
        layoutWidth = width; layoutHeight = height;
        place(titleBar, BORDER, BORDER, width - BORDER * 2, TITLE_HEIGHT);
        place(titleViewport, 0, 0, width - BORDER * 2 - CLOSE_WIDTH, TITLE_HEIGHT);
        place(closeButton, width - BORDER - CLOSE_WIDTH, BORDER, CLOSE_WIDTH, TITLE_HEIGHT);
        place(viewport, BORDER, BORDER + TITLE_HEIGHT, width - BORDER * 2, height - BORDER * 2 - TITLE_HEIGHT);
        place(grips[0], 0, BORDER, BORDER, height - BORDER * 2);
        place(grips[1], width - BORDER, BORDER, BORDER, height - BORDER * 2);
        place(grips[2], BORDER, 0, width - BORDER * 2, BORDER);
        place(grips[3], BORDER, height - BORDER, width - BORDER * 2, BORDER);
        place(grips[4], 0, 0, BORDER, BORDER); place(grips[5], width - BORDER, 0, BORDER, BORDER);
        place(grips[6], 0, height - BORDER, BORDER, BORDER); place(grips[7], width - BORDER, height - BORDER, BORDER, BORDER);
    }

    /**
     * Assigns nonnegative absolute chrome bounds without affecting outer frame ownership.
     * @param node owned chrome node
     * @param x left coordinate
     * @param y top coordinate
     * @param width requested width
     * @param height requested height
     */
    private static void place(UINode node, float x, float y, float width, float height) {
        node.getLayout().absolute().left(x).top(y).width(Math.max(0, width)).height(Math.max(0, height)).minSize(0, 0);
    }

    /**
     * Raises the frame on a primary press anywhere inside it without consuming child input.
     * @param context routed preview event
     */
    @Override public void onInputPreview(UIInputEvent context) {
        if (!isDisabled() && context.event() instanceof MousePressEvent press && press.getButton() == Mouse.LEFT) bringToFront();
    }

    /**
     * Gives each corner priority along both adjoining border arms, while leaving title
     * and client content hit testing unchanged. Incoming coordinates are render-space
     * coordinates already transformed by the root viewport and any scrolling ancestors.
     * @param x transformed render-space horizontal coordinate
     * @param y transformed render-space vertical coordinate
     * @param requiredBit requested input capability, or -1 for any node
     * @return diagonal handle near a corner, otherwise the normal descendant hit
     */
    @Override public UINode findNodeAt(float x, float y, int requiredBit) {
        if (!isVisible() || isDisabled()) return null;
        if (resizable && contains(x, y)) {
            float localX = x - getRenderX(), localY = getRenderY() + getHeight() - y;
            boolean onBorder = localX < BORDER || localX >= getWidth() - BORDER || localY < BORDER || localY >= getHeight() - BORDER;
            int horizontal = localX < CORNER_REACH ? -1 : localX >= getWidth() - CORNER_REACH ? 1 : 0;
            int vertical = localY < CORNER_REACH ? -1 : localY >= getHeight() - CORNER_REACH ? 1 : 0;
            if (onBorder && horizontal != 0 && vertical != 0) {
                Grip corner = grips[4 + (vertical > 0 ? 2 : 0) + (horizontal > 0 ? 1 : 0)];
                if (requiredBit < 0 || corner.getBit(requiredBit)) return corner;
            }
        }
        return super.findNodeAt(x, y, requiredBit);
    }

    /**
     * Cancels movement/resizing before the window leaves the hierarchy or its root is disposed.
     */
    @Override public void onDestroy() { cancelGesture(); layoutWidth = layoutHeight = -1; }

    /**
     * Captured move/resize target. A zero direction pair identifies the title bar;
     * edge and corner targets preserve their opposite boundaries when size limits apply.
     * @author Albert Beaupre
     */
    private final class Grip extends Button {
        private final int horizontal, vertical; // Edge directions, with (0,0) identifying window movement.
        private Frame origin; // Original computed frame for the current pointer gesture.
        private float startX, startY; // Parent-local pointer coordinates at initial press.

        /**
         * Creates one direction-aware pointer and keyboard manipulation target.
         * @param horizontal horizontal resize direction or zero
         * @param vertical vertical resize direction or zero
         */
        private Grip(int horizontal, int vertical) { this.horizontal = horizontal; this.vertical = vertical; }

        /**
         * Checks the enclosing window policy rather than treating hidden grips as active.
         * @return true when this enabled target may begin or continue a gesture
         */
        private boolean allowed() { return !Window.this.isDisabled() && !isDisabled() && (this == titleBar ? draggable : resizable); }

        /**
         * Selects the native edge or diagonal resize cursor. The title retains the
         * application cursor, and disabling resizing removes every resize request.
         * @return horizontal, vertical, or matching diagonal shape; zero for no override
         */
        @Override public int getCursorShape() {
            if (!allowed() || this == titleBar) return 0;
            if (horizontal == 0) return Mouse.CURSOR_VRESIZE;
            if (vertical == 0) return Mouse.CURSOR_HRESIZE;
            return horizontal == vertical ? Mouse.CURSOR_RESIZE_NWSE : Mouse.CURSOR_RESIZE_NESW;
        }

        /**
         * Captures an initial frame and parent-local pointer anchor for a primary press.
         * @param event routed mouse press
         */
        @Override public void onMousePress(MousePressEvent event) {
            if (!allowed() || event.getButton() != Mouse.LEFT || Window.this.getParent() == null) return;
            var point = Window.this.getParent().screenToLocal(event.getX(), event.getY());
            origin = computedFrame(); startX = point.x(); startY = point.y(); active = this; setDragging(true); event.consume();
        }

        /**
         * Applies captured displacement in parent coordinates, avoiding feedback as the
         * window itself moves. The root handles viewport scaling and pointer capture.
         * @param event routed drag destination
         */
        @Override public void onMouseDrag(MouseDragEvent event) {
            if (active != this || !allowed() || event.getButton() != Mouse.LEFT) return;
            var point = Window.this.getParent().screenToLocal(event.getToX(), event.getToY());
            transform(origin, horizontal, vertical, point.x() - startX, point.y() - startY); event.consume();
        }

        /**
         * Ends a primary gesture without invoking ordinary button activation.
         * @param event routed mouse release
         */
        @Override public void onMouseRelease(MouseReleaseEvent event) {
            if (event.getButton() == Mouse.LEFT) { if (active == this) cancelGesture(); event.consume(); }
        }

        /**
         * Stops the gesture on focus/capture loss, hiding, detachment, or root cancellation.
         */
        @Override public void onPointerCancel() { if (active == this) cancelGesture(); setDragging(false); }

        /**
         * Moves the title or resizes this handle's axes with arrow keys. Shift increases
         * the step from eight to 32 units; unrelated keys remain available to normal routing.
         * @param event routed key press
         */
        @Override public void onKeyPress(KeyPressEvent event) {
            if (!allowed()) return;
            float step = event.isShiftDown() ? 32 : 8, dx = 0, dy = 0;
            switch (event.getKey()) {
                case Keyboard.LEFT -> dx = -step;
                case Keyboard.RIGHT -> dx = step;
                case Keyboard.UP -> dy = -step;
                case Keyboard.DOWN -> dy = step;
                default -> { return; }
            }
            if (this != titleBar) { if (horizontal == 0) dx = 0; if (vertical == 0) dy = 0; }
            cancelGesture(); transform(computedFrame(), horizontal, vertical, dx, dy); event.consume();
        }
    }
}
