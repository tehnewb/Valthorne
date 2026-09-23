package valthorne.graphics.scene;

import valthorne.graphics.model.Material3D;
import valthorne.graphics.model.Model3D;
import valthorne.graphics.render.ModelBatch3D;
import valthorne.graphics.model.ModelBuilder3D;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.joml.primitives.AABBf;

/**
 * Hierarchical placement node with optional borrowed model geometry and material.
 * Child transforms compose with parent matrices, preserving shear from rotated
 * descendants of nonuniformly scaled parents. Visibility gates an entire subtree.
 * No GPU resources are owned and removing a child does not dispose its model.
 *
 * <pre>{@code
 * SceneNode3D assembly = new SceneNode3D().setPosition(3, 0, 0);
 * assembly.addChild(new SceneNode3D()
 *         .setModel(ModelBuilder3D.box(1, 1, 1)).setPosition(0, 0, 2));
 * batch.begin(state);
 * assembly.submit(batch);
 * batch.end();
 * }</pre>
 *
 * <p>Submission prepares world transforms and aggregate bounds before traversing
 * visible model nodes. Internal matrices and scratch instances are reused, so graph
 * mutation and traversal must remain on one thread and must not be reentrant.</p>
 *
 * @author Albert Beaupre
 */
public final class SceneNode3D {

    private final Vector3f position = new Vector3f(); // Live local translation.
    private final Vector3f scale = new Vector3f(1f, 1f, 1f); // Live local scale, initially one.
    private final ArrayList<SceneNode3D> children = new ArrayList<>(); // Owned child relationships in insertion order.
    private final ModelInstance3D scratchInstance = new ModelInstance3D(); // Reusable model placement for prepared submission.
    private final Matrix4f rotationMatrix = new Matrix4f(); // Scratch quaternion conversion matrix.
    private final Matrix4f localTransform = new Matrix4f(); // Scratch composed local transform.
    private final Matrix4f preparedWorld = new Matrix4f(); // World transform retained for the current traversal.
    private final AABBf subtreeBounds = new AABBf(); // Aggregate world bounds of visible descendant models.
    private Model3D model; // Borrowed model, or null for a grouping node.
    private Material3D material = new Material3D(); // Shared material used for this node's model.
    private boolean visible = true; // Logical visibility of this node and its descendants.
    private float yawRadians; // Stored Euler Z angle in radians.
    private float rotationX, rotationY; // Stored Euler X/Y angles in radians.
    private Quaternionf quaternion; // Copied quaternion override, or null for Euler rotation.
    private SceneNode3D parent; // Current parent, or null for an unattached root.
    private int subtreeModels; // Visible model count prepared for aggregate culling.

    /**
     * Copies and normalizes a finite, nonzero quaternion for transform composition. Stored Euler
     * angles remain unchanged but inactive until Euler mode is selected.
     *
     * @param rotation local orientation to copy
     * @return this node
     * @throws IllegalArgumentException if the quaternion is non-finite or zero
     */
    public SceneNode3D setRotation(Quaternionf rotation) {
        quaternion = ModelInstance3D.copyRotation(rotation, quaternion);
        return this;
    }

    /**
     * Selects Euler mode and stores rotations applied X, then Y, then Z to local points.
     * Angles are stored without finiteness validation.
     *
     * @param x X angle in radians
     * @param y Y angle in radians
     * @param z Z angle in radians
     * @return this node
     */
    public SceneNode3D setRotation(float x, float y, float z) {
        quaternion = null;
        rotationX = x;
        rotationY = y;
        yawRadians = z;
        return this;
    }

    /**
     * Returns the current hierarchy parent without changing attachment.
     *
     * @return parent node, or null
     */
    public SceneNode3D getParent() {
        return parent;
    }

    /**
     * Recursively composes parent and local translation/rotation/scale into caller
     * storage, preserving affine shear. Recomputes from live vectors on each call and
     * does not normalize or decompose the result.
     *
     * @param out destination matrix distinct from internal scratch storage
     * @return out
     */
    public Matrix4f getWorldTransform(Matrix4f out) {
        if (parent == null) out.identity();
        else parent.getWorldTransform(out);
        return appendLocalTransform(out);
    }

