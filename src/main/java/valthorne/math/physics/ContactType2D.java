package valthorne.math.physics;

/**
 * Native contact-cache transition for one body/subshape pair. Removed events
 * also occur when a body sleeps, so END describes removal from Jolt's active
 * contact cache and does not guarantee geometric separation.
 */
public enum ContactType2D {
    /**
     * A body/subshape pair entered the native contact cache.
     */
    BEGIN,

    /**
     * A previously cached pair remains in contact during this collision update.
     */
    PERSIST,

    /**
     * A cached pair was removed, including separation, sleeping or body removal.
     */
    END
}
