package valthorne.browser.physics;

import org.teavm.jso.JSObject;

/**
 * Borrowed numeric scratch shared by synchronous browser physics operations.
 * Values live in JavaScript rather than WASM memory and allocate only at world
 * creation. Callers must not retain another operation's scratch contents.
 */
public interface PhysicsVector extends JSObject {
    /**
     * Sets three coordinates and retains the same storage.
     */
    PhysicsVector set(float x, float y, float z);

    /**
     * Sets quaternion coordinates in the same storage.
     */
    PhysicsVector set(float x, float y, float z, float w);

    /**
     * Reads the horizontal coordinate.
     */
    float getX();

    /**
     * Reads the vertical coordinate.
     */
    float getY();

    /**
     * Reads depth or quaternion Z.
     */
    float getZ();

    /**
     * Reads quaternion W.
     */
    float getW();

    /**
     * Reads horizontal position with the shared vector convention.
     */
    float x();

    /**
     * Reads vertical position with the shared vector convention.
     */
    float y();

    /**
     * Projects a velocity back to the simulation plane.
     */
    void setZ(float z);

    /**
     * Adds a velocity change without allocating another vector.
     */
    void addInPlace(float x, float y, float z);
}
