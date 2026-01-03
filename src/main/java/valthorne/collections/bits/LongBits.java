package valthorne.collections.bits;

/**
 * The LongBits class represents a collection of bits stored in a long. It provides
 * basic operations for setting, clearing, finding the next set or clear bit, and
 * comparing with another instance.
 *
 * @author Albert Beaupre
 * @version 1.0
 * @since May 1st, 2024
 */
public class LongBits {
    private long bits;

    /**
     * Retrieves the state of the bit at the specified index.
     *
     * @param index The index of the bit to retrieve. Must be a non-negative integer.
     * @return true if the bit at the specified index is set (1), false if it is unset (0).
     */
    public boolean get(int index) {
        return (bits & (1 << index)) != 0;
    }

    /**
     * Sets the bit at the specified index to 1.
     *
     * @param index The index of the bit to set.
     */
    public void set(int index) {
        bits |= (1L << index);
    }

    /**
     * Clears the bit at the specified index, setting it to 0.
     *
     * @param index The index of the bit to clear.
     */
    public void clear(int index) {
        bits &= ~(1L << index);
    }

    /**
     * Sets or clears the bit at the specified index based on the given boolean value.
     *
     * @param index The index of the bit to modify.
     * @param set If true, sets the bit at the specified index to 1. If false, clears the bit at the specified index, setting it to 0.
     */
    public void set(int index, boolean set){
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
        return Long.toBinaryString(bits);
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
        for (int i = 0; i < Long.SIZE; i++) {
            if ((bits & (1L << i)) != 0) {
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
        bits ^= (1L << index);
    }

    /**
     * Performs a bitwise XOR operation with another instance.
     *
     * @param other The other LongBits instance for XOR operation.
     */
    public void xor(LongBits other) {
        this.bits ^= other.bits;
    }

    /**
     * Performs a bitwise AND operation with another instance.
     *
     * @param other The other LongBits instance for AND operation.
     */
    public void and(LongBits other) {
        this.bits &= other.bits;
    }

    /**
     * Performs a bitwise OR operation with another instance.
     *
     * @param other The other LongBits instance for OR operation.
     */
    public void or(LongBits other) {
        this.bits |= other.bits;
    }

    /**
     * Creates a copy of the current instance.
     *
     * @return A copy of the current instance.
     */
    public LongBits clone() {
        LongBits copy = new LongBits();
        copy.bits = this.bits;
        return copy;
    }

    /**
     * Finds and returns the index of the next clear bit, starting from the specified index.
     *
     * @param fromIndex The starting index for the search.
     * @return The index of the next clear bit, or -1 if no clear bit is found.
     */
    public int nextClearBit(int fromIndex) {
        for (int i = fromIndex; i < Long.SIZE; i++) {
            if ((bits & (1L << i)) == 0) {
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
        for (int i = fromIndex; i < Long.SIZE; i++) {
            if ((bits & (1L << i)) != 0) {
                return i;
            }
        }
        return -1; // No set bit found
    }

    /**
     * Checks if the same bits are set as another instance of LongBits.
     *
     * @param other The other LongBits instance to compare.
     * @return true if the bits are the same, false otherwise.
     */
    public boolean matches(LongBits other) {
        return this.bits == other.bits;
    }

    /**
     * Checks if any of the bits are similar to another instance of LongBits.
     *
     * @param other The other LongBits instance to compare.
     * @return true if any of the bits are the same, false otherwise.
     */
    public boolean anyMatch(LongBits other) {
        return (this.bits & other.bits) != 0;
    }

    /**
     * Gets the value of the bits stored in the long.
     *
     * @return The long containing the bits.
     */
    public long getBits() {
        return bits;
    }

}
