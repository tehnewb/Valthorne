package valthorne.graphics.render;

import valthorne.graphics.model.Material3D;
import valthorne.graphics.scene.Renderable3D;

/**
 * Selects the ordering and blending category of a {@link Material3D}.
 * {@link ModelBatch3D} processes these categories in declaration order: opaque
 * submissions first, then translucent submissions, then additive submissions.
 * Opaque objects are grouped by material order; the other passes are sorted by
 * descending {@link Renderable3D#getSortDepth(valthorne.camera.Camera3D)}.
 *
 * <p>Calling {@link Material3D#setRenderPass(RenderPass3D)} also enables depth
 * writes for opaque materials and disables them for the other categories.
 * Depth testing and subsequent explicit depth-write changes remain separate
 * material settings. These categories do not provide per-triangle transparency
 * sorting or order-independent transparency.</p>
 *
 * @author Albert Beaupre
 */
public enum RenderPass3D {
    /**
     * Draws without blending before transparent passes. Material grouping avoids
     * unnecessary state changes; depth testing resolves surface visibility when enabled.
     */
    OPAQUE,
    /**
     * Uses source-alpha/one-minus-source-alpha blending after opaque geometry.
     * Object-level back-to-front sorting approximates transparent compositing.
     */
    TRANSLUCENT,
    /**
     * Uses source-alpha/one blending after the translucent pass, adding the
     * alpha-weighted source color to the destination for effects such as glow.
     */
    ADDITIVE
}
