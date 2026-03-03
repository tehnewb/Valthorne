package valthorne.graphics.texture;

import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL11.*;

/**
 * Nine-patch texture (3x3) that draws all slices in a single draw call.
 *
 * <h2>Example</h2>
 * <pre>{@code
 * // Load nine-patch source and define border sizes (in source pixels).
 * NinePatchTexture panel = new NinePatchTexture("assets/ui/panel.png", 6, 6, 6, 6);
 *
 * // Place and size it like a normal texture.
 * panel.setPosition(24, 24);
 * panel.setSize(420, 180);
 *
 * // Optional: set a rotation origin (corners will still remain crisp relative to the source).
 * panel.setRotationOriginCenter();
 * panel.setRotation(5f);
 *
 * // Draw in your normal render loop (fixed-function pipeline).
 * panel.draw();
 * }</pre>
 *
 * <h2>What this class does</h2>
 * <p>
 * A nine-patch scales UI panels without stretching corners. The texture is divided into 9 rectangles:
 * 4 corners (never stretched), 4 edges (stretched in one axis), and 1 center (stretched in both axes).
 * This class builds the full 3x3 mesh once (or when it becomes "dirty") and draws it using a single
 * {@code glDrawArrays(GL_QUADS, 0, 36)} call.
 * </p>
 *
 * <h2>How the mesh is generated</h2>
 * <ul>
 *     <li><b>Local mesh</b>: computed from the requested output size/scale and border pixel sizes.</li>
 *     <li><b>Slice UVs</b>: computed from source pixel rectangles and mapped into the current base region.</li>
 *     <li><b>World vertices</b>: local vertices are transformed by rotation + translation using the current rotation origin.</li>
 * </ul>
 *
 * <h2>Region + flip compatibility</h2>
 * <p>
 * This class respects the UV region and flip state managed by {@link Texture}. If you call
 * {@link #setRegion(float, float, float, float)} or flip methods, the nine-patch UVs are regenerated so
 * each slice continues to sample the correct area inside the base region.
 * </p>
 *
 * <h2>Performance rules</h2>
 * <p>
 * Rendering is allocation-free during {@link #draw()}:
 * </p>
 * <ul>
 *     <li>All arrays and native {@link FloatBuffer}s are allocated once in the constructor.</li>
 *     <li>Rebuild work writes into preallocated arrays/buffers only.</li>
 *     <li>Rotation trig is cached and only recomputed when rotation changes.</li>
 * </ul>
 *
 * @author Albert Beaupre
 * @since February 5th, 2026
 */
public class NinePatchTexture extends Texture {

    private static final int QUADS = 9; // Number of slices in a 3x3 nine-patch.
    private static final int VERTS_PER_QUAD = 4; // Vertex count per quad when using GL_QUADS.
    private static final int VERTS = QUADS * VERTS_PER_QUAD; // Total vertex count for all slices (36).
    private static final int FLOATS_PER_VERT = 2; // Float components per vertex for position/uv (x,y) or (u,v).
    private static final int FLOATS = VERTS * FLOATS_PER_VERT; // Total float count for a full buffer (72).

    private FloatBuffer nineVertexBuffer = BufferUtils.createFloatBuffer(VERTS * FLOATS_PER_VERT); // World-space x/y buffer (direct, reused).
    private FloatBuffer nineUvBuffer = BufferUtils.createFloatBuffer(VERTS * FLOATS_PER_VERT); // UV buffer (direct, reused).

    private final float[] nineLocalVertices = new float[FLOATS]; // Local-space x/y vertices for all slices (packed TL,TR,BR,BL order).
    private final float[] nineLocalUvs = new float[FLOATS]; // Local-space u/v values for all slices (packed TL,TR,BR,BL order).

    private final float[] sliceX = new float[QUADS]; // Slice local x positions for each of the 9 slices.
    private final float[] sliceY = new float[QUADS]; // Slice local y positions for each of the 9 slices.
    private final float[] sliceW = new float[QUADS]; // Slice widths for each of the 9 slices.
    private final float[] sliceH = new float[QUADS]; // Slice heights for each of the 9 slices.

