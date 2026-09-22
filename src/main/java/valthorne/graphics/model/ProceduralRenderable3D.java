package valthorne.graphics.model;

import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.primitives.AABBf;
import valthorne.camera.Camera3D;
import org.joml.Matrix3f;

/**
 * A procedurally emitted mesh with a material, affine transform, and conservative
 * frustum culling. The emitter supplies world-space vertices; use this object's
 * point and normal transformation helpers when the generated geometry starts in
 * local coordinates. Set local bounds large enough to enclose that geometry.
 * An absent emitter, invalid bounds, or a cleared visibility flag prevents normal
 * visibility checks from accepting the renderable.
 * <p>
 * Position, scale, and local bounds are exposed as live mutable objects. Transform
 * evaluation notices direct position and scale edits, and world bounds are rebuilt
 * from all eight local corners when requested. Material and emitter references
 * are borrowed. This class owns no GPU resources and is not thread-safe.
 * </p>
 * <pre>{@code
 * ProceduralRenderable3D surface = new ProceduralRenderable3D()
 *         .setEmitter(emitter)
 *         .setLocalBounds(-1, -1, 0, 1, 1, 1)
 *         .setPosition(4, 2, 0)
 *         .setScale(2);
 * Vector3f worldPoint = surface.transform(new Vector3f(0, 0, 1), new Vector3f());
 * }</pre>
 *
 * @author Albert Beaupre
 */
public final class ProceduralRenderable3D implements MeshRenderable3D {

    private final Vector3f position = new Vector3f(); // Live parent-space translation, checked during transform evaluation.
    private final Vector3f scale = new Vector3f(1f, 1f, 1f); // Live nonzero local scale, checked during transform evaluation.
    private final AABBf localBounds = new AABBf(); // Live local bounds that must enclose emitted geometry.
    private final AABBf worldBounds = new AABBf(); // Reusable world-space axis-aligned bounds rebuilt on access.
    private final Vector3f boundsScratch = new Vector3f(); // Scratch vector for transforming one local bounds corner.
    private final Matrix4f rotationMatrix = new Matrix4f(); // Scratch matrix for applying a copied quaternion rotation.
    private final Matrix4f parentTransform = new Matrix4f(); // Copied parent affine transform applied before local placement.
    private final Matrix3f normalTransform = new Matrix3f(); // Scratch inverse-transpose matrix for normal transformation.
    private final Matrix4f worldTransform = new Matrix4f(); // Cached parent times translation times rotation times scale matrix.
    private final float[] lastTransform = new float[9]; // Last evaluated position, scale, and Euler components.
    private Material3D material = new Material3D(); // Borrowed material used for geometry submission.
    private ProceduralMeshEmitter3D emitter; // Borrowed callback that generates world-space geometry.
    private boolean visible = true; // Application visibility gate used by frustum checks.
    private float yawRadians; // Stored Z-axis Euler rotation in radians.
    private float rotationX, rotationY; // Stored X and Y Euler rotations in radians.
    private Quaternionf quaternion; // Copied normalized orientation, or null when Euler mode is active.
    private boolean transformValid; // Whether the cached world matrix matches the last evaluated components.
    private boolean worldBoundsDirty = true; // Bounds invalidation marker; bounds are currently rebuilt on every access.

    /**
     * Computes a conservative margin around transformed bounds to reduce premature
     * edge culling. Uses five percent of the largest extent, with a minimum of two
     * world units.
     *
     * @param bounds valid world-space bounds
     * @return outward padding in world units
     */
    private static float cullPadding(AABBf bounds) {
        float maxExtent = Math.max((bounds.maxX - bounds.minX), Math.max((bounds.maxY - bounds.minY), (bounds.maxZ - bounds.minZ)));
        return Math.max(2f, maxExtent * 0.05f);
    }

