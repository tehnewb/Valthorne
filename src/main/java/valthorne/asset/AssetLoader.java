package valthorne.asset;

/**
 * Converts an asset request into loaded data. {@link Assets} selects a registered
 * loader by the request's concrete parameter class and can invoke it on an executor
 * thread. Implementations used there must respect the thread requirements of their
 * resources and must not assume a current graphics or audio context.
 *
 * @param <P> the type of the parameters used to configure the loading of the asset. This type must extend
 *            the AssetParameters interface.
 * @param <T> the type of the asset data to be loaded.
 * @author Albert Beaupre
 */
public interface AssetLoader<P extends AssetParameters, T> {

    /**
     * Loads asset data using the given parameters. This operation performs the work
     * synchronously on the calling thread; scheduling and cache reuse belong to
     * {@link Assets}. Loading failures may be reported as runtime exceptions, which
     * complete an asynchronous asset request exceptionally. Concrete implementations
     * define validation and ownership of the returned data.
     *
     * @param parameters the parameters required to configure and execute the asset loading process
     * @return the loaded asset data of the specified type
     */
    T load(P parameters);
}
