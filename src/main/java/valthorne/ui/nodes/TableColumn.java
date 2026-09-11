package valthorne.ui.nodes;

import valthorne.ui.UINode;
import valthorne.ui.nodes.nano.NanoLabel;
import java.util.Comparator;
import java.util.Objects;
import java.util.function.Function;

/**
 * Immutable column definition for a virtual DataTable. Width is proportional
 * to the sum of column weights, and each visible cell is built from a fresh
 * unattached node returned by the factory. Factories may use either UI renderer.
 * Comparators sort row objects; a null comparator disables header sorting.
 *
 * @param <T> row data type
 *
 * @param title non-null header label
 * @param weight positive finite relative width
 * @param cell factory returning a fresh unattached node for each visible row
 * @param comparator optional row comparator; null makes the column unsortable
 * @author Albert Beaupre
 */
public record TableColumn<T>(String title, float weight, Function<? super T, ? extends UINode> cell,
                             Comparator<? super T> comparator) {
    /**
     * Validates required metadata while retaining the supplied callbacks. Factory
     * results are checked later when a table realizes visible cells.
     *
     * @param title non-null header label
     * @param weight positive finite relative width
     * @param cell non-null cell factory
     * @param comparator optional row ordering
     * @throws NullPointerException if title or cell is null
     * @throws IllegalArgumentException if weight is nonpositive or non-finite
     */
    public TableColumn {
        Objects.requireNonNull(title); Objects.requireNonNull(cell);
        if (!Float.isFinite(weight) || weight <= 0) throw new IllegalArgumentException("Column weight must be positive and finite");
    }
    /**
     * Creates fresh non-clickable NanoLabel cells from an extracted string and a
     * null-first natural string comparator. Null extracted values display as empty
     * text but sort before non-null values. The extractor may be invoked repeatedly
     * by sorting and rendering, so it should provide stable results.
     *
     * @param <T> row data type
     * @param title non-null header label
     * @param weight positive finite relative width
     * @param text non-null row-to-text extractor
     * @return immutable sortable text column
     * @throws NullPointerException if title or text is null
     * @throws IllegalArgumentException if weight is nonpositive or non-finite
     */
    public static <T> TableColumn<T> text(String title, float weight, Function<? super T, String> text) {
        Objects.requireNonNull(text);
        return new TableColumn<>(title, weight, row -> {
            NanoLabel label = new NanoLabel(Objects.toString(text.apply(row), ""));
            label.setClickable(false); return label;
        },
                Comparator.comparing(text, Comparator.nullsFirst(Comparator.naturalOrder())));
    }
}
