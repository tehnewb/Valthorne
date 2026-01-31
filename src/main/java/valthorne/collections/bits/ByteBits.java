package valthorne.collections.bits;

/**
 * The ByteBits class represents a collection of bits stored in a byte. It provides
 * basic operations for setting, clearing, finding the next set or clear bit, and
 * comparing with another instance.
 *
 * @author Albert Beaupre
 * @version 1.0
 * @since May 1st, 2024
 */
public class ByteBits {
    private byte bits;

    /**
     * Retrieves the state of the bit at the specified index.
     *
     * @param index The index of the bit to retrieve. Must be a non-negative integer.
     * @return true if the bit at the specified index is set (1), false if it is unset (0).
     */
    public boolean get(int index) {
        return (bits & (1L << index)) != 0;
    }

    /**
     * Sets the bit at the specified index to 1.
     *
     * @param index The index of the bit to set.
     */
    public void set(int index) {
        bits |= (1 << index);
    }

    /**
     * Clears the bit at the specified index, setting it to 0.
     *
     * @param index The index of the bit to clear.
     */
    public void clear(int index) {
        bits &= ~(1 << index);
    }

    /**
     * Sets or clears the bit at the specified index based on the given boolean value.
     *
     * @param index The index of the bit to modify.
     * @param set   If true, sets the bit at the specified index to 1. If false, clears the bit at the specified index, setting it to 0.
     */
    public void set(int index, boolean set) {
        if (set) set(index);
        else clear(index);
    }

    /**
     * Gets a string representation of the bits.
     *
     * @return A string representing the bits.
     */
    @Override
    public String toString() {
        return Integer.toBinaryString(Byte.toUnsignedInt(bits));
    }

    /**
     * Determines if all bits are unset (0).
     *
     * @return true if all bits are unset, false otherwise.
     */
    public boolean isEmpty() {
        return bits == 0;
    }

    /**
     * Calculates the number of set bits.
     *
     * @return The number of set bits.
     */
    public int size() {
        int count = 0;
        for (int i = 0; i < Byte.SIZE; i++) {
            if ((bits & (1 << i)) != 0) {
                count++;
            }
        }
        return count;
    }

    /**
     * Clears all bits in the collection.
     */
    public void clearAll() {
        bits = 0;
    }

    /**
     * Toggles the value of a specific bit.
     *
     * @param index The index of the bit to flip.
     */
    public void flip(int index) {
        bits ^= (1 << index);
    }

    /**
     * Finds and returns the index of the next clear bit, starting from the specified index.
     *
     * @param fromIndex The starting index for the search.
     * @return The index of the next clear bit, or -1 if no clear bit is found.
     */
    public int nextClearBit(int fromIndex) {
        for (int i = fromIndex; i < Byte.SIZE; i++) {
            if ((bits & (1 << i)) == 0) {
                return i;
            }
        }
        return -1; // No clear bit found
    }

    /**
     * Finds and returns the index of the next set bit, starting from the specified index.
     *
     * @param fromIndex The starting index for the search.
     * @return The index of the next set bit, or -1 if no set bit is found.
     */
    public int nextSetBit(int fromIndex) {
        for (int i = fromIndex; i < Byte.SIZE; i++) {
            if ((bits & (1 << i)) != 0) {
                return i;
            }
        }
        return -1; // No set bit found
    }

    /**
     * Checks if any of the bits are similar to another instance of ByteBits.
     *
     * @param other The other ByteBits instance to compare.
     * @return true if any of the bits are the same, false otherwise.
     */
    public boolean anyMatch(ByteBits other) {
        return (this.bits & other.bits) != 0;
    }

    /**
     * Gets the value of the bits stored in the byte.
     *
     * @return The byte containing the bits.
     */
    public byte getBits() {
        return bits;
    }

}