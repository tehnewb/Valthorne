package valthorne.graphics.model;

import valthorne.camera.Camera3D;
import valthorne.graphics.Color;
import valthorne.graphics.texture.Texture;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;

/**
 * Frame submission batch with frustum culling, material batching and shared mesh/billboard transparency ordering.
 * Built-in instances and materials are snapshotted at submission. Custom renderables must remain stable until end.
 * Owns the supplied mesh and billboard batches, but never owns submitted models or textures.
 * Opaque submissions group by material identity, while other passes sort back to front
 * across mesh and billboard submissions. State and camera references remain live during
 * a frame; do not mutate them between begin and end.
 *
 * <pre>{@code
 * ModelBatch3D batch = new ModelBatch3D();
 * batch.begin(state);
 * try {
 *     batch.submit(instance);
 *     batch.end();
 * } finally {
 *     batch.cancel();
 * }
 * // After the batch is no longer needed:
 * batch.close();
 * }</pre>
 *
 * <p>Construction and rendering use GPU resources on the owning graphics thread.
 * Material snapshots are cached by source identity per frame, so the first submission
 * using a material determines the snapshot reused by later submissions of that same
 * source. Custom renderable geometry remains borrowed until end or cancellation.</p>
 *
 * @author Albert Beaupre
 */
public final class ModelBatch3D implements AutoCloseable {
    private final MeshBatch3D meshBatch; // Owned triangle submission backend.
    private final BillboardBatch3D billboardBatch; // Owned billboard submission backend.
    private final ArrayList<Submission> queue = new ArrayList<>(); // Queued frame submissions before ordering and emission.
    private final IdentityHashMap<Material3D, Material3D> materials = new IdentityHashMap<>(); // Per-frame source-identity material snapshots.
    private final IdentityHashMap<Material3D, Integer> materialOrder = new IdentityHashMap<>(); // Stable encounter order of material snapshots.
    private final Material3D defaultMaterial = new Material3D(); // Fallback for renderables without a material.
    private MeshRenderState3D activeState; // Borrowed live render state for the active frame.
    private Camera3D activeCamera; // Borrowed camera rebuilt when begin is called.
    private boolean begun, disposed, cullingEnabled = true; // Frame lifecycle, disposal state, and frustum-culling enablement.
    private int submittedCount, visibleCount, culledCount; // Current or most recent frame's submission outcome counters.
    private int culledSubtreeCount; // Number of hierarchy branches rejected before per-item traversal.

    /**
     * Allocates default owned mesh and billboard backends. Construct on the graphics
     * thread and dispose this batch when all frame submissions are finished.
     */
    public ModelBatch3D() {this(new MeshBatch3D());}

    /**
     * Allocates an owned mesh backend with the requested float capacity and a default
     * billboard backend. Capacity units describe float storage rather than vertex count.
     *
     * @param initialCapacityFloats initial mesh float-buffer capacity
     */
    public ModelBatch3D(int initialCapacityFloats) {this(new MeshBatch3D(initialCapacityFloats));}

    /**
     * Takes ownership of a mesh backend and allocates a default billboard backend.
     * The supplied backend is disposed with this batch.
     *
     * @param meshBatch non-null mesh backend
     */
    public ModelBatch3D(MeshBatch3D meshBatch) {this(meshBatch, new BillboardBatch3D());}

    /**
     * Retains and takes disposal responsibility for both supplied rendering backends.
     * They must not be used independently while this batch is emitting a frame.
     *
     * @param meshBatch owned mesh backend
     * @param billboardBatch owned billboard backend
     * @throws NullPointerException if either backend is null
     */
    public ModelBatch3D(MeshBatch3D meshBatch, BillboardBatch3D billboardBatch) {
        this.meshBatch = java.util.Objects.requireNonNull(meshBatch);
        this.billboardBatch = java.util.Objects.requireNonNull(billboardBatch);
    }

