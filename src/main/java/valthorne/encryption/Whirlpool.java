package valthorne.encryption;

import java.util.Arrays;

/**
 * Whirlpool hash function implementation (NESSIE reference style API).
 *
 * <h2>Example</h2>
 * <pre>{@code
 * // One-shot hashing (byte[] slice).
 * byte[] digest = Whirlpool.whirlpool(data, 0, data.length);
 *
 * // Streaming-style hashing (NESSIE API).
 * Whirlpool w = new Whirlpool();
 * w.NESSIEinit();
 * w.NESSIEadd("hello world");          // ASCII helper
 * w.NESSIEadd(moreBytes, moreBits);    // bit-precise update
 * byte[] out = new byte[64];           // Whirlpool digest is 512 bits
 * w.NESSIEfinalize(out);
 * }</pre>
 *
 * <h2>How this implementation works</h2>
 * <p>
 * Whirlpool processes input in 512-bit (64-byte) blocks and produces a 512-bit (64-byte) digest.
 * This implementation follows the common NESSIE reference API pattern:
 * </p>
 * <ul>
 *     <li>{@link #NESSIEinit()} resets all internal state</li>
 *     <li>{@link #NESSIEadd(byte[], long)} feeds bits into the internal buffer</li>
 *     <li>{@link #NESSIEfinalize(byte[])} pads, appends bit-length, and emits the digest</li>
 * </ul>
 *
 * <h2>Bit-precise updates</h2>
 * <p>
 * {@link #NESSIEadd(byte[], long)} accepts a bit count (not a byte count). This allows hashing inputs
 * that are not byte-aligned. Internally, the class maintains:
 * </p>
 * <ul>
 *     <li>a 64-byte buffer</li>
 *     <li>a counter of how many bits are currently in the buffer</li>
 *     <li>a 256-bit total length counter stored as a 32-byte big-endian array</li>
 * </ul>
 *
 * <h2>Transform</h2>
 * <p>
 * The core compression step is performed by {@link #processBuffer()}:
 * </p>
 * <ul>
 *     <li>maps the 64-byte buffer into eight 64-bit words</li>
 *     <li>runs a 10-round internal block cipher-like permutation</li>
 *     <li>applies the Miyaguchi–Preneel compression function to update {@link #hash}</li>
 * </ul>
 *
 * <p>
 * <b>References</b>
 * </p>
 * <p>
 * The Whirlpool algorithm was developed by Paulo S. L. M. Barreto and Vincent Rijmen.
 * This code originates from the reference style implementation (v3.0 2003.03.12) and retains
 * its structure and naming conventions (e.g., "NESSIE" method names).
 * </p>
 *
 * @author Paulo S.L.M. Barreto
 * @author Vincent Rijmen.
 */
public class Whirlpool {

    protected static final int R = 10; // Round count for the internal dedicated block cipher.

    private static final String sbox =
            "\u1823\uc6E8\u87B8\u014F\u36A6\ud2F5\u796F\u9152"
                    + "\u60Bc\u9B8E\uA30c\u7B35\u1dE0\ud7c2\u2E4B\uFE57"
                    + "\u1577\u37E5\u9FF0\u4AdA\u58c9\u290A\uB1A0\u6B85"
                    + "\uBd5d\u10F4\ucB3E\u0567\uE427\u418B\uA77d\u95d8"
                    + "\uFBEE\u7c66\udd17\u479E\ucA2d\uBF07\uAd5A\u8333"
                    + "\u6302\uAA71\uc819\u49d9\uF2E3\u5B88\u9A26\u32B0"
                    + "\uE90F\ud580\uBEcd\u3448\uFF7A\u905F\u2068\u1AAE"
                    + "\uB454\u9322\u64F1\u7312\u4008\uc3Ec\udBA1\u8d3d"
                    + "\u9700\ucF2B\u7682\ud61B\uB5AF\u6A50\u45F3\u30EF"
                    + "\u3F55\uA2EA\u65BA\u2Fc0\udE1c\uFd4d\u9275\u068A"
                    + "\uB2E6\u0E1F\u62d4\uA896\uF9c5\u2559\u8472\u394c"
                    + "\u5E78\u388c\ud1A5\uE261\uB321\u9c1E\u43c7\uFc04"
                    + "\u5199\u6d0d\uFAdF\u7E24\u3BAB\ucE11\u8F4E\uB7EB"
                    + "\u3c81\u94F7\uB913\u2cd3\uE76E\uc403\u5644\u7FA9"
                    + "\u2ABB\uc153\udc0B\u9d6c\u3174\uF646\uAc89\u14E1"
                    + "\u163A\u6909\u70B6\ud0Ed\ucc42\u98A4\u285c\uF886"; // Packed S-box values used to build the C tables.