    /**
     * Appends the live local transform to an already captured parent transform.
     */
    public Matrix4f appendLocalTransform(Matrix4f out) {
        localTransform.identity().translate(position.x(), position.y(), position.z());
        if (quaternion == null) localTransform.rotateZ(yawRadians).rotateY(rotationY).rotateX(rotationX);
        else localTransform.mul(quaternion.get(rotationMatrix));
        localTransform.scale(scale.x(), scale.y(), scale.z());
        return out.mul(localTransform);
    }

    /**
     * Returns this node's borrowed geometry without traversing descendants.
     *
     * @return model, or null for a grouping-only node
     */
    public Model3D getModel() {
        return model;
    }

    /**
     * Changes borrowed geometry for future preparation without altering children or
     * disposing the previous model.
     *
     * @param model replacement model, or null
     * @return this node
     */
    public SceneNode3D setModel(Model3D model) {
        this.model = model;
        return this;
    }

    /**
     * Returns the shared material used by this node's model. Child materials are independent.
     *
     * @return live material reference
     */
    public Material3D getMaterial() {
        return material;
    }

    /**
     * Retains a non-null material reference without copying it or propagating it to children.
     *
     * @param material shared replacement material
     * @return this node
     * @throws NullPointerException if material is null
     */
    public SceneNode3D setMaterial(Material3D material) {
        if (material == null) throw new NullPointerException("material");
        this.material = material;
        return this;
    }

    /**
     * Returns the live local translation vector. Changes are reflected during the next
     * world-transform query or traversal.
     *
     * @return mutable local position
     */
    public Vector3f getPosition() {
        return position;
    }

    /**
     * Stores local translation without validating finiteness or changing parent links.
     *
     * @param x local X translation
     * @param y local Y translation
     * @param z local Z translation
     * @return this node
     */
    public SceneNode3D setPosition(float x, float y, float z) {
        position.set(x, y, z);
        return this;
    }

    /**
     * Returns the live local scale vector. Direct edits bypass the setter's zero check;
     * callers must maintain a usable transform.
     *
     * @return mutable local scale
     */
    public Vector3f getScale() {
        return scale;
    }

    /**
     * Sets every scale axis to one nonzero value. Negative scale is allowed; non-finite
     * values are not explicitly rejected here.
     *
     * @param uniformScale common local scale
     * @return this node
     * @throws IllegalArgumentException if scale is zero
     */
    public SceneNode3D setScale(float uniformScale) {
        return setScale(uniformScale, uniformScale, uniformScale);
    }

    /**
     * Stores nonzero local scale components. Negative scale and resulting parent-child
     * shear are preserved; finiteness is not checked by this setter.
     *
     * @param x X scale
     * @param y Y scale
     * @param z Z scale
     * @return this node
     * @throws IllegalArgumentException if any scale component is zero
     */
    public SceneNode3D setScale(float x, float y, float z) {
        if (x == 0f || y == 0f || z == 0f) {
            throw new IllegalArgumentException("scale components cannot be 0");
        }
        scale.set(x, y, z);
        return this;
    }

    /**
     * Returns the stored Euler Z angle, which may be inactive under quaternion rotation.
     * Does not derive yaw from the world matrix.
     *
     * @return stored yaw in radians
     */
    public float getYawRadians() {
        return yawRadians;
    }

    /**
     * Selects Euler mode and changes Z rotation while preserving stored X/Y angles.
     *
     * @param yawRadians local Z angle in radians
     * @return this node
     */
    public SceneNode3D setYawRadians(float yawRadians) {
        quaternion = null;
        this.yawRadians = yawRadians;
        return this;
    }

    /**
     * Returns this node's own visibility flag. Ancestor visibility is checked separately
     * when submitting a subtree.
     *
     * @return local visibility flag
     */
    public boolean isVisible() {
        return visible;
    }

    /**
     * Changes whether traversal includes this node and its descendants. Geometry and
     * relationships remain retained while hidden.
     *
     * @param visible subtree visibility flag
     * @return this node
     */
    public SceneNode3D setVisible(boolean visible) {
        this.visible = visible;
        return this;
    }

    /**
     * Appends a child, detaching it from its old parent when necessary. Rejects cycles;
     * an already-attached child is a no-op. Local transforms remain unchanged, so
     * reparenting can change world placement.
     *
     * @param child node to attach
     * @return this parent
     * @throws NullPointerException     if child is null
     * @throws IllegalArgumentException if attachment would create a cycle
     */
    public SceneNode3D addChild(SceneNode3D child) {
        if (child == null) throw new NullPointerException("child");
        for (SceneNode3D ancestor = this; ancestor != null; ancestor = ancestor.parent) {
            if (ancestor == child) throw new IllegalArgumentException("Scene graph cannot contain a cycle");
        }
        if (child.parent == this) return this;
        if (child.parent != null) child.parent.removeChild(child);
        child.parent = this;
        children.add(child);
        return this;
    }

