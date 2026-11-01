package org.lwjgl.nanovg;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import valthorne.browser.BrowserAssets;
import valthorne.browser.BrowserNative;

import static valthorne.browser.BrowserOperation.*;

/**
 * Browser bindings to the native NanoVG bgfx renderer. The unchanged UI pipeline
 * uses the same painter-ordered bgfx views as sprites and live Slug text. Colors
 * and paints use Java-managed reusable scratch; fonts and images are owned by
 * their native NanoVG context and released through the normal root lifecycle.
 */
public final class NanoVG {
    /**
     * Native ccw value.
     */
    public static final int NVG_CCW = 0x1;
    /**
     * Native cw value.
     */
    public static final int NVG_CW = 0x2;
    /**
     * Native solid value.
     */
    public static final int NVG_SOLID = 0x1;
    /**
     * Native hole value.
     */
    public static final int NVG_HOLE = 0x2;
    /**
     * Native butt value.
     */
    public static final int NVG_BUTT = 0;
    /**
     * Native round value.
     */
    public static final int NVG_ROUND = 1;
    /**
     * Native square value.
     */
    public static final int NVG_SQUARE = 2;
    /**
     * Native bevel value.
     */
    public static final int NVG_BEVEL = 3;
    /**
     * Native miter value.
     */
    public static final int NVG_MITER = 4;
    /**
     * Native align left value.
     */
    public static final int NVG_ALIGN_LEFT = 1<<0;
    /**
     * Native align center value.
     */
    public static final int NVG_ALIGN_CENTER = 1<<1;
    /**
     * Native align right value.
     */
    public static final int NVG_ALIGN_RIGHT = 1<<2;
    /**
     * Native align top value.
     */
    public static final int NVG_ALIGN_TOP = 1<<3;
    /**
     * Native align middle value.
     */
    public static final int NVG_ALIGN_MIDDLE = 1<<4;
    /**
     * Native align bottom value.
     */
    public static final int NVG_ALIGN_BOTTOM = 1<<5;
    /**
     * Native align baseline value.
     */
    public static final int NVG_ALIGN_BASELINE = 1<<6;
    /**
     * Native zero value.
     */
    public static final int NVG_ZERO = 1<<0;
    /**
     * Native one value.
     */
    public static final int NVG_ONE = 1<<1;
    /**
     * Native src color value.
     */
    public static final int NVG_SRC_COLOR = 1<<2;
    /**
     * Native one minus src color value.
     */
    public static final int NVG_ONE_MINUS_SRC_COLOR = 1<<3;
    /**
     * Native dst color value.
     */
    public static final int NVG_DST_COLOR = 1<<4;
    /**
     * Native one minus dst color value.
     */
    public static final int NVG_ONE_MINUS_DST_COLOR = 1<<5;
    /**
     * Native src alpha value.
     */
    public static final int NVG_SRC_ALPHA = 1<<6;
    /**
     * Native one minus src alpha value.
     */
    public static final int NVG_ONE_MINUS_SRC_ALPHA = 1<<7;
    /**
     * Native dst alpha value.
     */
    public static final int NVG_DST_ALPHA = 1<<8;
    /**
     * Native one minus dst alpha value.
     */
    public static final int NVG_ONE_MINUS_DST_ALPHA = 1<<9;
    /**
     * Native src alpha saturate value.
     */
    public static final int NVG_SRC_ALPHA_SATURATE = 1<<10;
    /**
     * Native source over value.
     */
    public static final int NVG_SOURCE_OVER = 0;
    /**
     * Native source in value.
     */
    public static final int NVG_SOURCE_IN = 1;
    /**
     * Native source out value.
     */
    public static final int NVG_SOURCE_OUT = 2;
    /**
     * Native atop value.
     */
    public static final int NVG_ATOP = 3;
    /**
     * Native destination over value.
     */
    public static final int NVG_DESTINATION_OVER = 4;
    /**
     * Native destination in value.
     */
    public static final int NVG_DESTINATION_IN = 5;
    /**
     * Native destination out value.
     */
    public static final int NVG_DESTINATION_OUT = 6;
    /**
     * Native destination atop value.
     */
    public static final int NVG_DESTINATION_ATOP = 7;
    /**
     * Native lighter value.
     */
    public static final int NVG_LIGHTER = 8;
    /**
     * Native copy value.
     */
    public static final int NVG_COPY = 9;
    /**
     * Native xor value.
     */
    public static final int NVG_XOR = 10;
    /**
     * Native image generate mipmaps value.
     */
    public static final int NVG_IMAGE_GENERATE_MIPMAPS = 1<<0;
    /**
     * Native image repeatx value.
     */
    public static final int NVG_IMAGE_REPEATX = 1<<1;
    /**
     * Native image repeaty value.
     */
    public static final int NVG_IMAGE_REPEATY = 1<<2;
    /**
     * Native image flipy value.
     */
    public static final int NVG_IMAGE_FLIPY = 1<<3;
    /**
     * Native image premultiplied value.
     */
    public static final int NVG_IMAGE_PREMULTIPLIED = 1<<4;
    /**
     * Native image nearest value.
     */
    public static final int NVG_IMAGE_NEAREST = 1<<5;

