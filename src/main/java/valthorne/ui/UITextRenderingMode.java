package valthorne.ui;

/**
 * Renderer and actual target antialiasing mode used by the root's most recent
 * text submission. An empty or fully clipped submission draws no text.
 */
public enum UITextRenderingMode {
    /**
     * No submission yet, or the last submission was fully clipped.
     */
    NOT_DRAWN,
    /**
     * Existing scalable live-outline renderer handled the submission.
     */
    SLUG,
    /**
     * Slug GPU curves use FreeType-hinted geometry and HarfBuzz positioning at the physical UI size.
     */
    SLUG_HINTED,
    /**
     * Existing NanoVG renderer handled the named-font submission.
     */
    NANOVG
}
