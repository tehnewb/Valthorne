package valthorne.math.physics;

import com.github.stephengold.joltjni.CustomContactListener;

/**
 * Native listener that forwards borrowed contact addresses only to the world's
 * capture subsystem. Never invokes application code while native body locks are
 * held. Inherits Jolt's default acceptance policy for all otherwise valid pairs.
 */
final class PhysicsContactBridge2D extends CustomContactListener {
    private final PhysicsContacts2D contacts; // Owning buffer that immediately copies borrowed callback data.

    /**
     * Allocates a native callback bridge for a world's contact buffer.
     *
     * @param contacts owning capture and delivery subsystem
     */
    PhysicsContactBridge2D(PhysicsContacts2D contacts) {
        this.contacts = contacts;
    }

    @Override
    public void onContactAdded(long first, long second, long manifold, long settings) {
        contacts.capture(ContactType2D.BEGIN, first, second, manifold);
    }

    @Override
    public void onContactPersisted(long first, long second, long manifold, long settings) {
        contacts.capture(ContactType2D.PERSIST, first, second, manifold);
    }

    @Override
    public void onContactRemoved(long pair) {
        contacts.captureRemoved(pair);
    }
}