    /**
     * Prevents constructing stateless vector bindings.
     */
    private NanoVG() {
    }

    /**
     * Executes the NanoVG BeginPath operation.
     * @param context native drawing value
     */
    public static void nvgBeginPath(long context) {
        /*
         * Copy explicit drawing arguments before executing on the shared native device.
         */
        BrowserNative.word(0, (int) context);
        BrowserNative.call(NVG_BEGIN_PATH);
    }

    /**
     * Executes the NanoVG ClosePath operation.
     * @param context native drawing value
     */
    public static void nvgClosePath(long context) {
        /*
         * Copy explicit drawing arguments before executing on the shared native device.
         */
        BrowserNative.word(0, (int) context);
        BrowserNative.call(NVG_CLOSE_PATH);
    }

    /**
     * Executes the NanoVG Fill operation.
     * @param context native drawing value
     */
    public static void nvgFill(long context) {
        /*
         * Copy explicit drawing arguments before executing on the shared native device.
         */
        BrowserNative.word(0, (int) context);
        BrowserNative.call(NVG_FILL);
    }

    /**
     * Executes the NanoVG Stroke operation.
     * @param context native drawing value
     */
    public static void nvgStroke(long context) {
        /*
         * Copy explicit drawing arguments before executing on the shared native device.
         */
        BrowserNative.word(0, (int) context);
        BrowserNative.call(NVG_STROKE);
    }

    /**
     * Executes the NanoVG Save operation.
     * @param context native drawing value
     */
    public static void nvgSave(long context) {
        /*
         * Copy explicit drawing arguments before executing on the shared native device.
         */
        BrowserNative.word(0, (int) context);
        BrowserNative.call(NVG_SAVE);
    }

    /**
     * Executes the NanoVG Restore operation.
     * @param context native drawing value
     */
    public static void nvgRestore(long context) {
        /*
         * Copy explicit drawing arguments before executing on the shared native device.
         */
        BrowserNative.word(0, (int) context);
        BrowserNative.call(NVG_RESTORE);
    }

    /**
     * Executes the NanoVG ResetTransform operation.
     * @param context native drawing value
     */
    public static void nvgResetTransform(long context) {
        /*
         * Copy explicit drawing arguments before executing on the shared native device.
         */
        BrowserNative.word(0, (int) context);
        BrowserNative.call(NVG_RESET_TRANSFORM);
    }

    /**
     * Executes the NanoVG ResetScissor operation.
     * @param context native drawing value
     */
    public static void nvgResetScissor(long context) {
        /*
         * Copy explicit drawing arguments before executing on the shared native device.
         */
        BrowserNative.word(0, (int) context);
        BrowserNative.call(NVG_RESET_SCISSOR);
    }

    /**
     * Executes the NanoVG EndFrame operation.
     * @param context native drawing value
     */
    public static void nvgEndFrame(long context) {
        /*
         * Copy explicit drawing arguments before executing on the shared native device.
         */
        BrowserNative.word(0, (int) context);
        BrowserNative.call(NVG_END_FRAME);
    }

    /**
     * Executes the NanoVG Translate operation.
     * @param context native drawing value
     * @param v0 native drawing value
     * @param v1 native drawing value
     */
    public static void nvgTranslate(long context, float v0, float v1) {
        /*
         * Copy explicit drawing arguments before executing on the shared native device.
         */
        BrowserNative.word(0, (int) context);
        BrowserNative.value(0, v0);
        BrowserNative.value(1, v1);
        BrowserNative.call(NVG_TRANSLATE);
    }

