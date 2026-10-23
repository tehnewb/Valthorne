package valthorne.graphics.texture;

import java.nio.FloatBuffer;
import org.lwjgl.BufferUtils;
import valthorne.graphics.GraphicsBackend;
import valthorne.graphics.GraphicsResource;
import valthorne.graphics.ImmediateTextureRenderer;
import valthorne.graphics.bgfx.BgfxGraphics;
import valthorne.graphics.bgfx.BgfxRenderTarget;
import valthorne.graphics.bgfx.BgfxResources;

import static org.lwjgl.bgfx.BGFX.bgfx_get_caps;

/**
 * Owns an RGBA8 render target with optional depth/stencil storage. Rendering uses
 * the same scene, batch and shader APIs as the window. Begin/end save and restore
 * the parent target and viewport; different targets may nest in reverse order.
 * Finish active batches before changing targets. Projection and scissor state
 * remain under caller control.
 *
 * <p>Drawing the attachment corrects the selected backend's texture origin.
 * Resize replaces attachments atomically and invalidates previously borrowed
 * handles. All operations belong to the rendering thread. Dispose attachments
 * before graphics shutdown; repeated disposal is harmless.</p>
 *
 * <pre>{@code
 * FrameBuffer target = new FrameBuffer(640, 360, true);
 * target.render(() -> {
 *     Window.clear(Color.TRANSPARENT);
 *     batch.begin();
 *     batch.draw(sprite);
 *     batch.end();
 * });
 * target.draw(0, 0, 640, 360);
 * target.dispose();
 * }</pre>
 */
public final class FrameBuffer implements GraphicsResource {
    private final boolean hasDepth; // Whether the target includes depth/stencil storage.
    private final FloatBuffer positions = BufferUtils.createFloatBuffer(8); // Reused destination quad positions.
    private final FloatBuffer coordinates = BufferUtils.createFloatBuffer(8); // Reused backend-corrected texture coordinates.
    private final int[] viewport = new int[4]; // Saved parent viewport in bottom-left physical pixels.
    private BgfxRenderTarget target; // Owned attachments, null after disposal.
    private int previousTarget; // Borrowed parent target handle during a capture.
    private int previousWidth; // Parent target width needed for viewport cropping.
    private int previousHeight; // Parent target height needed for viewport orientation.
    private int width; // Current target width in pixels.
    private int height; // Current target height in pixels.
    private boolean drawing; // Whether this instance owns an active capture scope.

    /**
     * Allocates an attachment set on the active graphics device.
     *
     * @param width positive pixel width within device limits
     * @param height positive pixel height within device limits
     * @param hasDepth whether depth/stencil storage is required
     */
    public FrameBuffer(int width, int height, boolean hasDepth) {
        this.hasDepth = hasDepth;
        target = new BgfxRenderTarget(BgfxGraphics.device(), width, height, hasDepth);
        this.width = width;
        this.height = height;
        BgfxResources.register(this);
    }

    /**
     * Saves the current target and viewport and selects this target's full extent.
     *
     * @throws IllegalStateException if disposed or already capturing
     */
    public void begin() {
        requireOpen();
        if (drawing) throw new IllegalStateException("Framebuffer capture is already active");
        previousTarget = BgfxGraphics.target();
        previousWidth = BgfxGraphics.targetWidth();
        previousHeight = BgfxGraphics.targetHeight();
        BgfxGraphics.getViewport(viewport);
        BgfxGraphics.setTarget(Short.toUnsignedInt(target.framebuffer()), width, height);
        BgfxGraphics.setViewport(0, 0, width, height);
        drawing = true;
    }

    /**
     * Restores the parent target and viewport. End nested captures in reverse order.
     */
    public void end() {
        requireOpen();
        if (!drawing) throw new IllegalStateException("Framebuffer capture is not active");
        BgfxGraphics.setTarget(previousTarget, previousWidth, previousHeight);
        BgfxGraphics.setViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
        drawing = false;
    }

