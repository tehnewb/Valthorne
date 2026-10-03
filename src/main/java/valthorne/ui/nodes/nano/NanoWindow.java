package valthorne.ui.nodes.nano;

import org.lwjgl.nanovg.NVGPaint;
import valthorne.Keyboard;
import valthorne.Mouse;
import valthorne.event.events.KeyPressEvent;
import valthorne.event.events.MouseDragEvent;
import valthorne.event.events.MousePressEvent;
import valthorne.event.events.MouseReleaseEvent;
import valthorne.graphics.Color;
import valthorne.ui.NanoUtility;
import valthorne.ui.UIInputEvent;
import valthorne.ui.UINode;
import valthorne.ui.enums.WindowSnapArea;
import valthorne.ui.theme.ResolvedStyle;
import valthorne.ui.theme.StyleKey;
import java.util.Objects;
import java.util.function.Consumer;

import static org.lwjgl.nanovg.NanoVG.*;

/**
 * NanoVG variant. Titled floating UI container with independently configurable movement and resizing.
 * This is an in-application widget, distinct from the native {@link valthorne.Window}.
 * Drag its title bar to move it, or any edge/corner to resize. Pointer capture keeps
 * gestures active outside the original bounds. Clicking a descendant raises the whole
 * window without detaching its controls or disrupting their focus. The top-right X
 * closes the UI window, preserving its content for a later call to open().
 * <pre>{@code
 * valthorne.ui.nodes.nano.NanoWindow tools = new valthorne.ui.nodes.nano.NanoWindow("Tools")
 *         .bounds(40, 60, 420, 300)
 *         .draggable(true).resizable(true)
 *         .minimumSize(240, 160);
 * tools.getContentPane().add(new NanoButton("Apply"));
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
public class NanoWindow extends NanoPanel {
    /*
     * Monotonic UI-thread creation order keeps shared dock slots stable when
     * window focus changes sibling painting order.
     */
    private static long nextDockOrder;

    private final long dockOrder = nextDockOrder++; // Stable shared-slot position independent of z-order.
    public static final StyleKey<Float> SHADOW_CORNER_RADIUS_KEY =
            StyleKey.of("nano.window.shadowCornerRadius", Float.class, 0f);
    private static final Color SHADOW_INNER = new Color(0x38000000);
    private static final Color SHADOW_OUTER = new Color(0x00000000);
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
     * Visual inset of the frame; resize targets extend farther inside than this outline.
     */
    private static final float FRAME_INSET = 3;
    /**
     * Length of each corner's two border arms, providing a practical diagonal grab area.
     */
    private static final float CORNER_REACH = 16;
    /**
     * Height reserved for the title bar, independent of the content's preferred size.
     */
    private static final float TITLE_HEIGHT = 30;
    /**
     * Width reserved at the right of the title bar for the close action.
     */
    private static final float CLOSE_WIDTH = 32;

    private final Grip titleBar = new Grip(0, 0); // Move handle containing the title label.
    private final NanoScrollPanel titleViewport = NanoWidgetSupport.viewport(); // Clips long captions without intercepting title dragging.
    private final NanoButton closeButton = new CloseButton(); // Independent title-bar close control, above the move handle.
    private final NanoPanel divider = new NanoPanel(); // Thin boundary between title controls and client content.
    private final NanoScrollPanel viewport = NanoWidgetSupport.viewport(); // Clips client controls to the current body area.
    private final NanoPanel contentPane = NanoWidgetSupport.panel(); // User-owned control hierarchy hosted by this window.
    private final Grip[] grips = new Grip[8]; // Four edges and four corners, above the client area in hit order.
    private boolean draggable = true; // Whether title-bar pointer and keyboard movement are enabled.
    private boolean resizable = true; // Whether edge/corner pointer and keyboard resizing are enabled.
    private boolean keepWithinParent = true; // Whether user movement/resizing clamps to parent bounds when possible.
    private float movementTopInset; // Reserved parent strip that window movement and resizing cannot overlap.
    private float minimumWidth = 160, minimumHeight = 100; // Minimum interactive and programmatic outer dimensions.
    private float maximumWidth = Float.MAX_VALUE, maximumHeight = Float.MAX_VALUE; // Inclusive configured outer-size ceilings.
    private Frame frame = new Frame(0, 0, 360, 260); // Last requested outer geometry, used while detached.
    private Consumer<Frame> changed = value -> {}; // Listener for committed user movement and resizing.
    private Runnable closed = () -> {}; // Notification after closing, with visibility and subtree input already cleared.
    private Grip active; // Current gesture owner, cleared on release, cancellation, or policy changes.
    private float layoutWidth = -1, layoutHeight = -1; // Cached dimensions for internal chrome layout.
    private boolean snapping; // Enables edge and corner workspace placement.
    private boolean snapGrowing = true; // Fills the allocated workspace docking region when snapping.
    private WindowSnapArea snapArea = WindowSnapArea.NONE; // Last committed workspace region.
    private WindowSnapArea pendingSnap = WindowSnapArea.NONE; // Current pointer preview destination.
    private Frame unsnappedFrame; // Floating frame restored when dragging away from a snap.
    private float parentWidth = -1; // Last workspace width constrained after parent layout.
    private float parentHeight = -1; // Last workspace height constrained after parent layout.

    /**
     * Draws a geometrically centered cross independent of font glyph bearings.
     */
    private static final class CloseButton extends NanoButton {
        /**
         * Keeps the regular button's hit area, focus, and style without a text glyph.
         */
        private CloseButton() { super(""); }

        /**
         * Paints the themed button first, then two balanced diagonals around its center.
         */
        @Override public void draw(long vg) {
            super.draw(vg);
            ResolvedStyle style = getStyle();
            Color ink = Color.WHITE;
            if (style != null) {
                if (!isEnabled()) ink = style.get(DISABLED_TEXT_COLOR_KEY);
                else if (isPressed()) ink = style.get(PRESSED_TEXT_COLOR_KEY);
                else if (isFocused()) ink = style.get(FOCUSED_TEXT_COLOR_KEY);
                else if (isHovered()) ink = style.get(HOVER_TEXT_COLOR_KEY);
                else ink = style.get(TEXT_COLOR_KEY);
            }
            float cx = getAbsoluteX() + getWidth() * 0.5f;
            float cy = getAbsoluteY() + getHeight() * 0.5f;
            float radius = 4.5f;
            nvgSave(vg);
            nvgGlobalAlpha(vg, !isEnabled() || isHovered() || isPressed() ? 1f : 0.68f);
            nvgBeginPath(vg);
            nvgMoveTo(vg, cx - radius, cy - radius);
            nvgLineTo(vg, cx + radius, cy + radius);
            nvgMoveTo(vg, cx + radius, cy - radius);
            nvgLineTo(vg, cx - radius, cy + radius);
            nvgStrokeColor(vg, NanoUtility.color1(ink));
            nvgStrokeWidth(vg, 1f);
            nvgLineCap(vg, NVG_BUTT);
            nvgStroke(vg);
            nvgRestore(vg);
        }
    }

    /**
     * Creates an empty titled window with default movement, sizing, and containment policies.
     * @param title nonnull title text, which may be empty
     */
    public NanoWindow(String title) {
        setStyleName("window-frame");
        getLayout().absolute().left(0).top(0).width(360).height(260).noGrow().noShrink().minSize(160, 100);
        titleBar.text(Objects.requireNonNull(title)); titleBar.setStyleName("window-title");
        titleBar.setStyle(NanoButton.BORDER_WIDTH_KEY, 0f);
        titleBar.setStyle(NanoButton.CORNER_RADIUS_KEY, 0f);
        titleBar.getLayout().itemsStart();
        titleBar.remove(titleBar.getLabel());
        titleViewport.horizontal(false).vertical(false).horizontalBar(false).verticalBar(false); titleViewport.setClickable(false);
        titleBar.getLabel().fontSize(14).setStyleName("window-caption");
        titleBar.getLabel().getLayout().marginTop(7).marginLeft(12);
        titleViewport.setContent(titleBar.getLabel()); titleViewport.getLayout().absolute().left(0).top(0).widthPercent(100).heightPercent(100);
        titleBar.add(titleViewport);
        closeButton.setStyleName("window-close");
        closeButton.setStyle(NanoButton.BORDER_WIDTH_KEY, 0f);
        closeButton.setStyle(NanoButton.CORNER_RADIUS_KEY, 0f);
        closeButton.action(button -> close());
        divider.setStyleName("window-divider"); divider.setClickable(false); divider.borderWidth(0);
        divider.backgroundColor(new Color(0xFF3E506A));
        viewport.horizontal(false).vertical(false).horizontalBar(false).verticalBar(false);
        contentPane.getLayout().column().widthPercent(100).heightPercent(100).padding(12);
        viewport.setContent(contentPane);
        super.add(viewport, titleBar, closeButton, divider);
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
    public NanoWindow title(String title) { titleBar.text(Objects.requireNonNull(title)); return this; }

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
    public NanoWindow close() {
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
    public NanoWindow open() {
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
    public NanoWindow onClose(Runnable listener) { closed = Objects.requireNonNull(listener); return this; }

    /**
     * Exposes the owned X button for styling and keyboard focus without transferring ownership.
     * @return top-right close control
     */
    public NanoButton getCloseButton() { return closeButton; }

    /**
     * Independently enables or disables title-bar dragging and keyboard movement.
     * Disabling movement cancels an active move gesture at its current position.
     * @param enabled whether the window may be moved interactively
     * @return this window
     */
    public NanoWindow draggable(boolean enabled) {
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
    public NanoWindow resizable(boolean enabled) {
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
     * Programmatic bounds still honor the configured minimum and maximum size.
     * @param enabled whether user geometry should remain inside the parent when possible
     * @return this window
     */
    public NanoWindow keepWithinParent(boolean enabled) { keepWithinParent = enabled; cancelGesture(); return this; }

    /**
     * Sets finite positive minimum dimensions and clamps the current frame silently.
     * Width must accommodate borders; height must accommodate title and borders.
     * @param width minimum outer width, at least 40
     * @param height minimum outer height, at least 48
     * @return this window
     * @throws IllegalArgumentException for invalid dimensions or minima above current maxima
     */
    public NanoWindow minimumSize(float width, float height) {
        validateSize(width, height);
        if (width > maximumWidth || height > maximumHeight) throw new IllegalArgumentException("Minimum exceeds maximum");
        Frame current = getFrame(); minimumWidth = width; minimumHeight = height;
        getLayout().minSize(width, height); return bounds(current.x(), current.y(), current.width(), current.height());
    }

    /**
     * Returns the smallest outer width accepted by bounds and resize gestures.
     *
     * @return configured minimum width in UI units
     */
    public float getMinimumWidth() { return minimumWidth; }

    /**
     * Returns the smallest outer height accepted by bounds and resize gestures.
     *
     * @return configured minimum height in UI units
     */
    public float getMinimumHeight() { return minimumHeight; }

    /**
     * Sets finite positive maximum dimensions and clamps the current frame silently.
     * @param width maximum outer width, at least the current minimum
     * @param height maximum outer height, at least the current minimum
     * @return this window
     * @throws IllegalArgumentException for invalid dimensions or maxima below current minima
     */
    public NanoWindow maximumSize(float width, float height) {
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
            throw new IllegalArgumentException("NanoWindow dimensions must be finite and at least 40 by 48");
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
    public NanoWindow bounds(float x, float y, float width, float height) {
        validateSize(width, height);
        if (!Float.isFinite(x) || !Float.isFinite(y)) throw new IllegalArgumentException("NanoWindow position must be finite");
        cancelGesture(); install(new Frame(x, Math.max(movementTopInset, y), Math.clamp(width, minimumWidth, maximumWidth), Math.clamp(height, minimumHeight, maximumHeight)));
        snapArea = WindowSnapArea.NONE;
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
     * clipped at the body boundary; add an explicit NanoScrollPanel when scrolling is desired.
     * @return client content panel
     */
    public NanoPanel getContentPane() { return contentPane; }

    /**
     * Replaces client controls with one supplied unattached node. The existing children
     * follow normal container removal/destruction; the new child keeps its own layout.
     * @param node nonnull unattached client control
     * @return this window
     */
    public NanoWindow content(UINode node) {
        Objects.requireNonNull(node);
        if (node.getParent() != null) throw new IllegalArgumentException("Content must be unattached");
        contentPane.clear(); contentPane.add(node); return this;
    }

    /**
     * Exposes the title bar as a keyboard-focus and style target without reparenting it.
     * @return owned title handle
     */
    public NanoButton getTitleBar() { return titleBar; }

    /**
     * Registers user-frame notifications. A listener sees committed bounds and may
     * modify the window; exceptions propagate. Silent setters never emit notifications.
     * @param listener nonnull movement/resizing consumer
     * @return this window
     */
    public NanoWindow onChange(Consumer<Frame> listener) { changed = Objects.requireNonNull(listener); return this; }

    /**
     * Raises this window among its siblings without losing child focus or pointer capture.
     * A detached window has no stacking order and ignores this operation.
     * @return this window
     */
    public NanoWindow bringToFront() { if (getParent() != null) getParent().bringToFront(this); return this; }

    /**
     * Commits geometry to absolute Yoga layout without invoking application callbacks.
     * @param next validated and constrained frame
     */
    private void install(Frame next) {
        frame = next; getLayout().absolute().left(next.x()).top(next.y()).width(next.width()).height(next.height()).minSize(Math.min(minimumWidth, next.width()), Math.min(minimumHeight, next.height()));
    }

    /**
     * Reflows a committed snap or fits floating geometry into the resolved parent.
     * Workspace containment takes precedence over configured minimum dimensions
     * when the game becomes smaller than those dimensions. Snap membership and
     * retained floating geometry survive shrinking and expanding the workspace.
     *
     * @return whether the frame changed
     */
    private boolean fitWorkspace() {
        if (getParent() == null || !keepWithinParent) return false;
        float width = getParent().getWidth();
        float height = getParent().getHeight();
        if (!Float.isFinite(width + height) || width <= 0 || height <= 0) return false;
        float top = Math.min(movementTopInset, height);
        Frame desired = frame;
        if (snapArea != WindowSnapArea.NONE) {
            boolean east = snapArea == WindowSnapArea.EAST || snapArea == WindowSnapArea.NORTH_EAST || snapArea == WindowSnapArea.SOUTH_EAST;
            boolean west = snapArea == WindowSnapArea.WEST || snapArea == WindowSnapArea.NORTH_WEST || snapArea == WindowSnapArea.SOUTH_WEST;
            boolean south = snapArea == WindowSnapArea.SOUTH || snapArea == WindowSnapArea.SOUTH_EAST || snapArea == WindowSnapArea.SOUTH_WEST;
            boolean north = snapArea == WindowSnapArea.NORTH || snapArea == WindowSnapArea.NORTH_EAST || snapArea == WindowSnapArea.NORTH_WEST;
            if (snapGrowing) {
                desired = NanoWindowDockLayout.resolve(this, this, snapArea, top);
                if (desired.width() <= 0 || desired.height() <= 0)
                    desired = new Frame(east ? width * .5f : 0, south ? top + (height - top) * .5f : top,
                            east || west ? width * .5f : width, north || south ? (height - top) * .5f : height - top);
            }
            else {
                Frame floating = getFloatingFrame();
                float w = Math.min(width, floating.width());
                float h = Math.min(height - top, floating.height());
                desired = new Frame(east ? width - w : west ? 0 : frame.x(), south ? height - h : north ? top : frame.y(), w, h);
            }
        }
        float w = Math.min(width, Math.min(maximumWidth, Math.max(snapArea == WindowSnapArea.NONE ? minimumWidth : 0, desired.width())));
        float h = Math.min(height - top, Math.min(maximumHeight, Math.max(snapArea == WindowSnapArea.NONE ? minimumHeight : 0, desired.height())));
        Frame next = new Frame(Math.clamp(desired.x(), 0, width - w), Math.clamp(desired.y(), top, height - h), w, h);
        if (next.equals(frame)) return false;
        cancelGesture();
        install(next);
        return true;
    }

    /**
     * Stops local gesture state. Root capture is released by the normal release/cancel
     * path, so this does not cancel unrelated input owned by another control.
     */
    private void cancelGesture() {
        pendingSnap = WindowSnapArea.NONE;
        if (active != null) { active.setDragging(false); active = null; }
    }

    /**
     * Enables pointer edge snapping for this window.
     *
     * @param enabled desired snapping policy
     * @return this window
     */
    public NanoWindow snapping(boolean enabled) {
        snapping = enabled;
        if (!enabled) pendingSnap = WindowSnapArea.NONE;
        return this;
    }

    /**
     * Reads the edge snapping policy.
     *
     * @return whether snapping is enabled
     */
    public boolean isSnapping() { return snapping; }

    /**
     * Selects shared docking-region sizing or alignment at the floating size.
     *
     * @param enabled fill the allocated docking region
     * @return this window
     */
    public NanoWindow snapGrowing(boolean enabled) {
        if (snapGrowing == enabled) return this;
        snapGrowing = enabled;
        if (active == null && snapArea != WindowSnapArea.NONE) snap(snapArea);
        return this;
    }

    /**
     * Reads the region filling policy.
     *
     * @return whether snapping grows the window
     */
    public boolean isSnapGrowing() { return snapGrowing; }

    /**
     * Reads the stable ordering used by windows sharing a docking region.
     *
     * @return UI-thread creation sequence
     */
    long getDockOrder() { return dockOrder; }

    /**
     * Returns the last committed snap region.
     *
     * @return region or NONE for a floating frame
     */
    public WindowSnapArea getSnapArea() { return snapArea; }

    /**
     * Returns the floating frame retained for drag-away restoration.
     *
     * @return retained floating bounds, or current bounds before the first snap
     */
    public Frame getFloatingFrame() { return snapArea == WindowSnapArea.NONE || unsnappedFrame == null ? frame : unsnappedFrame; }

    /**
     * Restores saved floating geometry before applying a persisted snap region.
     *
     * @param floating saved floating frame
     * @param area saved workspace region
     */
    public void restoreSnap(Frame floating, WindowSnapArea area) {
        unsnappedFrame = Objects.requireNonNull(floating);
        snapArea = Objects.requireNonNull(area);
    }

    /**
     * Applies an explicit workspace region, preserving the floating size.
     *
     * @param area requested region
     */
    public void snap(WindowSnapArea area) {
        Objects.requireNonNull(area);
        if (area == WindowSnapArea.NONE) {
            if (snapArea != WindowSnapArea.NONE && unsnappedFrame != null) install(unsnappedFrame);
            snapArea = area;
            fitWorkspace();
        } else {
            Frame target = snapFrame(area);
            if (target == null) return;
            if (snapArea == WindowSnapArea.NONE) unsnappedFrame = active == titleBar ? titleBar.floatingOrigin : frame;
            install(target);
            snapArea = area;
        }
        reflowDocks();
        changed.accept(frame);
    }

    /**
     * Reallocates visible dock siblings after this window changes membership.
     * Floating frames remain untouched, including their restoration geometry.
     */
    private void reflowDocks() {
        if (getParent() == null) return;
        for (UINode child : getParent().getChildren()) {
            if (!(child instanceof NanoWindow window) || !window.isVisible() || !window.snapGrowing || window.snapArea == WindowSnapArea.NONE) continue;
            Frame region = window.snapFrame(window.snapArea);
            if (region != null && !region.equals(window.frame)) {
                window.install(region);
                window.changed.accept(region);
            }
        }
    }

    /**
     * Provides the proposed parent-local region for an external preview painter.
     *
     * @return preview rectangle, or null outside an edge target
     */
    public Frame getSnapPreview() { return pendingSnap == WindowSnapArea.NONE ? null : snapFrame(pendingSnap); }

    /**
     * Resolves a target without violating the window's minimum dimensions.
     *
     * @param area edge or corner region
     * @return eligible frame or null when the workspace is too small
     */
    private Frame snapFrame(WindowSnapArea area) {
        if (getParent() == null) return null;
        float w = getParent().getWidth();
        float h = getParent().getHeight() - movementTopInset;
        boolean west = area == WindowSnapArea.WEST || area == WindowSnapArea.NORTH_WEST || area == WindowSnapArea.SOUTH_WEST;
        boolean east = area == WindowSnapArea.EAST || area == WindowSnapArea.NORTH_EAST || area == WindowSnapArea.SOUTH_EAST;
        boolean north = area == WindowSnapArea.NORTH || area == WindowSnapArea.NORTH_EAST || area == WindowSnapArea.NORTH_WEST;
        boolean south = area == WindowSnapArea.SOUTH || area == WindowSnapArea.SOUTH_EAST || area == WindowSnapArea.SOUTH_WEST;
        if (snapGrowing) {
            Frame slot = NanoWindowDockLayout.resolve(this, this, area, movementTopInset);
            for (UINode child : getParent().getChildren()) {
                if (!(child instanceof NanoWindow window) || !window.isVisible() || !window.snapGrowing || window == this || window.snapArea == WindowSnapArea.NONE) continue;
                Frame other = NanoWindowDockLayout.resolve(window, this, area, window.movementTopInset);
                if (other.width() < window.minimumWidth || other.height() < window.minimumHeight) return null;
            }
            if (slot.width() < minimumWidth || slot.height() < minimumHeight) return null;
            float width = Math.min(slot.width(), maximumWidth);
            float height = Math.min(slot.height(), maximumHeight);
            return new Frame(east ? slot.x() + slot.width() - width : slot.x(), south ? slot.y() + slot.height() - height : slot.y(), width, height);
        }
        Frame floating = getFloatingFrame();
        float width = Math.min(w, Math.clamp(floating.width(), minimumWidth, maximumWidth));
        float height = Math.min(h, Math.clamp(floating.height(), minimumHeight, maximumHeight));
        if (width < minimumWidth || height < minimumHeight) return null;
        return new Frame(east ? w - width : west ? 0 : Math.clamp(frame.x(), 0, w - width),
                south ? movementTopInset + h - height : north ? movementTopInset : Math.clamp(frame.y(), movementTopInset, movementTopInset + h - height), width, height);
    }

    /**
     * Proposes docking only when the gesture pushes a contacted edge outward.
     * Sliding along an edge or restoring a tall window cannot resnap it by accident.
     *
     * @param dx horizontal displacement from the floating gesture anchor
     * @param dy vertical displacement from the floating gesture anchor
     */
    private void previewSnap(float dx, float dy) {
        pendingSnap = WindowSnapArea.NONE;
        if (!snapping || getParent() == null) return;
        boolean west = frame.x() <= 0f;
        boolean east = frame.x() + frame.width() >= getParent().getWidth();
        boolean north = frame.y() <= movementTopInset;
        boolean south = frame.y() + frame.height() >= getParent().getHeight();
        boolean pushedWest = west && dx < 0;
        boolean pushedEast = east && dx > 0;
        boolean pushedNorth = north && dy < 0;
        boolean pushedSouth = south && dy > 0;
        if (!pushedWest && !pushedEast && !pushedNorth && !pushedSouth) return;
        if (west && east) { west = pushedWest; east = pushedEast; }
        if (north && south) { north = pushedNorth; south = pushedSouth; }
        pendingSnap = north ? (west ? WindowSnapArea.NORTH_WEST : east ? WindowSnapArea.NORTH_EAST : WindowSnapArea.NORTH)
                : south ? (west ? WindowSnapArea.SOUTH_WEST : east ? WindowSnapArea.SOUTH_EAST : WindowSnapArea.SOUTH)
                : west ? WindowSnapArea.WEST : east ? WindowSnapArea.EAST : WindowSnapArea.NONE;
    }

    /**
     * Uses the last committed frame; raising a window temporarily resets Yoga's computed
     * coordinates until the next layout pass, including during the first drag press.
     * @return outer frame at the start of a user operation
     */
    private Frame computedFrame() { return frame; }

    /**
     * Reserves a strip above this window for persistent parent chrome.
     * Applies to programmatic bounds, pointer gestures, and keyboard movement.
     *
     * @param inset nonnegative finite height in parent layout units
     * @return this window
     */
    public NanoWindow movementTopInset(float inset) {
        if (!Float.isFinite(inset) || inset < 0f) throw new IllegalArgumentException("Invalid movement inset");
        movementTopInset = inset;
        Frame current = getFrame();
        return bounds(current.x(), current.y(), current.width(), current.height());
    }

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
                if (vertical < 0 && y < movementTopInset) { height = Math.max(minimumHeight, height + y - movementTopInset); y = origin.y() + origin.height() - height; }
                if (horizontal > 0) width = Math.max(minimumWidth, Math.min(width, availableWidth - x));
                if (vertical > 0) height = Math.max(minimumHeight, Math.min(height, availableHeight - y));
            }
            x = Math.clamp(x, 0, Math.max(0, availableWidth - width));
            y = Math.clamp(y, movementTopInset, Math.max(movementTopInset, availableHeight - height));
        }
        y = Math.max(movementTopInset, y);
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
        if (getParent() != null && (parentWidth != getParent().getWidth() || parentHeight != getParent().getHeight())) {
            parentWidth = getParent().getWidth();
            parentHeight = getParent().getHeight();
            if (fitWorkspace()) {width = frame.width(); height = frame.height();}
        }
        if (width == layoutWidth && height == layoutHeight) return;
        layoutWidth = width; layoutHeight = height;
        place(titleBar, FRAME_INSET, FRAME_INSET, width - FRAME_INSET * 2, TITLE_HEIGHT);
        place(titleViewport, 0, 0, width - FRAME_INSET * 2 - CLOSE_WIDTH, TITLE_HEIGHT);
        place(closeButton, width - FRAME_INSET - CLOSE_WIDTH, FRAME_INSET, CLOSE_WIDTH, TITLE_HEIGHT);
        place(divider, FRAME_INSET, FRAME_INSET + TITLE_HEIGHT, width - FRAME_INSET * 2, 1);
        place(viewport, FRAME_INSET, FRAME_INSET + TITLE_HEIGHT + 1,
                width - FRAME_INSET * 2, height - FRAME_INSET * 2 - TITLE_HEIGHT - 1);
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
    @Override public void onDestroy() { cancelGesture(); layoutWidth = layoutHeight = parentWidth = parentHeight = -1; }

    /**
     * Gives the frame a soft outer shadow while preserving its normal themed fill and children.
     */
    @Override public void draw(long vg) {
        if (vg != 0L && getWidth() > 0 && getHeight() > 0) {
            float x = getAbsoluteX(), y = getAbsoluteY();
            Float shadowRadius = getStyle() == null ? null : getStyle().get(SHADOW_CORNER_RADIUS_KEY);
            NVGPaint shadow = NVGPaint.calloc();
            try {
                nvgBoxGradient(vg, x, y + 4, getWidth(), getHeight(),
                        Math.min(8f, shadowRadius == null ? SHADOW_CORNER_RADIUS_KEY.getDefaultValue() : shadowRadius), 16,
                        NanoUtility.color1(SHADOW_INNER), NanoUtility.color2(SHADOW_OUTER), shadow);
                nvgBeginPath(vg);
                nvgRoundedRect(vg, x - 18, y - 14, getWidth() + 36, getHeight() + 40,
                        Math.max(0f, shadowRadius == null ? SHADOW_CORNER_RADIUS_KEY.getDefaultValue() : shadowRadius));
                nvgFillPaint(vg, shadow);
                nvgFill(vg);
            } finally {
                shadow.free();
            }
        }
        super.draw(vg);
    }

    /**
     * Captured move/resize target. A zero direction pair identifies the title bar;
     * edge and corner targets preserve their opposite boundaries when size limits apply.
     * @author Albert Beaupre
     */
    private final class Grip extends NanoButton {
        private Frame floatingOrigin; // Floating restoration bounds retained before the current title drag.
        private boolean restoredForDrag; // Restores a movable floating size only once per title gesture.
        private Frame pressedFrame; // Original outer geometry restored by Escape during a gesture.
        private WindowSnapArea pressedArea; // Committed region retained before movement or resizing starts.
        private final int horizontal, vertical; // Edge directions, with (0,0) identifying window movement.
        private Frame origin; // Original computed frame for the current pointer gesture.
        private float startX, startY; // Parent-local pointer coordinates at initial press.

        /**
         * Creates one direction-aware pointer and keyboard manipulation target.
         * @param horizontal horizontal resize direction or zero
         * @param vertical vertical resize direction or zero
         */
        private Grip(int horizontal, int vertical) { this.horizontal = horizontal; this.vertical = vertical; }

        @Override
        public void draw(long vg) {
            if (horizontal == 0 && vertical == 0) super.draw(vg);
        }

        /**
         * Checks the enclosing window policy rather than treating hidden grips as active.
         * @return true when this enabled target may begin or continue a gesture
         */
        private boolean allowed() { return !NanoWindow.this.isDisabled() && !isDisabled() && (this == titleBar ? draggable : resizable); }

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
            if (!allowed() || event.getButton() != Mouse.LEFT || NanoWindow.this.getParent() == null) return;
            var point = NanoWindow.this.getParent().screenToLocal(event.getX(), event.getY());
            floatingOrigin = getFloatingFrame();
            pressedFrame = computedFrame();
            pressedArea = snapArea;
            restoredForDrag = false;
            if (this != titleBar) snapArea = WindowSnapArea.NONE;
            origin = computedFrame(); startX = point.x(); startY = point.y(); active = this; setDragging(true); event.consume();
        }

        /**
         * Applies captured displacement in parent coordinates, avoiding feedback as the
         * window itself moves. The root handles viewport scaling and pointer capture.
         * @param event routed drag destination
         */
        @Override public void onMouseDrag(MouseDragEvent event) {
            if (active != this || !allowed() || event.getButton() != Mouse.LEFT) return;
            movePointer(event.getToX(), event.getToY());
            event.consume();
        }

        /**
         * Applies the current captured pointer to the original gesture anchor.
         * Snapped frames restore their floating size beneath the actual grab
         * point; release uses this same path so its preview cannot be stale.
         *
         * @param x pointer X in screen coordinates
         * @param y pointer Y in screen coordinates
         */
        private void movePointer(float x, float y) {
            var point = NanoWindow.this.getParent().screenToLocal(x, y);
            if (this == titleBar && !restoredForDrag && point.x() == startX && point.y() == startY) {
                pendingSnap = WindowSnapArea.NONE;
                return;
            }
            if (this == titleBar && !restoredForDrag) {
                restoredForDrag = true;
                float availableWidth = NanoWindow.this.getParent().getWidth();
                float availableHeight = NanoWindow.this.getParent().getHeight() - movementTopInset;
                Frame floating = floatingOrigin;
                boolean restore = snapArea != WindowSnapArea.NONE;
                float width = floating.width();
                float height = floating.height();
                if (keepWithinParent && width >= availableWidth) {
                    width = Math.clamp(availableWidth * .75f, minimumWidth, maximumWidth);
                    restore = true;
                }
                if (keepWithinParent && height >= availableHeight) {
                    height = Math.clamp(availableHeight * .75f, minimumHeight, maximumHeight);
                    restore = true;
                }
                if (restore) {
                    float fraction = Math.clamp((startX - origin.x()) / origin.width(), 0f, 1f);
                    float titleOffset = startY - origin.y();
                    install(new Frame(startX - width * fraction, Math.max(movementTopInset, startY - titleOffset), width, height));
                    snapArea = WindowSnapArea.NONE;
                    origin = computedFrame();
                    reflowDocks();
                }
            }
            float dx = point.x() - startX;
            float dy = point.y() - startY;
            transform(origin, horizontal, vertical, dx, dy);
            if (this == titleBar) previewSnap(dx, dy);
        }

        /**
         * Ends a primary gesture without invoking ordinary button activation.
         * @param event routed mouse release
         */
        @Override public void onMouseRelease(MouseReleaseEvent event) {
            if (event.getButton() == Mouse.LEFT) {
                if (active == this) {
                    if (allowed()) movePointer(event.getX(), event.getY());
                    if (this == titleBar && pendingSnap != WindowSnapArea.NONE) snap(pendingSnap);
                    cancelGesture();
                }
                event.consume();
            }
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
            if (event.getKey() == Keyboard.ESCAPE && active == this) {
                install(pressedFrame);
                snapArea = pressedArea;
                unsnappedFrame = floatingOrigin;
                reflowDocks();
                cancelGesture();
                changed.accept(frame);
                event.consume();
                return;
            }
            float step = event.isShiftDown() ? 32 : 8, dx = 0, dy = 0;
            switch (event.getKey()) {
                case Keyboard.LEFT -> dx = -step;
                case Keyboard.RIGHT -> dx = step;
                case Keyboard.UP -> dy = -step;
                case Keyboard.DOWN -> dy = step;
                default -> { return; }
            }
            if (this != titleBar) { if (horizontal == 0) dx = 0; if (vertical == 0) dy = 0; }
            cancelGesture(); snapArea = WindowSnapArea.NONE; transform(computedFrame(), horizontal, vertical, dx, dy); event.consume();
        }
    }
}
