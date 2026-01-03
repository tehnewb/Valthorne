package valthorne.collections.stack;

import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * A generic implementation of a dynamic stack in Java. This is considered fast because it uses fewer
 * method calls and checks, which in turn is fewer instructions.
 *
 * @author Albert Beaupre
 * @since May 1st, 2024
 */
public class ShortFastStack implements Iterable<Short> {
    private short[] stack;
    private int ordinal;

    /**
     * Constructs a FastStack with the default initial size.
     */
    public ShortFastStack() {
        this(10);
    }

    /**
     * Constructs a FastStack with a specified initial size.
     *
     * @param size the initial size of the stack
     */
    public ShortFastStack(int size) {
        this.stack = new short[size];
    }

    /**
     * Adds an element to the top of the stack.
     *
     * @param data the element to be added
     */
    public void push(short data) {
        if (ordinal == stack.length) { // Resize the stack array if it reaches its capacity
            short[] copy = new short[stack.length * 2];
            // Copy the elements from the old array to the new array
            System.arraycopy(stack, 0, copy, 0, stack.length);
            // Update the reference to the new array
            stack = copy;
        }
        stack[ordinal++] = data;
    }

    /**
     * Removes and returns the element at the top of the stack.
     *
     * @return the element removed from the top of the stack, or -1 if the stack is empty
     */
    public short pop() {
        if (ordinal == 0)
            return -1;
        short old = stack[--ordinal];
        stack[ordinal] = 0;
        return old;
    }

    /**
     * Returns the element at the top of the stack without removing it.
     *
     * @return the element at the top of the stack
     * @throws NoSuchElementException if the stack is empty
     */
    public short peek() {
        if (ordinal == 0) {
            throw new NoSuchElementException("Stack is empty.");
        }
        return stack[ordinal - 1];
    }

    /**
     * Checks if the stack is empty.
     *
     * @return true if the stack is empty, false otherwise
     */
    public boolean isEmpty() {
        return ordinal == 0;
    }

    /**
     * Returns the current number of elements in the stack.
     *
     * @return the size of the stack
     */
    public int size() {
        return ordinal;
    }

    /**
     * Clears the stack by setting the number of elements to zero and filling the array with zeros.
     */
    public void clear() {
        for (int i = 0, len = stack.length; i < len; i++) {
            stack[i] = 0;
        }
        ordinal = 0;
    }

    /**
     * Returns a string representation of the elements in the stack.
     *
     * @return a string representation of the stack
     */
    @Override
    public String toString() {
        return Arrays.toString(Arrays.copyOf(stack, ordinal));
    }

    /**
     * Returns an iterator over the elements in this stack in LIFO order (from the top to the bottom).
     *
     * @return an Iterator of Short objects.
     */
    @Override
    public Iterator<Short> iterator() {
        return new ShortFastStackIterator();
    }

    /**
     * An iterator that traverses the ShortFastStack in LIFO order.
     */
    private class ShortFastStackIterator implements Iterator<Short> {
        // Begin iterating at the top of the stack (the last pushed element)
        private int currentIndex = ordinal - 1;

        @Override
        public boolean hasNext() {
            return currentIndex >= 0;
        }

        @Override
        public Short next() {
            if (!hasNext()) {
                throw new NoSuchElementException("No more elements in the stack.");
            }
            return stack[currentIndex--]; // Autoboxes short to Short.
        }

        /**
         * The remove operation is not supported in this iterator.
         *
         * @throws UnsupportedOperationException always.
         */
        @Override
        public void remove() {
            throw new UnsupportedOperationException("Remove not supported.");
        }
    }
}