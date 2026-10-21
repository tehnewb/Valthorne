package valthorne.ui;

/**
 * Renderer and actual target antialiasing mode used by the root's most recent
 * text submission. Native modes are read back from Direct2D rather than inferred
 * from ClearType eligibility. An empty or fully clipped submission draws no text.
 */
public enum UITextRenderingMode {
    /**
     * No submission yet, or the last native submission was fully clipped.
     */
    NOT_DRAWN,
    /**
     * Direct2D target delegates antialiasing selection to its default settings.
     */
    DIRECTWRITE_DEFAULT,
    /**
     * Direct2D target reports native LCD subpixel antialiasing.
     */
    DIRECTWRITE_CLEARTYPE,
    /**
     * Direct2D target reports native grayscale antialiasing.
     */
    DIRECTWRITE_GRAYSCALE,
    /**
     * Direct2D target reports native aliased text.
     */
    DIRECTWRITE_ALIASED,
    /**
     * Existing scalable live-outline renderer handled the submission.
     */
    SLUG,
    /**
     * Existing NanoVG renderer handled the named-font submission.
     */
    NANOVG
}