    /**
     * Copies and normalizes an orientation, switching subsequent transform evaluation
     * to quaternion mode. The caller may freely modify the supplied quaternion
     * after this call. Existing Euler values remain stored but are ignored until
     * an Euler setter is used.
     *
     * @param rotation finite, nonzero quaternion
     * @return this renderable
     * @throws NullPointerException     if rotation is null
     * @throws IllegalArgumentException if the quaternion is zero or nonfinite
     */
    public ProceduralRenderable3D setRotation(Quaternionf rotation) {
        quaternion = ModelInstance3D.copyRotation(rotation, quaternion);
        transformValid = false;
        return this;
    }

    /**
     * Switches to Euler orientation in Z-up space. Local points are rotated around
     * X, then Y, then Z before translation and the parent transform. Finite-value
     * validation occurs when the world transform is evaluated.
     *
     * @param x X-axis rotation in radians
     * @param y Y-axis rotation in radians
     * @param z Z-axis yaw in radians
     * @return this renderable
     */
    public ProceduralRenderable3D setRotation(float x, float y, float z) {
        quaternion = null;
        transformValid = false;
        rotationX = x;
        rotationY = y;
        yawRadians = z;
        return this;
    }

    /**
     * Copies the parent affine transform and invalidates the local transform cache.
     * The world matrix is parent times translation times rotation times scale.
     * The parent matrix is not validated here; callers must supply a usable affine
     * transform.
     *
     * @param parent nonnull parent transform; later caller edits have no effect
     * @return this renderable
     * @throws NullPointerException if parent is null
     */
    public ProceduralRenderable3D setParentTransform(Matrix4f parent) {
        parentTransform.set(parent);
        transformValid = false;
        return this;
    }

    /**
     * Writes the current world matrix into a caller-owned destination. Evaluates
     * pending setter changes and direct edits to the position or scale vectors
     * before copying the matrix.
     *
     * @param out nonnull destination matrix
     * @return out
     * @throws NullPointerException     if out is null
     * @throws IllegalArgumentException if local transform components are nonfinite or scale contains zero
     */
    public Matrix4f getWorldTransform(Matrix4f out) {
        updateTransform();
        return out.set(worldTransform);
    }

    /**
     * Transforms a local normal using the inverse transpose of the world matrix's
     * linear part and normalizes any nonzero result. This preserves normal direction
     * under nonuniform scaling; a zero input normal remains zero.
     *
     * @param normal local-space normal, which may also be out
     * @param out    destination for the world-space normal
     * @return out
     * @throws NullPointerException     if either vector is null
     * @throws IllegalArgumentException if the local transform is invalid
     * @throws IllegalStateException    if the world linear transform is effectively singular
     */
    public Vector3f transformNormal(Vector3f normal, Vector3f out) {
        updateTransform();
        if (Math.abs(worldTransform.determinant3x3()) < 1e-20f)
            throw new IllegalStateException("Singular normal transform");
        worldTransform.normal(normalTransform).transform(normal, out);
        return out.lengthSquared() == 0f ? out : out.normalize();
    }

    /**
     * Rebuilds the parent/translation/rotation/scale matrix when invalidated or when
     * live position and scale values differ from the cached components. Validates
     * finite local components and nonzero scale before updating the cache; a rebuild
     * marks world bounds dirty. Parent validity is the caller's responsibility.
     *
     * @throws IllegalArgumentException if a local component is nonfinite or scale contains zero
     */
    private void updateTransform() {
        if (transformValid && lastTransform[0] == position.x() && lastTransform[1] == position.y()
                && lastTransform[2] == position.z() && lastTransform[3] == scale.x()
                && lastTransform[4] == scale.y() && lastTransform[5] == scale.z()
                && lastTransform[6] == rotationX && lastTransform[7] == rotationY && lastTransform[8] == yawRadians)
            return;
        float[] values = {position.x(), position.y(), position.z(),
                scale.x(), scale.y(), scale.z(), rotationX, rotationY, yawRadians};
        for (float value : values)
            if (!Float.isFinite(value)) throw new IllegalArgumentException("Transform must be finite");
        if (scale.x() == 0f || scale.y() == 0f || scale.z() == 0f)
            throw new IllegalArgumentException("Scale components cannot be zero");
        System.arraycopy(values, 0, lastTransform, 0, 9);
        worldTransform.set(parentTransform).translate(values[0], values[1], values[2]);
        if (quaternion == null) worldTransform.rotateZ(yawRadians).rotateY(rotationY).rotateX(rotationX);
        else worldTransform.mul(quaternion.get(rotationMatrix));
        worldTransform.scale(values[3], values[4], values[5]);
        transformValid = true;
        worldBoundsDirty = true;
    }

