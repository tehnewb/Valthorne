package valthorne.graphics.font;

import org.lwjgl.BufferUtils;
import org.lwjgl.stb.STBTTFontinfo;
import org.lwjgl.stb.STBTTPackContext;
import org.lwjgl.stb.STBTTPackedchar;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.lwjgl.stb.STBTruetype.*;


/**
 * Represents font data generated from a TrueType font. This data includes a bitmap atlas, character metrics,
 * and additional metadata for rendering text.
 * <p>
 * The class is immutable and uses the following fields:
 * - A bitmap representing the font atlas.
 * - Character data for individual glyphs.
 * - Atlas pixel data and size.
 * - Details regarding the first and total number of characters rendered.
 * - Font metrics such as ascent, descent, scaling factor, and baseline offset.
 *
 * @author Albert Beaupre
 * @since December 6th, 2025
 */
public record FontData(ByteBuffer bitmap, STBTTPackedchar[] charData, byte[] atlasPixels, int atlasSize, int firstChar,
                       int numChars, float ascent, float descent, float scale, float baseline) {

    private static final IntBuffer ascBuf = BufferUtils.createIntBuffer(1);
    private static final IntBuffer descBuf = BufferUtils.createIntBuffer(1);
    private static final int atlas_size = 1024;
    private static final ByteBuffer bitmap_const = BufferUtils.createByteBuffer(atlas_size * atlas_size);
    private static final byte[] atlas_pixels = new byte[atlas_size * atlas_size];

    public static FontData load(byte[] fileData, int fontSize, int firstChar, int numChars) {
        // Load TTF file into STB
        STBTTFontinfo info = STBTTFontinfo.create();
        ByteBuffer fontBuffer = BufferUtils.createByteBuffer(fileData.length).put(fileData).flip();
        if (!stbtt_InitFont(info, fontBuffer)) throw new RuntimeException("Failed to init STB font");

        stbtt_GetFontVMetrics(info, ascBuf, descBuf, null);

        float ascent = ascBuf.get(0);
        float descent = descBuf.get(0);
        float scale = stbtt_ScaleForPixelHeight(info, fontSize);
        float baseline = ascent * scale;

        ByteBuffer bitmap = bitmap_const;

        // Allocate native character storage
        STBTTPackedchar.Buffer nativeChars = STBTTPackedchar.malloc(numChars);

        // STB font packing
        try (STBTTPackContext pc = STBTTPackContext.malloc()) {
            stbtt_PackBegin(pc, bitmap, atlas_size, atlas_size, 0, 1, 0);
            stbtt_PackSetOversampling(pc, 1, 1);
            stbtt_PackFontRange(pc, fontBuffer, 0, fontSize, firstChar, nativeChars);
            stbtt_PackEnd(pc);
        }

        // Copy native STB glyph metrics into Java array
        STBTTPackedchar[] charData = new STBTTPackedchar[numChars];
        for (int i = 0; i < numChars; i++)
            charData[i] = STBTTPackedchar.create().set(nativeChars.get(i));

        nativeChars.free();

        bitmap.rewind();
        byte[] atlasPixels = atlas_pixels;
        bitmap.get(atlasPixels);
        bitmap.rewind();
        return new FontData(bitmap, charData, atlasPixels, atlas_size, firstChar, numChars, ascent, descent, scale, baseline);
    }

    public static FontData load(String path, int fontSize, int firstChar, int numChars) {
        try {
            return FontData.load(Files.readAllBytes(Path.of(path)), fontSize, firstChar, numChars);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public float lineHeight() {
        return (ascent() - descent()) * scale();
    }

    /**
     * Returns the ascender height in pixels.
     * This is the distance from the baseline to the top of the tallest glyphs (e.g., capitals, 'h', 'l').
     * Use this for consistent top-alignment of text regardless of descenders like '_' or 'g'.
     *
     * @return pixel ascender height
     */
    public float ascender() {
        return ascent * scale;
    }

    /**
     * Returns the descender depth in pixels (usually negative).
     * This is the distance from the baseline down to the lowest point of deep glyphs.
     *
     * @return pixel descender (negative value)
     */
    public float descender() {
        return descent * scale;
    }

}
