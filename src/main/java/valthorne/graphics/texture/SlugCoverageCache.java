package valthorne.graphics.texture;

import valthorne.graphics.font.slug.SlugGlyph;

import java.util.Arrays;

import static org.lwjgl.opengl.GL33.*;

/**
 * Bounded graphics-thread cache of unmodified Slug coverage samples. Entries
 * retain glyph identity, physical em size and exact fractional baseline phase;
 * colors, opacity and clipping remain draw-time operations. No bitmap font
 * rasterizer or rescaling is involved. General transforms bypass this cache.
 *
 * <p>The owning texture batch releases the single R32F atlas and framebuffer.
 * Shelf exhaustion clears the index only after the owner flushes queued draws.
 * Glyph references are bounded and cleared on reset. Lookup allocates nothing;
 * GPU storage is created lazily and consumes sixteen MiB.</p>
 */
final class SlugCoverageCache {
    /**
     * Atlas extent in physical coverage samples.
     */
    static final int EXTENT = 2048;
    /**
     * Maximum cached glyph extent; larger glyphs retain analytic rendering.
     */
    static final int MAX_GLYPH_EXTENT = 256;
    /**
     * Power-of-two identity table with at most half its slots occupied.
     */
    private static final int SLOTS = 16384;
    private long generation; // Invalidates retained run geometry whenever sample addresses are reused.
    private final SlugGlyph[] glyphs = new SlugGlyph[SLOTS]; // Bounded identity keys.
    private final float[] sizes = new float[SLOTS]; // Physical em size for each key.
    private final float[] phasesX = new float[SLOTS]; // Exact fractional physical baseline X.
    private final float[] phasesY = new float[SLOTS]; // Exact fractional physical baseline Y.
    private final int[] xs = new int[SLOTS]; // Atlas column per entry.
    private final int[] ys = new int[SLOTS]; // Atlas row per entry.
    private final int[] lefts = new int[SLOTS]; // First sample column relative to the integer baseline.
    private final int[] bottoms = new int[SLOTS]; // First sample row relative to the integer baseline.
    private final int[] widths = new int[SLOTS]; // Initialized tile widths.
    private final int[] heights = new int[SLOTS]; // Initialized tile heights.
    private int count; // Number of retained glyph keys.
    private int nextX; // Shelf insertion column.
    private int nextY; // Shelf insertion row.
    private int rowHeight; // Height of the current shelf.
    private int texture; // Owned R32F sample texture, created on first insertion.
    private int framebuffer; // Owned framebuffer targeting the sample texture.

    /**
     * Finds a matching key or its vacant insertion slot without allocation.
     *
     * @param glyph outline identity, including its hinted size
     * @param size physical pixels per em
     * @param phaseX exact horizontal baseline fraction
     * @param phaseY exact vertical baseline fraction
     * @return occupied or vacant table slot
     */
    int find(SlugGlyph glyph, float size, float phaseX, float phaseY) {
        int hash = hash(glyph, size, phaseX, phaseY);
        int slot = hash & (SLOTS - 1);
        while (glyphs[slot] != null) {
            if (glyphs[slot] == glyph && sizes[slot] == size && phasesX[slot] == phaseX && phasesY[slot] == phaseY) return slot;
            slot = (slot + 1) & (SLOTS - 1);
        }
        return slot;
    }

    /**
     * Mixes exact coverage-key bits for bounded sample lookup.
     *
     * @param glyph outline identity
     * @param size physical em size
     * @param phaseX horizontal baseline fraction
     * @param phaseY vertical baseline fraction
     * @return avalanched table hash
     */
    static int hash(SlugGlyph glyph, float size, float phaseX, float phaseY) {
        /*
         * Mix aligned float bits before probing power-of-two tables, preventing
         * long clusters from the common binary font-unit and pixel fractions.
         */
        int hash = System.identityHashCode(glyph);
        hash = 31 * hash + Float.floatToIntBits(size);
        hash = 31 * hash + Float.floatToIntBits(phaseX);
        hash = 31 * hash + Float.floatToIntBits(phaseY);
        hash ^= hash >>> 16;
        hash *= 0x85EBCA6B;
        hash ^= hash >>> 13;
        hash *= 0xC2B2AE35;
        hash ^= hash >>> 16;
        return hash;
    }

    /**
     * Reports whether lookup returned an existing sample region.
     *
     * @param slot result of {@link #find}
     * @return true for a populated entry
     */
    boolean contains(int slot) { return glyphs[slot] != null; }

