package valthorne.graphics.model;

import valthorne.camera.Camera3D;
import org.joml.FrustumIntersection;
import org.joml.primitives.AABBf;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Places borrowed model geometry using local translation, rotation, scale, and a
 * copied parent transform. World matrices and bounds are computed lazily; direct
 * edits to the exposed position and scale vectors are detected on the next lookup.
 * Euler rotation applies X, then Y, then Z in conventional Z-up space; quaternion
 * rotation can replace that mode. Materials and models remain shared references.
 *
 * <pre>{@code
 * ModelInstance3D instance = new ModelInstance3D()
 *         .setModel(ModelBuilder3D.box(1, 1, 1))
 *         .setPosition(3, 0, 0)
 *         .setRotation(0, 0, 0.5f);
 * AABBf bounds = instance.getWorldBounds();
 * }</pre>
 *
 * <p>Use on one thread while rendering or querying. Bounds access returns live cached
 * storage that callers must not mutate. Transform validation is partly deferred:
 * non-finite local values and zero scale introduced through live vectors fail when
 * the world transform is next evaluated. This instance owns no GPU resources.</p>
 *
 * @author Albert Beaupre
 */
public final class ModelInstance3D implements MeshRenderable3D {

    private final Vector3f position = new Vector3f(); // Live local translation.
    private final Vector3f scale = new Vector3f(1f, 1f, 1f); // Live per-axis local scale, initially one.
    private final AABBf worldBounds = new AABBf(); // Cached world-space bounds, exposed read-only by convention.
    private final Vector3f boundsScratch = new Vector3f(); // Reusable transformed corner for bounds calculation.
    private final Matrix4f rotationMatrix = new Matrix4f(); // Reusable matrix for quaternion conversion.
    private final Matrix4f parentTransform = new Matrix4f(); // Copied affine parent transform, initially identity.
    private final org.joml.Matrix3f normalTransform = new org.joml.Matrix3f(); // Reusable inverse-transpose matrix for transforming normals.
    private final Matrix4f worldTransform = new Matrix4f(); // Cached parent-times-local transform.
    private final float[] lastTransform = new float[9]; // Last validated position, scale, and Euler components.
    private Model3D model; // Borrowed model geometry, or null.
    private Material3D material = new Material3D(); // Shared material reference, initially a new default material.
    private boolean visible = true; // Logical visibility flag before frustum checks.
    private float yawRadians; // Stored Euler Z rotation, possibly inactive in quaternion mode.
    private float rotationX, rotationY; // Stored Euler X/Y rotations in radians.
    private Quaternionf quaternion; // Copied quaternion override, or null for Euler mode.
    private boolean transformValid; // Whether cached matrix matches the last validated local values.
    private boolean worldBoundsDirty = true; // Whether model or world-transform changes require bounds rebuilding.

    /**
     * Computes conservative frustum padding as the larger of two world units and five
     * percent of the bounds' greatest extent.
     *
     * @param bounds current world-space bounds
     * @return padding in world units
     */
    private static float cullPadding(AABBf bounds) {
        float maxExtent = Math.max((bounds.maxX - bounds.minX), Math.max((bounds.maxY - bounds.minY), (bounds.maxZ - bounds.minZ)));
        return Math.max(2f, maxExtent * 0.05f);
    }