    /**
     * Executes the NanoVG Scale operation.
     * @param context native drawing value
     * @param v0 native drawing value
     * @param v1 native drawing value
     */
    public static void nvgScale(long context, float v0, float v1) {
        /*
         * Copy explicit drawing arguments before executing on the shared native device.
         */
        BrowserNative.word(0, (int) context);
        BrowserNative.value(0, v0);
        BrowserNative.value(1, v1);
        BrowserNative.call(NVG_SCALE);
    }

    /**
     * Executes the NanoVG MoveTo operation.
     * @param context native drawing value
     * @param v0 native drawing value
     * @param v1 native drawing value
     */
    public static void nvgMoveTo(long context, float v0, float v1) {
        /*
         * Copy explicit drawing arguments before executing on the shared native device.
         */
        BrowserNative.word(0, (int) context);
        BrowserNative.value(0, v0);
        BrowserNative.value(1, v1);
        BrowserNative.call(NVG_MOVE_TO);
    }

    /**
     * Executes the NanoVG LineTo operation.
     * @param context native drawing value
     * @param v0 native drawing value
     * @param v1 native drawing value
     */
    public static void nvgLineTo(long context, float v0, float v1) {
        /*
         * Copy explicit drawing arguments before executing on the shared native device.
         */
        BrowserNative.word(0, (int) context);
        BrowserNative.value(0, v0);
        BrowserNative.value(1, v1);
        BrowserNative.call(NVG_LINE_TO);
    }

    /**
     * Executes the NanoVG Transform operation.
     * @param context native drawing value
     * @param v0 native drawing value
     * @param v1 native drawing value
     * @param v2 native drawing value
     * @param v3 native drawing value
     * @param v4 native drawing value
     * @param v5 native drawing value
     */
    public static void nvgTransform(long context, float v0, float v1, float v2, float v3, float v4, float v5) {
        /*
         * Copy explicit drawing arguments before executing on the shared native device.
         */
        BrowserNative.word(0, (int) context);
        BrowserNative.value(0, v0);
        BrowserNative.value(1, v1);
        BrowserNative.value(2, v2);
        BrowserNative.value(3, v3);
        BrowserNative.value(4, v4);
        BrowserNative.value(5, v5);
        BrowserNative.call(NVG_TRANSFORM);
    }

    /**
     * Executes the NanoVG Rect operation.
     * @param context native drawing value
     * @param v0 native drawing value
     * @param v1 native drawing value
     * @param v2 native drawing value
     * @param v3 native drawing value
     */
    public static void nvgRect(long context, float v0, float v1, float v2, float v3) {
        /*
         * Copy explicit drawing arguments before executing on the shared native device.
         */
        BrowserNative.word(0, (int) context);
        BrowserNative.value(0, v0);
        BrowserNative.value(1, v1);
        BrowserNative.value(2, v2);
        BrowserNative.value(3, v3);
        BrowserNative.call(NVG_RECT);
    }

    /**
     * Executes the NanoVG RoundedRect operation.
     * @param context native drawing value
     * @param v0 native drawing value
     * @param v1 native drawing value
     * @param v2 native drawing value
     * @param v3 native drawing value
     * @param v4 native drawing value
     */
    public static void nvgRoundedRect(long context, float v0, float v1, float v2, float v3, float v4) {
        /*
         * Copy explicit drawing arguments before executing on the shared native device.
         */
        BrowserNative.word(0, (int) context);
        BrowserNative.value(0, v0);
        BrowserNative.value(1, v1);
        BrowserNative.value(2, v2);
        BrowserNative.value(3, v3);
        BrowserNative.value(4, v4);
        BrowserNative.call(NVG_ROUNDED_RECT);
    }

    /**
     * Executes the NanoVG Circle operation.
     * @param context native drawing value
     * @param v0 native drawing value
     * @param v1 native drawing value
     * @param v2 native drawing value
     */
    public static void nvgCircle(long context, float v0, float v1, float v2) {
        /*
         * Copy explicit drawing arguments before executing on the shared native device.
         */
        BrowserNative.word(0, (int) context);
        BrowserNative.value(0, v0);
        BrowserNative.value(1, v1);
        BrowserNative.value(2, v2);
        BrowserNative.call(NVG_CIRCLE);
    }

