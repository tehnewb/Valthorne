package valthorne.browser;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import org.lwjgl.stb.STBTTFontinfo;
import org.lwjgl.stb.STBTruetype;
import valthorne.graphics.font.FontData;
import valthorne.graphics.font.Glyph;
import valthorne.graphics.texture.TextureData;
import valthorne.math.MathUtils;

/**
 * Bakes the shared bitmap font API with the same STB packing algorithm as desktop.
 * The font parser owns an encoded native copy for later kerning. Atlas and glyph
 * data are copied into Java-owned storage, and packing memory is always released.
 * Construction is confined to the browser coroutine and creates no GPU resources.
 */
public final class BrowserFontBaker {
    /**
     * Prevents constructing stateless font loaders.
     */
    private BrowserFontBaker() {
    }

    /**
     * Validates and bakes one contiguous UTF-16 character range.
     * @param bytes nonempty encoded font
     * @param size positive pixel height
     * @param first first character in the unsigned UTF-16 range
     * @param count positive number of characters that remain in that range
     * @return owned CPU font data with independent kerning lifetime
     * @throws IllegalArgumentException if dimensions or range exceed supported storage
     * @throws IllegalStateException if parsing, allocation, or packing fails
     */
    public static FontData bake(byte[] bytes, int size, int first, int count) {
        /*
         * Validate sizes before multiplication and native allocation. A bounded
         * atlas prevents malformed configuration from wrapping buffer lengths.
         */
        if (bytes == null || bytes.length == 0) throw new IllegalArgumentException("fontBytes cannot be null/empty");
        if (size <= 0 || first < 0 || first > 65535 || count <= 0 || count > 65536 - first)
            throw new IllegalArgumentException("Invalid font size or UTF-16 character range");
        double estimate = Math.ceil(Math.sqrt(count) * size * 2.0);
        if (estimate > 8192) throw new IllegalArgumentException("Browser baked font atlas exceeds 8192 pixels per side");
        int side = MathUtils.nextPowerOfTwo((int) estimate);
        ByteBuffer encoded = ByteBuffer.allocateDirect(bytes.length);
        encoded.put(bytes)
                .flip();
        STBTTFontinfo info = STBTTFontinfo.create();
        if (!STBTruetype.stbtt_InitFont(info, encoded)) throw new IllegalStateException("Could not parse browser font");
        IntBuffer metrics = IntBuffer.allocate(3);
        STBTruetype.stbtt_GetFontVMetrics(info, metrics, metrics.duplicate().position(1), metrics.duplicate().position(2));
        float scale = STBTruetype.stbtt_ScaleForPixelHeight(info, size);
        int address = STBTruetype.browserBakeAtlas(info, side, size, first, count);
        if (address == 0) throw new IllegalStateException("Could not allocate or pack browser font atlas");
        try {
            int pixels = side * side;
            ByteBuffer packed = ByteBuffer.allocateDirect(count * 28)
                    .order(ByteOrder.LITTLE_ENDIAN);
            BrowserNative.readBytes(address + pixels, packed, packed.capacity());
            ByteBuffer rgba = ByteBuffer.allocateDirect(pixels * 4);
            BrowserNative.readAlphaRgba(address, rgba, pixels);
            Glyph[] glyphs = new Glyph[count];
            for (int i = 0; i < count; i++) {
                int offset = i * 28;
                glyphs[i] = new Glyph((char) (first + i), packed.getShort(offset) & 65535, packed.getShort(offset + 2) & 65535, packed.getShort(offset + 4) & 65535, packed.getShort(offset + 6) & 65535, packed.getFloat(offset + 8), packed.getFloat(offset + 12), packed.getFloat(offset + 20), packed.getFloat(offset + 24), packed.getFloat(offset + 16));
            }
            float ascent = metrics.get(0) * scale;
            float descent = metrics.get(1) * scale;
            float height = (metrics.get(0) - metrics.get(1) + metrics.get(2)) * scale;
            return new FontData(new TextureData(rgba, side, side), size, (char) first, (char) (first + count - 1), side, side, ascent, descent, scale, ascent, height, glyphs, info, encoded);
        } finally {
            BrowserNative.word(0, address);
            BrowserNative.call(BrowserOperation.IMAGE_FREE);
        }
    }
}
