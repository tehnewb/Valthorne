package valthorne.ui.nodes;

import valthorne.ui.UINode;
import valthorne.ui.behavior.SelectionModel;
import valthorne.ui.behavior.TableModel;

import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Objects;
import java.util.function.Predicate;
import valthorne.ui.nodes.nano.NanoLabel;
import valthorne.ui.theme.ResolvedStyle;
import valthorne.ui.theme.StyleKey;

/**
 * Sortable, filterable table with fixed headers and virtualized fixed-height rows.
 * Columns divide available width by relative weight and may create cells using
 * either renderer. Row data remains in a separate model while offscreen widgets
 * are discarded. Projection changes clear index selection and reset scrolling.
 * Use the model and column APIs to manage contents; inherited structural mutation
 * can break the internal header, viewport, and empty-message arrangement.
 *
 * <pre>{@code
 * DataTable<String> table = new DataTable<>(List.of(
 *         TableColumn.text("Name", 1f, value -> value)));
 * table.rows(List.of("Maple", "Cedar", "Oak"));
 * table.getLayout().width(400).height(300);
 * table.toggleSort(0);
 * }</pre>
 *
 * @param <T> row data type
 * @author Albert Beaupre
 */
public class DataTable<T> extends Panel {
    /**
     * Height of the fixed column header strip in UI units.
     */
    public static final StyleKey<Float> HEADER_HEIGHT_KEY = StyleKey.of("table.headerHeight", Float.class, 24f);
    /**
     * Height shared by all virtualized data rows in UI units.
     */
    public static final StyleKey<Float> ROW_HEIGHT_KEY = StyleKey.of("table.rowHeight", Float.class, 24f);
    private final TableModel<T> model = new TableModel<>(); // Owned source/filter/sort projection model.
    private final List<TableColumn<T>> columns; // Immutable column-membership snapshot.
    private final Panel headers = new Panel(); // Owned fixed header row.
    private final VirtualList rows; // Owned selectable viewport realizing visible row widgets.
    private final Button[] buttons; // Header buttons aligned with column indices.
    private final NanoLabel empty = new NanoLabel("No matching rows"); // Owned empty-projection message.
    private final double totalWeight; // Sum of column weights used for proportional cell widths.
    private final List<Comparator<? super T>> reverse = new ArrayList<>(); // Cached reverse comparator identities aligned with columns.

    /**
     * Copies column membership and builds a compact header plus selectable virtual
     * rows initially 24 units high without gaps. Theme keys scale both heights.
     * Registers synchronous model
     * change handling for rows, empty-state visibility, selection, and sort markers.
     *
     * @param columns nonempty list of non-null immutable column definitions
     * @throws NullPointerException     if the list or any column is null
     * @throws IllegalArgumentException if no columns are supplied
     */
    public DataTable(List<TableColumn<T>> columns) {
        this.columns = List.copyOf(columns);
        if (columns.isEmpty()) throw new IllegalArgumentException("At least one column is required");
        totalWeight = columns.stream().mapToDouble(TableColumn::weight).sum();
        buttons = new Button[columns.size()];
        getLayout()
                .column()
                .gap(0)
                .minHeight(0);
        headers.setStyleName("table-header-strip");
        headers.getLayout()
                .row()
                .height(24)
                .noShrink()
                .paddingRight(12);
        for (int i = 0; i < columns.size(); i++) {
            int index = i;
            TableColumn<T> column = columns.get(i);
            reverse.add(column.comparator() == null ? null : column.comparator().reversed());
            Button button = new Button(column.title())
                    .action(b -> toggleSort(index));
            button.setStyleName("table-header");
            button.getLayout()
                    .width(0)
                    .grow(column.weight())
                    .minWidth(0)
                    .heightPercent(100);
            if (column.comparator() == null) {
                button.setFocusable(false);
                button.setClickable(false);
            }
            headers.add(button);
            buttons[i] = button;
        }
        rows = new VirtualList(0, this::createRow).rowHeight(24).gap(0).selectable(true);
        rows.getLayout()
                .widthPercent(100)
                .height(0)
                .grow()
                .minHeight(0);
        super.add(headers);
        super.add(rows);
        empty.setClickable(false);
        empty.getLayout()
                .absolute()
                .left(8)
                .top(32);
        super.add(empty);
        model.onChange(() -> {
            rows.getSelection().clear();
            rows.itemCount(model.size());
            rows.scrollY(0);
            empty.setVisible(model.size() == 0);
            int sorted = getSortedColumn();
            for (int i = 0; i < buttons.length; i++) {
                buttons[i].text(this.columns.get(i).title() + (i == sorted ? isDescending() ? " [v]" : " [^]" : ""));
                buttons[i].setSelected(i == sorted);
            }
        });
    }

    /**
     * Exposes the live projection model. Its successful mutations rebuild visible
     * row membership and reset table selection and scrolling through a listener.
     *
     * @return internally owned row model
     */
    public TableModel<T> getModel() {
        return model;
    }

    /**
     * Exposes selection in current visible-row indices, not source-row indices.
     * Sorting, filtering, refreshing, or replacing model data clears this selection.
     *
     * @return live virtual-list selection model
     */
    public SelectionModel getSelection() {
        return rows.getSelection();
    }

    /**
     * Reads the number of realized row widgets, which includes overscan and any
     * rows retained for input ownership. It is not the model's total row count.
     *
     * @return currently live row widget count
     */
    public int getLiveRowCount() {
        return rows.getLiveItemCount();
    }

