package valthorne.graphics.texture;

import java.nio.ByteBuffer;
import valthorne.graphics.GraphicsBackend;
import valthorne.graphics.GraphicsResource;
import valthorne.graphics.Sprite;
import valthorne.graphics.bgfx.BgfxGraphics;
import valthorne.graphics.bgfx.BgfxResources;
import valthorne.graphics.bgfx.BgfxTexture2D;
import valthorne.io.pool.Poolable;

import static org.lwjgl.bgfx.BGFX.BGFX_TEXTURE_FORMAT_RGBA8;
import static org.lwjgl.bgfx.BGFX.bgfx_destroy_texture;

/**
 * <p>
 * {@code Texture} represents a GPU-backed 2D texture for the selected backend in Valthorne.
 * It acts as the primary wrapper around a texture object ID and its associated
 * {@link TextureData}. This class is responsible for creating the GPU texture,
 * uploading pixel data, configuring filtering behavior, exposing width and height,
 * and cleaning up the GPU resource when it is no longer needed.
 * </p>
 *
 * <p>
 * A {@code Texture} can be constructed from a file path, raw encoded image bytes,
 * or an already prepared {@link TextureData} object. During construction, the
 * texture is immediately created on the GPU, bound, configured with default filter
 * parameters, assigned clamp-to-edge wrapping, and populated with RGBA pixel data.
 * </p>
 *
 * <p>
 * This class is intentionally small and focused. It does not attempt to manage
 * atlases, regions, batching, or draw state directly. Instead, it serves as the
 * low-level image resource that other rendering types build on top of, such as:
 * </p>
 *
 * <ul>
 *     <li>{@link TextureRegion}</li>
 *     <li>{@link Sprite}</li>
 *     <li>{@link NinePatchTexture}</li>
 *     <li>{@link TextureBatch}</li>
 * </ul>
 *
 * <p>
 * The texture starts with {@link TextureFilter#NEAREST} filtering by default,
 * making it suitable for crisp pixel-art rendering. The filter can later be
 * changed with {@link #setFilter(TextureFilter)}. If the chosen filter uses
 * mipmaps, mipmaps are generated automatically after the filter is applied.
 * </p>
 *
 * <p>
 * Since this class implements {@link Poolable}, it can participate in pooling
 * systems. Its {@link #reset()} implementation restores the filter back to
 * {@link TextureFilter#NEAREST}.
 * </p>
 *
 * <h2>Example Usage</h2>
 *
 * <pre>{@code
 * Texture texture = new Texture("assets/player.png");
 * texture.setFilter(TextureFilter.LINEAR);
 *
 * int width = texture.getWidth();
 * int height = texture.getHeight();
 *
 * texture.bind();
 *
 * Sprite sprite = texture.sprite();
 *
 * TextureBatch batch = new TextureBatch(1000);
 * batch.begin();
 * batch.draw(texture, 100, 50, width, height);
 * batch.draw(sprite);
 * batch.end();
 *
 * texture.reset();
 * texture.dispose();
 * }</pre>
 *
 * <p>
 * This example shows the full typical lifecycle of the class: loading,
 * filtering, binding, converting into a {@link Sprite}, drawing, resetting,
 * and disposal.
 * </p>
 *
 * @author Albert Beaupre
 * @since March 13th, 2026
 */
public class Texture implements Poolable, GraphicsResource {

    private BgfxTexture2D bgfxTexture; // Owned native bgfx upload, absent for wrapped handles.
    protected int textureID; // Engine binding ID, stable across device and mip-storage changes.
    protected final boolean ownsData; // Whether this texture should dispose the decoded TextureData when the GPU texture is released
    protected TextureData data; // CPU-side texture metadata and pixel buffer associated with this texture
    protected TextureFilter filter = TextureFilter.NEAREST; // Current filtering mode applied to the texture
    private boolean disposed; // Whether this texture has already released its owned resources

    /**
     * <p>
     * Creates a texture by loading image data from a file path.
     * </p>
     *
     * <p>
     * This constructor delegates to {@link TextureData#load(String)} and then
     * forwards the resulting {@link TextureData} to
     * {@link #Texture(TextureData)}.
     * </p>
     *
     * @param path the image path to load
     */
    public Texture(String path) {
        this(TextureData.load(path), true);
    }

