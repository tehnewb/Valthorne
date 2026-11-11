package valthorne.browser;

import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;

/**
 * Receives copied DOM input for deferred delivery on the application coroutine.
 */
@JSFunctor
public interface BrowserInput extends JSObject {
    /**
     * Queues one browser event without reentering application callbacks.
     * @param kind event category
     * @param a first integer payload
     * @param b second integer payload
     * @param c third integer payload
     * @param d fourth integer payload
     * @param x horizontal or scroll payload
     * @param y vertical or scroll payload
     * @param text optional dropped-file path
     */
    void invoke(int kind, int a, int b, int c, int d, double x, double y, String text);
}
