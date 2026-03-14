package valthorne.collections.bits;

/**
 * <h1>LongBits</h1>
 *
 * <p>
 * {@code LongBits} is a compact bit container backed by a single {@code long}. It allows you to
 * work with up to 64 binary flags inside one primitive value while exposing a readable API for
 * querying, modifying, scanning, combining, and comparing those flags.
 * </p>
 *
 * <p>
 * This class is useful when you need a lightweight flag set for things such as:
 * </p>
 * <ul>
 *     <li>entity state flags</li>
 *     <li>component masks</li>
 *     <li>permission masks</li>
 *     <li>binary protocol fields</li>
 *     <li>compact runtime feature toggles</li>
 * </ul>
 *
 * <p>
 * Each bit index maps directly to one position inside the internal {@code long}:
 * index {@code 0} is the least significant bit and index {@code 63} is the most significant bit.
 * Every operation in this class validates the bit index so invalid positions fail fast instead of
 * silently producing incorrect results.
 * </p>
 *
 * <h2>Behavior overview</h2>
 * <ul>
 *     <li>{@link #get(int)} reads a bit</li>
 *     <li>{@link #set(int)} and {@link #clear(int)} modify individual bits</li>
 *     <li>{@link #flip(int)} toggles a bit</li>
 *     <li>{@link #xor(LongBits)}, {@link #and(LongBits)}, and {@link #or(LongBits)} combine masks</li>
 *     <li>{@link #nextSetBit(int)} and {@link #nextClearBit(int)} scan forward through the bit set</li>
 *     <li>{@link #matches(LongBits)}, {@link #allMatch(LongBits)}, and {@link #anyMatch(LongBits)}
 *     support exact, contained, and overlapping comparisons</li>
 * </ul>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * LongBits flags = new LongBits();
 *
 * flags.set(0);
 * flags.set(5);
 * flags.set(63);
 *
 * boolean enabled = flags.get(5);
 * int count = flags.size();
 * int nextSet = flags.nextSetBit(0);
 * int nextClear = flags.nextClearBit(0);
 *
 * LongBits required = new LongBits();
 * required.set(0);
 * required.set(5);
 *
 * boolean containsAll = flags.allMatch(required);
 * boolean overlaps = flags.anyMatch(required);
 *
 * System.out.println(flags);        // 64-bit binary string
 * System.out.println(enabled);      // true
 * System.out.println(count);        // 3
 * System.out.println(nextSet);      // 0
 * System.out.println(nextClear);    // 1
 * System.out.println(containsAll);  // true
 * System.out.println(overlaps);     // true
 * }</pre>
 *
 * @author Albert Beaupre
 * @since May 1st, 2024
 */
public class LongBits {

    /**
     * The total number of addressable bits inside one long value.
     */
    private static final int BIT_COUNT = Long.SIZE;

    private long bits; // The raw long value that stores all bit flags for this container.

    /**
     * Creates a new {@code LongBits} instance with all bits initially cleared.
     *
     * <p>
     * After construction, every bit from index {@code 0} through {@code 63} is unset.
     * </p>
     */
    public LongBits() {
        this.bits = 0L;
    }

    /**
     * Creates a new {@code LongBits} instance initialized with the supplied raw bit pattern.
     *
     * <p>
     * This constructor is useful when you already have a packed {@code long} mask from serialized
     * data, a native system, a save format, or another bit-oriented API.
     * </p>
     *
     * @param bits the initial raw bit pattern to store
     */
    public LongBits(long bits) {
        this.bits = bits;
    }

    /**
     * Returns whether the bit at the specified index is currently set.
     *
     * <p>
     * The supplied index must be in the inclusive range {@code 0..63}. This method checks that
     * position in the stored {@code long} and returns {@code true} when the bit is {@code 1}.
     * </p>
     *
     * @param index the bit index to query
     * @return {@code true} if the bit is set, otherwise {@code false}
     * @throws IndexOutOfBoundsException if {@code index} is outside {@code 0..63}
     */
    public boolean get(int index) {
        validateIndex(index);
        return (bits & mask(index)) != 0L;
    }

