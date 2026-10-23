package valthorne.graphics.font.slug;

import org.lwjgl.stb.STBTTFontinfo;

import java.nio.ByteBuffer;

import static org.lwjgl.stb.STBTruetype.stbtt_GetCodepointKernAdvance;

/**
 * Glyph metrics and lazily cached kerning shared by font resources.
 * Unlike SlugOutlines, this object retains no upload buffers, allowing temporary
 * curve and band staging to be reclaimed after GPU upload. Encoded font storage
 * stays alive for STB kerning queries. Values use normalized em units; glyph
 * lookup and kerning queries allocate nothing. The lazy cache is unsynchronized,
 * so measurement and rendering must remain confined to the owning thread.
 */
public final class SlugMetrics {
    private final STBTTFontinfo info; // STB font info kept alive for fallback queries.
    private final ByteBuffer fontBuffer; // Backing font bytes kept alive for STB font info.
    private final SlugGlyph[] glyphs; // Glyph lookup table for the configured character range.
    private final float[] kerning; // Pair cache in em units; NaN denotes an unqueried pair.
    private final int firstCodepoint; // First codepoint included in the lookup table.
    private final int characterCount; // Number of codepoints included in the lookup table.
    private final float emScale; // Raw font unit to em-space scale.
    private final float ascent; // Font ascent in em units.
    private final float descent; // Font descent in em units.
    private final float lineHeight; // Line height in em units.

    /**
     * Retains trusted compilation results without copying arrays or font storage.
     *
     * @param info initialized STB view borrowing fontBuffer
     * @param fontBuffer backing bytes kept alive for STB queries
     * @param glyphs contiguous compiled glyph range
     * @param kerning lazy pair advance cache
     * @param firstCodepoint first compiled character
     * @param emScale raw-font-unit to em multiplier
     * @param ascent font ascent in em units
     * @param descent font descent in em units
     * @param lineHeight baseline spacing in em units
     */
    SlugMetrics(STBTTFontinfo info, ByteBuffer fontBuffer, SlugGlyph[] glyphs, float[] kerning, int firstCodepoint, float emScale, float ascent, float descent, float lineHeight) {
        this.info = info;
        this.fontBuffer = fontBuffer;
        this.glyphs = glyphs;
        this.kerning = kerning;
        this.firstCodepoint = firstCodepoint;
        characterCount = glyphs.length;
        this.emScale = emScale;
        this.ascent = ascent;
        this.descent = descent;
        this.lineHeight = lineHeight;
    }

    /**
     * Looks up a compiled character without substituting another glyph.
     *
     * @param codepoint requested character value
     * @return compiled entry, or null outside the configured range
     */
    public SlugGlyph glyph(int codepoint) {
        int index = codepoint - firstCodepoint;
        if (index < 0 || index >= characterCount) {
            return null;
        }
        return glyphs[index];
    }

    /**
     * Returns an em-space pair adjustment, filling the pair cache on first use
     * for compiled characters. Out-of-range pairs query retained STB information.
     *
     * @param previousCodepoint preceding character
     * @param codepoint         current character
     * @return pair advance adjustment in em units
     */
    public float kerning(int previousCodepoint, int codepoint) {
        int previousIndex = previousCodepoint - firstCodepoint;
        int index = codepoint - firstCodepoint;
        if (previousIndex < 0 || previousIndex >= characterCount || index < 0 || index >= characterCount) {
            return stbtt_GetCodepointKernAdvance(info, previousCodepoint, codepoint) * emScale;
        }
        int pair = previousIndex * characterCount + index;
        float value = kerning[pair];
        if (Float.isNaN(value)) {
            value = stbtt_GetCodepointKernAdvance(info, previousCodepoint, codepoint) * emScale;
            kerning[pair] = value;
        }
        return value;
    }

    /**
     * Returns baseline ascent in em units.
     *
     * @return positive ascent for typical fonts
     */
    public float ascent() {
        return ascent;
    }

    /**
     * Returns baseline descent in em units.
     *
     * @return negative descent for typical fonts
     */
    public float descent() {
        return descent;
    }

    /**
     * Returns baseline spacing including the font's line gap.
     *
     * @return line height in em units
     */
    public float lineHeight() {
        return lineHeight;
    }
}
