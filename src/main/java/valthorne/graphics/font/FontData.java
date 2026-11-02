package valthorne.graphics.font;

import org.lwjgl.BufferUtils;
import org.lwjgl.stb.STBTTFontinfo;
import org.lwjgl.stb.STBTTPackContext;
import org.lwjgl.stb.STBTTPackedchar;
import org.lwjgl.system.MemoryStack;
import valthorne.graphics.texture.TextureData;
import valthorne.math.MathUtils;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.lwjgl.stb.STBTruetype.*;

/**
 * Represents baked font atlas data and metrics for a specific pixel size and character range.
 * <p>
 * Notes:
 * <ul>
 *   <li>ascent/descent/lineHeight are stored in <b>pixels</b> at the requested font size.</li>
 *   <li>stb's {@code descent} is typically negative (below baseline).</li>
 *   <li>{@code baseline} is equivalent to {@code ascent} in pixels (baseline-to-top).</li>
 *   <li>Includes an STB font handle + backing buffer so we can query <b>kerning</b>.</li>
 * </ul>
 * Loads use independent native metric and packing storage, allowing different fonts
 * to be prepared concurrently without sharing mutable output buffers.
 *
 * @author Albert Beaupre
 * @since February 2nd, 2026
 */
public record FontData(TextureData textureData, int fontSize, char startChar, char endChar, int atlasWidth, int atlasHeight, float ascent, float descent, float scale, float baseline, float lineHeight, Glyph[] glyphs, STBTTFontinfo stbInfo, ByteBuffer stbFontBuffer) {

    /**
     * Loads font data from a byte array and bakes an atlas using stb_truetype.
     *
     * @param fontBytes font file bytes
     * @param fontSize  pixel height
     * @param startChar first character codepoint to bake
     * @param numChars  number of consecutive characters to bake
     * @return baked {@link FontData}
     */
    public static FontData load(byte[] fontBytes, int fontSize, int startChar, int numChars) {
        /*
         * Per-load metric outputs keep concurrent asset preparation independent.
         * Only the font's backing buffer must survive for later kerning queries.
         */
        if (fontBytes == null || fontBytes.length == 0)
            throw new IllegalArgumentException("fontBytes cannot be null/empty");
        if (fontSize <= 0) throw new IllegalArgumentException("fontSize must be > 0");
        if (numChars <= 0) throw new IllegalArgumentException("numChars must be > 0");

        // IMPORTANT: keep this buffer alive for kerning queries later.
        ByteBuffer fontBuffer = BufferUtils.createByteBuffer(fontBytes.length);
        fontBuffer.put(fontBytes).flip();

        STBTTFontinfo info = STBTTFontinfo.create();
        if (!stbtt_InitFont(info, fontBuffer)) throw new RuntimeException("Failed to init STB font");

        float ascentRaw;
        float descentRaw;
        float lineGapRaw;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer ascent = stack.mallocInt(1);
            IntBuffer descent = stack.mallocInt(1);
            IntBuffer lineGap = stack.mallocInt(1);
            stbtt_GetFontVMetrics(info, ascent, descent, lineGap);
            ascentRaw = ascent.get(0);
            descentRaw = descent.get(0);
            lineGapRaw = lineGap.get(0);
        }

        float scale = stbtt_ScaleForPixelHeight(info, fontSize);

        float ascentPx = ascentRaw * scale;
        float descentPx = descentRaw * scale;
        float lineHeightPx = (ascentRaw - descentRaw + lineGapRaw) * scale;

        int estimatedSide = (int) Math.ceil(Math.sqrt(numChars) * fontSize * 2f);
        int atlasSize = MathUtils.nextPowerOfTwo(estimatedSide);

        ByteBuffer alphaBitmap = BufferUtils.createByteBuffer(atlasSize * atlasSize);
        STBTTPackedchar.Buffer nativeChars = STBTTPackedchar.malloc(numChars);

