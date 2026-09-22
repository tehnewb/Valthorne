package valthorne.ui.nodes.nano;

import valthorne.Keyboard;
import valthorne.Mouse;
import valthorne.event.events.KeyPressEvent;
import valthorne.event.events.MousePressEvent;
import valthorne.ui.UIInputEvent;
import valthorne.ui.UINode;
import valthorne.ui.behavior.RowHeightIndex;
import valthorne.ui.behavior.SelectionModel;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.IntFunction;

/** NanoVG scroll-panel variant of the virtualized list/grid component. */
public class NanoVirtualList extends NanoScrollPanel {
    private final NanoPanel rows = new NanoPanel();
    private final Map<Integer, UINode> live = new LinkedHashMap<>();
    private final IntFunction<? extends UINode> factory;
    private int itemCount, columns = 1, overscan = 2, first = -1, last = -1;
    private float rowHeight = 40, gap = 4, lastWidth = -1, pendingScroll = Float.NaN;
    private RowHeightIndex heights;
    private SelectionModel selection;

    public NanoVirtualList(int itemCount, IntFunction<? extends UINode> factory) {
        this.factory = Objects.requireNonNull(factory);
        if (itemCount < 0) throw new IllegalArgumentException("Negative item count");
        this.itemCount = itemCount;
        horizontal(false); horizontalBar(false);
        rows.getLayout().widthPercent(100).noShrink();
        super.setContent(rows); updateExtent();
    }

    private static boolean contains(UINode ancestor, UINode node) {
        for (; node != null; node = node.getParent()) if (node == ancestor) return true;
        return false;
    }

    public int getItemCount() { return itemCount; }
    public int getLiveItemCount() { return live.size(); }
    public SelectionModel getSelection() { return selection; }
    public UINode getItemNode(int index) { return live.get(index); }
    public int getColumns() { return columns; }
    public boolean hasVariableHeights() { return heights != null; }

    public NanoVirtualList selectable(boolean multiple) {
        if (selection == null) {
            selection = new SelectionModel(itemCount, multiple);
            selection.onChange(() -> live.forEach((index, node) -> node.setSelected(selection.isSelected(index))));
            setFocusable(true);
        } else selection.multiple(multiple);
        return this;
    }

    @Override public void onInputPreview(UIInputEvent context) {
        super.onInputPreview(context);
        if (selection == null || !(context.event() instanceof MousePressEvent press) || press.getButton() != Mouse.LEFT) return;
        for (var entry : live.entrySet()) if (contains(entry.getValue(), context.target())) {
            selection.select(entry.getKey(), press.isShiftDown(), press.isCtrlDown() || press.isSuperDown()); return;
        }
    }

    @Override public void onKeyPress(KeyPressEvent event) {
        if (selection == null || itemCount == 0 || isDisabled()) return;
        boolean control = event.isCtrlDown() || event.isSuperDown();
        int key = event.getKey();
        if (key == Keyboard.A && control) { selection.selectAll(); event.consume(); return; }
        int index = selection.lead(), next;
        if (key == Keyboard.HOME) next = 0;
        else if (key == Keyboard.END) next = itemCount - 1;
        else if (key == Keyboard.DOWN) next = index < 0 ? 0 : (int) Math.min(itemCount - 1L, (long) index + columns);
        else if (key == Keyboard.UP) next = index < 0 ? 0 : Math.max(0, index - columns);
        else if (key == Keyboard.RIGHT && columns > 1) next = Math.min(itemCount - 1, index + 1);
        else if (key == Keyboard.LEFT && columns > 1) next = Math.max(0, index - 1);
        else return;
        selection.select(next, event.isShiftDown(), control && event.isShiftDown());
        scrollToIndex(next);
        if (getRoot() != null) getRoot().setFocusTo(this);
        event.consume();
    }

    public NanoVirtualList columns(int value) {
        if (value < 1) throw new IllegalArgumentException("Columns must be positive");
        if (heights != null && value != 1) throw new IllegalStateException("Variable heights require one column");
        if (columns != value) { columns = value; invalidateItems(); }
        return this;
    }

    public NanoVirtualList rowHeight(float value) {
        if (!Float.isFinite(value) || value <= 0 || !Float.isFinite(value + gap)) throw new IllegalArgumentException("Invalid row height");
        if (rowHeight != value || heights != null) { heights = null; rowHeight = value; invalidateItems(); }
        return this;
    }

    public NanoVirtualList gap(float value) {
        if (!Float.isFinite(value) || value < 0 || !Float.isFinite(rowHeight + value)) throw new IllegalArgumentException("Invalid gap");
        if (gap == value) return this;
        if (heights != null) {
            for (int i = 0; i < heights.size(); i++) if (!Float.isFinite(heights.height(i) - gap + value)) throw new IllegalArgumentException("Row stride overflow");
            for (int i = 0; i < heights.size(); i++) heights.set(i, heights.height(i) - gap + value);
        }
        gap = value; invalidateItems(); return this;
    }

