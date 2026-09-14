package valthorne.ui;

import org.lwjgl.nanovg.NVGColor;
import valthorne.graphics.Color;

import static org.lwjgl.nanovg.NanoVG.nvgFontFace;
import static org.lwjgl.nanovg.NanoVG.nvgFontSize;
import static org.lwjgl.nanovg.NanoVG.nvgTextBounds;
import static org.lwjgl.nanovg.NanoVG.nvgTextMetrics;

/**
 * Shared NanoVG color conversion and text measurement helpers. Four per-thread
 * color slots and per-thread metric arrays avoid repeated scratch allocation.
 * Color-slot results are temporary aliases: another call to the same numbered
 * slot on that thread overwrites the returned struct. They must not be freed
 * or retained as independent color values.
 *
 * <p>Nonzero NanoVG handles must be valid on the calling render thread. Metric
 * calls select a font face and size without restoring previous text state.
 * A zero handle uses documented estimates so layout can run before a context
 * is available. Thread-local scratch does not make a shared native context safe
 * for concurrent access.</p>
 *
 * @author Albert Beaupre
 */
public final class NanoUtility {
    /** Sets opacity for a custom UI node within its root-managed draw callback. */
    public static void opacity(long vg, float alpha) { org.lwjgl.nanovg.NanoVG.nvgGlobalAlpha(vg, Math.max(0, Math.min(1, alpha))); }
    /** Registers a font from a filesystem path, including extracted classpath fonts. */
    public static int loadFont(long vg, String name, String path) { return org.lwjgl.nanovg.NanoVG.nvgCreateFont(vg, name, path); }
    /** Registers a bundled classpath font; the browser fetches the exported resource directly. */
    public static int loadResourceFont(long vg, String name, String resource) {
        return loadFont(vg, name, valthorne.io.file.ValthorneFiles.extractToTempPath(resource));
    }
    /** Draws one line without allocating geometry or color buffers per call. */
    public static void strokeLine(long vg, float x, float y, float xx, float yy, int color, float width) {
        org.lwjgl.nanovg.NanoVG.nvgBeginPath(vg); org.lwjgl.nanovg.NanoVG.nvgMoveTo(vg,x,y); org.lwjgl.nanovg.NanoVG.nvgLineTo(vg,xx,yy);
        org.lwjgl.nanovg.NanoVG.nvgStrokeColor(vg,color1(color)); org.lwjgl.nanovg.NanoVG.nvgStrokeWidth(vg,width); org.lwjgl.nanovg.NanoVG.nvgStroke(vg);
    }
    public static void strokeCircle(long vg, float x, float y, float radius, int color, float width) {
        org.lwjgl.nanovg.NanoVG.nvgBeginPath(vg); org.lwjgl.nanovg.NanoVG.nvgCircle(vg,x,y,radius);
        org.lwjgl.nanovg.NanoVG.nvgStrokeColor(vg,color1(color)); org.lwjgl.nanovg.NanoVG.nvgStrokeWidth(vg,width); org.lwjgl.nanovg.NanoVG.nvgStroke(vg);
    }
    public static void strokeDiamond(long vg, float x, float y, float radius, int color, float width) {
        org.lwjgl.nanovg.NanoVG.nvgBeginPath(vg); org.lwjgl.nanovg.NanoVG.nvgMoveTo(vg,x,y-radius); org.lwjgl.nanovg.NanoVG.nvgLineTo(vg,x+radius,y);
        org.lwjgl.nanovg.NanoVG.nvgLineTo(vg,x,y+radius); org.lwjgl.nanovg.NanoVG.nvgLineTo(vg,x-radius,y); org.lwjgl.nanovg.NanoVG.nvgClosePath(vg);
        org.lwjgl.nanovg.NanoVG.nvgStrokeColor(vg,color1(color)); org.lwjgl.nanovg.NanoVG.nvgStrokeWidth(vg,width); org.lwjgl.nanovg.NanoVG.nvgStroke(vg);
    }

