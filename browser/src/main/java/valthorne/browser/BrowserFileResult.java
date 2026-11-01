package valthorne.browser;

import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;

/**
 * Delivers one browser picker result or diagnostic to a suspended Java caller.
 */
@JSFunctor
public interface BrowserFileResult extends JSObject {
    /**
     * Completes exactly one browser picker operation.
     * @param result selected entry container, or null on cancellation or failure
     * @param error diagnostic on failure, otherwise null
     */
    void complete(JSObject result, String error);
}
