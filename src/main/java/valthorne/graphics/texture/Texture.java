package valthorne.graphics.texture;

import valthorne.graphics.Sprite;
import valthorne.io.pool.Poolable;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL30.glGenerateMipmap;

/**
 * <p>
 * {@code Texture} represents a GPU-backed 2D OpenGL texture in Valthorne.
 * It acts as the primary wrapper around a texture object ID and its associated
 * {@link TextureData}. This class is responsible for creating the OpenGL texture,
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
public class Texture implements Poolable {

    protected TextureData data; // CPU-side texture metadata and pixel buffer associated with this texture
    protected final int textureID; // OpenGL texture object ID created for this texture
    protected TextureFilter filter = TextureFilter.NEAREST; // Current filtering mode applied to the texture

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
        this(TextureData.load(path));
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
        this(TextureData.load(data));
    }

    /**
     * <p>
     * Creates a texture from an existing {@link TextureData} instance.
     * </p>
     *
     * <p>
     * This constructor creates a new OpenGL texture object, binds it,
     * applies the default filtering mode, applies clamp-to-edge wrapping,
     * and uploads the RGBA pixel buffer to the GPU using
     * {@code glTexImage2D}.
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
        if (data == null) throw new NullPointerException("TextureData cannot be null");

        this.textureID = glGenTextures();
        this.data = data;

        glBindTexture(GL_TEXTURE_2D, textureID);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, filter.minFilter);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, filter.magFilter);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, data.width(), data.height(), 0, GL_RGBA, GL_UNSIGNED_BYTE, data.buffer());
    }

    /**
     * <p>
     * Creates a texture wrapper around an existing OpenGL texture ID and
     * texture data.
     * </p>
     *
     * <p>
     * This constructor does not generate a new OpenGL texture object or upload
     * data. It simply wraps an already existing texture ID with the supplied
     * metadata. This is useful for subclasses or advanced cases where the
     * texture object has already been created elsewhere.
     * </p>
     *
     * @param textureID the existing OpenGL texture ID
     * @param data      the associated texture data
     * @throws NullPointerException if {@code data} is {@code null}
     */
    protected Texture(int textureID, TextureData data) {
        if (data == null) throw new NullPointerException("TextureData cannot be null");

        this.textureID = textureID;
        this.data = data;
    }

    /**
     * <p>
     * Binds this texture to {@code GL_TEXTURE_2D}.
     * </p>
     *
     * <p>
     * This makes the texture the current active 2D texture for subsequent
     * OpenGL operations in the currently active texture unit.
     * </p>
     */
    public void bind() {
        glBindTexture(GL_TEXTURE_2D, textureID);
    }

    /**
     * <p>
     * Applies a new filtering mode to this texture.
     * </p>
     *
     * <p>
     * The texture is bound, the minification and magnification parameters are
     * updated, and mipmaps are generated automatically when the supplied filter
     * requires them.
     * </p>
     *
     * @param filter the new filter to apply
     * @throws NullPointerException if {@code filter} is {@code null}
     */
    public void setFilter(TextureFilter filter) {
        if (filter == null) throw new NullPointerException("TextureFilter cannot be null");

        this.filter = filter;
        glBindTexture(GL_TEXTURE_2D, textureID);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, filter.minFilter);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, filter.magFilter);

        if (filter.usesMipmaps()) glGenerateMipmap(GL_TEXTURE_2D);
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
     * Returns the OpenGL texture object ID.
     * </p>
     *
     * @return the OpenGL texture ID
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
     * This deletes the OpenGL texture object using {@code glDeleteTextures}.
     * After disposal, the texture data and filter references are set to
     * {@code null}. The instance should not be used again after this call.
     * </p>
     */
    public void dispose() {
        glDeleteTextures(textureID);
        this.data = null;
        this.filter = null;
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
}