    /**
     * Per-thread color scratch slot 1, shared by its Color and packed-int overloads.
     */
    private static final ThreadLocal<NVGColor> COLOR_1 = ThreadLocal.withInitial(NVGColor::create);
    /**
     * Per-thread color scratch slot 2, shared by its Color and packed-int overloads.
     */
    private static final ThreadLocal<NVGColor> COLOR_2 = ThreadLocal.withInitial(NVGColor::create);
    /**
     * Per-thread color scratch slot 3, shared by its Color and packed-int overloads.
     */
    private static final ThreadLocal<NVGColor> COLOR_3 = ThreadLocal.withInitial(NVGColor::create);
    /**
     * Per-thread color scratch slot 4, shared by its Color and packed-int overloads.
     */
    private static final ThreadLocal<NVGColor> COLOR_4 = ThreadLocal.withInitial(NVGColor::create);

    /**
     * Per-thread four-component glyph bounds scratch used by width measurements.
     */
    private static final ThreadLocal<float[]> BOUNDS = ThreadLocal.withInitial(() -> new float[4]);
    /**
     * Per-thread single-float scratch for the signed ascender metric.
     */
    private static final ThreadLocal<float[]> ASCENDER = ThreadLocal.withInitial(() -> new float[1]);
    /**
     * Per-thread single-float scratch for the signed descender metric.
     */
    private static final ThreadLocal<float[]> DESCENDER = ThreadLocal.withInitial(() -> new float[1]);
    /**
     * Per-thread single-float scratch for line-height measurements.
     */
    private static final ThreadLocal<float[]> LINE_HEIGHT = ThreadLocal.withInitial(() -> new float[1]);

    /**
     * Prevents instantiation of this stateless facade. Scratch state is initialized
     * lazily per thread by the static helpers.
     */
    private NanoUtility() {
    }

    /**
     * Copies the source into this thread's reusable color slot 1. The returned
     * struct is overwritten by the next color1 call on this thread; use distinct
     * slots when several colors must coexist during a native call.
     *
     * @param color source color, or null for opaque white
     * @return borrowed slot 1 struct; do not free it
     */
    public static NVGColor color1(Color color) {
        return toNano(color, COLOR_1.get());
    }

    /**
     * Copies the source into this thread's reusable color slot 2. The returned
     * struct is overwritten by the next color2 call on this thread; use distinct
     * slots when several colors must coexist during a native call.
     *
     * @param color source color, or null for opaque white
     * @return borrowed slot 2 struct; do not free it
     */
    public static NVGColor color2(Color color) {
        return toNano(color, COLOR_2.get());
    }

    /**
     * Copies the source into this thread's reusable color slot 3. The returned
     * struct is overwritten by the next color3 call on this thread; use distinct
     * slots when several colors must coexist during a native call.
     *
     * @param color source color, or null for opaque white
     * @return borrowed slot 3 struct; do not free it
     */
    public static NVGColor color3(Color color) {
        return toNano(color, COLOR_3.get());
    }

    /**
     * Copies the source into this thread's reusable color slot 4. The returned
     * struct is overwritten by the next color4 call on this thread; use distinct
     * slots when several colors must coexist during a native call.
     *
     * @param color source color, or null for opaque white
     * @return borrowed slot 4 struct; do not free it
     */
    public static NVGColor color4(Color color) {
        return toNano(color, COLOR_4.get());
    }

    /**
     * Decodes packed ARGB into this thread's reusable color slot 1. The result
     * aliases any previous color1 result on the same thread.
     *
     * @param rgba packed 0xAARRGGBB color
     * @return borrowed slot 1 struct; do not free it
     */
    public static NVGColor color1(int rgba) {
        return toNano(rgba, COLOR_1.get());
    }

