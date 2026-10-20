package valthorne.ui;

import valthorne.graphics.Color;
import valthorne.graphics.font.directwrite.DirectWriteSelection;
import valthorne.graphics.font.slug.SlugFont;
import valthorne.graphics.font.slug.SlugTextRun;
import valthorne.graphics.texture.TextureBatch;
import valthorne.ui.theme.StyleKey;

/**
 * Shared typography operations for UI text controls. Eligible Windows UI uses the
 * operating system's exact-file DirectWrite layout and native grayscale rasterizer.
 * Non-Windows, rotated, and camera-zoomed controls keep scalable Slug rendering.
 * Measurement, caret positioning, and painting select the same backend. Direct
 * world-space SlugFont/SlugTextRun use remains unchanged.
 */
public final class UIText {
    /**
     * Optional final uniform opaque destination color for native ClearType. Set only
     * when the entire glyph/overhang rectangle already has this exact opaque color
     * and contains no background image, gradient, or underlying overlapping content.
     * Null preserves native transparent grayscale. Partial batch opacity always
     * selects grayscale, even when this hint is present. The backend never guesses
     * a background or inserts an opaque rectangle into ordinary transparent controls.
     */
    public static final StyleKey<Color> OPAQUE_BACKGROUND_KEY = StyleKey.of("text.nativeOpaqueBackground", Color.class);
    /**
     * Prevents construction of the typography facade.
     */
    private UIText() {}

    /**
     * Checks transform and platform eligibility without initializing native factories.
     *
     * @param node owning UI node
     * @return whether this node uses the root's native Windows text policy
     */
    public static boolean usesWindows(UINode node) {
        /*
         * Physical DPI scaling is supported. World camera zoom and node rotations
         * intentionally preserve live outline rendering instead of scaling bitmaps.
         */
        UIRoot root = node.getRoot();
        if (root == null || !root.isWindowsTextEnabled()) return false;
        var viewport = root.getViewport();
        if (viewport != null && viewport.getCamera() != null && viewport.getCamera().getZoom() != 1) return false;
        for (UINode current = node; current != null; current = current.getParent())
            if (current.getRotation() != 0) return false;
        return true;
    }

    /**
     * Measures full shaped text using the selected UI backend.
     *
     * @param node owning node
     * @param font borrowed font
     * @param text complete text
     * @param size logical DIPs/world units per em
     * @return native or scalable advance width
     */
    public static float width(UINode node, SlugFont font, String text, float size) {
        /*
         * Empty and zero-sized controls avoid native allocation entirely.
         */
        if (font == null || text == null || text.isEmpty() || size == 0) return 0;
        return usesWindows(node) ? node.getRoot().windowsText().layout(font, text, size).width() : font.getWidth(text, size);
    }

    /**
     * Measures native/scalable line-box height for consistent intrinsic layout.
     *
     * @param node owning node
     * @param font borrowed font
     * @param size logical em size
     * @return line height in logical units
     */
    public static float lineHeight(UINode node, SlugFont font, float size) {
        /*
         * DirectWrite supplies the empty line's actual baseline and line box.
         */
        if (font == null || size == 0) return 0;
        return usesWindows(node) ? node.getRoot().windowsText().layout(font, "", size).height() : font.lineHeight() * size;
    }

    /**
     * Measures complete multiline text using the same backend as drawing.
     *
     * @param node owning node
     * @param font borrowed source font
     * @param text complete displayed text
     * @param size logical em size
     * @return total logical line-box height
     */
    public static float height(UINode node, SlugFont font, String text, float size) {
        /*
         * DirectWrite retains native line breaks and fallback-font metrics.
         */
        if (font == null || size == 0) return 0;
        return usesWindows(node) ? node.getRoot().windowsText().layout(font, text, size).height() : font.getHeight(text, size);
    }

    /**
     * Measures the selected backend's baseline offset below the line-box top.
     *
     * @param node owning node
     * @param font borrowed font
     * @param size logical em size
     * @return logical ascent to baseline
     */
    public static float ascent(UINode node, SlugFont font, float size) {
        /*
         * Use the same native line-box convention during placement and editing.
         */
        if (font == null || size == 0) return 0;
        return usesWindows(node) ? node.getRoot().windowsText().layout(font, "", size).baseline() : font.ascent() * size;
    }