    /**
     * Returns the closest world-transformed triangle intersection after a bounds test.
     * Invisible or missing models return positive infinity. The result is a ray parameter,
     * equal to world distance for a normalized direction. Allocates three scratch vectors
     * per query and does not test texture alpha or material visibility.
     *
     * @param ray world-space query ray
     * @return nearest intersection parameter, or positive infinity when none
     */
    public float intersect(org.joml.primitives.Rayf ray) {
        if (!visible || model == null) return Float.POSITIVE_INFINITY;
        AABBf bounds = getWorldBounds();
        if (!(bounds.minX <= bounds.maxX && bounds.minY <= bounds.maxY && bounds.minZ <= bounds.maxZ)) return Float.POSITIVE_INFINITY;
        // Keep exact edge and planar hits in this conservative broad phase. JOML's
        // ray/AABB test uses an open interval. Scale padding to the ray origin too,
        // so subtraction cannot round a planar interval back to zero thickness.
        // The exact triangle test below decides whether this is an actual hit.
        float magnitude = Math.max(1f, Math.max(Math.abs(ray.oX), Math.max(Math.abs(ray.oY), Math.abs(ray.oZ))));
        magnitude = Math.max(magnitude, Math.max(Math.abs(bounds.minX), Math.abs(bounds.maxX)));
        magnitude = Math.max(magnitude, Math.max(Math.abs(bounds.minY), Math.abs(bounds.maxY)));
        magnitude = Math.max(magnitude, Math.max(Math.abs(bounds.minZ), Math.abs(bounds.maxZ)));
        float padding = magnitude * 1e-6f;
        if (!bounds.containsPoint(ray.oX, ray.oY, ray.oZ)
                && !org.joml.Intersectionf.testRayAab(ray.oX, ray.oY, ray.oZ, ray.dX, ray.dY, ray.dZ,
                bounds.minX - padding, bounds.minY - padding, bounds.minZ - padding,
                bounds.maxX + padding, bounds.maxY + padding, bounds.maxZ + padding))
            return Float.POSITIVE_INFINITY;
        float closest = Float.POSITIVE_INFINITY;
        Vector3f a = new Vector3f(), b = new Vector3f(), c = new Vector3f();
        for (Model3D.Triangle triangle : model.triangles()) {
            transform(triangle.a, a);
            transform(triangle.b, b);
            transform(triangle.c, c);
            float hit = org.joml.Intersectionf.intersectRayTriangle(ray.oX, ray.oY, ray.oZ,
                    ray.dX, ray.dY, ray.dZ, a.x(), a.y(), a.z(), b.x(), b.y(), b.z(),
                    c.x(), c.y(), c.z(), 1e-8f);
            if (hit >= 0f) closest = Math.min(closest, hit);
        }
        return closest;
    }

    /**
     * Copies and normalizes a finite, nonzero quaternion and invalidates the world matrix. Stored Euler values
     * are preserved but are not used to construct rotation while the override is active.
     *
     * @param rotation quaternion to copy
     * @return this instance
     * @throws IllegalArgumentException if the quaternion is non-finite or zero
     */
    public ModelInstance3D setRotation(Quaternionf rotation) {
        quaternion = copyRotation(rotation, quaternion);
        transformValid = false;
        return this;
    }

    /**
     * Validates incoming JOML values before replacing an engine orientation.
     */
    static Quaternionf copyRotation(Quaternionf source, Quaternionf destination) {
        double length = Math.sqrt((double) source.x() * source.x() + (double) source.y() * source.y()
                + (double) source.z() * source.z() + (double) source.w() * source.w());
        if (!Double.isFinite(length) || length == 0)
            throw new IllegalArgumentException("Quaternion must be finite and nonzero");
        if (destination == null) destination = new Quaternionf();
        return destination.set((float) (source.x() / length), (float) (source.y() / length),
                (float) (source.z() / length), (float) (source.w() / length));
    }

    /**
     * Switches to Euler mode and stores X, Y, then Z rotation angles. Finite-value checks
     * are deferred to world-transform evaluation.
     *
     * @param x X rotation in radians
     * @param y Y rotation in radians
     * @param z Z/yaw rotation in radians
     * @return this instance
     */
    public ModelInstance3D setRotation(float x, float y, float z) {
        quaternion = null;
        transformValid = false;
        rotationX = x;
        rotationY = y;
        yawRadians = z;
        return this;
    }

    /**
     * Copies a changed parent matrix and invalidates the world transform. Equal matrix
     * contents are a no-op. The supplied matrix should be a usable affine transform;
     * this setter does not validate singularity or finiteness.
     *
     * @param parent parent transform to copy
     * @return this instance
     * @throws NullPointerException if parent is null
     */
    public ModelInstance3D setParentTransform(Matrix4f parent) {
        if (parentTransform.equals(parent)) return this;
        parentTransform.set(parent);
        transformValid = false;
        return this;
    }