    private final int[] src = new int[QUADS * 4]; // Source rectangles per slice packed as [l,t,r,b] for each slice.

    private int left; // Left border width in source pixels.
    private int right; // Right border width in source pixels.
    private int top; // Top border height in source pixels.
    private int bottom; // Bottom border height in source pixels.

    private boolean dirtyMesh = true; // True when local slice geometry/uvs must be rebuilt.
    private boolean dirtyWorld = true; // True when world-space vertices must be rebuilt.

    private float baseU0, baseU1, baseV0, baseV1; // Normalized base region bounds derived from Texture UVs.
    private boolean flipX, flipY; // Base flip flags derived from Texture UV ordering.

    private float cachedRotation = Float.NaN; // Cached rotation value used to avoid recomputing trig each draw.
    private float sinRot; // Cached sin for the current rotation (radians).
    private float cosRot = 1f; // Cached cos for the current rotation (radians).

    /**
     * Creates a nine-patch from an image file and border sizes.
     *
     * <p>
     * The border sizes are expressed in <b>source pixels</b>. Corners are preserved at that pixel size,
     * while the center/edges stretch to fit the requested output size (via {@link #setSize(float, float)}).
     * </p>
     *
     * <p>
     * This constructor loads {@link TextureData} and then delegates to {@link #NinePatchTexture(TextureData, int, int, int, int)}.
     * </p>
     *
     * @param path   image file path used by {@link TextureData#load(String)}
     * @param left   left border width in pixels
     * @param right  right border width in pixels
     * @param top    top border height in pixels
     * @param bottom bottom border height in pixels
     * @throws NullPointerException if path is null
     */
    public NinePatchTexture(String path, int left, int right, int top, int bottom) {
        this(TextureData.load(path), left, right, top, bottom);
    }

    /**
     * Creates a nine-patch from existing CPU-side pixel data and border sizes.
     *
     * <p>
     * The initial texture size is set to the source image size so the nine-patch renders as the original
     * image until you resize it. Any subsequent size/scale/region/flip changes will mark the mesh dirty.
     * </p>
     *
     * @param data   source texture data (CPU-side pixels) used by {@link Texture}
     * @param left   left border width in pixels
     * @param right  right border width in pixels
     * @param top    top border height in pixels
     * @param bottom bottom border height in pixels
     * @throws NullPointerException if data is null
     */
    public NinePatchTexture(TextureData data, int left, int right, int top, int bottom) {
        super(data);
        this.left = left;
        this.right = right;
        this.top = top;
        this.bottom = bottom;

        setSize(data.width(), data.height());
        markDirtyAll();
    }

    /**
     * Marks both the local mesh and the world-space vertex buffer as dirty.
     *
     * <p>
     * Use this when a change affects slice sizes/positions or UV mapping (e.g., borders, size, scale,
     * region, flip, or rotation origin).
     * </p>
     */
    private void markDirtyAll() {
        dirtyMesh = true;
        dirtyWorld = true;
    }

    /**
     * Marks only the world-space vertex buffer as dirty.
     *
     * <p>
     * Use this when the local mesh and UVs remain correct, but a transform changes how the mesh is placed
     * in world space (e.g., position or rotation).
     * </p>
     */
    private void markDirtyWorld() {
        dirtyWorld = true;
    }

    /**
     * Sets position and marks the world buffer dirty when the position actually changes.
     *
     * <p>
     * This avoids rebuilding slice geometry/uvs, because only the final world transform differs.
     * </p>
     *
     * @param x new x position (bottom-left world coordinate, matching your engine convention)
     * @param y new y position (bottom-left world coordinate, matching your engine convention)
     */
    @Override
    public void setPosition(float x, float y) {
        float ox = getX();
        float oy = getY();
        super.setPosition(x, y);
        if (ox != x || oy != y) markDirtyWorld();
    }