    /**
     * Executes the NanoVG Scissor operation.
     * @param context native drawing value
     * @param v0 native drawing value
     * @param v1 native drawing value
     * @param v2 native drawing value
     * @param v3 native drawing value
     */
    public static void nvgScissor(long context, float v0, float v1, float v2, float v3) {
        /*
         * Copy explicit drawing arguments before executing on the shared native device.
         */
        BrowserNative.word(0, (int) context);
        BrowserNative.value(0, v0);
        BrowserNative.value(1, v1);
        BrowserNative.value(2, v2);
        BrowserNative.value(3, v3);
        BrowserNative.call(NVG_SCISSOR);
    }

    /**
     * Executes the NanoVG IntersectScissor operation.
     * @param context native drawing value
     * @param v0 native drawing value
     * @param v1 native drawing value
     * @param v2 native drawing value
     * @param v3 native drawing value
     */
    public static void nvgIntersectScissor(long context, float v0, float v1, float v2, float v3) {
        /*
         * Copy explicit drawing arguments before executing on the shared native device.
         */
        BrowserNative.word(0, (int) context);
        BrowserNative.value(0, v0);
        BrowserNative.value(1, v1);
        BrowserNative.value(2, v2);
        BrowserNative.value(3, v3);
        BrowserNative.call(NVG_INTERSECT_SCISSOR);
    }

    /**
     * Executes the NanoVG StrokeWidth operation.
     * @param context native drawing value
     * @param v0 native drawing value
     */
    public static void nvgStrokeWidth(long context, float v0) {
        /*
         * Copy explicit drawing arguments before executing on the shared native device.
         */
        BrowserNative.word(0, (int) context);
        BrowserNative.value(0, v0);
        BrowserNative.call(NVG_STROKE_WIDTH);
    }

    /**
     * Executes the NanoVG GlobalAlpha operation.
     * @param context native drawing value
     * @param v0 native drawing value
     */
    public static void nvgGlobalAlpha(long context, float v0) {
        /*
         * Copy explicit drawing arguments before executing on the shared native device.
         */
        BrowserNative.word(0, (int) context);
        BrowserNative.value(0, v0);
        BrowserNative.call(NVG_GLOBAL_ALPHA);
    }

    /**
     * Executes the NanoVG FontSize operation.
     * @param context native drawing value
     * @param v0 native drawing value
     */
    public static void nvgFontSize(long context, float v0) {
        /*
         * Copy explicit drawing arguments before executing on the shared native device.
         */
        BrowserNative.word(0, (int) context);
        BrowserNative.value(0, v0);
        BrowserNative.call(NVG_FONT_SIZE);
    }

    /**
     * Executes the NanoVG BeginFrame operation.
     * @param context native drawing value
     * @param v0 native drawing value
     * @param v1 native drawing value
     * @param v2 native drawing value
     */
    public static void nvgBeginFrame(long context, float v0, float v1, float v2) {
        /*
         * Copy explicit drawing arguments before executing on the shared native device.
         */
        BrowserNative.word(0, (int) context);
        BrowserNative.value(0, v0);
        BrowserNative.value(1, v1);
        BrowserNative.value(2, v2);
        BrowserNative.call(NVG_BEGIN_FRAME);
    }

    /**
     * Sets the NanoVG LineCap policy.
     * @param context native drawing value
     * @param value native drawing value
     */
    public static void nvgLineCap(long context, int value) {
        /*
         * Copy explicit drawing arguments before executing on the shared native device.
         */
        BrowserNative.word(0, (int) context);
        BrowserNative.word(1, value);
        BrowserNative.call(NVG_LINE_CAP);
    }

    /**
     * Sets the NanoVG LineJoin policy.
     * @param context native drawing value
     * @param value native drawing value
     */
    public static void nvgLineJoin(long context, int value) {
        /*
         * Copy explicit drawing arguments before executing on the shared native device.
         */
        BrowserNative.word(0, (int) context);
        BrowserNative.word(1, value);
        BrowserNative.call(NVG_LINE_JOIN);
    }

    /**
     * Sets the NanoVG TextAlign policy.
     * @param context native drawing value
     * @param value native drawing value
     */
    public static void nvgTextAlign(long context, int value) {
        /*
         * Copy explicit drawing arguments before executing on the shared native device.
         */
        BrowserNative.word(0, (int) context);
        BrowserNative.word(1, value);
        BrowserNative.call(NVG_TEXT_ALIGN);
    }

