package valthorne.browser;

import org.teavm.interop.Async;
import org.teavm.interop.AsyncCallback;
import org.teavm.jso.JSBody;

/**
 * Bridges the UI's clipboard policy to browser clipboard promises and native paste
 * events. Waiting suspends only the application coroutine. Failed or denied access
 * returns the same false/null outcomes as the desktop editing adapter.
 */
public final class BrowserClipboard {
    /**
     * Prevents constructing stateless clipboard adapters.
     */
    private BrowserClipboard() {
    }

    /**
     * Writes selected text and confirms assignment before a cut deletes it.
     * @param text copied text
     * @return whether clipboard assignment succeeded
     */
    @Async
    public static native boolean write(String text);

    /**
     * Connects one suspended write to the browser clipboard.
     * @param text copied text
     * @param callback suspended Java continuation
     */
    private static void write(String text, AsyncCallback<Boolean> callback) {
        /*
         * Await the browser result so a denied cut cannot remove the selection.
         */
        writeRequest(text, (success, ignored) -> callback.complete(success));
    }

    /**
     * Reads a trusted native paste event or the browser clipboard API.
     * @return clipboard text, or null when access is unavailable
     */
    @Async
    public static native String read();

    /**
     * Connects one suspended read to browser paste ownership.
     * @param callback suspended Java continuation
     */
    private static void read(AsyncCallback<String> callback) {
        /*
         * Native paste events avoid unnecessary permission prompts for Ctrl+V.
         */
        readRequest((success, text) -> callback.complete(success ? text : null));
    }

    /**
     * Starts an asynchronous browser clipboard write.
     * @param text selected text
     * @param callback one-shot completion
     */
    @JSBody(params = {"text", "callback"}, script = "globalThis.valthorneHost.writeClipboard(text).then(()=>callback(true,null),()=>callback(false,null));")
    private static native void writeRequest(String text, BrowserClipboardResult callback);

    /**
     * Starts an asynchronous browser clipboard read.
     * @param callback one-shot completion
     */
    @JSBody(params = "callback", script = "globalThis.valthorneHost.readClipboard().then(text=>callback(true,text),()=>callback(false,null));")
    private static native void readRequest(BrowserClipboardResult callback);
}
