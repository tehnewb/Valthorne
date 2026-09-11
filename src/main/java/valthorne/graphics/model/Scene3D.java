package valthorne.graphics.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Groups loose renderables and root-node hierarchies for submission and model
 * picking. Membership is stored in insertion order, with loose renderables
 * traversed before root nodes. The rendering batch remains responsible for
 * culling, material grouping and final pass ordering.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * Scene3D scene = new Scene3D();
 * scene.add(instance);
 * scene.addNode(rootNode);
 * boolean submittedVisibleObjects = scene.render(batch, renderState);
 * PickResult3D hit = scene.pick(ray);
 * }</pre>
 *
 * <p>This scene borrows objects and does not dispose them when removed or cleared.
 * Loose renderables may be added more than once; root-node membership is checked
 * for duplicates. Adding a root does not transfer hierarchy ownership or prevent
 * later reparenting, so callers must maintain valid scene-root membership.</p>
 *
 * <p>Picking tests model triangles and node visibility, independently of rendering
 * materials or a camera frustum. Billboards and custom procedural emitters are
 * not picked by this class. All stored objects remain mutable; use a consistent
 * owning thread and avoid changing membership during traversal.</p>
 *
 * @author Albert Beaupre
 */
public final class Scene3D {
    /**
     * Renders this scene through Filament into the current OpenGL viewport.
     */
    public void render(FilamentRenderer3D renderer,valthorne.camera.Camera3D camera){renderer.render(this,camera);}

    private final ArrayList<Renderable3D> renderables = new ArrayList<>(); // Borrowed loose renderables in insertion order; duplicates are permitted.
    private final ArrayList<SceneNode3D> nodes = new ArrayList<>(); // Borrowed hierarchy roots, checked for duplicates when added.
    private final ArrayList<PointLight3D> lights = new ArrayList<>(); // Borrowed point lights associated with this scene.
    private final List<PointLight3D> readOnlyLights = Collections.unmodifiableList(lights); // Unmodifiable live view of the scene's point-light list.

    /**
     * Adds a borrowed explicit Filament point light once, independently of mesh membership.
     */
    public void addLight(PointLight3D light) {
        java.util.Objects.requireNonNull(light, "light");
        if (!lights.contains(light)) lights.add(light);
    }

    /**
     * Removes an explicit light without disposing it or changing its parameters.
     */
    public boolean removeLight(PointLight3D light) {return lights.remove(light);}

    /**
     * Cached, read-only live view of explicit point lights, excluding emissive mesh lights.
     */
    public List<PointLight3D> getLights() {return readOnlyLights;}

    /**
     * Finds the closest finite forward model-triangle hit. Loose model instances
     * are tested first, followed by depth-first node traversal. Hidden nodes skip
     * their entire subtree; loose instances use their own intersection visibility
     * check. Equal-distance hits retain the first result encountered.
     *
     * <p>The ray and model transforms must use the same world space. Distance is
     * the ray parameter, equivalent to world distance for a normalized direction.
     * Texture transparency and material render-pass settings do not filter hits.</p>
     *
     * @param ray the nonnull world-space query ray
     * @return the nearest model hit, or null when no eligible triangle is hit
     * @throws NullPointerException if ray is null
     */
    public PickResult3D pick(org.joml.primitives.Rayf ray) {
        java.util.Objects.requireNonNull(ray, "ray");
        PickResult3D best = null;
        for (Renderable3D renderable : renderables) {
            if (renderable instanceof ModelInstance3D instance) best = pickInstance(ray, instance, null, best);
        }
        for (SceneNode3D node : nodes) best = pickNode(ray, node, best);
        return best;
    }

    /**
     * Tests a visible node's model before recursively testing its children.
     * Creates a temporary instance carrying the node's current world transform
     * and shared model/material references. A hidden node returns immediately,
     * preserving the previous result and skipping descendants.
     *
     * @param ray  the world-space ray used throughout the traversal
     * @param node the nonnull node whose subtree should be tested
     * @param best the nearest hit found so far, or null
     * @return the closest result after visiting this subtree
     */
    private PickResult3D pickNode(org.joml.primitives.Rayf ray, SceneNode3D node, PickResult3D best) {
        if (!node.isVisible()) return best;
        if (node.getModel() != null) {
            ModelInstance3D instance = new ModelInstance3D().setModel(node.getModel()).setMaterial(node.getMaterial())
                    .setParentTransform(node.getWorldTransform(new org.joml.Matrix4f()));
            best = pickInstance(ray, instance, node, best);
        }
        for (SceneNode3D child : node.getChildren()) best = pickNode(ray, child, best);
        return best;
    }

    /**
     * Replaces the current best result only for a strictly closer finite hit.
     * A winning hit allocates a position vector from the ray equation and retains
     * the supplied instance and optional original node in the result.
     *
     * @param ray      the world-space query ray
     * @param instance the instance whose transformed triangles should be tested
     * @param node     the original hierarchy node, or null for a loose instance
     * @param best     the previously nearest hit, or null
     * @return the original best result or a newly allocated closer hit
     */
    private PickResult3D pickInstance(org.joml.primitives.Rayf ray, ModelInstance3D instance, SceneNode3D node, PickResult3D best) {
        float distance = instance.intersect(ray);
        if (!Float.isFinite(distance) || (best != null && distance >= best.distance())) return best;
        return new PickResult3D(instance, node, distance, new org.joml.Vector3f(
                ray.oX + ray.dX * distance, ray.oY + ray.dY * distance, ray.oZ + ray.dZ * distance));
    }