    /**
     * Sets rotation and marks the world buffer dirty when rotation changes.
     *
     * <p>
     * Rotation trig is cached, and will only be recomputed on the next draw when the rotation differs.
     * </p>
     *
     * @param degrees rotation in degrees (consistent with {@link Texture})
     */
    @Override
    public void setRotation(float degrees) {
        float prev = getRotation();
        super.setRotation(degrees);
        if (prev != degrees) markDirtyWorld();
    }

    /**
     * Sets rotation origin and marks the mesh/world dirty when the origin changes.
     *
     * <p>
     * Origin changes affect how local vertices are transformed (the pivot), so both local and world
     * computations must be considered dirty.
     * </p>
     *
     * @param ox origin x in local space
     * @param oy origin y in local space
     */
    @Override
    public void setRotationOrigin(float ox, float oy) {
        float px = getRotationOrigin().getX();
        float py = getRotationOrigin().getY();
        super.setRotationOrigin(ox, oy);
        if (px != ox || py != oy) markDirtyAll();
    }

    /**
     * Sets rotation origin to the local center and marks the mesh/world dirty.
     *
     * <p>
     * The center depends on size, so origin-center changes can require local rebuild depending on how
     * your {@link Texture} computes/updates origin.
     * </p>
     */
    @Override
    public void setRotationOriginCenter() {
        super.setRotationOriginCenter();
        markDirtyAll();
    }

    /**
     * Sets width and marks the mesh/world dirty if the width changes.
     *
     * <p>
     * Slice widths depend on the final output width, so local slice geometry must be rebuilt.
     * </p>
     *
     * @param w new width
     */
    @Override
    public void setWidth(float w) {
        float prev = getWidth();
        super.setWidth(w);
        if (prev != w) markDirtyAll();
    }

    /**
     * Sets height and marks the mesh/world dirty if the height changes.
     *
     * <p>
     * Slice heights depend on the final output height, so local slice geometry must be rebuilt.
     * </p>
     *
     * @param h new height
     */
    @Override
    public void setHeight(float h) {
        float prev = getHeight();
        super.setHeight(h);
        if (prev != h) markDirtyAll();
    }

    /**
     * Sets size and marks the mesh/world dirty when either dimension changes.
     *
     * <p>
     * Nine-patch stretching is driven by the output size, so any size change requires recalculating
     * slice sizes and rebuilding UV mapping.
     * </p>
     *
     * @param w new width
     * @param h new height
     */
    @Override
    public void setSize(float w, float h) {
        float pw = getWidth();
        float ph = getHeight();
        super.setSize(w, h);
        if (pw != w || ph != h) markDirtyAll();
    }

    /**
     * Sets scale and marks the mesh/world dirty when either scale changes.
     *
     * <p>
     * The local mesh is computed using (size * scale), so scale changes alter slice geometry.
     * </p>
     *
     * @param sx x scale
     * @param sy y scale
     */
    @Override
    public void setScale(float sx, float sy) {
        float psx = getScaleX();
        float psy = getScaleY();
        super.setScale(sx, sy);
        if (psx != sx || psy != sy) markDirtyAll();
    }

    /**
     * Sets the base UV region and marks the mesh/world dirty.
     *
     * <p>
     * The nine-patch UVs are mapped into the base region, so any region change requires rebuilding UVs.
     * </p>
     *
     * @param left   left UV
     * @param top    top UV
     * @param right  right UV
     * @param bottom bottom UV
     */
    @Override
    public void setRegion(float left, float top, float right, float bottom) {
        super.setRegion(left, top, right, bottom);
        markDirtyAll();
    }

    /**
     * Flips the base texture UVs horizontally and rebuilds the nine-patch UV mapping.
     *
     * <p>
     * Nine-patch UVs must be regenerated so each slice continues to sample the correct rectangle.
     * </p>
     */
    @Override
    public void flipX() {
        super.flipX();
        markDirtyAll();
    }

