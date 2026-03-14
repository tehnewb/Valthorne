package valthorne.collections.bits;

/**
 * <h1>ByteBits</h1>
 *
 * <p>
 * {@code ByteBits} is a compact bit container backed by a single {@code byte}. It is useful when
 * you need to store up to eight on/off flags in one small value while still being able to query,
 * modify, compare, and scan those flags through a readable object-oriented API.
 * </p>
 *
 * <p>
 * Each bit index maps directly to one bit inside the internal byte:
 * </p>
 * <ul>
 *     <li>Index {@code 0} is the least significant bit</li>
 *     <li>Index {@code 7} is the most significant bit</li>
 * </ul>
 *
 * <p>
 * This class is appropriate for cases such as:
 * </p>
 * <ul>
 *     <li>storing small permission masks</li>
 *     <li>tracking component or state flags</li>
 *     <li>encoding boolean toggles compactly</li>
 *     <li>working with binary protocols that use one byte of flags</li>
 * </ul>
 *
 * <h2>Behavior notes</h2>
 *
 * <p>
 * All bit operations in this class are restricted to the valid byte index range of {@code 0..7}.
 * Any attempt to access a bit outside that range throws an {@link IndexOutOfBoundsException}.
 * This makes the API safer and prevents accidental misuse that would otherwise silently operate on
 * values outside the intended byte-width contract.
 * </p>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * ByteBits flags = new ByteBits();
 *
 * flags.set(0);
 * flags.set(3);
 * flags.set(7, true);
 *
 * boolean hasBit3 = flags.get(3);
 * int firstSet = flags.nextSetBit(0);
 * int firstClear = flags.nextClearBit(0);
 * int count = flags.size();
 *
 * System.out.println(flags);        // Example: 10001001
 * System.out.println(hasBit3);      // true
 * System.out.println(firstSet);     // 0
 * System.out.println(firstClear);   // 1
 * System.out.println(count);        // 3
 *
 * flags.flip(3);
 * flags.clear(7);
 *
 * ByteBits required = new ByteBits((byte) 0b00000001);
 * boolean containsRequired = flags.allMatch(required);
 * }</pre>
 *
 * @author Albert Beaupre
 * @since May 1st, 2024
 */
public class ByteBits {

    /**
     * The total number of addressable bits inside one byte.
     */
    private static final int BIT_COUNT = Byte.SIZE;

    private byte bits; // The raw byte value that stores all eight bit flags.

    /**
     * Creates a new {@code ByteBits} instance initialized with the provided raw byte value.
     *
     * <p>
     * This constructor is useful when you already have a packed flag byte from another source,
     * such as file data, network data, serialization, or a previously computed bit mask.
     * </p>
     *
     * @param bits the initial raw bit pattern to store
     */
    public ByteBits(byte bits) {
        this.bits = bits;
    }

    /**
     * Creates a new {@code ByteBits} instance with all bits cleared.
     *
     * <p>
     * After construction, every bit from index {@code 0} through {@code 7} is unset.
     * </p>
     */
    public ByteBits() {
        this.bits = 0;
    }

    /**
     * Returns whether the bit at the specified index is currently set.
     *
     * <p>
     * The provided index must be in the inclusive range {@code 0..7}. This method checks the
     * corresponding bit in the internal byte and returns {@code true} when that bit is {@code 1}.
     * </p>
     *
     * @param index the bit index to read
     * @return {@code true} if the bit is set, otherwise {@code false}
     * @throws IndexOutOfBoundsException if {@code index} is outside {@code 0..7}
     */
    public boolean get(int index) {
        validateIndex(index);
        return (bits & mask(index)) != 0;
    }

    /**
     * Sets the bit at the specified index to {@code 1}.
     *
     * <p>
     * This operation preserves every other bit and only enables the requested flag.
     * </p>
     *
     * @param index the bit index to set
     * @throws IndexOutOfBoundsException if {@code index} is outside {@code 0..7}
     */
    public void set(int index) {
        validateIndex(index);
        bits = (byte) (bits | mask(index));
    }

