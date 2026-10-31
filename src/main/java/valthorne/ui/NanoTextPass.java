package valthorne.ui;

/**
 * Paint stage used to keep every line's shadow behind all outlines and fills.
 * Stages reuse existing shaped layouts and carry no per-draw mutable state.
 */
public enum NanoTextPass {
    /**
     * Paints the complete line: shadow, outline and fill.
     */
    ALL,
    /**
     * Paints only the optional shadow.
     */
    SHADOW,
    /**
     * Paints only the optional outline.
     */
    OUTLINE,
    /**
     * Paints only the text fill.
     */
    FILL
}
