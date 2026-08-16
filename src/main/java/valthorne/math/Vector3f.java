package valthorne.math;

/**
 * Mutable 3D vector with common arithmetic and basis-building helpers.
 */
public class Vector3f {

    private float x;
    private float y;
    private float z;

    public Vector3f() {
        this(0f, 0f, 0f);
    }

    public Vector3f(Vector3f other) {
        this(other.x, other.y, other.z);
    }

    public Vector3f(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public float getX() {
        return x;
    }

    public void setX(float x) {
        this.x = x;
    }

    public float getY() {
        return y;
    }

    public void setY(float y) {
        this.y = y;
    }

    public float getZ() {
        return z;
    }

    public void setZ(float z) {
        this.z = z;
    }

    public Vector3f set(Vector3f other) {
        if (other == null) throw new NullPointerException("other");
        this.x = other.x;
        this.y = other.y;
        this.z = other.z;
        return this;
    }

    public Vector3f set(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
        return this;
    }

    public Vector3f add(Vector3f other) {
        if (other == null) throw new NullPointerException("other");
        this.x += other.x;
        this.y += other.y;
        this.z += other.z;
        return this;
    }

    public Vector3f add(float dx, float dy, float dz) {
        this.x += dx;
        this.y += dy;
        this.z += dz;
        return this;
    }

    public Vector3f sub(Vector3f other) {
        if (other == null) throw new NullPointerException("other");
        this.x -= other.x;
        this.y -= other.y;
        this.z -= other.z;
        return this;
    }

    public Vector3f sub(float dx, float dy, float dz) {
        this.x -= dx;
        this.y -= dy;
        this.z -= dz;
        return this;
    }

    public Vector3f mul(float scalar) {
        this.x *= scalar;
        this.y *= scalar;
        this.z *= scalar;
        return this;
    }

    public float dot(Vector3f other) {
        if (other == null) throw new NullPointerException("other");
        return x * other.x + y * other.y + z * other.z;
    }

    public Vector3f cross(Vector3f other) {
        if (other == null) throw new NullPointerException("other");
        float nx = y * other.z - z * other.y;
        float ny = z * other.x - x * other.z;
        float nz = x * other.y - y * other.x;
        this.x = nx;
        this.y = ny;
        this.z = nz;
        return this;
    }

    public Vector3f setCross(Vector3f a, Vector3f b) {
        if (a == null) throw new NullPointerException("a");
        if (b == null) throw new NullPointerException("b");
        this.x = a.y * b.z - a.z * b.y;
        this.y = a.z * b.x - a.x * b.z;
        this.z = a.x * b.y - a.y * b.x;
        return this;
    }

    public float lengthSquared() {
        return x * x + y * y + z * z;
    }

    public float length() {
        return (float) Math.sqrt(lengthSquared());
    }

    public Vector3f normalize() {
        float length = length();
        if (length != 0f) {
            x /= length;
            y /= length;
            z /= length;
        }
        return this;
    }

    public float distanceSquared(Vector3f other) {
        if (other == null) throw new NullPointerException("other");
        float dx = other.x - x;
        float dy = other.y - y;
        float dz = other.z - z;
        return dx * dx + dy * dy + dz * dz;
    }

    public float distance(Vector3f other) {
        return (float) Math.sqrt(distanceSquared(other));
    }

    public Vector3f lerp(Vector3f target, float alpha) {
        if (target == null) throw new NullPointerException("target");
        this.x += (target.x - this.x) * alpha;
        this.y += (target.y - this.y) * alpha;
        this.z += (target.z - this.z) * alpha;
        return this;
    }

    public Vector3f negate() {
        this.x = -x;
        this.y = -y;
        this.z = -z;
        return this;
    }

    public Vector3f copy() {
        return new Vector3f(this);
    }
}
