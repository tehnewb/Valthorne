package valthorne.ui;

import valthorne.graphics.Color;
import valthorne.graphics.font.TextFont;
import valthorne.graphics.font.TextRun;
import valthorne.ui.theme.StyleKey;
import valthorne.ui.theme.ResolvedStyle;
import valthorne.ui.nodes.nano.NanoCodeEditor;

import static org.lwjgl.nanovg.NanoVG.*;

/**
 * Mixed-renderer text bridge for Nano controls. A theme can provide a shared text font
 * for adaptive glyph rendering; roots supply smooth defaults when no face is configured.
 * Detached drawing without a root context retains the NanoVG renderer.
 * The active root coordinates backend transitions and painter order. Font
 * ownership stays with the theme, and controls retain their normal text input.
 * <pre>{@code
 * label.setStyle(NanoText.FONT, borrowedFont);
 * label.clearStyle(NanoText.FONT); // Restore current theme/root typography.
 * }</pre>
 */
public final class NanoText {
    /**
     * Optional text font resolved from local overrides, rules, and theme tokens.
     */
    public static final StyleKey<TextFont> FONT = StyleKey.of("nano.font", TextFont.class);

    /**
     * Optional monospace face for editable source and line-number glyphs;
     * attached editors fall back to the root's monospace text face.
     */
    public static final StyleKey<TextFont> CODE_FONT = StyleKey.of("nano.codeFont", TextFont.class);

    /**
     * Semibold face for window captions and inspector section headings.
     */
    public static final StyleKey<TextFont> HEADING_FONT = StyleKey.of("nano.headingFont", TextFont.class);

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
        TextFont font = font(node, name);
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
        TextFont font = font(node, name);
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
        return draw(node, vg, node instanceof NanoCodeEditor ? "code" : "default", x, y, text, size, color, align);
    }

    /**
     * Draws using the same named-font selection as measurement. Default and code names
     * use semantic text fonts; other registered names retain NanoVG rendering so a
     * widget's explicit custom font is not silently replaced by the root's default face.
     * The caller must have selected the named NanoVG font before entering this method.
     *
     * @param node owning control
     * @param vg active NanoVG context
     * @param name registered NanoVG font name
     * @param x aligned horizontal coordinate
     * @param y aligned vertical coordinate
     * @param text line contents
     * @param size logical em size
     * @param color borrowed text tint
     * @param align NanoVG alignment flags
     * @return end pen X for subsequent text runs
     */
    public static float draw(UINode node, long vg, String name, float x, float y, String text, float size, Color color, int align) {
        /*
         * Themes scope font selection to editor descendants, preserving custom
         * project font previews and scene fonts outside the overlay.
         */
        TextFont font = font(node, name);
        UIRoot root = node.getRoot();
        if (font == null || root == null || root.getRenderContext() == null) return nvgText(vg, x, y, text);
        float width = font.getWidth(text, size);
        float left = x - ((align & NVG_ALIGN_CENTER) != 0 ? width * .5f : (align & NVG_ALIGN_RIGHT) != 0 ? width : 0);
        float baseline = y;
        if ((align & NVG_ALIGN_TOP) != 0) baseline += font.ascent() * size;
        else if ((align & NVG_ALIGN_MIDDLE) != 0) baseline += (font.ascent() + font.descent()) * size * .5f;
        else if ((align & NVG_ALIGN_BOTTOM) != 0) baseline += font.descent() * size;
        root.getRenderContext()
                .drawNanoText(node, font, text, left, baseline, size, color);
        return left + width;
    }

    /**
     * Measures an independently shaped UTF-16 substring. Editing uses the complete
     * {@link #layout(UINode, String, float, String)} instead so caret positions
     * preserve substitutions and visual direction across range boundaries.
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
         * Substring width retains its literal measurement contract; it must not
         * be interpreted as a caret coordinate in a contextually shaped line.
         */
        if (font(node, name) == null) return NanoUtility.measureTextWidth(vg, name, size, text, start, end);
        return measureTextWidth(node, vg, name, size, text.substring(start, end));
    }

    /**
     * Borrows the selected adaptive layout for immediate caret and selection
     * queries. Registered custom NanoVG fonts return null and retain their own
     * measurement path. The next font operation may resize or rebuild the result.
     *
     * @param node owning editor
     * @param name registered font name
     * @param size logical units per em
     * @param text complete single-line display text
     * @return borrowed shaped layout, or null for a NanoVG font
     */
    public static TextRun layout(UINode node, String name, float size, String text) {
        TextFont selected = font(node, name);
        return selected == null ? null : selected.layout(text, size);
    }

    /**
     * Resolves semantic typography through the owning node's complete style cascade.
     * Local overrides and matching rules participate even without an assigned theme.
     * Heading and code faces retain their semantic priority over the general face;
     * an absent face falls back to the root's corresponding installed font.
     *
     * @param node owning control
     * @param name registered face; custom names keep NanoVG measurement and rendering
     * @return borrowed face or null for Nano rendering
     */
    private static TextFont font(UINode node, String name) {
        if (!"default".equals(name) && !"code".equals(name)) return null;
        ResolvedStyle style = node.getStyleOrDefaults();
        boolean monospace = node instanceof NanoCodeEditor || "code".equals(name);
        if (monospace) {
            TextFont code = style.get(CODE_FONT);
            return code != null ? code : node.getRoot() == null ? null : node.getRoot().getNanoTextFont(true);
        }
        TextFont heading = "editor-section".equals(node.getStyleName()) || "window-caption".equals(node.getStyleName()) ? style.get(HEADING_FONT) : null;
        if (heading != null) return heading;
        TextFont selected = style.get(FONT);
        return selected != null ? selected : node.getRoot() == null ? null : node.getRoot().getNanoTextFont(monospace);
    }
}