    /**
     * Detaches a direct child and clears its parent reference. Does not dispose resources
     * or preserve world placement by adjusting its local transform.
     *
     * @param child child to detach
     * @return true if removed, false if not a direct child
     */
    public boolean removeChild(SceneNode3D child) {
        if (!children.remove(child)) return false;
        child.parent = null;
        return true;
    }

    /**
     * Detaches all direct children by clearing their parent references and this list.
     * Descendant relationships, models, and materials remain intact.
     */
    public void clearChildren() {
        for (SceneNode3D child : children) child.parent = null;
        children.clear();
    }

    /**
     * Returns an unmodifiable live view in insertion order. Child nodes remain mutable
     * and later attachment changes are reflected in the view.
     *
     * @return live child-list view
     */
    public List<SceneNode3D> getChildren() {
        return Collections.unmodifiableList(children);
    }

    // Allocation-free indexed traversal without exposing mutable membership.

    /**
     * Reads direct child count for renderer traversal without allocating a snapshot.
     *
     * @return current number of direct children
     */
    public int childCount() {
        return children.size();
    }

    /**
     * Borrows a direct child in current list order. Hierarchy mutation during indexed
     * traversal is unsupported because indices may shift.
     *
     * @param index zero-based child index
     * @return borrowed child node
     * @throws IndexOutOfBoundsException if index is outside the current child list
     */
    public SceneNode3D childAt(int index) {
        return children.get(index);
    }

    /**
     * Checks this node and its ancestors for visibility, then prepares world transforms
     * and aggregate bounds before submitting visible geometry. Requires an active batch
     * when drawable content is traversed. Prepared instances are reused internally.
     *
     * @param batch active destination batch
     * @return accepted model-node count
     * @throws NullPointerException if batch is null
     */
    public int submit(ModelBatch3D batch) {
        if (batch == null) throw new NullPointerException("batch");
        if (!visible) return 0;
        for (SceneNode3D ancestor = parent; ancestor != null; ancestor = ancestor.parent)
            if (!ancestor.visible) return 0;
        prepare(parent == null ? null : parent.getWorldTransform(new Matrix4f()));
        return submitPrepared(batch);
    }

    /**
     * Recursively builds world transforms, scratch placements, and aggregate bounds/counts
     * for visible models. Invisible branches clear their aggregates and stop traversal.
     * Model resources remain borrowed and are not uploaded by preparation.
     *
     * @param parentWorld parent matrix, or null for root identity
     */
    private void prepare(Matrix4f parentWorld) {
        subtreeBounds.setMin(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY).setMax(Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY);
        subtreeModels = 0;
        if (!visible) return;
        localTransform.identity().translate(position.x(), position.y(), position.z());
        if (quaternion == null) localTransform.rotateZ(yawRadians).rotateY(rotationY).rotateX(rotationX);
        else localTransform.mul(quaternion.get(rotationMatrix));
        localTransform.scale(scale.x(), scale.y(), scale.z());
        if (parentWorld == null) preparedWorld.set(localTransform);
        else preparedWorld.set(parentWorld).mul(localTransform);
        if (model != null) {
            scratchInstance.setModel(model).setMaterial(material)
                    .setParentTransform(preparedWorld);
            subtreeBounds.union(scratchInstance.getWorldBounds());
            subtreeModels++;
        }
        for (SceneNode3D child : children) {
            child.prepare(preparedWorld);
            subtreeBounds.union(child.subtreeBounds);
            subtreeModels += child.subtreeModels;
        }
    }

    /**
     * Rejects invisible, empty, or fully culled branches before submitting the prepared
     * instance and recursing through children in order. Assumes prepare has just run
     * without intervening graph mutation.
     *
     * @param batch active destination batch
     * @return accepted model-node count
     */
    private int submitPrepared(ModelBatch3D batch) {
        if (!visible || subtreeModels == 0) return 0;
        if (batch.rejectSubtree(subtreeBounds, subtreeModels)) return 0;
        int submitted = model != null && batch.submit(scratchInstance) ? 1 : 0;
        for (SceneNode3D child : children) submitted += child.submitPrepared(batch);
        return submitted;
    }
}