    /**
     * Returns the live material used for mesh submission. Callers may mutate it;
     * neither reading nor replacing it transfers resource ownership.
     *
     * @return current nonnull material
     */
    @Override
    public Material3D getMaterial() {
        return material;
    }

    /**
     * Replaces the material reference without copying or disposing either material.
     *
     * @param material nonnull material to borrow for future submissions
     * @return this renderable
     * @throws NullPointerException if material is null
     */
    public ProceduralRenderable3D setMaterial(Material3D material) {
        if (material == null) throw new NullPointerException("material");
        this.material = material;
        return this;
    }

    /**
     * Returns the callback currently responsible for generating world-space geometry.
     *
     * @return borrowed emitter, or null when geometry generation is disabled
     */
    public ProceduralMeshEmitter3D getEmitter() {
        return emitter;
    }

    /**
     * Replaces the geometry callback without modifying the local bounds. Update those
     * bounds separately if the new geometry has a different extent.
     *
     * @param emitter callback to borrow, or null to disable emission
     * @return this renderable
     */
    public ProceduralRenderable3D setEmitter(ProceduralMeshEmitter3D emitter) {
        this.emitter = emitter;
        return this;
    }

    /**
     * Returns the live translation vector in parent coordinates. Direct mutations
     * are detected on the next world-transform evaluation and must remain finite.
     *
     * @return mutable position vector
     */
    public Vector3f getPosition() {
        return position;
    }

    /**
     * Stores translation in parent coordinates. Nonfinite inputs are rejected later
     * when the world transform is evaluated.
     *
     * @param x X translation
     * @param y Y translation
     * @param z Z translation
     * @return this renderable
     */
    public ProceduralRenderable3D setPosition(float x, float y, float z) {
        position.set(x, y, z);
        worldBoundsDirty = true;
        return this;
    }

    /**
     * Returns the live local scale vector. Direct edits are detected during transform
     * evaluation; every component must be finite and nonzero. Negative scale is
     * supported.
     *
     * @return mutable scale vector
     */
    public Vector3f getScale() {
        return scale;
    }

    /**
     * Applies the same local scale on all three axes. Negative values reflect all
     * axes; a nonfinite value is rejected during later transform evaluation.
     *
     * @param uniformScale nonzero scale factor
     * @return this renderable
     * @throws IllegalArgumentException if uniformScale is zero
     */
    public ProceduralRenderable3D setScale(float uniformScale) {
        return setScale(uniformScale, uniformScale, uniformScale);
    }

    /**
     * Stores per-axis local scale, allowing reflection through negative components.
     * Zero components are rejected immediately; finite-value checks occur when
     * the world transform is evaluated.
     *
     * @param x nonzero X scale
     * @param y nonzero Y scale
     * @param z nonzero Z scale
     * @return this renderable
     * @throws IllegalArgumentException if any component is zero
     */
    public ProceduralRenderable3D setScale(float x, float y, float z) {
        if (x == 0f || y == 0f || z == 0f) {
            throw new IllegalArgumentException("scale components cannot be 0");
        }
        scale.set(x, y, z);
        worldBoundsDirty = true;
        return this;
    }

    /**
     * Returns the stored Euler Z angle. In quaternion mode this is the last Euler
     * value, not an angle extracted from the active quaternion.
     *
     * @return stored yaw in radians
     */
    public float getYawRadians() {
        return yawRadians;
    }

