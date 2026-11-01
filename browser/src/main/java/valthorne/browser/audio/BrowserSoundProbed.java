package valthorne.browser.audio;

import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;

/**
 * Completes a bounded audio metadata probe.
 */
@JSFunctor
public interface BrowserSoundProbed extends JSObject {
    /**
     * Delivers exactly one result to the suspended Java caller.
     * @param metadata successful result, or null on failure
     * @param error failure description, or null on success
     */
    void complete(BrowserSoundMetadata metadata, String error);
}
