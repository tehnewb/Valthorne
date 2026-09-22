package valthorne.ui.nodes.nano;

import valthorne.Keyboard;
import valthorne.Mouse;
import valthorne.event.events.KeyPressEvent;
import valthorne.event.events.MouseDragEvent;
import valthorne.event.events.MousePressEvent;
import valthorne.event.events.MouseReleaseEvent;
import valthorne.ui.UINode;
import valthorne.ui.behavior.ChangeSignal;

import java.util.Objects;

/** NanoVG-backed two-pane splitter. */
public class NanoSplitPane extends NanoPanel {
    private final NanoPanel first = new NanoPanel(), second = new NanoPanel();
    private final Divider divider = new Divider();
    private final ChangeSignal changes = new ChangeSignal();
    private boolean vertical;
    private float ratio = .5f, thickness = 8, minimumFirst, minimumSecond;
    private float available, firstSize, dragOffset;
    private float lastWidth = -1, lastHeight = -1;

    public NanoSplitPane(UINode first, UINode second) {
        Objects.requireNonNull(first); Objects.requireNonNull(second);
        if (first == second || first.getParent() != null || second.getParent() != null)
            throw new IllegalArgumentException("Panes must be distinct unattached nodes");
        this.first.add(first); this.second.add(second);
        first.getLayout().widthPercent(100).heightPercent(100);
        second.getLayout().widthPercent(100).heightPercent(100);
        super.add(this.first); super.add(this.second); super.add(divider);
    }

    public NanoPanel first() { return first; }
    public NanoPanel second() { return second; }
    public NanoButton getDivider() { return divider; }
    public float getRatio() { return ratio; }
    public float getEffectiveRatio() { return available <= 0 ? 0 : firstSize / available; }
    public AutoCloseable onChange(Runnable listener) { return changes.subscribe(listener); }

    public NanoSplitPane ratio(float value) {
        if (!Float.isFinite(value)) throw new IllegalArgumentException("Nonfinite ratio");
        value = Math.clamp(value, 0, 1);
        if (ratio != value) { ratio = value; invalidateGeometry(); changes.fire(); }
        return this;
    }

    public NanoSplitPane vertical(boolean value) {
        if (vertical != value) { vertical = value; divider.setDragging(false); invalidateGeometry(); }
        return this;
    }

    public NanoSplitPane dividerSize(float pixels) {
        validateSize(pixels);
        if (pixels < 1) throw new IllegalArgumentException("Divider must be at least one pixel");
        if (thickness != pixels) { thickness = pixels; invalidateGeometry(); }
        return this;
    }

    public NanoSplitPane minimumSizes(float first, float second) {
        validateSize(first); validateSize(second);
        if (minimumFirst != first || minimumSecond != second) {
            minimumFirst = first; minimumSecond = second; invalidateGeometry();
        }
        return this;
    }

    private static void validateSize(float value) {
        if (!Float.isFinite(value) || value < 0) throw new IllegalArgumentException("Invalid size");
    }

    private static void place(UINode node, float x, float y, float width, float height) {
        node.getLayout().absolute().left(x).top(y).width(width).height(height).minSize(0, 0).padding(0);
    }

    private void invalidateGeometry() { lastWidth = -1; markLayoutDirty(); }

    @Override protected void afterLayout() {
        super.afterLayout();
        float width = getWidth(), height = getHeight();
        if (width == lastWidth && height == lastHeight) return;
        lastWidth = width; lastHeight = height;
        float size = vertical ? height : width;
        float bar = Math.min(size, thickness);
        available = Math.max(0, size - bar);
        double minimum = (double) minimumFirst + minimumSecond;
        firstSize = minimum > available ? (float) (available * (minimumFirst / minimum))
                : Math.clamp(available * ratio, minimumFirst, available - minimumSecond);
        if (vertical) {
            place(first, 0, 0, width, firstSize); place(divider, 0, firstSize, width, bar);
            place(second, 0, firstSize + bar, width, available - firstSize);
        } else {
            place(first, 0, 0, firstSize, height); place(divider, firstSize, 0, bar, height);
            place(second, firstSize + bar, 0, available - firstSize, height);
        }
    }

    private float pointer(float x, float y) {
        var point = screenToLocal(x, y);
        return vertical ? point.y() : point.x();
    }

    private void position(float desired) {
        if (available <= 0 || (double) minimumFirst + minimumSecond >= available) return;
        ratio(Math.clamp(desired, minimumFirst, available - minimumSecond) / available);
    }

    private final class Divider extends NanoButton {
        Divider() { super(""); }
        @Override public void onMousePress(MousePressEvent event) {
            if (isDisabled() || event.getButton() != Mouse.LEFT) return;
            setDragging(true); dragOffset = pointer(event.getX(), event.getY()) - firstSize; event.consume();
        }
        @Override public void onMouseDrag(MouseDragEvent event) {
            if (!isDragging() || event.getButton() != Mouse.LEFT) return;
            position(pointer(event.getToX(), event.getToY()) - dragOffset); event.consume();
        }
        @Override public void onMouseRelease(MouseReleaseEvent event) {
            if (event.getButton() == Mouse.LEFT) { setDragging(false); setPressed(false); event.consume(); }
        }
        @Override public void onPointerCancel() { setDragging(false); }
        @Override public void onKeyPress(KeyPressEvent event) {
            if (isDisabled()) return;
            int key = event.getKey(), previous = vertical ? Keyboard.UP : Keyboard.LEFT;
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
