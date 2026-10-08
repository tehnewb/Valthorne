package valthorne.asset;

/**
 * Mutable counters describing one prepared asset-loading observation. Getters expose
 * enrolled, processed, pending and failed request counts; fluent setters support
 * caller-owned progress values. Counts must be nonnegative but may be configured
 * independently. Instances are not thread-safe and require external coordination
 * when shared. {@link Assets#getLoadProgress()} returns a detached snapshot so callers
 * cannot change the counters maintained by Assets.
 */
public final class AssetLoadProgress {
    private int requests; // Enrolled requests, including queued and claimed work.
    private int processed; // Requests reaching a terminal result, including failures.
    private int pending; // Claimed requests whose completion callback has not run.
    private int failed; // Processed requests that failed or were cancelled.

    /**
     * Creates an empty progress observation.
     */
    public AssetLoadProgress() {
    }

    /**
     * Creates an observation containing queued requests.
     *
     * @param queued nonnegative enrolled request count
     * @throws IllegalArgumentException if queued is negative
     */
    public AssetLoadProgress(int queued) {
        setRequests(queued);
    }
    /**
     * Returns the requests request count.
     *
     * @return nonnegative requests count
     */
    public int getRequests() {
        return requests;
    }

    /**
     * Sets the requests request count.
     *
     * @param requests nonnegative count
     * @return this progress observation
     * @throws IllegalArgumentException if the count is negative
     */
    public AssetLoadProgress setRequests(int requests) {
        if (requests < 0) throw new IllegalArgumentException("requests must be nonnegative");
        this.requests = requests;
        return this;
    }

    /**
     * Returns the processed request count.
     *
     * @return nonnegative processed count
     */
    public int getProcessed() {
        return processed;
    }

    /**
     * Sets the processed request count.
     *
     * @param processed nonnegative count
     * @return this progress observation
     * @throws IllegalArgumentException if the count is negative
     */
    public AssetLoadProgress setProcessed(int processed) {
        if (processed < 0) throw new IllegalArgumentException("processed must be nonnegative");
        this.processed = processed;
        return this;
    }

    /**
     * Returns the pending request count.
     *
     * @return nonnegative pending count
     */
    public int getPending() {
        return pending;
    }

    /**
     * Sets the pending request count.
     *
     * @param pending nonnegative count
     * @return this progress observation
     * @throws IllegalArgumentException if the count is negative
     */
    public AssetLoadProgress setPending(int pending) {
        if (pending < 0) throw new IllegalArgumentException("pending must be nonnegative");
        this.pending = pending;
        return this;
    }

    /**
     * Returns the failed request count.
     *
     * @return nonnegative failed count
     */
    public int getFailed() {
        return failed;
    }

    /**
     * Sets the failed request count.
     *
     * @param failed nonnegative count
     * @return this progress observation
     * @throws IllegalArgumentException if the count is negative
     */
    public AssetLoadProgress setFailed(int failed) {
        if (failed < 0) throw new IllegalArgumentException("failed must be nonnegative");
        this.failed = failed;
        return this;
    }
}
