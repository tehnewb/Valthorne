package valthorne.collections.bits;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/**
 * <h1>Bits</h1>
 *
 * <p>
 * {@code Bits} is a dynamic bit container backed by a {@code long[]} where each
 * {@code long} stores 64 individual bit states. This class is intended for
 * compact, fast, low-level bit manipulation when you want direct control over
 * bit storage without the heavier behavior of higher-level abstractions.
 * </p>
 *
 * <p>
 * Internally, bit indices are mapped into the {@link #words} array using
 * 64-bit word segmentation:
 * </p>
 *
 * <ul>
 *     <li>The word index is computed with {@code index >> 6}</li>
 *     <li>The bit mask is computed with {@code 1L << index}</li>
 *     <li>Each word stores the enabled/disabled state of 64 consecutive bits</li>
 * </ul>
 *
 * <p>
 * This class supports:
 * </p>
 *
 * <ul>
 *     <li>setting and clearing individual bits</li>
 *     <li>reading individual bit states</li>
 *     <li>finding the next set bit</li>
 *     <li>finding the next clear bit</li>
 *     <li>resizing the underlying storage</li>
 *     <li>serializing to and from little-endian byte arrays</li>
 * </ul>
 *
 * <p>
 * The {@link #size} field tracks how many bits are currently enabled, not the
 * total storage capacity. That means {@link #size()} returns the number of set
 * bits currently stored in this container.
 * </p>
 *
 * <p>
 * This implementation is intentionally lightweight and performance-oriented.
 * It does not validate every possible incorrect input and does not attempt to
 * provide the same safety guarantees as {@link java.util.BitSet}. It is better
 * suited for internal engine code, data structures, masks, occupancy flags,
 * serialization helpers, and other systems where raw speed and compact storage
 * matter more than defensive guardrails.
 * </p>
 *
 * <h2>Example</h2>
 *
 * <pre>{@code
 * Bits bits = new Bits(128);
 *
 * bits.set(0);
 * bits.set(5);
 * bits.set(70);
 *
 * System.out.println(bits.get(0));   // true
 * System.out.println(bits.get(1));   // false
 *
 * bits.clear(5);
 *
 * int nextSet = bits.nextSetBit(0);      // 0
 * int nextClear = bits.nextClearBit(0);  // usually 1
 *
 * byte[] raw = bits.toByteArray();
 * Bits restored = Bits.valueOf(raw);
 *
 * System.out.println(restored);
 * System.out.println(restored.size());
 * }</pre>
 *
 * @author Albert Beaupre
 * @version 1.0
 * @since May 1st, 2024
 */
public class Bits {

    private long[] words; // The packed 64-bit word array that stores all bit states.
    private int size; // The number of bits currently set to true in this container.

    /**
     * Creates a new {@code Bits} container with enough internal storage to
     * address the requested number of bit positions.
     *
     * <p>
     * The backing {@link #words} array is sized by rounding the requested bit
     * count up to the nearest 64-bit word boundary. No bits are enabled during
     * construction, so the initial set-bit count remains zero.
     * </p>
     *
     * <p>
     * Examples:
     * </p>
     *
     * <ul>
     *     <li>{@code new Bits(1)} allocates one 64-bit word</li>
     *     <li>{@code new Bits(64)} allocates one 64-bit word</li>
     *     <li>{@code new Bits(65)} allocates two 64-bit words</li>
     * </ul>
     *
     * @param size the initial logical bit capacity to support
     */
    public Bits(int size) {
        words = new long[((size - 1) >> 6) + 1];
    }

