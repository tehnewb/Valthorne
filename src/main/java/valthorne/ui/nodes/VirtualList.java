package valthorne.ui.nodes;

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

/**
 * Fixed-height virtualized list/grid or measured variable-height single-column list.
 * Factories can return either renderer's nodes.
 * Only visible items, overscan and any focused/captured item remain attached.
 * Store durable item state in your data model, not in a transient row widget.
 * Default geometry uses one column, 40-unit rows, four-unit gaps, and two overscan
 * rows at each end. Factories run synchronously during layout or updates and must
 * return fresh unattached nodes. Removed row widgets follow the node lifecycle.
 *
 * <pre>{@code
 * VirtualList list = new VirtualList(10000, index -> new Button("Item " + index));
 * list.rowHeight(36).overscan(3).selectable(false);
 * list.getLayout().width(400).height(500);
 * }</pre>
 *
 * <p>Measured heights are supplied explicitly, not automatically read from row
 * widgets. Internal row storage owns the scroll content: do not replace it through
 * inherited content setters. Use the list on its owning UI thread.</p>
 *
 * @author Albert Beaupre
 */
public class VirtualList extends ScrollPanel {
    private final Panel rows = new Panel(); // Owned scroll-content container for materialized items.
    private final Map<Integer, UINode> live = new LinkedHashMap<>(); // Attached item nodes indexed by model position.
    private IntFunction<? extends UINode> factory; // Retained factory for newly visible item widgets.
    private int itemCount, columns = 1, overscan = 2; // Model size, grid columns, and extra visible rows.
    private float rowHeight = 40, gap = 4; // Fixed or estimated row height and inter-item gap in layout units.
    private int first = -1, last = -1; // Cached visible item range, with an exclusive end.
    private float lastWidth = -1; // Cached usable item area width.
    private RowHeightIndex heights; // Optional measured row strides including gaps.
    private SelectionModel selection; // Optional model selection independent of transient widgets.
    private float pendingScroll = Float.NaN; // Deferred anchor-preserving scroll offset, or NaN when none.

    /**
     * Creates a vertical scroll list with internal row content and no horizontal bar.
     * Item widgets remain deferred until synchronization with an attached root.
     *
     * @param itemCount nonnegative model item count
     * @param factory   supplier of a fresh unattached node for an item index
     * @throws NullPointerException     if factory is null
     * @throws IllegalArgumentException if itemCount is negative
     */
    public VirtualList(int itemCount, IntFunction<? extends UINode> factory) {
        this.factory = Objects.requireNonNull(factory);
        if (itemCount < 0) throw new IllegalArgumentException("Negative item count");
        this.itemCount = itemCount;
        horizontal(false);
        horizontalBar(false);
        rows.getLayout().widthPercent(100).noShrink();
        super.setContent(rows);
        updateExtent();
    }

    /**
     * Walks parent links to test identity or ancestry. Used to retain rows owning
     * focus or capture even after they leave the visible range.
     *
     * @param ancestor candidate row root
     * @param node     candidate descendant
     * @return whether node lies within ancestor's subtree
     */
    private static boolean contains(UINode ancestor, UINode node) {
        for (; node != null; node = node.getParent()) if (node == ancestor) return true;
        return false;
    }

    /**
     * Returns the model size independently of how many row widgets exist.
     *
     * @return total item count
     */
    public int getItemCount() {
        return itemCount;
    }

    /**
     * Counts attached widgets, including overscan and offscreen rows retained for
     * focus or pointer capture.
     *
     * @return current materialized item count
     */
    public int getLiveItemCount() {
        return live.size();
    }

    /**
     * Enables model selection and makes the list focusable on first use. Synchronizes
     * selected state on live rows through a change subscription. Later calls update
     * single/multiple selection mode without replacing the model.
     *
     * @param multiple whether multiple indices may be selected
     * @return this list
     */
    public VirtualList selectable(boolean multiple) {
        if (selection == null) {
            selection = new SelectionModel(itemCount, multiple);
            selection.onChange(() -> {
                for (var entry : live.entrySet()) entry.getValue().setSelected(selection.isSelected(entry.getKey()));
            });
            setFocusable(true);
        } else selection.multiple(multiple);
        return this;
    }

    /**
     * Returns the optional live selection model. The list controls its item count and
     * updates live row flags when selection changes.
     *
     * @return selection model, or null before selectable is called
     */
    public SelectionModel getSelection() {
        return selection;
    }

