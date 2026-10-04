package valthorne.math.physics;

import com.github.stephengold.joltjni.Body;
import com.github.stephengold.joltjni.ContactManifold;
import com.github.stephengold.joltjni.SubShapeIdPair;
import com.github.stephengold.joltjni.Vec3;

import java.util.Arrays;
import java.util.Objects;

/**
 * Lazily owned contact capture/delivery system with bounded parallel arrays.
 * Native callbacks copy borrowed data, then the owner delivers a reusable event
 * view after simulation. Optional workers synchronize only buffer writes; the
 * single-threaded path avoids locking. A volatile capture flag publishes body
 * membership before worker callbacks. Listener arrays change only on mutation.
 *
 * <p>Jolt JNI 6.0.0 requires borrowed Body/ContactManifold/SubShapeIdPair wrappers
 * and constructs a normal vector when reading a manifold. The JVM may eliminate
 * these temporary Java allocations; this subsystem adds no queued event objects or boxed
 * body-ID lookups. Removed handles are retained until the following native step
 * drains cached contacts, including handles removed during application callbacks.
 * Event overflow or callback-copy failure is reported after returning from native
 * code, preventing exceptions from escaping through worker-thread JNI callbacks.</p>
 */
final class PhysicsContacts2D implements AutoCloseable {
    /**
     * Private ordinal lookup avoids allocating an enum array during delivery.
     */
    private static final ContactType2D[] CONTACT_TYPES = ContactType2D.values();

    private final PhysicsWorld2D world; // Owning world used for generation-checked live-handle lookup.
    private final boolean threaded; // Whether worker callbacks need synchronized buffer publication.
    private final PhysicsContactBridge2D bridge; // Owned native callback resource, detached before closing.
    private PhysicsContactListener2D[] listeners = new PhysicsContactListener2D[0]; // Owner-thread listener snapshot, replaced only on mutation.
    private volatile boolean listening; // Publishes capture activation and body membership to native worker callbacks.
    private final ContactEvent2D view = new ContactEvent2D(); // Reused read-only event delivered to game code.
    private final byte[] types; // Compact transition ordinals in callback arrival order.
    private final RigidBody2D[] firstBodies; // Original first body references for each event.
    private final RigidBody2D[] secondBodies; // Original second body references for each event.
    private final int[] firstSubShapes; // Native first subshape IDs for each event.
    private final int[] secondSubShapes; // Native second subshape IDs for each event.
    private final float[] normalX; // Copied world-normal horizontal projections.
    private final float[] normalY; // Copied world-normal vertical projections.
    private final float[] penetration; // Copied native penetration distances in meters.
    private int count; // Buffered event count, protected by this monitor when workers are enabled.
    private boolean overflow; // Whether an event was rejected by the fixed buffer capacity.
    private Throwable captureFailure; // First callback-copy failure, or null after successful capture.
    private RigidBody2D[] retired = new RigidBody2D[16]; // Removed handles retained across the next native step.
    private int retiredCount; // Occupied retired-handle prefix, mutated only by the owner outside native update.
    private int retiredBoundary; // Retired prefix safe to release after this step's native capture.

    /**
     * Allocates the optional fixed event storage before its native bridge.
     *
     * @param world owning world
     * @param capacity maximum events buffered per fixed step
     * @param threaded whether native worker threads are active
     */
    PhysicsContacts2D(PhysicsWorld2D world, int capacity, boolean threaded) {
        this.world = world;
        this.threaded = threaded;
        types = new byte[capacity];
        firstBodies = new RigidBody2D[capacity];
        secondBodies = new RigidBody2D[capacity];
        firstSubShapes = new int[capacity];
        secondSubShapes = new int[capacity];
        normalX = new float[capacity];
        normalY = new float[capacity];
        penetration = new float[capacity];
        bridge = new PhysicsContactBridge2D(this);
    }

    /**
     * Exposes the owned bridge only for installation into the native system.
     *
     * @return native callback resource
     */
    PhysicsContactBridge2D bridge() {
        return bridge;
    }

