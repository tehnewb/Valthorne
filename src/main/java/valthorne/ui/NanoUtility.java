package valthorne.ui;

import org.lwjgl.nanovg.NVGColor;
import valthorne.graphics.Color;

import static org.lwjgl.nanovg.NanoVG.*;

public final class NanoUtility {

    private static final ThreadLocal<NVGColor> COLOR_1 = ThreadLocal.withInitial(NVGColor::create);
    private static final ThreadLocal<NVGColor> COLOR_2 = ThreadLocal.withInitial(NVGColor::create);
    private static final ThreadLocal<NVGColor> COLOR_3 = ThreadLocal.withInitial(NVGColor::create);
    private static final ThreadLocal<NVGColor> COLOR_4 = ThreadLocal.withInitial(NVGColor::create);

    private static final ThreadLocal<float[]> BOUNDS = ThreadLocal.withInitial(() -> new float[4]);
    private static final ThreadLocal<float[]> ASCENDER = ThreadLocal.withInitial(() -> new float[1]);
    private static final ThreadLocal<float[]> DESCENDER = ThreadLocal.withInitial(() -> new float[1]);
    private static final ThreadLocal<float[]> LINE_HEIGHT = ThreadLocal.withInitial(() -> new float[1]);

    private NanoUtility() {
    }

    public static NVGColor color1(Color color) {
        return toNano(color, COLOR_1.get());
    }

    public static NVGColor color2(Color color) {
        return toNano(color, COLOR_2.get());
    }

    public static NVGColor color3(Color color) {
        return toNano(color, COLOR_3.get());
    }

    public static NVGColor color4(Color color) {
        return toNano(color, COLOR_4.get());
    }

    public static NVGColor color1(int rgba) {
        return toNano(rgba, COLOR_1.get());
    }

    public static NVGColor color2(int rgba) {
        return toNano(rgba, COLOR_2.get());
    }

    public static NVGColor color3(int rgba) {
        return toNano(rgba, COLOR_3.get());
    }

    public static NVGColor color4(int rgba) {
        return toNano(rgba, COLOR_4.get());
    }

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

    public static NVGColor toNano(int rgba, NVGColor target) {
        target.r(((rgba >> 24) & 0xFF) / 255f);
        target.g(((rgba >> 16) & 0xFF) / 255f);
        target.b(((rgba >> 8) & 0xFF) / 255f);
        target.a((rgba & 0xFF) / 255f);
        return target;
    }

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

    public static float measureTextWidth(long vg, String fontName, float fontSize, String text, int start, int end) {
        if (text == null || text.isEmpty())
            return 0f;

        int safeStart = Math.max(0, start);
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

    public static float getLineHeight(long vg, String fontName, float fontSize) {
        return measureTextHeight(vg, fontName, fontSize);
    }

    public static float getAscender(long vg, String fontName, float fontSize) {
        if (vg == 0L)
            return fontSize;

        float[] ascender = ASCENDER.get();
        nvgFontFace(vg, fontName);
        nvgFontSize(vg, fontSize);
        nvgTextMetrics(vg, ascender, null, null);
        return ascender[0];
    }

    public static float getDescender(long vg, String fontName, float fontSize) {
        if (vg == 0L)
            return 0f;

        float[] descender = DESCENDER.get();
        nvgFontFace(vg, fontName);
        nvgFontSize(vg, fontSize);
        nvgTextMetrics(vg, null, descender, null);
        return descender[0];
    }

    public static float getTextCenterY(long vg, String fontName, float fontSize, float y, float height) {
        float ascender = getAscender(vg, fontName, fontSize);
        float descender = getDescender(vg, fontName, fontSize);
        float textHeight = ascender - descender;
        return y + (height - textHeight) * 0.5f + ascender;
    }
}