    /**
     * Creates a new {@code Bits} instance from a raw byte array.
     *
     * <p>
     * The byte array is interpreted in little-endian bit order. Each byte
     * contributes 8 bits, and each set source bit is copied into the returned
     * {@code Bits} object at the corresponding absolute bit index.
     * </p>
     *
     * <p>
     * This method constructs a {@code Bits} instance sized to
     * {@code byteArray.length * 8}, then replays all enabled bits through
     * {@link #set(int)} so the internal set-bit count is built correctly.
     * </p>
     *
     * @param byteArray the raw byte data to decode into a bit container
     * @return a new {@code Bits} instance representing the given bytes
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
     * Enables the bit at the given index.
     *
     * <p>
     * If the requested bit lies outside the currently allocated word range,
     * the backing array is expanded by doubling its length. After capacity
     * has been ensured, the target word and bit mask are computed and the
     * bit is enabled.
     * </p>
     *
     * <p>
     * The set-bit count {@link #size} is only incremented when the bit was
     * previously clear. Calling this method on an already enabled bit leaves
     * the count unchanged.
     * </p>
     *
     * @param index the zero-based bit index to enable
     */
    public void set(int index) {
        if (index >= words.length * 64) {
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
     * Clears the bit at the given index.
     *
     * <p>
     * If the requested bit lies outside the current backing storage, the method
     * returns immediately because that bit is already effectively clear.
     * </p>
     *
     * <p>
     * If the target bit exists and is currently enabled, this method disables it
     * and decrements the tracked set-bit count {@link #size}. Clearing a bit that
     * is already clear does nothing.
     * </p>
     *
     * @param index the zero-based bit index to clear
     */
    public void clear(int index) {
        if (index >> 6 >= words.length) return;

        long word = words[index >> 6];
        long mask = 1L << index;

        if ((word & mask) != 0) {
            words[index >> 6] = word & ~mask;
            size--;
        }
    }

    /**
     * Sets or clears the bit at the given index depending on the provided flag.
     *
     * <p>
     * This is a convenience overload that forwards to {@link #set(int)} when
     * {@code set} is true, and to {@link #clear(int)} when {@code set} is false.
     * </p>
     *
     * @param index the zero-based bit index to modify
     * @param set   true to enable the bit, false to clear it
     */
    public void set(int index, boolean set) {
        if (set) set(index);
        else clear(index);
    }

    /**
     * Returns whether the bit at the given index is currently enabled.
     *
     * <p>
     * If the requested index maps outside the allocated word range, this method
     * returns {@code false}. Otherwise it checks the target word using a bit mask.
     * </p>
     *
     * @param index the zero-based bit index to inspect
     * @return true if the bit is enabled, otherwise false
     */
    public boolean get(int index) {
        if (index >> 6 >= words.length) return false;

        return (words[index >> 6] & (1L << index)) != 0;
    }

    /**
     * Finds the next enabled bit starting at or after the supplied index.
     *
     * <p>
     * This method performs an efficient forward scan. It first checks the word
     * containing {@code fromIndex} by shifting away all bits before the requested
     * starting position. If any enabled bits remain, the exact index is found
     * with {@link Long#numberOfTrailingZeros(long)}.
     * </p>
     *
     * <p>
     * If no set bit exists in that partially scanned word, later words are
     * checked one by one until a non-zero word is found.
     * </p>
     *
     * @param fromIndex the first index that is allowed to match
     * @return the index of the next enabled bit, or {@code -1} if none exists
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
     * Finds the next clear bit starting at or after the supplied index.
     *
     * <p>
     * This method mirrors {@link #nextSetBit(int)} but searches for disabled bits
     * instead of enabled bits. It inverts scanned words so clear bits become
     * detectable through the same trailing-zero scan approach.
     * </p>
     *
     * <p>
     * If the requested starting word lies beyond the current backing array, the
     * original implementation returns the smaller of the requested index and the
     * current total bit capacity represented by {@code words.length << 6}. That
     * behavior is preserved here exactly.
     * </p>
     *
     * @param fromIndex the first index that is allowed to match
     * @return the index of the next clear bit, or {@code -1} if none is found
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
     * Clears all stored bits in this container.
     *
     * <p>
     * This method fills the entire backing word array with zeroes, making every
     * tracked bit disabled.
     * </p>
     *
     * <p>
     * This implementation preserves your original logic exactly and therefore does
     * not update {@link #size}. That means the tracked set-bit count may no longer
     * reflect the actual number of enabled bits after this method runs unless the
     * count is rebuilt separately.
     * </p>
     */
    public void clearAll() {
        Arrays.fill(words, 0);
    }

    /**
     * Resizes the internal backing storage to support the requested logical bit range.
     *
     * <p>
     * This method resizes the {@link #words} array to exactly the number of 64-bit
     * words needed to address the supplied size. Existing data is preserved up to the
     * new array length.
     * </p>
     *
     * <p>
     * If the new size is smaller than the current capacity, higher words may be
     * truncated. This method does not recompute {@link #size}, so the stored count
     * remains whatever it was before resizing.
     * </p>
     *
     * @param size the new logical bit capacity to support
     */
    public void resizeTo(int size) {
        this.words = Arrays.copyOf(words, ((size - 1) >> 6) + 1);
    }

    /**
     * Returns the number of currently enabled bits.
     *
     * <p>
     * This value is not the total addressable capacity. It is the tracked count of
     * bits currently set to {@code true}.
     * </p>
     *
     * @return the number of enabled bits currently tracked by this container
     */
    public int size() {
        return size;
    }

    /**
     * Converts this bit container into a little-endian byte array.
     *
     * <p>
     * The produced byte array contains the raw word data in little-endian order.
     * Full words are written directly, and the final word contributes only as many
     * bytes as are needed to represent its non-zero trailing content.
     * </p>
     *
     * <p>
     * This is useful for compact persistence, binary transport, custom save formats,
     * and restoring state later through {@link #valueOf(byte[])}.
     * </p>
     *
     * @return a little-endian byte array representing the current bit contents
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
     * Builds a human-readable binary representation of the internal word array.
     *
     * <p>
     * Each word is rendered as a 64-character binary segment padded with leading
     * zeroes so the storage layout is easy to inspect visually during debugging.
     * </p>
     *
     * <p>
     * The output format is:
     * </p>
     *
     * <pre>{@code
     * Bits[ 0000...0001 0000...0100 ]
     * }</pre>
     *
     * @return a string representation of all backing words in binary form
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