package valthorne.portable;

/**
 * Application-thread completion callback for one model request.
 * Successful delivery transfers model ownership; failure transfers no resource.
 */
public interface AssetListener {
    /**
     * Accepts ownership of a successfully loaded model.
     *
     * @param asset owned model that the recipient must eventually close
     */
    void loaded(ModelAsset asset);

    /**
     * Receives a terminal loading or cancellation failure.
     *
     * @param reason explanation of the failure
     */
    void failed(String reason);
}
