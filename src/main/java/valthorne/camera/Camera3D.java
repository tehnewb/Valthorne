package valthorne.camera;

import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.joml.primitives.Rayf;
import org.joml.Vector3f;

/**
 * Owns a mutable camera pose, projection and view matrices, their combined inverse,
 * and a frustum for projection, picking, and culling. Subclasses supply the projection
 * model. Pose setters change vectors immediately but derived matrices are refreshed
 * only by {@link #rebuild(float, float)}.
 *
 * <pre>{@code
 * PerspectiveCamera camera = new PerspectiveCamera();
 * camera.setPosition(4, 4, 3);
 * camera.lookAt(0, 0, 0, 0, 0, 1);
 * camera.rebuild(1280, 720);
 * Rayf ray = camera.screenPointToRay(640, 360, 0, 0, 1280, 720, new Rayf());
 * }</pre>
 *
 * <p>Projection utilities use bottom-left viewport coordinates and normalized depth
 * zero through one. Convert top-left input coordinates before calling them. Picking
 * rays begin on the near plane, not at the camera position. Mutable accessors return
 * owned storage; do not modify derived matrices independently or retain them as
 * snapshots. Scratch vectors make concurrent operations on one camera unsafe.</p>
 *
 * <p>When used with Valthorne's current 2D renderers, draw calls are rendered on the
 * world XY plane at {@code z = 0}. That lets the existing 2D APIs participate in a
 * 3D scene while the camera handles perspective, orbiting, and picking.</p>
 *
 * @author Albert Beaupre
 *
 * @author Albert Beaupre
 */
public abstract class Camera3D {

    protected final Vector3f position = new Vector3f(); // World-space eye position; edits require a rebuild.
    protected final Vector3f direction = new Vector3f(0f, 0f, -1f); // Forward direction, initially negative Z.
    protected final Vector3f up = new Vector3f(0f, 1f, 0f); // View-up vector, initially positive Y.
    protected final Vector3f right = new Vector3f(1f, 0f, 0f); // Right vector derived from direction cross up.
    protected final Matrix4f projection = new Matrix4f(); // Owned projection matrix last produced by rebuild.
    protected final Matrix4f view = new Matrix4f(); // Owned world-to-view matrix last produced by rebuild.
    protected final Matrix4f combined = new Matrix4f(); // Owned projection-times-view matrix.
    protected final Matrix4f inverseCombined = new Matrix4f(); // Owned inverse used for unprojection.
    protected final FrustumIntersection frustum = new FrustumIntersection(); // Owned culling planes extracted from the combined matrix.
    private final Vector3f tmpTarget = new Vector3f(); // Reusable look-at target computed from position and direction.
    private final Vector3f tmpNear = new Vector3f(); // Reusable world-space near-plane picking point.
    private final Vector3f tmpFar = new Vector3f(); // Reusable far-plane picking point and ray direction.
    protected float near = 0.1f; // Near clip distance in world units, initially 0.1.
    protected float far = 1000f; // Far clip distance in world units, initially 1000.
    protected float viewportWidth = 1f; // Width passed to the most recent rebuild, initially one.
    protected float viewportHeight = 1f; // Height passed to the most recent rebuild, initially one.

    /**
     * Rotates a vector in place with JOML using a locally normalized axis.
     * A zero-length axis leaves the vector unchanged. Component snapshots permit
     * the vector and axis to reference the same object.
     *
     * @param vector  mutable vector to rotate
     * @param axis    rotation axis, not modified unless it aliases vector
     * @param radians signed rotation angle in radians
     */
    private static void rotateVectorAroundAxis(Vector3f vector, Vector3f axis, float radians) {
        float length = axis.length();
        if (length == 0f) return;
        vector.rotateAxis(radians, axis.x() / length, axis.y() / length, axis.z() / length);
    }

    /**
     * Returns the live world-space eye position owned by this camera. Make a copy when
     * a snapshot is needed; derived state reflects the last rebuild, and direct edits
     * do not synchronize the other camera values.
     *
     * @return the mutable world-space eye position
     */
    public Vector3f getPosition() {
        return position;
    }

    /**
     * Returns the live forward basis vector owned by this camera. Make a copy when
     * a snapshot is needed; derived state reflects the last rebuild, and direct edits
     * do not synchronize the other camera values.
     *
     * @return the mutable forward basis vector
     */
    public Vector3f getDirection() {
        return direction;
    }

