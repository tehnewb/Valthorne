package valthorne.graphics.texture;

import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * The {@code TexturePacker} class is a CPU-side utility for constructing a new
 * texture atlas from arbitrary subregions of one or more existing textures.
 * It collects region requests, copies pixel buffer for each requested region into
 * a CPU buffer, and finally returns a {@link TextureData} containing the atlas.
 *
 * <p>No OpenGL calls are made here; uploading to the GPU is the responsibility
 * of the rendering layer (e.g., {@link Texture}).</p>
 *
 * @author Albert Beaupre
 * @since November 26th, 2025
 */
public class TexturePacker {

    /**
     * Internal structure describing a single copy operation from a source texture
     * into the final atlas buffer.
     */
    private record RegionRequest(TextureData src, int sx, int sy, int sw, int sh, int dx, int dy) {
    }

    /**
     * List of all pending region copy requests.
     */
    private final List<RegionRequest> regions = new ArrayList<>();

    /**
     * Width of the final output atlas.
     */
    private final int finalWidth;

    /**
     * Height of the final output atlas.
     */
    private final int finalHeight;

    /**
     * Constructs a texture packer with a fixed atlas size.
     *
     * @param width  final atlas width in pixels
     * @param height final atlas height in pixels
     */
    public TexturePacker(int width, int height) {
        this.finalWidth = width;
        this.finalHeight = height;
    }

    /**
     * Adds a rectangular region from a source texture into the atlas.
     *
     * @param src source texture data (CPU-side pixels)
     * @param sx  region X offset inside source texture
     * @param sy  region Y offset inside source texture
     * @param sw  width of the region to copy
     * @param sh  height of the region to copy
     * @param dx  destination X position in the final atlas
     * @param dy  destination Y position in the final atlas
     */
    public void addRegion(TextureData src, int sx, int sy, int sw, int sh, int dx, int dy) {
        regions.add(new RegionRequest(src, sx, sy, sw, sh, dx, dy));
    }

    /**
     * Bakes all added regions into a single final texture atlas.
     *
     * <p>This method performs only CPU work and returns a {@link TextureData}
     * containing the atlas pixels. Uploading to OpenGL should be done by
     * constructing a {@link Texture} with the result.</p>
     *
     * @return new {@link TextureData} containing the packed texture atlas
     */
    public TextureData bake() {
        // Allocate RGBA8 buffer
        ByteBuffer result = BufferUtils.createByteBuffer(finalWidth * finalHeight * 4);

        // Fill with transparent pixels
        for (int i = 0; i < finalWidth * finalHeight; i++) {
            result.put((byte) 0); // R
            result.put((byte) 0); // G
            result.put((byte) 0); // B
            result.put((byte) 0); // A
        }
        result.flip(); // position=0, limit=capacity

        // Copy each region into atlas
        for (RegionRequest r : regions) {
            blitRegion(r, result);
        }

        // Atlas is ready in CPU memory
        return new TextureData(result, (short) finalWidth, (short) finalHeight);
    }

    /**
     * Copies a rectangular region from a source texture into the target atlas buffer.
     *
     * <p>Assumes both source and destination are RGBA8, with pixels laid out as
     * a contiguous array of width*height texels.</p>
     *
     * <p>Coordinates sx/sy/dx/dy are treated as if (0,0) is the top-left
     * logical origin. Internally, the buffers are bottom-up (to match the
     * flipped load used for OpenGL), so we convert with (height - 1 - y).</p>
     *
     * @param r         region to copy
     * @param destAtlas the destination atlas buffer
     */
    private void blitRegion(RegionRequest r, ByteBuffer destAtlas) {
        ByteBuffer srcPixels = r.src.buffer();
        int srcW = r.src.width();
        int srcH = r.src.height();

        // Iterate region rows
        for (int y = 0; y < r.sh; y++) {
            for (int x = 0; x < r.sw; x++) {

                int srcX = r.sx + x;
                int srcY = r.sy + y;

                // Source buffer is stored bottom-up; convert from top-down coords
                int srcIndex = ((srcH - 1 - srcY) * srcW + srcX) * 4;

                int dstX = r.dx + x;
                int dstY = r.dy + y;

                // Destination atlas also bottom-up
                int dstIndex = ((finalHeight - 1 - dstY) * finalWidth + dstX) * 4;

                destAtlas.put(dstIndex, srcPixels.get(srcIndex));
                destAtlas.put(dstIndex + 1, srcPixels.get(srcIndex + 1));
                destAtlas.put(dstIndex + 2, srcPixels.get(srcIndex + 2));
                destAtlas.put(dstIndex + 3, srcPixels.get(srcIndex + 3));
            }
        }
    }
}
