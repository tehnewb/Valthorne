package valthorne.ui;

import valthorne.graphics.texture.TextureBatch;
import valthorne.ui.nodes.nano.NanoNode;

import static org.lwjgl.nanovg.NanoVG.*;

/**
 * Coordinates texture-batch and NanoVG painting within one UI root draw.
 * Containers retain responsibility for child traversal; this context selects
 * the backend for each node and preserves the caller's backend across nested
 * drawing. Flushing at transitions keeps mixed children in painter order.
 *
 * <h2>Custom Container Integration</h2>
 * <p>During a container's draw callback, render each child through its normal
 * dispatch entry point. For example, given a child belonging to the active root:</p>
 * <pre>{@code
 * UIRenderContext context = getRoot().getRenderContext();
 * TextureBatch activeBatch = context.getBatch();
 * child.render(activeBatch);
 * }</pre>
 * <p>A container can use {@link #drawChildren(UIContainer, UINode)} to retain
 * compatible sibling painting state without changing traversal order. Scoped
 * translations and clips can be balanced with try-with-resources:</p>
 * <pre>{@code
 * try (UIRenderContext.Scope offset = context.translate(12, 8)) {
 *     context.drawChildren(container, null);
 * }
 * }</pre>
 *
 * <h2>Shared Coordinates and Drawing State</h2>
 * <p>The batch's translation and clip scopes are the common spatial state in
 * bottom-left world coordinates. Before a NanoVG node draws, the context resets
 * the NanoVG transform and scissor, applies the captured camera offset and zoom,
 * converts the clip rectangle using the render-space height, and applies the
 * batch translation with its Y component negated. NanoVG state is saved before
 * the callback and restored afterward, including when that callback throws.</p>
 *
 * <h2>Lifecycle</h2>
 * <p>{@link UIRoot#draw()} creates a fresh context after beginning its batch and
 * initializing its NanoVG frame. The root owns those resources; this context
 * neither allocates nor disposes them. Widgets must not begin or end NanoVG
 * frames themselves. Backend transitions flush pending work, and returning to
 * texture painting restores the batch's graphics state.</p>
 *
 * <p>Use this context only during its owning root's draw on the graphics-context
 * thread. Camera values are captured at construction, while batch translation
 * and clipping are read for each NanoVG node. Custom containers should use
 * {@link UINode#render(TextureBatch)} with {@link #getBatch()} so nested nodes
 * participate in this dispatch and preserve mixed-backend ordering.</p>
 *
 * @author Albert Beaupre
 * @see UIRoot#getRenderContext()
 * @see NanoNode
 */
public final class UIRenderContext {
    private final TextureBatch batch; // Borrowed active batch supplying shared translation and clip state.
    private final long vg; // Borrowed NanoVG context handle; zero means NanoVG is unavailable.
    private final float height; // Captured render-space height used to convert bottom-left Y coordinates.
    private final float cameraX; // Captured horizontal camera offset applied before NanoVG scaling.
    private final float cameraY; // Captured vertical camera offset in NanoVG's coordinate orientation.
    private final float zoom; // Captured camera scale, or one when the root has no viewport camera.
    private final UIRoot root; // Owning root supplying inspection settings and per-draw snapshots.
    private boolean nanoActive; // True while NanoVG is selected; false while texture painting is selected.
    private int nodesDrawn, backendSwitches, nanoFlushes; // Node attempts, backend transitions, and NanoVG submissions for this draw.

    /**
     * Borrows the root's drawing resources and captures its render-space camera
     * mapping for the current draw. Without a viewport camera, zoom is one and
     * both camera offsets are zero. This constructor does not begin a batch or
     * NanoVG frame; the root must prepare those resources first.
     *
     * @param batch the already-begun batch used by the owning root
     * @param vg    the root's initialized NanoVG handle, or zero for texture-only drawing
     * @param root  the root supplying the render-space height and optional viewport
     * @throws NullPointerException if {@code root} is {@code null}
     */
    UIRenderContext(TextureBatch batch, long vg, UIRoot root) {
        this.batch = batch;
        this.root = root;
        this.vg = vg;
        this.height = root.getRenderSpaceHeight();
        var viewport = root.getViewport();
        var camera = viewport == null ? null : viewport.getCamera();
        zoom = camera == null ? 1f : camera.getZoom();
        cameraX = camera == null ? 0f : viewport.getWorldWidth() * 0.5f - camera.getCenter().x() * zoom;
        cameraY = camera == null ? 0f : height * 0.5f - (height - camera.getCenter().y()) * zoom;
    }

    /**
     * Returns visible-node dispatch attempts so far. Null and hidden nodes are
     * excluded; a visible node is counted before inspection and callback execution,
     * so a failing callback still contributes. Repeated dispatches count separately.
     *
     * @return the current draw's visible-node attempt count
     */
    public int getNodesDrawn() {return nodesDrawn;}

    /**
     * Returns attempted changes between painting backends. Selecting the current
     * backend adds nothing. A transition increments the counter before flushing,
     * so a failed transition can still be included.
     *
     * @return backend transitions attempted during this root draw
     */
    public int getBackendSwitches() {return backendSwitches;}

