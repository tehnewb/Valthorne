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
public class LongFastStack implements Iterable<Long> {
    private long[] stack;
    private int ordinal;

    /**
     * Constructs a FastStack with the default initial size.
     */
    public LongFastStack() {
        this(10);
    }

    /**
     * Constructs a FastStack with a specified initial size.
     *
     * @param size the initial size of the stack
     */
    public LongFastStack(int size) {
        this.stack = new long[size];
    }

    /**
     * Adds an element to the top of the stack.
     *
     * @param data the element to be added
     */
    public void push(long data) {
        if (ordinal == stack.length) { // Resize the stack array if it reaches its capacity
            // If the array is full, create a new array with double the capacity
            long[] copy = new long[stack.length * 2];
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
     * @return the element removed from the top of the stack, or -1 if empty
     */
    public long pop() {
        if (ordinal == 0)
            return -1;
        long old = stack[--ordinal];
        stack[ordinal] = 0;
        return old;
    }

    /**
     * Returns the element at the top of the stack without removing it.
     *
     * @return the element at the top of the stack
     */
    public long peek() {
        if (ordinal == 0)
            throw new NoSuchElementException("Stack is empty.");
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
        Arrays.fill(stack, 0);
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
     * Returns an iterator over the elements in this stack in LIFO order (top-of-stack to bottom).
     *
     * @return an Iterator.
     */
    @Override
    public Iterator<Long> iterator() {
        return new LongFastStackIterator();
    }

    /**
     * An iterator that iterates over the elements of the LongFastStack.
     */
    private class LongFastStackIterator implements Iterator<Long> {
        // Start iteration from the top-of-stack (last pushed element)
        private int currentIndex = ordinal - 1;

        @Override
        public boolean hasNext() {
            return currentIndex >= 0;
        }

        @Override
        public Long next() {
            if (!hasNext())
                throw new NoSuchElementException();
            return stack[currentIndex--];
        }

        /**
         * The remove operation is not supported by this iterator.
         *
         * @throws UnsupportedOperationException always.
         */
        @Override
        public void remove() {
            throw new UnsupportedOperationException("Remove not supported.");
        }
    }
}