    /**
     * Returns a live header button for styling or inspection. Non-sortable columns
     * have non-clickable, non-focusable headers; model updates rewrite sort labels.
     *
     * @param index zero-based column index
     * @return owned header button
     * @throws IndexOutOfBoundsException if index is outside the column list
     */
    public Button getHeader(int index) {
        return buttons[Objects.checkIndex(index, buttons.length)];
    }

    /**
     * Replaces source membership with a shallow immutable snapshot, retaining row
     * objects and reapplying the current filter and comparator. Successful rebuild
     * resets selection and scroll position.
     *
     * @param data non-null source rows with no null elements
     * @return this table
     * @throws NullPointerException if data or any row is null
     */
    public DataTable<T> rows(List<? extends T> data) {
        model.rows(data);
        return this;
    }

    /**
     * Rebuilds the visible projection using a non-null predicate and current sort.
     * Use a predicate returning true to remove filtering. Predicate failure leaves
     * the old projection intact; successful rebuild resets selection and scrolling.
     *
     * @param predicate pure row acceptance test that must not mutate the model
     * @return this table
     * @throws NullPointerException if predicate is null
     */
    public DataTable<T> filter(Predicate<? super T> predicate) {
        model.filter(predicate);
        return this;
    }

    /**
     * Sets the virtual list's fixed row height and invalidates its geometry.
     * All cells share this height; the header retains its separately themed height.
     *
     * @param height finite positive row height in UI units
     * @return this table
     * @throws IllegalArgumentException if height is nonpositive or non-finite
     */
    public DataTable<T> rowHeight(float height) {
        rows.rowHeight(height);
        setStyle(ROW_HEIGHT_KEY, height);
        return this;
    }

    @Override
    protected void applyLayout() {
        ResolvedStyle style = getStyle();
        if (style != null) {
            Float headerHeight = style.get(HEADER_HEIGHT_KEY);
            Float rowHeight = style.get(ROW_HEIGHT_KEY);
            if (headerHeight != null && Float.isFinite(headerHeight) && headerHeight > 0) {
                headers.getLayout()
                        .height(headerHeight);
                empty.getLayout()
                        .top(headerHeight + 8);
            }
            if (rowHeight != null && Float.isFinite(rowHeight) && rowHeight > 0) rows.rowHeight(rowHeight);
        }
        super.applyLayout();
    }

    /**
     * Delegates scrolling to the virtual list so a visible-projection row is brought
     * into view. The index refers to filtered/sorted membership rather than source order.
     *
     * @param index requested visible row index
     * @throws IndexOutOfBoundsException if index is outside the visible row projection
     */
    public void scrollToRow(int index) {
        rows.scrollToIndex(index);
    }

    /**
     * Matches the model's comparator by identity against original and cached reverse
     * column comparators. External comparators and null produce no recognized column.
     *
     * @return first matching column index, or minus one
     */
    public int getSortedColumn() {
        if (model.comparator() == null) return -1;
        for (int i = 0; i < columns.size(); i++)
            if (model.comparator() == columns.get(i).comparator() || model.comparator() == reverse.get(i)) return i;
        return -1;
    }

    /**
     * Checks whether the recognized column uses its cached reversed comparator.
     * Arbitrary external orderings are not inferred from their comparison behavior.
     *
     * @return true only for a recognized reversed column comparator
     */
    public boolean isDescending() {
        int column = getSortedColumn();
        return column >= 0 && model.comparator() == reverse.get(column);
    }

    /**
     * Cycles a sortable column from ascending to descending to original source order.
     * Selecting a different column starts ascending. A column without a comparator
     * is unchanged; successful model sorting resets selection and scroll position.
     *
     * @param index zero-based column index
     * @throws IndexOutOfBoundsException if index is outside the column list
     */
    public void toggleSort(int index) {
        TableColumn<T> column = columns.get(index);
        if (column.comparator() == null) return;
        int sortedColumn = getSortedColumn();
        boolean descending = isDescending();
        int nextColumn = index;
        boolean nextDescending = sortedColumn == index && !descending;
        if (sortedColumn == index && descending) nextColumn = -1;
        model.sort(nextColumn < 0 ? null : nextDescending ? reverse.get(index) : column.comparator());
    }

    /**
     * Builds one focusable row from the model's current visible item. Each factory
     * result is placed in a padded, width-weighted clipping cell. Factories must
     * return distinct unattached nodes; partially built nodes are not rolled back
     * if a later factory fails.
     *
     * @param index current visible-projection row index
     * @return fresh unattached row widget
     * @throws NullPointerException     if a cell factory returns null
     * @throws IllegalArgumentException if a returned cell already has a parent
     */
    private UINode createRow(int index) {
        T item = model.get(index);
        Panel row = new Panel();
        row.setStyleName("table-row");
        row.setFocusable(true);
        row.getLayout()
                .row()
                .itemsCenter();
        for (TableColumn<T> column : columns) {
            Panel cell = new TableCell();
            cell.setStyleName("widget-layout");
            cell.setClickable(false);
            cell.getLayout()
                    .widthPercent((float) (column.weight() / totalWeight * 100))
                    .heightPercent(100)
                    .minWidth(0)
                    .padding(6)
                    .justifyCenter();
            UINode node = Objects.requireNonNull(column.cell().apply(item), "Cell factory returned null");
            if (node.getParent() != null)
                throw new IllegalArgumentException("Cell factory must return fresh unattached nodes");
            cell.add(node);
            row.add(cell);
        }
        return row;
    }

}