    /**
     * Checks whether an insertion requires a flush and atlas reset first.
     *
     * @param width requested physical width
     * @param height requested physical height
     * @return true when no shelf or key capacity remains
     */
    boolean needsReset(int width, int height) {
        int y = nextX + width > EXTENT ? nextY + rowHeight : nextY;
        return count >= SLOTS / 2 || y + height > EXTENT;
    }

    /**
     * Discards keys and reuses existing GPU storage after queued draws finish.
     */
    void reset() {
        Arrays.fill(glyphs, null);
        count = nextX = nextY = rowHeight = 0;
        generation++;
    }

    /**
     * Returns the current sample-address generation without querying the GPU.
     *
     * @return generation incremented whenever sample addresses are reused
     */
    long generation() { return generation; }

    /**
     * Reserves a shelf region. The caller must fill every texel before drawing
     * from it and must restore texture/framebuffer bindings after initialization.
     *
     * @param slot vacant index slot
     * @param glyph outline identity
     * @param size physical pixels per em
     * @param phaseX horizontal baseline fraction
     * @param phaseY vertical baseline fraction
     * @param left first sample column relative to integer baseline
     * @param bottom first sample row relative to integer baseline
     * @param width sample width
     * @param height sample height
     */
    void insert(int slot, SlugGlyph glyph, float size, float phaseX, float phaseY, int left, int bottom, int width, int height) {
        if (texture == 0) {
            texture = glGenTextures();
            glBindTexture(GL_TEXTURE_2D, texture);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_R32F, EXTENT, EXTENT, 0, GL_RED, GL_FLOAT, 0L);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
            framebuffer = glGenFramebuffers();
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, framebuffer);
            glFramebufferTexture2D(GL_DRAW_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, texture, 0);
            if (glCheckFramebufferStatus(GL_DRAW_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE)
                throw new IllegalStateException("Slug coverage framebuffer is incomplete.");
        }
        if (nextX + width > EXTENT) {
            nextX = 0;
            nextY += rowHeight;
            rowHeight = 0;
        }
        if (glyphs[slot] == null) count++;
        glyphs[slot] = glyph;
        sizes[slot] = size;
        phasesX[slot] = phaseX;
        phasesY[slot] = phaseY;
        lefts[slot] = left;
        bottoms[slot] = bottom;
        widths[slot] = width;
        heights[slot] = height;
        xs[slot] = nextX;
        ys[slot] = nextY;
        nextX += width;
        rowHeight = Math.max(rowHeight, height);
    }

    /**
     * Returns a cached tile's first sample relative to the integer baseline.
     *
     * @param slot populated key
     * @return relative sample column
     */
    int left(int slot) { return lefts[slot]; }

    /**
     * Returns a cached tile's first row relative to the integer baseline.
     *
     * @param slot populated key
     * @return relative sample row
     */
    int bottom(int slot) { return bottoms[slot]; }

    /**
     * Returns the initialized sample rectangle width.
     *
     * @param slot populated key
     * @return physical sample columns
     */
    int width(int slot) { return widths[slot]; }

    /**
     * Returns the initialized sample rectangle height.
     *
     * @param slot populated key
     * @return physical sample rows
     */
    int height(int slot) { return heights[slot]; }

    /**
     * Returns the fixed physical row count used by batched atlas captures.
     *
     * @return atlas rows, bounded to sixteen MiB of R32F storage
     */
    int height() { return EXTENT; }

    /**
     * Returns an entry's atlas column.
     *
     * @param slot occupied index slot
     * @return sample column
     */
    int x(int slot) { return xs[slot]; }

    /**
     * Returns an entry's atlas row.
     *
     * @param slot occupied index slot
     * @return sample row
     */
    int y(int slot) { return ys[slot]; }

    /**
     * Returns the initialized atlas texture name.
     *
     * @return owned OpenGL texture
     */
    int texture() { return texture; }

    /**
     * Returns the initialized atlas framebuffer name.
     *
     * @return owned OpenGL framebuffer
     */
    int framebuffer() { return framebuffer; }

    /**
     * Releases GPU objects and retained keys on the owning graphics thread.
     */
    void dispose() {
        if (framebuffer != 0) glDeleteFramebuffers(framebuffer);
        if (texture != 0) glDeleteTextures(texture);
        framebuffer = texture = 0;
        reset();
    }
}
