package valthorne.browser.physics;

import org.teavm.jso.JSObject;
import org.teavm.jso.JSFunctor;

/**
 * Copies borrowed native contact identities and manifold data synchronously.
 * Application listeners run later, after the shared Java world commits a step.
 */
@JSFunctor
public interface PhysicsContactCallback extends JSObject {
    /**
     * Copies one begin, persist or end event into the bounded Java contact queue.
     */
    void capture(int type, int first, int second, int firstSubShape, int secondSubShape, float x, float y, float depth);
}
