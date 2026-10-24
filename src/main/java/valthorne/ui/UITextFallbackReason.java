package valthorne.ui;

/**
 * Reason a text submission used a scalable/NanoVG fallback or submitted no pixels.
 * NONE accompanies supported hinted submissions. This describes the last text
 * submission, not every control in the root or future eligibility.
 */
public enum UITextFallbackReason {
    /**
     * Hinted rendering handled the submission, or no submission has occurred yet.
     */
    NONE,
    /**
     * The control has no root that can own hinted resources.
     */
    DETACHED,
    /**
     * The root explicitly disabled Slug hinting.
     */
    DISABLED,
    /**
     * Camera zoom requires live scalable outlines.
     */
    CAMERA_ZOOM,
    /**
     * A control or ancestor is rotated.
     */
    ROTATION,
    /**
     * The active projection cannot preserve axis-aligned hinted outlines.
     */
    UNSUPPORTED_PROJECTION,
    /**
     * Physical scale is nonfinite or exceeds the supported hinting range.
     */
    UNSUPPORTED_SCALE,
    /**
     * The active physical viewport has no drawable extent.
     */
    EMPTY_VIEWPORT,
    /**
     * The named NanoVG font has no registered encoded source for portable shaping.
     */
    NANOVG_ONLY_FONT,
    /**
     * The shared UI render context is unavailable for a Nano text submission.
     */
    NO_RENDER_CONTEXT
}
