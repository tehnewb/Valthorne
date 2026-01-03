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
public class CharFastStack implements Iterable<Character> {
    private char[] stack;
    private int ordinal;

    /**
     * Constructs a FastStack with the default initial size.
     */
    public CharFastStack() {
        this(10);
    }

    /**
     * Constructs a FastStack with a specified initial size.
     *
     * @param size the initial size of the stack
     */
    public CharFastStack(int size) {
        this.stack = new char[size];
    }

    /**
     * Adds an element to the top of the stack.
     *
     * @param data the element to be added
     */
    public void push(char data) {
        if (ordinal == stack.length) { // Resize the stack array if it reaches its capacity
            char[] copy = new char[stack.length * 2];
            for (int i = 0; i < stack.length; i++) {
                copy[i] = stack[i];
            }
            stack = copy;
        }
        stack[ordinal++] = data;
    }

    /**
     * Removes and returns the element at the top of the stack.
     *
     * @return the element removed from the top of the stack or (char)-1 if the stack is empty
     */
    public char pop() {
        if (ordinal == 0)
            return (char) -1;
        char old = stack[--ordinal];
        stack[ordinal] = 0;
        return old;
    }

    /**
     * Returns the element at the top of the stack without removing it.
     *
     * @return the element at the top of the stack
     * @throws NoSuchElementException if the stack is empty
     */
    public char peek() {
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
        Arrays.fill(stack, (char) 0);
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
     * @return an Iterator of Character objects.
     */
    @Override
    public Iterator<Character> iterator() {
        return new CharFastStackIterator();
    }

    /**
     * An iterator that traverses the CharFastStack in LIFO order.
     */
    private class CharFastStackIterator implements Iterator<Character> {
        // Start iterating at the top of the stack (most recently added element)
        private int currentIndex = ordinal - 1;

        @Override
        public boolean hasNext() {
            return currentIndex >= 0;
        }

        @Override
        public Character next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            return stack[currentIndex--];  // Autoboxing from char to Character.
        }

        /**
         * Remove operation is not supported.
         *
         * @throws UnsupportedOperationException always.
         */
        @Override
        public void remove() {
            throw new UnsupportedOperationException("Remove not supported.");
        }
    }
}