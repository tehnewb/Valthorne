package valthorne.graphics.texture;

/**
 * A simple nine-patch (3x3) renderer built on top of {@link Texture}.
 *
 * <p>This class does <b>not</b> generate a new texture or bake pixels. It interprets the source
 * {@link TextureData} as a 3x3 grid (corners, edges, center) and draws nine quads using the same
 * underlying OpenGL texture.</p>
 *
 * <h2>Nine-patch model</h2>
 * <ul>
 *     <li><b>Corners</b> keep their original pixel size (left/right/top/bottom border sizes).</li>
 *     <li><b>Edges</b> stretch in one direction to fill remaining space.</li>
 *     <li><b>Center</b> stretches in both directions to fill remaining space.</li>
 * </ul>
 *
 * <p>The nine-patch is defined by pixel border sizes in source texture space:</p>
 * <ul>
 *     <li>{@link #left} and {@link #right} are the left/right border widths in pixels</li>
 *     <li>{@link #top} and {@link #bottom} are the top/bottom border heights in pixels</li>
 * </ul>
 *
 * <h2>Performance</h2>
 * <p>{@link #draw()} performs 9 small draw calls. It reuses internal arrays to avoid allocations.</p>
 *
 * <h2>Rotation note</h2>
 * <p>This implementation applies rotation math per-slice using {@link #getRotation()} and
 * {@link #getRotationOrigin()}, but it assumes the nine-patch remains axis-aligned in typical UI usage.
 * If you rely on rotation/flipping heavily, validate results visually.</p>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * NinePatchTexture panel = new NinePatchTexture("assets/ui/panel.png", 8, 8, 8, 8);
 * panel.setPosition(100, 100);
 * panel.setSize(400, 200);
 *
 * // In your render loop:
 * panel.draw();
 *
 * panel.dispose();
 * }</pre>
 *
 * @author Albert Beaupre
 * @since February 5th, 2026
 */
public class NinePatchTexture extends Texture {

    private float[] tmpOut = new float[2];              // Scratch array for rotated slice position output (x,y) to avoid allocations.
    private float[][] slicePos = new float[9][2];       // Destination top-left positions for each of the 9 slices (x,y) in world space.
    private float[][] sliceSize = new float[9][2];      // Destination sizes for each of the 9 slices (w,h) in world space.
    private int[][] srcRegions = new int[9][4];         // Source pixel rectangles for each slice: [left, top, right, bottom] in texture space.

    private int left;                                   // Left border width in source pixels (corner/edge thickness).
    private int right;                                  // Right border width in source pixels (corner/edge thickness).
    private int top;                                    // Top border height in source pixels (corner/edge thickness).
    private int bottom;                                 // Bottom border height in source pixels (corner/edge thickness).

    /**
     * Creates a {@code NinePatchTexture} from an image file path and border sizes.
     *
     * <p>This loads the image into {@link TextureData}, uploads it via {@link Texture},
     * and sets the initial logical size to the source image size.</p>
     *
     * @param path   image file path
     * @param left   left border width in pixels
     * @param right  right border width in pixels
     * @param top    top border height in pixels
     * @param bottom bottom border height in pixels
     * @throws NullPointerException if {@code path} is null and {@code TextureData.load(path)} does not accept null
     */
    public NinePatchTexture(String path, int left, int right, int top, int bottom) {
        this(TextureData.load(path), left, right, top, bottom);
    }

    /**
     * Creates a {@code NinePatchTexture} from existing {@link TextureData} and border sizes.
     *
     * <p>Border sizes are interpreted in the source texture's pixel space. The center stretch
     * regions are computed as:</p>
     * <ul>
     *     <li>Horizontal stretch width: {@code srcW - left - right}</li>
     *     <li>Vertical stretch height: {@code srcH - top - bottom}</li>
     * </ul>
     *
     * <p>The initial logical size is set to the full source texture size.</p>
     *
     * @param data   source texture data
     * @param left   left border width in pixels
     * @param right  right border width in pixels
     * @param top    top border height in pixels
     * @param bottom bottom border height in pixels
     * @throws NullPointerException if {@code data} is null
     */
    public NinePatchTexture(TextureData data, int left, int right, int top, int bottom) {
        super(data);
        this.left = left;
        this.right = right;
        this.top = top;
        this.bottom = bottom;

        // Default logical size to full source size.
        setSize(data.width(), data.height());
    }

