package valthorne.collections.stack;

import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Array-backed last-in, first-out storage for char values. Positive
 * capacity doubles when full; push and pop normally access only the top slot.
 * Empty pop returns (char) -1, which is also a valid stored value, whereas empty
 * peek throws. Use isEmpty when distinguishing an empty pop matters.
 *
 * <p>Iterators walk top to bottom over live storage with an initial cursor and
 * no concurrent-modification checks. Do not mutate the stack while iterating.
 * The class is mutable and supplies no thread synchronization. Construct with
 * positive capacity: a zero-length array cannot grow by doubling.</p>
 *
 * @author Albert Beaupre
 * @since May 1st, 2024
 */
public class CharFastStack implements Iterable<Character> {
    private char[] stack; // Backing storage; only slots before ordinal are occupied.
    private int ordinal; // Number of occupied slots and insertion index for the next push.

    /**
     * Creates an empty stack with ten storage slots. The backing array grows
     * when later pushes fill its current capacity.
     */
    public CharFastStack() {
        this(10);
    }

    /**
     * Allocates the requested initial storage without placing any elements in it.
     * Use positive capacity; zero is accepted here but the doubling growth rule
     * cannot make a zero-length array usable for push.
     *
     * @param size initial number of storage slots
     * @throws NegativeArraySizeException if size is negative
     */
    public CharFastStack(int size) {
        this.stack = new char[size];
    }

    /**
     * Appends a value at the top, doubling and copying the array when full.
     * Existing order is preserved. Storage itself does not box primitive values.
     *
     * @param data value to push
     * @throws ArrayIndexOutOfBoundsException if constructed with zero capacity
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
     * Removes the most recently pushed element and clears its vacated slot.
     * Empty stacks return (char) -1 without changing state; that result is not a
     * unique emptiness indicator because the same value can be pushed.
     *
     * @return previous top value, or (char) -1 when empty
     */
    public char pop() {
        if (ordinal == 0)
            return (char) -1;
        char old = stack[--ordinal];
        stack[ordinal] = 0;
        return old;
    }

    /**
     * Reads the most recently pushed element without removing or copying it.
     * Unlike pop, an empty stack is reported by an exception.
     *
     * @return current top value
     * @throws NoSuchElementException if the stack is empty
     */
    public char peek() {
        if (ordinal == 0) {
            throw new NoSuchElementException("Stack is empty.");
        }
        return stack[ordinal - 1];
    }

    /**
     * Tests logical membership rather than backing-array capacity.
     *
     * @return true when no elements are stored
     */
    public boolean isEmpty() {
        return ordinal == 0;
    }

    /**
     * Reads the number of occupied slots. Reserved backing capacity is excluded.
     *
     * @return current element count
     */
    public int size() {
        return ordinal;
    }

    /**
     * Fills the entire backing array with zero and resets the element count.
     * Allocated capacity is retained for reuse; existing iterators are invalid for
     * continued traversal after this mutation.
     */
    public void clear() {
        Arrays.fill(stack, (char) 0);
        ordinal = 0;
    }

    /**
     * Copies the occupied prefix and formats it in bottom-to-top storage order.
     * This order is the reverse of iterator traversal; unused capacity is omitted.
     *
     * @return bracketed representation of the current elements
     */
    @Override
    public String toString() {
        return Arrays.toString(Arrays.copyOf(stack, ordinal));
    }

    /**
     * Creates a top-to-bottom iterator whose initial cursor is the current top.
     * It reads live storage, does not detect later mutation, and does not support
     * removal. Keep the stack unchanged while using the iterator.
     *
     * @return new iterator over current stack positions
     */
    @Override
    public Iterator<Character> iterator() {
        return new CharFastStackIterator();
    }

    /**
     * Live-storage iterator with an independent descending cursor initialized from
     * the enclosing stack's top slot. It is neither a snapshot nor fail-fast;
     * mutation of the enclosing stack during traversal is unsupported.
     *
     * @author Albert Beaupre
     */
    private class CharFastStackIterator implements Iterator<Character> {
        // Start iterating at the top of the stack (most recently added element)
        private int currentIndex = ordinal - 1; // Next live slot to read, descending from the original top.

        /**
         * Checks whether the saved cursor still addresses a nonnegative slot.
         * Does not compare against the enclosing stack's current size.
         *
         * @return true when another cursor position remains
         */
        @Override
        public boolean hasNext() {
            return currentIndex >= 0;
        }

        /**
         * Reads the current live backing-array slot and decrements the cursor.
         * The primitive value is boxed for the Iterator interface.
         *
         * @return next value in top-to-bottom order
         * @throws NoSuchElementException if the cursor is exhausted
         */
        @Override
        public Character next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            return stack[currentIndex--];  // Autoboxing from char to Character.
        }

        /**
         * Rejects iterator removal without changing storage or cursor state.
         * Use the enclosing stack's operations outside iteration instead.
         *
         * @throws UnsupportedOperationException always
         */
        @Override
        public void remove() {
            throw new UnsupportedOperationException("Remove not supported.");
        }
    }
}
