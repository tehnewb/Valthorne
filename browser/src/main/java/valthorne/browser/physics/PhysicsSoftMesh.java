package valthorne.browser.physics;

import org.teavm.jso.JSObject;
import org.teavm.jso.typedarrays.Float32Array;

/**
 * Owns a reference to an optimized Jolt deformable mesh and its cached vertices.
 * Body destruction precedes closing the mesh; close releases retained settings
 * exactly once. All access is synchronous on the world owner coroutine.
 */
public interface PhysicsSoftMesh extends JSObject {
    /**
     * Returns the native body identifier.
     */
    int getId();

    /**
     * Returns a cached borrowed native vertex.
     */
    PhysicsVertex getVertex(int index);

    /**
     * Sets every optimized edge compliance.
     */
    void setCompliance(float compliance);

    /**
     * Sets XPBD solver iterations.
     */
    void setNumIterations(int iterations);

    /**
     * Sets native linear damping.
     */
    void setLinearDamping(float damping);

    /**
     * Sets native gravity scaling.
     */
    void setGravityFactor(float factor);

    /**
     * Controls native sleeping.
     */
    void setAllowSleeping(boolean enabled);

    /**
     * Copies packed XYZ world positions into caller storage.
     */
    void capture(Float32Array positions);

    /**
     * Releases the retained native settings reference.
     */
    void close();
}