    /**
     * Switches to Euler mode and replaces yaw while retaining the stored X and Y
     * Euler rotations. Finite-value validation occurs during transform evaluation.
     *
     * @param yawRadians Z-axis rotation in radians
     * @return this renderable
     */
    public ProceduralRenderable3D setYawRadians(float yawRadians) {
        quaternion = null;
        transformValid = false;
        this.yawRadians = yawRadians;
        worldBoundsDirty = true;
        return this;
    }

    /**
     * Returns the explicit visibility flag without testing the emitter, bounds, or
     * camera frustum.
     *
     * @return whether application visibility is enabled
     */
    public boolean isRenderableVisible() {
        return visible;
    }

    /**
     * Sets the application visibility flag used by both frustum-check overloads.
     * Direct calls to {@link #emit(MeshBatch3D)} do not consult this flag.
     *
     * @param visible whether visibility checks may accept this renderable
     * @return this renderable
     */
    public ProceduralRenderable3D setVisible(boolean visible) {
        this.visible = visible;
        return this;
    }

    /**
     * Returns the live local-space bounds used for culling. Direct edits are included
     * when world bounds are next requested. Keep the bounds consistent with emitted
     * geometry; initially they are empty.
     *
     * @return mutable local bounding box
     */
    public AABBf getLocalBounds() {
        return localBounds;
    }

    /**
     * Copies local bounds without reordering their endpoints. Invalid or empty bounds
     * cause visibility checks to reject this renderable.
     *
     * @param bounds nonnull bounds to copy
     * @return this renderable
     * @throws NullPointerException if bounds is null
     */
    public ProceduralRenderable3D setLocalBounds(AABBf bounds) {
        if (bounds == null) throw new NullPointerException("bounds");
        this.localBounds.set(bounds);
        worldBoundsDirty = true;
        return this;
    }

    /**
     * Sets local bounds and corrects reversed endpoints independently on each axis.
     * The supplied coordinates should enclose all geometry generated by the emitter.
     *
     * @param minX first X endpoint
     * @param minY first Y endpoint
     * @param minZ first Z endpoint
     * @param maxX second X endpoint
     * @param maxY second Y endpoint
     * @param maxZ second Z endpoint
     * @return this renderable
     */
    public ProceduralRenderable3D setLocalBounds(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        localBounds.setMin(minX, minY, minZ).setMax(maxX, maxY, maxZ).correctBounds();
        worldBoundsDirty = true;
        return this;
    }

    /**
     * Rebuilds the world-space axis-aligned box from the eight transformed local
     * corners. Returns a live internal result that is overwritten on the next call;
     * copy it if a stable snapshot is needed. Invalid local bounds produce an empty
     * world box.
     *
     * @return reusable world-space bounds
     * @throws IllegalArgumentException if the local transform is invalid
     */
    public AABBf getWorldBounds() {
        updateTransform();
        rebuildWorldBounds();
        return worldBounds;
    }

    /**
     * Tests application visibility, emitter availability, valid local bounds, and
     * intersection with the supplied camera's current frustum. The camera is not
     * rebuilt here; the caller must keep its matrices current.
     *
     * @param camera camera whose frustum is tested, or null to reject visibility
     * @return true if conservative culling accepts the renderable
     * @throws IllegalArgumentException if evaluating the local transform fails
     */
    @Override
    public boolean isVisible(Camera3D camera) {
        if (!visible || camera == null || emitter == null || !(localBounds.minX <= localBounds.maxX && localBounds.minY <= localBounds.maxY && localBounds.minZ <= localBounds.maxZ)) {
            return false;
        }
        return isVisible(camera.getFrustum());
    }

