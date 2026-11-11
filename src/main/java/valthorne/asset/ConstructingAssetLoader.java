package valthorne.asset;

/**
 * An asset loader that can finish its decoded data on the graphics thread.
 * The same registered loader supports requests for data and the finished type.
 * A successful construction takes ownership of the decoded data. On failure,
 * Assets releases the data using its normal data cleanup policy.
 *
 * @param <P> request parameters
 * @param <D> decoded data type
 * @param <T> constructed resource type
 */
public interface ConstructingAssetLoader<P extends AssetParameters, D, T> extends AssetLoader<P, D> {
    /**
     * Identifies the finished output that selects graphics-thread construction.
     *
     * @return finished resource class
     */
    Class<T> assetType();

    /**
     * Constructs the requested resource on the graphics thread.
     *
     * @param data decoded data transferred to the resource on success
     * @return newly owned resource
     */
    T construct(D data);

    /**
     * Releases a constructed resource and any data it owns on the graphics thread.
     *
     * @param asset resource to release
     */
    void dispose(T asset);
}
