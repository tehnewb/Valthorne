package java.util.concurrent;

import java.util.ArrayDeque;

/**
 * Drains dependent completion actions iteratively to bound callback stack depth. One dispatcher is
 * confined to each cooperative Java thread.
 */
final class CompletableDispatch {
    final ArrayDeque<Runnable> queue =
            new ArrayDeque<>(); // Pending dependent callbacks in completion order.
    boolean draining; // Whether nested completions should append to the current drain.

    /** Creates an empty per-coroutine dispatch queue. */
    CompletableDispatch() {}
}
