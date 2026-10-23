package valthorne.browser.audio;

import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;

/**
 * Completes one browser audio request without reentering another Java frame.
 */
@JSFunctor
public interface BrowserAudioResult extends JSObject {
    /**
     * Delivers the audio outcome to the waiting application coroutine.
     * @param success whether the requested operation completed successfully
     * @param text endpoint identifiers or a failure description, depending on the operation
     */
    void complete(boolean success, String text);
}