    /**
     * Flips the base texture UVs vertically and rebuilds the nine-patch UV mapping.
     *
     * <p>
     * Nine-patch UVs must be regenerated so each slice continues to sample the correct rectangle.
     * </p>
     */
    @Override
    public void flipY() {
        super.flipY();
        markDirtyAll();
    }

    /**
     * Sets horizontal flip state and rebuilds mesh/world only if the flip state changes.
     *
     * <p>
     * This method compares the derived base flip state (from the UV buffer ordering) before and after
     * applying the change so we only rebuild when necessary.
     * </p>
     *
     * @param flip true to flip horizontally, false to unflip
     */
    @Override
    public void setFlipX(boolean flip) {
        boolean before = isFlippedX();
        super.setFlipX(flip);
        if (before != flip) markDirtyAll();
    }

    /**
     * Sets vertical flip state and rebuilds mesh/world only if the flip state changes.
     *
     * <p>
     * This method compares the derived base flip state (from the UV buffer ordering) before and after
     * applying the change so we only rebuild when necessary.
     * </p>
     *
     * @param flip true to flip vertically, false to unflip
     */
    @Override
    public void setFlipY(boolean flip) {
        boolean before = isFlippedY();
        super.setFlipY(flip);
        if (before != flip) markDirtyAll();
    }

    /**
     * Detects whether the current base UV mapping is flipped horizontally.
     *
     * <p>
     * This is derived from {@link #getUVBuffer()} ordering and does not allocate or store extra state.
     * For the default TL/TR/BR/BL UV layout, a flip is indicated when the "left" U exceeds the "right" U.
     * </p>
     *
     * @return true if the underlying base region is horizontally flipped
     */
    private boolean isFlippedX() {
        FloatBuffer uv = getUVBuffer();
        return uv.get(0) > uv.get(2);
    }

    /**
     * Detects whether the current base UV mapping is flipped vertically.
     *
     * <p>
     * This is derived from {@link #getUVBuffer()} ordering and does not allocate or store extra state.
     * For the default TL/TR/BR/BL UV layout, a flip is indicated when the "top" V exceeds the "bottom" V.
     * </p>
     *
     * @return true if the underlying base region is vertically flipped
     */
    private boolean isFlippedY() {
        FloatBuffer uv = getUVBuffer();
        return uv.get(1) > uv.get(5);
    }

    /**
     * Caches normalized base region bounds and flip flags derived from the base {@link Texture} UV buffer.
     *
     * <p>
     * The base texture may already have a region applied and may be flipped. This method:
     * </p>
     * <ul>
     *     <li>Extracts the logical region extents (min/max U and V) into {@code baseU0/baseU1/baseV0/baseV1}.</li>
     *     <li>Records whether the base mapping is flipped so nine-patch slice UVs can match it.</li>
     * </ul>
     */
    private void cacheBaseRegionAndFlip() {
        FloatBuffer baseUv = getUVBuffer();

        float leftU = baseUv.get(0);
        float topV = baseUv.get(1);
        float rightU = baseUv.get(2);
        float bottomV = baseUv.get(5);

        baseU0 = (leftU < rightU) ? leftU : rightU;
        baseU1 = (leftU < rightU) ? rightU : leftU;
        baseV0 = (topV < bottomV) ? topV : bottomV;
        baseV1 = (topV < bottomV) ? bottomV : topV;

        flipX = (leftU > rightU);
        flipY = (topV > bottomV);
    }

