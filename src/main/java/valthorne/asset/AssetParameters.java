package valthorne.asset;

/**
 * Represents a set of parameters for an asset. Implementations of this interface
 * define specific configurations or attributes required to load, track, or manage
 * particular types of assets in a system.
 */
public interface AssetParameters {

    /**
     * Retrieves the unique key associated with the asset parameters.
     * This key is used to identify and manage the asset configuration within the system.
     *
     * @return a string representing the unique identifier key for the asset parameters
     */
    String key();

}
