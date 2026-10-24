package valthorne.collections.queue;

import java.util.Arrays;

/**
 * Sequential short index generator with a reusable-index buffer. Callers return
 * indices with push; buffered values are reused in last-in, first-out
 * order before sequential generation resumes. The class does not track live
 * allocations or reject duplicate returns, so uniqueness depends on caller use.
 *
 * <p>Arithmetic follows primitive overflow rules. Buffered indices use a simple
 * stack; no circular indexing or membership tracking is required. Operations
 * are unsynchronized.</p>
 *
 * @author Albert Beaupre
 */
public class ShortUUIDQueue {

    private short[] queue = new short[16]; // Returned indices awaiting reuse, valid before enqueue.
    private short dequeue; // Next sequential value when the reuse buffer is empty.
    private int enqueue; // Number of buffered values and insertion position.

    /**
     * Creates an empty reuse buffer and starts sequential generation at zero.
     * Sixteen returned-index slots are initially allocated.
     */
    public ShortUUIDQueue() {
        this(0);
    }

    /**
     * Creates an empty reuse buffer with a caller-selected generation start.
     * The int is narrowed to short without a range check.
     *
     * @param startingValue first sequential index when no returned indices exist
     */
    public ShortUUIDQueue(int startingValue) {
        this.dequeue = (short) startingValue;
    }

    /**
     * Reuses the newest returned index, or issues the next sequential value when
     * no returns are buffered. Reuse works at every capacity boundary; sequential
     * arithmetic retains the primitive type's overflow behavior.
     *
     * @return buffered value or next sequential value
     */
    public short pop() {
        if (enqueue == 0) return dequeue++;
        return queue[--enqueue];
    }

    /**
     * Returns an index for later reuse unless it is greater than the next sequential
     * value. Equality is accepted and advances that counter; negatives and duplicate
     * returns are not rejected. Full storage grows by doubling before insertion.
     *
     * @param index value to append to the reuse buffer
     */
    public void push(short index) {
        if (index > dequeue)
            return;

        if (enqueue == queue.length) queue = Arrays.copyOf(queue, queue.length * 2);

        if (index == dequeue)
            dequeue = (short) (index + 1);

        queue[enqueue++] = index;
    }

    /**
     * Copies the occupied reuse-buffer prefix in insertion order. Zero values and
     * duplicates are preserved; the method neither filters entries nor alters the
     * buffer. Modifying the result does not change subsequent allocation.
     *
     * @return independent array of currently buffered values
     */
    public short[] getCompactQueue() {
        return Arrays.copyOf(queue, enqueue);
    }

}
