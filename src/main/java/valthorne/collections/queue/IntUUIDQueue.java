package valthorne.collections.queue;

import java.util.Arrays;

/**
 * Sequential int index generator with a reusable-index buffer. Callers return
 * indices with push; buffered values are normally reused in last-in, first-out
 * order before sequential generation resumes. The class does not track live
 * allocations or reject duplicate returns, so uniqueness depends on caller use.
 *
 * <p>Arithmetic follows primitive overflow rules. The current pop calculation
 * also rejects a completely full buffer with an array-index exception; it does
 * not implement a general circular queue. Operations are unsynchronized.</p>
 *
 * @author Albert Beaupre
 */
public class IntUUIDQueue {

    // The array used to store the queue of indexes. The capacity of the queue can expand as needed.
    private int[] queue = new int[16]; // Returned indices awaiting reuse, valid before enqueue.
    // Next sequential value issued when no returned IDs are available.
    private int dequeue; // Next sequential value when the reuse buffer is empty.
    // The current position for enqueueing a new index. This also represents the number of elements in the queue.
    private int enqueue; // Number of buffered values and insertion position.

    /**
     * Creates an empty reuse buffer and starts sequential generation at zero.
     * Sixteen returned-index slots are initially allocated.
     */
    public IntUUIDQueue() {
        this(0);
    }

    /**
     * Creates an empty reuse buffer with a caller-selected generation start.
     * Negative starts are accepted; overflow is not checked.
     *
     * @param startingValue first sequential index when no returned indices exist
     */
    public IntUUIDQueue(int startingValue) {
        this.dequeue = startingValue;
    }

    /**
     * Reuses the newest buffered index when the buffer is partially occupied;
     * otherwise generates and increments the next sequential value. Arithmetic
     * wraps at the primitive limit. At full buffer capacity, the modulo-based
     * slot calculation produces minus one before membership is decremented.
     *
     * @return buffered value or next sequential value
     * @throws ArrayIndexOutOfBoundsException if the reuse buffer is exactly full
     */
    public int pop() {
        if (enqueue > 0) {
            // Locate the most recently returned ID using the current modulo-based index calculation.
            int index = (enqueue % this.queue.length) - 1;
            int oldIndex = this.queue[index];
            // Clear the consumed primitive slot and reduce the number of queued returns.
            this.queue[--enqueue] = 0;
            return oldIndex;
        } else {
            // If the queue is empty, generate and return a new sequential index, incrementing the dequeue counter.
            return dequeue++;
        }
    }

    /**
     * Returns an index for later reuse unless it is greater than the next sequential
     * value. Equality is accepted and advances that counter; negatives and duplicate
     * returns are not rejected. Full storage grows by doubling before insertion.
     *
     * @param index value to append to the reuse buffer
     */
    public void push(int index) {
        // Ignore indexes that are not valid for reuse (i.e., greater than the current dequeue index).
        if (index > dequeue)
            return;

        // If the queue is full, double its size and copy the existing elements.
        if (enqueue >= queue.length) {
            int[] newArray = new int[queue.length * 2];
            System.arraycopy(queue, 0, newArray, 0, queue.length);
            queue = newArray;
        }

        // Adjust the dequeue index if the pushed index is the new highest index, ensuring sequential order.
        if (index >= dequeue)
            dequeue = index + 1;

        // Add the index to the queue and increment the enqueue index, effectively adding the index to the queue.
        queue[enqueue++] = index;
    }

    /**
     * Copies the occupied reuse-buffer prefix in insertion order. Zero values and
     * duplicates are preserved; the method neither filters entries nor alters the
     * buffer. Modifying the result does not change subsequent allocation.
     *
     * @return independent array of currently buffered values
     */
    public int[] getCompactQueue() {
        return Arrays.copyOf(queue, enqueue);
    }

}
