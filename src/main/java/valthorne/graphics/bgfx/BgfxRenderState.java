package valthorne.graphics.bgfx;

import valthorne.graphics.shader.Shader;

/**
 * Reusable render-thread snapshot for a single external bgfx drawing scope.
 * Captures target, viewport, physical clipping, selected shader and sampled texture
 * slots. It owns no GPU resources. Store and restore in balanced order; separate
 * instances may nest, while one instance rejects overlapping captures. Explicit
 * per-draw bgfx state needs no depth/blend/program queries or native synchronization.
 */
public final class BgfxRenderState {
    private final int[] viewport = new int[4]; // Saved bottom-left physical viewport.
    private final int[] scissor = new int[4]; // Saved physical clipping rectangle.
    private final int[] textures = new int[16]; // Saved managed sampled texture slots.
    private int target; // Saved framebuffer index.
    private int width; // Saved physical target width.
    private int height; // Saved target height for orientation conversion.
    private boolean clipping; // Saved scissor enablement.
    private Shader shader; // Borrowed saved immediate shader, possibly null.
    private boolean captured; // Whether a balanced restore is pending.
    private int textureUnit; // Saved public active texture selector.

    /**
     * Allocates reusable primitive snapshot storage without reading native state.
     */
    public BgfxRenderState() {
    }

    /**
     * Captures the coordinator's current state without allocating temporary objects.
     */
    public void store() {
        if (captured) throw new IllegalStateException("Render state is already captured");
        BgfxGraphics.device();
        target = BgfxGraphics.target();
        height = BgfxGraphics.targetHeight();
        width = BgfxGraphics.targetWidth();
        BgfxGraphics.getViewport(viewport);
        BgfxGraphics.getScissor(scissor);
        clipping = BgfxGraphics.isScissorEnabled();
        shader = BgfxGraphics.shader();
        textureUnit = BgfxGraphics.textureUnit();
        System.arraycopy(BgfxGraphics.textures(), 0, textures, 0, textures.length);
        captured = true;
    }

    /**
     * Restores captured state, starting a new ordered pass for the saved target.
     * An already restored scope is a no-op.
     */
    public void restore() {
        if (!captured) return;
        BgfxGraphics.setTarget(target, width, height);
        BgfxGraphics.setViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
        BgfxGraphics.setScissor(scissor[0], scissor[1], scissor[2], scissor[3], clipping);
        BgfxGraphics.bindShader(shader);
        BgfxGraphics.textureUnit(textureUnit);
        System.arraycopy(textures, 0, BgfxGraphics.textures(), 0, textures.length);
        shader = null;
        captured = false;
    }
}
