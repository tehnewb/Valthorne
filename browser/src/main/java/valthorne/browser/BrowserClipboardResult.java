package valthorne.browser;

import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;

/**
 * Completes one browser clipboard request without reentering another Java frame.
 */
@JSFunctor
public interface BrowserClipboardResult extends JSObject {
    /**
     * Delivers the clipboard outcome to the waiting application coroutine.
     * @param success whether clipboard access succeeded
     * @param text retrieved text, or null for writes and failed reads
     */
    void complete(boolean success, String text);
}