    /**
     * Clears the bit at the specified index to {@code 0}.
     *
     * <p>
     * This operation preserves every other bit and only disables the requested flag.
     * </p>
     *
     * @param index the bit index to clear
     * @throws IndexOutOfBoundsException if {@code index} is outside {@code 0..7}
     */
    public void clear(int index) {
        validateIndex(index);
        bits = (byte) (bits & ~mask(index));
    }

    /**
     * Sets or clears the bit at the specified index based on the supplied boolean value.
     *
     * <p>
     * When {@code set} is {@code true}, this behaves the same as {@link #set(int)}.
     * When {@code set} is {@code false}, this behaves the same as {@link #clear(int)}.
     * </p>
     *
     * @param index the bit index to modify
     * @param set {@code true} to set the bit, {@code false} to clear it
     * @throws IndexOutOfBoundsException if {@code index} is outside {@code 0..7}
     */
    public void set(int index, boolean set) {
        if (set) {
            set(index);
        } else {
            clear(index);
        }
    }

    /**
     * Returns a binary string representation of the stored bits.
     *
     * <p>
     * Unlike {@link Integer#toBinaryString(int)} alone, this method always returns a fixed-width
     * 8-character binary string so the full byte pattern is visible, including leading zeroes.
     * </p>
     *
     * @return an 8-character binary representation of the current bit state
     */
    @Override
    public String toString() {
        String binary = Integer.toBinaryString(Byte.toUnsignedInt(bits));
        return "0".repeat(BIT_COUNT - binary.length()) + binary;
    }

    /**
     * Returns whether all bits in this container are currently cleared.
     *
     * <p>
     * This is equivalent to checking whether the raw byte value is zero.
     * </p>
     *
     * @return {@code true} if no bits are set, otherwise {@code false}
     */
    public boolean isEmpty() {
        return bits == 0;
    }

    /**
     * Returns the number of set bits currently stored in this byte.
     *
     * <p>
     * This method counts how many of the eight bit positions currently contain {@code 1}.
     * </p>
     *
     * @return the number of enabled bits
     */
    public int size() {
        return Integer.bitCount(Byte.toUnsignedInt(bits));
    }

    /**
     * Clears every bit in this container.
     *
     * <p>
     * After this call, {@link #isEmpty()} will return {@code true}.
     * </p>
     */
    public void clearAll() {
        bits = 0;
    }

    /**
     * Flips the bit at the specified index.
     *
     * <p>
     * If the bit is currently set, it becomes cleared. If it is currently cleared, it becomes set.
     * </p>
     *
     * @param index the bit index to flip
     * @throws IndexOutOfBoundsException if {@code index} is outside {@code 0..7}
     */
    public void flip(int index) {
        validateIndex(index);
        bits = (byte) (bits ^ mask(index));
    }

    /**
     * Finds the index of the next clear bit starting at the supplied index.
     *
     * <p>
     * The search begins at {@code fromIndex} and proceeds upward through the byte. If a clear bit
     * is found, its index is returned immediately. If no clear bit exists in the remaining range,
     * this method returns {@code -1}.
     * </p>
     *
     * @param fromIndex the starting bit index for the scan
     * @return the index of the next clear bit, or {@code -1} if none is found
     * @throws IndexOutOfBoundsException if {@code fromIndex} is outside {@code 0..7}
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
     * Finds the index of the next set bit starting at the supplied index.
     *
     * <p>
     * The search begins at {@code fromIndex} and proceeds upward through the byte. If a set bit
     * is found, its index is returned immediately. If no set bit exists in the remaining range,
     * this method returns {@code -1}.
     * </p>
     *
     * @param fromIndex the starting bit index for the scan
     * @return the index of the next set bit, or {@code -1} if none is found
     * @throws IndexOutOfBoundsException if {@code fromIndex} is outside {@code 0..7}
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
     * Returns whether this instance shares at least one set bit with another {@code ByteBits}.
     *
     * <p>
     * This performs a bitwise AND between the two values and checks whether the result is non-zero.
     * It is useful when you want to know whether two flag sets overlap at all.
     * </p>
     *
     * @param other the other bit container to compare against
     * @return {@code true} if both instances have at least one common set bit, otherwise {@code false}
     * @throws NullPointerException if {@code other} is null
     */
    public boolean anyMatch(ByteBits other) {
        if (other == null) {
            throw new NullPointerException("other");
        }
        return (this.bits & other.bits) != 0;
    }