    private static final long[][] C = new long[8][256]; // Circulant tables used by the round transformation.
    private static final long[] rc = new long[R + 1]; // Round constants (index 1..R).

    static {
        for (int x = 0; x < 256; x++) {
            char c = sbox.charAt(x / 2);
            long v1 = ((x & 1) == 0) ? c >>> 8 : c & 0xff;
            long v2 = v1 << 1;
            if (v2 >= 0x100L) {
                v2 ^= 0x11dL;
            }
            long v4 = v2 << 1;
            if (v4 >= 0x100L) {
                v4 ^= 0x11dL;
            }
            long v5 = v4 ^ v1;
            long v8 = v4 << 1;
            if (v8 >= 0x100L) {
                v8 ^= 0x11dL;
            }
            long v9 = v8 ^ v1;

            C[0][x] = (v1 << 56) | (v1 << 48) | (v4 << 40) | (v1 << 32) | (v8 << 24) | (v5 << 16) | (v2 << 8) | (v9);

            for (int t = 1; t < 8; t++) {
                C[t][x] = (C[t - 1][x] >>> 8) | ((C[t - 1][x] << 56));
            }
        }

        rc[0] = 0L;
        for (int r = 1; r <= R; r++) {
            int i = 8 * (r - 1);
            rc[r] = (C[0][i] & 0xff00000000000000L)
                    ^ (C[1][i + 1] & 0x00ff000000000000L)
                    ^ (C[2][i + 2] & 0x0000ff0000000000L)
                    ^ (C[3][i + 3] & 0x000000ff00000000L)
                    ^ (C[4][i + 4] & 0x00000000ff000000L)
                    ^ (C[5][i + 5] & 0x0000000000ff0000L)
                    ^ (C[6][i + 6] & 0x000000000000ff00L)
                    ^ (C[7][i + 7] & 0x00000000000000ffL);
        }
    }

    protected byte[] bitLength = new byte[32]; // Total number of hashed bits as a 256-bit big-endian counter.
    protected byte[] buffer = new byte[64]; // Current 512-bit message block buffer (byte-oriented).
    protected int bufferBits = 0; // Number of valid bits currently in {@link #buffer}.
    protected int bufferPos = 0; // Current byte index in {@link #buffer} where new bits will be written.
    protected long[] hash = new long[8]; // Current 512-bit chaining value (eight 64-bit words).
    protected long[] K = new long[8]; // Round key schedule state for the internal cipher.
    protected long[] L = new long[8]; // Temporary word array used during key/state mixing.
    protected long[] block = new long[8]; // Current 512-bit block mapped from {@link #buffer}.
    protected long[] state = new long[8]; // Internal cipher state for the current block transform.

    /**
     * Creates a new Whirlpool instance with uninitialized state.
     *
     * <p>
     * Call {@link #NESSIEinit()} before feeding data, or use {@link #whirlpool(byte[], int, int)}
     * for one-shot hashing.
     * </p>
     */
    public Whirlpool() {
    }

    /**
     * Convenience one-shot helper that hashes a byte array slice and returns the 64-byte digest.
     *
     * <p>
     * If {@code off > 0}, this copies the slice into a new array before hashing. The digest is always
     * computed over {@code len} bytes (i.e., {@code len * 8} bits).
     * </p>
     *
     * @param data source byte array
     * @param off starting offset into {@code data}
     * @param len number of bytes to hash
     * @return 64-byte Whirlpool digest
     */
    public static byte[] whirlpool(byte[] data, int off, int len) {
        byte[] source;
        if (off <= 0) {
            source = data;
        } else {
            source = new byte[len];
            System.arraycopy(data, off, source, 0, len);
        }
        Whirlpool whirlpool = new Whirlpool();
        whirlpool.NESSIEinit();
        whirlpool.NESSIEadd(source, len * 8L);
        byte[] digest = new byte[64];
        whirlpool.NESSIEfinalize(digest);
        return digest;
    }

