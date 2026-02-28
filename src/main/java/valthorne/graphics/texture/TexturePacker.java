package valthorne.graphics.texture;

import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * CPU-side texture atlas builder that copies pixels from existing {@link TextureData}, {@link Texture},
 * or {@link TextureRegion} sources into a new RGBA8 atlas buffer.
 *
 * <h2>Example</h2>
 * <pre>{@code
 * // Suppose you already have CPU-side data for several textures/regions.
 * Texture player = new Texture("assets/player.png");       // Must have CPU pixels via getData()
 * Texture ui     = new Texture("assets/ui.png");
 *
 * TexturePacker packer = new TexturePacker(1024, 1024);
 *
 * // Copy a raw rectangle from a texture into the atlas at (0,0).
 * packer.addRegion(player,  0, 0, 128, 128,   0,   0);
 *
 * // Copy a TextureRegion into the atlas at (256,0).
 * TextureRegion button = new TextureRegion(ui, 16, 16, 64, 32);
 * packer.addRegion(button, 256, 0);
 *
 * // Bake CPU atlas pixels.
 * TextureData atlasData = packer.bake();
 *
 * // Upload to GPU (your rendering layer responsibility).
 * Texture atlas = new Texture(atlasData);
 * }</pre>
 *
 * <h2>Coordinate expectations</h2>
 * <ul>
 *     <li>All region coordinates (sx, sy, dx, dy) are interpreted as <b>top-left origin</b> in pixel space.</li>
 *     <li>Internally, the pixel buffers are treated as <b>bottom-up</b> (common in OpenGL-facing pipelines),
 *     so a conversion is performed when reading/writing.</li>
 * </ul>
 *
 * <h2>What this class does not do</h2>
 * <ul>
 *     <li>No packing algorithm. You must decide placement (dx/dy) yourself.</li>
 *     <li>No OpenGL calls. This only produces a {@link TextureData} atlas in CPU memory.</li>
 * </ul>
 *
 * @author Albert Beaupre
 * @since November 25th, 2025
 */
public class TexturePacker {

    private final List<RegionRequest> regions = new ArrayList<>(); // Pending copy operations to apply during bake().
    private final int finalWidth; // Atlas output width in pixels.
    private final int finalHeight; // Atlas output height in pixels.

    /**
     * Creates a texture packer that produces an atlas with a fixed final size.
     *
     * <p>This does not allocate the atlas buffer immediately. Allocation happens in {@link #bake()}.</p>
     *
     * @param width  atlas width in pixels (must be > 0)
     * @param height atlas height in pixels (must be > 0)
     * @throws IllegalArgumentException if width or height is <= 0
     */
    public TexturePacker(int width, int height) {
        if (width <= 0) throw new IllegalArgumentException("width must be > 0");
        if (height <= 0) throw new IllegalArgumentException("height must be > 0");
        this.finalWidth = width;
        this.finalHeight = height;
    }

    /**
     * Queues a rectangular region copy from a source {@link TextureData} into the atlas.
     *
     * <p>All coordinates are interpreted as if (0,0) is the <b>top-left</b> of the image in pixel space.</p>
     *
     * <p>This method only records the request. The actual pixel copying occurs when {@link #bake()} is called.</p>
     *
     * @param src source CPU pixels (RGBA8) (must be non-null)
     * @param sx  source X in pixels (top-left origin)
     * @param sy  source Y in pixels (top-left origin)
     * @param sw  region width in pixels (must be > 0)
     * @param sh  region height in pixels (must be > 0)
     * @param dx  destination X in pixels within the atlas (top-left origin)
     * @param dy  destination Y in pixels within the atlas (top-left origin)
     * @throws NullPointerException     if src is null
     * @throws IllegalArgumentException if sw/sh <= 0
     */
    public void addRegion(TextureData src, int sx, int sy, int sw, int sh, int dx, int dy) {
        Objects.requireNonNull(src, "src");
        if (sw <= 0) throw new IllegalArgumentException("sw must be > 0");
        if (sh <= 0) throw new IllegalArgumentException("sh must be > 0");
        regions.add(new RegionRequest(src, sx, sy, sw, sh, dx, dy));
    }

