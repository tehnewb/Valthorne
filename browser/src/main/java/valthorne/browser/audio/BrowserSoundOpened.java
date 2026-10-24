package valthorne.browser.audio;

import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;

/**
 * Completes ownership transfer for an incremental decoder.
 */
@JSFunctor
public interface BrowserSoundOpened extends JSObject {
    /**
     * Delivers exactly one result to the suspended Java caller.
     * @param handle successful result, or null on failure
     * @param error failure description, or null on success
     */
    void complete(BrowserSoundHandle handle, String error);
}
