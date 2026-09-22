package valthorne.tick;

import java.util.Arrays;
import java.util.Objects;

/**
 * An ordered, single-threaded collection of ticks driven by one frame update.
 *
 * <p>Register ticks once, start them explicitly, and call {@link #update(float)}
 * from the owning application's update loop. Each tick present at update entry
 * receives the same delta, in registration order, unless it is removed before
 * its turn. Register a dependency before the ticks that read its state.</p>
 *
 * <h2>Lifecycle and ownership</h2>
 * <p>Registration does not start a tick. Removal and clearing do not stop ticks;
 * they only remove this manager's references. Paused ticks remain registered
 * and receive updates so continuation conditions and schedules can advance.
 * Stopped ticks also remain registered and can later be restarted. Call
 * {@link #removeStopped()} explicitly to discard stopped ticks that are no longer
 * needed. Do not separately update ticks already updated by this manager.</p>
 *
 * <h2>Changes during callbacks</h2>
 * <p>Callbacks may add, remove, or clear registrations. New registrations first
 * receive an update on the next manager update. Removing a tick before its turn
 * skips it immediately. Removing and re-adding the same tick places it at the end
 * as a new registration. Removing the currently executing tick does not interrupt
 * that tick's own update; call {@link Tick#end()} as well when required.</p>
 *
 * <h2>Storage and costs</h2>
 * <p>One reusable reference array stores the ticks. Normal updates allocate no
 * manager-owned objects and make one linear traversal; removals during callbacks
 * trigger one additional stable compaction after the traversal. No iterators,
 * per-tick wrapper objects, per-update snapshots, worker threads, or locks are
 * created. Allocation can occur when construction or registration grows storage,
 * when explicitly trimming it, or in user callbacks and exceptional paths.</p>
 *
 * <p>Membership is based on reference identity, not {@code equals}. Membership
 * searches and individual removals are linear; this deliberately avoids a
 * second hash-based index. Pre-size the array for a known population. For heavy
 * registration churn at very large populations, measure these linear searches
 * separately from update throughput.</p>
 *
 * <h2>Safety</h2>
 * <p>This class is not thread-safe. Use the manager and its ticks on one owning
 * thread. Recursive manager updates are rejected. A tick exception propagates
 * and aborts the remaining updates in that call, but completed mutations remain
 * applied and storage is compacted in a {@code finally} block.</p>
 *
 * <pre>{@code
 * TickManager ticks = new TickManager(64);
 * Tick primary = new Tick().delay(1f).repeat(5);
 * Tick follower = new Tick().delay(0.25f)
 *         .runIf(t -> primary.getIterations() >= 2)
 *         .endIf(t -> primary.isStopped());
 * ticks.add(primary);
 * ticks.add(follower);
 * primary.start();
 * follower.start();
 * // Inside the game loop:
 * ticks.update(delta);
 * }</pre>
 *
 * @author Albert Beaupre
 * @since September 22nd, 2026
 */
public final class TickManager {

    /**
     * Shared storage for managers with zero capacity.
     */
    private static final Tick[] EMPTY = new Tick[0];
    private Tick[] ticks; // Tick references in registration order, with temporary holes.
    private int size; // Number of registered ticks, excluding temporary holes.
    private int used; // Occupied array prefix, including temporary holes.
    private int firstRemoved = -1; // Earliest temporary hole, or -1 when dense.
    private boolean updating; // Whether this manager is currently traversing ticks.

    /**
     * Creates an empty manager with capacity for sixteen tick references.
     */
    public TickManager() {
        this(16);
    }

    /**
     * Creates an empty manager with a chosen initial capacity.
     *
     * @param initialCapacity nonnegative number of reference slots to reserve
     * @throws IllegalArgumentException if the capacity is negative
     */
    public TickManager(int initialCapacity) {
        if (initialCapacity < 0) throw new IllegalArgumentException("initialCapacity must be nonnegative.");
        ticks = initialCapacity == 0 ? EMPTY : new Tick[initialCapacity];
    }