    /**
     * After normal scroll-panel preview, selects the live row containing a left-press
     * target when selection is enabled. Shift extends and Control/Super toggles according
     * to the selection model. Does not consume the press, allowing row controls to handle it.
     *
     * @param context routed preview with the original target
     */
    @Override
    public void onInputPreview(UIInputEvent context) {
        super.onInputPreview(context);
        if (selection == null || !(context.event() instanceof MousePressEvent press) || press.getButton() != Mouse.LEFT)
            return;
        for (var entry : live.entrySet())
            if (contains(entry.getValue(), context.target())) {
                selection.select(entry.getKey(), press.isShiftDown(), press.isCtrlDown() || press.isSuperDown());
                return;
            }
    }

    /**
     * Handles selection navigation on an enabled nonempty list. Supports Home/End,
     * Up/Down by column stride, horizontal arrows for grids, and Control/Super+A.
     * Recognized navigation scrolls to the item and focuses the list to avoid pinning
     * old focused rows, then consumes the key. Other keys are ignored.
     *
     * @param event routed unhandled key press
     */
    @Override
    public void onKeyPress(KeyPressEvent event) {
        if (selection == null || itemCount == 0 || isDisabled()) return;
        boolean control = event.isCtrlDown() || event.isSuperDown();
        int key = event.getKey();
        if (key == Keyboard.A && control) {
            selection.selectAll();
            event.consume();
            return;
        }
        int index = selection.lead();
        int next;
        if (key == Keyboard.HOME) next = 0;
        else if (key == Keyboard.END) next = itemCount - 1;
        else if (key == Keyboard.DOWN) next = index < 0 ? 0 : (int) Math.min(itemCount - 1L, (long) index + columns);
        else if (key == Keyboard.UP) next = index < 0 ? 0 : Math.max(0, index - columns);
        else if (key == Keyboard.RIGHT && columns > 1) next = Math.min(itemCount - 1, index + 1);
        else if (key == Keyboard.LEFT && columns > 1) next = Math.max(0, index - 1);
        else return;
        selection.select(next, event.isShiftDown(), control && event.isShiftDown());
        scrollToIndex(next);
        // Keep keyboard navigation on the list, avoiding pinning every previously focused row.
        if (getRoot() != null) getRoot().setFocusTo(this);
        event.consume();
    }

    /**
     * Looks up a materialized widget without creating it or validating the model index.
     * The node belongs to the list and may be removed after scrolling.
     *
     * @param index model item index
     * @return attached node, or null if not materialized
     */
    public UINode getItemNode(int index) {
        return live.get(index);
    }

    /**
     * Returns the configured grid column count; measured-height mode requires one.
     *
     * @return positive number of columns
     */
    public int getColumns() {
        return columns;
    }

    /**
     * Changes fixed-grid column count and invalidates item geometry. Equal values do
     * nothing. Variable-height mode rejects every value other than one.
     *
     * @param columns positive grid width in items
     * @return this list
     * @throws IllegalArgumentException if columns is below one
     * @throws IllegalStateException    if measured heights are active and columns is not one
     */
    public VirtualList columns(int columns) {
        if (columns < 1) throw new IllegalArgumentException("Columns must be positive");
        if (heights != null && columns != 1) throw new IllegalStateException("Variable heights require one column");
        if (this.columns == columns) return this;
        this.columns = columns;
        invalidateItems();
        return this;
    }

    /**
     * Sets fixed row height and disables measured-height storage. Invalidates geometry
     * when the fixed height or mode changes; existing widgets can be retained.
     *
     * @param height finite positive height in layout units
     * @return this list
     * @throws IllegalArgumentException if height or its gap-inclusive stride is invalid
     */
    public VirtualList rowHeight(float height) {
        if (!Float.isFinite(height) || height <= 0) throw new IllegalArgumentException("Invalid row height");
        if (!Float.isFinite(height + gap)) throw new IllegalArgumentException("Row stride overflow");
        if (rowHeight == height && heights == null) return this;
        heights = null;
        rowHeight = height;
        invalidateItems();
        return this;
    }

    /**
     * Changes spacing and adjusts measured strides by the gap difference when needed.
     * Validates all adjusted strides before mutation and invalidates item geometry.
     *
     * @param gap finite nonnegative spacing in layout units
     * @return this list
     * @throws IllegalArgumentException if gap is invalid or a stride overflows
     */
    public VirtualList gap(float gap) {
        if (!Float.isFinite(gap) || gap < 0) throw new IllegalArgumentException("Invalid gap");
        if (!Float.isFinite(rowHeight + gap)) throw new IllegalArgumentException("Row stride overflow");
        if (this.gap == gap) return this;
        if (heights != null) {
            for (int i = 0; i < heights.size(); i++)
                if (!Float.isFinite(heights.height(i) - this.gap + gap))
                    throw new IllegalArgumentException("Row stride overflow");
            for (int i = 0; i < heights.size(); i++) heights.set(i, heights.height(i) - this.gap + gap);
        }
        this.gap = gap;
        invalidateItems();
        return this;
    }

