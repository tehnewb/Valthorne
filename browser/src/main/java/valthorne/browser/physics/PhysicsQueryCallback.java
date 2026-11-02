package valthorne.browser.physics;

import org.teavm.jso.JSObject;
import org.teavm.jso.JSFunctor;

/**
 * Receives conservative native broadphase candidates on the owner coroutine.
 */
@JSFunctor
public interface PhysicsQueryCallback extends JSObject {
    /**
     * Collects one complete native body identifier, including its generation.
     */
    void addHit(int id);
}
