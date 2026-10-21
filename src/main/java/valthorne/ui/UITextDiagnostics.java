package valthorne.ui;

import valthorne.graphics.font.directwrite.DirectWriteRenderingSettings;

/**
 * On-demand snapshot of the owning root's most recent text submission. Native
 * scale values are physical framebuffer pixels per logical DIP, not Windows'
 * display percentage. Settings may remain available after a later fallback draw;
 * the mode and reason identify which renderer actually handled that submission.
 * Calling the accessor allocates this snapshot; rendering does not allocate one.
 *
 * @param mode renderer and actual native target antialiasing mode
 * @param fallbackReason reason native text was bypassed or nothing was drawn
 * @param scaleX physical pixels per DIP horizontally, or zero for other renderers
 * @param scaleY physical pixels per DIP vertically, or zero for other renderers
 * @param nativeSettings latest native settings, or null before native initialization
 */
public record UITextDiagnostics(UITextRenderingMode mode, UITextFallbackReason fallbackReason, float scaleX, float scaleY, DirectWriteRenderingSettings nativeSettings) {}
