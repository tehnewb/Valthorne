package valthorne.browser;

import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;

/**
 * Delivers completion of a browser file write without retaining its borrowed bytes.
 */
@JSFunctor
public interface BrowserFileWritten extends JSObject {
    /**
     * Completes one write or reports its failure.
     * @param error diagnostic or null on success
     */
    void complete(String error);
}