    /**
     * Copies instance material settings, multiplies instance and part tints, and borrows
     * the part texture only when the instance has none. A nonopaque part pass replaces
     * an opaque instance pass using the material setter's normal depth-write behavior.
     *
     * @param instance instance-level overrides
     * @param part OBJ part material
     * @return newly combined material sharing texture resources
     */
    private static Material3D combine(Material3D instance, Material3D part) {
        Material3D result = instance.copy();
        Color a = instance.getTint(), b = part.getTint();
        result.setTint(new Color(a.r() * b.r(), a.g() * b.g(), a.b() * b.b(), a.a() * b.a()));
        if (instance.getTexture() == null) result.setTexture(part.getTexture());
        if (instance.getRenderPass() == RenderPass3D.OPAQUE && part.getRenderPass() != RenderPass3D.OPAQUE)
            result.setRenderPass(part.getRenderPass());
        return result;
    }

    /**
     * Starts a frame, rebuilds the state's camera with its retained viewport dimensions,
     * prepares non-shadow lighting, clears submissions and material snapshots, and resets
     * statistics. Does not snapshot the state or camera themselves.
     *
     * @param state live render state containing a camera
     * @throws IllegalStateException if disposed or already active
     * @throws IllegalArgumentException if state or its camera is null
     */
    public void begin(MeshRenderState3D state) {
        if (disposed) throw new IllegalStateException("ModelBatch3D is disposed");
        if (begun) throw new IllegalStateException("ModelBatch3D is already active");
        if (state == null || state.getCamera() == null) throw new IllegalArgumentException("A camera is required");
        activeState = state;
        activeCamera = state.getCamera();
        // Keep camera matrices and frustum in sync with direct position/direction edits.
        activeCamera.rebuild(activeCamera.getViewportWidth(), activeCamera.getViewportHeight());
        if (state.getLighting() != null && !state.isShadowPass()) state.getLighting().prepare(activeCamera);
        queue.clear();
        materials.clear();
        materialOrder.clear();
        submittedCount = visibleCount = culledCount = 0;
        culledSubtreeCount = 0;
        begun = true;
    }

    /**
     * Submits a model instance through the common renderable path, including culling,
     * snapshotting, and OBJ part expansion. Requires an active frame.
     *
     * @param instance instance to submit, or null to count as culled
     * @return whether the submission was accepted
     */
    public boolean submit(ModelInstance3D instance) {return submit((Renderable3D) instance);}

    /**
     * Counts and tests a renderable for visibility and optional frustum culling. Rejects
     * missing model or billboard texture data. Copies built-in instances and sprites;
     * custom mesh/billboard implementations remain live until end. OBJ submission uploads
     * its textures and expands parts with combined materials. Shadow filtering can leave
     * an accepted submission with no queued draw, so true does not guarantee GPU output.
     *
     * @param renderable supported mesh or billboard, or null
     * @return true when accepted by submission checks, false when culled
     * @throws IllegalStateException if no frame is active
     * @throws IllegalArgumentException if a visible renderable has an unsupported type
     */
    public boolean submit(Renderable3D renderable) {
        ensureBegun();
        submittedCount++;
        if (renderable == null || !renderable.isRenderableVisible()
                || (cullingEnabled && !renderable.isVisible(activeCamera))) {
            culledCount++;
            return false;
        }
        if (!(renderable instanceof MeshRenderable3D) && !(renderable instanceof BillboardRenderable3D))
            throw new IllegalArgumentException("Unsupported renderable: " + renderable.getClass().getName());
        if (renderable instanceof ModelInstance3D instance && instance.getModel() == null) {
            culledCount++;
            return false;
        }
        if (renderable instanceof BillboardRenderable3D billboard
                && (billboard.getTextureRegion() == null || billboard.getTextureRegion().getTexture() == null)) {
            culledCount++;
            return false;
        }
        if (renderable instanceof ModelInstance3D instance && instance.getModel() instanceof ObjModel3D obj) {
            obj.uploadTextures();
            for (ObjModel3D.Part part : obj.getParts()) {
                Material3D combined = combine(instance.getMaterial(), part.material());
                enqueue(new ModelInstance3D().set(instance).setModel(part.model()).setMaterial(combined));
            }
        } else if (renderable instanceof ModelInstance3D instance) enqueue(new ModelInstance3D().set(instance));
        else if (renderable instanceof BillboardSprite3D billboard) enqueue(new BillboardSprite3D().set(billboard));
        else enqueue(renderable);
        visibleCount++;
        return true;
    }

