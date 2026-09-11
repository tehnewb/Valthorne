package valthorne.ui.nodes.nano;

import org.lwjgl.nanovg.NVGPaint;
import valthorne.graphics.texture.TextureBatch;
import valthorne.graphics.texture.TextureData;
import valthorne.ui.UINode;

import static org.lwjgl.nanovg.NanoVG.*;

/**
 * UI image node that uploads borrowed TextureData to a lazily created NanoVG
 * image and stretches it across the node's layout rectangle. The image pattern
 * uses full opacity and no rotation; aspect ratio follows the assigned width
 * and height rather than a separate fit policy.
 *
 * <p>Construction with a texture initializes layout dimensions from its pixel
 * dimensions. The native image is created on the first draw and recreated when
 * the NanoVG context changes. Texture replacement or node destruction deletes
 * the owned image handle; the source TextureData is retained by reference and
 * is never disposed by this node.</p>
 *
 * <p>Keep source pixel storage available for initial upload and any later context
 * change. In-place edits to that storage are not detected once an image exists,
 * and assigning the same TextureData instance is a no-op. Rendering and cleanup
 * belong on the graphics thread while the image's owning context remains valid.
 * Attach the node to a UIRoot and use its mixed-backend drawing lifecycle.</p>
 *
 * @author Albert Beaupre
 */
public class NanoImage extends UINode implements NanoNode {

    private TextureData texture; // Borrowed source pixels retained for lazy upload and context recreation.
    private int imageHandle = -1; // Owned NanoVG image name, positive only after successful creation.
    private long imageContext; // NanoVG context that owns the current image handle.

    /**
     * Retains optional source pixels and, when present, sets initial layout width
     * and height to their dimensions. No native image is created at construction.
     *
     * @param texture the borrowed source data, or null for an initially empty node
     */
    public NanoImage(TextureData texture) {
        this.texture = texture;
        if (texture != null) getLayout().width(texture.width()).height(texture.height());
    }

    /**
     * Leaves image creation deferred until drawing supplies the NanoVG context.
     * This lifecycle hook performs no resource allocation.
     */
    @Override
    public void onCreate() {
    }

    /**
     * Releases the owned NanoVG image through its recorded context and clears
     * handle bookkeeping. The borrowed source data remains retained and is not
     * disposed. Run before the owning NanoVG context is destroyed.
     */
    @Override
    public void onDestroy() {
        deleteImage();
    }

    /**
     * Performs no per-frame work; this node has no image animation or upload
     * polling. Source replacement is explicit through texture(TextureData).
     *
     * @param delta the frame interval supplied by UI traversal, unused here
     */
    @Override
    public void update(float delta) {
    }

    /**
     * Routes drawing through the shared UI rendering entry point so an attached
     * root can dispatch this node to NanoVG with the proper clipping and state.
     *
     * @param batch the active texture batch used by mixed UI traversal
     */
    @Override
    public void draw(TextureBatch batch) {
        render(batch);
    }

    /**
     * Lazily ensures a context-local image, then fills the node's absolute layout
     * rectangle with its image pattern. Null source data, a zero context, failed
     * image creation or nonpositive layout dimensions produce no painted image.
     * Upload is attempted before the layout-size check. A temporary native paint
     * structure is freed after use; frame and context ownership remain with the root.
     *
     * @param vg the prepared NanoVG context, or zero to skip drawing
     */
    @Override
    public void draw(long vg) {
        if (texture == null || vg == 0L)
            return;

        ensureImage(vg);

        if (imageHandle <= 0)
            return;

        float x = getAbsoluteX();
        float y = getAbsoluteY();
        float width = getWidth();
        float height = getHeight();

        if (width <= 0f || height <= 0f)
            return;

        try (NVGPaint paint = NVGPaint.calloc()) {
            nvgImagePattern(vg, x, y, width, height, 0f, imageHandle, 1f, paint);
            nvgBeginPath(vg);
            nvgRect(vg, x, y, width, height);
            nvgFillPaint(vg, paint);
            nvgFill(vg);
        }
    }

    /**
     * Returns the retained source directly, without copying pixels or exposing
     * the separate native image. Mutating its pixels does not refresh an existing upload.
     *
     * @return the borrowed TextureData, or null when no source is assigned
     */
    public TextureData getTexture() {
        return texture;
    }

    /**
     * Replaces source data after deleting any existing native image. A nonnull
     * replacement sets layout dimensions from the source and enables flex grow
     * and shrink with automatic basis through Layout.fill. Null clears the image
     * source while retaining current layout settings. Changed sources mark layout
     * dirty; assigning the identical reference performs no work or refresh.
     *
     * @param texture the new borrowed source, or null to stop painting
     * @return this node for chaining
     */
    public NanoImage texture(TextureData texture) {
        if (this.texture == texture)
            return this;

        deleteImage();
        this.texture = texture;

        if (texture != null)
            getLayout().width(texture.width()).height(texture.height()).fill();

        markLayoutDirty();
        return this;
    }

    /**
     * Reuses a positive image handle owned by the requested context, otherwise
     * deletes the old image and uploads current RGBA pixels. Vertical flipping
     * matches TextureData's bottom-first rows to NanoVG. Failed creation leaves
     * a nonpositive handle and is retried by a later draw.
     *
     * @param vg the valid nonzero destination NanoVG context
     */
    private void ensureImage(long vg) {
        if (imageHandle > 0 && imageContext == vg)
            return;

        deleteImage();

        // TextureData uses the same bottom-first rows as TextureBatch.
        imageHandle = nvgCreateImageRGBA(vg, texture.width(), texture.height(), NVG_IMAGE_FLIPY, texture.buffer());
        imageContext = vg;
    }

    /**
     * Deletes a positive image handle through its recorded nonzero context and
     * resets the handle to -1 and context to zero. Repeated calls with no image
     * do nothing beyond resetting bookkeeping. Source TextureData is untouched.
     */
    private void deleteImage() {
        if (imageHandle > 0 && imageContext != 0L)
            nvgDeleteImage(imageContext, imageHandle);

        imageHandle = -1;
        imageContext = 0L;
    }
}