    /**
     * Registers a tick without changing its lifecycle state.
     *
     * <p>An identical reference already registered is ignored. During an update,
     * the new entry is appended beyond that update's original boundary, even
     * when an earlier slot has been removed. Consequently, new ticks cannot
     * receive time from a frame that was already being processed.</p>
     *
     * @param tick non-null tick to register
     * @return true if registered, or false if the same reference was already present
     * @throws NullPointerException if tick is null
     */
    public boolean add(Tick tick) {
        Objects.requireNonNull(tick, "tick");
        if (indexOf(tick) >= 0) return false;
        if (used == Integer.MAX_VALUE) throw new OutOfMemoryError("Too many tick registrations.");
        if (used == ticks.length) ensureCapacity(used + 1);
        ticks[used++] = tick;
        size++;
        return true;
    }

    /**
     * Unregisters a tick without stopping it.
     *
     * <p>Removal before a tick's turn prevents that manager update from reaching
     * it. Removal of the currently executing tick takes effect for subsequent
     * manager updates but does not cancel its current callback or catch-up work.
     * Surviving registrations keep their relative order.</p>
     *
     * @param tick reference to remove; null is treated as absent
     * @return true if the reference was registered and removed
     */
    public boolean remove(Tick tick) {
        int index = indexOf(tick);
        if (index < 0) return false;
        size--;
        if (updating) {
            ticks[index] = null;
            if (firstRemoved < 0 || index < firstRemoved) firstRemoved = index;
        } else {
            int tail = --used - index;
            if (tail != 0) System.arraycopy(ticks, index + 1, ticks, index, tail);
            ticks[used] = null;
        }
        return true;
    }

    /**
     * Advances the ticks registered at entry in their registration order.
     *
     * <p>One call to {@link Tick#update(float)} is made for each surviving entry,
     * including paused and stopped ticks. The tick itself decides whether any
     * callbacks or lifecycle actions are due. Newly added entries wait until
     * the next call. There is no implicit starting, stopping, or removal.</p>
     *
     * @param delta finite, nonnegative frame time in seconds
     * @throws IllegalArgumentException if delta is negative, NaN, or infinite
     * @throws IllegalStateException    if called recursively on this manager
     */
    public void update(float delta) {
        if (!Float.isFinite(delta) || delta < 0f)
            throw new IllegalArgumentException("delta must be finite and nonnegative.");
        if (updating) throw new IllegalStateException("TickManager.update cannot be called recursively.");
        if (size == 0) return;

        final int limit = used;
        updating = true;
        try {
            for (int i = 0; i < limit; i++) {
                // Do not cache the array across user code: a callback may grow it,
                // then remove a later entry from the replacement array.
                Tick tick = ticks[i];
                if (tick != null) tick.update(delta);
            }
        } finally {
            updating = false;
            if (firstRemoved >= 0) compact();
        }
    }

    /**
     * Removes all registrations without changing any tick's lifecycle state.
     *
     * <p>Capacity is retained for reuse and references are cleared immediately.
     * During an update, not-yet-visited entries are skipped and new registrations
     * still wait until the next update. An executing tick is not interrupted.</p>
     */
    public void clear() {
        if (size == 0) return;
        Arrays.fill(ticks, 0, used, null);
        size = 0;
        if (updating) {
            // Preserve the traversal boundary until the outer update finishes.
            firstRemoved = 0;
        } else {
            used = 0;
            firstRemoved = -1;
        }
    }

    /**
     * Removes every currently stopped tick in one scan.
     *
     * <p>This also removes ticks that have been registered but never started.
     * Paused ticks are retained. Invoke this only when stopped ticks do not need
     * to remain registered for later starts or restarts. No listeners are called.</p>
     *
     * @return number of registrations removed
     */
    public int removeStopped() {
        int removed = 0;
        try {
            for (int i = 0; i < used; i++) {
                Tick tick = ticks[i];
                if (tick != null && tick.isStopped()) {
                    ticks[i] = null;
                    if (firstRemoved < 0 || i < firstRemoved) firstRemoved = i;
                    size--;
                    removed++;
                }
            }
            return removed;
        } finally {
            // Also restore dense storage if an overridden state getter throws.
            if (!updating && firstRemoved >= 0) compact();
        }
    }