    /**
     * Rebuilds the local mesh (slice rectangles + packed local vertices) and the UV mapping for all slices.
     *
     * <p>
     * This method is called only when {@code dirtyMesh} is true. It computes:
     * </p>
     * <ul>
     *     <li>Slice destination rectangles based on output size/scale and border pixel sizes.</li>
     *     <li>Slice source rectangles in source pixel space.</li>
     *     <li>Per-slice UVs mapped into the base region and adjusted for base flips.</li>
     * </ul>
     *
     * <p>
     * All writes go into preallocated arrays and the preallocated {@link FloatBuffer} for UVs.
     * No objects are allocated.
     * </p>
     */
    private void rebuildLocalMesh() {
        final TextureData data = getData();
        final int srcW = data.width();
        final int srcH = data.height();

        cacheBaseRegionAndFlip();

        final float totalW = getWidth() * getScaleX();
        final float totalH = getHeight() * getScaleY();

        final float stretchW = Math.max(0f, totalW - left - right);
        final float stretchH = Math.max(0f, totalH - top - bottom);

        final float x0 = 0f;
        final float x1 = left;
        final float x2 = left + stretchW;

        final float y0 = 0f;
        final float y1 = top;
        final float y2 = top + stretchH;

        sliceX[0] = x0;
        sliceY[0] = y0;
        sliceX[1] = x1;
        sliceY[1] = y0;
        sliceX[2] = x2;
        sliceY[2] = y0;

        sliceX[3] = x0;
        sliceY[3] = y1;
        sliceX[4] = x1;
        sliceY[4] = y1;
        sliceX[5] = x2;
        sliceY[5] = y1;

        sliceX[6] = x0;
        sliceY[6] = y2;
        sliceX[7] = x1;
        sliceY[7] = y2;
        sliceX[8] = x2;
        sliceY[8] = y2;

        sliceW[0] = left;
        sliceH[0] = top;
        sliceW[1] = stretchW;
        sliceH[1] = top;
        sliceW[2] = right;
        sliceH[2] = top;

        sliceW[3] = left;
        sliceH[3] = stretchH;
        sliceW[4] = stretchW;
        sliceH[4] = stretchH;
        sliceW[5] = right;
        sliceH[5] = stretchH;

        sliceW[6] = left;
        sliceH[6] = bottom;
        sliceW[7] = stretchW;
        sliceH[7] = bottom;
        sliceW[8] = right;
        sliceH[8] = bottom;

        setSrc(0, 0, 0, left, top);
        setSrc(1, left, 0, srcW - right, top);
        setSrc(2, srcW - right, 0, srcW, top);

        setSrc(3, 0, top, left, srcH - bottom);
        setSrc(4, left, top, srcW - right, srcH - bottom);
        setSrc(5, srcW - right, top, srcW, srcH - bottom);

        setSrc(6, 0, srcH - bottom, left, srcH);
        setSrc(7, left, srcH - bottom, srcW - right, srcH);
        setSrc(8, srcW - right, srcH - bottom, srcW, srcH);

        int vi = 0;

        for (int i = 0; i < QUADS; i++) {
            final float px = sliceX[i];
            final float py = sliceY[i];
            final float w = sliceW[i];
            final float h = sliceH[i];

            final float xR = px + w;
            final float yB = py + h;

            nineLocalVertices[vi] = px;
            nineLocalVertices[vi + 1] = py;
            nineLocalVertices[vi + 2] = xR;
            nineLocalVertices[vi + 3] = py;
            nineLocalVertices[vi + 4] = xR;
            nineLocalVertices[vi + 5] = yB;
            nineLocalVertices[vi + 6] = px;
            nineLocalVertices[vi + 7] = yB;

            final int si = i * 4;
            final float u0 = src[si] / (float) srcW;
            final float v0 = src[si + 1] / (float) srcH;
            final float u1 = src[si + 2] / (float) srcW;
            final float v1 = src[si + 3] / (float) srcH;

            float ru0 = baseU0 + (baseU1 - baseU0) * u0;
            float ru1 = baseU0 + (baseU1 - baseU0) * u1;
            float rv0 = baseV0 + (baseV1 - baseV0) * v0;
            float rv1 = baseV0 + (baseV1 - baseV0) * v1;

            if (flipX) {
                float t = ru0;
                ru0 = ru1;
                ru1 = t;
            }
            if (flipY) {
                float t = rv0;
                rv0 = rv1;
                rv1 = t;
            }

            nineLocalUvs[vi] = ru0;
            nineLocalUvs[vi + 1] = rv0;
            nineLocalUvs[vi + 2] = ru1;
            nineLocalUvs[vi + 3] = rv0;
            nineLocalUvs[vi + 4] = ru1;
            nineLocalUvs[vi + 5] = rv1;
            nineLocalUvs[vi + 6] = ru0;
            nineLocalUvs[vi + 7] = rv1;

            vi += 8;
        }

        nineUvBuffer.position(0);
        nineUvBuffer.put(nineLocalUvs, 0, FLOATS);
        nineUvBuffer.position(0);

        dirtyMesh = false;
        dirtyWorld = true;
    }

