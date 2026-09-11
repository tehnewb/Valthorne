package valthorne.asset;

/**
 * Describes an asset request and its identity in the shared {@link Assets} cache.
 * The concrete parameter class selects the registered loader; {@link #key()} selects
 * the cached future. Implementations should include every loading option that changes
 * the result in the key and avoid collisions with other asset types.
 *
 * @author Albert Beaupre
 */
public interface AssetParameters {

    /**
     * Returns the stable cache identity for this request. Requests with the same key
     * share an existing load even if their parameter objects differ. The key must be
     * non-null when used with {@link Assets}; generating it should not load the asset.
     *
     * @return a string representing the unique identifier key for the asset parameters
     */
    String key();

}