    /**
     * <p>
     * Creates a texture by loading image data from encoded image bytes.
     * </p>
     *
     * <p>
     * This constructor delegates to {@link TextureData#load(byte[])} and then
     * forwards the resulting {@link TextureData} to
     * {@link #Texture(TextureData)}.
     * </p>
     *
     * @param data the encoded image bytes to load
     */
    public Texture(byte[] data) {
        this(TextureData.load(data), true);
    }

    /**
     * <p>
     * Creates a texture from an existing {@link TextureData} instance.
     * </p>
     *
     * <p>
     * This constructor creates a new GPU texture object, binds it,
     * applies the default filtering mode, applies clamp-to-edge wrapping,
     * and uploads a copied RGBA pixel buffer to the graphics device.
     * </p>
     *
     * <p>
     * The default filter at construction time is
     * {@link TextureFilter#NEAREST}.
     * </p>
     *
     * @param data the texture data to upload
     * @throws NullPointerException if {@code data} is {@code null}
     */
    public Texture(TextureData data) {
        this(data, false);
    }

    /**
     * Creates and uploads an RGBA8 GPU texture, retaining the supplied pixel data.
     * Configures the default filters and clamp-to-edge wrapping and leaves the texture
     * bound on the active texture unit. Disposal always releases the GPU object and
     * also releases pixel data when ownsData is true. Requires a current graphics device.
     *
     * @param data decoded RGBA pixels and dimensions
     * @param ownsData whether disposal also releases the decoded pixel buffer
     * @throws NullPointerException if data is null
     */
    protected Texture(TextureData data, boolean ownsData) {
        this(data, ownsData, BGFX_TEXTURE_FORMAT_RGBA8);
    }

    /**
     * Allocates a managed atlas in a compact native format with recovery storage.
     * @param data dimensions and optional initial pixels; null pixels clear the atlas
     * @param format supported bgfx sampled texture format
     */
    protected Texture(TextureData data, int format) {
        this(data, false, format);
    }

    /**
     * Creates the native allocation while retaining logical texture ownership.
     * @param data validated dimensions and optional initial image
     * @param ownsData whether decoded pixels are owned
     * @param format native storage format matching the packed pixels
     */
    private Texture(TextureData data, boolean ownsData, int format) {
        if (data == null) throw new NullPointerException("TextureData cannot be null");

        bgfxTexture = new BgfxTexture2D(BgfxGraphics.device(), data.width(), data.height(), format, data.buffer());
        textureID = bgfxTexture.id();
        this.data = data;
        this.ownsData = ownsData;
        BgfxResources.register(this);
    }

    /**
     * Copies one packed atlas rectangle into the GPU and retained recovery image.
     * @param x destination column
     * @param y destination row
     * @param width rectangle width
     * @param height rectangle height
     * @param pixels borrowed packed pixels
     */
    protected final void updatePixels(int x, int y, int width, int height, ByteBuffer pixels) {
        if (disposed || bgfxTexture == null) throw new IllegalStateException("Texture is not a live managed allocation");
        bgfxTexture.update(x, y, width, height, pixels);
    }

    /**
     * <p>
     * Creates a texture wrapper around an existing GPU texture ID and
     * texture data.
     * </p>
     *
     * <p>
     * This constructor does not generate a new GPU texture object or upload
     * data. It simply wraps an already existing texture ID with the supplied
     * metadata. This is useful for subclasses or advanced cases where the
     * texture object has already been created elsewhere.
     * </p>
     *
     * @param textureID the existing GPU texture ID
     * @param data      the associated texture data
     * @throws NullPointerException if {@code data} is {@code null}
     */
    protected Texture(int textureID, TextureData data) {
        this(textureID, data, false);
    }

    /**
     * Wraps an existing texture handle without uploading pixels or changing engine render state.
     * The wrapper takes responsibility for deleting the handle on disposal; ownsData
     * separately controls release of the associated CPU pixel buffer.
     *
     * @param textureID existing GPU texture handle
     * @param data retained pixel data and dimensions
     * @param ownsData whether disposal also releases the pixel buffer
     * @throws NullPointerException if data is null
     */
    protected Texture(int textureID, TextureData data, boolean ownsData) {
        if (data == null) throw new NullPointerException("TextureData cannot be null");

        this.textureID = textureID;
        this.data = data;
        this.ownsData = ownsData;
        BgfxResources.register(this);
    }

    /**
     * <p>
     * Selects this texture on the engine's active sampled texture slot.
     * </p>
     *
     * <p>
     * This makes the texture the current active 2D texture for subsequent
     * engine draws in the currently active texture unit.
     * </p>
     */
    public void bind() {
        BgfxGraphics.bindTexture(BgfxGraphics.textureUnit(), textureID);
    }

