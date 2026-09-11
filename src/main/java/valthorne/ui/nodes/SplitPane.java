package valthorne.ui.nodes;

import valthorne.Keyboard;
import valthorne.Mouse;
import valthorne.event.events.KeyPressEvent;
import valthorne.event.events.MouseDragEvent;
import valthorne.event.events.MousePressEvent;
import valthorne.event.events.MouseReleaseEvent;
import valthorne.ui.UINode;
import valthorne.ui.behavior.ChangeSignal;

import java.util.Objects;

/**
 * Two mixed-renderer panes separated by a captured, keyboard-accessible divider.
 * Vertical means top/bottom; horizontal means left/right. When minimum sizes cannot
 * fit, available space is shared proportionally. Requested ratio survives resizing.
 * Manage pane contents through first()/second(), not inherited structural methods.
 * Initial layout is horizontal with ratio 0.5, divider thickness eight layout units,
 * and zero minimum sizes. Ratio notifications run synchronously on the UI thread;
 * geometry changes take effect through a subsequent layout pass.
 *
 * <pre>{@code
 * SplitPane split = new SplitPane(new Panel(), new Panel());
 * split.ratio(0.3f).minimumSizes(120, 200).dividerSize(8);
 * split.getLayout().width(800).height(600);
 * }</pre>
 *
 * @author Albert Beaupre
 */
public class SplitPane extends Panel {
    private final Panel first = new Panel(), second = new Panel(); // Owned containers for the two user-supplied pane contents.
    private final Divider divider = new Divider(); // Owned input control positioned between the pane containers.
    private final ChangeSignal changes = new ChangeSignal(); // Synchronous requested-ratio change notifications.
    private boolean vertical; // True splits top/bottom; false splits left/right.
    private float ratio = .5f, thickness = 8, minimumFirst, minimumSecond; // Requested first-pane fraction, divider extent, and minimum pane extents.
    private float available, firstSize, dragOffset; // Last layout's content extent, first-pane size, and pointer grab offset.
    private float lastWidth = -1, lastHeight = -1; // Cached layout dimensions; negative width forces recomputation.

    /**
     * Attaches distinct, unattached nodes to owned pane containers and sizes their
     * layouts to fill those containers. The supplied nodes become part of this pane's
     * lifecycle; later content management should use first() and second().
     *
     * @param first initial content of the first pane
     * @param second initial content of the second pane
     * @throws NullPointerException if either node is null
     * @throws IllegalArgumentException if nodes are identical or already attached
     */
    public SplitPane(UINode first, UINode second) {
        Objects.requireNonNull(first);
        Objects.requireNonNull(second);
        if (first == second || first.getParent() != null || second.getParent() != null)
            throw new IllegalArgumentException("Panes must be distinct unattached nodes");
        this.first.add(first);
        this.second.add(second);
        first.getLayout().widthPercent(100).heightPercent(100);
        second.getLayout().widthPercent(100).heightPercent(100);
        super.add(this.first);
        super.add(this.second);
        super.add(divider);
    }

    /**
     * Checks a layout extent before configuration is changed. Zero is accepted here;
     * divider thickness imposes its additional minimum separately.
     *
     * @param value extent in layout units
     * @throws IllegalArgumentException if value is negative or non-finite
     */
    private static void validateSize(float value) {
        if (!Float.isFinite(value) || value < 0) throw new IllegalArgumentException("Invalid size");
    }

    /**
     * Sets absolute placement and size for an internal child. Resets minimum size and
     * padding to zero so child defaults cannot change the computed split geometry.
     *
     * @param node child to position
     * @param x parent-local left coordinate
     * @param y parent-local top coordinate
     * @param width horizontal extent
     * @param height vertical extent
     */
    private static void place(UINode node, float x, float y, float width, float height) {
        node.getLayout().absolute().left(x).top(y).width(width).height(height).minSize(0, 0).padding(0);
    }

    /**
     * Returns the owned first-pane container for content management. It occupies the
     * left side in horizontal mode or the top in vertical mode.
     *
     * @return live first-pane container
     */
    public Panel first() {return first;}

    /**
     * Returns the owned second-pane container for content management. It occupies the
     * right side in horizontal mode or the bottom in vertical mode.
     *
     * @return live second-pane container
     */
    public Panel second() {return second;}

