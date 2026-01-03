package valthorne.collections.queue;

import java.util.Arrays;

/**
 * A class that manages a unique index queue, allowing for the retrieval of unused
 * and reinsertion of used indices. This class is designed to manage a sequence of unique
 * long indexes efficiently, reusing them when possible. It provides methods to pop an
 * index from the queue and push an index back into it, automatically handling the queue's
 * capacity and ensures uniqueness and sequential order of indexes.
 *
 * @author Albert Beaupre
 * @since May 1st, 2024
 */
public class LongUUIDQueue {

    // The array used to store the queue of indexes. The capacity of the queue can expand as needed.
    private long[] queue = new long[16];
    // The index to be dequeued next if the queue is empty. This ensures unique and sequential index generation.
    private long dequeue;
    // The current position for enqueueing a new index. This also represents the number of elements in the queue.
    private int enqueue;

    /**
     * Constructs a new LongUUIDQueue object with the default starting value of zero.
     * This constructor initializes the queue's dequeue index to 0, allowing sequential
     * generation of unique integer indices starting from zero.
     */
    public LongUUIDQueue() {
        this(0);
    }

    /**
     * Constructs a new LongUUIDQueue object with the specified starting value.
     * This starting value determines the initial value for generating unique indices.
     *
     * @param startingValue The initial value to set for the queue's dequeue index.
     */
    public LongUUIDQueue(long startingValue) {
        this.dequeue = startingValue;
    }

    /**
     * Pops an index from the queue, returning a unique long index.
     * If the queue is empty, it generates a new index sequentially.
     * This method ensures that indexes are reused when possible, and new indexes are generated only when necessary.
     *
     * @return The next available unique index. If the queue is not empty, it returns and removes the oldest index in the queue.
     * If the queue is empty, it generates and returns a new sequential index.
     */
    public long pop() {
        if (enqueue > 0) {
            // Calculate the index within the queue array to pop, taking into account the circular nature of the queue.
            int index = (enqueue % this.queue.length) - 1;
            long oldIndex = this.queue[index];
            // Clear the old index to prevent memory leaks and decrement the enqueue index to remove the element from the queue.
            this.queue[--enqueue] = 0;
            return oldIndex;
        } else {
            // If the queue is empty, generate and return a new sequential index, incrementing the dequeue counter.
            return dequeue++;
        }
    }

    /**
     * Pushes a given index back into the queue if it is valid (not greater than the current dequeue index).
     * This method allows for the reuse of indexes by adding them back into the queue.
     * It also ensures that the queue's capacity is dynamically adjusted to accommodate more indexes if needed.
     *
     * @param index The index to be pushed back into the queue.
     */
    public void push(long index) {
        // Ignore indexes that are not valid for reuse (i.e., greater than the current dequeue index).
        if (index > dequeue)
            return;

        // If the queue is full, double its size and copy the existing elements.
        if (enqueue >= queue.length) {
            long[] newArray = new long[queue.length * 2];
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
     * Compacts the queue into a new array containing only non-zero indices.
     * This method iterates through the current queue, ignoring zeros,
     * and copies all valid indices into a new, smaller array.
     *
     * @return a new, compacted array containing all valid indices from the queue.
     */
    public long[] getCompactQueue() {
        return Arrays.copyOf(queue, enqueue);
    }

}