package valthorne.encryption;

/**
 * <html>
 * ------------------------------------------------------------------------------<br>
 * Rand.java: By Bob Jenkins. My random number generator, ISAAC.<br>
 * <blockquote>rand.init() -- initialize<br>
 * </blockquote> <blockquote>rand.val() -- get a random value<br>
 * </blockquote> MODIFIED:<br>
 * <blockquote>960327: Creation (addition of randinit, really)<br>
 * </blockquote> <blockquote>970719: use context, not global variables, for
 * internal state<br>
 * </blockquote> <blockquote>980224: Translate to Java<br>
 * </blockquote>
 * ------------------------------------------------------------------------------
 * </html>
 *
 * @author Bob Jenkins
 */
public class ISAAC {

	/**
	 * The size of the seed array
	 */
	public static final int SEED_LENGTH = 256;

	private final static int SIZEL = 8; /* log of size of rsl[] and mem[] */
	private final static int SIZE = 1 << SIZEL; /* size of rsl[] and mem[] */
	private final static int MASK = (SIZE - 1) << 2; /* for pseudorandom lookup */

	private int count; /* count through the results in results[] */
	private final int[] results; /* the results given to the user */
	private final int[] memory; /* the internal memory */
	private int accumulator; /* accumulator */
	private int lastResult; /* the last result */
	private int counter; /* counter, guarantees cycle is at least 2^^40 */

	/**
	 * Constructs a new {@code IsaacCipher} object with no seed defined.
	 */
	public ISAAC() {
		memory = new int[SIZE];
		results = new int[SIZE];
		init(false);
	}

	/**
	 * Constructs a new {@code IsaacCipher} object with the given {@code seed}
	 * defined and initializes the cipher based on the seed. The seed will be used
	 * for the results generated using {@link #generateIsaacResults()}.
	 * 
	 * @param seed the cipher seed to use for generating results
	 */
	public ISAAC(int[] seed) {
		memory = new int[SIZE];
		results = new int[SIZE];
        System.arraycopy(seed, 0, results, 0, seed.length);
		init(true);
	}

	/**
	 * Generate 256 results. This is a fast (not small) implementation.
	 */
	public final void generateIsaacResults() {
		int i, j, x, y;

		lastResult += ++counter;
		for (i = 0, j = SIZE / 2; i < SIZE / 2;) {
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

		for (j = 0; j < SIZE / 2;) {
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
	 * Initializes this {@code ISAACCipher}, and if the given {@code flag} argument
	 * is true, then a second pass is made.
	 * 
	 * @param flag the flag determining if a second pass should be made
	 */
	public final void init(boolean flag) {
		int i;
		int a, b, c, d, e, f, g, h;
		a = b = c = d = e = f = g = h = 0x9e3779b9; /* the golden ratio */

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

		for (i = 0; i < SIZE; i += 8) { /* fill in mem[] with messy stuff */
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

		if (flag) { /* second pass makes all of seed affect all of mem */
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
	 * This method returns the next value within the Isaac results generated
	 * beforehand. If the increment of this method is called beyond the size of this
	 * Isaac Cipher, then new results are generated again.
	 */
	public final int nextValue() {
		if (count-- == 0) {
			generateIsaacResults();
			count = SIZE - 1;
		}
		return results[count];
	}

}