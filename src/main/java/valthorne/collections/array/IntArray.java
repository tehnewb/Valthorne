package valthorne.collections.array;

/**
 * IntArray is a class that represents a dynamic array with automatic resizing capabilities for primitive ints.
 * It allows the storage and retrieval of elements at specified indices, automatically resizing the underlying array
 * when necessary to accommodate new elements.
 *
 * @author Albert Beaupre
 * @version 1.0
 * @since May 1st, 2024
 */
public class IntArray {

    /**
     * The underlying array to store int elements.
     */
    private int[] array;

    /**
     * Constructs a IntArray with the specified initial size.
     *
     * @param size the initial size of the array.
     */
    public IntArray(int size) {
        this.array = new int[size];
    }

    /**
     * Sets the element at the specified index. If the index is greater than or equal to the current length of the array,
     * the array is resized to accommodate the new index, and the element is then set at the specified index.
     *
     * @param index the index at which to set the element.
     * @param value the value to be set at the specified index.
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
     * Retrieves the element at the specified index.
     *
     * @param index the index of the element to retrieve.
     * @return the element at the specified index.
     */
    public int get(int index) {
        return array[index];
    }

    /**
     * @return The length of elements within this ResizingArray.
     */
    public int length() {
        return array.length;
    }

    /**
     * @return The elements within this ResizingArray.
     */
    public int[] getElements() {
        return array;
    }
}
