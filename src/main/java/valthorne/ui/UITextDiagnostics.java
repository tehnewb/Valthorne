package valthorne.ui;

/**
 * On-demand snapshot of the root's most recent UI text submission. Rendering
 * records primitive values without allocating snapshots. Scale describes physical
 * framebuffer pixels per logical unit for hinted text, or zero for fallback paths.
 *
 * @param mode renderer that handled the last submission
 * @param fallbackReason reason hinting was bypassed or no pixels were drawn
 * @param scaleX physical pixels per logical unit horizontally
 * @param scaleY physical pixels per logical unit vertically
 */
public record UITextDiagnostics(UITextRenderingMode mode, UITextFallbackReason fallbackReason, float scaleX, float scaleY) {}
