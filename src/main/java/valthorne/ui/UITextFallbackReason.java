package valthorne.ui;

/**
 * Reason a text submission used a scalable/NanoVG fallback or submitted no pixels.
 * NONE accompanies supported native submissions. This describes the last text
 * submission, not every control in the root or future eligibility.
 */
public enum UITextFallbackReason {
    /**
     * Native rendering handled the submission, or no submission has occurred yet.
     */
    NONE,
    /**
     * The control has no root that can own native resources.
     */
    DETACHED,
    /**
     * The platform does not support this Windows backend.
     */
    NON_WINDOWS,
    /**
     * The root explicitly disabled Windows text rendering.
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
     * The active projection cannot preserve native axis-aligned bitmap pixels.
     */
    UNSUPPORTED_PROJECTION,
    /**
     * Physical scale is nonfinite or exceeds the supported raster range.
     */
    UNSUPPORTED_SCALE,
    /**
     * The active physical viewport has no drawable extent.
     */
    EMPTY_VIEWPORT,
    /**
     * The complete native raster lies outside the physical viewport.
     */
    OUTSIDE_VIEWPORT,
    /**
     * The named NanoVG font has no registered encoded source for native shaping.
     */
    NANOVG_ONLY_FONT,
    /**
     * The shared UI render context is unavailable for a Nano text submission.
     */
    NO_RENDER_CONTEXT
}
