package valthorne.browser.font;

import org.teavm.jso.JSBody;
import org.teavm.jso.JSByRef;

/**
 * Resolves logical bidi runs with the pinned browser UAX9 implementation.
 * UTF-16 offsets and visual reordering follow the subset of java.text.Bidi used
 * by adaptive text. One layout owns its levels and boundaries; no source text
 * or mutable bidi state is retained by the shared JavaScript resolver.
 */
public final class BrowserBidi {
    /**
     * Selects automatic paragraph direction with left-to-right fallback.
     */
    public static final int DIRECTION_DEFAULT_LEFT_TO_RIGHT = -2;
    private final byte[] levels; // One resolved embedding level per UTF-16 unit.
    private final int[] starts; // Logical run starts followed by the final boundary.
    private final int count; // Active logical run count.

    /**
     * Resolves one nonempty paragraph into logical directional runs.
     * @param source complete paragraph
     * @param direction default paragraph direction
     */
    public BrowserBidi(String source, int direction) {
        levels = new byte[source.length()];
        starts = new int[source.length() + 1];
        resolve(source, direction, levels);
        int runs = 0;
        for (int i = 0; i < levels.length; i++)
            if (i == 0 || levels[i] != levels[i - 1]) starts[runs++] = i;
        starts[runs] = source.length();
        count = runs;
    }
    /**
     * Returns the number of logical directional runs.
     * @return run count
     */
    public int getRunCount() { return count; }
    /**
     * Returns a run's first UTF-16 unit.
     * @param run logical run index
     * @return inclusive start offset
     */
    public int getRunStart(int run) { return starts[run]; }
    /**
     * Returns a run's exclusive UTF-16 boundary.
     * @param run logical run index
     * @return exclusive end offset
     */
    public int getRunLimit(int run) { return starts[run + 1]; }
    /**
     * Returns a logical run's resolved embedding level.
     * @param run logical run index
     * @return nonnegative embedding level
     */
    public int getRunLevel(int run) { return levels[starts[run]] & 255; }
    /**
     * Reorders caller-owned run values by their resolved embedding levels.
     * @param levels per-run embedding levels
     * @param levelStart start in the level array
     * @param values parallel run values
     * @param valueStart start in the value array
     * @param count number of runs
     */
    public static void reorderVisually(byte[] levels, int levelStart, Object[] values, int valueStart, int count) {
        /*
         * UAX9 L2 reverses maximal ranges from the highest level down through
         * the lowest odd level. Swaps retain existing boxed run indices.
         */
        int maximum = 0;
        int minimumOdd = 256;
        for (int i = 0; i < count; i++) {
            int level = levels[levelStart + i] & 255;
            maximum = Math.max(maximum, level);
            if ((level & 1) != 0) minimumOdd = Math.min(minimumOdd, level);
        }
        for (int level = maximum; level >= minimumOdd; level--) {
            for (int first = 0; first < count; ) {
                while (first < count && (levels[levelStart + first] & 255) < level) first++;
                int end = first;
                while (end < count && (levels[levelStart + end] & 255) >= level) end++;
                for (int a = first, b = end - 1; a < b; a++, b--) {
                    Object value = values[valueStart + a];
                    values[valueStart + a] = values[valueStart + b];
                    values[valueStart + b] = value;
                }
                first = end;
            }
        }
    }
    /**
     * Copies the resolver's levels into the current layout's owned storage.
     * @param source complete UTF-16 paragraph
     * @param direction automatic or explicit paragraph direction
     * @param output caller-owned embedding levels
     */
    @JSBody(params = {"source", "direction", "output"}, script = "const resolvedLevels=globalThis.valthorneHost.bidi.getEmbeddingLevels(source,direction===0?'ltr':direction===1?'rtl':undefined).levels;output.set(resolvedLevels);")
    private static native void resolve(String source, int direction, @JSByRef byte[] output);
}