    /**
     * Draws the nine-patch by rendering 9 quads from the same underlying texture.
     *
     * <p>High-level flow:</p>
     * <ol>
     *     <li>Read current destination box: {@link #getX()}, {@link #getY()}, {@link #getWidth()}, {@link #getHeight()}.</li>
     *     <li>Compute stretchable destination sizes based on borders.</li>
     *     <li>Compute the 9 destination rectangles (positions + sizes).</li>
     *     <li>Compute the 9 source rectangles inside the texture in pixel space.</li>
     *     <li>For each slice:
     *         <ul>
     *             <li>Apply source region via {@link #setRegion(float, float, float, float)}.</li>
     *             <li>Set slice position/size via {@link Texture#setPosition(float, float)} and {@link Texture#setSize(float, float)}.</li>
     *             <li>Draw via {@link Texture#draw()}.</li>
     *         </ul>
     *     </li>
     *     <li>Restore the texture's region/position/size to the original values.</li>
     * </ol>
     *
     * <p><b>Mutation warning:</b> This method repeatedly changes the base {@link Texture}'s region, size, and position.
     * That is required for the implementation. The original values are restored at the end.</p>
     */
    @Override
    public void draw() {
        TextureData data = getData();
        int srcW = data.width();
        int srcH = data.height();

        float baseX = getX();
        float baseY = getY();
        float totalW = getWidth();
        float totalH = getHeight();

        float originX = getRotationOrigin().getX();
        float originY = getRotationOrigin().getY();

        float rad = (float) Math.toRadians(-getRotation());
        float sin = (float) Math.sin(rad);
        float cos = (float) Math.cos(rad);

        float stretchW = Math.max(0f, totalW - left - right);
        float stretchH = Math.max(0f, totalH - top - bottom);

        float x1 = baseX + left;
        float x2 = baseX + left + stretchW;
        float y1 = baseY + top;
        float y2 = baseY + top + stretchH;

        // Destination positions (top-left for each slice in the 3x3 grid).
        slicePos[0][0] = baseX; slicePos[0][1] = baseY;
        slicePos[1][0] = x1;    slicePos[1][1] = baseY;
        slicePos[2][0] = x2;    slicePos[2][1] = baseY;

        slicePos[3][0] = baseX; slicePos[3][1] = y1;
        slicePos[4][0] = x1;    slicePos[4][1] = y1;
        slicePos[5][0] = x2;    slicePos[5][1] = y1;

        slicePos[6][0] = baseX; slicePos[6][1] = y2;
        slicePos[7][0] = x1;    slicePos[7][1] = y2;
        slicePos[8][0] = x2;    slicePos[8][1] = y2;

        // Destination sizes (w,h for each slice).
        sliceSize[0][0] = left;     sliceSize[0][1] = top;
        sliceSize[1][0] = stretchW; sliceSize[1][1] = top;
        sliceSize[2][0] = right;    sliceSize[2][1] = top;

        sliceSize[3][0] = left;     sliceSize[3][1] = stretchH;
        sliceSize[4][0] = stretchW; sliceSize[4][1] = stretchH;
        sliceSize[5][0] = right;    sliceSize[5][1] = stretchH;

        sliceSize[6][0] = left;     sliceSize[6][1] = bottom;
        sliceSize[7][0] = stretchW; sliceSize[7][1] = bottom;
        sliceSize[8][0] = right;    sliceSize[8][1] = bottom;

        // Source regions (pixel rectangles) for each slice in the 3x3 grid.
        int[] r;

        r = srcRegions[0]; r[0] = 0;          r[1] = 0;           r[2] = left;        r[3] = top;
        r = srcRegions[1]; r[0] = left;       r[1] = 0;           r[2] = srcW - right; r[3] = top;
        r = srcRegions[2]; r[0] = srcW - right; r[1] = 0;         r[2] = srcW;        r[3] = top;

        r = srcRegions[3]; r[0] = 0;          r[1] = top;         r[2] = left;        r[3] = srcH - bottom;
        r = srcRegions[4]; r[0] = left;       r[1] = top;         r[2] = srcW - right; r[3] = srcH - bottom;
        r = srcRegions[5]; r[0] = srcW - right; r[1] = top;       r[2] = srcW;        r[3] = srcH - bottom;

        r = srcRegions[6]; r[0] = 0;          r[1] = srcH - bottom; r[2] = left;      r[3] = srcH;
        r = srcRegions[7]; r[0] = left;       r[1] = srcH - bottom; r[2] = srcW - right; r[3] = srcH;
        r = srcRegions[8]; r[0] = srcW - right; r[1] = srcH - bottom; r[2] = srcW;    r[3] = srcH;

        // Draw the 9 slices.
        for (int i = 0; i < 9; i++) {
            int[] uv = srcRegions[i];
            setRegion(uv[0], uv[1], uv[2], uv[3]);

            float lx = slicePos[i][0] - originX;
            float ly = slicePos[i][1] - originY;

            tmpOut[0] = lx * cos - ly * sin + originX;
            tmpOut[1] = lx * sin + ly * cos + originY;

            super.setPosition(tmpOut[0], tmpOut[1]);
            super.setSize(sliceSize[i][0], sliceSize[i][1]);
            super.draw();
        }

        // Restore original state for callers that reuse this Texture after drawing.
        setRegion(0, 0, srcW, srcH);
        super.setPosition(baseX, baseY);
        super.setSize(totalW, totalH);
    }

    /**
     * Returns the left border size in source pixels.
     *
     * @return left border width in pixels
     */
    public int getLeft() {
        return left;
    }

    /**
     * Sets the left border size in source pixels.
     *
     * <p>This affects how large the left corners/edges are and how much horizontal space is left
     * for the stretchable center portion.</p>
     *
     * @param left left border width in pixels
     */
    public void setLeft(int left) {
        this.left = left;
    }

    /**
     * Returns the right border size in source pixels.
     *
     * @return right border width in pixels
     */
    public int getRight() {
        return right;
    }

    /**
     * Sets the right border size in source pixels.
     *
     * @param right right border width in pixels
     */
    public void setRight(int right) {
        this.right = right;
    }

    /**
     * Returns the top border size in source pixels.
     *
     * @return top border height in pixels
     */
    public int getTop() {
        return top;
    }

    /**
     * Sets the top border size in source pixels.
     *
     * @param top top border height in pixels
     */
    public void setTop(int top) {
        this.top = top;
    }

    /**
     * Returns the bottom border size in source pixels.
     *
     * @return bottom border height in pixels
     */
    public int getBottom() {
        return bottom;
    }

    /**
     * Sets the bottom border size in source pixels.
     *
     * @param bottom bottom border height in pixels
     */
    public void setBottom(int bottom) {
        this.bottom = bottom;
    }

    /**
     * Disposes the underlying OpenGL texture and clears internal scratch buffers.
     *
     * <p>After calling this method, the instance must not be used again.</p>
     */
    @Override
    public void dispose() {
        super.dispose();

        this.tmpOut = null;
        this.slicePos = null;
        this.sliceSize = null;
        this.srcRegions = null;
    }
}