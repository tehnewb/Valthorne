package valthorne.collections.array;

/**
 * FloatArray is a class that represents a dynamic array with automatic resizing capabilities for primitive floats.
 * It allows the storage and retrieval of elements at specified indices, automatically resizing the underlying array
 * when necessary to accommodate new elements.
 *
 * @author Albert Beaupre
 * @version 1.0
 * @since May 1st, 2024
 */
public class FloatArray {

    /**
     * The underlying array to store float elements.
     */
    private float[] array;

    /**
     * Constructs a FloatArray with the specified initial size.
     *
     * @param size the initial size of the array.
     */
    public FloatArray(int size) {
        this.array = new float[size];
    }

    /**
     * Sets the float at the specified index. If the index is greater than or equal to the current length of the array,
     * the array is resized to accommodate the new index, and the float is then set at the specified index.
     *
     * @param index the index at which to set the float.
     * @param value the float value to be set at the specified index.
     */
    public void set(int index, float value) {
        if (index >= array.length) {
            float[] copy = new float[index + 1];
            System.arraycopy(array, 0, copy, 0, array.length);
            array = copy;
        }
        array[index] = value;
    }

    /**
     * Retrieves the float at the specified index.
     *
     * @param index the index of the float to retrieve.
     * @return the float at the specified index.
     */
    public float get(int index) {
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
    public float[] getElements() {
        return array;
    }
}
