package valthorne.camera;

import valthorne.math.Matrix4f;
import valthorne.math.Ray3f;
import valthorne.math.Vector3f;

/**
 * Base class for 3D cameras that produce a combined projection-view matrix.
 *
 * <p>When used with Valthorne's current 2D renderers, draw calls are rendered on the
 * world XY plane at {@code z = 0}. That lets the existing 2D APIs participate in a
 * 3D scene while the camera handles perspective, orbiting, and picking.</p>
 */
public abstract class Camera3D {

    protected final Vector3f position = new Vector3f();
    protected final Vector3f direction = new Vector3f(0f, 0f, -1f);
    protected final Vector3f up = new Vector3f(0f, 1f, 0f);
    protected final Vector3f right = new Vector3f(1f, 0f, 0f);
    protected final Matrix4f projection = new Matrix4f();
    protected final Matrix4f view = new Matrix4f();
    protected final Matrix4f combined = new Matrix4f();
    protected final Matrix4f inverseCombined = new Matrix4f();
    protected float near = 0.1f;
    protected float far = 1000f;
    protected float viewportWidth = 1f;
    protected float viewportHeight = 1f;

    private final Vector3f tmpTarget = new Vector3f();
    private final Vector3f tmpNear = new Vector3f();
    private final Vector3f tmpFar = new Vector3f();

    public Vector3f getPosition() {
        return position;
    }

    public Vector3f getDirection() {
        return direction;
    }

    public Vector3f getUp() {
        return up;
    }

    public Vector3f getRight() {
        return right;
    }

    public Matrix4f getProjection() {
        return projection;
    }

    public Matrix4f getView() {
        return view;
    }

    public Matrix4f getCombined() {
        return combined;
    }

    public Matrix4f getInverseCombined() {
        return inverseCombined;
    }

    public float getNear() {
        return near;
    }

    public float getFar() {
        return far;
    }

    public float getViewportWidth() {
        return viewportWidth;
    }

    public float getViewportHeight() {
        return viewportHeight;
    }

    public void setPosition(float x, float y, float z) {
        position.set(x, y, z);
    }

    public void setDirection(float x, float y, float z) {
        direction.set(x, y, z);
        orthonormalizeBasis();
    }

    public void setUp(float x, float y, float z) {
        up.set(x, y, z);
        orthonormalizeBasis();
    }

    public void setClipPlanes(float near, float far) {
        if (near <= 0f) throw new IllegalArgumentException("near must be > 0");
        if (far <= near) throw new IllegalArgumentException("far must be > near");
        this.near = near;
        this.far = far;
    }

    public void move(float dx, float dy, float dz) {
        position.add(dx, dy, dz);
    }

    public void moveForward(float distance) {
        position.add(direction.getX() * distance, direction.getY() * distance, direction.getZ() * distance);
    }

    public void strafeRight(float distance) {
        position.add(right.getX() * distance, right.getY() * distance, right.getZ() * distance);
    }

    public void moveUp(float distance) {
        position.add(up.getX() * distance, up.getY() * distance, up.getZ() * distance);
    }

    public void lookAt(float targetX, float targetY, float targetZ) {
        direction.set(targetX - position.getX(), targetY - position.getY(), targetZ - position.getZ());
        orthonormalizeBasis();
    }

    public void yaw(float radians) {
        rotateVectorAroundAxis(direction, up, radians);
        rotateVectorAroundAxis(right, up, radians);
        orthonormalizeBasis();
    }

    public void pitch(float radians) {
        rotateVectorAroundAxis(direction, right, radians);
        rotateVectorAroundAxis(up, right, radians);
        orthonormalizeBasis();
    }

    public void roll(float radians) {
        rotateVectorAroundAxis(up, direction, radians);
        rotateVectorAroundAxis(right, direction, radians);
        orthonormalizeBasis();
    }

    public void rebuild(float viewportWidth, float viewportHeight) {
        if (viewportWidth <= 0f) throw new IllegalArgumentException("viewportWidth must be > 0");
        if (viewportHeight <= 0f) throw new IllegalArgumentException("viewportHeight must be > 0");

        this.viewportWidth = viewportWidth;
        this.viewportHeight = viewportHeight;

        orthonormalizeBasis();
        buildProjection(viewportWidth, viewportHeight);

        tmpTarget.set(position).add(direction);
        view.lookAt(
                position.getX(), position.getY(), position.getZ(),
                tmpTarget.getX(), tmpTarget.getY(), tmpTarget.getZ(),
                up.getX(), up.getY(), up.getZ()
        );

        combined.setToProduct(projection, view);
        inverseCombined.set(combined).invert();
    }

