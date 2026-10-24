package valthorne.browser;

import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;

/**
 * Resumes the suspended application coroutine from an animation frame.
 */
@JSFunctor
public interface BrowserWake extends JSObject {
    /**
     * Signals that the browser can deliver the next frame.
     */
    void invoke();
}