    public NanoVirtualList itemCount(int count) {
        if (count < 0) throw new IllegalArgumentException("Negative item count");
        if (heights != null) heights.resize(count, rowHeight + gap);
        itemCount = count;
        if (selection != null) selection.itemCount(count);
        refreshItems(); return this;
    }

    public NanoVirtualList overscan(int value) {
        if (value < 0) throw new IllegalArgumentException("Negative overscan");
        if (overscan != value) { overscan = value; invalidateItems(); }
        return this;
    }

    public void refreshItems() {
        if (getRoot() != null && (owns(getRoot().getFocused()) || owns(getRoot().getCaptured()))) getRoot().cancelInput();
        rows.clear(); live.clear(); invalidateItems();
    }

    private boolean owns(UINode node) {
        for (; node != null; node = node.getParent()) if (node == rows) return true;
        return false;
    }

    private void invalidateItems() { first = last = -1; updateExtent(); markLayoutDirty(); }

    public NanoVirtualList variableHeights() {
        if (columns != 1) throw new IllegalStateException("Variable heights require one column");
        if (heights == null) { heights = new RowHeightIndex(itemCount, rowHeight + gap); invalidateItems(); }
        return this;
    }

    public NanoVirtualList itemHeight(int index, float value) {
        Objects.checkIndex(index, itemCount);
        if (!Float.isFinite(value) || value <= 0 || !Float.isFinite(value + gap)) throw new IllegalArgumentException("Invalid item height");
        variableHeights();
        float stride = value + gap;
        if (heights.height(index) == stride) return this;
        float scroll = Float.isNaN(pendingScroll) ? getScrollY() : pendingScroll;
        int anchor = heights.indexAt(scroll);
        float offset = (float) (scroll - heights.offset(anchor));
        heights.set(index, stride); pendingScroll = (float) heights.offset(anchor) + offset;
        invalidateItems(); return this;
    }

    public float getItemHeight(int index) {
        Objects.checkIndex(index, itemCount);
        return heights == null ? rowHeight : heights.height(index) - gap;
    }

    private void updateExtent() {
        long count = ((long) itemCount + columns - 1) / columns;
        double extent = (heights == null ? count * (double) (rowHeight + gap) : heights.totalHeight()) - gap;
        rows.getLayout().height((float) Math.min(Float.MAX_VALUE, Math.max(0, extent)));
    }

    public void scrollToIndex(int index) {
        if (index < 0 || index >= itemCount) throw new IndexOutOfBoundsException(index);
        if (getRoot() != null && isLayoutDirty()) getRoot().layout();
        scrollY((float) (heights == null ? (index / columns) * (double) (rowHeight + gap) : heights.offset(index)));
        synchronizeRows();
    }

    @Override public void update(float delta) { synchronizeRows(); super.update(delta); }

    @Override protected void afterLayout() {
        super.afterLayout();
        if (!Float.isNaN(pendingScroll)) { scrollY(pendingScroll); pendingScroll = Float.NaN; }
        synchronizeRows();
    }

    private void synchronizeRows() {
        if (getRoot() == null || getHeight() <= 0) return;
        long startRow = heights == null ? (long) (getScrollY() / (rowHeight + gap)) : heights.indexAt(getScrollY());
        long endRow = heights == null ? (long) Math.ceil(((double) getScrollY() + getHeight()) / (rowHeight + gap)) : (long) heights.indexAt((double) getScrollY() + getHeight()) + 1;
        int nextFirst = (int) Math.min(itemCount, Math.max(0, startRow - overscan) * columns);
        int nextLast = (int) Math.min(itemCount, (Math.min(itemCount, endRow) + overscan) * columns);
        float width = Math.max(0, getWidth() - (getMaxScrollY() > 0 ? getVerticalBarWidth() : 0));
        boolean extra = live.size() > nextLast - nextFirst;
        if (first == nextFirst && last == nextLast && lastWidth == width && !extra) return;
        first = nextFirst; last = nextLast; lastWidth = width;
        var iterator = live.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (entry.getKey() >= first && entry.getKey() < last) continue;
            if (contains(entry.getValue(), getRoot().getFocused()) || contains(entry.getValue(), getRoot().getCaptured())) continue;
            rows.remove(entry.getValue()); iterator.remove();
        }
        float cellWidth = Math.max(0, (width - (columns - 1) * gap) / columns);
        for (int index = first; index < last; index++) {
            UINode node = live.get(index);
            if (node == null) {
                node = Objects.requireNonNull(factory.apply(index), "Item factory returned null");
                if (node.getParent() != null || live.containsValue(node)) throw new IllegalArgumentException("Factory must return a fresh, unattached node");
                live.put(index, node); rows.add(node);
                if (selection != null) node.setSelected(selection.isSelected(index));
            }
            node.getLayout().absolute().left((index % columns) * (cellWidth + gap))
                    .top((float) (heights == null ? (index / columns) * (double) (rowHeight + gap) : heights.offset(index)))
                    .width(cellWidth).height(getItemHeight(index)).noGrow().noShrink();
        }
    }
}
