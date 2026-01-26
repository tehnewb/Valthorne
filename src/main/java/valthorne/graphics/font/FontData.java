package valthorne.graphics.font;

import org.lwjgl.BufferUtils;
import org.lwjgl.stb.STBTTFontinfo;
import org.lwjgl.stb.STBTTPackContext;
import org.lwjgl.stb.STBTTPackedchar;
import valthorne.graphics.texture.TextureData;
import valthorne.graphics.texture.TexturePacker;
import valthorne.math.MathUtils;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.lwjgl.stb.STBTruetype.*;


/**
 * Represents font data generated from a TTF file using the STB TrueType library.
 *
 * @param bitmap      The raw bitmap data buffer containing the font atlas
 * @param charData    Array of packed character data containing glyph metrics and positions in the atlas
 * @param atlasPixels Byte array containing the font atlas pixel data
 * @param atlasSize   The width and height of the square font atlas texture
 * @param firstChar   The first character code in the font's character range
 * @param numChars    The number of characters included in the font data
 * @param ascent      The font's ascent value - distance from baseline to top of tallest glyphs
 * @param descent     The font's descent value - distance from baseline to bottom of lowest glyphs
 * @param scale       The scaling factor applied to convert from font units to pixels
 * @param baseline    The baseline position in pixels relative to the top of line
 * @author Albert Beaupre
 * @since December 25th, 2025
 */
