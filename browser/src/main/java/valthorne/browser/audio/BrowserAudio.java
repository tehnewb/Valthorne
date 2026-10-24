package valthorne.browser.audio;

import java.util.Arrays;
import java.util.List;
import org.teavm.interop.Async;
import org.teavm.interop.AsyncCallback;
import org.teavm.jso.JSBody;


/**
 * Owns Web Audio session startup, output selection, and completion of context
 * teardown. Calls belong to AudioRuntime's cooperative owner. Browser autoplay
 * policy may suspend the context until a trusted activation; readiness means
 * resources can be created, independently of that policy.
 */
public final class BrowserAudio {
    /**
     * Prevents construction of session lifecycle bindings.
     */
    private BrowserAudio() {
    }

    /**
     * Creates the session context without awaiting trusted user activation.
     */
    @JSBody(script = "globalThis.valthorneHost.audioBackend.initialize();")
    public static native void initialize();

    /**
     * Returns whether output switching is implemented by this browser.
     * @return availability of AudioContext.setSinkId
     */
    @JSBody(script = "return typeof globalThis.valthorneHost.audioBackend.context().setSinkId==='function';")
    public static native boolean supportsSwitching();

    /**
     * Returns the context's selected output token.
     * @return endpoint identifier, with default representing the system output
     */
    @JSBody(script = "return globalThis.valthorneHost.audioBackend.context().sinkId||'default';")
    public static native String device();

    /**
     * Lists outputs already visible under the browser's existing permissions.
     * @return immutable endpoint identifier snapshot including the default output
     */
    public static List<String> devices() {
        /*
         * Identifiers match setSinkId exactly; enumeration never requests new permissions.
         */
        return List.copyOf(Arrays.asList(deviceNames().split("\n")));
    }

    /**
     * Suspends while the browser enumerates its already authorized outputs.
     * @return newline-delimited endpoint identifiers
     */
    @Async
    private static native String deviceNames();

    /**
     * Connects an output enumeration promise to its suspended caller.
     * @param callback waiting Java continuation
     */
    private static void deviceNames(AsyncCallback<String> callback) {
        /*
         * Permission and API absence still expose the universally valid default endpoint.
         */
        enumerate((success, value) -> callback.complete(success ? value : "default"));
    }

    /**
     * Reads output identifiers without prompting for microphone access.
     * @param callback one-shot enumeration result
     */
    @JSBody(params = "callback", script = "globalThis.valthorneHost.audioBackend.deviceIds().then(value=>callback(true,value),()=>callback(false,null));")
    private static native void enumerate(BrowserAudioResult callback);

    /**
     * Selects an authorized output and waits for the browser's actual result.
     * @param name endpoint identifier, or null for default
     * @return successful output selection
     */
    @Async
    public static native boolean switchDevice(String name);

    /**
     * Suspends the owning Java coroutine during output selection.
     * @param name endpoint identifier
     * @param callback waiting Java continuation
     */
    private static void switchDevice(String name, AsyncCallback<Boolean> callback) {
        /*
         * Rejection preserves existing sources and returns the facade's documented false outcome.
         */
        select(name, (success, ignored) -> callback.complete(success));
    }

    /**
     * Starts a resource-preserving output selection.
     * @param name endpoint identifier
     * @param callback one-shot success outcome
     */
    @JSBody(params = {"name", "callback"}, script = "const context=globalThis.valthorneHost.audioBackend.context();if(typeof context.setSinkId!=='function'){callback(false,null);return;}context.setSinkId(!name||name==='default'?'':name).then(()=>callback(true,null),()=>callback(false,null));")
    private static native void select(String name, BrowserAudioResult callback);

    /**
     * Waits until sources, decoders, and the context have been released.
     */
    @Async
    public static native void close();

    /**
     * Delivers the actual context-close outcome before session termination completes.
     * @param callback waiting owner continuation
     */
    private static void close(AsyncCallback<Void> callback) {
        /*
         * A replacement session cannot reuse the host until browser teardown has completed.
         */
        closeRequest((success, error) -> {
            if (success) callback.complete(null);
            else callback.error(new IllegalStateException("Could not close browser audio: " + error));
        });
    }

    /**
     * Closes the current browser playback session.
     * @param callback one-shot teardown outcome
     */
    @JSBody(params = "callback", script = "globalThis.valthorneHost.audioBackend.close().then(()=>callback(true,null),error=>callback(false,String(error)));")
    private static native void closeRequest(BrowserAudioResult callback);
}