    /**
     * Returns the live divider for styling and enabled-state customization. Its input
     * callbacks implement split resizing rather than ordinary button activation.
     *
     * @return owned divider control
     */
    public Button getDivider() {return divider;}

    /**
     * Returns the requested first-pane fraction, independent of minimum-size constraints
     * and current layout. This value survives container resizing.
     *
     * @return requested fraction in the inclusive range zero through one
     */
    public float getRatio() {return ratio;}

    /**
     * Returns the first-pane fraction computed by the last layout, excluding divider
     * thickness. May differ from the requested ratio when minimum sizes constrain it.
     *
     * @return actual first-pane fraction, or zero when no content space is available
     */
    public float getEffectiveRatio() {return available <= 0 ? 0 : firstSize / available;}

    /**
     * Registers a synchronous callback for changes to the requested ratio. Geometry
     * may not have been laid out when the callback runs; orientation and size changes
     * alone do not fire this signal.
     *
     * @param listener callback to subscribe
     * @return handle whose close operation removes the subscription
     */
    public AutoCloseable onChange(Runnable listener) {return changes.subscribe(listener);}

    /**
     * Clamps a finite requested fraction to zero through one. A changed value invalidates
     * geometry and immediately notifies listeners; an unchanged value does neither.
     * Minimum sizes constrain the effective geometry without replacing this request.
     *
     * @param value desired fraction of content space assigned to the first pane
     * @return this split pane
     * @throws IllegalArgumentException if value is non-finite
     */
    public SplitPane ratio(float value) {
        if (!Float.isFinite(value)) throw new IllegalArgumentException("Nonfinite ratio");
        value = Math.clamp(value, 0, 1);
        if (ratio != value) {
            ratio = value;
            invalidateGeometry();
            changes.fire();
        }
        return this;
    }

    /**
     * Changes orientation and clears divider dragging when the value changes. Marks
     * geometry dirty while preserving ratio and minimum sizes; does not fire a ratio
     * change notification.
     *
     * @param value true for top/bottom, false for left/right
     * @return this split pane
     */
    public SplitPane vertical(boolean value) {
        if (vertical != value) {
            vertical = value;
            divider.setDragging(false);
            invalidateGeometry();
        }
        return this;
    }

    /**
     * Stores a finite divider extent of at least one layout unit and invalidates changed
     * geometry. Layout caps the actual divider to the available container extent.
     *
     * @param pixels divider thickness in layout units
     * @return this split pane
     * @throws IllegalArgumentException if pixels is non-finite or less than one
     */
    public SplitPane dividerSize(float pixels) {
        validateSize(pixels);
        if (pixels < 1) throw new IllegalArgumentException("Divider must be at least one pixel");
        if (thickness != pixels) {
            thickness = pixels;
            invalidateGeometry();
        }
        return this;
    }

    /**
     * Sets finite nonnegative minimum extents along the split axis. If their sum exceeds
     * available content space, layout distributes that space proportionally instead.
     * Changed minima invalidate geometry without changing the requested ratio.
     *
     * @param first minimum first-pane extent in layout units
     * @param second minimum second-pane extent in layout units
     * @return this split pane
     * @throws IllegalArgumentException if either extent is negative or non-finite
     */
    public SplitPane minimumSizes(float first, float second) {
        validateSize(first);
        validateSize(second);
        if (minimumFirst != first || minimumSecond != second) {
            minimumFirst = first;
            minimumSecond = second;
            invalidateGeometry();
        }
        return this;
    }

    /**
     * Invalidates cached dimensions and marks layout dirty so the next layout callback
     * recomputes internal geometry even if the outer dimensions have not changed.
     */
    private void invalidateGeometry() {
        lastWidth = -1;
        markLayoutDirty();
    }