    /**
     * Measures signed descent using the active line-box convention.
     *
     * @param node owning node
     * @param font borrowed font
     * @param size logical em size
     * @return negative distance from baseline to line-box bottom
     */
    public static float descent(UINode node, SlugFont font, float size) {
        /*
         * Existing controls use negative descent in their vertical centering formula.
         */
        return usesWindows(node) ? ascent(node, font, size) - lineHeight(node, font, size) : font.descent() * size;
    }

    /**
     * Measures an insertion position in the fully shaped string.
     *
     * @param node owning control
     * @param font borrowed font
     * @param text complete displayed string
     * @param size logical em size
     * @param position UTF-16 insertion position
     * @return logical horizontal caret coordinate
     */
    public static float caretX(UINode node, SlugFont font, String text, float size, int position) {
        /*
         * Native hit metrics retain ligatures and kerning context across the caret.
         */
        if (size == 0 || text.isEmpty()) return 0;
        return usesWindows(node) ? node.getRoot().windowsText().layout(font, text, size).caretX(position) : font.getWidth(text, 0, position, size);
    }

    /**
     * Maps a point to an insertion position, preserving native glyph cluster boundaries.
     *
     * @param node owning editor
     * @param font borrowed font
     * @param text displayed string
     * @param size logical em size
     * @param x horizontal point relative to layout origin
     * @return UTF-16 insertion position
     */
    public static int hitTest(UINode node, SlugFont font, String text, float size, float x) {
        /*
         * Retain the existing simple fallback while native text uses real shaping.
         */
        if (size == 0 || text.isEmpty()) return 0;
        if (usesWindows(node)) return node.getRoot().windowsText().layout(font, text, size).hitTest(x, lineHeight(node, font, size) * .5f);
        for (int i = 1; i <= text.length(); i++)
            if (font.getWidth(text, 0, i, size) >= x) return i;
        return text.length();
    }

    /**
     * Borrows native visual selection ranges when this control uses DirectWrite.
     *
     * @param node owning editor
     * @param font borrowed source font
     * @param text complete displayed line
     * @param size logical em size
     * @param start inclusive UTF-16 selection position
     * @param end exclusive UTF-16 selection position
     * @return native selection geometry, or null for the existing scalable fallback
     */
    public static DirectWriteSelection selection(UINode node, SlugFont font, String text, float size, int start, int end) {
        /*
         * Bidirectional selections cannot be represented by subtracting caret widths.
         */
        return usesWindows(node) && size > 0 ? node.getRoot().windowsText().layout(font, text, size).selection(start, end) : null;
    }

    /**
     * Draws one retained UI text run through the same backend as measurement.
     *
     * @param node owning control
     * @param batch active texture batch
     * @param run retained scalable fallback layout
     * @param font borrowed source font
     * @param text complete line contents
     * @param x baseline X
     * @param baseline baseline Y in bottom-left coordinates
     * @param size logical em size
     * @param color optional foreground override
     */
    public static void draw(UINode node, TextureBatch batch, SlugTextRun run, SlugFont font, String text, float x, float baseline, float size, Color color) {
        /*
         * Cached native pixels use normal batch ordering/clipping; curve runs remain
         * available for transformed UI and non-Windows platforms.
         */
        if (text == null || text.isEmpty() || size == 0) return;
        int background = -1;
        Color opaque = node.getStyleOrDefaults().get(OPAQUE_BACKGROUND_KEY);
        if (opaque != null && opaque.getAlpha() == 255 && batch.getOpacityMultiplier() == 1)
            background = opaque.getRed() << 16 | opaque.getGreen() << 8 | opaque.getBlue();
        if (usesWindows(node) && node.getRoot().windowsText().draw(batch, font, text, x, baseline, size, color, background)) return;
        if (run != null) run.draw(batch, x, baseline, color);
        else font.draw(batch, text, x, baseline, size, color);
    }
}