        try (STBTTPackContext pc = STBTTPackContext.malloc()) {
            stbtt_PackBegin(pc, alphaBitmap, atlasSize, atlasSize, 0, 1, 0);
            stbtt_PackSetOversampling(pc, 1, 1);
            stbtt_PackFontRange(pc, fontBuffer, 0, fontSize, startChar, nativeChars);
            stbtt_PackEnd(pc);
        }

        alphaBitmap.rewind();

        ByteBuffer rgba = BufferUtils.createByteBuffer(atlasSize * atlasSize * 4);
        for (int i = 0; i < atlasSize * atlasSize; i++) {
            byte a = alphaBitmap.get();
            rgba.put((byte) 255);
            rgba.put((byte) 255);
            rgba.put((byte) 255);
            rgba.put(a);
        }
        rgba.flip();

        TextureData textureData = new TextureData(rgba, atlasSize, atlasSize);

        Glyph[] glyphArray = new Glyph[numChars];
        for (int i = 0; i < numChars; i++) {
            int codepoint = startChar + i;
            STBTTPackedchar g = nativeChars.get(i);

            glyphArray[i] = new Glyph((char) codepoint, g.x0(), g.y0(), g.x1(), g.y1(), g.xoff(), g.yoff(), g.xoff2(), g.yoff2(), g.xadvance());
        }

        nativeChars.free();

        char endChar = (char) (startChar + numChars - 1);

        return new FontData(textureData, fontSize, (char) startChar, endChar, atlasSize, atlasSize, ascentPx, descentPx, scale, ascentPx, lineHeightPx, glyphArray, info, fontBuffer);
    }

    /**
     * Loads font data from a file path.
     *
     * @param path      font file path
     * @param fontSize  pixel height
     * @param firstChar first baked character
     * @param numChars  number of baked characters
     * @return baked {@link FontData}
     */
    public static FontData load(String path, int fontSize, int firstChar, int numChars) {
        try {
            return load(Files.readAllBytes(Path.of(path)), fontSize, firstChar, numChars);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Converts the current {@code FontData} object into a {@code Font} object.
     *
     * @return a new {@code Font} instance created from the current {@code FontData}.
     */
    public Font asFont() {
        return new Font(this);
    }

    /**
     * Releases native resources owned by this baked font data.
     *
     * <p>
     * Atlas disposal follows {@link TextureData} ownership. This loader's baked pixel
     * buffers and font backing storage are garbage collected once no longer referenced;
     * metric and glyph metadata remain available to callers retaining this record.
     * </p>
     */
    public void dispose() {
        if (textureData != null) {
            textureData.dispose();
        }
    }

    /**
     * Fast glyph lookup.
     *
     * @param c character
     * @return glyph or null if outside baked range
     */
    public Glyph glyph(char c) {
        int idx = c - startChar;
        if (idx < 0 || idx >= glyphs.length) return null;
        return glyphs[idx];
    }

    /**
     * Kerning advance in pixels (already scaled to the baked font size).
     *
     * <p>
     * This value should be added to your pen position before placing the second glyph.
     * Returns 0 if kerning data isn't available.
     *
     * @param left  previous character
     * @param right current character
     * @return kerning advance in pixels at baked size
     */
    public float getKerningAdvance(char left, char right) {
        if (left == 0 || right == 0) return 0f;
        if (stbInfo == null || stbFontBuffer == null) return 0f;

        int adv = stbtt_GetCodepointKernAdvance(stbInfo, left, right);
        return adv * scale;
    }

    /**
     * Tests whether a UTF-16 character falls within the contiguous baked glyph-array range. This checks the index range only and does not establish that a particular glyph has a drawable outline.
     *
     * @param c character
     * @return true if the character is inside the baked range
     */
    public boolean contains(char c) {
        int idx = c - startChar;
        return idx >= 0 && idx < glyphs.length;
    }
}
