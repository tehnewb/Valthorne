package valthorne.asset;

/**
 * AssetLoader is a generic interface defining a contract for loading asset data of a specific type using
 * parameterized configuration. It is designed to handle asset loading processes for various types of assets.
 *
 * @param <P> the type of the parameters used to configure the loading of the asset. This type must extend
 *            the AssetParameters interface.
 * @param <T> the type of the asset data to be loaded.
 */
public interface AssetLoader<P extends AssetParameters, T> {

    /**
     * Loads asset data using the given parameters.
     *
     * @param parameters the parameters required to configure and execute the asset loading process
     * @return the loaded asset data of the specified type
     */
    T load(P parameters);
}