    /**
     * Returns the live view-up basis vector owned by this camera. Make a copy when
     * a snapshot is needed; derived state reflects the last rebuild, and direct edits
     * do not synchronize the other camera values.
     *
     * @return the mutable view-up basis vector
     */
    public Vector3f getUp() {
        return up;
    }

    /**
     * Returns the live right basis vector owned by this camera. Make a copy when
     * a snapshot is needed; derived state reflects the last rebuild, and direct edits
     * do not synchronize the other camera values.
     *
     * @return the mutable right basis vector
     */
    public Vector3f getRight() {
        return right;
    }

    /**
     * Returns the live projection matrix owned by this camera. Make a copy when
     * a snapshot is needed; derived state reflects the last rebuild, and direct edits
     * do not synchronize the other camera values.
     *
     * @return the mutable projection matrix
     */
    public Matrix4f getProjection() {
        return projection;
    }

    /**
     * Returns the live view matrix owned by this camera. Make a copy when
     * a snapshot is needed; derived state reflects the last rebuild, and direct edits
     * do not synchronize the other camera values.
     *
     * @return the mutable view matrix
     */
    public Matrix4f getView() {
        return view;
    }

    /**
     * Returns the live projection-times-view matrix owned by this camera. Make a copy when
     * a snapshot is needed; derived state reflects the last rebuild, and direct edits
     * do not synchronize the other camera values.
     *
     * @return the mutable projection-times-view matrix
     */
    public Matrix4f getCombined() {
        return combined;
    }

    /**
     * Returns the live inverse combined matrix owned by this camera. Make a copy when
     * a snapshot is needed; derived state reflects the last rebuild, and direct edits
     * do not synchronize the other camera values.
     *
     * @return the mutable inverse combined matrix
     */
    public Matrix4f getInverseCombined() {
        return inverseCombined;
    }

    /**
     * Returns the live culling frustum owned by this camera. Make a copy when
     * a snapshot is needed; derived state reflects the last rebuild, and direct edits
     * do not synchronize the other camera values.
     *
     * @return the mutable culling frustum
     */
    public FrustumIntersection getFrustum() {
        return frustum;
    }

    /**
     * Returns the configured near clip distance in world units. Reading it does not
     * rebuild the camera or validate the current matrices.
     *
     * @return near clip distance in world units
     */
    public float getNear() {
        return near;
    }

    /**
     * Returns the configured far clip distance in world units. Reading it does not
     * rebuild the camera or validate the current matrices.
     *
     * @return far clip distance in world units
     */
    public float getFar() {
        return far;
    }

    /**
     * Returns the configured viewport width retained by the last rebuild. Reading it does not
     * rebuild the camera or validate the current matrices.
     *
     * @return viewport width retained by the last rebuild
     */
    public float getViewportWidth() {
        return viewportWidth;
    }

    /**
     * Returns the configured viewport height retained by the last rebuild. Reading it does not
     * rebuild the camera or validate the current matrices.
     *
     * @return viewport height retained by the last rebuild
     */
    public float getViewportHeight() {
        return viewportHeight;
    }

    /**
     * Replaces the world-space eye position without changing orientation or rebuilding
     * matrices. Components are stored without finiteness validation.
     *
     * @param x X component
     * @param y Y component
     * @param z Z component
     */
    public void setPosition(float x, float y, float z) {
        position.set(x, y, z);
    }

    /**
     * Replaces the forward direction and reconstructs an orthonormal basis using the
     * current up hint. A zero direction falls back to negative Z; parallel up vectors
     * are replaced with a usable axis. Rebuild before projecting or drawing.
     *
     * @param x X component
     * @param y Y component
     * @param z Z component
     */
    public void setDirection(float x, float y, float z) {
        direction.set(x, y, z);
        orthonormalizeBasis();
    }

    /**
     * Supplies a view-up hint and reconstructs the basis around the current direction.
     * Zero or parallel hints are replaced as needed, so the resulting up vector may
     * differ from the supplied vector. Matrices are not rebuilt.
     *
     * @param x X component
     * @param y Y component
     * @param z Z component
     */
    public void setUp(float x, float y, float z) {
        up.set(x, y, z);
        orthonormalizeBasis();
    }

