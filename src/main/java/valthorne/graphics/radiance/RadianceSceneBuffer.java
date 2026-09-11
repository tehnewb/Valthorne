package valthorne.graphics.radiance;

import valthorne.graphics.texture.Texture;

/**
 * Owns a nearest-filtered floating-point framebuffer for captured radiance scene data.
 * Begin/end temporarily replace the framebuffer and viewport; all resource and drawing
 * operations require the owning graphics context. Balance these calls and do not nest
 * them on this instance. Clear outside an active begin/end pair.
 *
 * <pre>{@code
 * RadianceSceneBuffer scene = new RadianceSceneBuffer(640, 360);
 * scene.clear();
 * scene.begin();
 * try {
 *     // Draw encoded scene data using the active framebuffer.
 * } finally {
 *     scene.end();
 * }
 * scene.dispose();
 * }</pre>
 *
 * <p>Resizing discards captured pixels and invalidates borrowed texture wrappers.
 * Dispose after consumers finish; this class does not manage their lifetime.</p>
 *
 * @author Albert Beaupre
 */
public final class RadianceSceneBuffer {

    private final RadianceRenderTarget target; // Owned framebuffer and floating-point color texture.

    /**
     * Allocates a color framebuffer and nearest-filtered RGBA16F texture immediately.
     * Creation does not preserve all incoming graphics bindings.
     *
     * @param width positive buffer width in pixels
     * @param height positive buffer height in pixels
     * @throws IllegalArgumentException if either dimension is nonpositive
     * @throws IllegalStateException if framebuffer creation is incomplete
     */
    public RadianceSceneBuffer(int width, int height) {
        this.target = new RadianceRenderTarget(width, height, true, false);
    }

    /**
     * Saves the current framebuffer and viewport and binds this buffer for capture.
     * Does not clear existing contents. Call end after drawing and avoid nested begins.
     */
    public void begin() {
        target.begin();
    }

    /**
     * Restores the framebuffer and viewport saved by the latest begin. Other graphics
     * state is not restored, and unmatched calls can restore stale saved values.
     */
    public void end() {
        target.end();
    }

    /**
     * Clears the color attachment to transparent black through a temporary binding.
     * Call outside this buffer's begin/end pair; the OpenGL clear color remains changed.
     */
    public void clear() {
        clear(0f, 0f, 0f, 0f);
    }

    /**
     * Clears the color attachment using a temporary binding and restores framebuffer
     * and viewport. Other clear-related state, including clear color, is not restored.
     *
     * @param r red component
     * @param g green component
     * @param b blue component
     * @param a alpha component
     */
    public void clear(float r, float g, float b, float a) {
        target.clear(r, g, b, a);
    }

    /**
     * Replaces storage if positive dimensions change, discarding captured data and
     * invalidating previously borrowed wrappers. Equal dimensions perform no work.
     *
     * @param width positive replacement width in pixels
     * @param height positive replacement height in pixels
     * @throws IllegalArgumentException if either dimension is nonpositive
     */
    public void resize(int width, int height) {
        target.resize(width, height);
    }

    /**
     * Returns the retained storage width without querying the GPU.
     *
     * @return width in pixels
     */
    public int getWidth() {
        return target.getWidth();
    }

    /**
     * Returns the retained storage height without querying the GPU.
     *
     * @return height in pixels
     */
    public int getHeight() {
        return target.getHeight();
    }

    /**
     * Returns the captured color texture's name without binding it or transferring
     * ownership. The name can change after resize.
     *
     * @return current texture name, or zero after disposal
     */
    public int getTextureID() {
        return target.getTextureID();
    }

    /**
     * Returns a borrowed wrapper for the captured texture. Do not dispose it separately;
     * resize and disposal of this buffer invalidate earlier wrappers.
     *
     * @return current wrapper, or null after disposal
     */
    public Texture getTexture() {
        return target.getTexture();
    }

    /**
     * Releases the owned framebuffer and texture. Repeated calls do not delete resources
     * again; previously returned texture wrappers must no longer be used.
     */
    public void dispose() {
        target.dispose();
    }
}
