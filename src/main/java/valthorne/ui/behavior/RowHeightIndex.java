package valthorne.ui.behavior;

import java.util.Arrays;
import java.util.Objects;

/**
 * Maintains measured row strides and cumulative offsets for a variable-height
 * list without scanning every preceding row. A Fenwick tree stores partial sums
 * in double precision, while individual heights remain floats. Point updates,
 * prefix sums and position lookup take O(log n) time; construction and resizing
 * take O(n) time and storage uses approximately 12 bytes per row, plus array overhead.
 *
 * <p>Use one consistent coordinate unit, normally UI pixels. Each stored height
 * includes any trailing gap; the index does not track content height and spacing
 * separately. Rows occupy intervals from their starting offset, inclusive, to
 * the next offset, exclusive. All strides must be finite and strictly positive.</p>
 *
 * <pre>{@code
 * RowHeightIndex rows = new RowHeightIndex(1000, 28f);
 * rows.set(3, 52f);
 * double fifthRowTop = rows.offset(4);
 * int firstVisible = rows.indexAt(120);
 * if (firstVisible < rows.size()) {
 *     float visibleRowStride = rows.height(firstVisible);
 * }
 * }</pre>
 *
 * <p>This mutable index is intended for UI-thread use and provides no
 * synchronization. Successful queries and point updates allocate no objects;
 * resizing replaces backing arrays. Empty indexes are supported, with zero
 * total height and zero as the end sentinel returned by position lookup.</p>
 *
 * @author Albert Beaupre
 */
public final class RowHeightIndex {
    private float[] heights; // Per-row strides, including any caller-supplied trailing gap.
    private double[] tree; // One-based Fenwick partial sums; element zero is unused.

    /**
     * Creates an index with every row initialized to the same estimated stride.
     * The estimate is validated even for an empty index, and the cumulative
     * structure is built immediately in linear time.
     *
     * @param count           the initial row count, from zero through Integer.MAX_VALUE - 1
     * @param estimatedHeight the finite positive initial stride in UI coordinate units
     * @throws IllegalArgumentException if count is negative or Integer.MAX_VALUE,
     *                                  or the estimate is non-finite or not positive
     */
    public RowHeightIndex(int count, float estimatedHeight) {
        validateHeight(estimatedHeight);
        if (count < 0 || count == Integer.MAX_VALUE) throw new IllegalArgumentException("Invalid row count");
        heights = new float[count];
        Arrays.fill(heights, estimatedHeight);
        rebuild();
    }

    /**
     * Rejects strides that cannot represent a finite, nonempty row interval.
     * Strict positivity keeps cumulative positions increasing and makes boundary
     * lookup meaningful; zero, negative values, infinities and NaN are rejected.
     *
     * @param height the candidate row stride
     * @throws IllegalArgumentException if height is non-finite or not positive
     */
    private static void validateHeight(float height) {
        if (!Float.isFinite(height) || height <= 0)
            throw new IllegalArgumentException("Height must be finite and positive");
    }

    /**
     * Returns the current row count. This is also the valid exclusive endpoint
     * for {@link #offset(int)} and the past-end sentinel for {@link #indexAt(double)}.
     *
     * @return the number of indexed rows, possibly zero
     */
    public int size() {return heights.length;}

    /**
     * Reads a row's current stride in constant time, including any gap captured
     * in the estimate or most recent measurement. The result is not a prefix sum.
     *
     * @param index the zero-based row index
     * @return the row's finite positive stride
     * @throws IndexOutOfBoundsException if index is outside the current rows
     */
    public float height(int index) {return heights[Objects.checkIndex(index, heights.length)];}

    /**
     * Replaces one row's stride and propagates its difference through the partial
     * sums in logarithmic time. An unchanged value returns without updating the
     * tree. Validation completes before any state changes; callers must separately
     * update layout or scroll anchors affected by the new measurement.
     *
     * @param index  the zero-based row to update
     * @param height the finite positive replacement stride, including any gap
     * @throws IndexOutOfBoundsException if index is outside the current rows
     * @throws IllegalArgumentException  if height is non-finite or not positive
     */
    public void set(int index, float height) {
        Objects.checkIndex(index, heights.length);
        validateHeight(height);
        double delta = (double) height - heights[index];
        if (delta == 0) return;
        heights[index] = height;
        for (int i = index + 1; i > 0 && i < tree.length; i += i & -i) tree[i] += delta;
    }