public record FontData(ByteBuffer bitmap, STBTTPackedchar[] charData, byte[] atlasPixels, int atlasSize, int firstChar,
                       int numChars, float ascent, float descent, float scale, float baseline) {

    private static final IntBuffer ascBuf = BufferUtils.createIntBuffer(1);
    private static final IntBuffer descBuf = BufferUtils.createIntBuffer(1);

    /**
     * Loads font data from the provided byte array and generates font details and bitmap atlas
     * for rendering characters.
     *
     * @param fileData  the byte array containing the font file data
     * @param fontSize  the desired font size in pixels
     * @param firstChar the Unicode code point of the first character to load
     * @param numChars  the total number of consecutive characters to load starting from firstChar
     * @return a new instance of FontData containing the loaded font information,
     * including bitmap atlas, character metrics, and scaling information
     * @throws RuntimeException if the font initialization or packing fails
     */
    public static FontData load(byte[] fileData, int fontSize, int firstChar, int numChars) {
        STBTTFontinfo info = STBTTFontinfo.create();
        ByteBuffer fontBuffer = BufferUtils.createByteBuffer(fileData.length).put(fileData).flip();
        if (!stbtt_InitFont(info, fontBuffer))
            throw new RuntimeException("Failed to init STB font");

        stbtt_GetFontVMetrics(info, ascBuf, descBuf, null);

        float ascent = ascBuf.get(0);
        float descent = descBuf.get(0);
        float scale = stbtt_ScaleForPixelHeight(info, fontSize);
        float baseline = ascent * scale;

        int estimatedSide = (int) Math.ceil(Math.sqrt(numChars) * fontSize * 2f);
        int atlasSize = MathUtils.nextPowerOfTwo(estimatedSide);

        ByteBuffer bitmap = BufferUtils.createByteBuffer(atlasSize * atlasSize);
        byte[] atlasPixels = new byte[atlasSize * atlasSize];

        STBTTPackedchar.Buffer nativeChars = STBTTPackedchar.malloc(numChars);

        try (STBTTPackContext pc = STBTTPackContext.malloc()) {
            stbtt_PackBegin(pc, bitmap, atlasSize, atlasSize, 0, 1, 0);
            stbtt_PackSetOversampling(pc, 1, 1);
            stbtt_PackFontRange(pc, fontBuffer, 0, fontSize, firstChar, nativeChars);
            stbtt_PackEnd(pc);
        }

        STBTTPackedchar[] charData = new STBTTPackedchar[numChars];
        for (int i = 0; i < numChars; i++)
            charData[i] = STBTTPackedchar.create().set(nativeChars.get(i));

        nativeChars.free();

        bitmap.rewind();
        bitmap.get(atlasPixels);
        bitmap.rewind();

        return new FontData(bitmap, charData, atlasPixels, atlasSize, firstChar, numChars, ascent, descent, scale, baseline);
    }

    /**
     * Loads a FontData instance from a font file located at the specified file path.
     *
     * @param path      the file path to the font file
     * @param fontSize  the desired size of the font in pixels
     * @param firstChar the Unicode code point of the first character to load
     * @param numChars  the total number of consecutive characters to load starting from firstChar
     * @return a new instance of FontData containing the loaded font information
     * @throws RuntimeException if there is an error reading the file or loading the font
     */
    public static FontData load(String path, int fontSize, int firstChar, int numChars) {
        try {
            return load(Files.readAllBytes(Path.of(path)), fontSize, firstChar, numChars);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Retrieves the texture data for the specified character.
     * <p>
     * The method extracts the pixel data and dimensions of the glyph representing
     * the given character from the font atlas. The resulting texture data includes
     * a byte buffer with RGBA values and the width and height of the glyph.
     *
     * @param c the character to retrieve texture data for
     * @return a {@code TextureData} object containing the pixel data and dimensions
     * of the glyph corresponding to the specified character
     * @throws IllegalArgumentException if the character is outside the configured
     *                                  font range
     */
    public TextureData retrieveTextureDataForCharacter(char c) {
        int index = c - firstChar;
        if (index < 0 || index >= numChars)
            throw new IllegalArgumentException("Character not in font range: " + c);

        STBTTPackedchar glyph = charData[index];

        int x0 = glyph.x0();
        int y0 = glyph.y0();
        int x1 = glyph.x1();
        int y1 = glyph.y1();

        int width = x1 - x0;
        int height = y1 - y0;

        ByteBuffer out = BufferUtils.createByteBuffer(width * height * 4);

        for (int y = 0; y < height; y++) {
            int srcY = y0 + y;
            for (int x = 0; x < width; x++) {
                int srcX = x0 + x;
                int srcIndex = srcY * atlasSize + srcX;
                byte alpha = atlasPixels[srcIndex];

                out.put((byte) 255);
                out.put((byte) 255);
                out.put((byte) 255);
                out.put(alpha);
            }
        }

        out.flip();
        return new TextureData(out, (short) width, (short) height);
    }

    /**
     * Creates a texture representation from the given string by mapping each character
     * to its corresponding texture data and arranging them into a texture atlas.
     * <p>
     * This method calculates the required dimensions for the texture atlas based on the
     * width and height of the characters in the text string, then packs the individual glyph
     * textures into the atlas. The result is returned as a {@code TextureData} instance.
     *
     * @param text the input string for which the texture will be created; must not be null or empty
     * @return a {@code TextureData} object containing the packed texture atlas for the input string
     * @throws IllegalArgumentException if the input string is null or empty
     */
    public TextureData createTextureDataFromString(String text) {
        if (text == null || text.isEmpty())
            throw new IllegalArgumentException("Text cannot be null or empty");

        float minY = 0;
        float maxY = 0;
        int atlasWidth = 0;

        for (char c : text.toCharArray()) {
            int index = c - firstChar;
            if (index < 0 || index >= numChars) continue;

            STBTTPackedchar g = charData[index];
            minY = Math.min(minY, g.yoff());
            maxY = Math.max(maxY, g.yoff2());
            atlasWidth += (int) g.xadvance();
        }

        int atlasHeight = (int) Math.ceil(maxY - minY);
        int baselineY = (int) Math.ceil(maxY);

        TexturePacker packer = new TexturePacker(atlasWidth, atlasHeight);

        int cursorX = 0;

        for (char c : text.toCharArray()) {
            int index = c - firstChar;
            if (index < 0 || index >= numChars)
                continue;

            STBTTPackedchar g = charData[index];
            TextureData tex = retrieveTextureDataForCharacter(c);
            packer.addRegion(tex, 0, 0, tex.width(), tex.height(), cursorX, baselineY - (int) g.yoff2());
            cursorX += (int) g.xadvance();
        }

        return packer.bake();
    }

    /**
     * Calculates and returns the height of a single line of text based on the font's
     * ascent, descent, and scaling factor.
     * <p>
     * The line height is determined by the vertical distance between the ascent and
     * descent values, multiplied by the scaling parameter to account for the font size.
     *
     * @return the calculated height of a single line of text as a floating-point value
     */
    public float lineHeight() {
        return (ascent - descent) * scale;
    }

    /**
     * Calculates and returns the scaled ascent value of the font.
     * The ascent represents the vertical distance from the baseline to the highest point of the
     * font's glyphs, scaled by the current font size scaling factor.
     *
     * @return the scaled ascent of the font as a floating-point value
     */
    public float ascender() {
        return ascent * scale;
    }

    /**
     * Calculates and returns the scaled descent value of the font.
     * The descent represents the vertical distance from the baseline to the lowest point of the
     * font's glyphs, scaled by the current font size scaling factor.
     *
     * @return the scaled descent of the font as a floating-point value
     */
    public float descender() {
        return descent * scale;
    }
}