    /**
     * Queues a rectangular region copy from a {@link Texture} into the atlas.
     *
     * <p>This is a convenience overload that uses {@link Texture#getData()} as the CPU-side pixel source.</p>
     *
     * @param src source texture (must be non-null)
     * @param sx  source X in pixels (top-left origin)
     * @param sy  source Y in pixels (top-left origin)
     * @param sw  region width in pixels (must be > 0)
     * @param sh  region height in pixels (must be > 0)
     * @param dx  destination X in pixels within the atlas (top-left origin)
     * @param dy  destination Y in pixels within the atlas (top-left origin)
     * @throws NullPointerException     if src is null
     * @throws IllegalArgumentException if sw/sh <= 0
     */
    public void addRegion(Texture src, int sx, int sy, int sw, int sh, int dx, int dy) {
        Objects.requireNonNull(src, "src");
        TextureData data = src.getData();
        addRegion(data, sx, sy, sw, sh, dx, dy);
    }

    /**
     * Queues an entire {@link Texture} copy into the atlas at (dx,dy).
     *
     * <p>This copies the full texture rectangle: (0,0) to (width,height) in top-left pixel space.</p>
     *
     * @param texture source texture (must be non-null)
     * @param dx      destination X in pixels within the atlas (top-left origin)
     * @param dy      destination Y in pixels within the atlas (top-left origin)
     * @throws NullPointerException if texture is null
     */
    public void addRegion(Texture texture, int dx, int dy) {
        Objects.requireNonNull(texture, "texture");
        TextureData data = texture.getData();
        addRegion(data, 0, 0, data.width(), data.height(), dx, dy);
    }

    /**
     * Queues an entire {@link TextureRegion} copy into the atlas at (dx,dy).
     *
     * <p>The region rectangle is taken from the region's pixel fields:
     * {@link TextureRegion#getRegionX()}, {@link TextureRegion#getRegionY()},
     * {@link TextureRegion#getRegionWidth()}, {@link TextureRegion#getRegionHeight()}.</p>
     *
     * @param region region to copy (must be non-null)
     * @param dx     destination X in pixels within the atlas (top-left origin)
     * @param dy     destination Y in pixels within the atlas (top-left origin)
     * @throws NullPointerException if region is null, or if its backing texture is null
     */
    public void addRegion(TextureRegion region, int dx, int dy) {
        Objects.requireNonNull(region, "region");
        Texture texture = region.getTexture();
        if (texture == null) throw new NullPointerException("TextureRegion has null backing Texture");

        TextureData data = texture.getData();

        int sx = (int) region.getRegionX();
        int sy = (int) region.getRegionY();
        int sw = (int) region.getRegionWidth();
        int sh = (int) region.getRegionHeight();

        addRegion(data, sx, sy, sw, sh, dx, dy);
    }

    /**
     * Builds the final atlas into a new {@link TextureData} (RGBA8).
     *
     * <p>Implementation details:</p>
     * <ul>
     *     <li>Allocates a {@code finalWidth * finalHeight * 4} RGBA8 {@link ByteBuffer}.</li>
     *     <li>Initializes it to fully transparent (0,0,0,0).</li>
     *     <li>Applies each queued {@link RegionRequest} by copying pixels row-by-row.</li>
     * </ul>
     *
     * <p>Bounds behavior:</p>
     * <ul>
     *     <li>Requests that lie partially outside the source or destination are <b>clipped</b>.</li>
     *     <li>Requests that do not overlap the atlas after clipping become a no-op.</li>
     * </ul>
     *
     * @return new {@link TextureData} containing the atlas pixels
     */
    public TextureData bake() {
        ByteBuffer result = BufferUtils.createByteBuffer(finalWidth * finalHeight * 4);

        for (int i = 0; i < finalWidth * finalHeight; i++) {
            result.put((byte) 0);
            result.put((byte) 0);
            result.put((byte) 0);
            result.put((byte) 0);
        }
        result.flip();

        for (RegionRequest r : regions) {
            blitRegionClipped(r, result);
        }

        return new TextureData(result, (short) finalWidth, (short) finalHeight);
    }

