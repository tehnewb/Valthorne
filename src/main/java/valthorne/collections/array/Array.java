package valthorne.collections.array;

/**
 * Resizable indexed storage for object references with no separate logical element count.
 * Every allocated slot is readable, including untouched null entries. Set may
 * replace the backing array; callers holding getElements results must reacquire
 * that array after growth. Mutations are unsynchronized.
 *
 * <p>Growth allocates an Object array of twice the requested index, even after
 * construction with a concrete component type. A zero-capacity array cannot
 * accept index zero under that rule; use a positive initial size. Typed array
 * casts from getElements are not generally safe after growth.</p>
 *
 * @param <T> stored reference type; null is permitted
 * @author Albert Beaupre
 */
public class Array<T> {

    private T[] array; // Current backing storage, replaced when set grows its allocated length.

    /**
     * Allocates Object-backed storage of the requested length, initially filled with
     * null. Prefer positive size to permit a first write at index zero.
     *
     * @param size initial allocated length
     * @throws NegativeArraySizeException if size is negative
     */
    public Array(int size) {
        this.array = (T[]) new Object[size];
    }

    /**
     * Allocates storage with the supplied runtime component type. A subsequent
     * growth replaces it with Object-backed storage, so this constructor does not
     * guarantee a persistent concrete array type.
     *
     * @param type reference component class used for initial allocation
     * @param size initial allocated length
     * @throws NullPointerException if type is null
     * @throws NegativeArraySizeException if size is negative
     * @throws ClassCastException if a primitive component class is supplied
     */
    public Array(Class<T> type, int size) {
        this.array = (T[]) java.lang.reflect.Array.newInstance(type, size);
    }

    /**
     * Stores a value, replacing backing storage when index reaches or exceeds length.
     * Growth uses an Object array of length index times two, losing any concrete
     * component type selected at construction. Index zero cannot grow an empty
     * array. Previously returned backing arrays no longer track the replacement.
     *
     * @param index nonnegative destination slot whose growth size fits an int
     * @param value value to store
     * @throws ArrayIndexOutOfBoundsException if index is negative or zero cannot grow empty storage
     */
    public void set(int index, T value) {
        if (index >= array.length) {
            T[] copy = (T[]) new Object[index * 2];
            System.arraycopy(array, 0, copy, 0, array.length);
            array = copy;
        }
        array[index] = value;
    }

    /**
     * Reads an allocated slot without resizing or changing membership.
     * Untouched slots return null; length describes all readable indices.
     *
     * @param index slot from zero through length minus one
     * @return stored reference, without copying
     * @throws ArrayIndexOutOfBoundsException if index is outside allocated storage
     */
    public T get(int index) {
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
     * The runtime component type is Object unless typed construction has occurred
     * and no growth has replaced that array. Assigning to a concrete T array can
     * therefore cause a caller-side ClassCastException.
     *
     * @return current mutable backing array
     */
    public T[] getElements() {
        return array;
    }
}
