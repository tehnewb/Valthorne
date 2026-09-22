package valthorne.ui.nodes.nano;

import valthorne.ui.UINode;
import valthorne.ui.behavior.SelectionModel;
import valthorne.ui.behavior.TableModel;
import valthorne.ui.nodes.TableColumn;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/** NanoVG-backed sortable, filterable, virtualized data table. */
public class NanoDataTable<T> extends NanoPanel {
    private final TableModel<T> model = new TableModel<>();
    private final List<TableColumn<T>> columns;
    private final NanoPanel headers = new NanoPanel();
    private final NanoVirtualList rows;
    private final NanoButton[] buttons;
    private final NanoLabel empty = new NanoLabel("No matching rows");
    private final double totalWeight;
    private final List<Comparator<? super T>> reverse = new ArrayList<>();

    public NanoDataTable(List<TableColumn<T>> columns) {
        this.columns = List.copyOf(columns);
        if (columns.isEmpty()) throw new IllegalArgumentException("At least one column is required");
        totalWeight = columns.stream().mapToDouble(TableColumn::weight).sum();
        buttons = new NanoButton[columns.size()];
        getLayout().column().gap(4).minHeight(0);
        headers.getLayout().row().height(36).noShrink().paddingRight(16);
        for (int i = 0; i < columns.size(); i++) {
            int index = i;
            TableColumn<T> column = columns.get(i);
            reverse.add(column.comparator() == null ? null : column.comparator().reversed());
            NanoButton button = new NanoButton(column.title()).action(value -> toggleSort(index));
            button.getLayout().width(0).grow(column.weight()).minWidth(0).heightPercent(100);
            if (column.comparator() == null) { button.setFocusable(false); button.setClickable(false); }
            headers.add(button); buttons[i] = button;
        }
        rows = new NanoVirtualList(0, this::createRow).rowHeight(36).gap(2).selectable(true);
        rows.getLayout().widthPercent(100).height(0).grow().minHeight(0);
        super.add(headers); super.add(rows);
        empty.setClickable(false); empty.getLayout().absolute().left(8).top(48); super.add(empty);
        model.onChange(() -> {
            rows.getSelection().clear(); rows.itemCount(model.size()); rows.scrollY(0); empty.setVisible(model.size() == 0);
            int sorted = getSortedColumn();
            for (int i = 0; i < buttons.length; i++) {
                buttons[i].text(this.columns.get(i).title() + (i == sorted ? isDescending() ? " [v]" : " [^]" : ""));
                buttons[i].setSelected(i == sorted);
            }
        });
    }

    public TableModel<T> getModel() { return model; }
    public SelectionModel getSelection() { return rows.getSelection(); }
    public int getLiveRowCount() { return rows.getLiveItemCount(); }
    public NanoButton getHeader(int index) { return buttons[Objects.checkIndex(index, buttons.length)]; }
    public NanoDataTable<T> rows(List<? extends T> data) { model.rows(data); return this; }
    public NanoDataTable<T> filter(Predicate<? super T> predicate) { model.filter(predicate); return this; }
    public NanoDataTable<T> rowHeight(float height) { rows.rowHeight(height); return this; }
    public void scrollToRow(int index) { rows.scrollToIndex(index); }

    public int getSortedColumn() {
        if (model.comparator() == null) return -1;
        for (int i = 0; i < columns.size(); i++)
            if (model.comparator() == columns.get(i).comparator() || model.comparator() == reverse.get(i)) return i;
        return -1;
    }

    public boolean isDescending() {
        int column = getSortedColumn();
        return column >= 0 && model.comparator() == reverse.get(column);
    }

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

    private UINode createRow(int index) {
        T item = model.get(index);
        NanoPanel row = new NanoPanel();
        row.setStyleName("table-row"); row.setFocusable(true); row.getLayout().row().itemsCenter();
        for (TableColumn<T> column : columns) {
            NanoPanel cell = new NanoPanel();
            cell.setClickable(false);
            cell.getLayout().widthPercent((float) (column.weight() / totalWeight * 100)).heightPercent(100)
                    .minWidth(0).padding(6).justifyCenter();
            UINode node = Objects.requireNonNull(column.cell().apply(item), "Cell factory returned null");
            if (node.getParent() != null) throw new IllegalArgumentException("Cell factory must return fresh unattached nodes");
            cell.add(node); row.add(cell);
        }
        return row;
    }
}