    /**
     * Runs drawing inside a capture and restores the parent even if drawing fails.
     *
     * @param drawCalls nonnull drawing operation that finishes its batches
     */
    public void render(Runnable drawCalls) {
        if (drawCalls == null) throw new NullPointerException("drawCalls");
        begin();
        try {
            drawCalls.run();
        } finally {
            end();
        }
    }

    /**
     * Replaces attachments when dimensions change. Allocation failure retains the
     * current attachments. Borrowed handles must be fetched again after resizing.
     *
     * @param width positive new pixel width
     * @param height positive new pixel height
     */
    public void resize(int width, int height) {
        requireOpen();
        if (drawing) throw new IllegalStateException("End capture before resizing the framebuffer");
        if (this.width == width && this.height == height) return;
        BgfxRenderTarget replacement = new BgfxRenderTarget(BgfxGraphics.device(), width, height, hasDepth);
        target.close();
        target = replacement;
        this.width = width;
        this.height = height;
    }

    /**
     * Draws the color attachment with consistent orientation across backends.
     *
     * @param x destination left edge in world units
     * @param y destination bottom edge in world units
     * @param width destination width in world units
     * @param height destination height in world units
     */
    public void draw(float x, float y, float width, float height) {
        drawAttachment(x, y, width, height, false);
    }

    /**
     * Draws the color attachment with an intentional vertical reflection.
     *
     * @param x destination left edge in world units
     * @param y destination bottom edge in world units
     * @param width destination width in world units
     * @param height destination height in world units
     */
    public void drawFlippedY(float x, float y, float width, float height) {
        drawAttachment(x, y, width, height, true);
    }

    /**
     * Writes a destination quad and submits it without temporary Java allocations.
     *
     * @param x left edge
     * @param y bottom edge
     * @param width destination width
     * @param height destination height
     * @param flip whether to reflect vertically after backend correction
     */
    private void drawAttachment(float x, float y, float width, float height, boolean flip) {
        requireOpen();
        if (drawing) throw new IllegalStateException("End capture before sampling the framebuffer");
        positions.put(0, x)
                .put(1, y)
                .put(2, x + width)
                .put(3, y)
                .put(4, x + width)
                .put(5, y + height)
                .put(6, x)
                .put(7, y + height);
        float bottom = bgfx_get_caps().originBottomLeft() != flip ? 0 : 1;
        float top = 1 - bottom;
        coordinates.put(0, 0)
                .put(1, bottom)
                .put(2, 1)
                .put(3, bottom)
                .put(4, 1)
                .put(5, top)
                .put(6, 0)
                .put(7, top);
        ImmediateTextureRenderer.drawQuads(Short.toUnsignedInt(target.texture()), positions, coordinates, 1, null);
    }

    /**
     * Returns the current attachment width.
     *
     * @return width in pixels
     */
    public int getWidth() {
        return width;
    }

    /**
     * Returns the current attachment height.
     *
     * @return height in pixels
     */
    public int getHeight() {
        return height;
    }

    /**
     * Borrows the active device's framebuffer handle until resize, backend replacement or disposal.
     *
     * @return device framebuffer handle
     */
    public int getFrameBufferId() {
        requireOpen();
        return Short.toUnsignedInt(target.framebuffer());
    }

    /**
     * Borrows the active device's color texture handle until resize or disposal.
     * Raw sampling must account for the device's render-target texture origin.
     *
     * @return device texture handle
     */
    public int getColorTextureId() {
        requireOpen();
        return Short.toUnsignedInt(target.texture());
    }

    /**
     * Releases attachments after the final capture and before graphics shutdown.
     */
    public void dispose() {
        if (target == null) return;
        if (drawing) throw new IllegalStateException("End capture before disposing the framebuffer");
        BgfxResources.unregister(this);
        target.close();
        target = null;
    }

    /**
     * Rejects use after attachment disposal.
     */
    private void requireOpen() {
        if (target == null) throw new IllegalStateException("Framebuffer is disposed");
    }

    @Override
    public void validateGraphicsReset(GraphicsBackend backend) {
        if (drawing) throw new IllegalStateException("End framebuffer captures before switching graphics backends");
    }

}
