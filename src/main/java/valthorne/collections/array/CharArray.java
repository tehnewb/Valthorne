package valthorne.collections.array;

/**
 * CharArray is a class that represents a dynamic array with automatic resizing capabilities for primitive chars.
 * It allows the storage and retrieval of elements at specified indices, automatically resizing the underlying array
 * when necessary to accommodate new elements.
 *
 * @author Albert Beaupre
 * @version 1.0
 * @since May 1st, 2024
 */
public class CharArray {

    /**
     * The underlying array to store char elements.
     */
    private char[] array;

    /**
     * Constructs a CharArray with the specified initial size.
     *
     * @param size the initial size of the array.
     */
    public CharArray(int size) {
        this.array = new char[size];
    }

    /**
     * Sets the char at the specified index. If the index is greater than or equal to the current length of the array,
     * the array is resized to accommodate the new index, and the char is then set at the specified index.
     *
     * @param index the index at which to set the char.
     * @param value the char value to be set at the specified index.
     */
    public void set(int index, char value) {
        if (index >= array.length) {
            char[] copy = new char[index + 1];
            System.arraycopy(array, 0, copy, 0, array.length);
            array = copy;
        }
        array[index] = value;
    }

    /**
     * Retrieves the char at the specified index.
     *
     * @param index the index of the char to retrieve.
     * @return the char at the specified index.
     */
    public char get(int index) {
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
    public char[] getElements() {
        return array;
    }
}
