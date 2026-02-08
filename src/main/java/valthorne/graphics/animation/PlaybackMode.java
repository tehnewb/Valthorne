package valthorne.graphics.animation;

/**
 * Represents the playback modes for a media or process in the system.
 * This enum defines the possible directions a playback can take.
 *
 * @author Albert Beaupre
 * @since February 8th, 2026
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
