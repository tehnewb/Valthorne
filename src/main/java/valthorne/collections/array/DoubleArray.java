package valthorne.collections.array;

/**
 * DoubleArray is a class that represents a dynamic array with automatic resizing capabilities for primitive doubles.
 * It allows the storage and retrieval of elements at specified indices, automatically resizing the underlying array
 * when necessary to accommodate new elements.
 *
 * @author Albert Beaupre
 * @version 1.0
 * @since May 1st, 2024
 */
public class DoubleArray {

    /**
     * The underlying array to store double elements.
     */
    private double[] array;

    /**
     * Constructs a DoubleArray with the specified initial size.
     *
     * @param size the initial size of the array.
     */
    public DoubleArray(int size) {
        this.array = new double[size];
    }

    /**
     * Sets the double at the specified index. If the index is greater than or equal to the current length of the array,
     * the array is resized to accommodate the new index, and the double is then set at the specified index.
     *
     * @param index the index at which to set the double.
     * @param value the double value to be set at the specified index.
     */
    public void set(int index, double value) {
        if (index >= array.length) {
            double[] copy = new double[index + 1];
            System.arraycopy(array, 0, copy, 0, array.length);
            array = copy;
        }
        array[index] = value;
    }

    /**
     * Retrieves the double at the specified index.
     *
     * @param index the index of the double to retrieve.
     * @return the double at the specified index.
     */
    public double get(int index) {
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
    public double[] getElements() {
        return array;
    }
}
