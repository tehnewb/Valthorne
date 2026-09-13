package valthorne.portable;

/** Platform-neutral asynchronous scene assets. Callback delivery occurs on the application thread. */
public interface AssetService {
    interface Request {
        /** Idempotent; cancellation before completion delivers failed("cancelled"). */
        void cancel();
    }
    interface Listener {
        /** Transfers ownership of an attached model handle to the caller. */
        void loaded(ModelAsset asset);
        /** Terminal failure, including cancellation; no model ownership is transferred. */
        void failed(String reason);
    }
    /** Loads a self-contained binary glTF 2.0 asset. Implementations impose explicit resource limits. */
    Request loadGlb(String uri, Listener listener);
}