    /**
     * Filters shadow-pass entries to opaque shadow-casting meshes, snapshots materials
     * by identity, and captures sort depth and material encounter order. Shadow material
     * snapshots force depth testing and writing. Does not increment submission counters.
     *
     * @param renderable stable or already copied renderable to queue
     */
    private void enqueue(Renderable3D renderable) {
        Material3D source = renderable.getMaterial() == null ? defaultMaterial : renderable.getMaterial();
        if (activeState.isShadowPass() && (!(renderable instanceof MeshRenderable3D)
                || !source.isCastsShadow() || source.getRenderPass() != RenderPass3D.OPAQUE)) return;
        Material3D material = materials.computeIfAbsent(source, Material3D::copy);
        if (activeState.isShadowPass()) material.setDepthTest(true).setDepthWrite(true);
        int order = materialOrder.computeIfAbsent(material, key -> materialOrder.size());
        queue.add(new Submission(renderable, material, renderable.getSortDepth(activeCamera), order));
    }

    /**
     * Traverses a scene node through its submission logic, allowing whole-subtree culling.
     * The node and its resources remain caller-owned.
     *
     * @param node scene subtree root
     * @return accepted submissions reported by the subtree
     * @throws NullPointerException if node is null
     * @throws IllegalStateException if no frame is active
     */
    public int submit(SceneNode3D node) {
        ensureBegun();
        return java.util.Objects.requireNonNull(node).submit(this);
    }

    /**
     * Submits each value in iteration order and counts accepted values. Iteration or
     * submission failures leave earlier entries queued; cancel if abandoning the frame.
     *
     * @param renderables values to submit
     * @return number accepted by the common submission path
     * @throws IllegalStateException if no frame is active
     */
    public int submit(Iterable<? extends Renderable3D> renderables) {
        ensureBegun();
        int count = 0;
        for (Renderable3D renderable : renderables) if (submit(renderable)) count++;
        return count;
    }

    /**
     * Orders queued entries by pass, then material order for opaque content or descending
     * depth for other passes. Emits compatible material/backend/texture runs. Restores
     * captured graphics state and the prior state material, and clears the active frame
     * in finally even when emission fails. Does not dispose submitted resources.
     *
     * @throws IllegalStateException if no frame is active
     */
    public void end() {
        ensureBegun();
        Material3D previous = activeState.getMaterial();
        try (RenderStateSnapshot3D ignored = new RenderStateSnapshot3D()) {
            queue.sort(Comparator.comparingInt((Submission s) -> s.material.getRenderPass().ordinal())
                    .thenComparingDouble(s -> s.material.getRenderPass() == RenderPass3D.OPAQUE ? s.materialOrder : -s.depth));
            for (int i = 0; i < queue.size(); ) {
                Submission first = queue.get(i);
                boolean mesh = first.renderable instanceof MeshRenderable3D;
                Texture texture = mesh ? null : ((BillboardRenderable3D) first.renderable).getTextureRegion().getTexture();
                if (mesh) meshBatch.begin();
                else billboardBatch.begin(texture);
                int end = i;
                while (end < queue.size()) {
                    Submission s = queue.get(end);
                    if (s.material != first.material || (s.renderable instanceof MeshRenderable3D) != mesh) break;
                    if (!mesh && ((BillboardRenderable3D) s.renderable).getTextureRegion().getTexture() != texture)
                        break;
                    if (mesh) ((MeshRenderable3D) s.renderable).emit(meshBatch);
                    else ((BillboardRenderable3D) s.renderable).emit(billboardBatch, activeCamera);
                    end++;
                }
                activeState.setMaterial(first.material);
                if (mesh) meshBatch.renderInBatch(activeState);
                else billboardBatch.render(activeState);
                i = end;
            }
        } finally {
            activeState.setMaterial(previous);
            cancel();
        }
    }

    /**
     * Discards queued submissions and material snapshots and clears active references.
     * Safe when no frame is active. Retains counters for inspection and does not draw.
     */
    public void cancel() {
        queue.clear();
        materials.clear();
        materialOrder.clear();
        activeState = null;
        activeCamera = null;
        begun = false;
    }