    /**
     * Returns NanoVG end-frame submissions made when returning to texture painting.
     * The count increments after the native end-frame call, before restoring batch
     * bindings. It is a submission count rather than a GPU draw-call count.
     *
     * @return NanoVG submissions completed by this context so far
     */
    public int getNanoFlushes() {return nanoFlushes;}

    /**
     * Returns the active batch shared by all nodes in this draw. This is the
     * root-owned instance, not a copy: its translation and clip scopes also
     * control subsequent NanoVG node dispatch. Callers must balance any scopes
     * they change and leave batch lifetime management to the root.
     *
     * @return the borrowed batch to pass to {@link UINode#render(TextureBatch)}
     */
    public TextureBatch getBatch() {return batch;}

    /**
     * Dispatches one visible node to its supported painting backend. Null or
     * invisible nodes produce no drawing or backend transitions. Nodes implementing
     * {@link NanoNode} receive the NanoVG handle; all other nodes receive the batch.
     * Child traversal remains the node's responsibility.
     *
     * <p>Each callback runs with its backend selected. NanoVG callbacks additionally
     * receive the shared clip and translation state mapped into NanoVG coordinates.
     * A {@code finally} block restores the previous backend, allowing a container
     * to paint its foreground after children that use the other backend. NanoVG
     * callbacks also restore saved NanoVG state. Callback exceptions propagate
     * after this cleanup; already submitted drawing is not rolled back.</p>
     *
     * @param node the node to paint, or {@code null} to do nothing
     * @throws IllegalStateException if a visible NanoVG node is drawn without an
     *                               available NanoVG context
     */
    public void draw(UINode node) {drawNode(node, true);}

    /**
     * Performs node dispatch, counting and optional inspection for a visible node.
     * NanoVG callbacks receive saved drawing state and the mapped batch clip and
     * translation. Texture callbacks receive the shared active batch directly.
     *
     * <p>When requested, a NanoVG callback restores its entry backend in cleanup.
     * Disabling that restoration permits consecutive NanoVG siblings to share a
     * backend interval; the enclosing child traversal restores the parent's state.
     * Texture callbacks always restore their entry backend. NanoVG drawing state
     * is restored regardless of this flag.</p>
     *
     * @param node           the node to dispatch, or null to skip
     * @param restoreBackend whether NanoVG dispatch restores its entry backend
     * @throws IllegalStateException if a visible NanoVG node has no available context
     */
    private void drawNode(UINode node, boolean restoreBackend) {
        if (node == null || !node.isVisible()) return;
        nodesDrawn++;
        root.getInspector().record(node, root);
        boolean previousBackend = nanoActive;
        if (node instanceof NanoNode nano) {
            if (vg == 0L) throw new IllegalStateException("NanoVG context is unavailable.");
            selectBackend(true);
            nvgSave(vg);
            try {
                // Clip is already translated into world space by the batch.
                nvgResetTransform(vg);
                nvgTranslate(vg, cameraX, cameraY);
                nvgScale(vg, zoom, zoom);
                nvgResetScissor(vg);
                if (batch.isClipEnabled()) {
                    nvgScissor(vg, batch.getClipX(), height - batch.getClipY() - batch.getClipHeight(), batch.getClipWidth(), batch.getClipHeight());
                }
                nvgTranslate(vg, batch.getTranslationX(), -batch.getTranslationY());
                nano.draw(vg);
            } finally {
                nvgRestore(vg);
                if (restoreBackend) selectBackend(previousBackend);
            }
        } else {
            selectBackend(false);
            try {
                node.draw(batch);
            } finally {
                selectBackend(previousBackend);
            }
        }
    }

    /**
     * Draws children in their existing order, skipping one optional child by
     * reference identity. Null and invisible children are ignored by dispatch.
     * NanoVG siblings may retain their backend between callbacks, reducing
     * transitions while preserving painter order.
     *
     * <p>The parent's entry backend is restored in a finally block, allowing the
     * parent to paint foreground content afterward. Do not structurally modify
     * the child collection while it is being traversed. This method does not draw
     * the container itself or establish translation and clip scopes for it.</p>
     *
     * @param container the container whose children should be traversed
     * @param excluded  the child to skip, or null when no nonnull child is excluded
     * @throws NullPointerException if container is null
     */
    public void drawChildren(UIContainer container, UINode excluded) {
        boolean parentBackend = nanoActive;
        try {
            for (int i = 0; i < container.size(); i++) {
                UINode child = container.get(i);
                if (child != excluded) drawNode(child, false);
            }
        } finally {selectBackend(parentBackend);}
    }

    /**
     * Pushes an additional translation in top-left layout units. Positive X moves
     * right and positive Y moves down; Y is negated when stored in the batch's
     * bottom-left coordinate system. Both painting backends read this shared state.
     *
     * @param x additional horizontal displacement in layout units
     * @param y additional downward displacement in layout units
     * @return a scope that pops this translation when closed; close scopes in reverse order
     * @throws IllegalStateException if the batch is not drawing or its translation stack is full
     */
    public Scope translate(float x, float y) {
        batch.pushTranslation(x, -y);
        return new Scope(batch::popTranslation);
    }

