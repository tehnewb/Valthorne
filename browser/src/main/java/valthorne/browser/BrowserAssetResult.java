package valthorne.browser;

import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;

/**
 * Completes one asynchronous browser asset read with copied bytes or a diagnostic.
 */
@JSFunctor
public interface BrowserAssetResult extends JSObject {
    /**
     * Delivers exactly one request result.
     * @param bytes encoded asset bytes, or null on failure
     * @param error failure diagnostic, or null on success
     */
    void complete(byte[] bytes, String error);
}
