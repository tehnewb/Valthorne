package valthorne.graphics.texture;

import valthorne.graphics.Drawable;

/**
 * <p>
 * {@code NinePatchDrawable} is a small {@link Drawable} adapter that wraps a
 * {@link NinePatchTexture} so it can be used anywhere a generic drawable object
 * is expected.
 * </p>
 *
 * @author Albert Beaupre
 * @since December 22nd, 2025
 */
public record NinePatchDrawable(NinePatchTexture texture) implements Drawable {

    /**
     * <p>
     * Creates a new {@code NinePatchDrawable} by loading a {@link NinePatchTexture}
     * from a path string.
     * </p>
     *
     * <p>
     * The supplied border sizes are forwarded into the wrapped
     * {@link NinePatchTexture} constructor. Note that the constructor parameter order
     * accepted here is {@code left, top, right, bottom}, while the wrapped texture
     * constructor is called using {@code left, right, top, bottom}.
     * </p>
     *
     * @param string the texture path
     * @param left   the left border size
     * @param top    the top border size
     * @param right  the right border size
     * @param bottom the bottom border size
     */
    public NinePatchDrawable(String string, int left, int top, int right, int bottom) {
        this(new NinePatchTexture(string, left, right, top, bottom));
    }

    /**
     * <p>
     * Creates a new {@code NinePatchDrawable} from existing {@link TextureData}.
     * </p>
     *
     * <p>
     * The supplied border sizes are forwarded into the wrapped
     * {@link NinePatchTexture} constructor. Note that the constructor parameter order
     * accepted here is {@code left, top, right, bottom}, while the wrapped texture
     * constructor is called using {@code left, right, top, bottom}.
     * </p>
     *
     * @param data   the source texture data
     * @param left   the left border size
     * @param top    the top border size
     * @param right  the right border size
     * @param bottom the bottom border size
     */
    public NinePatchDrawable(TextureData data, int left, int top, int right, int bottom) {
        this(new NinePatchTexture(data, left, right, top, bottom));
    }

    /**
     * <p>
     * Creates a new {@code NinePatchDrawable} from an existing {@link Texture}.
     * </p>
     *
     * <p>
     * The supplied border sizes are forwarded into the wrapped
     * {@link NinePatchTexture} constructor. Note that the constructor parameter order
     * accepted here is {@code left, top, right, bottom}, while the wrapped texture
     * constructor is called using {@code left, right, top, bottom}.
     * </p>
     *
     * @param texture the source texture
     * @param left    the left border size
     * @param top     the top border size
     * @param right   the right border size
     * @param bottom  the bottom border size
     */
    public NinePatchDrawable(Texture texture, int left, int top, int right, int bottom) {
        this(new NinePatchTexture(texture, left, right, top, bottom));
    }

    /**
     * <p>
     * Draws the wrapped {@link NinePatchTexture} through the provided
     * {@link TextureBatch} using the supplied destination rectangle.
     * </p>
     *
     * <p>
     * This delegates directly to {@link TextureBatch#draw(NinePatchTexture, float, float, float, float)}.
     * </p>
     *
     * @param batch  the batch used for drawing
     * @param x      the destination X position
     * @param y      the destination Y position
     * @param width  the destination width
     * @param height the destination height
     */
    @Override
    public void draw(TextureBatch batch, float x, float y, float width, float height) {
        batch.draw(texture, x, y, width, height);
    }

    /**
     * <p>
     * Returns the current width of the wrapped {@link NinePatchTexture}.
     * </p>
     *
     * @return the wrapped texture width
     */
    @Override
    public float getWidth() {
        return texture.getWidth();
    }

    /**
     * <p>
     * Returns the current height of the wrapped {@link NinePatchTexture}.
     * </p>
     *
     * @return the wrapped texture height
     */
    @Override
    public float getHeight() {
        return texture.getHeight();
    }

    /**
     * <p>
     * Returns the wrapped {@link NinePatchTexture}.
     * </p>
     *
     * <p>
     * This method overrides the record-generated accessor explicitly so it can remain
     * part of the {@link Drawable} contract with clear documentation.
     * </p>
     *
     * @return the wrapped nine-patch texture
     */
    @Override
    public NinePatchTexture texture() {
        return texture;
    }
}