    /**
     * Writes a source rectangle for a specific slice into the packed {@code src} array.
     *
     * <p>
     * Rectangles are stored as integer pixel coordinates in the full source texture space:
     * {@code [left, top, right, bottom]}.
     * </p>
     *
     * @param quad slice index 0..8
     * @param l    left pixel (inclusive)
     * @param t    top pixel (inclusive)
     * @param r    right pixel (exclusive)
     * @param b    bottom pixel (exclusive)
     */
    private void setSrc(int quad, int l, int t, int r, int b) {
        final int i = quad * 4;
        src[i] = l;
        src[i + 1] = t;
        src[i + 2] = r;
        src[i + 3] = b;
    }

    /**
     * Updates cached {@code sin/cos} for the current rotation if the rotation value changed.
     *
     * <p>
     * Trig calls are relatively expensive. This method ensures we only compute trig when rotation
     * changes, not every frame.
     * </p>
     */
    private void cacheRotationTrigIfNeeded() {
        final float rot = getRotation();
        if (rot == cachedRotation) return;

        cachedRotation = rot;

        final float rad = (float) Math.toRadians(-rot);
        sinRot = (float) Math.sin(rad);
        cosRot = (float) Math.cos(rad);
    }

    /**
     * Rebuilds the world-space vertex buffer by transforming the packed local vertices.
     *
     * <p>
     * This applies:
     * </p>
     * <ul>
     *     <li>Translation: {@code (getX(), getY())}</li>
     *     <li>Pivot: {@code getRotationOrigin()}</li>
     *     <li>Rotation: {@code getRotation()} using cached trig</li>
     * </ul>
     *
     * <p>
     * The world-space positions are written into {@link #nineVertexBuffer} using absolute puts so the
     * buffer can be reused without changing capacity or allocating.
     * </p>
     */
    private void updateWorldBuffers() {
        cacheRotationTrigIfNeeded();

        final float ox = getRotationOrigin().getX();
        final float oy = getRotationOrigin().getY();

        final float px = getX() + ox;
        final float py = getY() + oy;

        nineVertexBuffer.position(0);

        for (int v = 0; v < VERTS; v++) {
            final int idx = v * 2;

            final float lx = nineLocalVertices[idx] - ox;
            final float ly = nineLocalVertices[idx + 1] - oy;

            final float rx = lx * cosRot - ly * sinRot;
            final float ry = lx * sinRot + ly * cosRot;

            nineVertexBuffer.put(idx, rx + px);
            nineVertexBuffer.put(idx + 1, ry + py);
        }

        nineVertexBuffer.position(0);
        dirtyWorld = false;
    }