    /**
     * Copies a single queued region into the destination atlas buffer, with clipping.
     *
     * <p>This method treats incoming coordinates as top-left origin, but reads/writes the underlying buffers
     * as bottom-up. The conversion is:</p>
     * <ul>
     *     <li>{@code bufferRow = (height - 1 - y)}</li>
     * </ul>
     *
     * <p>Clipping rules:</p>
     * <ul>
     *     <li>Clips the source rectangle to [0..srcW) x [0..srcH).</li>
     *     <li>Clips the destination rectangle to [0..atlasW) x [0..atlasH).</li>
     *     <li>Maintains correct source/destination alignment during clipping.</li>
     * </ul>
     *
     * @param r         region request describing what to copy and where
     * @param destAtlas destination atlas RGBA8 buffer
     */
    private void blitRegionClipped(RegionRequest r, ByteBuffer destAtlas) {
        TextureData src = r.src;
        if (src == null) return;

        ByteBuffer srcPixels = src.buffer();
        int srcW = src.width();
        int srcH = src.height();
        if (srcPixels == null) return;

        int sx0 = r.sx;
        int sy0 = r.sy;
        int dx0 = r.dx;
        int dy0 = r.dy;
        int w = r.sw;
        int h = r.sh;

        if (w <= 0 || h <= 0) return;

        // Clip left/top against source and destination.
        int clipLeft = Math.max(0, Math.max(-sx0, -dx0));
        int clipTop = Math.max(0, Math.max(-sy0, -dy0));
        sx0 += clipLeft;
        dx0 += clipLeft;
        w -= clipLeft;
        sy0 += clipTop;
        dy0 += clipTop;
        h -= clipTop;

        // Clip right/bottom against source and destination.
        int clipRight = Math.max(0, Math.max((sx0 + w) - srcW, (dx0 + w) - finalWidth));
        int clipBottom = Math.max(0, Math.max((sy0 + h) - srcH, (dy0 + h) - finalHeight));
        w -= clipRight;
        h -= clipBottom;

        if (w <= 0 || h <= 0) return;

        // Copy row-by-row.
        for (int y = 0; y < h; y++) {
            int srcY = sy0 + y;
            int dstY = dy0 + y;

            int srcRow = (srcH - 1 - srcY);
            int dstRow = (finalHeight - 1 - dstY);

            int srcBase = (srcRow * srcW + sx0) * 4;
            int dstBase = (dstRow * finalWidth + dx0) * 4;

            for (int x = 0; x < w; x++) {
                int si = srcBase + (x * 4);
                int di = dstBase + (x * 4);

                destAtlas.put(di,     srcPixels.get(si));
                destAtlas.put(di + 1, srcPixels.get(si + 1));
                destAtlas.put(di + 2, srcPixels.get(si + 2));
                destAtlas.put(di + 3, srcPixels.get(si + 3));
            }
        }
    }

    /**
     * Immutable description of one region copy request.
     *
     * @param src source texture pixels
     * @param sx  source X (top-left origin)
     * @param sy  source Y (top-left origin)
     * @param sw  width to copy
     * @param sh  height to copy
     * @param dx  destination X in atlas (top-left origin)
     * @param dy  destination Y in atlas (top-left origin)
     */
    private record RegionRequest(TextureData src, int sx, int sy, int sw, int sh, int dx, int dy) { }
}