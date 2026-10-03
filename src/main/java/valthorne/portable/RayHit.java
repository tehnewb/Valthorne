package valthorne.portable;

/**
 * Closest physics ray intersection. The body is borrowed from the scene; this
 * result does not own it or remove it. Distance uses the scene's world units.
 *
 * @param body borrowed hit body
 * @param distance distance along the normalized ray
 */
public record RayHit(PhysicsBody body, float distance) {
}
