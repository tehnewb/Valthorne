package valthorne.browser.physics;

import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;

/**
 * Returns an independently initialized native world to its suspended Java owner.
 */
@JSFunctor
public interface PhysicsWorldResult extends JSObject {
    /**
     * Supplies the owned world on success or the native initialization failure.
     * @param world owned world, or null on failure
     * @param error failure description, or null on success
     */
    void complete(BrowserPhysicsWorld world, String error);
}