    /**
     * Adds a listener once by identity, allocating only the changed snapshot.
     *
     * @param listener nonnull owner-thread application callback
     */
    void add(PhysicsContactListener2D listener) {
        Objects.requireNonNull(listener, "listener");
        for (PhysicsContactListener2D existing : listeners) if (existing == listener) return;
        PhysicsContactListener2D[] changed = Arrays.copyOf(listeners, listeners.length + 1);
        changed[listeners.length] = listener;
        listeners = changed;
    }

    /**
     * Removes a listener by identity; an absent listener leaves storage unchanged.
     *
     * @param listener nonnull owner-thread callback to remove
     */
    void remove(PhysicsContactListener2D listener) {
        Objects.requireNonNull(listener, "listener");
        for (int i = 0; i < listeners.length; i++) {
            if (listeners[i] != listener) continue;
            PhysicsContactListener2D[] changed = new PhysicsContactListener2D[listeners.length - 1];
            System.arraycopy(listeners, 0, changed, 0, i);
            System.arraycopy(listeners, i + 1, changed, i, changed.length - i);
            listeners = changed;
            return;
        }
    }

    /**
     * Publishes callback activation and snapshots the removed-handle cleanup
     * boundary before native workers can start reading body membership.
     */
    void beginStep() {
        retiredBoundary = retiredCount;
        listening = listeners.length != 0;
    }

    /**
     * Keeps an original body handle available for native END callbacks after
     * its native index has been removed or reused. Growth occurs only on removal.
     *
     * @param body handle about to be removed from native membership
     */
    void retire(RigidBody2D body) {
        if (retiredCount == retired.length) retired = Arrays.copyOf(retired, retired.length * 2);
        retired[retiredCount++] = body;
    }

    /**
     * Resolves removed IDs without ever confusing a reused native generation.
     *
     * @param id complete native ID
     * @return matching live/retired handle, or null for an unobserved old body
     */
    private RigidBody2D resolve(int id) {
        RigidBody2D live = world.findBody(id);
        if (live != null) return live;
        for (int i = 0; i < retiredCount; i++) if (retired[i].id == id) return retired[i];
        return null;
    }

    /**
     * Copies a borrowed manifold on the native callback thread.
     *
     * @param type beginning or persisting contact
     * @param firstAddress first borrowed Body address
     * @param secondAddress second borrowed Body address
     * @param manifoldAddress borrowed ContactManifold address
     */
    void capture(ContactType2D type, long firstAddress, long secondAddress, long manifoldAddress) {
        if (!listening) return;
        try {
            RigidBody2D first = world.findBody(new Body(firstAddress).getId());
            RigidBody2D second = world.findBody(new Body(secondAddress).getId());
            ContactManifold manifold = new ContactManifold(manifoldAddress);
            Vec3 normal = manifold.getWorldSpaceNormal();
            record(type, first, second, manifold.getSubShapeId1(), manifold.getSubShapeId2(), normal.getX(), normal.getY(), manifold.getPenetrationDepth());
        } catch (RuntimeException | Error failure) {
            recordFailure(failure);
        }
    }

    /**
     * Copies removed-pair identity while the native borrowed pair is valid.
     *
     * @param pairAddress borrowed SubShapeIdPair address
     */
    void captureRemoved(long pairAddress) {
        if (!listening) return;
        try {
            SubShapeIdPair pair = new SubShapeIdPair(pairAddress);
            record(ContactType2D.END, resolve(pair.getBody1Id()), resolve(pair.getBody2Id()), pair.getSubShapeId1(), pair.getSubShapeId2(), 0, 0, 0);
        } catch (RuntimeException | Error failure) {
            recordFailure(failure);
        }
    }