    /**
     * Decodes packed ARGB into this thread's reusable color slot 2. The result
     * aliases any previous color2 result on the same thread.
     *
     * @param rgba packed 0xAARRGGBB color
     * @return borrowed slot 2 struct; do not free it
     */
    public static NVGColor color2(int rgba) {
        return toNano(rgba, COLOR_2.get());
    }

    /**
     * Decodes packed ARGB into this thread's reusable color slot 3. The result
     * aliases any previous color3 result on the same thread.
     *
     * @param rgba packed 0xAARRGGBB color
     * @return borrowed slot 3 struct; do not free it
     */
    public static NVGColor color3(int rgba) {
        return toNano(rgba, COLOR_3.get());
    }

    /**
     * Decodes packed ARGB into this thread's reusable color slot 4. The result
     * aliases any previous color4 result on the same thread.
     *
     * @param rgba packed 0xAARRGGBB color
     * @return borrowed slot 4 struct; do not free it
     */
    public static NVGColor color4(int rgba) {
        return toNano(rgba, COLOR_4.get());
    }

    /**
     * Copies color components into caller-provided NanoVG storage without clamping.
     * Null input writes opaque white. The target remains caller-owned.
     *
     * @param color source color, or null for opaque white
     * @param target non-null writable destination struct
     * @return the same target struct
     * @throws NullPointerException if target is null
     */
    public static NVGColor toNano(Color color, NVGColor target) {
        if (color == null) {
            target.r(1f).g(1f).b(1f).a(1f);
            return target;
        }

        target.r(color.r());
        target.g(color.g());
        target.b(color.b());
        target.a(color.a());
        return target;
    }

    /**
     * Decodes packed ARGB consistently with Color(int), despite the parameter's
     * rgba name. Each unsigned byte is divided by 255 and written to the target.
     *
     * @param rgba packed 0xAARRGGBB value
     * @param target non-null writable destination struct
     * @return the same target struct
     * @throws NullPointerException if target is null
     */
    public static NVGColor toNano(int rgba, NVGColor target) {
        target.r(((rgba >> 16) & 0xFF) / 255f);
        target.g(((rgba >> 8) & 0xFF) / 255f);
        target.b((rgba & 0xFF) / 255f);
        target.a(((rgba >>> 24) & 0xFF) / 255f);
        return target;
    }

    /**
     * Measures glyph bounds width at origin using the selected face and size.
     * Null or empty text returns zero before touching context state. A zero handle
     * estimates half font size per UTF-16 unit. Does not normalize tabs or newlines.
     *
     * @param vg valid NanoVG context, or zero for estimation
     * @param fontName registered font face for native measurement
     * @param fontSize requested text size in UI units
     * @param text text to measure, possibly null
     * @return bounds width, or estimated width without a context
     */
    public static float measureTextWidth(long vg, String fontName, float fontSize, String text) {
        if (text == null || text.isEmpty())
            return 0f;

        if (vg == 0L)
            return text.length() * fontSize * 0.5f;

        float[] bounds = BOUNDS.get();
        nvgFontSize(vg, fontSize);
        nvgFontFace(vg, fontName);
        nvgTextBounds(vg, 0f, 0f, text, bounds);
        return bounds[2] - bounds[0];
    }

    /**
     * Measures a clamped UTF-16 substring range. Start is clamped to string bounds;
     * end is clamped no earlier than start, so reversed ranges are empty. Native
     * measurement creates a substring and changes font state; a zero handle uses
     * half font size per selected code unit.
     *
     * @param vg valid NanoVG context, or zero for estimation
     * @param fontName registered font face
     * @param fontSize requested text size in UI units
     * @param text source string, possibly null
     * @param start requested inclusive UTF-16 start index
     * @param end requested exclusive UTF-16 end index
     * @return width of the selected range, or zero for null/empty text or range
     */
    public static float measureTextWidth(long vg, String fontName, float fontSize, String text, int start, int end) {
        if (text == null || text.isEmpty())
            return 0f;

        int safeStart = Math.min(text.length(), Math.max(0, start));
        int safeEnd = Math.max(safeStart, Math.min(end, text.length()));
        if (safeStart == safeEnd)
            return 0f;

        if (vg == 0L)
            return (safeEnd - safeStart) * fontSize * 0.5f;

        float[] bounds = BOUNDS.get();
        nvgFontSize(vg, fontSize);
        nvgFontFace(vg, fontName);
        nvgTextBounds(vg, 0f, 0f, text.substring(safeStart, safeEnd), bounds);
        return bounds[2] - bounds[0];
    }

