package valthorne.ui;

import valthorne.graphics.Color;
import valthorne.graphics.font.slug.SlugFont;
import valthorne.ui.theme.StyleKey;
import valthorne.ui.nodes.nano.NanoCodeEditor;

import static org.lwjgl.nanovg.NanoVG.*;

/**
 * Mixed-renderer text bridge for Nano controls. A theme can provide a Slug font
 * for smooth curve glyphs; roots supply smooth defaults when no face is configured.
 * Detached drawing without a root context retains the NanoVG renderer.
 * The active root coordinates backend transitions and painter order. Font
 * ownership stays with the theme, and controls retain their normal text input.
 */
public final class NanoText {
    /**
     * Optional inherited curve font; absent tokens preserve NanoVG text.
     */
    public static final StyleKey<SlugFont> SLUG_FONT = StyleKey.of("nano.slugFont", SlugFont.class);
    /**
     * Optional monospace face for editable source and line-number glyphs.
     */
    public static final StyleKey<SlugFont> SLUG_CODE_FONT = StyleKey.of("nano.slugCodeFont", SlugFont.class);
    /**
     * Semibold face for window captions and inspector section headings.
     */
    public static final StyleKey<SlugFont> SLUG_HEADING_FONT = StyleKey.of("nano.slugHeadingFont", SlugFont.class);

    /**
     * Prevents construction of the text rendering facade.
     */
    private NanoText() {}

    /**
     * Measures advances with the renderer selected for the owning control.
     *
     * @param node owning control
     * @param vg NanoVG context or zero during detached layout
     * @param name fallback Nano font name
     * @param size logical em size
     * @param text line contents
     * @return horizontal advance
     */
    public static float measureTextWidth(UINode node, long vg, String name, float size, String text) {
        /*
         * Matching glyph metrics keep carets, selection, and scrolling aligned
         * with smooth text instead of borrowing the NanoVG raster metrics.
         */
        SlugFont font = font(node);
        return font == null ? NanoUtility.measureTextWidth(vg, name, size, text) : font.getWidth(text, size);
    }

    /**
     * Measures line height with the selected text renderer.
     *
     * @param node owning control
     * @param vg NanoVG context or zero during detached layout
     * @param name fallback Nano font name
     * @param size logical em size
     * @return logical line height
     */
    public static float measureTextHeight(UINode node, long vg, String name, float size) {
        /*
         * Curve metrics remain available outside a frame, avoiding estimates
         * when editor controls lay out before their first draw.
         */
        SlugFont font = font(node);
        return font == null ? NanoUtility.measureTextHeight(vg, name, size) : font.lineHeight() * size;
    }

    /**
     * Draws a line with explicit styling through the configured renderer.
     *
     * @param node owning control
     * @param vg active NanoVG context
     * @param x aligned X coordinate in top-left layout space
     * @param y aligned Y coordinate in top-left layout space
     * @param text line contents
     * @param size em size in logical units
     * @param color text tint
     * @param align NanoVG alignment flags
     * @return end pen X for syntax-highlighted runs
     */
    public static float draw(UINode node, long vg, float x, float y, String text, float size, Color color, int align) {
        /*
         * Themes scope font selection to editor descendants, preserving custom
         * project font previews and scene fonts outside the overlay.
         */
        SlugFont font = font(node);
        UIRoot root = node.getRoot();
        if (font == null || root == null || root.getRenderContext() == null) return nvgText(vg, x, y, text);
        float width = font.getWidth(text, size);
        float left = x - ((align & NVG_ALIGN_CENTER) != 0 ? width * .5f : (align & NVG_ALIGN_RIGHT) != 0 ? width : 0);
        float baseline = y;
        if ((align & NVG_ALIGN_TOP) != 0) baseline += font.ascent() * size;
        else if ((align & NVG_ALIGN_MIDDLE) != 0) baseline += (font.ascent() + font.descent()) * size * .5f;
        else if ((align & NVG_ALIGN_BOTTOM) != 0) baseline += font.descent() * size;
        root.getRenderContext().drawNanoSlug(node, font, text, left, baseline, size, color);
        return left + width;
    }

    /**
     * Measures a UTF-16 range using the same glyph advances as its editor.
     *
     * @param node owning editor
     * @param vg fallback Nano context
     * @param name fallback font name
     * @param size logical em size
     * @param text full text
     * @param start inclusive range start
     * @param end exclusive range end
     * @return horizontal advance
     */
    public static float measureTextWidth(UINode node, long vg, String name, float size, String text, int start, int end) {
        /*
         * Range slicing occurs only for caret measurement, matching the existing
         * editor's prefix measurement semantics.
         */
        if (font(node) == null) return NanoUtility.measureTextWidth(vg, name, size, text, start, end);
        return measureTextWidth(node, vg, name, size, text.substring(start, end));
    }

    /**
     * Resolves the semantic font inherited by an editor control.
     *
     * @param node owning control
     * @return borrowed face or null for Nano rendering
     */
    private static SlugFont font(UINode node) {
        /*
         * Code controls use a matching monospace face while normal controls
         * share the proportional interface face.
         */
        if (node.getTheme() == null) return node.getRoot() == null ? null : node.getRoot().getNanoTextFont(node instanceof NanoCodeEditor);
        SlugFont heading = "editor-section".equals(node.getStyleName()) || "window-caption".equals(node.getStyleName()) ? node.getTheme().getToken(SLUG_HEADING_FONT) : null;
        if (heading != null) return heading;
        SlugFont code = node instanceof NanoCodeEditor ? node.getTheme().getToken(SLUG_CODE_FONT) : null;
        SlugFont selected = code == null ? node.getTheme().getToken(SLUG_FONT) : code;
        return selected != null ? selected : node.getRoot() == null ? null : node.getRoot().getNanoTextFont(node instanceof NanoCodeEditor);
    }
}
