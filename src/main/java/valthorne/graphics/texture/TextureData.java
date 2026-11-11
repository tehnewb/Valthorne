package valthorne.graphics.texture;

import org.lwjgl.BufferUtils;
import org.lwjgl.stb.STBImage;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

import static org.lwjgl.system.MemoryUtil.memAddress0;

/**
 * Immutable description of decoded CPU-side RGBA pixels and their dimensions.
 * Loading uses STB without creating a GPU resource. Construct a {@link Texture}
 * on the graphics owner to upload these pixels through the selected bgfx backend.
 *
 * <p>Image factories request four channels and flip rows by default to match the
 * engine's UV convention. The explicit flip overload can preserve source row
 * order. The loading factories own their native pixel allocations until
 * {@link #dispose()}. Explicitly supplied buffers are borrowed: their original
 * allocator or garbage collector controls their lifetime. Disposing such a view
 * never frees caller-owned storage.</p>
 *
 * @param buffer the raw binary buffer containing the texture image buffer
 * @param width  the width of the decoded texture in pixels
 * @param height the height of the decoded texture in pixels
 * @author Albert Beaupre
 * @since November 26th, 2025
 */
public record TextureData(ByteBuffer buffer, int width, int height) {

    /**
     * Serializes STB image operations and access to decoded-buffer ownership.
     */
    private static final Object STB_LOCK = new Object();
    /**
     * Live factory-owned records, removed on disposal without retaining freed buffers.
     */
    private static final Set<TextureData> nativeImages = Collections.newSetFromMap(new IdentityHashMap<>());

    /**
     * Reads and decodes image bytes into CPU-side RGBA pixel storage.
     *
     * @param path the file path to the texture image (e.g., PNG, JPEG)
     * @return decoded RGBA pixels and their dimensions
     * @throws RuntimeException if an I/O error occurs or if the image decoding fails
     */
    public static TextureData load(String path) {
        try {
            return load(Files.readAllBytes(Path.of(path)), true);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Loads a texture from the specified file path and optionally flips it vertically before decoding.
     *
     * @param path           the file path to the texture image (e.g., PNG, JPEG)
     * @param flipVertically whether to flip the image vertically during decoding
     * @return a new {@code TextureData} instance containing the decoded image data
     * @throws RuntimeException if an I/O error occurs while reading the file
     */
    public static TextureData load(String path, boolean flipVertically) {
        try {
            return load(Files.readAllBytes(Path.of(path)), flipVertically);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Loads a texture from raw image bytes and decodes it.
     *
     * @param data the raw image bytes (e.g., PNG, JPEG)
     * @return a new {@code TextureData} instance containing the decoded image data
     */
    public static TextureData load(byte[] data) {
        return load(data, true);
    }

    /**
     * Decodes encoded image bytes into owned CPU-side RGBA pixels.
     *
     * <p>This method handles:</p>
     * <ul>
     *     <li>ByteBuffer preparation</li>
     *     <li>STB image decoding</li>
     *     <li>Optional image-row flipping</li>
     *     <li>RGBA output and pixel dimensions</li>
     * </ul>
     *
     * @param data raw PNG/JPEG/etc. bytes
     * @param flipVertically whether to reverse decoded image rows
     * @return decoded RGBA pixels and their dimensions
     * @throws RuntimeException if STBImage fails to decode the image
     */
    public static TextureData load(byte[] data, boolean flipVertically) {
        /*
         * Track the owning record by identity so borrowed views of the same
         * buffer cannot release an allocation owned by a factory result.
         */
        final IntBuffer w = BufferUtils.createIntBuffer(1);
        final IntBuffer h = BufferUtils.createIntBuffer(1);
        final IntBuffer comp = BufferUtils.createIntBuffer(1);

        ByteBuffer dataBuffer = BufferUtils.createByteBuffer(data.length);
        dataBuffer.put(data).flip();

        ByteBuffer image;

        synchronized (STB_LOCK) {
            STBImage.stbi_set_flip_vertically_on_load(flipVertically);
            image = STBImage.stbi_load_from_memory(dataBuffer, w, h, comp, 4);
        }

        if (image == null)
            throw new RuntimeException("Failed to load image: " + STBImage.stbi_failure_reason());

        int width = w.get(0);
        int height = h.get(0);

        TextureData decoded = new TextureData(image, width, height);
        synchronized (STB_LOCK) {
            nativeImages.add(decoded);
        }
        return decoded;
    }

    /**
     * Converts the {@code TextureData} instance into a {@code Texture} object.
     *
     * @return a new {@code Texture} instance created from the current {@code TextureData}
     */
    public Texture asTexture() {
        return new Texture(this);
    }

    /**
     * Converts the current {@code TextureData} instance into a {@code NinePatchTexture}.
     *
     * @param left   the number of pixels to define the left stretchable area
     * @param right  the number of pixels to define the right stretchable area
     * @param top    the number of pixels to define the top stretchable area
     * @param bottom the number of pixels to define the bottom stretchable area
     * @return a new {@code NinePatchTexture} instance created based on the specified stretchable areas
     */
    public NinePatchTexture asNinePatchTexture(int left, int right, int top, int bottom) {
        return new NinePatchTexture(this, left, right, top, bottom);
    }

    /**
     * Releases a factory-owned STB allocation exactly once. Explicit constructor
     * buffers remain owned by their caller. The buffer's current position does not
     * affect the allocation address. Disposed factory data must no longer be read
     * or uploaded; the record retains its dimensions but no usable native pixels.
     */
    public void dispose() {
        if (buffer == null) return;
        synchronized (STB_LOCK) {
            if (!nativeImages.remove(this)) return;
            STBImage.nstbi_image_free(memAddress0(buffer));
        }
    }

}