    /**
     * Appends an arc with an explicit winding direction.
     * @param context native drawing value
     * @param x native drawing value
     * @param y native drawing value
     * @param radius native drawing value
     * @param start native drawing value
     * @param end native drawing value
     * @param direction native drawing value
     */
    public static void nvgArc(long context, float x, float y, float radius, float start, float end, int direction) {
        /*
         * Copy explicit drawing arguments before executing on the shared native device.
         */
        BrowserNative.word(0, (int) context);
        BrowserNative.word(1, direction);
        BrowserNative.value(0, x);
        BrowserNative.value(1, y);
        BrowserNative.value(2, radius);
        BrowserNative.value(3, start);
        BrowserNative.value(4, end);
        BrowserNative.call(NVG_ARC);
    }

    /**
     * Copies the NanoVG FillColor value.
     * @param context native drawing value
     * @param color native drawing value
     */
    public static void nvgFillColor(long context, NVGColor color) {
        /*
         * Copy explicit drawing arguments before executing on the shared native device.
         */
        BrowserNative.word(0, (int) context);
        color(color, 0);
        BrowserNative.call(NVG_FILL_COLOR);
    }

    /**
     * Copies the NanoVG StrokeColor value.
     * @param context native drawing value
     * @param color native drawing value
     */
    public static void nvgStrokeColor(long context, NVGColor color) {
        /*
         * Copy explicit drawing arguments before executing on the shared native device.
         */
        BrowserNative.word(0, (int) context);
        color(color, 0);
        BrowserNative.call(NVG_STROKE_COLOR);
    }

    /**
     * Copies the complete NanoVG paint value.
     * @param context native drawing value
     * @param paint native drawing value
     */
    public static void nvgFillPaint(long context, NVGPaint paint) {
        /*
         * Copy explicit drawing arguments before executing on the shared native device.
         */
        BrowserNative.word(0, (int) context);
        paint.write();
        BrowserNative.call(NVG_FILL_PAINT);
    }

    /**
     * Copies the complete NanoVG paint value.
     * @param context native drawing value
     * @param paint native drawing value
     */
    public static void nvgStrokePaint(long context, NVGPaint paint) {
        /*
         * Copy explicit drawing arguments before executing on the shared native device.
         */
        BrowserNative.word(0, (int) context);
        paint.write();
        BrowserNative.call(NVG_STROKE_PAINT);
    }

    /**
     * Creates a native gradient in caller-owned reusable paint storage.
     * @param context native drawing value
     * @param sx native drawing value
     * @param sy native drawing value
     * @param ex native drawing value
     * @param ey native drawing value
     * @param inner native drawing value
     * @param outer native drawing value
     * @param result native drawing value
     * @return native drawing result
     */
    public static NVGPaint nvgLinearGradient(long context, float sx, float sy, float ex, float ey, NVGColor inner, NVGColor outer, NVGPaint result) {
        /*
         * Copy explicit drawing arguments before executing on the shared native device.
         */
        BrowserNative.word(0, (int) context);
        BrowserNative.value(0, sx);
        BrowserNative.value(1, sy);
        BrowserNative.value(2, ex);
        BrowserNative.value(3, ey);
        color(inner, 4);
        color(outer, 8);
        BrowserNative.call(NVG_LINEAR_GRADIENT);
        return result.read();
    }

    /**
     * Creates a native gradient in caller-owned reusable paint storage.
     * @param context native drawing value
     * @param x native drawing value
     * @param y native drawing value
     * @param width native drawing value
     * @param height native drawing value
     * @param radius native drawing value
     * @param feather native drawing value
     * @param inner native drawing value
     * @param outer native drawing value
     * @param result native drawing value
     * @return native drawing result
     */
    public static NVGPaint nvgBoxGradient(long context, float x, float y, float width, float height, float radius, float feather, NVGColor inner, NVGColor outer, NVGPaint result) {
        /*
         * Copy explicit drawing arguments before executing on the shared native device.
         */
        BrowserNative.word(0, (int) context);
        BrowserNative.value(0, x);
        BrowserNative.value(1, y);
        BrowserNative.value(2, width);
        BrowserNative.value(3, height);
        BrowserNative.value(4, radius);
        BrowserNative.value(5, feather);
        color(inner, 6);
        color(outer, 10);
        BrowserNative.call(NVG_BOX_GRADIENT);
        return result.read();
    }

