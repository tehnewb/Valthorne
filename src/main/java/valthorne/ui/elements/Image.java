package valthorne.ui.elements;

import valthorne.graphics.texture.Texture;
import valthorne.graphics.texture.TextureBatch;
import valthorne.graphics.texture.TextureData;
import valthorne.ui.Element;

/**
 * <h1>Image</h1>
 *
 * <p>
 * {@code Image} is a simple UI element used to display a {@link Texture} inside the UI system.
 * It acts as a bridge between the UI layout system and the rendering system by allowing a texture
 * to behave like any other {@link Element}. This means the image can participate in UI layout,
 * positioning, sizing, clipping, and parent-child relationships exactly like buttons, labels,
 * sliders, or any other UI element.
 * </p>
 *
 * <p>
 * The element itself does not manage animation, interaction, or state transitions. Its purpose is
 * purely visual: rendering a texture at the element's current position and size.
 * </p>
 *
 * <h2>Rendering behavior</h2>
 *
 * <p>
 * When drawn, the image delegates rendering to the provided {@link TextureBatch}. The texture is
 * rendered using the element's {@code x}, {@code y}, {@code width}, and {@code height}. This allows
 * the image to be resized, stretched, or scaled as part of UI layout calculations.
 * </p>
 *
 * <p>
 * Because the UI system may move or resize elements during layout passes, the image ensures that
 * the wrapped {@link Texture} stays synchronized with the element by updating the texture's position
 * and size whenever {@link #setPosition(float, float)} or {@link #setSize(float, float)} is called.
 * </p>
 *
 * <h2>Texture ownership</h2>
 *
 * <p>
 * The image does not automatically dispose of the texture it references. Resource management is
 * expected to be handled externally by the engine or asset system. This allows multiple UI elements
 * to reuse the same texture safely.
 * </p>
 *
 * <h2>Example</h2>
 *
 * <pre>{@code
 * Texture logoTexture = new Texture("assets/ui/logo.png");
 *
 * Image logo = new Image(logoTexture);
 * logo.setPosition(20f, 20f);
 * logo.setSize(256f, 128f);
 *
 * ui.add(logo);
 * }</pre>
 *
 * <p>
 * The image will render the logo texture at the specified location and scale it to the specified
 * size inside the UI.
 * </p>
 *
 * @author Albert Beaupre
 * @since January 31st, 2026
 */
public class Image extends Element {

    private Texture texture; // The texture rendered by this image element.

    /**
     * Creates a new image element using the provided texture.
     *
     * <p>
     * The texture is stored directly and used during rendering. The caller retains ownership of
     * the texture and is responsible for disposing of it when no longer needed.
     * </p>
     *
     * @param texture the texture that will be displayed by this image
     */
    public Image(Texture texture) {
        this.texture = texture;
    }

    /**
     * Updates the image element.
     *
     * <p>
     * The image currently has no time-based behavior or animation logic, so this method performs
     * no operations. It exists to satisfy the UI element lifecycle and allows subclasses or
     * future implementations to add animation or dynamic behavior if desired.
     * </p>
     *
     * @param delta the elapsed time in seconds since the previous update
     */
    @Override
    public void update(float delta) {

    }

    /**
     * Draws the image using the provided texture batch.
     *
     * <p>
     * Rendering is delegated to {@link TextureBatch#draw(Texture, float, float, float, float)}.
     * The texture is drawn using the element's position and size so it integrates naturally
     * with the UI layout system.
     * </p>
     *
     * <p>
     * If the texture reference is null, no rendering occurs.
     * </p>
     *
     * @param batch the texture batch used to render the image
     */
    @Override
    public void draw(TextureBatch batch) {
        if (texture == null) {
            return;
        }

        batch.draw(texture, this.x, this.y, this.width, this.height);
    }

    /**
     * Replaces the texture using a {@link TextureData} source.
     *
     * <p>
     * A new {@link Texture} instance is created from the supplied data. The previous texture
     * reference is replaced but not disposed. This allows external systems to manage the
     * lifecycle of textures independently.
     * </p>
     *
     * @param data the texture data used to create the new texture
     * @return this image for chaining
     */
    public Image setTexture(TextureData data) {
        this.texture = new Texture(data);
        return this;
    }

    /**
     * Returns the texture currently used by this image element.
     *
     * @return the texture being rendered, or null if none is assigned
     */
    public Texture getTexture() {
        return texture;
    }
}