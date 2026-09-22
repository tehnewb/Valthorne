package valthorne.ui.nodes.nano;

import org.lwjgl.nanovg.NVGPaint;
import valthorne.graphics.texture.TextureData;

import static org.lwjgl.nanovg.NanoVG.*;

/**
 * Interactive hyperlink rendered from owned image data instead of text.
 *
 * <p>The supplied {@link TextureData} is uploaded lazily for the NanoVG context
 * used to draw the node. Moving the node to another context deletes the previous
 * image handle and creates a replacement. Destroying the node releases both the
 * NanoVG image and its CPU-side texture data, so callers must not share ownership
 * of that data after construction.</p>
 *
 * <p>For example:</p>
 * <pre>{@code
 * NanoImageHyperlink link = new NanoImageHyperlink(
 *         "Project home", "https://example.com", iconData);
 * parent.add(link);
 * }</pre>
 */
public class NanoImageHyperlink extends NanoHyperlink {
    private final TextureData texture; // Owned CPU-side pixels used for lazy uploads.
    private int imageHandle = -1; // Current NanoVG image handle, or -1 when absent.
    private long imageContext; // NanoVG context that owns the current image handle.

    /**
     * Creates an image-backed hyperlink and takes ownership of its texture data.
     *
     * @param label accessible hyperlink label passed to the base node
     * @param url destination opened when the link is activated
     * @param texture non-null image data disposed when this node is destroyed
     * @throws IllegalArgumentException if {@code texture} is null
     */
    public NanoImageHyperlink(String label, String url, TextureData texture) {
        super(label, url);
        if (texture == null) throw new IllegalArgumentException("texture must not be null");
        this.texture = texture;
    }

    /**
     * Draws the image centered in the node, scaling it to the interactive size
     * while preserving aspect ratio and reflecting hover, focus, press, and
     * disabled state through size or opacity.
     *
     * @param vg active NanoVG context; zero or an invisible node is a no-op
     */
    @Override
    public void draw(long vg) {
        if (!isVisible() || vg == 0L) return;
        ensureImage(vg);
        if (imageHandle <= 0) return;
        float maxSize = isHovered() || isFocused() ? 30 : 28;
        float scale = Math.min(maxSize / texture.width(), maxSize / texture.height());
        float width = texture.width() * scale, height = texture.height() * scale;
        float x = getAbsoluteX() + (getWidth() - width) * .5f;
        float y = getAbsoluteY() + (getHeight() - height) * .5f;
        float opacity = isEnabled() ? (isPressed() ? .72f : 1f) : .45f;
        try (NVGPaint paint = NVGPaint.calloc()) {
            nvgImagePattern(vg, x, y, width, height, 0, imageHandle, opacity, paint);
            nvgBeginPath(vg);
            nvgRect(vg, x, y, width, height);
            nvgFillPaint(vg, paint);
            nvgFill(vg);
        }
    }

    /**
     * Releases the context-owned image, disposes the owned texture data, and then
     * delegates remaining node cleanup to the base hyperlink implementation.
     */
    @Override
    public void onDestroy() {
        deleteImage();
        texture.dispose();
        super.onDestroy();
    }

    /**
     * Ensures the texture is uploaded into the active NanoVG context.
     * Switching contexts first deletes the image owned by the previous context.
     *
     * @param vg nonzero NanoVG context receiving the image
     */
    private void ensureImage(long vg) {
        if (imageHandle > 0 && imageContext == vg) return;
        deleteImage();
        imageHandle = nvgCreateImageRGBA(vg, texture.width(), texture.height(), NVG_IMAGE_FLIPY, texture.buffer());
        imageContext = vg;
    }

    /**
     * Deletes the current NanoVG image when present and resets ownership metadata.
     * Repeated calls are safe no-ops after the first deletion.
     */
    private void deleteImage() {
        if (imageHandle > 0 && imageContext != 0L) nvgDeleteImage(imageContext, imageHandle);
        imageHandle = -1;
        imageContext = 0L;
    }
}