    public Vector3f project(Vector3f world, int viewportX, int viewportY, int viewportWidth, int viewportHeight, Vector3f out) {
        if (world == null) throw new NullPointerException("world");
        return project(world.getX(), world.getY(), world.getZ(), viewportX, viewportY, viewportWidth, viewportHeight, out);
    }

    public Vector3f project(float worldX, float worldY, float worldZ,
                            int viewportX, int viewportY, int viewportWidth, int viewportHeight,
                            Vector3f out) {
        if (out == null) throw new NullPointerException("out");
        float[] m = combined.get();

        float clipX = m[0] * worldX + m[4] * worldY + m[8] * worldZ + m[12];
        float clipY = m[1] * worldX + m[5] * worldY + m[9] * worldZ + m[13];
        float clipZ = m[2] * worldX + m[6] * worldY + m[10] * worldZ + m[14];
        float clipW = m[3] * worldX + m[7] * worldY + m[11] * worldZ + m[15];

        float invW = clipW == 0f ? 0f : 1f / clipW;
        float ndcX = clipX * invW;
        float ndcY = clipY * invW;
        float ndcZ = clipZ * invW;

        out.set(
                viewportX + (ndcX + 1f) * 0.5f * viewportWidth,
                viewportY + (ndcY + 1f) * 0.5f * viewportHeight,
                (ndcZ + 1f) * 0.5f
        );
        return out;
    }

    public Vector3f unproject(float screenX, float screenY, float depth,
                              int viewportX, int viewportY, int viewportWidth, int viewportHeight,
                              Vector3f out) {
        if (out == null) throw new NullPointerException("out");

        float ndcX = ((screenX - viewportX) / viewportWidth) * 2f - 1f;
        float ndcY = ((screenY - viewportY) / viewportHeight) * 2f - 1f;
        float ndcZ = depth * 2f - 1f;

        float[] m = inverseCombined.get();
        float worldX = m[0] * ndcX + m[4] * ndcY + m[8] * ndcZ + m[12];
        float worldY = m[1] * ndcX + m[5] * ndcY + m[9] * ndcZ + m[13];
        float worldZ = m[2] * ndcX + m[6] * ndcY + m[10] * ndcZ + m[14];
        float worldW = m[3] * ndcX + m[7] * ndcY + m[11] * ndcZ + m[15];

        if (worldW != 0f) {
            float invW = 1f / worldW;
            worldX *= invW;
            worldY *= invW;
            worldZ *= invW;
        }

        return out.set(worldX, worldY, worldZ);
    }

    public Ray3f screenPointToRay(float screenX, float screenY,
                                  int viewportX, int viewportY, int viewportWidth, int viewportHeight,
                                  Ray3f out) {
        if (out == null) throw new NullPointerException("out");

        unproject(screenX, screenY, 0f, viewportX, viewportY, viewportWidth, viewportHeight, tmpNear);
        unproject(screenX, screenY, 1f, viewportX, viewportY, viewportWidth, viewportHeight, tmpFar);
        tmpFar.sub(tmpNear).normalize();
        return out.set(tmpNear, tmpFar);
    }

    protected abstract void buildProjection(float viewportWidth, float viewportHeight);

    private void orthonormalizeBasis() {
        if (direction.lengthSquared() == 0f) {
            direction.set(0f, 0f, -1f);
        }
        direction.normalize();

        if (up.lengthSquared() == 0f) {
            up.set(0f, 1f, 0f);
        }
        up.normalize();

        right.setCross(direction, up);
        if (right.lengthSquared() < 1e-8f) {
            if (Math.abs(direction.getY()) > 0.999f) {
                up.set(0f, 0f, 1f);
            } else {
                up.set(0f, 1f, 0f);
            }
            right.setCross(direction, up);
        }

        right.normalize();
        up.setCross(right, direction).normalize();
    }

    private static void rotateVectorAroundAxis(Vector3f vector, Vector3f axis, float radians) {
        float ax = axis.getX();
        float ay = axis.getY();
        float az = axis.getZ();

        float axisLen = (float) Math.sqrt(ax * ax + ay * ay + az * az);
        if (axisLen == 0f) {
            return;
        }
        ax /= axisLen;
        ay /= axisLen;
        az /= axisLen;

        float vx = vector.getX();
        float vy = vector.getY();
        float vz = vector.getZ();
        float cos = (float) Math.cos(radians);
        float sin = (float) Math.sin(radians);
        float dot = vx * ax + vy * ay + vz * az;

        float rx = vx * cos + (ay * vz - az * vy) * sin + ax * dot * (1f - cos);
        float ry = vy * cos + (az * vx - ax * vz) * sin + ay * dot * (1f - cos);
        float rz = vz * cos + (ax * vy - ay * vx) * sin + az * dot * (1f - cos);

        vector.set(rx, ry, rz);
    }
}
