package valthorne.encryption;

/**
 * ISAAC (Indirection, Shift, Accumulate, Add, and Count) pseudo-random number generator.
 *
 * <h2>Example</h2>
 * <pre>{@code
 * // Unseeded (still generates output, but deterministic based on default state).
 * ISAAC rng = new ISAAC();
 * int a = rng.nextValue();
 * int b = rng.nextValue();
 *
 * // Seeded (recommended): the seed is copied into the internal results buffer before init.
 * int[] seed = { 1, 2, 3, 4, 5, 6, 7, 8 };
 * ISAAC seeded = new ISAAC(seed);
 * int r0 = seeded.nextValue();
 * int r1 = seeded.nextValue();
 *
 * // Re-key / re-seed: create a new instance with a different seed.
 * // (This implementation does not expose a "setSeed" method.)
 * }</pre>
 *
 * <h2>What this class does</h2>
 * <p>
 * This class implements Bob Jenkins' ISAAC algorithm. ISAAC is a fast PRNG that produces
 * 32-bit values from a 256-word internal state. It generates results in batches of 256
 * integers into {@link #results}. Calls to {@link #nextValue()} consume this batch until
 * depleted, then {@link #generateIsaacResults()} is invoked to produce the next batch.
 * </p>
 *
 * <h2>State layout</h2>
 * <ul>
 *     <li>{@link #memory} holds the 256-word internal state table.</li>
 *     <li>{@link #results} holds the 256-word output batch provided to the caller.</li>
 *     <li>{@link #accumulator}, {@link #lastResult}, and {@link #counter} are the running mixers.</li>
 *     <li>{@link #count} tracks the remaining unread values in {@link #results}.</li>
 * </ul>
 *
 * <h2>Seeding behavior</h2>
 * <p>
 * When constructed with a seed, the seed values are copied into {@link #results} and then
 * {@link #init(boolean)} is run with {@code true}, causing the seed to be mixed into the
 * internal state with an additional "second pass". If constructed with no seed, {@link #init(boolean)}
 * is run with {@code false}, producing a deterministic sequence from the default state.
 * </p>
 *
 * @author Bob Jenkins
 */
public class ISAAC {

    private static final int SIZEL = 8; // log2 of the internal state size (256 => 2^8).
    private static final int SIZE = 1 << SIZEL; // Number of 32-bit words in the internal state and output arrays.
    private static final int MASK = (SIZE - 1) << 2; // Bitmask used for indirection lookups (word index * 4).

    private final int[] results; // Output batch of 256 generated values returned to the caller.
    private final int[] memory; // Internal 256-word state table used by the ISAAC mixing function.

    private int count; // Remaining unread values in {@link #results} (counts down to 0).
    private int accumulator; // Accumulator mixer used in each generation step.
    private int lastResult; // Running result value added into the generation stream.
    private int counter; // Counter that guarantees a very long cycle by advancing each generation.

    /**
     * Constructs an ISAAC generator with the default (unseeded) state.
     *
     * <p>
     * This creates {@link #memory} and {@link #results}, then runs {@link #init(boolean)} with {@code false}.
     * The produced sequence is deterministic for this implementation because the initial arrays are zeroed.
     * </p>
     */
    public ISAAC() {
        memory = new int[SIZE];
        results = new int[SIZE];
        init(false);
    }

    /**
     * Constructs an ISAAC generator seeded with the provided int array.
     *
     * <p>
     * The seed is copied into {@link #results} starting at index 0, then {@link #init(boolean)} is run
     * with {@code true}. When the flag is true, seeding affects the state in a more thorough way by
     * performing a second mixing pass.
     * </p>
     *
     * <p>
     * This constructor assumes {@code seed.length <= 256}. If it is larger, the copy would overflow
     * the {@link #results} array.
     * </p>
     *
     * @param seed seed words to mix into the generator state
     */
    public ISAAC(int[] seed) {
        memory = new int[SIZE];
        results = new int[SIZE];
        System.arraycopy(seed, 0, results, 0, seed.length);
        init(true);
    }

    /**
     * Generates the next batch of 256 ISAAC results into {@link #results}.
     *
     * <p>
     * This is the core batch generator. It advances {@link #counter}, mixes {@link #accumulator},
     * and uses indirection into {@link #memory} via {@link #MASK} to produce the output stream.
     * </p>
     *
     * <p>
     * The algorithm runs in two half-size loops to match the reference layout, where {@code j}
     * starts at {@code SIZE/2} for the first half and then wraps for the second half.
     * </p>
     */
    public final void generateIsaacResults() {
        int i, j, x, y;

        lastResult += ++counter;
        for (i = 0, j = SIZE / 2; i < SIZE / 2; ) {
            x = memory[i];
            accumulator ^= accumulator << 13;
            accumulator += memory[j++];
            memory[i] = y = memory[(x & MASK) >> 2] + accumulator + lastResult;
            results[i++] = lastResult = memory[((y >> SIZEL) & MASK) >> 2] + x;

            x = memory[i];
            accumulator ^= accumulator >>> 6;
            accumulator += memory[j++];
            memory[i] = y = memory[(x & MASK) >> 2] + accumulator + lastResult;
            results[i++] = lastResult = memory[((y >> SIZEL) & MASK) >> 2] + x;

            x = memory[i];
            accumulator ^= accumulator << 2;
            accumulator += memory[j++];
            memory[i] = y = memory[(x & MASK) >> 2] + accumulator + lastResult;
            results[i++] = lastResult = memory[((y >> SIZEL) & MASK) >> 2] + x;

            x = memory[i];
            accumulator ^= accumulator >>> 16;
            accumulator += memory[j++];
            memory[i] = y = memory[(x & MASK) >> 2] + accumulator + lastResult;
            results[i++] = lastResult = memory[((y >> SIZEL) & MASK) >> 2] + x;
        }

        for (j = 0; j < SIZE / 2; ) {
            x = memory[i];
            accumulator ^= accumulator << 13;
            accumulator += memory[j++];
            memory[i] = y = memory[(x & MASK) >> 2] + accumulator + lastResult;
            results[i++] = lastResult = memory[((y >> SIZEL) & MASK) >> 2] + x;

            x = memory[i];
            accumulator ^= accumulator >>> 6;
            accumulator += memory[j++];
            memory[i] = y = memory[(x & MASK) >> 2] + accumulator + lastResult;
            results[i++] = lastResult = memory[((y >> SIZEL) & MASK) >> 2] + x;

            x = memory[i];
            accumulator ^= accumulator << 2;
            accumulator += memory[j++];
            memory[i] = y = memory[(x & MASK) >> 2] + accumulator + lastResult;
            results[i++] = lastResult = memory[((y >> SIZEL) & MASK) >> 2] + x;

            x = memory[i];
            accumulator ^= accumulator >>> 16;
            accumulator += memory[j++];
            memory[i] = y = memory[(x & MASK) >> 2] + accumulator + lastResult;
            results[i++] = lastResult = memory[((y >> SIZEL) & MASK) >> 2] + x;
        }
    }