    /**
     * Reads the selected font's line-height metric, including its line spacing,
     * rather than measuring a specific glyph string. Changes font face/size state.
     *
     * @param vg valid NanoVG context, or zero to return fontSize
     * @param fontName registered font face
     * @param fontSize requested text size in UI units
     * @return font line height, or fontSize without a context
     */
    public static float measureTextHeight(long vg, String fontName, float fontSize) {
        if (vg == 0L)
            return fontSize;

        float[] ascender = ASCENDER.get();
        float[] descender = DESCENDER.get();
        float[] lineHeight = LINE_HEIGHT.get();

        nvgFontFace(vg, fontName);
        nvgFontSize(vg, fontSize);
        nvgTextMetrics(vg, ascender, descender, lineHeight);
        return lineHeight[0];
    }

    /**
     * Delegates to measureTextHeight with identical font-state and fallback behavior.
     *
     * @param vg valid NanoVG context, or zero for estimation
     * @param fontName registered font face
     * @param fontSize requested text size in UI units
     * @return line-height metric, or fontSize without a context
     */
    public static float getLineHeight(long vg, String fontName, float fontSize) {
        return measureTextHeight(vg, fontName, fontSize);
    }

    /**
     * Reads the ascender metric above the text baseline after selecting the font.
     *
     * @param vg valid NanoVG context, or zero to return fontSize
     * @param fontName registered font face
     * @param fontSize requested text size in UI units
     * @return ascender metric, or fontSize without a context
     */
    public static float getAscender(long vg, String fontName, float fontSize) {
        if (vg == 0L)
            return fontSize;

        float[] ascender = ASCENDER.get();
        nvgFontFace(vg, fontName);
        nvgFontSize(vg, fontSize);
        nvgTextMetrics(vg, ascender, null, null);
        return ascender[0];
    }

    /**
     * Reads the signed descender metric after selecting the font; NanoVG normally
     * reports a negative distance for glyphs below the baseline.
     *
     * @param vg valid NanoVG context, or zero to return zero
     * @param fontName registered font face
     * @param fontSize requested text size in UI units
     * @return signed descender metric, or zero without a context
     */
    public static float getDescender(long vg, String fontName, float fontSize) {
        if (vg == 0L)
            return 0f;

        float[] descender = DESCENDER.get();
        nvgFontFace(vg, fontName);
        nvgFontSize(vg, fontSize);
        nvgTextMetrics(vg, null, descender, null);
        return descender[0];
    }

    /**
     * Computes a baseline that centers ascender-minus-descender height inside a
     * vertical interval. It is for baseline-aligned text, not top-aligned text, and
     * changes native font state through metric calls.
     *
     * @param vg valid NanoVG context, or zero for approximate metrics
     * @param fontName registered font face
     * @param fontSize requested text size in UI units
     * @param y top of the containing interval
     * @param height containing interval height
     * @return baseline Y for vertically centered text
     */
    public static float getTextCenterY(long vg, String fontName, float fontSize, float y, float height) {
        float ascender = getAscender(vg, fontName, fontSize);
        float descender = getDescender(vg, fontName, fontSize);
        float textHeight = ascender - descender;
        return y + (height - textHeight) * 0.5f + ascender;
    }
}
