package valthorne.asset;

/**
 * Bookkeeping for one prepared-loading observation between progress resets.
 * Assets guards all access with its progress lock; callbacks retain this object
 * after reset so earlier work cannot modify the replacement observation.
 */
final class AssetLoadProgress {
    int requests; // Requests enrolled, including queued and claimed work.
    int processed; // Requests reaching a terminal result, including failures.
    int pending; // Claimed requests whose completion callback has not run.
    int failed; // Processed requests that failed or were cancelled.

    /**
     * Starts an observation containing the requests still waiting in the queue.
     *
     * @param queued number of queued requests enrolled at reset
     */
    AssetLoadProgress(int queued) {
        requests = queued;
    }
}
