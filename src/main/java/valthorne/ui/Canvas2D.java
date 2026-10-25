package valthorne.ui;

import static org.lwjgl.nanovg.NanoVG.*;

/**
 * Drawing operations for custom NanoContainer HUDs, in top-left UI coordinates.
 * These calls borrow an active NanoVG frame on its render thread. Geometry uses
 * UI pixels and modifies the current paint, path, font, or stroke state. No
 * context or font ownership is transferred; callers balance their own frame.
 */
public final class Canvas2D {
    /**
     * NanoVG horizontal alignment bit for left-aligned text.
     */
    public static final int ALIGN_LEFT = 1;
    /**
     * NanoVG horizontal alignment bit for centered text.
     */
    public static final int ALIGN_CENTER = 2;
    /**
     * NanoVG vertical alignment bit for top-aligned text.
     */
    public static final int ALIGN_TOP = 8;

    /**
     * Prevents construction of the stateless drawing facade.
     */
    private Canvas2D() {}

    /**
     * Sets both fill and stroke colors, clamping opacity to the normalized range.
     *
     * @param vg current NanoVG context with an active frame
     * @param rgb 24-bit RGB color
     * @param alpha opacity clamped between zero and one
     */
    public static void color(long vg, int rgb, float alpha) {
        var ink = NanoUtility.color1((Math.round(Math.clamp(alpha, 0, 1) * 255) << 24) | (rgb & 0xffffff));
        nvgFillColor(vg, ink);
        nvgStrokeColor(vg, ink);
    }

    /**
     * Starts a fresh path, discarding the previous current path.
     *
     * @param vg current NanoVG context with an active frame
     */
    public static void beginPath(long vg) {
        nvgBeginPath(vg);
    }

    /**
     * Fills the current path using the current fill paint.
     *
     * @param vg current NanoVG context with an active frame
     */
    public static void fill(long vg) {
        nvgFill(vg);
    }

    /**
     * Strokes the current path using its current paint and width.
     *
     * @param vg current NanoVG context with an active frame
     */
    public static void stroke(long vg) {
        nvgStroke(vg);
    }

    /**
     * Appends a rounded rectangle to the current path.
     *
     * @param vg current NanoVG context with an active frame
     * @param x horizontal UI coordinate
     * @param y vertical UI coordinate
     * @param w rectangle width in UI pixels
     * @param h rectangle height in UI pixels
     * @param r corner radius in UI pixels
     */
    public static void roundedRect(long vg, float x, float y, float w, float h, float r) {
        nvgRoundedRect(vg, x, y, w, h, r);
    }

    /**
     * Appends an axis-aligned rectangle to the current path.
     *
     * @param vg current NanoVG context with an active frame
     * @param x horizontal UI coordinate
     * @param y vertical UI coordinate
     * @param w rectangle width in UI pixels
     * @param h rectangle height in UI pixels
     */
    public static void rect(long vg, float x, float y, float w, float h) {
        nvgRect(vg, x, y, w, h);
    }

    /**
     * Starts a path subcontour at the supplied UI coordinate.
     *
     * @param vg current NanoVG context with an active frame
     * @param x horizontal UI coordinate
     * @param y vertical UI coordinate
     */
    public static void moveTo(long vg, float x, float y) {
        nvgMoveTo(vg, x, y);
    }

    /**
     * Appends a line from the current path cursor to a UI coordinate.
     *
     * @param vg current NanoVG context with an active frame
     * @param x horizontal UI coordinate
     * @param y vertical UI coordinate
     */
    public static void lineTo(long vg, float x, float y) {
        nvgLineTo(vg, x, y);
    }

    /**
     * Selects a font previously registered in the current NanoVG context.
     *
     * @param vg current NanoVG context with an active frame
     * @param face registered font name
     */
    public static void fontFace(long vg, String face) {
        nvgFontFace(vg, face);
    }

    /**
     * Sets text size for subsequent text drawing in UI pixels.
     *
     * @param vg current NanoVG context with an active frame
     * @param size font size in UI pixels
     */
    public static void fontSize(long vg, float size) {
        nvgFontSize(vg, size);
    }

    /**
     * Sets the combined horizontal and vertical NanoVG text alignment bits.
     *
     * @param vg current NanoVG context with an active frame
     * @param align combined NanoVG alignment flags
     */
    public static void textAlign(long vg, int align) {
        nvgTextAlign(vg, align);
    }

    /**
     * Draws text at a UI coordinate with the current font and paint.
     *
     * @param vg current NanoVG context with an active frame
     * @param x horizontal UI coordinate
     * @param y vertical UI coordinate
     * @param text text to draw
     */
    public static void text(long vg, float x, float y, String text) {
        nvgText(vg, x, y, text);
    }

    /**
     * Sets the stroke width for subsequent paths in UI pixels.
     *
     * @param vg current NanoVG context with an active frame
     * @param width stroke width in UI pixels
     */
    public static void strokeWidth(long vg, float width) {
        nvgStrokeWidth(vg, width);
    }
}