    /**
     * Tests membership using reference identity in a linear scan.
     *
     * @param tick reference to find; null is treated as absent
     * @return true if the exact reference is registered
     */
    public boolean contains(Tick tick) {
        return indexOf(tick) >= 0;
    }

    /**
     * Returns a registered tick by its current logical position.
     *
     * <p>Lookup is constant-time while storage is dense. During a callback after
     * removals, lookup at or beyond the first hole scans surviving entries.
     * New registrations are visible here immediately, before their first update.</p>
     *
     * @param index zero-based index among currently registered ticks
     * @return registered tick at that position
     * @throws IndexOutOfBoundsException if index is outside zero through size minus one
     */
    public Tick get(int index) {
        Objects.checkIndex(index, size);
        if (firstRemoved < 0 || index < firstRemoved) return ticks[index];
        int logical = firstRemoved;
        for (int i = firstRemoved; i < used; i++) {
            Tick tick = ticks[i];
            if (tick != null && logical++ == index) return tick;
        }
        throw new AssertionError("TickManager storage invariant violated.");
    }

    /**
     * Returns the current registration count, including deferred additions.
     *
     * @return number of ticks currently registered, regardless of lifecycle state
     */
    public int size() {
        return size;
    }

    /**
     * Returns whether no ticks are registered.
     *
     * @return true when the registration count is zero
     */
    public boolean isEmpty() {
        return size == 0;
    }

    /**
     * Returns the current reference-array capacity.
     *
     * @return number of reserved reference slots
     */
    public int getCapacity() {
        return ticks.length;
    }

    /**
     * Reserves at least the requested number of reference slots.
     *
     * <p>Existing entries are retained. Growth uses approximately one-and-a-half
     * times the current capacity unless more is requested. During updates,
     * removed slots are not reused until final compaction; those temporary holes
     * still occupy capacity. This method never shrinks the array.</p>
     *
     * @param minimumCapacity nonnegative minimum backing-array length
     * @throws IllegalArgumentException if minimumCapacity is negative
     */
    public void ensureCapacity(int minimumCapacity) {
        if (minimumCapacity < 0) throw new IllegalArgumentException("minimumCapacity must be nonnegative.");
        if (minimumCapacity <= ticks.length) return;
        long preferred = Math.max((long) minimumCapacity, (long) ticks.length + (ticks.length >>> 1) + 1L);
        int capacity = (int) Math.min(preferred, Integer.MAX_VALUE - 8L);
        if (capacity < minimumCapacity) capacity = minimumCapacity;
        ticks = Arrays.copyOf(ticks, capacity);
    }

    /**
     * Shrinks storage to exactly the current registration count.
     *
     * <p>This may allocate and copy an array; use it at an explicit cleanup point,
     * not every frame. After {@link #clear()}, it releases the backing array in
     * favor of the shared empty array. Calling during an update is forbidden
     * because traversal boundaries must remain stable.</p>
     *
     * @throws IllegalStateException if called during this manager's update
     */
    public void trimToSize() {
        if (updating) throw new IllegalStateException("Cannot trim TickManager during an update.");
        if (ticks.length != size) ticks = size == 0 ? EMPTY : Arrays.copyOf(ticks, size);
    }

    /**
     * Finds a reference in the occupied array prefix without invoking user code.
     *
     * @param tick reference to find, or null
     * @return physical index, or minus one if absent
     */
    private int indexOf(Tick tick) {
        if (tick == null) return -1;
        for (int i = 0; i < used; i++) {
            if (ticks[i] == tick) return i;
        }
        return -1;
    }

    /**
     * Compacts holes in place, preserves order, and clears vacated references.
     */
    private void compact() {
        int write = firstRemoved;
        for (int read = write + 1; read < used; read++) {
            Tick tick = ticks[read];
            if (tick != null) ticks[write++] = tick;
        }
        Arrays.fill(ticks, write, used, null);
        used = write;
        firstRemoved = -1;
    }
}
