package valthorne.collections.array;

import java.util.Arrays;

/**
 * The SwapOnRemoveIntArray class is a resizable array-based collection that allows elements to be efficiently
 * added and removed. When an element is removed, it is swapped with the last element in the array to maintain
 * array continuity and improve removal performance.
 *
 * @author Albert Beaupre
 * @version 1.0
 * @since May 1st, 2024
 */
public class SwapOnRemoveIntArray {

    private int[] data;   // The array to store elements
    private int size;      // The current number of elements in the array

    /**
     * Constructs a new SwapOnRemoveIntArray with an initial capacity of 10.
     */
    public SwapOnRemoveIntArray() {
        this.data = new int[10];
    }

    /**
     * Adds the specified element to the end of this array. If the array is full, it is resized to twice its
     * current capacity to accommodate more elements.
     *
     * @param element the element to be added to the array
     */
    public void add(int element) {
        if (size == data.length) {
            // If the array is full, create a new array with double the capacity
            int[] copy = new int[data.length * 2];
            // Copy the elements from the old array to the new array
            for (int i = 0; i < data.length; i++)
                copy[i] = data[i];
            // Update the reference to the new array
            data = copy;
        }
        // Add the new element to the end of the array
        data[size++] = element;
    }

    /**
     * Removes the element at the specified index from the array. The element to be removed
     * is swapped with the last element in the array, and the size of the array is decremented.
     * The removed element is returned.
     *
     * @param index the index of the element to be removed
     * @return the element that was removed from the array
     */
    public int remove(int index) {
        // Swap the element to be removed with the last element in the array
        int object = data[index];
        data[index] = data[--size];
        // Set the last element to null
        data[size] = 0;
        return object;
    }

    /**
     * Removes the specific value from the array. If the value is not found, nothing happens.
     *
     * @param value the value to remove
     */
    public void removeValue(int value) {
        for (int i = 0; i < size; i++) {
            if (data[i] == value) {
                // Copy the remove method so we don't have a method call
                data[i] = data[--size];
                data[size] = 0;
            }
        }
    }

    /**
     * Returns the current number of elements in the array.
     *
     * @return the size of the array
     */
    public int size() {
        return size;
    }

    /**
     * Checks if the array contains the specified element.
     *
     * @param element the element to be checked for existence in the array
     * @return true if the element is found, false otherwise
     */
    public boolean contains(int element) {
        for (int i : data) {
            if (i == element) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the array of elements.
     *
     * @return an array containing the elements in this collection
     */
    public int[] getData() {
        return data;
    }

    /**
     * Returns the element at the specified index in the array.
     *
     * @param index the index of the element to retrieve
     * @return the element at the specified index
     */
    public int get(int index) {
        return data[index];
    }

    /**
     * Removes all elements from the array, leaving it empty.
     */
    public void clear() {
        Arrays.fill(data, 0);
        size = 0;
    }

    /**
     * Returns the index of the first occurrence of the specified element in the array.
     *
     * @param element the element to search for
     * @return the index of the element, or -1 if the element is not found
     */
    public int indexOf(int element) {
        for (int i = 0; i < size; i++) {
            if (data[i] == element) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Trims the capacity of the array to the current size, reducing memory usage.
     */
    public void trimToSize() {
        if (size < data.length) {
            data = Arrays.copyOf(data, size);
        }
    }

    /**
     * Checks if the array is empty.
     *
     * @return true if the array is empty, false otherwise
     */
    public boolean isEmpty() {
        return size == 0;
    }

    /**
     * Checks if the array is full.
     *
     * @return true if the array is full, false otherwise
     */
    public boolean isFull() {
        return size == data.length;
    }

    /**
     * Returns a string representation of the array.
     *
     * @return a string representation of the array
     */
    @Override
    public String toString() {
        return "SwapOnRemoveIntArray" + Arrays.toString(data);
    }
}