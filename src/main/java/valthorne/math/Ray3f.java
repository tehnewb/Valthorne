package valthorne.math;

/**
 * Mutable ray defined by an origin and a normalized direction.
 */
public final class Ray3f {

    private final Vector3f origin = new Vector3f();
    private final Vector3f direction = new Vector3f(0f, 0f, -1f);

    public Ray3f() {
    }

    public Ray3f(Vector3f origin, Vector3f direction) {
        set(origin, direction);
    }

    public Vector3f getOrigin() {
        return origin;
    }

    public Vector3f getDirection() {
        return direction;
    }

    public Ray3f set(Vector3f origin, Vector3f direction) {
        if (origin == null) throw new NullPointerException("origin");
        if (direction == null) throw new NullPointerException("direction");
        this.origin.set(origin);
        this.direction.set(direction).normalize();
        return this;
    }

    public Ray3f set(float originX, float originY, float originZ,
                     float directionX, float directionY, float directionZ) {
        this.origin.set(originX, originY, originZ);
        this.direction.set(directionX, directionY, directionZ).normalize();
        return this;
    }
}
