package valthorne.portable;

/**
 * Platform-neutral asynchronous model loading with explicit resource limits.
 * Callbacks run on the application thread and transfer successful asset ownership.
 */
public interface AssetService {
    /**
     * Loads a self-contained binary glTF 2.0 asset asynchronously.
     *
     * @param uri asset location supported by the implementation
     * @param listener terminal completion callback
     * @return cancellation action for this request
     */
    AssetRequest loadGlb(String uri, AssetListener listener);
}