    /**
     * Repositions the two containers and divider after normal panel layout. Unchanged
     * cached dimensions skip computation. Divider space is removed before applying
     * the requested ratio and minima; insufficient space is shared proportionally.
     */
    @Override
    protected void afterLayout() {
        super.afterLayout();
        float width = getWidth(), height = getHeight();
        if (width == lastWidth && height == lastHeight) return;
        lastWidth = width;
        lastHeight = height;
        float size = vertical ? height : width;
        float bar = Math.min(size, thickness);
        available = Math.max(0, size - bar);
        double minimum = (double) minimumFirst + minimumSecond;
        firstSize = minimum > available ? (float) (available * (minimumFirst / minimum))
                : Math.clamp(available * ratio, minimumFirst, available - minimumSecond);
        if (vertical) {
            place(first, 0, 0, width, firstSize);
            place(divider, 0, firstSize, width, bar);
            place(second, 0, firstSize + bar, width, available - firstSize);
        } else {
            place(first, 0, 0, firstSize, height);
            place(divider, firstSize, 0, bar, height);
            place(second, firstSize + bar, 0, available - firstSize, height);
        }
    }

    /**
     * Converts a screen point into this pane's local coordinates and selects the active
     * split-axis coordinate. Uses the node's current viewport and layout mapping.
     *
     * @param x screen X coordinate
     * @param y screen Y coordinate
     * @return local X in horizontal mode, or local Y in vertical mode
     */
    private float pointer(float x, float y) {
        var point = screenToLocal(x, y);
        return vertical ? point.y() : point.x();
    }

    /**
     * Converts a desired first-pane extent into a constrained requested ratio. Does
     * nothing when content space is zero or minima leave no resizing room. Otherwise
     * delegates notification and invalidation to ratio(float).
     *
     * @param desired desired first-pane extent in layout units
     */
    private void position(float desired) {
        if (available <= 0 || (double) minimumFirst + minimumSecond >= available) return;
        ratio(Math.clamp(desired, minimumFirst, available - minimumSecond) / available);
    }

    /**
     * Implements pointer dragging and axis-aware keyboard resizing for its enclosing
     * split pane. The root provides input routing and capture; this control retains
     * dragging state while the enclosing pane stores the grab offset.
     *
     * @author Albert Beaupre
     */
    private final class Divider extends Button {
        /**
         * Begins dragging for an enabled left-button press, preserving the pointer's offset
         * from the first-pane boundary. Consumes accepted presses; other presses are ignored.
         *
         * @param event routed mouse press
         */
        @Override
        public void onMousePress(MousePressEvent event) {
            if (isDisabled() || event.getButton() != Mouse.LEFT) return;
            setDragging(true);
            dragOffset = pointer(event.getX(), event.getY()) - firstSize;
            event.consume();
        }

        /**
         * Moves the divider from a left-button drag while dragging is active. Applies the
         * stored grab offset and minimum constraints, then consumes the event.
         *
         * @param event routed drag with current destination coordinates
         */
        @Override
        public void onMouseDrag(MouseDragEvent event) {
            if (!isDragging() || event.getButton() != Mouse.LEFT) return;
            position(pointer(event.getToX(), event.getToY()) - dragOffset);
            event.consume();
        }

        /**
         * Ends dragging and clears pressed state for a left-button release, consuming it
         * even if no drag is currently active. Other buttons are ignored.
         *
         * @param event routed mouse release
         */
        @Override
        public void onMouseRelease(MouseReleaseEvent event) {
            if (event.getButton() == Mouse.LEFT) {
                setDragging(false);
                setPressed(false);
                event.consume();
            }
        }

        /**
         * Clears dragging after routed pointer cancellation. Leaves the current split ratio
         * and geometry intact.
         */
        @Override
        public void onPointerCancel() {setDragging(false);}

        /**
         * Resizes an enabled divider with the orientation's arrow keys, Home, or End.
         * Arrow steps are eight layout units, or 32 with Shift. Home and End request the
         * minimum and maximum permitted positions. Recognized keys are consumed even
         * when constraints prevent movement; other keys are ignored.
         *
         * @param event routed divider key press
         */
        @Override
        public void onKeyPress(KeyPressEvent event) {
            if (isDisabled()) return;
            int key = event.getKey();
            int previous = vertical ? Keyboard.UP : Keyboard.LEFT;
            int next = vertical ? Keyboard.DOWN : Keyboard.RIGHT;
            float step = event.isShiftDown() ? 32 : 8;
            if (key == previous) position(firstSize - step);
            else if (key == next) position(firstSize + step);
            else if (key == Keyboard.HOME) position(0);
            else if (key == Keyboard.END) position(available);
            else return;
            event.consume();
        }
    }
}
