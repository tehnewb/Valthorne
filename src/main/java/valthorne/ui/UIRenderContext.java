package valthorne.ui;

import valthorne.Window;
import valthorne.graphics.font.slug.SlugFont;
import valthorne.graphics.texture.TextureBatch;
import valthorne.graphics.shader.Shader;
import valthorne.ui.nodes.nano.NanoNode;

import static org.lwjgl.nanovg.NanoVG.*;
import valthorne.graphics.Color;

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
 * translations and clips can be balanced with try/finally:</p>
 * <pre>{@code
 * UIRenderScope offset = context.translate(12, 8);
 * try {
 *     context.drawChildren(container, null);
 * } finally {
 *     offset.restore();
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
 * <h2>Rotation</h2>
 * <p>This context composes cached node rotations around layout centers. Previous
 * transforms live in traversal locals rather than a batch stack. NanoVG and Slug
 * receive the same transform; textured nodes use a root-owned rotation shader.
 * Entering or leaving a rotated subtree flushes texture work before changing
 * shader state. Visibility and rotation remain responsibilities of the UI.</p>
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
    /*
     * Preview opacity that keeps content visible while exposing items beneath it.
     */
    private static final float DRAG_OPACITY = 0.82f;
    private final TextureBatch batch; // Borrowed active batch supplying shared translation and clip state.
    private final long vg; // Borrowed NanoVG context handle; zero means NanoVG is unavailable.
    private final float height; // Captured render-space height used to convert bottom-left Y coordinates.
    private final float cameraX; // Captured horizontal camera offset applied before NanoVG scaling.
    private final float cameraY; // Captured vertical camera offset in NanoVG's coordinate orientation.
    private final float zoom; // Captured camera scale, or one when the root has no viewport camera.
    private final UIRoot root; // Owning root supplying inspection settings and per-draw snapshots.
    private float transformCosine = 1f; // Accumulated UI rotation cosine shared by all rendering backends.
    private float transformSine; // Accumulated UI rotation sine; parents compose once per rotated node.
    private float transformX; // Accumulated rigid transform translation in world coordinates.
    private float transformY; // Accumulated rigid transform translation in world coordinates.
    private boolean nanoActive; // True while NanoVG is selected; false while texture painting is selected.
    private float dragOpacity = 1f; // Opacity applied only while painting the lifted item.
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
     * Paints curve glyphs within a Nano control while retaining backend order.
     * Coordinates use the same top-left logical space as the Nano callback;
     * clipping intersects the control and its current ancestor clip.
     *
     * @param node owning Nano control
     * @param font borrowed curve font
     * @param text line contents
     * @param x left pen coordinate
     * @param baseline top-down baseline coordinate
     * @param size logical em size
     * @param color borrowed tint
     */
    public void drawNanoSlug(UINode node, SlugFont font, String text, float x, float baseline, float size, Color color) {
        selectBackend(false);
        float tx = batch.getTranslationX();
        float ty = batch.getTranslationY();
        float left = node.getAbsoluteX() + tx;
        float bottom = height - node.getAbsoluteY() - node.getHeight() + ty;
        float right = left + node.getWidth();
        float top = bottom + node.getHeight();
        if (batch.isClipEnabled()) {
            left = Math.max(left, batch.getClipX());
            bottom = Math.max(bottom, batch.getClipY());
            right = Math.min(right, batch.getClipX() + batch.getClipWidth());
            top = Math.min(top, batch.getClipY() + batch.getClipHeight());
        }
        batch.beginScissor(left, bottom, Math.max(0f, right - left), Math.max(0f, top - bottom));
        try {
            font.draw(batch, text, x, batch.alignTextBaseline(height - baseline), size, color);
        } finally {
            batch.endScissor();
            selectBackend(true);
        }
    }

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
     * Paints a lifted item without a separate background. NanoVG and texture
     * content share the same slight transparency, which is restored before
     * ordinary UI painting resumes, even if the widget throws while drawing.
     *
     * @param node the existing list or tree item following the pointer
     */
    void drawDragPreview(UINode node) {
        float previousDragOpacity = dragOpacity;
        float previousBatchOpacity = batch.getOpacityMultiplier();
        dragOpacity = DRAG_OPACITY;
        batch.setOpacityMultiplier(previousBatchOpacity * DRAG_OPACITY);
        try {
            draw(node);
        } finally {
            dragOpacity = previousDragOpacity;
            batch.setOpacityMultiplier(previousBatchOpacity);
        }
    }

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
        if (node == null || !node.isVisible() || root.skipsDragPreviewSource(node) || root.skipsExternalPresentation(node)) return;
        if (node.getRotation() == 0) {
            drawNodeContents(node, restoreBackend);
            return;
        }

        boolean previousNano = nanoActive;
        Shader previousShader = batch.getShader();
        float previousCosine = transformCosine;
        float previousSine = transformSine;
        float previousX = transformX;
        float previousY = transformY;
        selectBackend(false);
        batch.flush();
        UIRotationShader shader = root.rotationShader();
        batch.setShader(shader);
        float cosine = node.rotationCosine();
        float sine = node.rotationSine();
        float x = node.getAbsoluteX() + node.getWidth() * .5f + batch.getTranslationX();
        float y = height - node.getAbsoluteY() - node.getHeight() * .5f + batch.getTranslationY();
        float tx = x - cosine * x + sine * y;
        float ty = y - sine * x - cosine * y;
        transformX += previousCosine * tx - previousSine * ty;
        transformY += previousSine * tx + previousCosine * ty;
        transformCosine = previousCosine * cosine - previousSine * sine;
        transformSine = previousSine * cosine + previousCosine * sine;
        shader.transform(transformCosine, transformSine, transformX, transformY);
        batch.setTextTransform(transformCosine, transformSine, transformX, transformY);
        try {
            drawNodeContents(node, restoreBackend);
        } finally {
            try {
                selectBackend(false);
            } finally {
                transformCosine = previousCosine;
                transformSine = previousSine;
                transformX = previousX;
                transformY = previousY;
                batch.setTextTransform(previousCosine, previousSine, previousX, previousY);
                batch.setShader(previousShader);
                if (previousShader instanceof UIRotationShader previousRotation)
                    previousRotation.transform(previousCosine, previousSine, previousX, previousY);
                if (previousNano) selectBackend(true);
            }
        }
    }

    /**
     * Dispatches a visible node under its already prepared transform scope.
     *
     * @param node visible node
     * @param restoreBackend whether the previous painter must be restored
     */
    private void drawNodeContents(UINode node, boolean restoreBackend) {
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
                float c = transformCosine;
                float s = transformSine;
                nvgTransform(vg, c, -s, s, c, transformX - s * height, height * (1 - c) - transformY);
                nvgTranslate(vg, batch.getTranslationX(), -batch.getTranslationY());
                if (dragOpacity != 1f) nvgGlobalAlpha(vg, dragOpacity);
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
        } finally {
            selectBackend(parentBackend);
        }
    }

    /**
     * Pushes an additional translation in top-left layout units. Positive X moves
     * right and positive Y moves down; Y is negated when stored in the batch's
     * bottom-left coordinate system. Both painting backends read this shared state.
     *
     * @param x additional horizontal displacement in layout units
     * @param y additional downward displacement in layout units
     * @return a scope that pops this translation when restored; restore scopes in reverse order
     * @throws IllegalStateException if the batch is not drawing or its translation stack is full
     */
    public UIRenderScope translate(float x, float y) {
        batch.pushTranslation(x, -y);
        return new UIRenderScope(batch::popTranslation);
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
     * @return a scope restoring the previous clip when restored, in reverse nesting order
     * @throws IllegalStateException if the batch is not drawing or its clip stack is full
     */
    public UIRenderScope clip(float x, float y, float width, float clipHeight) {
        batch.beginScissor(x, height - y - clipHeight, width, clipHeight);
        return new UIRenderScope(batch::endScissor);
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
                nvgStrokeColor(vg, NanoUtility.color1(new Color(entry.captured() ? 0xFFFFB454 : entry.focused() ? 0xFF67E8F9 : 0x665B8DEF)));
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

}