    /**
     * Serializes event publication only when callbacks can actually be concurrent.
     *
     * @param type transition kind
     * @param first first original handle
     * @param second second original handle
     * @param firstSubShape first subshape ID
     * @param secondSubShape second subshape ID
     * @param x horizontal normal projection
     * @param y vertical normal projection
     * @param depth signed penetration in meters
     */
    private void record(ContactType2D type, RigidBody2D first, RigidBody2D second, int firstSubShape, int secondSubShape, float x, float y, float depth) {
        if (first == null || second == null) return;
        if (threaded) {
            synchronized (this) {
                append(type, first, second, firstSubShape, secondSubShape, x, y, depth);
            }
        } else {
            append(type, first, second, firstSubShape, secondSubShape, x, y, depth);
        }
    }

    /**
     * Writes one complete event with exclusive access to the buffer.
     *
     * @param type transition kind
     * @param first first original handle
     * @param second second original handle
     * @param firstSubShape first subshape ID
     * @param secondSubShape second subshape ID
     * @param x horizontal normal projection
     * @param y vertical normal projection
     * @param depth signed penetration in meters
     */
    private void append(ContactType2D type, RigidBody2D first, RigidBody2D second, int firstSubShape, int secondSubShape, float x, float y, float depth) {
        if (count == types.length) {
            overflow = true;
            return;
        }
        types[count] = (byte) type.ordinal();
        firstBodies[count] = first;
        secondBodies[count] = second;
        firstSubShapes[count] = firstSubShape;
        secondSubShapes[count] = secondSubShape;
        normalX[count] = x;
        normalY[count] = y;
        penetration[count] = depth;
        count++;
    }

    /**
     * Retains a callback-copy failure instead of throwing it through native JNI.
     *
     * @param failure first failure to report after the native update
     */
    private void recordFailure(Throwable failure) {
        if (threaded) {
            synchronized (this) {
                if (captureFailure == null) captureFailure = failure;
            }
        } else if (captureFailure == null) {
            captureFailure = failure;
        }
    }

    /**
     * Acquires worker writes after native stepping and rejects an incomplete
     * event stream before any application callback observes a partial batch.
     *
     * @throws IllegalStateException if buffering overflowed or native data could not be copied
     */
    void checkCapture() {
        if (threaded) {
            synchronized (this) {
                requireComplete();
            }
        } else {
            requireComplete();
        }
    }

    /**
     * Validates the event stream with exclusive buffer visibility.
     *
     * @throws IllegalStateException if capture was incomplete
     */
    private void requireComplete() {
        if (captureFailure != null) throw new IllegalStateException("Unable to capture native contact data", captureFailure);
        if (overflow) throw new IllegalStateException("Physics contact event capacity exhausted");
    }

    /**
     * Delivers one batch to a stable listener snapshot after native state is
     * committed. Listener mutations affect the next fixed step. Failures discard
     * undelivered events while retaining bodies removed during this dispatch.
     */
    void dispatch() {
        PhysicsContactListener2D[] snapshot = listeners;
        try {
            for (int i = 0; i < count; i++) {
                view.set(CONTACT_TYPES[types[i]], firstBodies[i], secondBodies[i], firstSubShapes[i], secondSubShapes[i], normalX[i], normalY[i], penetration[i]);
                for (PhysicsContactListener2D listener : snapshot) listener.onContact(view);
            }
        } finally {
            discard();
        }
    }

    /**
     * Releases buffered references and the retired prefix consumed by this
     * native step. Later removals survive until their next native END callbacks.
     */
    void discard() {
        Arrays.fill(firstBodies, 0, count, null);
        Arrays.fill(secondBodies, 0, count, null);
        count = 0;
        int remaining = retiredCount - retiredBoundary;
        System.arraycopy(retired, retiredBoundary, retired, 0, remaining);
        Arrays.fill(retired, remaining, retiredCount, null);
        retiredCount = remaining;
        retiredBoundary = 0;
        view.set(null, null, null, 0, 0, 0, 0, 0);
    }

    @Override
    public void close() {
        listening = false;
        listeners = new PhysicsContactListener2D[0];
        retiredBoundary = retiredCount;
        discard();
        bridge.close();
    }
}
