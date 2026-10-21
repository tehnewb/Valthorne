package valthorne.ui;

import valthorne.graphics.Color;
import valthorne.graphics.font.directwrite.DirectWriteBackend;
import valthorne.graphics.font.TextSelection;
import valthorne.graphics.font.slug.SlugFont;
import valthorne.graphics.font.slug.SlugTextRun;
import valthorne.graphics.texture.TextureBatch;
import valthorne.ui.theme.StyleKey;

/**
 * Shared typography for UI controls. Slug is the portable default: HarfBuzz supplies
 * full-context glyph positions and FreeType hints axis-aligned UI geometry at its
 * physical size. Rotated and camera-zoomed controls retain scalable world outlines.
 * DirectWrite is an explicit Windows comparison/override. Measurement, caret
 * positioning, selection, and painting share the selected shaping backend.
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
     * Stable platform eligibility avoids repeating operating-system string work per draw.
     */
    private static final boolean WINDOWS_SUPPORTED = DirectWriteBackend.isSupported();
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
        return fallbackReason(node) == UITextFallbackReason.NONE;
    }

    /**
     * Checks portable hinted-outline policy without querying OpenGL or loading native fonts.
     * Actual framebuffer size/projection is validated when drawing. Logical metrics
     * come from the same shaped source regardless of hinted-view eligibility.
     *
     * @param node owning UI node
     * @return whether transform policy allows the hinted Slug UI path
     */
    public static boolean usesHintedSlug(UINode node) {
        /*
         * World camera zoom and node rotations retain continuous scalable geometry.
         */
        UIRoot root = node.getRoot();
        return root != null && !root.isWindowsTextEnabled() && slugFallbackReason(node) == UITextFallbackReason.NONE;
    }

    /**
     * Resolves why physical-size UI hinting is unavailable for this node.
     *
     * @param node owning UI node
     * @return NONE for hinted UI, otherwise an explicit scalable fallback reason
     */
    private static UITextFallbackReason slugFallbackReason(UINode node) {
        /*
         * Portable eligibility does not depend on the Windows-only comparison policy.
         */
        UIRoot root = node.getRoot();
        if (root == null) return UITextFallbackReason.DETACHED;
        if (!root.isSlugHintingEnabled()) return UITextFallbackReason.DISABLED;
        var viewport = root.getViewport();
        if (viewport != null && viewport.getCamera() != null && viewport.getCamera().getZoom() != 1) return UITextFallbackReason.CAMERA_ZOOM;
        for (UINode current = node; current != null; current = current.getParent())
            if (current.getRotation() != 0) return UITextFallbackReason.ROTATION;
        return UITextFallbackReason.NONE;
    }

    /**
     * Determines the transform/policy fallback without initializing native resources.
     *
     * @param node owning UI node
     * @return NONE for native eligibility, otherwise the relevant fallback reason
     */
    private static UITextFallbackReason fallbackReason(UINode node) {
        /*
         * Keep measurement and drawing eligibility identical while retaining a
         * precise reason for diagnostics on an actual text submission.
         */
        UIRoot root = node.getRoot();
        if (root == null) return UITextFallbackReason.DETACHED;
        if (!WINDOWS_SUPPORTED) return UITextFallbackReason.NON_WINDOWS;
        if (!root.isWindowsTextEnabled()) return UITextFallbackReason.DISABLED;
        var viewport = root.getViewport();
        if (viewport != null && viewport.getCamera() != null && viewport.getCamera().getZoom() != 1) return UITextFallbackReason.CAMERA_ZOOM;
        for (UINode current = node; current != null; current = current.getParent())
            if (current.getRotation() != 0) return UITextFallbackReason.ROTATION;
        return UITextFallbackReason.NONE;
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
        return usesWindows(node) ? node.getRoot().windowsText().layout(font, text, size).caretX(position) : font.shape(text).caretX(position) * size;
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
         * Both backends hit-test complete shaped context rather than prefix widths.
         */
        if (size == 0 || text.isEmpty()) return 0;
        if (usesWindows(node)) return node.getRoot().windowsText().layout(font, text, size).hitTest(x, lineHeight(node, font, size) * .5f);
        return font.shape(text).hitTest(x / size, font.lineHeight() * .5f);
    }

    /**
     * Borrows visual selection ranges from the selected full-context shaping backend.
     *
     * @param node owning editor
     * @param font borrowed source font
     * @param text complete displayed line
     * @param size logical em size
     * @param start inclusive UTF-16 selection position
     * @param end exclusive UTF-16 selection position
     * @return shaped selection geometry, or null for an absent font or zero size
     */
    public static TextSelection selection(UINode node, SlugFont font, String text, float size, int start, int end) {
        /*
         * Bidirectional selections cannot be represented by subtracting caret widths.
         */
        if (font == null || size <= 0) return null;
        return usesWindows(node) ? node.getRoot().windowsText().layout(font, text, size).selection(start, end) : font.shape(text).selection(start, end, size);
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
         * Hinted Slug is the default for ordinary UI. Explicit DirectWrite and
         * transformed scalable runs preserve the same batch ordering/clipping.
         */
        if (text == null || text.isEmpty() || size == 0) return;
        int background = -1;
        Color opaque = node.getStyleOrDefaults().get(OPAQUE_BACKGROUND_KEY);
        if (opaque != null && opaque.getAlpha() == 255 && batch.getOpacityMultiplier() == 1)
            background = opaque.getRed() << 16 | opaque.getGreen() << 8 | opaque.getBlue();
        UITextFallbackReason reason = fallbackReason(node);
        UIRoot root = node.getRoot();
        if (reason == UITextFallbackReason.NONE) {
            reason = root.windowsText().draw(batch, font, text, x, baseline, size, color, background);
            if (reason == UITextFallbackReason.NONE) return;
        }
        if (root != null && !root.isWindowsTextEnabled()) {
            reason = slugFallbackReason(node);
            if (reason == UITextFallbackReason.NONE) {
                reason = root.slugText().draw(batch, font, text, x, baseline, size, color);
                if (reason == UITextFallbackReason.NONE) return;
            }
        }
        if (run != null) run.draw(batch, x, baseline, color);
        else font.draw(batch, text, x, baseline, size, color);
        if (root != null) root.recordTextRendering(UITextRenderingMode.SLUG, reason, 0, 0);
    }
}
