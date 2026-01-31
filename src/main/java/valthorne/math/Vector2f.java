package valthorne.math;

/**
 * Represents a 2D vector with float components. Provides methods for common vector
 * operations such as addition, subtraction, scalar multiplication, and linear interpolation.
 */
public class Vector2f {

    /**
     * The x-coordinate of this vector
     */
    private float x;

    /**
     * The y-coordinate of this vector
     */
    private float y;

    /**
     * Creates a zero vector (0,0)
     */
    public Vector2f() {
        this(0, 0);
    }

    /**
     * Creates a vector with the given coordinates
     *
     * @param x The x-coordinate
     * @param y The y-coordinate
     */
    public Vector2f(float x, float y) {
        this.x = x;
        this.y = y;
    }

    /**
     * Negates the components of this vector, producing a new vector
     * where each coordinate is the inverse of the corresponding
     * coordinate in the original vector.
     *
     * @return A new vector with components (-x, -y).
     */
    public Vector2f negate() {
        return new Vector2f(-x, -y);
    }

    /**
     * Returns the x-coordinate of this vector.
     *
     * @return The x-coordinate of this vector.
     */
    public float getX() {
        return x;
    }

    /**
     * Sets the x-coordinate of this vector.
     *
     * @param x The new x-coordinate
     */
    public void setX(float x) {
        this.x = x;
    }

    /**
     * Returns the y-coordinate of this vector.
     *
     * @return The y-coordinate of this vector.
     */
    public float getY() {
        return y;
    }

    /**
     * Sets the y-coordinate of this vector.
     *
     * @param y The new y-coordinate
     */
    public void setY(float y) {
        this.y = y;
    }

    /**
     * Sets this vector's coordinates to the specified values
     *
     * @param x The new x-coordinate
     * @param y The new y-coordinate
     * @return This vector for chaining
     */
    public Vector2f set(float x, float y) {
        this.x = x;
        this.y = y;
        return this;
    }

    /**
     * Adds the specified offsets to this vector's coordinates
     *
     * @param dx The x offset to add
     * @param dy The y offset to add
     * @return This vector for chaining
     */
    public Vector2f add(float dx, float dy) {
        this.x += dx;
        this.y += dy;
        return this;
    }

    /**
     * Adds another vector to this vector
     *
     * @param v The vector to add
     * @return This vector for chaining
     */
    public Vector2f add(Vector2f v) {
        this.x += v.x;
        this.y += v.y;
        return this;
    }

    /**
     * Subtracts another vector from this vector
     *
     * @param v The vector to subtract
     * @return This vector for chaining
     */
    public Vector2f sub(Vector2f v) {
        this.x -= v.x;
        this.y -= v.y;
        return this;
    }

    /**
     * Multiplies this vector by a scalar value
     *
     * @param s The scalar to multiply by
     * @return This vector for chaining
     */
    public Vector2f mul(float s) {
        this.x *= s;
        this.y *= s;
        return this;
    }

    /**
     * Linearly interpolates this vector toward target vector by alpha amount
     *
     * @param t The target vector
     * @param a The interpolation factor (0-1)
     * @return This vector for chaining
     */
    public Vector2f lerp(Vector2f t, float a) {
        this.x += (t.x - this.x) * a;
        this.y += (t.y - this.y) * a;
        return this;
    }

    /**
     * Creates and returns a copy of this vector
     *
     * @return A new vector with the same coordinates
     */
    public Vector2f copy() {
        return new Vector2f(x, y);
    }

    /**
     * Calculates the length (magnitude) of this vector.
     * The length is computed as the square root of the sum
     * of the squares of the x and y components.
     *
     * @return The length (magnitude) of this vector.
     */
    public float length() {
        return (float) Math.sqrt(x * x + y * y);
    }

    /**
     * Normalizes this vector to have a unit length of 1 while preserving its direction.
     * If the vector's length is zero, this method may result in an undefined behavior.
     *
     * @return A new vector that is the normalized version of this vector.
     */
    public Vector2f normalize() {
        float len = length();
        return new Vector2f(x / len, y / len);
    }

    /**
     * Rotates this 2D vector by the specified angle in radians and returns a new vector
     * with the rotated coordinates.
     *
     * @param angle the angle of rotation in radians
     * @return a new vector representing the result of the rotation
     */
    public Vector2f rotate(float angle) {
        float sin = (float) Math.sin(angle);
        float cos = (float) Math.cos(angle);
        return new Vector2f(cos * x - sin * y, sin * x + cos * y);
    }

    /**
     * Calculates the dot product of this vector with another vector.
     *
     * @param v The other vector to calculate the dot product with
     * @return The dot product of this vector and the given vector
     */
    public float dot(Vector2f v) {
        return x * v.x + y * v.y;
    }

    /**
     * Calculates the cross-product of this vector with another vector.
     * The cross-product in 2D is a scalar value, but here it is represented as a vector.
     *
     * @param v The other vector with which to calculate the cross-product.
     * @return A new vector representing the result of the cross-product.
     */
    public Vector2f cross(Vector2f v) {
        return new Vector2f(x * v.y - y * v.x, y * v.x - x * v.y);
    }

    /*
     * Clamps the x and y coordinates of this vector to the specified minimum and maximum values.
     *
     * @param min The minimum value to clamp to.
     * @param max The maximum value to clamp to.
     * @return A new vector with x and y coordinates clamped between the specified minimum and maximum limits.
     */
    public Vector2f clamp(float min, float max) {
        return new Vector2f(Math.max(min, Math.min(max, x)), Math.max(min, Math.min(max, y)));
    }

    /**
     * Clamps the x and y coordinates of this vector within the bounds defined by the given minimum and maximum vectors.
     * The resulting vector will have each coordinate individually clamped between the corresponding coordinates of the
     * minimum and maximum vectors.
     *
     * @param min The vector representing the minimum bounds for clamping.
     * @param max The vector representing the maximum bounds for clamping.
     * @return A new vector with x and y coordinates clamped between the corresponding values of the specified minimum
     * and maximum vectors.
     */
    public Vector2f clamp(Vector2f min, Vector2f max) {
        return new Vector2f(Math.max(min.x, Math.min(max.x, x)), Math.max(min.y, Math.min(max.y, y)));
    }

    /**
     * Clamps the x and y coordinates of this vector to the specified minimum value,
     * using {@code Float.MAX_VALUE} as the maximum limit.
     *
     * @param min The minimum value to clamp the vector's coordinates to.
     * @return A new vector with x and y coordinates clamped to the specified minimum limit
     * and {@code Float.MAX_VALUE} as the maximum limit.
     */
    public Vector2f clamp(float min) {
        return clamp(min, Float.MAX_VALUE);
    }

    /**
     * Clamps the x and y coordinates of this vector to the specified minimum vector.
     * The maximum limit for clamping is set to a vector with coordinates {@code Float.MAX_VALUE, Float.MAX_VALUE}.
     *
     * @param min The vector representing the minimum bounds for clamping.
     * @return A new vector with x and y coordinates clamped to the specified minimum vector
     * and a maximum vector of {@code Float.MAX_VALUE, Float.MAX_VALUE}.
     */
    public Vector2f clamp(Vector2f min) {
        return clamp(min, new Vector2f(Float.MAX_VALUE, Float.MAX_VALUE));
    }

    /**
     * Returns a string representation of this vector
     *
     * @return String in format "(x, y)"
     */
    @Override
    public String toString() {
        return "(" + x + ", " + y + ")";
    }
}