    /**
     * Draws the nine-patch using one OpenGL draw call.
     *
     * <p>
     * This method:
     * </p>
     * <ul>
     *     <li>Rebuilds the local mesh/UVs if needed.</li>
     *     <li>Rebuilds world-space vertex positions if needed.</li>
     *     <li>Binds the underlying texture and issues {@code glDrawArrays(GL_QUADS, 0, 36)}.</li>
     * </ul>
     *
     * <p>
     * This matches {@link Texture}'s fixed-function usage ({@code glVertexPointer/glTexCoordPointer}).
     * It assumes required client states are enabled by your renderer (as with your legacy pipeline).
     * </p>
     */
    @Override
    public void draw() {
        if (dirtyMesh) rebuildLocalMesh();
        if (dirtyWorld) updateWorldBuffers();

        glColor4f(getColor().r(), getColor().g(), getColor().b(), getColor().a());
        glBindTexture(GL_TEXTURE_2D, getTextureID());

        glVertexPointer(2, GL_FLOAT, 0, nineVertexBuffer);
        glTexCoordPointer(2, GL_FLOAT, 0, nineUvBuffer);

        glDrawArrays(GL_QUADS, 0, VERTS);
    }

    /**
     * Returns the configured left border size in source pixels.
     *
     * <p>
     * The left border is preserved (not stretched) and determines the width of the left column slices.
     * </p>
     *
     * @return left border width in pixels
     */
    public int getLeft() {
        return left;
    }

    /**
     * Sets the left border size in source pixels and marks the mesh dirty if it changes.
     *
     * <p>
     * Changing border sizes affects slice layout and UV rectangles, so the local mesh and world buffer
     * must be rebuilt.
     * </p>
     *
     * @param left new left border width in pixels
     */
    public void setLeft(int left) {
        if (this.left == left) return;
        this.left = left;
        markDirtyAll();
    }

    /**
     * Returns the configured right border size in source pixels.
     *
     * <p>
     * The right border is preserved (not stretched) and determines the width of the right column slices.
     * </p>
     *
     * @return right border width in pixels
     */
    public int getRight() {
        return right;
    }

    /**
     * Sets the right border size in source pixels and marks the mesh dirty if it changes.
     *
     * <p>
     * Changing border sizes affects slice layout and UV rectangles, so the local mesh and world buffer
     * must be rebuilt.
     * </p>
     *
     * @param right new right border width in pixels
     */
    public void setRight(int right) {
        if (this.right == right) return;
        this.right = right;
        markDirtyAll();
    }

    /**
     * Returns the configured top border size in source pixels.
     *
     * <p>
     * The top border is preserved (not stretched) and determines the height of the top row slices.
     * </p>
     *
     * @return top border height in pixels
     */
    public int getTop() {
        return top;
    }

    /**
     * Sets the top border size in source pixels and marks the mesh dirty if it changes.
     *
     * <p>
     * Changing border sizes affects slice layout and UV rectangles, so the local mesh and world buffer
     * must be rebuilt.
     * </p>
     *
     * @param top new top border height in pixels
     */
    public void setTop(int top) {
        if (this.top == top) return;
        this.top = top;
        markDirtyAll();
    }

    /**
     * Returns the configured bottom border size in source pixels.
     *
     * <p>
     * The bottom border is preserved (not stretched) and determines the height of the bottom row slices.
     * </p>
     *
     * @return bottom border height in pixels
     */
    public int getBottom() {
        return bottom;
    }

    /**
     * Sets the bottom border size in source pixels and marks the mesh dirty if it changes.
     *
     * <p>
     * Changing border sizes affects slice layout and UV rectangles, so the local mesh and world buffer
     * must be rebuilt.
     * </p>
     *
     * @param bottom new bottom border height in pixels
     */
    public void setBottom(int bottom) {
        if (this.bottom == bottom) return;
        this.bottom = bottom;
        markDirtyAll();
    }

    /**
     * Disposes the GPU texture and releases native buffer references owned by this instance.
     *
     * <p>
     * This calls {@link Texture#dispose()} first. The nine-patch buffers are direct buffers created once
     * and held for the lifetime of the object. Setting them to null releases Java references so the JVM
     * can reclaim them when appropriate.
     * </p>
     */
    @Override
    public void dispose() {
        super.dispose();
        nineVertexBuffer = null;
        nineUvBuffer = null;
    }
}