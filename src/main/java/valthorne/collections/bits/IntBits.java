package valthorne.collections.bits;

/**
 * <h1>IntBits</h1>
 *
 * <p>
 * {@code IntBits} is a compact bit container backed by a single {@code int}. It exposes a readable
 * API for working with up to 32 independent binary flags inside one integer value.
 * </p>
 *
 * <p>
 * This class is useful when you need a fast and memory-efficient mask for things such as:
 * </p>
 * <ul>
 *     <li>entity flags</li>
 *     <li>UI state masks</li>
 *     <li>render feature toggles</li>
 *     <li>permission sets</li>
 *     <li>compact save or packet fields</li>
 * </ul>
 *
 * <p>
 * Bit index {@code 0} maps to the least significant bit and bit index {@code 31} maps to the most
 * significant bit. All index-based methods validate the supplied index to ensure operations stay
 * inside the valid integer bit range.
 * </p>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * IntBits flags = new IntBits();
 *
 * flags.set(1);
 * flags.set(4);
 * flags.flip(4);
 * flags.set(10, true);
 *
 * boolean hasBit1 = flags.get(1);
 * int count = flags.size();
 * int nextSet = flags.nextSetBit(0);
 *
 * IntBits required = new IntBits();
 * required.set(1);
 *
 * boolean exact = flags.matches(required);
 * boolean overlap = flags.anyMatch(required);
 * boolean contains = flags.allMatch(required);
 * }</pre>
 *
 * @author Albert Beaupre
 * @since May 1st, 2024
 */
public class IntBits {

    /**
     * The total number of addressable bits inside one int value.
     */
    private static final int BIT_COUNT = Integer.SIZE;

    private int bits; // The raw integer value that stores all bit flags for this container.

    /**
     * Creates a new {@code IntBits} instance with all bits initially cleared.
     */
    public IntBits() {
        this.bits = 0;
    }

    /**
     * Creates a new {@code IntBits} instance initialized with the supplied raw bit pattern.
     *
     * @param bits the initial raw integer mask
     */
    public IntBits(int bits) {
        this.bits = bits;
    }

    /**
     * Returns whether the bit at the specified index is set.
     *
     * @param index the bit index to query
     * @return {@code true} if that bit is enabled, otherwise {@code false}
     * @throws IndexOutOfBoundsException if {@code index} is outside {@code 0..31}
     */
    public boolean get(int index) {
        validateIndex(index);
        return (bits & mask(index)) != 0;
    }

    /**
     * Sets the bit at the specified index to {@code 1}.
     *
     * @param index the bit index to set
     * @throws IndexOutOfBoundsException if {@code index} is outside {@code 0..31}
     */
    public void set(int index) {
        validateIndex(index);
        bits |= mask(index);
    }

    /**
     * Clears the bit at the specified index to {@code 0}.
     *
     * @param index the bit index to clear
     * @throws IndexOutOfBoundsException if {@code index} is outside {@code 0..31}
     */
    public void clear(int index) {
        validateIndex(index);
        bits &= ~mask(index);
    }

    /**
     * Sets or clears the bit at the specified index based on the supplied boolean value.
     *
     * @param index the bit index to modify
     * @param set   {@code true} to set the bit, {@code false} to clear it
     * @throws IndexOutOfBoundsException if {@code index} is outside {@code 0..31}
     */
    public void set(int index, boolean set) {
        if (set) {
            set(index);
        } else {
            clear(index);
        }
    }

    /**
     * Returns a fixed-width 32-bit binary string representation of the current bit pattern.
     *
     * @return a 32-character binary string
     */
    @Override
    public String toString() {
        String binary = Integer.toBinaryString(bits);
        return "0".repeat(BIT_COUNT - binary.length()) + binary;
    }

    /**
     * Returns whether all bits are currently cleared.
     *
     * @return {@code true} if the stored mask is zero
     */
    public boolean isEmpty() {
        return bits == 0;
    }

    /**
     * Returns the number of set bits currently stored in this container.
     *
     * @return the population count of the current mask
     */
    public int size() {
        return Integer.bitCount(bits);
    }

    /**
     * Clears every bit in this container.
     */
    public void clearAll() {
        bits = 0;
    }

    /**
     * Toggles the bit at the specified index.
     *
     * @param index the bit index to flip
     * @throws IndexOutOfBoundsException if {@code index} is outside {@code 0..31}
     */
    public void flip(int index) {
        validateIndex(index);
        bits ^= mask(index);
    }

