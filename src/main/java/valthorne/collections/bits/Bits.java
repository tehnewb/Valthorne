package valthorne.collections.bits;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/**
 * A custom bit manipulation class for managing bits more efficiently than java.util.BitSet,
 * especially when dealing with more than 64 bits.
 * This class allows for setting, clearing, and retrieving bits at specific indices.
 * <p>
 * This class should be chosen over java.util.BitSet if you want slightly faster operations with bits.
 * This class is also unsafe compared to java.util.BitSet as there are no checks for incorrect parameters
 * and no errors thrown specific to this class if an illegal operation has occurred. The purpose for this is to remove
 * as many branches as possible.
 *
 * @author Albert Beaupre
 * @version 1.0
 * @since May 1st, 2024
 */
public class Bits {

    /**
     * The internal array of longs holding the bits.
     */
    private long[] words;

    /**
     * The number of bits in the collection.
     */
    private int size;

    /**
     * Initializes a new instance of the Bits class with the specified size.
     *
     * @param size The number of bits in the Bits collection.
     */
    public Bits(int size) {
        words = new long[((size - 1) >> 6) + 1];
    }

    /**
     * Creates a new Bits instance from the given byte array.
     * The bits are read from the byte array in little-endian order.
     *
     * @param byteArray The byte array representing the Bits.
     * @return A new Bits instance with bits set according to the byte array.
     */
    public static Bits valueOf(byte[] byteArray) {
        int size = byteArray.length * 8;
        Bits bits = new Bits(size);

        for (int i = 0; i < byteArray.length; i++) {
            for (int j = 0; j < 8; j++) {
                int bitIndex = i * 8 + j;
                if (bitIndex < size) {
                    if ((byteArray[i] & (1 << j)) != 0) {
                        bits.set(bitIndex);
                    }
                } else {
                    break;
                }
            }
        }
        return bits;
    }

    /**
     * Sets the bit at the specified index to 1 (true).
     * If the index is outside the current capacity, the internal array is resized.
     *
     * @param index The index of the bit to set.
     */
    public void set(int index) {
        if (index >= words.length * 64) { // increase size to next power of 2
            long[] newWords = new long[words.length * 2];
            System.arraycopy(words, 0, newWords, 0, words.length);
            words = newWords;
        }

        long word = words[index >> 6];
        long mask = 1L << index;

        if ((word & mask) == 0) {
            words[index >> 6] = word | mask;
            size++;
        }
    }

    /**
     * Clears the bit at the specified index, setting it to 0 (false).
     *
     * @param index The index of the bit to clear.
     */
    public void clear(int index) {
        if (index >> 6 >= words.length)
            return;
        long word = words[index >> 6];
        long mask = 1L << index;

        if ((word & mask) != 0) {
            words[index >> 6] = word & ~mask;
            size--;
        }
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
     * Gets the state of the bit at the specified index.
     * If the index is out of bounds, false is returned.
     *
     * @param index The index of the bit to check.
     * @return true if the bit is set (1), false if it's clear (0).
     */
    public boolean get(int index) {
        if (index >> 6 >= words.length)
            return false;
        return ((words[index >> 6] & (1L << index)) != 0);
    }

    /**
     * Finds and returns the index of the next set bit (bit with value 1) starting from the specified index.
     *
     * @param fromIndex The starting index for the search.
     * @return The index of the next set bit or -1 if none are found.
     */
    public int nextSetBit(int fromIndex) {
        int u = fromIndex >>> 6;
        if (u >= words.length) return -1;
        long mask = words[u] >>> fromIndex;
        if (mask != 0) return fromIndex + Long.numberOfTrailingZeros(mask);

        for (int i = u + 1; i < words.length; i++) {
            if ((mask = words[i]) != 0) {
                return i * 64 + Long.numberOfTrailingZeros(mask);
            }
        }

        return -1;
    }

    /**
     * Finds and returns the index of the next clear bit (bit with value 0) starting from the specified index.
     *
     * @param fromIndex The starting index for the search.
     * @return The index of the next clear bit or -1 if none are found.
     */
    public int nextClearBit(int fromIndex) {
        int u = fromIndex >>> 6;

        if (u >= words.length) return Math.min(fromIndex, words.length << 6);

        long mask = ~(words[u] >>> fromIndex);

        if (mask != 0) return fromIndex + Long.numberOfTrailingZeros(mask);

        for (int i = u + 1; i < words.length; i++) {
            if ((mask = ~words[i]) != 0) {
                return i * 64 + Long.numberOfTrailingZeros(mask);
            }
        }
        return -1;
    }

    /**
     * Clears all bits in the Bits collection, setting them to 0.
     */
    public void clearAll() {
        Arrays.fill(words, 0);
    }

    /**
     * Resize the internal array to the specified size.
     *
     * @param size The new size of the Bits collection.
     */
    public void resizeTo(int size) {
        this.words = Arrays.copyOf(words, ((size - 1) >> 6) + 1);
    }

    /**
     * Gets the current size of the Bits collection.
     *
     * @return The number of bits in the Bits collection.
     */
    public int size() {
        return size;
    }

    /**
     * Converts the Bits to a byte array.
     * The resulting byte array represents the bits stored in little-endian order.
     *
     * @return A byte array representing the Bits.
     */
    public byte[] toByteArray() {
        int n = words.length;
        int len = 8 * (n - 1);
        for (long x = words[n - 1]; x != 0; x >>>= 8)
            len++;
        byte[] bytes = new byte[len];
        ByteBuffer bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < n - 1; i++)
            bb.putLong(words[i]);
        for (long x = words[n - 1]; x != 0; x >>>= 8)
            bb.put((byte) (x & 0xff));
        return bytes;
    }

    /**
     * Returns a binary string representation of the Bits collection, showing the bits stored in the long words.
     *
     * @return A binary string representing the contents of the Bits collection.
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Bits[");
        for (long word : words) {
            String binaryString = Long.toBinaryString(word);
            int padding = 64 - binaryString.length();
            String paddedBinaryString = "0".repeat(padding) + binaryString;
            sb.append(" ");
            sb.append(paddedBinaryString);
        }
        sb.append("]");
        return sb.toString();
    }

}