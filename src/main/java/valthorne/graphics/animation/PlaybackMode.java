package valthorne.graphics.animation;

/**
 * Selects the direction policy used by Animation when advancing frames.
 * Forward and reverse traverse one direction; bidirectional changes direction at
 * endpoints. This enum does not determine whether playback repeats or stops;
 * looping and completion limits are separate animation settings.
 *
 * @author Albert Beaupre
 */
public enum PlaybackMode {
    /**
     * Indicates the playback mode for continuous forward progression.
     * Used to specify that playback or processing should continue in a forward
     * or normal direction without reversing or altering its course.
     */
    FORWARD,

    /**
     * Represents the playback mode where the process or media progresses
     * in the reverse direction. Used to indicate that playback or processing
     * should move backward.
     */
    REVERSE,

    /**
     * Represents the playback mode where the process or media can progress
     * in both forward and reverse directions. This mode allows switching
     * between directions as needed during playback or processing.
     */
    BIDIRECTIONAL,
}