    /**
     * Sets the bit at the specified index to {@code 1}.
     *
     * <p>
     * All other bits remain unchanged.
     * </p>
     *
     * @param index the bit index to set
     * @throws IndexOutOfBoundsException if {@code index} is outside {@code 0..63}
     */
    public void set(int index) {
        validateIndex(index);
        bits |= mask(index);
    }

    /**
     * Clears the bit at the specified index to {@code 0}.
     *
     * <p>
     * All other bits remain unchanged.
     * </p>
     *
     * @param index the bit index to clear
     * @throws IndexOutOfBoundsException if {@code index} is outside {@code 0..63}
     */
    public void clear(int index) {
        validateIndex(index);
        bits &= ~mask(index);
    }

    /**
     * Sets or clears the bit at the specified index depending on the supplied boolean value.
     *
     * <p>
     * This is a convenience overload that routes to {@link #set(int)} when {@code set} is true
     * and {@link #clear(int)} when {@code set} is false.
     * </p>
     *
     * @param index the bit index to modify
     * @param set   {@code true} to set the bit, {@code false} to clear it
     * @throws IndexOutOfBoundsException if {@code index} is outside {@code 0..63}
     */
    public void set(int index, boolean set) {
        if (set) {
            set(index);
        } else {
            clear(index);
        }
    }

    /**
     * Returns a fixed-width binary string representation of the current bit pattern.
     *
     * <p>
     * This method always returns all 64 bit positions, including leading zeroes, so the entire
     * state of the mask is visible.
     * </p>
     *
     * @return a 64-character binary string
     */
    @Override
    public String toString() {
        String binary = Long.toBinaryString(bits);
        return "0".repeat(BIT_COUNT - binary.length()) + binary;
    }

    /**
     * Returns whether all bits in this container are currently cleared.
     *
     * @return {@code true} if the stored value is zero, otherwise {@code false}
     */
    public boolean isEmpty() {
        return bits == 0L;
    }

    /**
     * Returns the number of set bits currently stored in this container.
     *
     * <p>
     * This counts how many bit positions currently contain {@code 1}.
     * </p>
     *
     * @return the population count of the current mask
     */
    public int size() {
        return Long.bitCount(bits);
    }

    /**
     * Clears every bit in this container.
     *
     * <p>
     * After this call, the stored mask becomes zero.
     * </p>
     */
    public void clearAll() {
        bits = 0L;
    }

    /**
     * Toggles the bit at the specified index.
     *
     * <p>
     * A set bit becomes clear and a clear bit becomes set.
     * </p>
     *
     * @param index the bit index to flip
     * @throws IndexOutOfBoundsException if {@code index} is outside {@code 0..63}
     */
    public void flip(int index) {
        validateIndex(index);
        bits ^= mask(index);
    }

    /**
     * Applies a bitwise XOR operation using another {@code LongBits} instance.
     *
     * <p>
     * Every differing bit between the two masks becomes set and every matching bit becomes clear.
     * </p>
     *
     * @param other the other bit container to XOR with
     * @throws NullPointerException if {@code other} is null
     */
    public void xor(LongBits other) {
        if (other == null) {
            throw new NullPointerException("other");
        }
        this.bits ^= other.bits;
    }

    /**
     * Applies a bitwise AND operation using another {@code LongBits} instance.
     *
     * <p>
     * Only bits that are set in both masks remain set afterward.
     * </p>
     *
     * @param other the other bit container to AND with
     * @throws NullPointerException if {@code other} is null
     */
    public void and(LongBits other) {
        if (other == null) {
            throw new NullPointerException("other");
        }
        this.bits &= other.bits;
    }

    /**
     * Applies a bitwise OR operation using another {@code LongBits} instance.
     *
     * <p>
     * Any bit set in either mask becomes set in this instance afterward.
     * </p>
     *
     * @param other the other bit container to OR with
     * @throws NullPointerException if {@code other} is null
     */
    public void or(LongBits other) {
        if (other == null) {
            throw new NullPointerException("other");
        }
        this.bits |= other.bits;
    }

    /**
     * Creates and returns a copy of this {@code LongBits} instance.
     *
     * <p>
     * The returned object stores the same raw bit pattern but is fully independent from the
     * original instance.
     * </p>
     *
     * @return a new {@code LongBits} containing the same bit pattern
     */
    @Override
    public LongBits clone() {
        return new LongBits(this.bits);
    }

