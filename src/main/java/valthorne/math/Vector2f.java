package valthorne.math;

/**
 * A 2D vector class that provides common operations for working with vectors
 * in two-dimensional space. It contains methods for basic vector arithmetic,
 * transformation, and utility functions.
 *
 * @author Albert Beaupre
 * @since October 15th, 2025
 */
public class Vector2f {

    /**
     * The x-coordinate of this vector.
     */
    private float x;

    /**
     * The y-coordinate of this vector.
     */
    private float y;

    /**
     * Creates a zero vector (0,0).
     */
    public Vector2f() {
        this(0, 0);
    }

    /**
     * Creates a zero vector (0,0).
     */
    public Vector2f(Vector2f vector) {
        this(vector.x, vector.y);
    }

    /**
     * Creates a vector with the given coordinates.
     *
     * @param x The x-coordinate
     * @param y The y-coordinate
     */
    public Vector2f(float x, float y) {
        this.x = x;
        this.y = y;
    }

    /**
     * Negates this vector in-place.
     *
     * @return This vector for chaining.
     */
    public Vector2f negate() {
        this.x = -this.x;
        this.y = -this.y;
        return this;
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
     * Sets this vector's coordinates to the same as the specified vector.
     *
     * @param vector The vector whose coordinates will be copied to this vector.
     * @return This vector for chaining.
     */
    public Vector2f set(Vector2f vector) {
        this.x = vector.getX();
        this.y = vector.getY();
        return this;
    }

    /**
     * Sets this vector's coordinates to the specified values.
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
     * Adds the specified offsets to this vector's coordinates.
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
     * Adds another vector to this vector.
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
     * Subtracts another vector from this vector.
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
     * Multiplies this vector by a scalar value.
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
     * Linearly interpolates this vector toward target vector by alpha amount.
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
     * Copies the coordinates of {@code other} into this vector.
     *
     * <p>This replaces the old allocation-based {@code copy()} usage in hot paths.</p>
     *
     * @param other The vector to copy from
     * @return This vector for chaining
     */
    public Vector2f copy(Vector2f other) {
        this.x = other.x;
        this.y = other.y;
        return this;
    }

    /**
     * Calculates the length (magnitude) of this vector.
     *
     * @return The length (magnitude) of this vector.
     */
    public float length() {
        return (float) Math.sqrt(x * x + y * y);
    }

    /**
     * Normalizes this vector in-place to have a unit length of 1 while preserving its direction.
     *
     * <p>If the vector length is zero, this method leaves the vector unchanged.</p>
     *
     * @return This vector for chaining.
     */
    public Vector2f normalize() {
        float len = length();
        if (len != 0.0f) {
            x /= len;
            y /= len;
        }
        return this;
    }

    /**
     * Rotates this vector in-place by the specified angle in radians.
     *
     * @param angle the angle of rotation in radians
     * @return This vector for chaining
     */
    public Vector2f rotate(float angle) {
        float sin = (float) Math.sin(angle);
        float cos = (float) Math.cos(angle);

        float nx = x * cos - y * sin;
        float ny = x * sin + y * cos;

        this.x = nx;
        this.y = ny;
        return this;
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
     * Calculates the 2D cross product (scalar) of this vector with another vector.
     *
     * <p>
     * In 2D physics, the cross product of two vectors is a scalar:
     * {@code cross(a,b) = a.x*b.y - a.y*b.x}.
     * </p>
     *
     * @param v The other vector
     * @return The scalar cross product
     */
    public float cross(Vector2f v) {
        return x * v.y - y * v.x;
    }

    /**
     * Clamps this vector's x and y coordinates to the specified minimum and maximum values in-place.
     *
     * @param min The minimum value to clamp to.
     * @param max The maximum value to clamp to.
     * @return This vector for chaining.
     */
    public Vector2f clamp(float min, float max) {
        this.x = Math.max(min, Math.min(max, this.x));
        this.y = Math.max(min, Math.min(max, this.y));
        return this;
    }

    /**
     * Clamps this vector's x and y coordinates within the bounds defined by the given minimum and maximum vectors.
     *
     * @param min The vector representing the minimum bounds for clamping.
     * @param max The vector representing the maximum bounds for clamping.
     * @return This vector for chaining.
     */
    public Vector2f clamp(Vector2f min, Vector2f max) {
        this.x = Math.max(min.x, Math.min(max.x, this.x));
        this.y = Math.max(min.y, Math.min(max.y, this.y));
        return this;
    }

    /**
     * Clamps this vector's x and y coordinates to the specified minimum value,
     * using {@code Float.MAX_VALUE} as the maximum limit.
     *
     * @param min The minimum value to clamp the vector's coordinates to.
     * @return This vector for chaining.
     */
    public Vector2f clamp(float min) {
        return clamp(min, Float.MAX_VALUE);
    }

    /**
     * Returns a string representation of this vector.
     *
     * @return String in format "(x, y)"
     */
    @Override
    public String toString() {
        return "(" + x + ", " + y + ")";
    }
}