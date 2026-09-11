package valthorne.collections.array;

/**
 * Resizable indexed storage for int values with no separate logical element count.
 * Every allocated slot is readable, including untouched zero entries. Set may
 * replace the backing array; callers holding getElements results must reacquire
 * that array after growth. Mutations are unsynchronized.
 *
 * <p>Growth allocates exactly index plus one slots, preserving earlier values.
 * There is no shrink operation or automatic growth on reads. Large sparse
 * indices still allocate every intermediate slot.</p>
 *
 * @author Albert Beaupre
 */
public class IntArray {

    private int[] array; // Current backing storage, replaced when set grows its allocated length.

    /**
     * Allocates primitive storage of the requested length, initially filled with
     * zero. A later set beyond the last slot grows storage as needed.
     *
     * @param size initial allocated length
     * @throws NegativeArraySizeException if size is negative
     */
    public IntArray(int size) {
        this.array = new int[size];
    }

    /**
     * Stores a value, replacing backing storage when index reaches or exceeds length.
     * Growth allocates exactly index plus one slots, copying prior values and
     * leaving intermediate new slots zero. Previously returned backing arrays
     * no longer track the replacement.
     *
     * @param index nonnegative destination slot whose growth size fits an int
     * @param value value to store
     * @throws ArrayIndexOutOfBoundsException if index is negative
     */
    public void set(int index, int value) {
        if (index >= array.length) {
            int[] copy = new int[index + 1];
            System.arraycopy(array, 0, copy, 0, array.length);
            array = copy;
        }
        array[index] = value;
    }

    /**
     * Reads an allocated slot without resizing or changing membership.
     * Untouched slots return zero; length describes all readable indices.
     *
     * @param index slot from zero through length minus one
     * @return stored primitive value
     * @throws ArrayIndexOutOfBoundsException if index is outside allocated storage
     */
    public int get(int index) {
        return array[index];
    }

    /**
     * Returns backing-array length, including untouched and default-valued slots.
     * This is capacity, not a count of explicitly assigned values.
     *
     * @return current allocated number of slots
     */
    public int length() {
        return array.length;
    }

    /**
     * Exposes the live backing array without copying it. Direct writes immediately
     * affect this object until a subsequent set replaces storage during growth.
     * Retained arrays remain valid Java arrays after growth but no longer represent
     * this object's current storage.
     *
     * @return current mutable backing array
     */
    public int[] getElements() {
        return array;
    }
}