    /**
     * Stores clip distances after checking their order. Supply finite values with
     * positive near and far greater than near; comparisons do not separately reject
     * NaN. Rebuild to apply the distances to projection and culling.
     *
     * @param near near distance in world units
     * @param far  far distance in world units
     * @throws IllegalArgumentException if near is nonpositive or far is at most near
     */
    public void setClipPlanes(float near, float far) {
        if (near <= 0f) throw new IllegalArgumentException("near must be > 0");
        if (far <= near) throw new IllegalArgumentException("far must be > near");
        this.near = near;
        this.far = far;
    }

    /**
     * Adds a world-space offset to the eye position while preserving orientation.
     * The derived view and projection state is unchanged until rebuild.
     *
     * @param dx world X displacement
     * @param dy world Y displacement
     * @param dz world Z displacement
     */
    public void move(float dx, float dy, float dz) {
        position.add(dx, dy, dz);
    }

    /**
     * Moves the eye along the current forward basis vector without rebuilding matrices.
     * Distance is in world units when that vector is normalized; direct accessor edits
     * can change its length and therefore the effective displacement.
     *
     * @param distance signed displacement; negative values move in the opposite direction
     */
    public void moveForward(float distance) {
        position.add(direction.x() * distance, direction.y() * distance, direction.z() * distance);
    }

    /**
     * Moves the eye along the current right basis vector without rebuilding matrices.
     * Distance is in world units when that vector is normalized; direct accessor edits
     * can change its length and therefore the effective displacement.
     *
     * @param distance signed displacement; negative values move in the opposite direction
     */
    public void strafeRight(float distance) {
        position.add(right.x() * distance, right.y() * distance, right.z() * distance);
    }

    /**
     * Moves the eye along the current up basis vector without rebuilding matrices.
     * Distance is in world units when that vector is normalized; direct accessor edits
     * can change its length and therefore the effective displacement.
     *
     * @param distance signed displacement; negative values move in the opposite direction
     */
    public void moveUp(float distance) {
        position.add(up.x() * distance, up.y() * distance, up.z() * distance);
    }

    /**
     * Aims from the current eye toward a world-space point, rebuilding the basis with
     * the current up hint. A coincident target falls back to negative Z. This changes
     * orientation only; call rebuild to refresh matrices.
     *
     * @param targetX target world X coordinate
     * @param targetY target world Y coordinate
     * @param targetZ target world Z coordinate
     */
    public void lookAt(float targetX, float targetY, float targetZ) {
        direction.set(targetX - position.x(), targetY - position.y(), targetZ - position.z());
        orthonormalizeBasis();
    }

    /**
     * Aims toward a world-space point using an explicit up hint. The hint is normalized
     * and made perpendicular to the viewing direction, with a fallback for degenerate
     * inputs. Eye position is preserved and matrices are not rebuilt.
     *
     * @param targetX target world X coordinate
     * @param targetY target world Y coordinate
     * @param targetZ target world Z coordinate
     * @param upX     up-hint X component
     * @param upY     up-hint Y component
     * @param upZ     up-hint Z component
     */
    public void lookAt(float targetX, float targetY, float targetZ, float upX, float upY, float upZ) {
        up.set(upX, upY, upZ);
        direction.set(targetX - position.x(), targetY - position.y(), targetZ - position.z());
        orthonormalizeBasis();
    }

    /**
     * Rotates orientation around the current up axis using Rodrigues' formula,
     * then reconstructs an orthonormal basis. Position is preserved; rebuild before
     * using derived matrices or the frustum.
     *
     * @param radians signed right-handed rotation angle in radians
     */
    public void yaw(float radians) {
        rotateVectorAroundAxis(direction, up, radians);
        rotateVectorAroundAxis(right, up, radians);
        orthonormalizeBasis();
    }

    /**
     * Rotates orientation around the current right axis using Rodrigues' formula,
     * then reconstructs an orthonormal basis. Position is preserved; rebuild before
     * using derived matrices or the frustum.
     *
     * @param radians signed right-handed rotation angle in radians
     */
    public void pitch(float radians) {
        rotateVectorAroundAxis(direction, right, radians);
        rotateVectorAroundAxis(up, right, radians);
        orthonormalizeBasis();
    }

    /**
     * Rotates orientation around the current forward axis using Rodrigues' formula,
     * then reconstructs an orthonormal basis. Position is preserved; rebuild before
     * using derived matrices or the frustum.
     *
     * @param radians signed right-handed rotation angle in radians
     */
    public void roll(float radians) {
        rotateVectorAroundAxis(up, direction, radians);
        rotateVectorAroundAxis(right, direction, radians);
        orthonormalizeBasis();
    }

