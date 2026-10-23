package valthorne.ui.nodes.nano;

import valthorne.ui.UINode;
import valthorne.ui.behavior.SelectionModel;
import valthorne.ui.behavior.TableModel;
import valthorne.ui.nodes.TableColumn;
import valthorne.ui.theme.ResolvedStyle;
import valthorne.ui.theme.StyleKey;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Sortable NanoVG table with a fixed header and virtualized fixed-height rows.
 * The table owns header and row controls; cell factories must return fresh,
 * unattached nodes. Selection indices refer to the filtered and sorted projection.
 * Mutation and layout run on the UI thread. Scrolling materializes only visible
 * rows, and reverse comparator identities are cached when columns are created.
 *
 * <pre>{@code
 * NanoDataTable<Asset> table = new NanoDataTable<>(columns);
 * table.rows(assets).rowHeight(24);
 * root.add(table);
 * }</pre>
 *
 * @param <T> application row value type
 */
public class NanoDataTable<T> extends NanoPanel {
    /**
     * Height of the fixed column header strip in UI units.
     */
    public static final StyleKey<Float> HEADER_HEIGHT_KEY = StyleKey.of("nano.table.headerHeight", Float.class, 24f);
    /**
     * Height shared by all virtualized data rows in UI units.
     */
    public static final StyleKey<Float> ROW_HEIGHT_KEY = StyleKey.of("nano.table.rowHeight", Float.class, 24f);
    private final TableModel<T> model = new TableModel<>(); // Owned content model retaining edits, projection, or selection state.
    private final List<TableColumn<T>> columns; // Immutable column membership and cell-factory definitions.
    private final NanoPanel headers = new NanoPanel(); // Owned container holding the fixed header controls.
    private final NanoVirtualList rows; // Current virtual row projection owned by this control.
    private final NanoButton[] buttons; // Owned header controls aligned with the column definitions.
    private final NanoLabel empty = new NanoLabel("No matching rows"); // Owned message shown when no visible rows remain.
    private final double totalWeight; // Cached sum of column weights used to distribute cell widths.
    private final List<Comparator<? super T>> reverse = new ArrayList<>(); // Cached reverse comparator identities aligned with the columns.

    /**
     * Builds the control and its owned children from the supplied configuration.
     *
     * @param columns nonempty column definitions copied by membership
     */
    public NanoDataTable(List<TableColumn<T>> columns) {
        this.columns = List.copyOf(columns);
        if (columns.isEmpty()) throw new IllegalArgumentException("At least one column is required");
        totalWeight = columns.stream().mapToDouble(TableColumn::weight).sum();
        buttons = new NanoButton[columns.size()];
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
            NanoButton button = new NanoButton(column.title())
                    .action(value -> toggleSort(index));
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
        rows = new NanoVirtualList(0, this::createRow).rowHeight(24).gap(0).selectable(true);
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
            rows.getSelection().clear(); rows.itemCount(model.size()); rows.scrollY(0); empty.setVisible(model.size() == 0);
            int sorted = getSortedColumn();
            for (int i = 0; i < buttons.length; i++) {
                buttons[i].text(this.columns.get(i).title() + (i == sorted ? isDescending() ? " [v]" : " [^]" : ""));
                buttons[i].setSelected(i == sorted);
            }
        });
    }

    /**
     * Borrows the owned table model for source, filtering, and ordering operations.
     *
     * @return the owned table model for source, filtering, and ordering operations
     */
    public TableModel<T> getModel() {
        return model;
    }
    /**
     * Borrows the virtual row selection model; indices refer to the current visible projection.
     *
     * @return the virtual row selection model; indices refer to the current visible projection
     */
    public SelectionModel getSelection() {
        return rows.getSelection();
    }
    /**
     * Returns the number of currently materialized virtual rows.
     *
     * @return the number of currently materialized virtual rows
     */
    public int getLiveRowCount() {
        return rows.getLiveItemCount();
    }
    /**
     * Borrows the owned header control for styling or interaction configuration.
     *
     * @param index zero-based index in the current visible or retained membership
     * @return the owned header control for styling or interaction configuration
     */
    public NanoButton getHeader(int index) {
        return buttons[Objects.checkIndex(index, buttons.length)];
    }
    /**
     * Replaces table source membership and refreshes its visible projection.
     *
     * @param data source row membership copied by the table model
     * @return this control for further configuration
     */
    public NanoDataTable<T> rows(List<? extends T> data) {
        model.rows(data);
        return this;
    }
    /**
     * Replaces the row predicate and refreshes the visible table projection.
     *
     * @param predicate nonnull row visibility predicate
     * @return this control for further configuration
     */
    public NanoDataTable<T> filter(Predicate<? super T> predicate) {
        model.filter(predicate);
        return this;
    }
    /**
     * Overrides the themed row height with a finite positive value.
     *
     * @param height uniform row height in UI units
     * @return this table
     */
    public NanoDataTable<T> rowHeight(float height) {
        rows.rowHeight(height);
        setStyle(ROW_HEIGHT_KEY, height);
        return this;
    }
    /**
     * Reveals the requested visible-projection row in the virtual viewport.
     *
     * @param index zero-based index in the current visible or retained membership
     */
    public void scrollToRow(int index) {
        rows.scrollToIndex(index);
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
     * Returns the active sort column by cached comparator identity, or minus one when unsorted.
     *
     * @return the active sort column by cached comparator identity, or minus one when unsorted
     */
    public int getSortedColumn() {
        if (model.comparator() == null) return -1;
        for (int i = 0; i < columns.size(); i++)
            if (model.comparator() == columns.get(i).comparator() || model.comparator() == reverse.get(i)) return i;
        return -1;
    }

    /**
     * Reports whether the active column uses its cached reverse comparator.
     *
     * @return reports whether the active column uses its cached reverse comparator
     */
    public boolean isDescending() {
        int column = getSortedColumn();
        return column >= 0 && model.comparator() == reverse.get(column);
    }

    /**
     * Cycles the selected column through ascending, descending, and unsorted order.
     *
     * @param index zero-based index in the current visible or retained membership
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
     * Creates a fresh row and unattached cells for one visible-projection item.
     *
     * @param index zero-based index in the current visible or retained membership
     * @return creates a fresh row and unattached cells for one visible-projection item
     */
    private UINode createRow(int index) {
        T item = model.get(index);
        NanoPanel row = new NanoPanel();
        row.setStyleName("table-row");
        row.setFocusable(true);
        row.getLayout()
                .row()
                .itemsCenter();
        for (TableColumn<T> column : columns) {
            NanoPanel cell = new NanoPanel();
            cell.setStyleName("widget-layout");
            cell.setClickable(false);
            cell.getLayout()
                    .widthPercent((float) (column.weight() / totalWeight * 100))
                    .heightPercent(100)
                    .minWidth(0)
                    .padding(6)
                    .justifyCenter();
            UINode node = Objects.requireNonNull(column.cell().apply(item), "Cell factory returned null");
            if (node.getParent() != null) throw new IllegalArgumentException("Cell factory must return fresh unattached nodes");
            cell.add(node);
            row.add(cell);
        }
        return row;
    }
}