    /**
     * Processes the current 512-bit {@link #buffer} contents and updates {@link #hash}.
     *
     * <p>
     * This is the compression step. It maps the byte buffer into eight 64-bit words ({@link #block}),
     * initializes the internal cipher state ({@link #state}) with {@code block ^ hash}, then executes
     * {@link #R} rounds where both the key schedule and state are transformed using the precomputed
     * circulant tables {@link #C} and round constants {@link #rc}.
     * </p>
     *
     * <p>
     * After the rounds, it applies the Miyaguchi–Preneel construction:
     * {@code hash ^= state ^ block}.
     * </p>
     */
    protected void processBuffer() {
        for (int i = 0, j = 0; i < 8; i++, j += 8) {
            block[i] = (((long) buffer[j]) << 56)
                    ^ (((long) buffer[j + 1] & 0xffL) << 48)
                    ^ (((long) buffer[j + 2] & 0xffL) << 40)
                    ^ (((long) buffer[j + 3] & 0xffL) << 32)
                    ^ (((long) buffer[j + 4] & 0xffL) << 24)
                    ^ (((long) buffer[j + 5] & 0xffL) << 16)
                    ^ (((long) buffer[j + 6] & 0xffL) << 8)
                    ^ (((long) buffer[j + 7] & 0xffL));
        }

        for (int i = 0; i < 8; i++) {
            state[i] = block[i] ^ (K[i] = hash[i]);
        }

        for (int r = 1; r <= R; r++) {
            for (int i = 0; i < 8; i++) {
                L[i] = 0L;
                for (int t = 0, s = 56; t < 8; t++, s -= 8) {
                    L[i] ^= C[t][(int) (K[(i - t) & 7] >>> s) & 0xff];
                }
            }
            System.arraycopy(L, 0, K, 0, 8);
            K[0] ^= rc[r];

            for (int i = 0; i < 8; i++) {
                L[i] = K[i];
                for (int t = 0, s = 56; t < 8; t++, s -= 8) {
                    L[i] ^= C[t][(int) (state[(i - t) & 7] >>> s) & 0xff];
                }
            }
            System.arraycopy(L, 0, state, 0, 8);
        }

        for (int i = 0; i < 8; i++) {
            hash[i] ^= state[i] ^ block[i];
        }
    }

    /**
     * Resets the instance to the initial hashing state.
     *
     * <p>
     * This clears the total bit-length counter, resets the internal buffer and counters, and sets the
     * chaining value {@link #hash} to its initial (all-zero) value.
     * </p>
     *
     * <p>
     * Call this before starting a new hash computation with this instance.
     * </p>
     */
    public void NESSIEinit() {
        Arrays.fill(bitLength, (byte) 0);
        bufferBits = bufferPos = 0;
        buffer[0] = 0;
        Arrays.fill(hash, 0L);
    }

