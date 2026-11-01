package valthorne.graphics.font.adaptive;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import valthorne.browser.BrowserNative;
import static org.lwjgl.system.MemoryUtil.*;
import static valthorne.browser.BrowserOperation.*;

/**
 * Owns the browser FreeType/HarfBuzz/msdfgen face and reusable shaping scratch.
 * Native code retains canonical font bytes. Java owns only transfer buffers;
 * native raster allocations are copied once and freed before returning. Calls
 * run on the font's application coroutine, matching the desktop ownership model.
 */
final class AdaptiveNativeFace implements AutoCloseable {
    /**
     * Normalized MSDF sampling resolution shared with desktop.
     */
    static final int MSDF_EM = 64;
    /**
     * Full signed-distance range in atlas texels.
     */
    static final int MSDF_RANGE = 8;
    private final FontShape shapeOutput = new FontShape(); // Reusable packed native shaping output.
    private int handle; // Owned native face, zero after close.
    private ByteBuffer textBytes; // Reused UTF-16 context for the current layout.
    private final float ascent; // Normalized typographic ascent.
    private final float descent; // Normalized typographic descent.
    private final float lineHeight; // Normalized baseline spacing.
    private final float spaceAdvance; // Normalized nominal space advance.

    /**
     * Opens an independently owned scalable SFNT/WOFF face.
     * @param source immutable encoded font snapshot
     */
    AdaptiveNativeFace(byte[] source) {
        ByteBuffer encoded = memAlloc(source.length);
        try {
            encoded.put(source)
                    .flip();
            handle = BrowserNative.call(ADAPTIVE_FONT_CREATE, encoded);
            if (handle == 0) throw new IllegalArgumentException("Unable to open scalable browser font; use SFNT or WOFF outlines");
            BrowserNative.word(0, handle);
            BrowserNative.call(ADAPTIVE_FONT_METRICS);
            ascent = Float.intBitsToFloat(BrowserNative.word(0));
            descent = Float.intBitsToFloat(BrowserNative.word(1));
            lineHeight = Float.intBitsToFloat(BrowserNative.word(2));
            spaceAdvance = Float.intBitsToFloat(BrowserNative.word(3));
        } finally {
            memFree(encoded);
        }
    }
    /**
     * Returns normalized typographic ascent.
     * @return ascent in em units
     */
    float ascent() { return ascent; }
    /**
     * Returns normalized typographic descent.
     * @return descent in em units
     */
    float descent() { return descent; }
    /**
     * Returns normalized baseline spacing.
     * @return baseline spacing in em units
     */
    float lineHeight() { return lineHeight; }
    /**
     * Returns normalized nominal space advance.
     * @return horizontal space advance
     */
    float spaceAdvance() { return spaceAdvance; }
    /**
     * Resolves one nominal Unicode glyph for bounded prewarming.
     * @param codepoint Unicode scalar
     * @return glyph index or zero
     */
    int nominalGlyph(int codepoint) {
        BrowserNative.word(0, handle);
        BrowserNative.word(1, codepoint);
        return BrowserNative.call(ADAPTIVE_FONT_NOMINAL);
    }
    /**
     * Encodes complete UTF-16 shaping context once per layout rebuild.
     * @param source complete source string
     */
    void prepareText(String source) {
        int length = Math.multiplyExact(source.length(), 2);
        if (textBytes == null) textBytes = memAlloc(Math.max(256, length));
        else if (textBytes.capacity() < length) textBytes = memRealloc(textBytes, Math.max(length, textBytes.capacity() * 2));
        textBytes.order(ByteOrder.LITTLE_ENDIAN)
                .clear()
                .limit(length);
        for (int i = 0; i < source.length(); i++) textBytes.putShort(i * 2, (short) source.charAt(i));
    }
    /**
     * Drops unusually large transfers while reusing ordinary label scratch.
     */
    void releaseText() {
        if (textBytes != null && textBytes.capacity() > 8192) { memFree(textBytes); textBytes = null; }
    }
    /**
     * Shapes one directional segment with its complete source context.
     * @param start first UTF-16 unit
     * @param end boundary after the segment
     * @param rtl whether this directional segment is right to left
     * @return borrowed packed result, overwritten on the next call
     */
    FontShape shape(int start, int end, boolean rtl) {
        BrowserNative.word(0, handle);
        BrowserNative.word(1, start);
        BrowserNative.word(2, end);
        BrowserNative.word(3, rtl ? 1 : 0);
        int pointer = BrowserNative.call(ADAPTIVE_FONT_SHAPE, textBytes);
        if (BrowserNative.word(7) != 0) throw new IllegalStateException("Browser font shaping failed");
        int count = BrowserNative.word(0);
        ByteBuffer destination = shapeOutput.prepare(count);
        if (count != 0) BrowserNative.readBytes(pointer, destination, Math.multiplyExact(count, FontShape.STRIDE));
        return shapeOutput;
    }
    /**
     * Rasterizes hinted grayscale coverage from the shaped glyph index.
     * @param glyph font glyph index
     * @param size physical pixels per em
     * @param hinting immutable FreeType hinting mode
     * @return owned bottom-up R8 raster
     */
    FontRaster bitmap(int glyph, int size, FontHinting hinting) { return raster(glyph, size, hinting.loadFlags()); }
    /**
     * Generates the same normalized MSDF image used by the desktop font.
     * @param glyph font glyph index
     * @return owned bottom-up RGB8 raster
     */
    FontRaster msdf(int glyph) { return raster(glyph, 0, 0); }
    /**
     * Transfers one native raster and releases the native output in every outcome.
     * @param glyph shaped glyph index
     * @param size coverage em size, or zero for MSDF
     * @param flags FreeType outline hinting flags
     * @return independent packed image and normalized bearings
     */
    private FontRaster raster(int glyph, int size, int flags) {
        BrowserNative.word(0, handle);
        BrowserNative.word(1, glyph);
        BrowserNative.word(2, size);
        BrowserNative.word(3, flags);
        int pointer = BrowserNative.call(ADAPTIVE_FONT_RASTER);
        if (BrowserNative.word(7) != 0) throw new IllegalStateException("Browser font rasterization failed");
        int width = BrowserNative.word(0);
        int height = BrowserNative.word(1);
        float left = Float.intBitsToFloat(BrowserNative.word(2));
        float bottom = Float.intBitsToFloat(BrowserNative.word(3));
        int scale = size == 0 ? MSDF_EM : size;
        if (width == 0 || height == 0) return new FontRaster(null, 0, 0, 0, 0, scale);
        ByteBuffer pixels = null;
        try {
            if (size != 0) {
                if (width > FontAtlasPage.MAX_SIDE - 4 || height > FontAtlasPage.MAX_SIDE - 4)
                    throw new IllegalArgumentException("Glyph exceeds atlas dimensions");
                int paddedWidth = Math.addExact(width, 2);
                int paddedHeight = Math.addExact(height, 2);
                pixels = memCalloc(Math.multiplyExact(paddedWidth, paddedHeight));
                BrowserNative.readPaddedAlpha(pointer, pixels, width, height);
                return new FontRaster(pixels, paddedWidth, paddedHeight, left - 1f / scale, bottom - 1f / scale, scale);
            }
            int length = Math.multiplyExact(Math.multiplyExact(width, height), 3);
            pixels = memAlloc(length);
            BrowserNative.readBytes(pointer, pixels, length);
            return new FontRaster(pixels, width, height, left, bottom, scale);
        } catch (RuntimeException | Error failure) {
            memFree(pixels);
            throw failure;
        } finally {
            BrowserNative.word(0, pointer);
            BrowserNative.call(IMAGE_FREE);
        }
    }
    /**
     * Reads the Unicode script tag from HarfBuzz's shared immutable Unicode tables.
     * @param codepoint Unicode scalar
     * @return four-character OpenType script tag
     */
    static int script(int codepoint) {
        /*
         * The native tables match the shaper; no per-character wrappers or Java
         * UnicodeScript emulation are needed in the browser.
         */
        BrowserNative.word(0, codepoint);
        return BrowserNative.call(ADAPTIVE_FONT_SCRIPT);
    }
    @Override
    public void close() {
        if (handle == 0) return;
        BrowserNative.word(0, handle);
        BrowserNative.call(ADAPTIVE_FONT_FREE);
        handle = 0;
        memFree(textBytes);
        textBytes = null;
        shapeOutput.close();
    }
}
