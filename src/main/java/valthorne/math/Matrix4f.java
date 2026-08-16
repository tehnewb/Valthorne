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
     * Sets up a perspective projection matrix.
     *
     * @param fovYRadians vertical field of view in radians
     * @param aspect aspect ratio (width / height)
     * @param near near clipping plane distance
     * @param far far clipping plane distance
     * @return this matrix for chaining
     */
    public Matrix4f perspective(float fovYRadians, float aspect, float near, float far) {
        identity();

        float f = 1f / (float) Math.tan(fovYRadians * 0.5f);

        m[0] = f / aspect;
        m[5] = f;
        m[10] = (far + near) / (near - far);
        m[11] = -1f;
        m[14] = (2f * far * near) / (near - far);
        m[15] = 0f;
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
     * Applies a rotation around the X axis.
     *
     * @param radians angle of rotation in radians
     * @return this matrix for chaining
     */
    public Matrix4f rotateX(float radians) {
        float c = (float) Math.cos(radians);
        float s = (float) Math.sin(radians);
        Matrix4f r = new Matrix4f();
        r.m[5] = c;
        r.m[6] = s;
        r.m[9] = -s;
        r.m[10] = c;
        return multiply(r);
    }

    /**
     * Applies a rotation around the Y axis.
     *
     * @param radians angle of rotation in radians
     * @return this matrix for chaining
     */
    public Matrix4f rotateY(float radians) {
        float c = (float) Math.cos(radians);
        float s = (float) Math.sin(radians);
        Matrix4f r = new Matrix4f();
        r.m[0] = c;
        r.m[2] = -s;
        r.m[8] = s;
        r.m[10] = c;
        return multiply(r);
    }

    /**
     * Sets this matrix to a look-at view transform.
     *
     * @param eyeX camera position x
     * @param eyeY camera position y
     * @param eyeZ camera position z
     * @param centerX target position x
     * @param centerY target position y
     * @param centerZ target position z
     * @param upX world up x
     * @param upY world up y
     * @param upZ world up z
     * @return this matrix for chaining
     */
    public Matrix4f lookAt(float eyeX, float eyeY, float eyeZ,
                           float centerX, float centerY, float centerZ,
                           float upX, float upY, float upZ) {
        float fx = centerX - eyeX;
        float fy = centerY - eyeY;
        float fz = centerZ - eyeZ;
        float fLen = (float) Math.sqrt(fx * fx + fy * fy + fz * fz);
        if (fLen == 0f) {
            return identity();
        }
        fx /= fLen;
        fy /= fLen;
        fz /= fLen;

        float upLen = (float) Math.sqrt(upX * upX + upY * upY + upZ * upZ);
        if (upLen == 0f) {
            upX = 0f;
            upY = 1f;
            upZ = 0f;
            upLen = 1f;
        }
        upX /= upLen;
        upY /= upLen;
        upZ /= upLen;

        float sx = fy * upZ - fz * upY;
        float sy = fz * upX - fx * upZ;
        float sz = fx * upY - fy * upX;
        float sLen = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
        if (sLen == 0f) {
            return identity();
        }
        sx /= sLen;
        sy /= sLen;
        sz /= sLen;

        float ux = sy * fz - sz * fy;
        float uy = sz * fx - sx * fz;
        float uz = sx * fy - sy * fx;

        identity();
        m[0] = sx;
        m[1] = ux;
        m[2] = -fx;

        m[4] = sy;
        m[5] = uy;
        m[6] = -fy;

        m[8] = sz;
        m[9] = uz;
        m[10] = -fz;

        m[12] = -(sx * eyeX + sy * eyeY + sz * eyeZ);
        m[13] = -(ux * eyeX + uy * eyeY + uz * eyeZ);
        m[14] = fx * eyeX + fy * eyeY + fz * eyeZ;
        return this;
    }

    /**
     * Multiplies this matrix with another matrix.
     *
     * @param b Matrix to multiply with
     * @return this matrix for chaining
     */
    public Matrix4f multiply(Matrix4f b) {
        if (b == null) throw new NullPointerException("b");
        float[] r = new float[16];
        multiply(this.m, b.m, r);
        this.m = r;
        return this;
    }

    /**
     * Sets this matrix to the product of {@code left * right}.
     *
     * @param left left matrix
     * @param right right matrix
     * @return this matrix for chaining
     */
    public Matrix4f setToProduct(Matrix4f left, Matrix4f right) {
        if (left == null) throw new NullPointerException("left");
        if (right == null) throw new NullPointerException("right");
        float[] r = new float[16];
        multiply(left.m, right.m, r);
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
     * Copies values from a raw float array into this matrix.
     *
     * @param values source matrix data in column-major order
     * @return this matrix for chaining
     */
    public Matrix4f set(float[] values) {
        if (values == null) throw new NullPointerException("values");
        if (values.length < 16) throw new IllegalArgumentException("values must contain at least 16 floats");
        System.arraycopy(values, 0, this.m, 0, 16);
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

    /**
     * Inverts this matrix in-place.
     *
     * @return this matrix for chaining
     * @throws IllegalStateException if the matrix is singular and cannot be inverted
     */
    public Matrix4f invert() {
        float[] inv = new float[16];
        float[] a = this.m;

        inv[0] = a[5] * a[10] * a[15] - a[5] * a[11] * a[14] - a[9] * a[6] * a[15]
                + a[9] * a[7] * a[14] + a[13] * a[6] * a[11] - a[13] * a[7] * a[10];
        inv[4] = -a[4] * a[10] * a[15] + a[4] * a[11] * a[14] + a[8] * a[6] * a[15]
                - a[8] * a[7] * a[14] - a[12] * a[6] * a[11] + a[12] * a[7] * a[10];
        inv[8] = a[4] * a[9] * a[15] - a[4] * a[11] * a[13] - a[8] * a[5] * a[15]
                + a[8] * a[7] * a[13] + a[12] * a[5] * a[11] - a[12] * a[7] * a[9];
        inv[12] = -a[4] * a[9] * a[14] + a[4] * a[10] * a[13] + a[8] * a[5] * a[14]
                - a[8] * a[6] * a[13] - a[12] * a[5] * a[10] + a[12] * a[6] * a[9];
        inv[1] = -a[1] * a[10] * a[15] + a[1] * a[11] * a[14] + a[9] * a[2] * a[15]
                - a[9] * a[3] * a[14] - a[13] * a[2] * a[11] + a[13] * a[3] * a[10];
        inv[5] = a[0] * a[10] * a[15] - a[0] * a[11] * a[14] - a[8] * a[2] * a[15]
                + a[8] * a[3] * a[14] + a[12] * a[2] * a[11] - a[12] * a[3] * a[10];
        inv[9] = -a[0] * a[9] * a[15] + a[0] * a[11] * a[13] + a[8] * a[1] * a[15]
                - a[8] * a[3] * a[13] - a[12] * a[1] * a[11] + a[12] * a[3] * a[9];
        inv[13] = a[0] * a[9] * a[14] - a[0] * a[10] * a[13] - a[8] * a[1] * a[14]
                + a[8] * a[2] * a[13] + a[12] * a[1] * a[10] - a[12] * a[2] * a[9];
        inv[2] = a[1] * a[6] * a[15] - a[1] * a[7] * a[14] - a[5] * a[2] * a[15]
                + a[5] * a[3] * a[14] + a[13] * a[2] * a[7] - a[13] * a[3] * a[6];
        inv[6] = -a[0] * a[6] * a[15] + a[0] * a[7] * a[14] + a[4] * a[2] * a[15]
                - a[4] * a[3] * a[14] - a[12] * a[2] * a[7] + a[12] * a[3] * a[6];
        inv[10] = a[0] * a[5] * a[15] - a[0] * a[7] * a[13] - a[4] * a[1] * a[15]
                + a[4] * a[3] * a[13] + a[12] * a[1] * a[7] - a[12] * a[3] * a[5];
        inv[14] = -a[0] * a[5] * a[14] + a[0] * a[6] * a[13] + a[4] * a[1] * a[14]
                - a[4] * a[2] * a[13] - a[12] * a[1] * a[6] + a[12] * a[2] * a[5];
        inv[3] = -a[1] * a[6] * a[11] + a[1] * a[7] * a[10] + a[5] * a[2] * a[11]
                - a[5] * a[3] * a[10] - a[9] * a[2] * a[7] + a[9] * a[3] * a[6];
        inv[7] = a[0] * a[6] * a[11] - a[0] * a[7] * a[10] - a[4] * a[2] * a[11]
                + a[4] * a[3] * a[10] + a[8] * a[2] * a[7] - a[8] * a[3] * a[6];
        inv[11] = -a[0] * a[5] * a[11] + a[0] * a[7] * a[9] + a[4] * a[1] * a[11]
                - a[4] * a[3] * a[9] - a[8] * a[1] * a[7] + a[8] * a[3] * a[5];
        inv[15] = a[0] * a[5] * a[10] - a[0] * a[6] * a[9] - a[4] * a[1] * a[10]
                + a[4] * a[2] * a[9] + a[8] * a[1] * a[6] - a[8] * a[2] * a[5];

        float det = a[0] * inv[0] + a[1] * inv[4] + a[2] * inv[8] + a[3] * inv[12];
        if (Math.abs(det) <= 1e-8f) {
            throw new IllegalStateException("Matrix is singular and cannot be inverted");
        }

        det = 1f / det;
        for (int i = 0; i < 16; i++) {
            inv[i] *= det;
        }

        this.m = inv;
        return this;
    }

    private static void multiply(float[] a, float[] b, float[] out) {
        for (int col = 0; col < 4; col++) {
            int colBase = col * 4;
            for (int row = 0; row < 4; row++) {
                out[colBase + row] =
                        a[row] * b[colBase]
                                + a[4 + row] * b[colBase + 1]
                                + a[8 + row] * b[colBase + 2]
                                + a[12 + row] * b[colBase + 3];
            }
        }
    }
}
