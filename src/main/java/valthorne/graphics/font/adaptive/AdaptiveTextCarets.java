package valthorne.graphics.font.adaptive;

import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lazy, CPU-only editing geometry for one single-line adaptive layout. Shaping
 * supplies visual cluster advances in em units; this class maps those advances
 * to extended graphemes without reshaping isolated prefixes. Ligatures divide
 * their advance evenly among graphemes. A primary caret chooses the lower bidi
 * embedding level at ambiguous boundaries, preferring the next grapheme on ties.
 * Selection uses individual grapheme spans so mixed-direction ranges do not
 * highlight intervening unselected text. Primitive storage is reused across
 * edits; querying carets, hit testing and selection allocate nothing.
 * The owning run confines all access to its font's graphics thread.
 */
final class AdaptiveTextCarets {
    /**
     * Unicode extended-grapheme segmentation, used only when text is not ASCII.
     */
    private static final Pattern GRAPHEME = Pattern.compile("\\X");

    private int[] offsets = new int[0]; // Ordered UTF-16 grapheme boundaries, including the final source boundary.
    private float[] leading = new float[0]; // Logical leading edge of each grapheme in normalized visual coordinates.
    private float[] trailing = new float[0]; // Logical trailing edge of each grapheme in normalized visual coordinates.
    private byte[] levels = new byte[0]; // Bidi embedding level assigned by the paragraph resolver.
    private long[] visual = new long[0]; // Sortable visual coordinate and logical index for selection traversal.
    private int count; // Number of active graphemes, excluding the final insertion boundary.
    private int leftmost; // Boundary supplying the leftmost primary caret, used to clamp pointer hits.
    private int rightmost; // Boundary supplying the rightmost primary caret, used to clamp pointer hits.

    /**
     * Creates empty editing storage, allocated only when a run is first edited.
     */
    AdaptiveTextCarets() {}

    /**
     * Segments a changed source and clears its previous cluster geometry.
     * ASCII needs neither a matcher nor a per-character wrapper.
     *
     * @param source nonnull single-line source
     */
    void reset(String source) {
        int needed = source.length() + 1;
        if (needed > offsets.length) {
            int capacity = Math.max(needed, Math.max(16, offsets.length * 2));
            offsets = new int[capacity];
            leading = new float[capacity];
            trailing = new float[capacity];
            levels = new byte[capacity];
            visual = new long[capacity];
        }
        boolean ascii = true;
        for (int i = 0; i < source.length(); i++) {
            if (source.charAt(i) <= 127) continue;
            ascii = false;
            break;
        }
        count = 0;
        if (ascii) {
            for (int i = 0; i < source.length(); i++) offsets[count++] = i;
        } else {
            Matcher matcher = GRAPHEME.matcher(source);
            while (matcher.find()) offsets[count++] = matcher.start();
        }
        offsets[count] = source.length();
        Arrays.fill(leading, 0, count, Float.NaN);
        Arrays.fill(trailing, 0, count, Float.NaN);
        Arrays.fill(levels, 0, count, (byte) 0);
    }

    /**
     * Distributes a complete shaped cluster's advance among its graphemes.
     * Zero-advance marks stay within their grapheme rather than creating carets.
     *
     * @param start first logical UTF-16 unit of the cluster
     * @param end logical boundary following the cluster
     * @param x visual pen before the cluster in em units
     * @param advance visual pen advance in em units
     * @param level bidi embedding level for the cluster
     */
    void cluster(int start, int end, float x, float advance, int level) {
        int first = boundary(start);
        int limit = boundary(end);
        if (offsets[limit] < end) limit++;
        int length = limit - first;
        if (length <= 0) return;
        boolean rtl = (level & 1) != 0;
        for (int i = 0; i < length; i++) {
            int index = first + i;
            float left = x + advance * i / length;
            float right = x + advance * (i + 1) / length;
            if (rtl) {
                float logicalLeft = x + advance - (right - x);
                right = x + advance - (left - x);
                left = logicalLeft;
            }
            // A script boundary can split one grapheme into several shaped clusters.
            if (!Float.isNaN(leading[index])) {
                left = Math.min(left, Math.min(leading[index], trailing[index]));
                right = Math.max(right, Math.max(leading[index], trailing[index]));
            }
            leading[index] = rtl ? right : left;
            trailing[index] = rtl ? left : right;
            levels[index] = (byte) level;
        }
    }

