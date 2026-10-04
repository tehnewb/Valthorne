package valthorne.math.physics;

/**
 * Receives buffered native contact transitions on the world's owner thread
 * after a successful fixed step and pose capture. Body/joint mutations and
 * queries are permitted; recursive stepping, clear, and world close are rejected.
 * The event is a reused read-only view: call {@link ContactEvent2D#copy()} to
 * retain a snapshot. Delivery order across independent body pairs is unspecified.
 */
@FunctionalInterface
public interface PhysicsContactListener2D {
    /**
     * Handles one native contact-cache transition. A thrown exception propagates
     * to the stepping caller and discards the rest of that step's event batch;
     * the successful native step and frame time remain committed.
     *
     * @param event borrowed event view, valid only for the duration of this callback
     */
    void onContact(ContactEvent2D event);
}