    /**
     * Initializes or re-initializes the generator state.
     *
     * <p>
     * This performs the ISAAC initialization routine:
     * </p>
     * <ul>
     *     <li>sets eight mixing variables (a..h) to the golden ratio constant</li>
     *     <li>scrambles them for 4 rounds to diffuse the initial constant state</li>
     *     <li>fills {@link #memory} in 8-word chunks, optionally adding {@link #results} as seed input</li>
     *     <li>if {@code flag} is true, performs a second pass so the entire seed affects the entire state</li>
     *     <li>generates the first output batch via {@link #generateIsaacResults()}</li>
     *     <li>resets {@link #count} so {@link #nextValue()} can consume outputs</li>
     * </ul>
     *
     * @param flag if true, mixes the seed in two passes; if false, uses only the first pass
     */
    public final void init(boolean flag) {
        int i;
        int a, b, c, d, e, f, g, h;
        a = b = c = d = e = f = g = h = 0x9e3779b9;

        for (i = 0; i < 4; ++i) {
            a ^= b << 11;
            d += a;
            b += c;
            b ^= c >>> 2;
            e += b;
            c += d;
            c ^= d << 8;
            f += c;
            d += e;
            d ^= e >>> 16;
            g += d;
            e += f;
            e ^= f << 10;
            h += e;
            f += g;
            f ^= g >>> 4;
            a += f;
            g += h;
            g ^= h << 8;
            b += g;
            h += a;
            h ^= a >>> 9;
            c += h;
            a += b;
        }

        for (i = 0; i < SIZE; i += 8) {
            if (flag) {
                a += results[i];
                b += results[i + 1];
                c += results[i + 2];
                d += results[i + 3];
                e += results[i + 4];
                f += results[i + 5];
                g += results[i + 6];
                h += results[i + 7];
            }
            a ^= b << 11;
            d += a;
            b += c;
            b ^= c >>> 2;
            e += b;
            c += d;
            c ^= d << 8;
            f += c;
            d += e;
            d ^= e >>> 16;
            g += d;
            e += f;
            e ^= f << 10;
            h += e;
            f += g;
            f ^= g >>> 4;
            a += f;
            g += h;
            g ^= h << 8;
            b += g;
            h += a;
            h ^= a >>> 9;
            c += h;
            a += b;
            memory[i] = a;
            memory[i + 1] = b;
            memory[i + 2] = c;
            memory[i + 3] = d;
            memory[i + 4] = e;
            memory[i + 5] = f;
            memory[i + 6] = g;
            memory[i + 7] = h;
        }

        if (flag) {
            for (i = 0; i < SIZE; i += 8) {
                a += memory[i];
                b += memory[i + 1];
                c += memory[i + 2];
                d += memory[i + 3];
                e += memory[i + 4];
                f += memory[i + 5];
                g += memory[i + 6];
                h += memory[i + 7];
                a ^= b << 11;
                d += a;
                b += c;
                b ^= c >>> 2;
                e += b;
                c += d;
                c ^= d << 8;
                f += c;
                d += e;
                d ^= e >>> 16;
                g += d;
                e += f;
                e ^= f << 10;
                h += e;
                f += g;
                f ^= g >>> 4;
                a += f;
                g += h;
                g ^= h << 8;
                b += g;
                h += a;
                h ^= a >>> 9;
                c += h;
                a += b;
                memory[i] = a;
                memory[i + 1] = b;
                memory[i + 2] = c;
                memory[i + 3] = d;
                memory[i + 4] = e;
                memory[i + 5] = f;
                memory[i + 6] = g;
                memory[i + 7] = h;
            }
        }

        generateIsaacResults();
        count = SIZE;
    }

    /**
     * Returns the next 32-bit pseudo-random value from the generator.
     *
     * <p>
     * Values are returned from the pre-generated {@link #results} batch. When the batch is exhausted,
     * this method regenerates a new batch by calling {@link #generateIsaacResults()}.
     * </p>
     *
     * <p>
     * This implementation consumes results from the end of the array backward via {@link #count}.
     * </p>
     *
     * @return next pseudo-random 32-bit value
     */
    public final int nextValue() {
        if (count-- == 0) {
            generateIsaacResults();
            count = SIZE - 1;
        }
        return results[count];
    }
}