package valthorne.ui.nodes;

import valthorne.graphics.Color;
import valthorne.graphics.texture.Texture;
import valthorne.graphics.texture.NinePatchTexture;
import valthorne.graphics.texture.TextureBatch;
import valthorne.ui.UINode;

/**
 * <p>
 * {@code Image} renders a {@link Texture} in the UI hierarchy, optionally
 * through a nine-slice {@link NinePatchTexture}. Slice borders use source-image
 * pixels and remain configured while a node is detached and reattached.
 * </p>
 *
 * <p>
 * This node stores a single texture reference and draws it to its current
 * render bounds using a {@link TextureBatch}. During creation, it initializes
 * its layout size to the texture's native width and height and marks itself
 * as fill-enabled through the layout configuration.
 * </p>
 *
 * <p>
 * The node supports a color tint and optional nine-slice borders. Region
 * selection, scaling policy, and interaction behavior remain the caller's
 * responsibility through the broader UI and texture APIs.
 * </p>
 *
 * <h2>Example Usage</h2>
 *
 * <pre>{@code
 * Texture logo = new Texture("assets/ui/logo.png");
 *
 * Image image = new Image(logo);
 * image.getLayout()
 *      .width(128)
 *      .height(128);
 *
 * Texture current = image.getTexture();
 * image.texture(new Texture("assets/ui/other.png"));
 *
 * image.update(delta);
 * image.draw(batch);
 * }</pre>
 *
 * <p>
 * This example demonstrates the complete usage of the class: construction,
 * layout sizing, texture access, texture replacement, update, and draw.
 * </p>
 *
 * @author Albert Beaupre
 * @since March 13th, 2026
 */
public class Image extends UINode {

    private Texture texture; // Texture currently displayed by this image node
    private Color color = new Color(1, 1, 1, 1); // Mutable image tint applied when drawing the texture.
    private NinePatchTexture ninePatch; // Optional borrowed-texture nine-slice renderer owned by this node.
    private boolean ninePatchEnabled; // Nine-slice mode retained while this UI node is detached.
    private int patchLeft; // Fixed source pixels along the left edge.
    private int patchRight; // Fixed source pixels along the right edge.
    private int patchTop; // Fixed source pixels along the top edge.
    private int patchBottom; // Fixed source pixels along the bottom edge.

    /**
     * <p>
     * Creates a new image node using the provided texture.
     * </p>
     *
     * @param texture the texture to display
     */
    public Image(Texture texture) {
        this.texture = texture;
    }

    /**
     * <p>
     * Called when this image node is created.
     * </p>
     *
     * <p>
     * A retained NinePatch is rebuilt after this node reattaches to a UI root.
     * </p>
     */
    @Override
    public void onCreate() {
        if (ninePatchEnabled && ninePatch == null && texture != null) {
            ninePatch = new NinePatchTexture(texture, patchLeft, patchRight, patchTop, patchBottom);
            ninePatch.setColor(color);
        }
    }

    /**
     * <p>
     * Called when this image node is destroyed.
     * </p>
     *
     * <p>
     * Releases the borrowed-texture NinePatch mesh while keeping its border
     * settings for a later reattachment.
     * </p>
     */
    @Override
    public void onDestroy() {
        if (ninePatch != null) {
            ninePatch.dispose();
            ninePatch = null;
        }
    }

    /**
     * <p>
     * Updates this image node.
     * </p>
     *
     * <p>
     * This implementation currently performs no per-frame logic.
     * </p>
     *
     * @param delta the frame delta time
     */
    @Override
    public void update(float delta) {

    }

    /**
     * <p>
     * Draws the image using the provided {@link TextureBatch}.
     * </p>
     *
     * <p>
     * If no texture is assigned, drawing is skipped. Otherwise either the
     * regular image or its configured NinePatch fills the current layout bounds.
     * </p>
     *
     * @param batch the batch used for rendering
     */
    @Override
    public void draw(TextureBatch batch) {
        if (texture == null) return;
        if (ninePatch != null)
            batch.draw(ninePatch, getRenderX(), getRenderY(), getWidth(), getHeight());
        else
            batch.draw(texture, getRenderX(), getRenderY(), getWidth(), getHeight(), color);
    }

