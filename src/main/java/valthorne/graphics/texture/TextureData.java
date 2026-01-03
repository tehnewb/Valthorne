package valthorne.graphics.texture;

import org.lwjgl.BufferUtils;
import org.lwjgl.stb.STBImage;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Immutable container holding OpenGL texture information and its decoded width/height.
 *
 * <p>This record acts as a lightweight wrapper around a created OpenGL texture.
 * It stores the OpenGL-generated texture ID along with the pixel dimensions obtained
 * from STBImage. No rendering functionality is included—only loading and metadata.</p>
 *
 * <p>The texture loading pipeline performs the following operations:</p>
 * <ol>
 *     <li>Converts raw bytes into a direct {@link ByteBuffer}</li>
 *     <li>Decodes image buffer using {@link STBImage#stbi_load_from_memory}</li>
 *     <li>Creates an OpenGL texture via {@code glGenTextures()}</li>
 *     <li>Uploads RGBA8 buffer to GPU memory</li>
 *     <li>Applies texture parameters (min/mag filter, wrap mode)</li>
 *     <li>Frees decoded image memory</li>
 * </ol>
 *
 * <p>This class always forces the loaded image into 4-channel RGBA format for consistency.
 * STBImage is configured to vertically flip textures to match OpenGL UV orientation.</p>
 *
 * @param buffer the raw binary buffer containing the texture image buffer
 * @param width  the width of the decoded texture in pixels
 * @param height the height of the decoded texture in pixels
 * @author Albert Beaupre
 * @since November 26th, 2025
 */
public record TextureData(ByteBuffer buffer, short width, short height) {

    private static final IntBuffer w = BufferUtils.createIntBuffer(1);
    private static final IntBuffer h = BufferUtils.createIntBuffer(1);
    private static final IntBuffer comp = BufferUtils.createIntBuffer(1);

    /**
     * Loads a texture from a file path. This is a convenience wrapper around
     * {@link #load(byte[])} which retrieves the raw file bytes first.
     *
     * @param path file system path to an image
     * @return a fully constructed {@link TextureData} instance
     * @throws RuntimeException if file reading fails or image decoding fails
     */
    public static TextureData load(String path) {
        try {
            return load(Files.readAllBytes(Path.of(path)));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Loads a texture from raw image bytes, decodes it, and uploads it to OpenGL.
     *
     * <p>This method handles:</p>
     * <ul>
     *     <li>ByteBuffer preparation</li>
     *     <li>STB image decoding</li>
     *     <li>Texture creation</li>
     *     <li>Texture parameter configuration</li>
     *     <li>GL texture upload</li>
     * </ul>
     *
     * @param data raw PNG/JPEG/etc. bytes
     * @return a new {@link TextureData} containing the GPU texture ID and pixel size
     * @throws RuntimeException if STBImage fails to decode the image
     */
    public static TextureData load(byte[] data) {
        ByteBuffer dataBuffer = BufferUtils.createByteBuffer(data.length);
        dataBuffer.put(data).flip();

        STBImage.stbi_set_flip_vertically_on_load(true);

        ByteBuffer image = STBImage.stbi_load_from_memory(dataBuffer, w, h, comp, 4);

        if (image == null)
            throw new RuntimeException("Failed to load image: " + STBImage.stbi_failure_reason());

        int width = w.get(0);
        int height = h.get(0);

        return new TextureData(image, (short) width, (short) height);
    }

    /**
     * Releases the native resources associated with the texture data.
     * <p>
     * This method frees the memory associated with the image buffer to prevent
     * memory leaks. It should be called when the texture data is no longer needed.
     */
    public void dispose() {
        STBImage.stbi_image_free(buffer());
    }

}
