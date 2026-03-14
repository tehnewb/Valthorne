package valthorne.collections.bits;

/**
 * <h1>ShortBits</h1>
 *
 * <p>
 * {@code ShortBits} is a compact bit container backed by a single {@code short}. It allows you to
 * store and manipulate up to 16 individual binary flags inside one short value while exposing an
 * easy-to-read utility-style API.
 * </p>
 *
 * <p>
 * This class is useful when you want something smaller than an integer mask but still need direct
 * control over bit-level state, such as:
 * </p>
 * <ul>
 *     <li>small protocol fields</li>
 *     <li>tile or chunk flags</li>
 *     <li>limited feature masks</li>
 *     <li>compact state values</li>
 * </ul>
 *
 * <p>
 * Bit indices range from {@code 0} through {@code 15}. All index-based methods validate the
 * requested index so invalid bit positions fail immediately.
 * </p>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * ShortBits flags = new ShortBits();
 *
 * flags.set(0);
 * flags.set(7);
 * flags.set(15);
 *
 * boolean topBit = flags.get(15);
 * int count = flags.size();
 *
 * ShortBits required = new ShortBits();
 * required.set(0);
 * required.set(7);
 *
 * boolean containsAll = flags.allMatch(required);
 * boolean overlaps = flags.anyMatch(required);
 *
 * System.out.println(flags);
 * System.out.println(topBit);
 * System.out.println(count);
 * System.out.println(containsAll);
 * System.out.println(overlaps);
 * }</pre>
 *
 * @author Albert Beaupre
 * @since March 11th, 2026
 */
public class ShortBits {

    /**
     * The total number of addressable bits inside one short value.
     */
    private static final int BIT_COUNT = Short.SIZE;

    private short bits; // The raw short value that stores all bit flags for this container.

    /**
     * Creates a new {@code ShortBits} instance initialized with the supplied raw short value.
     *
     * @param bits the initial raw bit pattern to store
     */
    public ShortBits(short bits) {
        this.bits = bits;
    }

    /**
     * Creates a new {@code ShortBits} instance with all bits initially cleared.
     */
    public ShortBits() {
        this.bits = 0;
    }

    /**
     * Returns whether the bit at the specified index is currently set.
     *
     * @param index the bit index to query
     * @return {@code true} if the bit is set, otherwise {@code false}
     * @throws IndexOutOfBoundsException if {@code index} is outside {@code 0..15}
     */
    public boolean get(int index) {
        validateIndex(index);
        return ((bits & 0xFFFF) & mask(index)) != 0;
    }

    /**
     * Sets the bit at the specified index to {@code 1}.
     *
     * @param index the bit index to set
     * @throws IndexOutOfBoundsException if {@code index} is outside {@code 0..15}
     */
    public void set(int index) {
        validateIndex(index);
        bits = (short) ((bits & 0xFFFF) | mask(index));
    }

    /**
     * Clears the bit at the specified index to {@code 0}.
     *
     * @param index the bit index to clear
     * @throws IndexOutOfBoundsException if {@code index} is outside {@code 0..15}
     */
    public void clear(int index) {
        validateIndex(index);
        bits = (short) ((bits & 0xFFFF) & ~mask(index));
    }

    /**
     * Sets or clears the bit at the specified index based on the supplied boolean value.
     *
     * @param index the bit index to modify
     * @param set   {@code true} to set the bit, {@code false} to clear it
     * @throws IndexOutOfBoundsException if {@code index} is outside {@code 0..15}
     */
    public void set(int index, boolean set) {
        if (set) {
            set(index);
        } else {
            clear(index);
        }
    }

    /**
     * Returns a fixed-width 16-bit binary string representation of the current short mask.
     *
     * @return a 16-character binary string
     */
    @Override
    public String toString() {
        String binary = Integer.toBinaryString(Short.toUnsignedInt(bits));
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
        return Integer.bitCount(Short.toUnsignedInt(bits));
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
     * @throws IndexOutOfBoundsException if {@code index} is outside {@code 0..15}
     */
    public void flip(int index) {
        validateIndex(index);
        bits = (short) ((bits & 0xFFFF) ^ mask(index));
    }

    /**
     * Finds the next clear bit starting at the supplied index.
     *
     * @param fromIndex the starting index for the scan
     * @return the next clear bit index, or {@code -1} if none is found
     * @throws IndexOutOfBoundsException if {@code fromIndex} is outside {@code 0..15}
     */
    public int nextClearBit(int fromIndex) {
        validateIndex(fromIndex);
        for (int i = fromIndex; i < BIT_COUNT; i++) {
            if ((((bits & 0xFFFF) & mask(i))) == 0) {
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
     * @throws IndexOutOfBoundsException if {@code fromIndex} is outside {@code 0..15}
     */
    public int nextSetBit(int fromIndex) {
        validateIndex(fromIndex);
        for (int i = fromIndex; i < BIT_COUNT; i++) {
            if ((((bits & 0xFFFF) & mask(i))) != 0) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Returns whether this instance stores exactly the same bit pattern as another {@code ShortBits}.
     *
     * @param other the other bit container to compare against
     * @return {@code true} if both instances have identical bit patterns
     * @throws NullPointerException if {@code other} is null
     */
    public boolean matches(ShortBits other) {
        if (other == null) {
            throw new NullPointerException("other");
        }
        return this.bits == other.bits;
    }

    /**
     * Returns whether this instance stores exactly the same raw bit pattern as the supplied short.
     *
     * @param bits the raw short mask to compare against
     * @return {@code true} if the stored mask matches exactly
     */
    public boolean matches(short bits) {
        return this.bits == bits;
    }

    /**
     * Returns whether all set bits in the supplied {@code ShortBits} are also set in this instance.
     *
     * @param bits the mask whose set bits must all be present here
     * @return {@code true} if this instance contains the supplied mask
     * @throws NullPointerException if {@code bits} is null
     */
    public boolean allMatch(ShortBits bits) {
        if (bits == null) {
            throw new NullPointerException("bits");
        }
        return ((this.bits & 0xFFFF) & (bits.bits & 0xFFFF)) == (bits.bits & 0xFFFF);
    }

    /**
     * Returns whether all set bits in the supplied raw short are also set in this instance.
     *
     * @param bits the raw short mask to test for containment
     * @return {@code true} if this instance contains the supplied mask
     */
    public boolean allMatch(short bits) {
        int other = bits & 0xFFFF;
        return ((this.bits & 0xFFFF) & other) == other;
    }

    /**
     * Returns whether this instance shares at least one set bit with another {@code ShortBits}.
     *
     * @param other the other bit container to compare against
     * @return {@code true} if both masks overlap on at least one set bit
     * @throws NullPointerException if {@code other} is null
     */
    public boolean anyMatch(ShortBits other) {
        if (other == null) {
            throw new NullPointerException("other");
        }
        return (((this.bits & 0xFFFF) & (other.bits & 0xFFFF)) != 0);
    }


    public void setBits(short bits) {
        this.bits = bits;
    }

    /**
     * Returns the raw short value currently stored by this container.
     *
     * @return the packed short bit mask
     */
    public short getBits() {
        return bits;
    }

    /**
     * Validates that a bit index falls within the valid short range.
     *
     * @param index the index to validate
     * @throws IndexOutOfBoundsException if the index is outside {@code 0..15}
     */
    private static void validateIndex(int index) {
        if (index < 0 || index >= BIT_COUNT) {
            throw new IndexOutOfBoundsException("Bit index must be between 0 and 15: " + index);
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