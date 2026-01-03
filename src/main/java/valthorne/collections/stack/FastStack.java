package valthorne.collections.stack;

import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * A generic implementation of a dynamic stack in Java. This is considered fast because it uses fewer
 * method calls and checks, which in turn is fewer instructions.
 *
 * @param <T> the type of elements stored in the stack
 * @author Albert Beaupre
 * @since May 1st, 2024
 */
public class FastStack<T> implements Iterable<T> {
    private T[] stack;
    private int ordinal;

    /**
     * Constructs a FastStack with the default initial size.
     */
    public FastStack() {
        this(10);
    }

    /**
     * Constructs a FastStack with a specified initial size.
     *
     * @param size the initial size of the stack
     */
    public FastStack(int size) {
        // Use of unchecked cast is intentional.
        this.stack = (T[]) new Object[size];
    }

    /**
     * Adds an element to the top of the stack.
     *
     * @param data the element to be added
     */
    public void push(T data) {
        if (ordinal == stack.length) { // Resize the stack array if it reaches its capacity
            T[] copy = (T[]) new Object[stack.length * 2];
            System.arraycopy(stack, 0, copy, 0, stack.length);
            stack = copy;
        }
        stack[ordinal++] = data;
    }

    /**
     * Removes and returns the element at the top of the stack.
     *
     * @return the element removed from the top of the stack, or null if the stack is empty
     */
    public T pop() {
        if (ordinal == 0)
            return null;
        T old = stack[--ordinal];
        stack[ordinal] = null;
        return old;
    }

    /**
     * Returns the element at the top of the stack without removing it.
     *
     * @return the element at the top of the stack
     * @throws NoSuchElementException if the stack is empty
     */
    public T peek() {
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
     * Clears the stack by setting the number of elements to zero and filling the array with null values.
     */
    public void clear() {
        for (int i = 0, len = stack.length; i < len; i++) {
            stack[i] = null;
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
     * Returns an iterator over the elements in this stack in LIFO order (from the top of the stack to the bottom).
     *
     * @return an Iterator of T objects.
     */
    @Override
    public Iterator<T> iterator() {
        return new FastStackIterator();
    }

    /**
     * An iterator that traverses the FastStack in LIFO order.
     */
    private class FastStackIterator implements Iterator<T> {
        // Start from the top of the stack (the last pushed element)
        private int currentIndex = ordinal - 1;

        /**
         * Checks if there are more elements to iterate over.
         *
         * @return true if there is another element, false otherwise.
         */
        @Override
        public boolean hasNext() {
            return currentIndex >= 0;
        }

        /**
         * Returns the next element in the iteration.
         *
         * @return the next element in the stack.
         * @throws NoSuchElementException if no further elements exist.
         */
        @Override
        public T next() {
            if (!hasNext()) {
                throw new NoSuchElementException("No more elements in the stack.");
            }
            return stack[currentIndex--];
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