    /**
     * Completes invisible boundary geometry and sorts primitive visual keys once
     * per edit. Warm selection traversal then needs only a sequential scan.
     */
    void finish() {
        float previous = 0;
        for (int i = 0; i < count; i++) {
            if (Float.isNaN(leading[i])) leading[i] = previous;
            if (Float.isNaN(trailing[i])) trailing[i] = leading[i];
            previous = trailing[i];
            int bits = Float.floatToIntBits(Math.min(leading[i], trailing[i]));
            int ordered = bits ^ ((bits >> 31) & 0x7fffffff);
            visual[i] = ((long) ordered << 32) | i;
        }
        Arrays.sort(visual, 0, count);
        leftmost = 0;
        rightmost = 0;
        if (count == 0) return;
        for (int i = 1; i <= count; i++) {
            if (primaryX(i) < primaryX(leftmost)) leftmost = i;
            if (primaryX(i) > primaryX(rightmost)) rightmost = i;
        }
    }

    /**
     * Clamps and snaps a UTF-16 position backward to an existing grapheme.
     *
     * @param index requested insertion offset
     * @return index into the boundary array
     */
    private int boundary(int index) {
        int position = Arrays.binarySearch(offsets, 0, count + 1, Math.clamp(index, 0, offsets[count]));
        return position >= 0 ? position : -position - 2;
    }

    /**
     * Resolves one primary insertion position in em units.
     *
     * @param index UTF-16 insertion offset
     * @return visual horizontal coordinate before logical size scaling
     */
    float caretX(int index) {
        if (count == 0) return 0;
        return primaryX(boundary(index));
    }

    /**
     * Resolves an already segmented insertion boundary without another search.
     *
     * @param position index into the ordered grapheme boundary array
     * @return primary visual coordinate in em units
     */
    private float primaryX(int position) {
        if (position == count) return trailing[count - 1];
        if (position > 0 && levels[position - 1] < levels[position]) return trailing[position - 1];
        return leading[position];
    }

    /**
     * Finds the nearest primary insertion boundary, including both text ends.
     *
     * @param x visual horizontal coordinate in logical units
     * @param size logical units per em
     * @return UTF-16 offset without splitting a grapheme
     */
    int hitTest(float x, float size) {
        if (count == 0 || size == 0) return 0;
        if (x < primaryX(leftmost) * size) return offsets[leftmost];
        if (x > primaryX(rightmost) * size) return offsets[rightmost];
        int closest = 0;
        float distance = Float.POSITIVE_INFINITY;
        for (int i = 0; i <= count; i++) {
            float next = Math.abs(primaryX(i) * size - x);
            if (next >= distance) continue;
            distance = next;
            closest = offsets[i];
        }
        return closest;
    }

    /**
     * Collects selected graphemes in visual order, merging touching spans while
     * preserving gaps occupied by unselected text. Output may be smaller than
     * required; the return value always describes the complete selection.
     *
     * @param start first UTF-16 endpoint
     * @param end second UTF-16 endpoint
     * @param size logical units per em
     * @param ranges caller-owned left/right pair storage
     * @return required number of visual ranges
     */
    int selectionRanges(int start, int end, float size, float[] ranges) {
        if (ranges == null) throw new NullPointerException("ranges");
        if (start == end || size == 0) return 0;
        int first = boundary(Math.min(start, end));
        int limit = boundary(Math.max(start, end));
        if (offsets[limit] < Math.min(Math.max(start, end), offsets[count])) limit++;
        int result = 0;
        float left = 0;
        float right = 0;
        for (int i = 0; i < count; i++) {
            int index = (int) visual[i];
            if (index < first || index >= limit) continue;
            float nextLeft = Math.min(leading[index], trailing[index]);
            float nextRight = Math.max(leading[index], trailing[index]);
            if (nextLeft == nextRight) continue;
            if (result > 0 && nextLeft <= right + 0.000001f) {
                right = Math.max(right, nextRight);
            } else {
                if (result > 0 && result * 2 <= ranges.length) {
                    ranges[(result - 1) * 2] = left * size;
                    ranges[(result - 1) * 2 + 1] = right * size;
                }
                left = nextLeft;
                right = nextRight;
                result++;
            }
        }
        if (result > 0 && result * 2 <= ranges.length) {
            ranges[(result - 1) * 2] = left * size;
            ranges[(result - 1) * 2 + 1] = right * size;
        }
        return result;
    }
}