    /**
     * Refreshes lazy transform state and copies the world matrix into caller storage.
     * Direct edits to position and scale are incorporated before copying.
     *
     * @param out destination matrix
     * @return out
     * @throws IllegalArgumentException if local transform values are invalid
     */
    public Matrix4f getWorldTransform(Matrix4f out) {
        updateTransform();
        return out.set(worldTransform);
    }

    /**
     * Refreshes transform state and applies the world matrix's normal transformation.
     * Uses the matrix helper's inverse-transpose and normalization behavior, rather
     * than transforming a normal as a position.
     *
     * @param normal local-space normal
     * @param out destination world-space normal
     * @return out
     */
    public Vector3f transformNormal(Vector3f normal, Vector3f out) {
        updateTransform();
        if (Math.abs(worldTransform.determinant3x3()) < 1e-20f)
            throw new IllegalStateException("Singular normal transform");
        worldTransform.normal(normalTransform).transform(normal, out);
        return out.lengthSquared() == 0f ? out : out.normalize();
    }

    /**
     * Refreshes the cached world matrix when mutable position/scale components or
     * rotation state change. Validation completes before cache keys are replaced,
     * allowing callers to repair invalid input and retry. Successful updates mark
     * world bounds dirty; unchanged lookups leave both caches untouched.
     *
     * @throws IllegalArgumentException if a local component is nonfinite or any
     *                                  scale component is zero
     */
    private void updateTransform() {
        if (transformValid && lastTransform[0] == position.x() && lastTransform[1] == position.y()
                && lastTransform[2] == position.z() && lastTransform[3] == scale.x()
                && lastTransform[4] == scale.y() && lastTransform[5] == scale.z()
                && lastTransform[6] == rotationX && lastTransform[7] == rotationY && lastTransform[8] == yawRadians)
            return;
        float x = position.x(), y = position.y(), z = position.z();
        float sx = scale.x(), sy = scale.y(), sz = scale.z();
        if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)
                || !Float.isFinite(sx) || !Float.isFinite(sy) || !Float.isFinite(sz)
                || !Float.isFinite(rotationX) || !Float.isFinite(rotationY) || !Float.isFinite(yawRadians))
            throw new IllegalArgumentException("Transform must be finite");
        if (sx == 0f || sy == 0f || sz == 0f)
            throw new IllegalArgumentException("Scale components cannot be zero");
        lastTransform[0] = x;
        lastTransform[1] = y;
        lastTransform[2] = z;
        lastTransform[3] = sx;
        lastTransform[4] = sy;
        lastTransform[5] = sz;
        lastTransform[6] = rotationX;
        lastTransform[7] = rotationY;
        lastTransform[8] = yawRadians;
        worldTransform.set(parentTransform).translate(x, y, z);
        if (quaternion == null) worldTransform.rotateZ(yawRadians).rotateY(rotationY).rotateX(rotationX);
        else worldTransform.mul(quaternion.get(rotationMatrix));
        worldTransform.scale(sx, sy, sz);
        transformValid = true;
        worldBoundsDirty = true;
    }

    /**
     * Returns the borrowed model reference without copying geometry.
     *
     * @return current model, or null
     */
    public Model3D getModel() {
        return model;
    }

    /**
     * Replaces borrowed geometry and invalidates world bounds only when identity changes.
     * Does not dispose the old model or alter placement and material.
     *
     * @param model replacement model, or null to remove geometry
     * @return this instance
     */
    public ModelInstance3D setModel(Model3D model) {
        if (this.model == model) return this;
        this.model = model;
        this.worldBoundsDirty = true;
        return this;
    }

    /**
     * Copies placement, visibility, parent transform, and quaternion state while sharing
     * the other instance's model and material. Invalidates both derived caches; this
     * is a placement snapshot rather than a deep resource copy.
     *
     * @param other instance to copy
     * @return this instance
     * @throws NullPointerException if other is null
     */
    public ModelInstance3D set(ModelInstance3D other) {
        if (other == null) throw new NullPointerException("other");
        this.model = other.model;
        this.material = other.material;
        this.position.set(other.position);
        this.scale.set(other.scale);
        this.visible = other.visible;
        this.yawRadians = other.yawRadians;
        this.quaternion = other.quaternion == null ? null : new Quaternionf(other.quaternion);
        this.rotationX = other.rotationX;
        this.rotationY = other.rotationY;
        this.parentTransform.set(other.parentTransform);
        this.transformValid = false;
        this.worldBoundsDirty = true;
        return this;
    }

    /**
     * Returns the live shared material. Changes to it can affect other instances using
     * the same reference; frame batching may snapshot it separately.
     *
     * @return current material
     */
    @Override
    public Material3D getMaterial() {
        return material;
    }

    /**
     * Retains a non-null material reference without copying or disposing either material.
     * Does not affect transform or bounds caches.
     *
     * @param material shared replacement material
     * @return this instance
     * @throws NullPointerException if material is null
     */
    public ModelInstance3D setMaterial(Material3D material) {
        if (material == null) throw new NullPointerException("material");
        this.material = material;
        return this;
    }

    /**
     * Returns the live local translation vector. Direct changes are detected when derived
     * transform state is next evaluated.
     *
     * @return owned mutable position
     */
    public Vector3f getPosition() {
        return position;
    }

    /**
     * Copies the supplied local translation, retaining no vector reference. Value
     * finiteness is checked when the transform is next evaluated.
     *
     * @param position local translation to copy
     * @return this instance
     * @throws NullPointerException if position is null
     */
    public ModelInstance3D setPosition(Vector3f position) {
        if (position == null) throw new NullPointerException("position");
        return setPosition(position.x(), position.y(), position.z());
    }

    /**
     * Stores local translation and marks world bounds dirty. Matrix refresh remains lazy
     * and detects the changed components on its next evaluation.
     *
     * @param x local X translation
     * @param y local Y translation
     * @param z local Z translation
     * @return this instance
     */
    public ModelInstance3D setPosition(float x, float y, float z) {
        this.position.set(x, y, z);
        this.worldBoundsDirty = true;
        return this;
    }

    /**
     * Returns the live per-axis local scale. Direct edits are detected and validated
     * when transform state is evaluated; zero components are invalid.
     *
     * @return owned mutable scale
     */
    public Vector3f getScale() {
        return scale;
    }

    /**
     * Assigns the same local scale to all axes. Negative values are supported, zero
     * is rejected immediately, and finiteness is checked during transform evaluation.
     *
     * @param uniformScale common nonzero scale
     * @return this instance
     * @throws IllegalArgumentException if scale is zero
     */
    public ModelInstance3D setScale(float uniformScale) {
        return setScale(uniformScale, uniformScale, uniformScale);
    }

    /**
     * Stores nonzero per-axis local scale and invalidates bounds. Negative scale is
     * allowed; non-finite values fail on subsequent world-transform evaluation.
     *
     * @param x X scale
     * @param y Y scale
     * @param z Z scale
     * @return this instance
     * @throws IllegalArgumentException if any component is zero
     */
    public ModelInstance3D setScale(float x, float y, float z) {
        if (x == 0f || y == 0f || z == 0f) {
            throw new IllegalArgumentException("scale components cannot be 0");
        }
        this.scale.set(x, y, z);
        this.worldBoundsDirty = true;
        return this;
    }

    /**
     * Returns the stored Euler Z angle. It does not extract an angle from an active
     * quaternion override and may therefore be inactive configuration.
     *
     * @return stored yaw in radians
     */
    public float getYawRadians() {
        return yawRadians;
    }

    /**
     * Switches to Euler mode and replaces Z rotation while preserving stored X/Y angles.
     * Invalidates matrix and bounds; finite-value validation is deferred.
     *
     * @param yawRadians Z rotation in radians
     * @return this instance
     */
    public ModelInstance3D setYawRadians(float yawRadians) {
        quaternion = null;
        transformValid = false;
        this.yawRadians = yawRadians;
        this.worldBoundsDirty = true;
        return this;
    }

    /**
     * Returns logical visibility without checking model presence, bounds, or a camera.
     *
     * @return instance visibility flag
     */
    public boolean isRenderableVisible() {
        return visible;
    }

    /**
     * Changes logical visibility for later submission and intersection checks. Geometry
     * and cached transforms are retained.
     *
     * @param visible whether the instance is eligible to render
     * @return this instance
     */
    public ModelInstance3D setVisible(boolean visible) {
        this.visible = visible;
        return this;
    }

    /**
     * Refreshes transform and, when dirty, encloses the eight transformed corners of
     * the model's local bounds. Returns owned cached storage that must not be modified.
     * Missing or invalid local geometry leaves empty bounds.
     *
     * @return live world-space bounds
     */
    public AABBf getWorldBounds() {
        updateTransform();
        if (worldBoundsDirty) {
            rebuildWorldBounds();
        }
        return worldBounds;
    }

    /**
     * Tests logical visibility and model/camera presence, then uses the camera's current
     * frustum with conservative bounds padding. Does not rebuild the camera itself.
     *
     * @param camera camera with current frustum, or null
     * @return whether the instance passes the visibility test
     */
    @Override
    public boolean isVisible(Camera3D camera) {
        if (!visible || camera == null || model == null) {
            return false;
        }
        return isVisible(camera.getFrustum());
    }

    /**
     * Tests world bounds using conservative padding after checking logical visibility
     * and model/frustum presence. Null frustums return false rather than disabling culling.
     *
     * @param frustum world-space culling frustum, or null
     * @return whether padded bounds intersect the frustum
     */
    public boolean isVisible(FrustumIntersection frustum) {
        if (!visible || frustum == null || model == null) {
            return false;
        }
        AABBf bounds = getWorldBounds();
        float padding = cullPadding(bounds);
        return (bounds.minX <= bounds.maxX && bounds.minY <= bounds.maxY && bounds.minZ <= bounds.maxZ) && frustum.testAab(bounds.minX - padding, bounds.minY - padding, bounds.minZ - padding,
                bounds.maxX + padding, bounds.maxY + padding, bounds.maxZ + padding);
    }

    /**
     * Delegates geometry emission to the supplied mesh batch. This method does not
     * independently cull the instance or manage the batch lifecycle.
     *
     * @param meshBatch destination batch
     * @throws NullPointerException if meshBatch is null
     */
    @Override
    public void emit(MeshBatch3D meshBatch) {
        if (meshBatch == null) throw new NullPointerException("meshBatch");
        meshBatch.model(this);
    }

    /**
     * Transforms a local point using the current lazy world matrix. Input and output
     * may alias because components are captured before writing.
     *
     * @param localPoint source local-space point
     * @param out destination world-space point
     * @return out
     * @throws NullPointerException if either vector is null
     */
    public Vector3f transform(Vector3f localPoint, Vector3f out) {
        if (localPoint == null) throw new NullPointerException("localPoint");
        return transform(localPoint.x(), localPoint.y(), localPoint.z(), out);
    }

    /**
     * Refreshes the world transform and applies it to supplied local coordinates using
     * caller-owned output storage.
     *
     * @param localX local X coordinate
     * @param localY local Y coordinate
     * @param localZ local Z coordinate
     * @param out destination world-space point
     * @return out
     * @throws NullPointerException if out is null
     */
    public Vector3f transform(float localX, float localY, float localZ, Vector3f out) {
        if (out == null) throw new NullPointerException("out");
        updateTransform();
        out.set(localX, localY, localZ);
        return worldTransform.transformPosition(out, out);
    }

    /**
     * Clears bounds and includes the eight transformed local-box corners when the model
     * has valid local bounds. Empty geometry leaves the cleared bound unchanged.
     */
    private void rebuildWorldBounds() {
        worldBounds.setMin(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY).setMax(Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY);
        worldBoundsDirty = false;

        if (model == null) {
            return;
        }

        AABBf localBounds = model.getLocalBounds();
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
     * Transforms one local bounds corner into reusable scratch storage and expands the
     * world bound to include it.
     *
     * @param x local corner X
     * @param y local corner Y
     * @param z local corner Z
     */
    private void includeCorner(float x, float y, float z) {
        worldBounds.union(transform(x, y, z, boundsScratch));
    }
}
