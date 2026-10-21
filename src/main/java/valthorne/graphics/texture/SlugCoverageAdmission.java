package valthorne.graphics.texture;

import valthorne.graphics.font.slug.SlugGlyph;

import java.util.Arrays;

/**
 * Bounded history identifying coverage samples reused across drawing scopes.
 * One-off moving positions consume only CPU admission slots; clearing this
 * history never evicts reusable atlas coverage. Exact keys avoid admission
 * starvation from collisions, and repeated hits in one scope count only once.
 * All state is confined to the owning texture batch's graphics thread.
 */
final class SlugCoverageAdmission {
    /**
     * Power-of-two table kept at most half full for bounded probing cost.
     */
    private static final int SLOTS = 16384;
    /**
     * Number of distinct drawing scopes required before retaining a sample.
     */
    private static final int REUSE_SCOPES = 3;
    private final SlugGlyph[] glyphs = new SlugGlyph[SLOTS]; // Exact candidate glyph identities.
    private final float[] sizes = new float[SLOTS]; // Candidate physical em sizes.
    private final float[] phasesX = new float[SLOTS]; // Exact horizontal baseline fractions.
    private final float[] phasesY = new float[SLOTS]; // Exact vertical baseline fractions.
    private final int[] seen = new int[SLOTS]; // Most recent scope observing each candidate.
    private final byte[] repeats = new byte[SLOTS]; // Distinct-scope observation counts, saturated at the admission threshold.
    private int count; // Occupied history slots, independent of GPU atlas occupancy.

    /**
     * Records an exact sample key without allocating or changing GPU addresses.
     *
     * @param glyph immutable outline identity
     * @param size physical pixels per em
     * @param phaseX exact horizontal baseline fraction
     * @param phaseY exact vertical baseline fraction
     * @param scope current drawing scope generation
     * @return true after reuse has been observed in three distinct scopes
     */
    boolean observe(SlugGlyph glyph, float size, float phaseX, float phaseY, int scope) {
        if (count >= SLOTS / 2) reset();
        int slot = SlugCoverageCache.hash(glyph, size, phaseX, phaseY) & (SLOTS - 1);
        while (glyphs[slot] != null) {
            if (glyphs[slot] == glyph && sizes[slot] == size && phasesX[slot] == phaseX && phasesY[slot] == phaseY) {
                if (seen[slot] != scope) {
                    seen[slot] = scope;
                    repeats[slot] = (byte) Math.min(REUSE_SCOPES, repeats[slot] + 1);
                }
                return repeats[slot] >= REUSE_SCOPES;
            }
            slot = (slot + 1) & (SLOTS - 1);
        }
        glyphs[slot] = glyph;
        sizes[slot] = size;
        phasesX[slot] = phaseX;
        phasesY[slot] = phaseY;
        seen[slot] = scope;
        repeats[slot] = 1;
        count++;
        return false;
    }

    /**
     * Releases candidate references without disturbing cached GPU coverage.
     */
    void reset() {
        Arrays.fill(glyphs, null);
        count = 0;
    }
}
