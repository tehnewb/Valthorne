package valthorne.graphics.bgfx;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import org.teavm.interop.Async;
import org.teavm.interop.AsyncCallback;
import org.teavm.jso.JSBody;
import valthorne.browser.BrowserShaderResult;

/**
 * Compiles portable bgfx source in an isolated, lazily loaded WebAssembly compiler.
 * Loading suspends the application coroutine; compilation itself is synchronous
 * and cannot interleave native scratch operations. The host owns compiler memory
 * through the application session. Successful bytecode is retained by the shared
 * stage recipe, so rendering and context restoration do not recompile warm stages.
 */
final class BgfxShaderCompiler {
    /**
     * Prevents constructing the browser compiler boundary.
     */
    private BgfxShaderCompiler() {
    }

    /**
     * Compiles a validated portable stage for WebGL 2.
     *
     * @param source portable bgfx stage source
     * @param varyings explicit shared varyings, or null for engine defaults
     * @param type compiler stage letter
     * @param family selected backend family, which must be essl
     * @return complete bgfx shader container
     */
    static byte[] compile(String source, String varyings, char type, String family) {
        if (!family.equals("essl")) throw new UnsupportedOperationException("Browser source shaders require WebGL 2");
        if (varyings == null) {
            try (InputStream stream = BgfxShaderCompiler.class.getResourceAsStream("/valthorne/shaders/bgfx/engine/varying.def.sc")) {
                if (stream == null) throw new IllegalStateException("Missing engine varying declarations");
                varyings = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException failure) {
                throw new UncheckedIOException("Could not read shader varyings", failure);
            }
        }
        return compile(source, varyings, Character.toString(type));
    }

    /**
     * Suspends the current coroutine while the compiler module loads if necessary.
     *
     * @param source validated portable source
     * @param varyings shared varying declarations
     * @param type compiler stage letter
     * @return complete shader container
     */
    @Async
    private static native byte[] compile(String source, String varyings, String type);

    /**
     * Connects the one-shot browser compiler result to the waiting coroutine.
     *
     * @param source validated portable source
     * @param varyings shared varying declarations
     * @param type compiler stage letter
     * @param callback suspended application continuation
     */
    private static void compile(String source, String varyings, String type, AsyncCallback<byte[]> callback) {
        request(source, varyings, type, (bytes, error) -> {
            if (error == null) callback.complete(bytes);
            else callback.error(new IllegalArgumentException("Could not compile " + type + " shader:\n" + error));
        });
    }

    /**
     * Requests compilation from the owning browser application session.
     *
     * @param source portable source
     * @param varyings shared varying declarations
     * @param type stage letter
     * @param callback one-shot compilation result
     */
    @JSBody(params = {"source", "varyings", "type", "callback"}, script = "globalThis.valthorneHost.shaders.compile(source,varyings,type).then(bytes=>callback(new Int8Array(bytes.buffer,bytes.byteOffset,bytes.byteLength),null),error=>callback(null,String(error)));")
    private static native void request(String source, String varyings, String type, BrowserShaderResult callback);
}