    /**
     * Pushes a clip rectangle expressed in top-left render-space coordinates.
     * Converts its Y origin using the captured render-space height and passes it
     * to the batch, where nested scissors intersect. Coordinates must already
     * include any desired translation; this method does not add the batch's
     * translation to the rectangle. Prefer nonnegative finite dimensions.
     *
     * @param x          the left edge in render-space layout units
     * @param y          the top edge in render-space layout units
     * @param width      the requested clip width
     * @param clipHeight the requested clip height
     * @return a scope restoring the previous clip when closed, in reverse nesting order
     * @throws IllegalStateException if the batch is not drawing or its clip stack is full
     */
    public Scope clip(float x, float y, float width, float clipHeight) {
        batch.beginScissor(x, height - y - clipHeight, width, clipHeight);
        return new Scope(batch::endScissor);
    }

    /**
     * Draws recorded inspector outlines after the normal tree and overlays.
     * Disabled outlines or an unavailable NanoVG handle produce no work. Each
     * entry uses its recorded bounds and optional clip, with camera mapping
     * applied to the inspection layer.
     *
     * <p>Captured nodes use orange outlines, focused nodes cyan, and other nodes
     * a translucent blue. Captured or focused outlines are two units wide; others
     * are one. Cleanup restores NanoVG state and selects texture painting, rather
     * than restoring an arbitrary entry backend. Inspection contributes backend
     * transition and flush counts but does not increment node dispatch counts.</p>
     */
    void drawInspection() {
        if (!root.getInspector().isOutlines() || vg == 0) return;
        selectBackend(true);
        nvgSave(vg);
        try {
            nvgResetTransform(vg);
            nvgResetScissor(vg);
            nvgTranslate(vg, cameraX, cameraY);
            nvgScale(vg, zoom, zoom);
            for (var entry : root.getInspector().entries()) {
                nvgSave(vg);
                if (entry.clip() != null) {
                    var c = entry.clip();
                    nvgScissor(vg, c.x(), c.y(), c.width(), c.height());
                }
                var b = entry.bounds();
                nvgBeginPath(vg);
                nvgRect(vg, b.x(), b.y(), b.width(), b.height());
                nvgStrokeWidth(vg, entry.focused() || entry.captured() ? 2 : 1);
                nvgStrokeColor(vg, NanoUtility.color1(new valthorne.graphics.Color(entry.captured() ? 0xFFFFB454 : entry.focused() ? 0xFF67E8F9 : 0x665B8DEF)));
                nvgStroke(vg);
                nvgRestore(vg);
            }
        } finally {
            nvgRestore(vg);
            selectBackend(false);
        }
    }

    /**
     * Changes the active painter, flushing the previous backend's pending work.
     * Selecting the current backend is a no-op. Entering NanoVG flushes texture
     * geometry; returning to textures submits NanoVG commands with end-frame and
     * restores graphics bindings through {@link TextureBatch#resumeAfterExternalDraw()}.
     *
     * <p>No new NanoVG frame is begun here, preserving state needed by nested
     * callbacks. The transition counter increments before operations execute,
     * while the selected-backend flag changes only after they finish.</p>
     *
     * @param nano true to select NanoVG, false to select texture painting
     */
    private void selectBackend(boolean nano) {
        if (nanoActive == nano) return;
        backendSwitches++;
        if (nano) {
            batch.flush();
        } else {
            nvgEndFrame(vg);
            nanoFlushes++;
            batch.resumeAfterExternalDraw();
        }
        nanoActive = nano;
    }

    /**
     * Owns one pending restoration action for a translation or clipping scope.
     * Use with try-with-resources inside the root draw and close nested scopes in
     * reverse order. Closing is idempotent, but the scope does not validate nesting
     * order or extend the lifetime of the batch it restores.
     *
     * <p>The restoration reference is cleared before invocation, so an action that
     * throws is not retried on another close. Instances are not synchronized.</p>
     *
     * @author Albert Beaupre
     */
    public static final class Scope implements AutoCloseable {
        private Runnable restore; // One-shot restoration callback, cleared before execution.

        /**
         * Retains a restoration callback without running it or changing batch state.
         * The enclosing context creates this only after pushing the matching scope.
         *
         * @param restore the action to run once on close, or null for an inactive scope
         */
        private Scope(Runnable restore) {this.restore = restore;}

        /**
         * Runs the pending restoration once and marks this scope closed before
         * invoking it. Later calls do nothing, including after restoration throws.
         * Batch state failures propagate to the caller.
         *
         * @throws IllegalStateException if the underlying batch restoration rejects
         *                               the current drawing or stack state
         */
        @Override
        public void close() {
            if (restore != null) {
                Runnable action = restore;
                restore = null;
                action.run();
            }
        }
    }
}