    /**
     * <p>
     * Returns the currently active filter for this texture.
     * </p>
     *
     * @return the current texture filter
     */
    public TextureFilter getFilter() {
        return filter;
    }

    /**
     * <p>
     * Applies a new filtering mode to this texture.
     * </p>
     *
     * <p>
     * The minification and magnification parameters are updated, and mipmaps
     * are generated automatically when the supplied filter
     * requires them. Replacing mip storage preserves the engine texture ID and
     * pending batch references.
     * </p>
     *
     * @param filter the new filter to apply
     * @throws NullPointerException if {@code filter} is {@code null}
     */
    public void setFilter(TextureFilter filter) {
        if (filter == null) throw new NullPointerException("TextureFilter cannot be null");

        if (disposed) throw new IllegalStateException("Texture is disposed");
        if (this.filter.usesMipmaps() != filter.usesMipmaps()) {
            if (bgfxTexture == null) throw new UnsupportedOperationException("Wrapped textures cannot replace their mip storage");
            bgfxTexture.setMipmaps(filter.usesMipmaps());
        }
        if (bgfxTexture != null) bgfxTexture.setFilter(filter);
        else BgfxGraphics.setTextureFilter(textureID, filter);
        this.filter = filter;
    }

    /**
     * <p>
     * Returns the {@link TextureData} associated with this texture.
     * </p>
     *
     * <p>
     * This provides access to the texture's width, height, and original
     * pixel buffer metadata as long as the texture has not been disposed.
     * </p>
     *
     * @return the texture data
     */
    public TextureData getData() {
        return data;
    }

    /**
     * <p>
     * Returns the engine texture ID used by engine drawing APIs. Owned asset IDs
     * survive backend and mip storage changes. Native integrations must resolve
     * the current device handle through {@link BgfxGraphics#textureHandle(int)}.
     * </p>
     *
     * @return engine texture identifier, or a wrapped native handle
     */
    public int getTextureID() {
        return textureID;
    }

    /**
     * <p>
     * Returns the width of this texture in pixels.
     * </p>
     *
     * @return the texture width
     */
    public int getWidth() {
        return data.width();
    }

    /**
     * Describes signed-distance sampling to the standard batch shader. Ordinary
     * textures return zero; MSDF atlas textures return their full distance range
     * in texels; R8 coverage pages return a negative marker. This immutable metadata is read only when binding a new texture.
     * @return full MSDF range, negative R8 coverage marker, or zero for RGBA sampling
     */
    public float getDistanceFieldRange() { return 0f; }

    /**
     * <p>
     * Returns the height of this texture in pixels.
     * </p>
     *
     * @return the texture height
     */
    public int getHeight() {
        return data.height();
    }

    /**
     * <p>
     * Creates a new {@link Sprite} backed by this texture.
     * </p>
     *
     * <p>
     * This is a convenience factory method for quickly turning a texture into a
     * full-region sprite without manually constructing the sprite yourself.
     * </p>
     *
     * @return a new sprite using this texture
     */
    public Sprite sprite() {
        return new Sprite(this);
    }

    /**
     * <p>
     * Disposes this texture's GPU resource and clears stored references.
     * </p>
     *
     * <p>
     * This releases the owned GPU texture after queued commands finish.
     * After disposal, the texture data and filter references are set to
     * {@code null}. The instance should not be used again after this call.
     * </p>
     */
    public void dispose() {
        if (disposed) {
            return;
        }

        BgfxResources.unregister(this);
        if (bgfxTexture != null) bgfxTexture.close();
        else {
            BgfxGraphics.resetTextureState(textureID);
            bgfx_destroy_texture((short) textureID);
        }
        if (ownsData && data != null) {
            data.dispose();
        }
        this.data = null;
        this.filter = null;
        this.disposed = true;
    }

    /**
     * <p>
     * Resets this texture to its pooled default state.
     * </p>
     *
     * <p>
     * The reset behavior restores the filtering mode to
     * {@link TextureFilter#NEAREST}.
     * </p>
     */
    @Override
    public void reset() {
        setFilter(TextureFilter.NEAREST);
    }

    @Override
    public void validateGraphicsReset(GraphicsBackend backend) {
        if (bgfxTexture == null) throw new IllegalStateException("Wrapped native textures require explicit ownership and cannot be restored automatically");
    }

}
