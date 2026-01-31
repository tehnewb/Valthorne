package valthorne.graphics.texture;

/**
 * Simple nine-patch renderer built on top of {@link Texture}.
 *
 * <p>This class does <b>not</b> do any CPU-side pixel baking or resizing.
 * It simply interprets the source {@link TextureData} as a 3x3 nine-patch
 * (corners, edges, center), and in {@link #draw()} it renders nine scaled
 * quads using the original texture buffer.</p>
 *
 * <p>The nine-patch is defined by pixel borders: left, right, top, bottom.
 * These borders are measured in the source texture's pixel space.</p>
 *
 * <p>At render time, the nine slices are scaled to fit the current
 * {@link #getWidth()} and {@link #getHeight()} dimensions. No additional
 * textures or FBOs are created, and memory usage stays low and stable.</p>
 *
 * <p><b>Note:</b> This class assumes you will not rotate or flip the
 * nine-patch. It is intended for axis-aligned UI panels. Using
 * {@link #setRotation(float)} or flip methods may produce undefined results.</p>
 *
 * @author Albert Beaupre
 * @since December 1st, 2025
 */
public class NinePatchTexture extends Texture {

    // Reusable temp arrays (NO per-frame allocation)
    private final float[] tmpOut = new float[2];
    // Slice data reused every frame
    private final float[][] slicePos = new float[9][2];
    private final float[][] sliceSize = new float[9][2];
    private final int[][] srcRegions = new int[9][4];
    /**
     * Pixel border sizes in the source texture.
     */
    private int left;
    private int right;
    private int top;
    private int bottom;

    /**
     * Creates a NinePatchTexture from an image path and border sizes.
     *
     * @param path   image file path
     * @param left   left border in pixels
     * @param right  right border in pixels
     * @param top    top border in pixels
     * @param bottom bottom border in pixels
     */
    public NinePatchTexture(String path, int left, int right, int top, int bottom) {
        this(TextureData.load(path), left, right, top, bottom);
    }

    /**
     * Creates a NinePatchTexture from an existing {@link TextureData}
     * and pixel border sizes.
     *
     * @param data   source texture buffer
     * @param left   left border in pixels
     * @param right  right border in pixels
     * @param top    top border in pixels
     * @param bottom bottom border in pixels
     */
    public NinePatchTexture(TextureData data, int left, int right, int top, int bottom) {
        super(data);
        this.left = left;
        this.right = right;
        this.top = top;
        this.bottom = bottom;

        // By default, the logical size of the patch is the full source size.
        setSize(data.width(), data.height());
    }

    /**
     * Draws the nine-patch using 9 quads from the same underlying texture.
     *
     * <p>This method:
     * <ol>
     *     <li>Computes the destination rectangles for corners, edges, and center</li>
     *     <li>For each slice, sets an appropriate source region</li>
     *     <li>Sets position and size for that slice</li>
     *     <li>Calls {@link Texture#draw()} to render a quad</li>
     * </ol>
     *
     * <p>All slices share the same texture object and GL state, so the cost
     * is very low (9 small draw calls).</p>
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

        slicePos[0][0] = baseX;
        slicePos[0][1] = baseY;
        slicePos[1][0] = x1;
        slicePos[1][1] = baseY;
        slicePos[2][0] = x2;
        slicePos[2][1] = baseY;

        slicePos[3][0] = baseX;
        slicePos[3][1] = y1;
        slicePos[4][0] = x1;
        slicePos[4][1] = y1;
        slicePos[5][0] = x2;
        slicePos[5][1] = y1;

        slicePos[6][0] = baseX;
        slicePos[6][1] = y2;
        slicePos[7][0] = x1;
        slicePos[7][1] = y2;
        slicePos[8][0] = x2;
        slicePos[8][1] = y2;

        sliceSize[0][0] = left;
        sliceSize[0][1] = top;
        sliceSize[1][0] = stretchW;
        sliceSize[1][1] = top;
        sliceSize[2][0] = right;
        sliceSize[2][1] = top;

        sliceSize[3][0] = left;
        sliceSize[3][1] = stretchH;
        sliceSize[4][0] = stretchW;
        sliceSize[4][1] = stretchH;
        sliceSize[5][0] = right;
        sliceSize[5][1] = stretchH;

        sliceSize[6][0] = left;
        sliceSize[6][1] = bottom;
        sliceSize[7][0] = stretchW;
        sliceSize[7][1] = bottom;
        sliceSize[8][0] = right;
        sliceSize[8][1] = bottom;

        int[] r;

        r = srcRegions[0];
        r[0] = 0;
        r[1] = 0;
        r[2] = left;
        r[3] = top;

        r = srcRegions[1];
        r[0] = left;
        r[1] = 0;
        r[2] = srcW - right;
        r[3] = top;

        r = srcRegions[2];
        r[0] = srcW - right;
        r[1] = 0;
        r[2] = srcW;
        r[3] = top;

        r = srcRegions[3];
        r[0] = 0;
        r[1] = top;
        r[2] = left;
        r[3] = srcH - bottom;

        r = srcRegions[4];
        r[0] = left;
        r[1] = top;
        r[2] = srcW - right;
        r[3] = srcH - bottom;

        r = srcRegions[5];
        r[0] = srcW - right;
        r[1] = top;
        r[2] = srcW;
        r[3] = srcH - bottom;

        r = srcRegions[6];
        r[0] = 0;
        r[1] = srcH - bottom;
        r[2] = left;
        r[3] = srcH;

        r = srcRegions[7];
        r[0] = left;
        r[1] = srcH - bottom;
        r[2] = srcW - right;
        r[3] = srcH;

        r = srcRegions[8];
        r[0] = srcW - right;
        r[1] = srcH - bottom;
        r[2] = srcW;
        r[3] = srcH;

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

        setRegion(0, 0, srcW, srcH);
        super.setPosition(baseX, baseY);
        super.setSize(totalW, totalH);
    }

    /**
     * Retrieves the size of the left border in pixels.
     *
     * @return the left border size in pixels
     */
    public int getLeft() {
        return left;
    }

    /**
     * Sets the size of the left border in pixels.
     *
     * @param left the left border size in pixels to be set
     */
    public void setLeft(int left) {
        this.left = left;
    }

    /**
     * Retrieves the size of the right border in pixels.
     *
     * @return the right border size in pixels
     */
    public int getRight() {
        return right;
    }

    /**
     * Sets the size of the right border in pixels.
     *
     * @param right the right border size in pixels to be set
     */
    public void setRight(int right) {
        this.right = right;
    }

    /**
     * Retrieves the size of the top border in pixels.
     *
     * @return the top border size in pixels
     */
    public int getTop() {
        return top;
    }

    /**
     * Sets the size of the top border in pixels.
     *
     * @param top the top border size in pixels to be set
     */
    public void setTop(int top) {
        this.top = top;
    }

    /**
     * Retrieves the size of the bottom border in pixels.
     *
     * @return the bottom border size in pixels
     */
    public int getBottom() {
        return bottom;
    }

    /**
     * Sets the size of the bottom border in pixels.
     *
     * @param bottom the bottom border size in pixels to be set
     */
    public void setBottom(int bottom) {
        this.bottom = bottom;
    }
}
