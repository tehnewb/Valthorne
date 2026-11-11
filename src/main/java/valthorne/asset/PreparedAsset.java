package valthorne.asset;

/**
 * Stores the input and output type selected for a queued asset.
 * @param parameters input to the registered loader
 * @param type requested data or finished resource type
 */
record PreparedAsset(AssetParameters parameters, Class<?> type) {}