    /**
     * Refreshes the basis, subclass projection, view matrix, combined matrix, inverse,
     * and frustum in that order using the supplied viewport aspect ratio. This must
     * follow pose or projection changes before drawing, culling, or picking. Supply
     * finite positive dimensions; only nonpositive dimensions are explicitly rejected.
     *
     * @param viewportWidth  viewport width in units matching viewportHeight
     * @param viewportHeight viewport height in units matching viewportWidth
     * @throws IllegalArgumentException if either dimension is nonpositive
     */
    public void rebuild(float viewportWidth, float viewportHeight) {
        if (viewportWidth <= 0f) throw new IllegalArgumentException("viewportWidth must be > 0");
        if (viewportHeight <= 0f) throw new IllegalArgumentException("viewportHeight must be > 0");

        this.viewportWidth = viewportWidth;
        this.viewportHeight = viewportHeight;

        orthonormalizeBasis();
        buildProjection(viewportWidth, viewportHeight);

        tmpTarget.set(position).add(direction);
        view.setLookAt(position.x(), position.y(), position.z(), tmpTarget.x(), tmpTarget.y(), tmpTarget.z(), up.x(), up.y(), up.z());

        projection.mul(view, combined);
        if (!combined.isFinite() || Math.abs(combined.determinant()) <= 1e-8f)
            throw new IllegalStateException("Singular camera transform");
        inverseCombined.set(combined).invert();
        frustum.set(combined);
    }

    /**
     * Projects a world-space point through the last rebuilt combined matrix. Output X/Y
     * use bottom-left screen coordinates and Z is normalized depth; results are not
     * clipped to the viewport. The output may alias the input vector.
     *
     * @param world          point to project
     * @param viewportX      left viewport origin in screen units
     * @param viewportY      bottom viewport origin in screen units
     * @param viewportWidth  viewport width in screen units
     * @param viewportHeight viewport height in screen units
     * @param out            destination vector overwritten with screen X/Y and depth
     * @return out
     * @throws NullPointerException if world or out is null
     */
    public Vector3f project(Vector3f world, int viewportX, int viewportY, int viewportWidth, int viewportHeight, Vector3f out) {
        if (world == null) throw new NullPointerException("world");
        return project(world.x(), world.y(), world.z(), viewportX, viewportY, viewportWidth, viewportHeight, out);
    }

    /**
     * Projects coordinates with the current combined matrix and maps normalized device
     * coordinates to the supplied viewport. No clipping or visibility test is performed.
     * For zero homogeneous W the inverse is treated as zero, mapping finite clip
     * coordinates to the viewport center and depth 0.5. Dimensions are not validated.
     *
     * @param worldX         world X coordinate
     * @param worldY         world Y coordinate
     * @param worldZ         world Z coordinate
     * @param viewportX      left viewport origin in screen units
     * @param viewportY      bottom viewport origin in screen units
     * @param viewportWidth  viewport width in screen units
     * @param viewportHeight viewport height in screen units
     * @param out            destination for bottom-left screen X/Y and normalized depth
     * @return out
     * @throws NullPointerException if out is null
     */
    public Vector3f project(float worldX, float worldY, float worldZ, int viewportX, int viewportY, int viewportWidth, int viewportHeight, Vector3f out) {
        if (out == null) throw new NullPointerException("out");
        Matrix4f m = combined;

        float clipX = m.m00() * worldX + m.m10() * worldY + m.m20() * worldZ + m.m30();
        float clipY = m.m01() * worldX + m.m11() * worldY + m.m21() * worldZ + m.m31();
        float clipZ = m.m02() * worldX + m.m12() * worldY + m.m22() * worldZ + m.m32();
        float clipW = m.m03() * worldX + m.m13() * worldY + m.m23() * worldZ + m.m33();

        float invW = clipW == 0f ? 0f : 1f / clipW;
        float ndcX = clipX * invW;
        float ndcY = clipY * invW;
        float ndcZ = clipZ * invW;

        out.set(viewportX + (ndcX + 1f) * 0.5f * viewportWidth, viewportY + (ndcY + 1f) * 0.5f * viewportHeight, (ndcZ + 1f) * 0.5f);
        return out;
    }

