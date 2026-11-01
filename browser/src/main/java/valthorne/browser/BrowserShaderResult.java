package valthorne.browser;

import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;

/**
 * Delivers a portable shader compiler's one-shot bytecode or failure diagnostic.
 */
@JSFunctor
public interface BrowserShaderResult extends JSObject {
    /**
     * Resumes the requesting application coroutine exactly once.
     *
     * @param bytes complete container, or null on failure
     * @param error compiler diagnostic, or null on success
     */
    void complete(byte[] bytes, String error);
}
