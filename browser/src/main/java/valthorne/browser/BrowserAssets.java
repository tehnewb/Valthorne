package valthorne.browser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
        if (!path.contains("://")) {
            Path workspacePath = Path.of(path);
            if (Files.isRegularFile(workspacePath)) return Files.readAllBytes(workspacePath);
        }
        byte[] local = local(path);
        if (local != null) return local;
        return fetch(path);
    }

    /**
     * Reads a sandbox path, embedded asset, or host-relative application URL.
     * @param path logical resource path
     * @return independent encoded bytes
     * @throws IOException if the resource cannot be read
     */
    public static byte[] read(Path path) throws IOException {
        /*
         * Preserve the same resolution policy for map dependency resolvers.
         */
        return read(path.toString());
    }

    /**
     * Resolves a map dependency beside its referring document without converting
     * web URLs into virtual filesystem paths. Plain resource paths stay relative
     * so classpath manifests and pages hosted below the origin root still work.
     * @param parent referring document path or URL, optionally null or blank
     * @param dependency nonnull child path or URL
     * @return normalized resource path or absolute HTTP(S) URL
     */
    public static String resolve(String parent, String dependency) {
        /*
         * URL resolution must preserve scheme and authority. Workspace/classpath
         * dependencies instead use the same lexical path semantics as Java loaders.
         */
        if (dependency == null) throw new NullPointerException("dependency");
        if (dependency.contains("://") || parent != null && parent.contains("://")) return resolveUrl(parent, dependency);
        Path child = Path.of(dependency);
        if (child.isAbsolute()) return BrowserPaths.normalize(dependency);
        Path directory = parent == null || parent.isBlank() ? null : Path.of(parent).getParent();
        return BrowserPaths.normalize((directory == null ? child : directory.resolve(child)).toString());
    }

    /**
     * Resolves an HTTP(S) dependency using the browser URL parser.
     * @param parent document URL or null
     * @param dependency child reference
     * @return resolved absolute URL
     */
    @JSBody(params = {"parent", "dependency"}, script = "return new URL(dependency,parent ? new URL(parent,document.baseURI) : document.baseURI).href;")
    private static native String resolveUrl(String parent, String dependency);

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