    /**
     * Appends a loose renderable without copying it or checking for duplicates.
     * Backend compatibility is checked later by the model batch at submission.
     *
     * @param renderable the nonnull object to append
     * @throws NullPointerException if renderable is null
     */
    public void add(Renderable3D renderable) {
        if (renderable == null) throw new NullPointerException("renderable");
        renderables.add(renderable);
    }

    /**
     * Removes the first matching loose entry using list equality semantics.
     * Other duplicates remain, and no model or GPU resources are disposed.
     *
     * @param renderable the entry to remove; null matches no normally added entry
     * @return whether a matching entry was removed
     */
    public boolean remove(Renderable3D renderable) {
        return renderables.remove(renderable);
    }

    /**
     * Adds a parentless hierarchy root unless it is already present. The parent
     * check occurs before the duplicate check. Descendants stay attached to their
     * existing hierarchy; this method does not copy or reparent anything.
     *
     * @param node the nonnull parentless root to register
     * @throws NullPointerException     if node is null
     * @throws IllegalArgumentException if node currently has a parent
     */
    public void addNode(SceneNode3D node) {
        if (node == null) throw new NullPointerException("node");
        if (node.getParent() != null) throw new IllegalArgumentException("Only root nodes can be added to a scene");
        if (nodes.contains(node)) return;
        nodes.add(node);
    }

    /**
     * Removes a registered hierarchy root without changing its children or
     * disposing resources. This only changes membership in this scene.
     *
     * @param node the root to remove
     * @return whether the root was present and removed
     */
    public boolean removeNode(SceneNode3D node) {
        return nodes.remove(node);
    }

    /**
     * Removes all loose entries and registered roots. Referenced objects remain
     * alive, node hierarchies remain intact, and native resources are not disposed.
     */
    public void clear() {
        renderables.clear();
        nodes.clear();
        lights.clear();
    }

    /**
     * Counts top-level membership entries, including duplicate loose renderables.
     * Descendants, model parts, visibility and eventual draw calls are not counted.
     *
     * @return loose-entry count plus registered-root count
     */
    public int size() {
        return renderables.size() + nodes.size();
    }

    /**
     * Returns an unmodifiable live view of loose membership in insertion order.
     * Later scene additions and removals appear in the view; contained objects
     * are still mutable. Copy the list when a stable membership snapshot is needed.
     *
     * @return a read-only list view backed by this scene's loose entries
     */
    public List<Renderable3D> getRenderables() {
        return Collections.unmodifiableList(renderables);
    }

    // Allocation-free package traversal; membership must remain stable during collection.
    int renderableCount() {return renderables.size();}
    Renderable3D renderableAt(int index) {return renderables.get(index);}
    int nodeCount() {return nodes.size();}
    SceneNode3D nodeAt(int index) {return nodes.get(index);}

    /**
     * Returns an unmodifiable live view of registered roots in insertion order.
     * This does not freeze node hierarchies or prevent later scene membership changes.
     *
     * @return a read-only list view backed by this scene's root entries
     */
    public List<SceneNode3D> getNodes() {
        return Collections.unmodifiableList(nodes);
    }

    /**
     * Queues loose renderables followed by each root hierarchy into an already
     * begun model batch. The batch decides visibility and supported backends.
     * This method neither begins nor ends the batch, enabling several scenes
     * to share one caller-managed submission interval.
     *
     * <p>If submission fails, earlier accepted entries remain queued until the
     * caller cancels or otherwise manages the batch. Use {@link #render} when
     * this scene should own the begin/end/cancel sequence.</p>
     *
     * @param batch the nonnull batch already begun with an active render state
     * @return the sum of accepted submissions reported by the batch and node traversal
     * @throws NullPointerException  if batch is null
     * @throws IllegalStateException if the batch rejects submission outside its active interval
     */
    public int submit(ModelBatch3D batch) {
        if (batch == null) throw new NullPointerException("batch");
        int accepted = batch.submit(renderables);
        for (SceneNode3D node : nodes) {
            accepted += batch.submit(node);
        }
        return accepted;
    }

    /**
     * Begins the batch, submits this scene and renders queued work through batch
     * end. After a successful begin, cancellation always runs in cleanup, including
     * when scene traversal or drawing throws. The scene and its objects are retained.
     *
     * <p>The result reflects the batch's visible-submission count, not a pixel
     * readback or proof that any fragment reached the framebuffer. Rendering
     * requires the graphics context expected by the batch to be current.</p>
     *
     * @param batch the nonnull batch whose lifecycle is managed for this call
     * @param state the nonnull render state and active camera configuration
     * @return whether the completed batch reports any visible submissions
     * @throws NullPointerException if batch or state is null
     */
    public boolean render(ModelBatch3D batch, MeshRenderState3D state) {
        if (batch == null) throw new NullPointerException("batch");
        if (state == null) throw new NullPointerException("state");
        batch.begin(state);
        try {
            submit(batch);
            batch.end();
        } finally {
            batch.cancel();
        }
        return batch.getVisibleCount() > 0;
    }
}
