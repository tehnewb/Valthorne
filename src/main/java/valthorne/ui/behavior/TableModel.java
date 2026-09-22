package valthorne.ui.behavior;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * UI-thread model projecting a shallow immutable row snapshot through filtering
 * and stable sorting. Visible membership is stored as primitive source indices;
 * reads allocate no new row objects. Predicates and comparators run only when
 * mutated or refreshed and must be pure and must not reenter this model.
 *
 * <p>A rebuild computes new membership before publishing state, so predicate or
 * comparator failure preserves the old projection. Notifications run synchronously
 * after publication; listener failure does not undo the committed state. Mutating
 * a row object requires refresh to reconsider its visibility or position.</p>
 *
 * @param <T> row object type
 * @author Albert Beaupre
 */
public final class TableModel<T> {
    private List<T> source = List.of(); // Shallow immutable source-membership snapshot.
    private int[] visible = new int[0]; // Visible source indices in filtered and sorted order.
    private Predicate<? super T> filter = row -> true; // Retained pure acceptance predicate.
    private Comparator<? super T> comparator; // Retained ordering, null for source order.
    private final ChangeSignal changes = new ChangeSignal(); // Synchronous post-commit notification registrations.

    /**
     * Reads filtered membership length, including all rows surviving the last rebuild.
     *
     * @return current visible row count
     */
    public int size() {
        return visible.length;
    }

    /**
     * Reads source snapshot membership independent of filtering and sorting.
     *
     * @return source row count
     */
    public int sourceSize() {
        return source.size();
    }

    /**
     * Returns the retained comparator identity used by the current projection.
     *
     * @return active ordering, or null for source order
     */
    public Comparator<? super T> comparator() {
        return comparator;
    }

    /**
     * Resolves a visible index through the primitive projection to a borrowed row.
     * The row object is not copied; refresh after changes affecting filter or order.
     *
     * @param index zero-based visible index
     * @return original row object
     * @throws IndexOutOfBoundsException if index is outside visible membership
     */
    public T get(int index) {
        return source.get(visible[Objects.checkIndex(index, visible.length)]);
    }

    /**
     * Maps a visible index to its position in the current source snapshot.
     * The mapping may change after any successful rebuild.
     *
     * @param index zero-based visible index
     * @return source snapshot index
     * @throws IndexOutOfBoundsException if index is outside visible membership
     */
    public int sourceIndex(int index) {
        return visible[Objects.checkIndex(index, visible.length)];
    }

    /**
     * Registers a synchronous listener for every successful rebuild, including
     * refreshes that preserve identical membership. The listener observes committed
     * state; close the returned handle to remove this registration.
     *
     * @param listener non-null callback
     * @return independent removal handle
     * @throws NullPointerException if listener is null
     */
    public AutoCloseable onChange(Runnable listener) {
        return changes.subscribe(listener);
    }

    /**
     * Copies source membership and reapplies the retained filter and comparator.
     * Row objects remain borrowed. Publication occurs only after filtering/sorting
     * succeeds, followed by synchronous change notification.
     *
     * @param rows non-null source list without null rows
     * @throws NullPointerException if rows or any element is null
     */
    public void rows(List<? extends T> rows) {
        rebuild(List.copyOf(rows), filter, comparator);
    }

    /**
     * Replaces the acceptance predicate and rebuilds the projection in the existing
     * order. Use an always-true predicate to include every source row. Predicate
     * failure leaves current state intact.
     *
     * @param filter non-null pure acceptance predicate
     * @throws NullPointerException if filter is null
     */
    public void filter(Predicate<? super T> filter) {
        rebuild(source, Objects.requireNonNull(filter), comparator);
    }

    /**
     * Rebuilds ordering with the supplied comparator; null restores source order.
     * Equal comparisons retain original source order, not the previous projection's
     * order. The current filter remains applied.
     *
     * @param comparator pure row ordering, or null for source order
     */
    public void sort(Comparator<? super T> comparator) {
        rebuild(source, filter, comparator);
    }

    /**
     * Reevaluates the current source objects using the retained filter and ordering.
     * Use after mutable row fields change. Successful refresh always emits a change
     * notification even if the visible indices are unchanged.
     */
    public void refresh() {
        rebuild(source, filter, comparator);
    }

    /**
     * Builds a fresh index projection, filters source-order rows, and optionally
     * stable-sorts them before committing all model fields. Callback errors during
     * computation leave prior fields intact; listener errors occur after commit.
     *
     * @param rows      immutable source-membership snapshot
     * @param predicate non-null pure acceptance test
     * @param order     optional pure ordering
     */
    private void rebuild(List<T> rows, Predicate<? super T> predicate, Comparator<? super T> order) {
        int[] next = new int[rows.size()];
        int count = 0;
        for (int i = 0; i < rows.size(); i++) if (predicate.test(rows.get(i))) next[count++] = i;
        if (count != next.length) next = Arrays.copyOf(next, count);
        if (order != null && count > 1) sort(next, new int[count], 0, count, rows, order);
        // Commit only after user predicates/comparators finish successfully.
        source = rows;
        filter = predicate;
        comparator = order;
        visible = next;
        changes.fire();
    }

    /**
     * Stable merge-sorts a half-open interval of source indices using row values.
     * Ties take the left entry first, preserving source order. Work storage is
     * shared across recursive calls and must match the index-array capacity.
     *
     * @param <T>     row type
     * @param indices source indices reordered in place
     * @param work    merge scratch storage
     * @param from    inclusive interval start
     * @param to      exclusive interval end
     * @param rows    source snapshot providing comparison values
     * @param order   non-null comparator
     */
    private static <T> void sort(int[] indices, int[] work, int from, int to, List<T> rows, Comparator<? super T> order) {
        if (to - from < 2) return;
        int mid = (from + to) >>> 1;
        sort(indices, work, from, mid, rows, order);
        sort(indices, work, mid, to, rows, order);
        int left = from, right = mid;
        for (int i = from; i < to; i++) {
            if (left < mid && (right == to || order.compare(rows.get(indices[left]), rows.get(indices[right])) <= 0))
                work[i] = indices[left++];
            else work[i] = indices[right++];
        }
        System.arraycopy(work, from, indices, from, to - from);
    }
}