    /**
     * Updates model size, resizes any measured-height index using the estimate for new
     * items, synchronizes selection count, and refreshes all widgets. Even an unchanged
     * count discards current widgets through the refresh path.
     *
     * @param count nonnegative item count
     * @return this list
     * @throws IllegalArgumentException if count is negative
     */
    public VirtualList itemCount(int count) {
        if (count < 0) throw new IllegalArgumentException("Negative item count");
        if (heights != null) heights.resize(count, rowHeight + gap);
        itemCount = count;
        if (selection != null) selection.itemCount(count);
        refreshItems();
        return this;
    }

    /**
     * Sets the extra row count materialized beyond each visible end. A changed value
     * invalidates the cached range; zero limits materialization to visible and pinned rows.
     *
     * @param rows nonnegative overscan row count
     * @return this list
     * @throws IllegalArgumentException if rows is negative
     */
    public VirtualList overscan(int rows) {
        if (rows < 0) throw new IllegalArgumentException("Negative overscan");
        if (overscan == rows) return this;
        overscan = rows;
        invalidateItems();
        return this;
    }

    /**
     * Cancels root input when focus or capture belongs to row content, removes every
     * live widget, and invalidates the range and extent. Durable data and selection stay
     * in their models; widgets will be recreated when synchronized.
     */
    public void refreshItems() {
        if (getRoot() != null && (owns(getRoot().getFocused()) || owns(getRoot().getCaptured())))
            getRoot().cancelInput();
        rows.clear();
        live.clear();
        invalidateItems();
    }

    /**
     * Tests whether a node's parent chain reaches the internal row container.
     *
     * @param node possible descendant, or null
     * @return whether row content owns the node
     */
    private boolean owns(UINode node) {
        for (; node != null; node = node.getParent()) if (node == rows) return true;
        return false;
    }

    /**
     * Invalidates the cached visible range, recomputes total content extent, and marks
     * layout dirty without immediately discarding materialized widgets.
     */
    private void invalidateItems() {
        first = last = -1;
        updateExtent();
        markLayoutDirty();
    }

    /**
     * Enables measured single-column rows using current height plus gap as the estimate.
     * Allocates per-item height indexing on first use; repeated calls preserve measurements.
     *
     * @return this list
     * @throws IllegalStateException if the configured column count is not one
     */
    public VirtualList variableHeights() {
        if (columns != 1) throw new IllegalStateException("Variable heights require one column");
        if (heights == null) {
            heights = new RowHeightIndex(itemCount, rowHeight + gap);
            invalidateItems();
        }
        return this;
    }

    /**
     * Reports whether per-item measured strides are active.
     *
     * @return true for measured single-column mode
     */
    public boolean hasVariableHeights() {
        return heights != null;
    }

    /**
     * Enables measured mode if needed and replaces one row's gap-inclusive stride.
     * Preserves the top visible row and its relative scroll offset through a deferred
     * scroll adjustment applied after layout. Equal measured values are a no-op.
     *
     * @param index  item whose height changed
     * @param height finite positive content height excluding gap
     * @return this list
     * @throws IndexOutOfBoundsException if index is invalid
     * @throws IllegalArgumentException  if height or stride is invalid
     * @throws IllegalStateException     if multiple columns prevent measured mode
     */
    public VirtualList itemHeight(int index, float height) {
        Objects.checkIndex(index, itemCount);
        if (!Float.isFinite(height) || height <= 0 || !Float.isFinite(height + gap))
            throw new IllegalArgumentException("Invalid item height");
        variableHeights();
        float stride = height + gap;
        if (heights.height(index) == stride) return this;
        float scroll = Float.isNaN(pendingScroll) ? getScrollY() : pendingScroll;
        int anchor = heights.indexAt(scroll);
        float offset = (float) (scroll - heights.offset(anchor));
        heights.set(index, stride);
        pendingScroll = (float) heights.offset(anchor) + offset;
        invalidateItems();
        return this;
    }

    /**
     * Returns fixed or measured content height, excluding the inter-row gap.
     * Does not instantiate or measure a widget.
     *
     * @param index valid model index
     * @return content height in layout units
     * @throws IndexOutOfBoundsException if index is invalid
     */
    public float getItemHeight(int index) {
        Objects.checkIndex(index, itemCount);
        return heights == null ? rowHeight : heights.height(index) - gap;
    }

