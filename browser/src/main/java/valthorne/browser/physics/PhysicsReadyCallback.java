package valthorne.browser.physics;

import org.teavm.jso.JSObject;
import org.teavm.jso.JSFunctor;

/**
 * Completes optional physics initialization on the suspended Java coroutine.
 */
@JSFunctor
public interface PhysicsReadyCallback extends JSObject {
    /**
     * Returns null on success or a contextual browser failure.
     */
    void complete(String error);
}