    /**
     * Feeds input bits into the hash function.
     *
     * <p>
     * This method accepts an explicit bit count, allowing non-byte-aligned hashing. It updates the
     * 256-bit {@link #bitLength} counter, then shifts bits from {@code source} into {@link #buffer}.
     * When the buffer reaches 512 bits, {@link #processBuffer()} is called and the buffer is reset.
     * </p>
     *
     * <p>
     * The buffer is treated as a bit stream; {@link #bufferBits} and {@link #bufferPos} track the current
     * write location. This method maintains the invariant {@code bufferBits < 512} when it returns.
     * </p>
     *
     * @param source input bytes containing the bits to hash
     * @param sourceBits number of bits from {@code source} to consume
     */
    public void NESSIEadd(byte[] source, long sourceBits) {
        int sourcePos = 0; // Index into source for the current byte being consumed.
        int sourceGap = (8 - ((int) sourceBits & 7)) & 7; // Number of "unused" low bits in the last source byte.
        int bufferRem = bufferBits & 7; // Number of already-occupied bits in the current buffer byte.
        int b;

        long value = sourceBits;
        for (int i = 31, carry = 0; i >= 0; i--) {
            carry += (bitLength[i] & 0xff) + ((int) value & 0xff);
            bitLength[i] = (byte) carry;
            carry >>>= 8;
            value >>>= 8;
        }

        while (sourceBits > 8) {
            b = ((source[sourcePos] << sourceGap) & 0xff) | ((source[sourcePos + 1] & 0xff) >>> (8 - sourceGap));
            if (b < 0 || b >= 256)
                throw new RuntimeException("LOGIC ERROR");

            buffer[bufferPos++] |= b >>> bufferRem;
            bufferBits += 8 - bufferRem;
            if (bufferBits == 512) {
                processBuffer();
                bufferBits = bufferPos = 0;
            }
            buffer[bufferPos] = (byte) ((b << (8 - bufferRem)) & 0xff);
            bufferBits += bufferRem;

            sourceBits -= 8;
            sourcePos++;
        }

        if (sourceBits > 0) {
            b = (source[sourcePos] << sourceGap) & 0xff;
            buffer[bufferPos] |= b >>> bufferRem;
        } else {
            b = 0;
        }

        if (bufferRem + sourceBits < 8) {
            bufferBits += sourceBits;
        } else {
            bufferPos++;
            bufferBits += 8 - bufferRem;
            sourceBits -= 8 - bufferRem;

            if (bufferBits == 512) {
                processBuffer();
                bufferBits = bufferPos = 0;
            }
            buffer[bufferPos] = (byte) ((b << (8 - bufferRem)) & 0xff);
            bufferBits += (int) sourceBits;
        }
    }

    /**
     * Finalizes hashing, writes the digest, and leaves the instance in a "consumed" state.
     *
     * <p>
     * This performs Whirlpool padding:
     * </p>
     * <ul>
     *     <li>append a single {@code 1} bit</li>
     *     <li>append {@code 0} bits until the buffer has room for the 256-bit length field</li>
     *     <li>append the 256-bit total bit-length stored in {@link #bitLength}</li>
     * </ul>
     *
     * <p>
     * After padding, it processes the final block and writes the 512-bit digest (64 bytes) to {@code digest}
     * in big-endian word order.
     * </p>
     *
     * @param digest output buffer that receives exactly 64 bytes
     */
    public void NESSIEfinalize(byte[] digest) {
        buffer[bufferPos] |= 0x80 >>> (bufferBits & 7);
        bufferPos++;

        if (bufferPos > 32) {
            while (bufferPos < 64) {
                buffer[bufferPos++] = 0;
            }
            processBuffer();
            bufferPos = 0;
        }
        while (bufferPos < 32) {
            buffer[bufferPos++] = 0;
        }

        System.arraycopy(bitLength, 0, buffer, 32, 32);
        processBuffer();

        for (int i = 0, j = 0; i < 8; i++, j += 8) {
            long h = hash[i];
            digest[j] = (byte) (h >>> 56);
            digest[j + 1] = (byte) (h >>> 48);
            digest[j + 2] = (byte) (h >>> 40);
            digest[j + 3] = (byte) (h >>> 32);
            digest[j + 4] = (byte) (h >>> 24);
            digest[j + 5] = (byte) (h >>> 16);
            digest[j + 6] = (byte) (h >>> 8);
            digest[j + 7] = (byte) (h);
        }
    }

    /**
     * Convenience helper that hashes an ASCII string using {@link #NESSIEadd(byte[], long)}.
     *
     * <p>
     * This method converts each {@code char} to a single byte via simple casting, which matches the
     * original reference behavior. It is intended for ASCII text only. For UTF-8 or other encodings,
     * convert the string yourself and call {@link #NESSIEadd(byte[], long)}.
     * </p>
     *
     * @param source ASCII plaintext string to hash
     */
    public void NESSIEadd(String source) {
        if (!source.isEmpty()) {
            byte[] data = new byte[source.length()];
            for (int i = 0; i < source.length(); i++) {
                data[i] = (byte) source.charAt(i);
            }
            NESSIEadd(data, 8L * data.length);
        }
    }
}