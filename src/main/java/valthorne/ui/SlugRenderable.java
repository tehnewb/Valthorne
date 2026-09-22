package valthorne.ui;

import valthorne.graphics.font.slug.SlugBatch;
import valthorne.graphics.texture.TextureBatch;

/**
 * Marks a UI node whose geometry is submitted through the shared Slug text backend.
 * The render context groups adjacent implementations into one Slug batch interval,
 * preserving painter order without requiring nodes to begin or end the renderer.
 *
 * @author Albert Beaupre
 * @since September 21st, 2026
 */
public interface SlugRenderable {

    /**
     * Selects live curve rendering. False dispatches through the node's ordinary
     * texture draw method, enabling cached Slug UI rendering.
     *
     * @return true when this draw requires the Slug backend
     */
    default boolean usesSlugBackend() { return true; }

    /**
     * Returns an optional caller-owned renderer retained for compatibility. A null
     * result selects the owning {@link UIRoot}'s lazily created shared renderer.
     *
     * @return preferred renderer, or null for the root renderer
     */
    default SlugBatch getSlugBatch() {
        return null;
    }

    /**
     * Submits geometry to an already active Slug batch. Implementations must not
     * begin, end, cancel, or dispose the supplied renderer.
     *
     * @param batch active renderer selected by the UI render context
     * @param uiBatch active UI batch supplying translation and clipping state
     */
    void drawSlug(SlugBatch batch, TextureBatch uiBatch);
}
