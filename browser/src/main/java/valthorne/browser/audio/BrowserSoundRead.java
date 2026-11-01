package valthorne.browser.audio;

import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;
import org.teavm.jso.typedarrays.Uint8Array;

/**
 * Completes one bounded PCM read.
 */
@JSFunctor
public interface BrowserSoundRead extends JSObject {
    /**
     * Delivers exactly one result to the suspended Java caller.
     * @param bytes successful result, or null on failure
     * @param error failure description, or null on success
     */
    void complete(Uint8Array bytes, String error);
}
