package valthorne.browser.physics;

import org.teavm.jso.JSObject;

/**
 * Borrows one deformable Jolt vertex until its owning body is destroyed.
 * Velocity reads reuse one numeric vector per vertex; setters copy immediately.
 */
public interface PhysicsVertex extends JSObject {
    /**
     * Copies local mesh position into native storage.
     */
    void setPosition(PhysicsVector position);

    /**
     * Returns the reused vertex velocity scratch.
     */
    PhysicsVector getVelocity();

    /**
     * Copies the supplied velocity immediately.
     */
    void setVelocity(PhysicsVector velocity);

    /**
     * Returns inverse vertex mass; zero means pinned.
     */
    float getInvMass();

    /**
     * Updates inverse vertex mass without reallocating the mesh.
     */
    void setInvMass(float inverseMass);
}
