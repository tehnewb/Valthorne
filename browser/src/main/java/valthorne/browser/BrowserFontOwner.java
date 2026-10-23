package valthorne.browser;

import org.teavm.jso.JSObject;
import org.teavm.jso.JSProperty;

/**
 * Keeps a native CPU font parser alive for the Java font metrics that borrow it.
 * The browser host registers the token with its native-allocation finalizer.
 */
public interface BrowserFontOwner extends JSObject {
    /**
     * Returns the native parser while keeping its lifetime token reachable.
     * @return native font pointer
     */
    @JSProperty
    int getPointer();
}