    /**
     * <p>
     * Returns the texture currently assigned to this image node.
     * </p>
     *
     * @return the current texture
     */
    public Texture getTexture() {
        return texture;
    }

    /**
     * <p>
     * Assigns a new texture to this image node.
     * </p>
     *
     * <p>
     * This method stores the new texture reference and returns this image for
     * fluent configuration. An enabled NinePatch is rebuilt against the new
     * image, with borders clipped if the new source is smaller.
     * </p>
     *
     * @param texture the new texture to display
     * @return this image node
     */
    public Image texture(Texture texture) {
        if (ninePatch != null) ninePatch.dispose();
        ninePatch = null;
        this.texture = texture;
        if (ninePatchEnabled && texture != null) {
            patchLeft = Math.min(patchLeft, texture.getWidth());
            patchRight = Math.min(patchRight, texture.getWidth() - patchLeft);
            patchTop = Math.min(patchTop, texture.getHeight());
            patchBottom = Math.min(patchBottom, texture.getHeight() - patchTop);
            onCreate();
        }
        return this;
    }

    /**
     * Enables nine-slice rendering using pixel widths from the source image.
     * The backing texture remains borrowed from this image node.
     *
     * @param left fixed left-edge width in pixels
     * @param right fixed right-edge width in pixels
     * @param top fixed top-edge height in pixels
     * @param bottom fixed bottom-edge height in pixels
     * @return this image node
     */
    public Image ninePatch(int left, int right, int top, int bottom) {
        if (texture == null)
            throw new IllegalStateException("Assign an image before enabling NinePatch");
        if (left < 0 || right < 0 || top < 0 || bottom < 0 || left + right > texture.getWidth() || top + bottom > texture.getHeight())
            throw new IllegalArgumentException("NinePatch borders must fit inside the image");
        if (ninePatch != null) ninePatch.dispose();
        ninePatch = null;
        patchLeft = left;
        patchRight = right;
        patchTop = top;
        patchBottom = bottom;
        ninePatchEnabled = true;
        onCreate();
        return this;
    }

    /**
     * Returns to ordinary stretched-image rendering without releasing the
     * backing texture owned by the caller.
     *
     * @return this image node
     */
    public Image disableNinePatch() {
        if (ninePatch != null) ninePatch.dispose();
        ninePatch = null;
        ninePatchEnabled = false;
        return this;
    }

    /**
     * Reports nine-slice mode even when the node is detached from a UI root.
     *
     * @return true when a NinePatch should be used on the next draw
     */
    public boolean isNinePatchEnabled() {
        return ninePatchEnabled;
    }

    /**
     * Reads one source-image border while retaining settings across detaches.
     *
     * @param side zero left, one right, two top, or three bottom
     * @return fixed border width in source pixels
     */
    public int getNinePatchBorder(int side) {
        return switch (side) {
            case 0 -> patchLeft;
            case 1 -> patchRight;
            case 2 -> patchTop;
            case 3 -> patchBottom;
            default -> throw new IllegalArgumentException("Unknown NinePatch border");
        };
    }

    /**
     * Returns the active nine-slice descriptor for editor inspection.
     *
     * @return nine-slice descriptor, or null when regular image drawing is used
     */
    public NinePatchTexture getNinePatch() {
        return ninePatch;
    }

    /**
     * Sets the color of this image node.
     *
     * @param color the color to apply to this image node
     * @return this image node, allowing for fluent configuration
     */
    public Image color(Color color) {
        this.color = color;
        if (ninePatch != null) ninePatch.setColor(color);
        return this;
    }

    /**
     * Returns the current color assigned to this image node.
     *
     * @return the current color
     */
    public Color getColor() {
        return color;
    }
}