    /**
     * Creates a sampled image pattern in caller-owned paint storage.
     * @param context native drawing value
     * @param x native drawing value
     * @param y native drawing value
     * @param width native drawing value
     * @param height native drawing value
     * @param angle native drawing value
     * @param image native drawing value
     * @param alpha native drawing value
     * @param result native drawing value
     * @return native drawing result
     */
    public static NVGPaint nvgImagePattern(long context, float x, float y, float width, float height, float angle, int image, float alpha, NVGPaint result) {
        /*
         * Copy explicit drawing arguments before executing on the shared native device.
         */
        BrowserNative.word(0, (int) context);
        BrowserNative.word(1, image);
        BrowserNative.value(0, x);
        BrowserNative.value(1, y);
        BrowserNative.value(2, width);
        BrowserNative.value(3, height);
        BrowserNative.value(4, angle);
        BrowserNative.value(5, alpha);
        BrowserNative.call(NVG_IMAGE_PATTERN);
        return result.read();
    }

    /**
     * Copies an RGBA image into a context-owned bgfx texture.
     * @param context native drawing value
     * @param width native drawing value
     * @param height native drawing value
     * @param flags native drawing value
     * @param pixels native drawing value
     * @return native drawing result
     */
    public static int nvgCreateImageRGBA(long context, int width, int height, int flags, ByteBuffer pixels) {
        /*
         * Copy explicit drawing arguments before executing on the shared native device.
         */
        if (pixels.remaining() != Math.multiplyExact(Math.multiplyExact(width, height), 4)) throw new IllegalArgumentException("RGBA image dimensions do not match its pixels");
        BrowserNative.word(0, (int) context);
        BrowserNative.word(1, width);
        BrowserNative.word(2, height);
        BrowserNative.word(3, flags);
        return BrowserNative.call(NVG_IMAGE_CREATE, pixels);
    }

    /**
     * Releases a context-owned native image.
     * @param context native drawing value
     * @param image native drawing value
     */
    public static void nvgDeleteImage(long context, int image) {
        /*
         * Copy explicit drawing arguments before executing on the shared native device.
         */
        BrowserNative.word(0, (int) context);
        BrowserNative.word(1, image);
        BrowserNative.call(NVG_IMAGE_DELETE);
    }

    /**
     * Updates a native image from the current RGBA range.
     * @param context native drawing value
     * @param image native drawing value
     * @param pixels native drawing value
     */
    public static void nvgUpdateImage(long context, int image, ByteBuffer pixels) {
        /*
         * Copy explicit drawing arguments before executing on the shared native device.
         */
        BrowserNative.word(0, (int) context);
        BrowserNative.word(1, image);
        if (BrowserNative.call(NVG_IMAGE_UPDATE, pixels) == 0) throw new IllegalArgumentException("Image update dimensions do not match its pixels");
    }

