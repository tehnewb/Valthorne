package valthorne.graphics.font;

import org.lwjgl.BufferUtils;
import org.lwjgl.stb.STBTTFontinfo;
import org.lwjgl.stb.STBTTPackContext;
import org.lwjgl.stb.STBTTPackedchar;
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
 * </ul>
 *
 * @author Albert Beaupre
 * @since December 6th, 2025
 */
public record FontData(TextureData textureData, int fontSize, char startChar, char endChar, int atlasWidth, int atlasHeight, float ascent, float descent, float scale, float baseline, float lineHeight, Glyph[] glyphs) {

    private static final IntBuffer ASC_BUF = BufferUtils.createIntBuffer(1);
    private static final IntBuffer DESC_BUF = BufferUtils.createIntBuffer(1);
    private static final IntBuffer GAP_BUF = BufferUtils.createIntBuffer(1);

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
        if (fontBytes == null || fontBytes.length == 0)
            throw new IllegalArgumentException("fontBytes cannot be null/empty");
        if (fontSize <= 0) throw new IllegalArgumentException("fontSize must be > 0");
        if (numChars <= 0) throw new IllegalArgumentException("numChars must be > 0");

        STBTTFontinfo info = STBTTFontinfo.create();

        ByteBuffer fontBuffer = BufferUtils.createByteBuffer(fontBytes.length);
        fontBuffer.put(fontBytes).flip();

        if (!stbtt_InitFont(info, fontBuffer)) throw new RuntimeException("Failed to init STB font");

        stbtt_GetFontVMetrics(info, ASC_BUF, DESC_BUF, GAP_BUF);

        float ascentRaw = ASC_BUF.get(0);
        float descentRaw = DESC_BUF.get(0);
        float lineGapRaw = GAP_BUF.get(0);

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

        TextureData textureData = new TextureData(rgba, (short) atlasSize, (short) atlasSize);

        Glyph[] glyphArray = new Glyph[numChars];
        for (int i = 0; i < numChars; i++) {
            int codepoint = startChar + i;
            STBTTPackedchar g = nativeChars.get(i);

            glyphArray[i] = new Glyph((char) codepoint, g.x0(), g.y0(), g.x1(), g.y1(), g.xoff(), g.yoff(), g.xoff2(), g.yoff2(), g.xadvance());
        }

        nativeChars.free();

        char endChar = (char) (startChar + numChars - 1);

        return new FontData(textureData, fontSize, (char) startChar, endChar, atlasSize, atlasSize, ascentPx, descentPx, scale, ascentPx, lineHeightPx, glyphArray);
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
     * @param c character
     * @return true if the character is inside the baked range
     */
    public boolean contains(char c) {
        int idx = c - startChar;
        return idx >= 0 && idx < glyphs.length;
    }
}
