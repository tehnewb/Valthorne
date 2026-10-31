package valthorne.graphics.animation;

/**
 * One validated CPU-only sprite-sheet rectangle and its positive playback time.
 * Coordinates use the original image's top-left origin, before decoding flips.
 * Bounds against a particular image are checked by AnimationData.
 *
 * @param x left source pixel
 * @param y top source pixel
 * @param width positive source width
 * @param height positive source height
 * @param duration finite positive seconds at speed one
 */
public record AnimationFrameData(int x, int y, int width, int height, float duration) {
    /**
     * Rejects invalid source dimensions and non-progressing frame durations.
     *
     * @param x nonnegative left source pixel
     * @param y nonnegative top source pixel
     * @param width positive source width
     * @param height positive source height
     * @param duration finite positive duration in seconds
     */
    public AnimationFrameData {
        if (x < 0 || y < 0 || width < 1 || height < 1)
            throw new IllegalArgumentException("Frame coordinates must be nonnegative and dimensions positive");
        if (!Float.isFinite(duration) || duration <= 0)
            throw new IllegalArgumentException("Frame duration must be finite and positive");
    }
}