    /**
     * Finds the next clear bit starting at the specified index.
     *
     * <p>
     * The scan begins at {@code fromIndex} and proceeds upward toward bit {@code 63}. If no clear
     * bit exists in that range, this method returns {@code -1}.
     * </p>
     *
     * @param fromIndex the first index to inspect
     * @return the index of the next clear bit, or {@code -1} if none exists
     * @throws IndexOutOfBoundsException if {@code fromIndex} is outside {@code 0..63}
     */
    public int nextClearBit(int fromIndex) {
        validateIndex(fromIndex);
        for (int i = fromIndex; i < BIT_COUNT; i++) {
            if ((bits & mask(i)) == 0L) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Finds the next set bit starting at the specified index.
     *
     * <p>
     * The scan begins at {@code fromIndex} and proceeds upward toward bit {@code 63}. If no set
     * bit exists in that range, this method returns {@code -1}.
     * </p>
     *
     * @param fromIndex the first index to inspect
     * @return the index of the next set bit, or {@code -1} if none exists
     * @throws IndexOutOfBoundsException if {@code fromIndex} is outside {@code 0..63}
     */
    public int nextSetBit(int fromIndex) {
        validateIndex(fromIndex);
        for (int i = fromIndex; i < BIT_COUNT; i++) {
            if ((bits & mask(i)) != 0L) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Returns whether this instance stores exactly the same raw bit pattern as another
     * {@code LongBits} instance.
     *
     * @param other the other bit container to compare against
     * @return {@code true} if both instances contain the exact same bits
     * @throws NullPointerException if {@code other} is null
     */
    public boolean matches(LongBits other) {
        if (other == null) {
            throw new NullPointerException("other");
        }
        return this.bits == other.bits;
    }

    /**
     * Returns whether this instance stores exactly the same raw bit pattern as the supplied long.
     *
     * @param bits the raw bit pattern to compare against
     * @return {@code true} if the stored mask matches exactly
     */
    public boolean matches(long bits) {
        return this.bits == bits;
    }

    /**
     * Returns whether all set bits in the supplied {@code LongBits} are also set in this instance.
     *
     * <p>
     * This checks containment, not exact equality.
     * </p>
     *
     * @param bits the mask whose set bits must all exist in this instance
     * @return {@code true} if this instance fully contains the supplied mask
     * @throws NullPointerException if {@code bits} is null
     */
    public boolean allMatch(LongBits bits) {
        if (bits == null) {
            throw new NullPointerException("bits");
        }
        return (this.bits & bits.bits) == bits.bits;
    }

    /**
     * Returns whether all set bits in the supplied raw long are also set in this instance.
     *
     * @param bits the raw bit mask to test for containment
     * @return {@code true} if this instance contains the supplied mask
     */
    public boolean allMatch(long bits) {
        return (this.bits & bits) == bits;
    }

    /**
     * Returns whether this instance shares at least one set bit with another {@code LongBits}.
     *
     * <p>
     * This is useful when you only care whether two masks overlap at all.
     * </p>
     *
     * @param other the other bit container to compare against
     * @return {@code true} if at least one bit is set in both masks
     * @throws NullPointerException if {@code other} is null
     */
    public boolean anyMatch(LongBits other) {
        if (other == null) {
            throw new NullPointerException("other");
        }
        return (this.bits & other.bits) != 0L;
    }

    /**
     * Returns the raw long value currently stored by this container.
     *
     * @return the packed long bit mask
     */
    public long getBits() {
        return bits;
    }

    /**
     * Validates that a bit index falls within the valid long bit range.
     *
     * @param index the index to validate
     * @throws IndexOutOfBoundsException if the index is outside {@code 0..63}
     */
    private static void validateIndex(int index) {
        if (index < 0 || index >= BIT_COUNT) {
            throw new IndexOutOfBoundsException("Bit index must be between 0 and 63: " + index);
        }
    }

    /**
     * Returns the mask for the specified bit index.
     *
     * <p>
     * This method assumes the index has already been validated.
     * </p>
     *
     * @param index the validated bit index
     * @return a long mask with that bit position enabled
     */
    private static long mask(int index) {
        return 1L << index;
    }
}