package valthorne.browser;

import java.io.IOException;
import org.teavm.interop.Async;
import org.teavm.interop.AsyncCallback;
import org.teavm.jso.JSBody;

/**
 * Reads embedded classpath assets or fetches application URLs while suspending
 * the current Java coroutine. Asset loading never blocks the browser event loop
 * or starts a second application frame. HTTP and decoding failures propagate to
 * the caller through the existing engine exception paths.
 */
public final class BrowserAssets {
    /**
     * Prevents constructing stateless browser asset readers.
     */
    private BrowserAssets() {
    }

    /**
     * Reads encoded bytes from the export's classpath resources or a browser URL.
     * @param path classpath-relative asset name or fetchable URL
     * @return independently owned asset bytes
     * @throws IOException if the resource cannot be read
     */
    public static byte[] read(String path) throws IOException {
        /*
         * Prefer resources registered by the export manifest, preserving existing
         * engine shader and font loading without an HTTP request per asset.
         */
        if (path == null) throw new NullPointerException("path");
        byte[] local = local(path);
        if (local != null) return local;
        return fetch(path);
    }

    /**
     * Finds embedded bytes without starting a network request.
     * @param path classpath-relative asset name
     * @return owned resource bytes, or null for a network asset
     * @throws IOException if an embedded resource cannot be read
     */
    public static byte[] local(String path) throws IOException {
        /*
         * Incremental media callers must distinguish embedded data from URLs
         * before choosing whether to retain the encoded source in memory.
         */
        if (path == null) throw new NullPointerException("path");
        String name = path.startsWith("/") ? path : "/" + path;
        try (var stream = BrowserAssets.class.getResourceAsStream(name)) {
            return stream == null ? null : stream.readAllBytes();
        }
    }

    /**
     * Suspends until a browser fetch completes.
     * @param path fetchable application asset path
     * @return encoded bytes
     * @throws IOException if fetching fails
     */
    @Async
    private static native byte[] fetch(String path) throws IOException;

    /**
     * Connects one suspended Java read to the host's cancellable browser fetch.
     * @param path requested asset
     * @param callback suspended Java continuation
     */
    private static void fetch(String path, AsyncCallback<byte[]> callback) {
        /*
         * Continuations resume the waiting application coroutine, preserving the
         * same init/update/render/dispose ordering across asynchronous I/O.
         */
        request(path, (bytes, error) -> {
            if (error == null) callback.complete(bytes);
            else callback.error(new IOException("Could not read browser asset " + path + ": " + error));
        });
    }

    /**
     * Starts a fetch owned by the current browser application session.
     * @param path requested asset
     * @param callback one-shot completion
     */
    @JSBody(params = {"path", "callback"}, script = "globalThis.valthorneHost.readAsset(path).then(bytes=>callback(new Int8Array(bytes),null),error=>callback(null,String(error)));")
    private static native void request(String path, BrowserAssetResult callback);
}