    /**
     * Sums row strides in the half-open range {@code [0, endExclusive)} in
     * logarithmic time. Passing a row index gives that row's starting position;
     * zero returns zero and {@link #size()} returns the entire indexed extent.
     *
     * @param endExclusive the number of leading rows to include, from zero through size
     * @return the cumulative stride in the same units as the stored heights
     * @throws IndexOutOfBoundsException if the endpoint is negative or greater than size
     */
    public double offset(int endExclusive) {
        if (endExclusive < 0 || endExclusive > size()) throw new IndexOutOfBoundsException(endExclusive);
        double sum = 0;
        for (int i = endExclusive; i > 0; i -= i & -i) sum += tree[i];
        return sum;
    }

    /**
     * Returns the sum of all row strides, including the final row's trailing gap
     * if one was supplied. Empty indexes return zero. This delegates to a
     * logarithmic prefix query rather than maintaining a separate cached total.
     *
     * @return the total indexed extent in UI coordinate units
     */
    public double totalHeight() {return offset(size());}

    /**
     * Locates a position within the cumulative strides in logarithmic time.
     * A row's starting boundary belongs to that row; positions in its trailing
     * gap also belong to it because spacing is included in the stride.
     *
     * <p>Negative positions, including negative infinity, clamp to zero. Positions
     * at or beyond the total extent, including positive infinity, return
     * {@link #size()}, which is a sentinel rather than a valid row index. An
     * empty index returns zero for every non-NaN input.</p>
     *
     * @param offset the position measured from the first row's start
     * @return the containing zero-based row, or size when past the indexed rows
     * @throws IllegalArgumentException if offset is NaN
     */
    public int indexAt(double offset) {
        if (Double.isNaN(offset)) throw new IllegalArgumentException("NaN offset");
        if (offset < 0) return 0;
        int index = 0;
        double sum = 0;
        for (int bit = Integer.highestOneBit(size()); bit != 0; bit >>>= 1) {
            int next = index + bit;
            if (next <= size() && sum + tree[next] <= offset) {
                index = next;
                sum += tree[next];
            }
        }
        return index;
    }

    /**
     * Changes the row count while retaining measurements at surviving indices.
     * Growing fills only new rows with the estimate; shrinking discards removed
     * measurements, which are not recovered by a later expansion. A changed
     * count reallocates storage and rebuilds partial sums in linear time.
     *
     * <p>The estimate and count are validated even when the count is unchanged.
     * For that case, valid arguments produce no allocation or measurement change.</p>
     *
     * @param count           the replacement row count, from zero through Integer.MAX_VALUE - 1
     * @param estimatedHeight the finite positive stride for newly added rows
     * @throws IllegalArgumentException if count is negative or Integer.MAX_VALUE,
     *                                  or the estimate is non-finite or not positive
     */
    public void resize(int count, float estimatedHeight) {
        validateHeight(estimatedHeight);
        if (count < 0 || count == Integer.MAX_VALUE) throw new IllegalArgumentException("Invalid row count");
        if (count == size()) return;
        int previous = size();
        heights = Arrays.copyOf(heights, count);
        if (count > previous) Arrays.fill(heights, previous, count, estimatedHeight);
        rebuild();
    }

    /**
     * Replaces the cumulative tree with a one-based structure derived from the
     * current heights. Each partial sum is forwarded to its parent once, giving
     * linear construction time without repeated point updates. Row values are
     * preserved and are expected to have been validated by the caller.
     */
    private void rebuild() {
        tree = new double[heights.length + 1];
        for (int i = 1; i < tree.length; i++) {
            tree[i] += heights[i - 1];
            int parent = i + (i & -i);
            if (parent > 0 && parent < tree.length) tree[parent] += tree[i];
        }
    }
}