    /**
     * Transforms a bottom-left screen position and normalized depth using the inverse
     * from the last rebuild. Depth zero is the near plane and one is the far plane;
     * outside values extrapolate. A zero homogeneous W skips division. Viewport
     * dimensions are not validated and must be nonzero for meaningful results.
     *
     * @param screenX        horizontal screen coordinate
     * @param screenY        vertical coordinate measured upward
     * @param depth          normalized depth
     * @param viewportX      left viewport origin in screen units
     * @param viewportY      bottom viewport origin in screen units
     * @param viewportWidth  viewport width in screen units
     * @param viewportHeight viewport height in screen units
     * @param out            destination world-space point
     * @return out
     * @throws NullPointerException if out is null
     */
    public Vector3f unproject(float screenX, float screenY, float depth, int viewportX, int viewportY, int viewportWidth, int viewportHeight, Vector3f out) {
        if (out == null) throw new NullPointerException("out");

        float ndcX = ((screenX - viewportX) / viewportWidth) * 2f - 1f;
        float ndcY = ((screenY - viewportY) / viewportHeight) * 2f - 1f;
        float ndcZ = depth * 2f - 1f;

        Matrix4f m = inverseCombined;
        float worldX = m.m00() * ndcX + m.m10() * ndcY + m.m20() * ndcZ + m.m30();
        float worldY = m.m01() * ndcX + m.m11() * ndcY + m.m21() * ndcZ + m.m31();
        float worldZ = m.m02() * ndcX + m.m12() * ndcY + m.m22() * ndcZ + m.m32();
        float worldW = m.m03() * ndcX + m.m13() * ndcY + m.m23() * ndcZ + m.m33();

        if (worldW != 0f) {
            float invW = 1f / worldW;
            worldX *= invW;
            worldY *= invW;
            worldZ *= invW;
        }

        return out.set(worldX, worldY, worldZ);
    }

    /**
     * Unprojects a screen point at near and far depth and writes a normalized ray
     * between them. Its origin is the near-plane point for both perspective and
     * orthographic cameras. Uses shared scratch vectors and the last rebuilt inverse.
     *
     * @param screenX        horizontal screen coordinate
     * @param screenY        bottom-left vertical screen coordinate
     * @param viewportX      left viewport origin in screen units
     * @param viewportY      bottom viewport origin in screen units
     * @param viewportWidth  viewport width in screen units
     * @param viewportHeight viewport height in screen units
     * @param out            ray whose origin and direction will be replaced
     * @return out
     * @throws NullPointerException if out is null
     */
    public Rayf screenPointToRay(float screenX, float screenY, int viewportX, int viewportY, int viewportWidth, int viewportHeight, Rayf out) {
        if (out == null) throw new NullPointerException("out");

        unproject(screenX, screenY, 0f, viewportX, viewportY, viewportWidth, viewportHeight, tmpNear);
        unproject(screenX, screenY, 1f, viewportX, viewportY, viewportWidth, viewportHeight, tmpFar);
        tmpFar.sub(tmpNear).normalize();
        out.oX = tmpNear.x(); out.oY = tmpNear.y(); out.oZ = tmpNear.z();
        out.dX = tmpFar.x(); out.dY = tmpFar.y(); out.dZ = tmpFar.z();
        return out;
    }

    /**
     * Replaces the owned projection matrix for the subclass's projection model.
     * Called during rebuild after basis correction and before combined matrices are
     * computed. Implementations should use the configured clip planes and supplied
     * aspect ratio without replacing the owned matrix reference.
     *
     * @param viewportWidth  width supplied to rebuild
     * @param viewportHeight height supplied to rebuild
     */
    protected abstract void buildProjection(float viewportWidth, float viewportHeight);

    /**
     * Normalizes direction and up, computes right as direction cross up, then derives
     * a perpendicular up. Zero direction falls back to negative Z; zero up falls back
     * to positive Y. A nearly parallel pair uses Z or Y as an alternate hint. Non-finite
     * inputs are not repaired. Updates vectors only, leaving matrices unchanged.
     */
    private void orthonormalizeBasis() {
        if (direction.lengthSquared() == 0f) {
            direction.set(0f, 0f, -1f);
        }
        direction.normalize();

        if (up.lengthSquared() == 0f) {
            up.set(0f, 1f, 0f);
        }
        up.normalize();

        direction.cross(up, right);
        if (right.lengthSquared() < 1e-8f) {
            if (Math.abs(direction.y()) > 0.999f) {
                up.set(0f, 0f, 1f);
            } else {
                up.set(0f, 1f, 0f);
            }
            direction.cross(up, right);
        }

        right.normalize();
        right.cross(direction, up).normalize();
    }
}