    /**
     * Returns whether this instance shares at least one set bit with the supplied raw byte.
     *
     * <p>
     * This is the raw-byte overload of {@link #anyMatch(ByteBits)}.
     * </p>
     *
     * @param bits the raw bit pattern to compare against
     * @return {@code true} if at least one corresponding bit is set in both values
     */
    public boolean anyMatch(byte bits) {
        return (this.bits & bits) != 0;
    }

    /**
     * Returns whether all set bits in the supplied {@code ByteBits} are also set in this instance.
     *
     * <p>
     * This method does not require the two byte values to be identical. It only checks whether this
     * instance fully contains the bit mask represented by the supplied argument.
     * </p>
     *
     * @param bits the bit container whose set bits must all be present in this instance
     * @return {@code true} if every set bit in {@code bits} is also set here
     * @throws NullPointerException if {@code bits} is null
     */
    public boolean allMatch(ByteBits bits) {
        if (bits == null) {
            throw new NullPointerException("bits");
        }
        return (this.bits & bits.bits) == bits.bits;
    }

    /**
     * Returns whether all set bits in the supplied raw byte are also set in this instance.
     *
     * <p>
     * This is the raw-byte overload of {@link #allMatch(ByteBits)}.
     * </p>
     *
     * @param bits the raw bit mask to test for containment
     * @return {@code true} if every set bit in {@code bits} is also set in this instance
     */
    public boolean allMatch(byte bits) {
        return (this.bits & bits) == bits;
    }

    /**
     * Returns whether this instance stores exactly the same raw bit pattern as another
     * {@code ByteBits} instance.
     *
     * <p>
     * This is a strict equality check on the internal byte value.
     * </p>
     *
     * @param other the other bit container to compare against
     * @return {@code true} if both instances have identical bit patterns
     * @throws NullPointerException if {@code other} is null
     */
    public boolean matches(ByteBits other) {
        if (other == null) {
            throw new NullPointerException("other");
        }
        return this.bits == other.bits;
    }

    /**
     * Returns whether this instance stores exactly the same raw bit pattern as the supplied byte.
     *
     * @param bits the raw byte to compare against
     * @return {@code true} if the stored byte matches the supplied byte exactly
     */
    public boolean matches(byte bits) {
        return this.bits == bits;
    }

    /**
     * Returns the raw byte value currently stored by this instance.
     *
     * <p>
     * This method is useful when you need to serialize, transmit, or otherwise work directly with the
     * packed byte representation.
     * </p>
     *
     * @return the stored raw bit byte
     */
    public byte getBits() {
        return bits;
    }

    /**
     * Validates that a bit index is within the valid byte range.
     *
     * <p>
     * The valid inclusive range is {@code 0..7}. Any value outside that range causes an
     * {@link IndexOutOfBoundsException}.
     * </p>
     *
     * @param index the bit index to validate
     * @throws IndexOutOfBoundsException if the index is outside {@code 0..7}
     */
    private static void validateIndex(int index) {
        if (index < 0 || index >= BIT_COUNT) {
            throw new IndexOutOfBoundsException("Bit index must be between 0 and 7: " + index);
        }
    }

    /**
     * Returns the bit mask for the specified index.
     *
     * <p>
     * This method assumes the index has already been validated.
     * </p>
     *
     * @param index the validated bit index
     * @return the integer mask representing that bit position
     */
    private static int mask(int index) {
        return 1 << index;
    }
}