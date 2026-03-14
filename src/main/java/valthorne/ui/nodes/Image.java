package valthorne.ui.nodes;

import valthorne.graphics.texture.Texture;
import valthorne.graphics.texture.TextureBatch;
import valthorne.ui.UINode;

/**
 * <p>
 * {@code Image} is a simple UI node that renders a {@link Texture}.
 * It is intended for displaying static or dynamically swapped images inside
 * the UI hierarchy.
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
 * The class is intentionally minimal. It does not manage color tinting, region
 * selection, scaling policies, or interaction behavior on its own. Instead, it
 * serves as a lightweight textured UI element that can participate in the
 * broader layout and node lifecycle system.
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
     * The node's layout is initialized to the texture's native width and height
     * and then configured to fill according to the layout system.
     * </p>
     */
    @Override
    public void onCreate() {
        this.getLayout().width(texture.getWidth()).height(texture.getHeight()).fill();
    }

    /**
     * <p>
     * Called when this image node is destroyed.
     * </p>
     *
     * <p>
     * This implementation currently performs no additional destruction logic, but
     * the method exists to fulfill the node lifecycle contract.
     * </p>
     */
    @Override
    public void onDestroy() {

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
     * If no texture is assigned, drawing is skipped. Otherwise the texture is drawn
     * using the node's current render position and size.
     * </p>
     *
     * @param batch the batch used for rendering
     */
    @Override
    public void draw(TextureBatch batch) {
        if (texture == null) return;
        batch.draw(texture, getRenderX(), getRenderY(), getWidth(), getHeight());
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
     * fluent configuration.
     * </p>
     *
     * @param texture the new texture to display
     * @return this image node
     */
    public Image texture(Texture texture) {
        this.texture = texture;
        return this;
    }
}