    /**
     * Applies a bitwise XOR operation using another {@code IntBits} instance.
     *
     * @param other the other bit container
     * @throws NullPointerException if {@code other} is null
     */
    public void xor(IntBits other) {
        if (other == null) {
            throw new NullPointerException("other");
        }
        this.bits ^= other.bits;
    }

    /**
     * Applies a bitwise AND operation using another {@code IntBits} instance.
     *
     * @param other the other bit container
     * @throws NullPointerException if {@code other} is null
     */
    public void and(IntBits other) {
        if (other == null) {
            throw new NullPointerException("other");
        }
        this.bits &= other.bits;
    }

    /**
     * Applies a bitwise OR operation using another {@code IntBits} instance.
     *
     * @param other the other bit container
     * @throws NullPointerException if {@code other} is null
     */
    public void or(IntBits other) {
        if (other == null) {
            throw new NullPointerException("other");
        }
        this.bits |= other.bits;
    }

    /**
     * Creates and returns a copy of this {@code IntBits} instance.
     *
     * @return a new {@code IntBits} containing the same bit pattern
     */
    @Override
    public IntBits clone() {
        return new IntBits(this.bits);
    }

    /**
     * Finds the next clear bit starting at the supplied index.
     *
     * @param fromIndex the starting index for the scan
     * @return the next clear bit index, or {@code -1} if none is found
     * @throws IndexOutOfBoundsException if {@code fromIndex} is outside {@code 0..31}
     */
    public int nextClearBit(int fromIndex) {
        validateIndex(fromIndex);
        for (int i = fromIndex; i < BIT_COUNT; i++) {
            if ((bits & mask(i)) == 0) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Finds the next set bit starting at the supplied index.
     *
     * @param fromIndex the starting index for the scan
     * @return the next set bit index, or {@code -1} if none is found
     * @throws IndexOutOfBoundsException if {@code fromIndex} is outside {@code 0..31}
     */
    public int nextSetBit(int fromIndex) {
        validateIndex(fromIndex);
        for (int i = fromIndex; i < BIT_COUNT; i++) {
            if ((bits & mask(i)) != 0) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Returns whether this instance stores exactly the same bit pattern as another {@code IntBits}.
     *
     * @param other the other bit container to compare against
     * @return {@code true} if both masks are identical
     * @throws NullPointerException if {@code other} is null
     */
    public boolean matches(IntBits other) {
        if (other == null) {
            throw new NullPointerException("other");
        }
        return this.bits == other.bits;
    }

    /**
     * Returns whether this instance stores exactly the same raw bit pattern as the supplied int.
     *
     * @param bits the raw integer mask to compare against
     * @return {@code true} if the stored mask matches exactly
     */
    public boolean matches(int bits) {
        return this.bits == bits;
    }

    /**
     * Returns whether all set bits in the supplied {@code IntBits} are also set in this instance.
     *
     * @param other the mask whose set bits must all be present here
     * @return {@code true} if this instance contains the supplied mask
     * @throws NullPointerException if {@code other} is null
     */
    public boolean allMatch(IntBits other) {
        if (other == null) {
            throw new NullPointerException("other");
        }
        return (this.bits & other.bits) == other.bits;
    }

    /**
     * Returns whether all set bits in the supplied raw int are also set in this instance.
     *
     * @param bits the raw integer mask to test for containment
     * @return {@code true} if this instance contains that mask
     */
    public boolean allMatch(int bits) {
        return (this.bits & bits) == bits;
    }

    /**
     * Returns whether this instance shares at least one set bit with another {@code IntBits}.
     *
     * @param other the other bit container to compare against
     * @return {@code true} if both masks overlap on at least one set bit
     * @throws NullPointerException if {@code other} is null
     */
    public boolean anyMatch(IntBits other) {
        if (other == null) {
            throw new NullPointerException("other");
        }
        return (this.bits & other.bits) != 0;
    }

    /**
     * Returns the raw integer bit mask stored by this container.
     *
     * @return the packed integer bit value
     */
    public int getBits() {
        return bits;
    }

    /**
     * Validates that a bit index falls within the valid int range.
     *
     * @param index the index to validate
     * @throws IndexOutOfBoundsException if the index is outside {@code 0..31}
     */
    private static void validateIndex(int index) {
        if (index < 0 || index >= BIT_COUNT) {
            throw new IndexOutOfBoundsException("Bit index must be between 0 and 31: " + index);
        }
    }

    /**
     * Returns the mask for the specified bit index.
     *
     * @param index the validated bit index
     * @return an integer mask with that bit enabled
     */
    private static int mask(int index) {
        return 1 << index;
    }
}