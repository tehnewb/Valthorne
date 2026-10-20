package valthorne.math.physics;

/**
 * Read-only view of one buffered native contact transition. The dispatcher
 * reuses this object for every delivery; retain values or call {@link #copy()}
 * when a durable event is needed. Copied events own no native memory.
 *
 * <p>The normal projects Jolt's world-space normal from first body toward second
 * onto XY and therefore need not have unit length. Penetration may be negative
 * for speculative contacts. END events have zero normal and penetration. Body
 * references identify the original handles even if a body was destroyed or its
 * native slot reused; destroyed handles support metadata but no native access.
 * Sensor contacts use the same transitions without applying collision response.</p>
 */
public final class ContactEvent2D {
    private ContactType2D type; // Native transition, assigned before delivery.
    private RigidBody2D first; // Original first body handle, assigned before delivery.
    private RigidBody2D second; // Original second body handle, assigned before delivery.
    private int firstSubShape; // Native subshape identifier for the first body.
    private int secondSubShape; // Native subshape identifier for the second body.
    private float normalX; // Horizontal projection of the first-to-second world normal.
    private float normalY; // Vertical projection of the first-to-second world normal.
    private float penetration; // Native penetration depth in meters, possibly negative.

    /**
     * Creates an uninitialized dispatcher view or snapshot destination.
     */
    ContactEvent2D() {
    }

    /**
     * Returns this event's contact-cache transition.
     *
     * @return beginning, persisting, or ending native contact
     */
    public ContactType2D getType() {
        return type;
    }

    /**
     * Returns the original first handle, including after its destruction.
     *
     * @return first body involved in the contact
     */
    public RigidBody2D getFirstBody() {
        return first;
    }

    /**
     * Returns the original second handle, including after its destruction.
     *
     * @return second body involved in the contact
     */
    public RigidBody2D getSecondBody() {
        return second;
    }

    /**
     * Returns the native first subshape identifier for pair matching.
     *
     * @return first subshape ID
     */
    public int getFirstSubShape() {
        return firstSubShape;
    }

    /**
     * Returns the native second subshape identifier for pair matching.
     *
     * @return second subshape ID
     */
    public int getSecondSubShape() {
        return secondSubShape;
    }

    /**
     * Returns the horizontal first-to-second normal projection.
     *
     * @return normal X, or zero for END
     */
    public float getNormalX() {
        return normalX;
    }

    /**
     * Returns the vertical first-to-second normal projection.
     *
     * @return normal Y, or zero for END
     */
    public float getNormalY() {
        return normalY;
    }

    /**
     * Returns the native penetration distance.
     *
     * @return meters of penetration, possibly negative, or zero for END
     */
    public float getPenetrationDepth() {
        return penetration;
    }

    /**
     * Allocates an independent read-only snapshot for retention after delivery.
     *
     * @return durable snapshot of the current event values
     */
    public ContactEvent2D copy() {
        ContactEvent2D copy = new ContactEvent2D();
        copy.set(type, first, second, firstSubShape, secondSubShape, normalX, normalY, penetration);
        return copy;
    }

    /**
     * Replaces the borrowed view with copied values from one native transition.
     *
     * @param type transition kind
     * @param first original first body
     * @param second original second body
     * @param firstSubShape native first subshape ID
     * @param secondSubShape native second subshape ID
     * @param normalX horizontal normal projection
     * @param normalY vertical normal projection
     * @param penetration signed penetration in meters
     */
    void set(ContactType2D type, RigidBody2D first, RigidBody2D second, int firstSubShape, int secondSubShape, float normalX, float normalY, float penetration) {
        this.type = type;
        this.first = first;
        this.second = second;
        this.firstSubShape = firstSubShape;
        this.secondSubShape = secondSubShape;
        this.normalX = normalX;
        this.normalY = normalY;
        this.penetration = penetration;
    }
}
