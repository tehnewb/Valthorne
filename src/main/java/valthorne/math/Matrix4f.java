package valthorne.math;

/**
 * Represents a 4x4 matrix commonly used in 3D graphics and mathematical operations.
 * Provides utility methods for common transformations such as translation,
 * scaling, rotation, orthogonal projection, and multiplication.
 */
public class Matrix4f {

    /**
     * The matrix elements stored in column-major order
     */
    public float[] m = new float[16];

    /**
     * Constructs a new identity matrix
     */
    public Matrix4f() {
        identity();
    }

    /**
     * Sets this matrix to the identity matrix.
     *
     * @return this matrix for chaining
     */
    public Matrix4f identity() {
        for (int i = 0; i < 16; i++) m[i] = 0;
        m[0] = m[5] = m[10] = m[15] = 1;
        return this;
    }

    /**
     * Sets up an orthographic projection matrix.
     *
     * @param left   Left clipping plane
     * @param right  Right clipping plane
     * @param bottom Bottom clipping plane
     * @param top    Top clipping plane
     * @param near   Near clipping plane
     * @param far    Far clipping plane
     * @return this matrix for chaining
     */
    public Matrix4f ortho(float left, float right, float bottom, float top, float near, float far) {
        identity();
        m[0] = 2f / (right - left);
        m[5] = 2f / (top - bottom);
        m[10] = -2f / (far - near);
        m[12] = -(right + left) / (right - left);
        m[13] = -(top + bottom) / (top - bottom);
        m[14] = -(far + near) / (far - near);
        return this;
    }

    /**
     * Applies a translation transformation.
     *
     * @param x Translation in x direction
     * @param y Translation in y direction
     * @param z Translation in z direction
     * @return this matrix for chaining
     */
    public Matrix4f translate(float x, float y, float z) {
        Matrix4f t = new Matrix4f();
        t.m[12] = x;
        t.m[13] = y;
        t.m[14] = z;
        return multiply(t);
    }

    /**
     * Applies a uniform scale transformation.
     *
     * @param s Scale factor to apply in all directions
     * @return this matrix for chaining
     */
    public Matrix4f scale(float s) {
        Matrix4f t = new Matrix4f();
        t.m[0] = s;
        t.m[5] = s;
        t.m[10] = s;
        return multiply(t);
    }

    /**
     * Applies a rotation around the Z axis.
     *
     * @param radians Angle of rotation in radians
     * @return this matrix for chaining
     */
    public Matrix4f rotateZ(float radians) {
        float c = (float) Math.cos(radians);
        float s = (float) Math.sin(radians);
        Matrix4f r = new Matrix4f();
        r.m[0] = c;
        r.m[1] = s;
        r.m[4] = -s;
        r.m[5] = c;
        return multiply(r);
    }

    /**
     * Multiplies this matrix with another matrix.
     *
     * @param b Matrix to multiply with
     * @return this matrix for chaining
     */
    public Matrix4f multiply(Matrix4f b) {
        float[] a = this.m;
        float[] bb = b.m;
        float[] r = new float[16];
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 4; col++) {
                r[col + row * 4] = a[row * 4] * bb[col] + a[row * 4 + 1] * bb[col + 4] + a[row * 4 + 2] * bb[col + 8] + a[row * 4 + 3] * bb[col + 12];
            }
        }
        this.m = r;
        return this;
    }

    /**
     * Copies values from another matrix into this matrix.
     *
     * @param other Matrix to copy from
     * @return this matrix for chaining
     */
    public Matrix4f set(Matrix4f other) {
        System.arraycopy(other.m, 0, this.m, 0, 16);
        return this;
    }

    /**
     * Creates a copy of this matrix.
     *
     * @return A new matrix containing a copy of this matrix
     */
    public Matrix4f copy() {
        Matrix4f c = new Matrix4f();
        c.set(this);
        return c;
    }

    /**
     * Returns the internal array of matrix elements.
     *
     * @return The matrix elements array
     */
    public float[] get() {
        return m;
    }

    /**
     * Copies the matrix elements into the provided array.
     *
     * @param dest Destination array for matrix elements
     */
    public void get(float[] dest) {
        System.arraycopy(m, 0, dest, 0, 16);
    }

    /**
     * Applies a non-uniform scale transformation.
     *
     * @param x Scale factor in x-direction
     * @param y Scale factor in y-direction
     * @param z Scale factor in z-direction
     * @return this matrix for chaining
     */
    public Matrix4f scale(float x, float y, float z) {
        Matrix4f t = new Matrix4f();
        t.m[0] = x;
        t.m[5] = y;
        t.m[10] = z;
        return multiply(t);
    }
}