    /**
     * Sets row-container height from fixed row count or summed measured strides,
     * removing the final gap and clamping the extent to nonnegative finite float range.
     */
    private void updateExtent() {
        long count = ((long) itemCount + columns - 1) / columns;
        rows.getLayout().height((float) Math.min(Float.MAX_VALUE, Math.max(0, (heights == null ? count * (double) (rowHeight + gap) : heights.totalHeight()) - gap)));
    }

    /**
     * Requests the item's row start as vertical scroll offset, laying out a dirty root
     * first when attached, then synchronizes widgets. Scroll-panel bounds can clamp the
     * offset near the end, so the requested item need not land exactly at the top.
     *
     * @param index item to reveal
     * @throws IndexOutOfBoundsException if index is invalid
     */
    public void scrollToIndex(int index) {
        if (index < 0 || index >= itemCount) throw new IndexOutOfBoundsException(index);
        if (getRoot() != null && isLayoutDirty()) getRoot().layout();
        scrollY((float) (heights == null ? (index / columns) * (double) (rowHeight + gap) : heights.offset(index)));
        synchronizeRows();
    }

    /**
     * Synchronizes the materialized range before the normal scroll-panel update.
     * Factories and child lifecycle callbacks can therefore run during this call.
     *
     * @param delta elapsed seconds
     */
    @Override
    public void update(float delta) {
        synchronizeRows();
        super.update(delta);
    }

    /**
     * Runs normal scroll-panel layout, applies any pending anchor-preserving scroll
     * offset, clears that pending value, and synchronizes row widgets.
     */
    @Override
    protected void afterLayout() {
        super.afterLayout();
        if (!Float.isNaN(pendingScroll)) {
            scrollY(pendingScroll);
            pendingScroll = Float.NaN;
        }
        synchronizeRows();
    }

    /**
     * Computes the visible/overscan range for the current scroll position and height.
     * Retains offscreen rows that own focus or capture, removes other stale widgets,
     * and creates missing rows. Uses list width minus 16 layout units for cells and
     * positions widgets absolutely. Detached or zero-height lists do nothing.
     *
     * @throws NullPointerException     if the factory returns null
     * @throws IllegalArgumentException if the factory reuses an attached or live node
     */
    private void synchronizeRows() {
        if (getRoot() == null || getHeight() <= 0) return;
        long startRow = heights == null ? (long) (getScrollY() / (rowHeight + gap)) : heights.indexAt(getScrollY());
        long endRow = heights == null ? (long) Math.ceil(((double) getScrollY() + getHeight()) / (rowHeight + gap)) : (long) heights.indexAt((double) getScrollY() + getHeight()) + 1;
        int nextFirst = (int) Math.min(itemCount, Math.max(0, startRow - overscan) * columns);
        int nextLast = (int) Math.min(itemCount, (Math.min(itemCount, endRow) + overscan) * columns);
        float width = Math.max(0, getWidth() - (getMaxScrollY() > 0 ? getVerticalBarWidth() : 0));
        // Pinned items must also be reconsidered when focus/capture changes.
        boolean extra = live.size() > nextLast - nextFirst;
        if (first == nextFirst && last == nextLast && lastWidth == width && !extra) return;
        first = nextFirst;
        last = nextLast;
        lastWidth = width;
        var iterator = live.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (entry.getKey() >= first && entry.getKey() < last) continue;
            if (contains(entry.getValue(), getRoot().getFocused()) || contains(entry.getValue(), getRoot().getCaptured()))
                continue;
            rows.remove(entry.getValue());
            iterator.remove();
        }
        float cellWidth = Math.max(0, (width - (columns - 1) * gap) / columns);
        for (int index = first; index < last; index++) {
            UINode node = live.get(index);
            if (node == null) {
                node = Objects.requireNonNull(factory.apply(index), "Item factory returned null");
                if (node.getParent() != null || live.containsValue(node))
                    throw new IllegalArgumentException("Factory must return a fresh, unattached node");
                live.put(index, node);
                rows.add(node);
                if (selection != null) node.setSelected(selection.isSelected(index));
            }
            node.getLayout().absolute().left((index % columns) * (cellWidth + gap)).top((float) (heights == null ? (index / columns) * (double) (rowHeight + gap) : heights.offset(index))).width(cellWidth).height(getItemHeight(index)).noGrow().noShrink();
        }
    }
}
