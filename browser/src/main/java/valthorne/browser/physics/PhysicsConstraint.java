package valthorne.browser.physics;

import org.teavm.jso.JSObject;

/**
 * Owns a reference to a native distance or pivot constraint.
 * Remove it from the world before closing; type information survives disposal.
 */
public interface PhysicsConstraint extends JSObject {
    /**
     * Reports whether this relationship has distance limits.
     */
    boolean isDistance();

    /**
     * Reports participation in native solving.
     */
    boolean getEnabled();

    /**
     * Updates participation without releasing the native constraint.
     */
    void setEnabled(boolean enabled);

    /**
     * Returns the lower anchor separation bound in meters.
     */
    float getMinDistance();

    /**
     * Returns the upper anchor separation bound in meters.
     */
    float getMaxDistance();

    /**
     * Changes validated anchor separation bounds in meters.
     */
    void setDistance(float minimum, float maximum);

    /**
     * Releases the native reference exactly once.
     */
    void close();
}