    /**
     * Returns attempted submissions, including entries counted by subtree rejection.
     * Resets at begin and is retained after end or cancel.
     *
     * @return submitted count
     */
    public int getSubmittedCount() {return submittedCount;}

    /**
     * Returns submissions accepted before shadow filtering and OBJ part expansion.
     * This is not a draw-call or emitted-part count.
     *
     * @return accepted submission count
     */
    public int getVisibleCount() {return visibleCount;}

    /**
     * Returns rejected submissions, including null/invisible/missing-data entries and
     * models counted by whole-subtree rejection.
     *
     * @return culled submission count
     */
    public int getCulledCount() {return culledCount;}

    /**
     * Returns hierarchy branches rejected by bounds before individual submission.
     * Resets at the start of each frame.
     *
     * @return rejected subtree count
     */
    public int getCulledSubtreeCount() {return culledSubtreeCount;}

    /**
     * Tests world-space subtree bounds against the active frustum when culling is enabled.
     * Rejection increments submitted and culled model counts and the subtree counter.
     *
     * @param bounds subtree world-space bounds
     * @param models descendant model count to include in statistics
     * @return whether traversal should skip the subtree
     * @throws IllegalStateException if no frame is active
     */
    boolean rejectSubtree(org.joml.primitives.AABBf bounds, int models) {
        ensureBegun();
        if (cullingEnabled && !activeCamera.getFrustum().testAab(bounds.minX, bounds.minY, bounds.minZ, bounds.maxX, bounds.maxY, bounds.maxZ)) {
            submittedCount += models;
            culledCount += models;
            culledSubtreeCount++;
            return true;
        }
        return false;
    }

    /**
     * Returns the owned mesh backend for inspection. Do not dispose it separately or
     * modify it during this batch's emission.
     *
     * @return owned mesh backend
     */
    public MeshBatch3D getMeshBatch() {return meshBatch;}

    /**
     * Returns the owned billboard backend. Its lifecycle ends when this batch is disposed.
     *
     * @return owned billboard backend
     */
    public BillboardBatch3D getBillboardBatch() {return billboardBatch;}

    /**
     * Reports whether frustum checks are enabled; logical visibility and missing-data
     * checks still apply when frustum culling is disabled.
     *
     * @return current frustum-culling flag
     */
    public boolean isCullingEnabled() {return cullingEnabled;}

    /**
     * Changes frustum-culling behavior for subsequent submissions and subtree tests.
     * Already queued entries are not reevaluated.
     *
     * @param enabled whether to test bounds against the camera frustum
     * @return this batch
     */
    public ModelBatch3D setCullingEnabled(boolean enabled) {
        cullingEnabled = enabled;
        return this;
    }

    /**
     * Cancels pending work and disposes both owned backends. Repeated completed disposal
     * is a no-op; submitted models, materials, and textures are never disposed here.
     */
    public void dispose() {
        if (disposed) return;
        cancel();
        meshBatch.dispose();
        billboardBatch.dispose();
        disposed = true;
    }

    /**
     * Delegates to dispose, releasing owned backends and cancelling pending submissions.
     * Use on the owning graphics thread.
     */
    @Override
    public void close() {dispose();}

    /**
     * Enforces the active-frame precondition before submission, traversal, or end.
     *
     * @throws IllegalStateException if begin has not established an active frame
     */
    private void ensureBegun() {if (!begun) throw new IllegalStateException("Call ModelBatch3D.begin first");}

    /**
     * Retains one stable renderable reference and captured material/order values for
     * deferred frame emission. Texture resources remain shared with their owners.
     *
     * <p>The entry combines borrowed geometry with the material state captured for this frame.
     * Sorting uses the captured depth and material order, so those keys do not change if
     * external scene state changes before queued emission.</p>
     *
     * @param renderable submitted geometry provider
     * @param material per-frame material snapshot
     * @param depth captured camera sort depth
     * @param materialOrder encounter order used for opaque grouping
     * @author Albert Beaupre
     */
    private record Submission(Renderable3D renderable, Material3D material, float depth, int materialOrder) {
    }
}
