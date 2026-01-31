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
 * Represents a font data structure, primarily used for rendering text with
 * information including texture data, font metrics, glyphs, and atlas information.
 * <p>
 * This class acts as an encapsulation of font loading and character-to-glyph mapping,
 * designed for optimized text rendering. It handles font metrics (ascent, descent,
 * line height, etc.) and provides a pre-baked glyph atlas for a specific font size
 * and character range.
 * <p>
 * Instances of this class are immutable and provide fast glyph lookup for characters
 * within the pre-defined range. Additionally, the class facilitates loading font data
 * either from byte arrays or file paths.
 * <p>
 * Parameters:
 * - textureData: Contains the OpenGL texture buffer and metadata for the glyph atlas.
 * - fontSize: Defines the size of the font in pixels.
 * - startChar: The first character in the glyph range.
 * - endChar: The last character in the glyph range.
 * - atlasWidth: The width of the atlas texture.
 * - atlasHeight: The height of the atlas texture.
 * - ascent: The font ascent (distance from the baseline to the top of glyphs).
 * - descent: The font descent (distance from the baseline to the bottom of glyphs).
 * - scale: The scale factor for the font when rendering.
 * - baseline: The y-offset for properly aligning glyphs relative to the baseline.
 * - lineHeight: The total height between lines of text.
 * - glyphs: Array of glyphs representing individual characters.
 *
 * @author Albert Beaupre
 * @since December 6th, 2025
 */
public record FontData(TextureData textureData, int fontSize, char startChar, char endChar, int atlasWidth, int atlasHeight, float ascent, float descent, float scale, float baseline, float lineHeight, Glyph[] glyphs) {

    private static final IntBuffer ASC_BUF = BufferUtils.createIntBuffer(1);
    private static final IntBuffer DESC_BUF = BufferUtils.createIntBuffer(1);

    /**
     * Loads font data from the specified byte array and generates a FontData object.
     *
     * @param fontBytes the byte array containing the font data; must not be null or empty
     * @param fontSize  the desired font size; must be greater than zero
     * @param startChar the starting character of the font's glyph range
     * @param numChars  the number of consecutive characters to include starting from {@code startChar}; must be greater than zero
     * @return a FontData object containing the loaded font data, including texture, glyphs, metrics, and more
     * @throws IllegalArgumentException if {@code fontBytes} is null or empty, or if {@code fontSize} or {@code numChars} is less than or equal to zero
     * @throws RuntimeException         if there is an error initializing or processing the font data
     */
    public static FontData load(byte[] fontBytes, int fontSize, int startChar, int numChars) {
        if (fontBytes == null || fontBytes.length == 0)
            throw new IllegalArgumentException("fontBytes cannot be null/empty");
        if (fontSize <= 0) throw new IllegalArgumentException("fontSize must be > 0");
        if (numChars <= 0) throw new IllegalArgumentException("numChars must be > 0");

        STBTTFontinfo info = STBTTFontinfo.create();
        ByteBuffer fontBuffer = BufferUtils.createByteBuffer(fontBytes.length).put(fontBytes).flip();
        if (!stbtt_InitFont(info, fontBuffer)) throw new RuntimeException("Failed to init STB font");

        stbtt_GetFontVMetrics(info, ASC_BUF, DESC_BUF, null);
        float ascentRaw = ASC_BUF.get(0);
        float descentRaw = DESC_BUF.get(0);
        float scale = stbtt_ScaleForPixelHeight(info, fontSize);

        float ascentPx = ascentRaw * scale;
        float descentPx = descentRaw * scale;
        float baseline = ascentRaw * scale;
        float lineHeight = (ascentRaw - descentRaw) * scale;

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

        return new FontData(textureData, fontSize, (char) startChar, endChar, atlasSize, atlasSize, ascentPx, descentPx, scale, baseline, lineHeight, glyphArray);
    }

    /**
     * Loads font data from the specified file path and generates a FontData object.
     *
     * @param path      the file path to the font file
     * @param fontSize  the desired font size
     * @param firstChar the first character to bake into the font's texture
     * @param numChars  the number of characters to include in the font's texture, starting from {@code firstChar}
     * @return a FontData object containing the loaded font data
     * @throws RuntimeException if there is an error reading the file or creating the font
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
     * @return glyph, or null if outside range
     */
    public Glyph glyph(char c) {
        int idx = c - startChar;
        if (idx < 0 || idx >= glyphs.length) return null;
        return glyphs[idx];
    }

    /**
     * @param c character
     * @return true if glyph exists in the baked range
     */
    public boolean contains(char c) {
        int idx = c - startChar;
        return idx >= 0 && idx < glyphs.length;
    }
}