    /**
     * Tests the transformed bounds against a frustum after expanding them by the
     * larger of two world units or five percent of their largest extent. Returns
     * false for a null frustum, missing emitter, disabled visibility, or invalid bounds.
     *
     * @param frustum current clipping frustum
     * @return whether the padded world bounds intersect the frustum
     * @throws IllegalArgumentException if evaluating the local transform fails
     */
    public boolean isVisible(FrustumIntersection frustum) {
        if (!visible || frustum == null || emitter == null || !(localBounds.minX <= localBounds.maxX && localBounds.minY <= localBounds.maxY && localBounds.minZ <= localBounds.maxZ)) {
            return false;
        }
        AABBf bounds = getWorldBounds();
        float padding = cullPadding(bounds);
        return (bounds.minX <= bounds.maxX && bounds.minY <= bounds.maxY && bounds.minZ <= bounds.maxZ) && frustum.testAab(bounds.minX - padding, bounds.minY - padding, bounds.minZ - padding,
                bounds.maxX + padding, bounds.maxY + padding, bounds.maxZ + padding);
    }

    /**
     * Invokes the current emitter with the borrowed destination batch and this owner.
     * Does nothing when no emitter is installed. This method does not perform
     * visibility checks, transform generated vertices, or manage the batch's lifetime;
     * the rendering pipeline and callback handle those responsibilities.
     *
     * @param meshBatch prepared, nonnull mesh destination
     * @throws NullPointerException if meshBatch is null
     */
    @Override
    public void emit(MeshBatch3D meshBatch) {
        if (meshBatch == null) throw new NullPointerException("meshBatch");
        if (emitter != null) {
            emitter.emit(meshBatch, this);
        }
    }

    /**
     * Transforms a local point into world space, including translation and the parent
     * matrix. The input and destination may be the same vector.
     *
     * @param localPoint nonnull local-space point
     * @param out        nonnull destination
     * @return out
     * @throws NullPointerException     if either vector is null
     * @throws IllegalArgumentException if the local transform is invalid
     */
    public Vector3f transform(Vector3f localPoint, Vector3f out) {
        if (localPoint == null) throw new NullPointerException("localPoint");
        return transform(localPoint.x(), localPoint.y(), localPoint.z(), out);
    }

    /**
     * Transforms local coordinates as a position using the current world matrix.
     * Updates the transform cache before writing the caller's destination.
     *
     * @param localX local X coordinate
     * @param localY local Y coordinate
     * @param localZ local Z coordinate
     * @param out    nonnull destination vector
     * @return out
     * @throws NullPointerException     if out is null
     * @throws IllegalArgumentException if the local transform is invalid
     */
    public Vector3f transform(float localX, float localY, float localZ, Vector3f out) {
        if (out == null) throw new NullPointerException("out");
        updateTransform();
        out.set(localX, localY, localZ);
        return worldTransform.transformPosition(out, out);
    }

    /**
     * Clears and recomputes the world box from all eight local corners. Invalid
     * local bounds leave an empty result. Recomputes on every invocation, so direct
     * mutations through the local-bounds getter are reflected without a setter.
     */
    private void rebuildWorldBounds() {
        worldBounds.setMin(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY).setMax(Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY);
        worldBoundsDirty = false;
        if (!(localBounds.minX <= localBounds.maxX && localBounds.minY <= localBounds.maxY && localBounds.minZ <= localBounds.maxZ)) {
            return;
        }

        includeCorner(localBounds.minX, localBounds.minY, localBounds.minZ);
        includeCorner(localBounds.minX, localBounds.minY, localBounds.maxZ);
        includeCorner(localBounds.minX, localBounds.maxY, localBounds.minZ);
        includeCorner(localBounds.minX, localBounds.maxY, localBounds.maxZ);
        includeCorner(localBounds.maxX, localBounds.minY, localBounds.minZ);
        includeCorner(localBounds.maxX, localBounds.minY, localBounds.maxZ);
        includeCorner(localBounds.maxX, localBounds.maxY, localBounds.minZ);
        includeCorner(localBounds.maxX, localBounds.maxY, localBounds.maxZ);
    }

    /**
     * Transforms one local corner into reusable scratch storage and expands the
     * current world box to include it.
     *
     * @param x local corner X
     * @param y local corner Y
     * @param z local corner Z
     */
    private void includeCorner(float x, float y, float z) {
        worldBounds.union(transform(x, y, z, boundsScratch));
    }
}