    /**
     * Loads encoded font data from browser application assets.
     * @param context native drawing value
     * @param name native drawing value
     * @param filename native drawing value
     * @return native drawing result
     */
    public static int nvgCreateFont(long context, CharSequence name, CharSequence filename) {
        /*
         * Copy explicit drawing arguments before executing on the shared native device.
         */
        try {
            return nvgCreateFontMem(context, name, ByteBuffer.wrap(BrowserAssets.read(filename.toString())), false);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    /**
     * Registers a native font from an independent encoded copy.
     * @param context native drawing value
     * @param name native drawing value
     * @param font native drawing value
     * @param freeData native drawing value
     * @return native drawing result
     */
    public static int nvgCreateFontMem(long context, CharSequence name, ByteBuffer font, boolean freeData) {
        /*
         * Copy explicit drawing arguments before executing on the shared native device.
         */
        byte[] encodedName = name.toString().getBytes(StandardCharsets.UTF_8);
        int size = font.remaining();
        ByteBuffer upload = ByteBuffer.allocateDirect(Math.addExact(size, encodedName.length + 1));
        upload.put(font.duplicate()).put(encodedName).put((byte) 0).flip();
        BrowserNative.word(0, (int) context);
        BrowserNative.word(1, size);
        return BrowserNative.call(NVG_FONT_CREATE, upload);
    }

    /**
     * Selects a registered font face.
     * @param context native drawing value
     * @param name native drawing value
     */
    public static void nvgFontFace(long context, CharSequence name) {
        /*
         * Copy explicit drawing arguments before executing on the shared native device.
         */
        BrowserNative.word(0, (int) context);
        BrowserNative.callString(NVG_FONT_FACE, name.toString());
    }

    /**
     * Draws UTF-8 text through the native vector context.
     * @param context native drawing value
     * @param x native drawing value
     * @param y native drawing value
     * @param text native drawing value
     * @return native drawing result
     */
    public static float nvgText(long context, float x, float y, CharSequence text) {
        /*
         * Copy explicit drawing arguments before executing on the shared native device.
         */
        BrowserNative.word(0, (int) context);
        BrowserNative.value(0, x);
        BrowserNative.value(1, y);
        return Float.intBitsToFloat(BrowserNative.callString(NVG_TEXT, text.toString()));
    }

    /**
     * Measures UTF-8 text into caller-owned bounds.
     * @param context native drawing value
     * @param x native drawing value
     * @param y native drawing value
     * @param text native drawing value
     * @param bounds native drawing value
     * @return native drawing result
     */
    public static float nvgTextBounds(long context, float x, float y, CharSequence text, float[] bounds) {
        /*
         * Copy explicit drawing arguments before executing on the shared native device.
         */
        BrowserNative.word(0, (int) context);
        BrowserNative.value(0, x);
        BrowserNative.value(1, y);
        int result = BrowserNative.callString(NVG_TEXT_BOUNDS, text.toString());
        if (bounds != null) {
            for (int i = 0; i < 4; i++) bounds[i] = Float.intBitsToFloat(BrowserNative.word(i));
        }
        return Float.intBitsToFloat(result);
    }

    /**
     * Measures UTF-8 text into caller-owned bounds.
     * @param context native drawing value
     * @param x native drawing value
     * @param y native drawing value
     * @param text native drawing value
     * @param bounds native drawing value
     * @return native drawing result
     */
    public static float nvgTextBounds(long context, float x, float y, CharSequence text, FloatBuffer bounds) {
        /*
         * Copy explicit drawing arguments before executing on the shared native device.
         */
        BrowserNative.word(0, (int) context);
        BrowserNative.value(0, x);
        BrowserNative.value(1, y);
        int result = BrowserNative.callString(NVG_TEXT_BOUNDS, text.toString());
        if (bounds != null) {
            for (int i = 0; i < 4; i++) bounds.put(bounds.position() + i, Float.intBitsToFloat(BrowserNative.word(i)));
        }
        return Float.intBitsToFloat(result);
    }

    /**
     * Copies the selected font vertical metrics.
     * @param context native drawing value
     * @param ascent native drawing value
     * @param descent native drawing value
     * @param height native drawing value
     */
    public static void nvgTextMetrics(long context, float[] ascent, float[] descent, float[] height) {
        /*
         * Copy explicit drawing arguments before executing on the shared native device.
         */
        BrowserNative.word(0, (int) context);
        BrowserNative.call(NVG_TEXT_METRICS);
        if (ascent != null) ascent[0] = Float.intBitsToFloat(BrowserNative.word(0));
        if (descent != null) descent[0] = Float.intBitsToFloat(BrowserNative.word(1));
        if (height != null) height[0] = Float.intBitsToFloat(BrowserNative.word(2));
    }

    /**
     * Copies the selected font vertical metrics.
     * @param context native drawing value
     * @param ascent native drawing value
     * @param descent native drawing value
     * @param height native drawing value
     */
    public static void nvgTextMetrics(long context, FloatBuffer ascent, FloatBuffer descent, FloatBuffer height) {
        /*
         * Copy explicit drawing arguments before executing on the shared native device.
         */
        BrowserNative.word(0, (int) context);
        BrowserNative.call(NVG_TEXT_METRICS);
        if (ascent != null) ascent.put(ascent.position(), Float.intBitsToFloat(BrowserNative.word(0)));
        if (descent != null) descent.put(descent.position(), Float.intBitsToFloat(BrowserNative.word(1)));
        if (height != null) height.put(height.position(), Float.intBitsToFloat(BrowserNative.word(2)));
    }

    /**
     * Copies one reusable color into native float scratch.
     * @param color copied source color
     * @param offset first scalar destination
     */
    private static void color(NVGColor color, int offset) {
        /*
         * Native NanoVG receives a value copy and never retains Java color scratch.
         */
        BrowserNative.value(offset, color.r());
        BrowserNative.value(offset + 1, color.g());
        BrowserNative.value(offset + 2, color.b());
        BrowserNative.value(offset + 3, color.a());
    }
}
