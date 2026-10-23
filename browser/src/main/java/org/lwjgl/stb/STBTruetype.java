package org.lwjgl.stb;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import valthorne.browser.BrowserNative;

import static valthorne.browser.BrowserOperation.*;

/**
 * Exposes STB's CPU outline and metric operations through the browser module.
 * The existing Slug compiler consumes the same curve commands and normalized
 * metrics as on desktop; font data never depends on the WebGL device lifetime.
 */
public final class STBTruetype {
    /**
     * Begins an outline contour.
     */
    public static final byte STBTT_vmove = 1;
    /**
     * Appends a straight segment.
     */
    public static final byte STBTT_vline = 2;
    /**
     * Appends a quadratic segment.
     */
    public static final byte STBTT_vcurve = 3;
    /**
     * Appends a cubic segment.
     */
    public static final byte STBTT_vcubic = 4;

    /**
     * Prevents constructing stateless font-parser bindings.
     */
    private STBTruetype() {
    }

    /**
     * Parses an owned native copy of encoded font bytes.
     * @param info destination parser descriptor
     * @param bytes TrueType or OpenType bytes
     * @return whether initialization succeeded
     */
    public static boolean stbtt_InitFont(STBTTFontinfo info, ByteBuffer bytes) {
        /*
         * Retain native encoded bytes with the CPU metric descriptor, independently
         * of texture uploads or graphics resource restoration.
         */
        return info.initialize(bytes);
    }

    /**
     * Copies vertical font metrics in raw font units.
     * @param info initialized parser
     * @param ascent ascent destination
     * @param descent descent destination
     * @param gap line-gap destination
     */
    public static void stbtt_GetFontVMetrics(STBTTFontinfo info, IntBuffer ascent, IntBuffer descent, IntBuffer gap) {
        /*
         * One native query returns all three related metrics without temporary arrays.
         */
        BrowserNative.word(0, info.pointer());
        BrowserNative.call(FONT_METRICS);
        ascent.put(ascent.position(), BrowserNative.word(0));
        descent.put(descent.position(), BrowserNative.word(1));
        gap.put(gap.position(), BrowserNative.word(2));
    }

    /**
     * Computes the multiplier from raw font units to a requested em size.
     * @param info initialized parser
     * @param pixels requested pixels per em
     * @return raw-unit scale
     */
    public static float stbtt_ScaleForMappingEmToPixels(STBTTFontinfo info, float pixels) {
        /*
         * STB's exact mapping preserves the desktop compiler's em-space metrics.
         */
        BrowserNative.word(0, info.pointer());
        BrowserNative.call(FONT_METRICS);
        return Float.intBitsToFloat(BrowserNative.word(3)) * pixels;
    }

    /**
     * Copies one character's raw horizontal metrics.
     * @param info initialized parser
     * @param codepoint requested Unicode value
     * @param advance advance destination
     * @param bearing left-side-bearing destination
     */
    public static void stbtt_GetCodepointHMetrics(STBTTFontinfo info, int codepoint, IntBuffer advance, IntBuffer bearing) {
        /*
         * Return both metrics from one parser query at the compilation boundary.
         */
        BrowserNative.word(0, info.pointer());
        BrowserNative.word(1, codepoint);
        BrowserNative.call(FONT_HORIZONTAL);
        advance.put(advance.position(), BrowserNative.word(0));
        bearing.put(bearing.position(), BrowserNative.word(1));
    }

    /**
     * Copies one character's font-space outline bounds.
     * @param info initialized parser
     * @param codepoint requested Unicode value
     * @param x0 minimum horizontal destination
     * @param y0 minimum vertical destination
     * @param x1 maximum horizontal destination
     * @param y1 maximum vertical destination
     * @return whether the character has an outline
     */
    public static boolean stbtt_GetCodepointBox(STBTTFontinfo info, int codepoint, IntBuffer x0, IntBuffer y0, IntBuffer x1, IntBuffer y1) {
        /*
         * Copy bounds before another native command reuses result scratch storage.
         */
        BrowserNative.word(0, info.pointer());
        BrowserNative.word(1, codepoint);
        boolean present = BrowserNative.call(FONT_BOX) != 0;
        x0.put(x0.position(), BrowserNative.word(0));
        y0.put(y0.position(), BrowserNative.word(1));
        x1.put(x1.position(), BrowserNative.word(2));
        y1.put(y1.position(), BrowserNative.word(3));
        return present;
    }

    /**
     * Queries one raw pair adjustment for the shared lazy kerning cache.
     * @param info initialized parser
     * @param previous previous Unicode value
     * @param current current Unicode value
     * @return raw font-unit pair adjustment
     */
    public static int stbtt_GetCodepointKernAdvance(STBTTFontinfo info, int previous, int current) {
        /*
         * SlugMetrics caches in-range results, so repeated pairs avoid this bridge.
         */
        BrowserNative.word(0, info.pointer());
        BrowserNative.word(1, previous);
        BrowserNative.word(2, current);
        return BrowserNative.call(FONT_KERNING);
    }

    /**
     * Allocates the native outline commands for one character.
     * @param info initialized parser
     * @param codepoint requested Unicode value
     * @return owned shape array, or null for an empty glyph
     */
    public static STBTTVertexBuffer stbtt_GetCodepointShape(STBTTFontinfo info, int codepoint) {
        /*
         * Native shape arrays are temporary compiler inputs and must be freed in
         * the caller's existing finally block after contour conversion.
         */
        BrowserNative.word(0, info.pointer());
        BrowserNative.word(1, codepoint);
        int pointer = BrowserNative.call(FONT_SHAPE);
        int count = BrowserNative.word(0);
        if (pointer != 0 && count <= 0) {
            BrowserNative.word(0, info.pointer());
            BrowserNative.word(1, pointer);
            BrowserNative.call(FONT_SHAPE_FREE);
            return null;
        }
        return pointer == 0 || count <= 0 ? null : new STBTTVertexBuffer(pointer, count);
    }

    /**
     * Releases a native shape once after its commands have been compiled.
     * @param info parser that allocated the shape
     * @param vertices former shape array, including null
     */
    public static void stbtt_FreeShape(STBTTFontinfo info, STBTTVertexBuffer vertices) {
        /*
         * Null and repeated release do not reach the native allocator.
         */
        if (vertices == null || vertices.pointer() == 0) return;
        BrowserNative.word(0, info.pointer());
        BrowserNative.word(1, vertices.pointer());
        BrowserNative.call(FONT_SHAPE_FREE);
        vertices.released();
    }
}
