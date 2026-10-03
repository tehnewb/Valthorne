package valthorne.portable;

/**
 * Cancellation action for one asynchronous asset request. It owns no asset.
 * Implementations deliver terminal callbacks on the application thread.
 */
public interface AssetRequest {
    /**
     * Cancels an unfinished request, delivering a terminal cancellation failure.
     * Repeated cancellation has no effect after completion.
     */
    void cancel();
}
