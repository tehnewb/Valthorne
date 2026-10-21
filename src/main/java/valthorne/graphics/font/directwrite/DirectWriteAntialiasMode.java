package valthorne.graphics.font.directwrite;

/**
 * Actual antialiasing mode reported by a configured native Direct2D text target.
 * ClearType requires an eligible opaque destination and Windows font smoothing
 * settings that allow it; transparent targets use grayscale or aliased rendering.
 */
public enum DirectWriteAntialiasMode {
    /**
     * Native target default; additional settings determine the rendering behavior.
     */
    DEFAULT,
    /**
     * LCD subpixel antialiasing on an eligible opaque destination.
     */
    CLEARTYPE,
    /**
     * Scalar grayscale coverage suitable for transparent destinations.
     */
    GRAYSCALE,
    /**
     * Unsmooth edges when native font smoothing is disabled.
     */
    ALIASED
}
