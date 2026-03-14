package valthorne.graphics.texture;

import org.lwjgl.BufferUtils;
import valthorne.graphics.Color;
import valthorne.io.pool.Poolable;
import valthorne.math.Vector2f;
import valthorne.math.geometry.Rectangle;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL11.*;

/**
 * <p>
 * {@code NinePatchTexture} represents a drawable nine-patch texture built around a
 * backing {@link Texture} and a {@link TextureRegion}. It divides a texture into a
 * 3x3 grid made of fixed-size corners, stretchable edges, and a stretchable center.
 * This allows UI panels, windows, buttons, and other scalable elements to grow or
 * shrink without distorting their borders.
 * </p>
 *
 * <p>
 * Internally, this class precomputes a local nine-patch mesh and a matching UV layout.
 * When geometry-related properties change, such as size, scale, region, flip state,
 * or slice thickness, the local mesh is marked dirty and rebuilt on demand. When only
 * world-space properties change, such as position or rotation, the world buffer is
 * updated without rebuilding the local mesh. This split keeps rendering efficient while
 * still supporting dynamic transforms.
 * </p>
 *
 * <p>
 * The nine-patch is composed of nine quads:
 * </p>
 *
 * <ul>
 *     <li>bottom-left, bottom-center, bottom-right</li>
 *     <li>middle-left, middle-center, middle-right</li>
 *     <li>top-left, top-center, top-right</li>
 * </ul>
 *
 * <p>
 * Each quad stores local vertex positions and UV coordinates. These are later converted
 * into world-space positions using the current position, origin, scale, and rotation.
 * Because of that, the class supports:
 * </p>
 *
 * <ul>
 *     <li>custom source regions</li>
 *     <li>custom destination position and size</li>
 *     <li>scaling</li>
 *     <li>rotation around a configurable origin</li>
 *     <li>horizontal and vertical flipping</li>
 *     <li>runtime color tinting</li>
 *     <li>pool reset behavior through {@link Poolable}</li>
 * </ul>
 *
 * <p>
 * This class performs immediate-mode style quad submission using client-side array
 * pointers and {@code glDrawArrays(GL_QUADS, ...)}. That means it is useful for
 * standalone drawing and is also compatible with higher-level systems that want to
 * inspect its nine-patch properties and feed them into a batch renderer such as
 * {@link TextureBatch}.
 * </p>
 *
 * <h2>Example Usage</h2>
 *
 * <pre>{@code
 * NinePatchTexture panel = new NinePatchTexture("panel.png", 8, 8, 8, 8);
 * panel.setPosition(100, 80);
 * panel.setSize(320, 180);
 * panel.setRotationOriginCenter();
 * panel.setRotation(5f);
 * panel.setColor(new Color(1f, 1f, 1f, 0.95f));
 *
 * panel.draw();
 *
 * panel.setFlip(true, false);
 * panel.setScale(1.25f, 1.25f);
 * panel.draw();
 *
 * panel.reset();
 * panel.dispose();
 * }</pre>
 *
 * <p>
 * The example above demonstrates the full lifecycle of the class: construction,
 * resizing, transforming, tinting, drawing, resetting, and disposal.
 * </p>
 *
 * @author Albert Beaupre
 * @since February 5th, 2026
 */
public class NinePatchTexture implements Poolable {

    /**
     * The number of quads used to represent the nine-patch.
     */
    private static final int QUADS = 9;

    /**
     * The number of vertices contained in each quad.
     */
    private static final int VERTS_PER_QUAD = 4;

    /**
     * The total number of vertices across all quads.
     */
    private static final int VERTS = QUADS * VERTS_PER_QUAD;

    /**
     * The number of float components per vertex.
     */
    private static final int FLOATS_PER_VERT = 2;

    /**
     * The total number of float components required for the full nine-patch mesh.
     */
    private static final int FLOATS = VERTS * FLOATS_PER_VERT;

    private final Texture texture; // Backing texture used by the nine-patch
    private final TextureRegion region; // Source region inside the backing texture
    private FloatBuffer nineVertexBuffer = BufferUtils.createFloatBuffer(FLOATS); // World-space vertex buffer used for rendering
    private FloatBuffer nineUvBuffer = BufferUtils.createFloatBuffer(FLOATS); // UV buffer used for rendering
    private final float[] nineLocalVertices = new float[FLOATS]; // Cached local-space vertex positions for all nine quads
    private final float[] nineLocalUvs = new float[FLOATS]; // Cached local-space UV coordinates for all nine quads
    private final float[] sliceX = new float[QUADS]; // Cached local X positions for each patch slice
    private final float[] sliceY = new float[QUADS]; // Cached local Y positions for each patch slice
    private final float[] sliceW = new float[QUADS]; // Cached widths for each patch slice
    private final float[] sliceH = new float[QUADS]; // Cached heights for each patch slice
    private final int[] src = new int[QUADS * 4]; // Cached source pixel rectangles for each patch slice
    private final Rectangle bounds; // Destination bounds of the nine-patch in world space
    private Vector2f origin = new Vector2f(0f, 0f); // Rotation origin used when transforming the mesh
    private Color color = new Color(1f, 1f, 1f, 1f); // Current tint color applied during drawing

    private int left; // Left border thickness in pixels
    private int right; // Right border thickness in pixels
    private int top; // Top border thickness in pixels
    private int bottom; // Bottom border thickness in pixels

    private boolean flippedX; // Whether the nine-patch is horizontally flipped
    private boolean flippedY; // Whether the nine-patch is vertically flipped
    private boolean dirtyMesh = true; // Whether the local mesh and UVs need to be rebuilt
    private boolean dirtyWorld = true; // Whether world-space transformed vertices need to be updated

    private float scaleX = 1f; // Horizontal scale factor
    private float scaleY = 1f; // Vertical scale factor
    private float rotation; // Rotation angle in degrees
    private float baseU0; // Cached normalized minimum U value of the active region
    private float baseU1; // Cached normalized maximum U value of the active region
    private float baseV0; // Cached normalized minimum V value of the active region
    private float baseV1; // Cached normalized maximum V value of the active region
    private boolean regionFlipX; // Whether the underlying region is naturally flipped on the X axis
    private boolean regionFlipY; // Whether the underlying region is naturally flipped on the Y axis
    private float cachedRotation = Float.NaN; // Last rotation value used to cache sine and cosine
    private float sinRot; // Cached sine of the current rotation
    private float cosRot = 1f; // Cached cosine of the current rotation

    /**
     * <p>
     * Creates a new {@code NinePatchTexture} from a texture path.
     * </p>
     *
     * <p>
     * This constructor creates a new {@link Texture} from the supplied path and then
     * delegates to the texture-based constructor.
     * </p>
     *
     * @param path   the texture path to load
     * @param left   the left border size in pixels
     * @param right  the right border size in pixels
     * @param top    the top border size in pixels
     * @param bottom the bottom border size in pixels
     */
    public NinePatchTexture(String path, int left, int right, int top, int bottom) {
        this(new Texture(path), left, right, top, bottom);
    }

    /**
     * <p>
     * Creates a new {@code NinePatchTexture} from an existing {@link TextureData} instance.
     * </p>
     *
     * <p>
     * This constructor creates a new {@link Texture} from the supplied texture data and
     * then delegates to the texture-based constructor.
     * </p>
     *
     * @param data   the texture data used to construct the backing texture
     * @param left   the left border size in pixels
     * @param right  the right border size in pixels
     * @param top    the top border size in pixels
     * @param bottom the bottom border size in pixels
     */
    public NinePatchTexture(TextureData data, int left, int right, int top, int bottom) {
        this(new Texture(data), left, right, top, bottom);
    }

    /**
     * <p>
     * Creates a new {@code NinePatchTexture} from an existing {@link Texture}.
     * </p>
     *
     * <p>
     * The full texture is initially used as the source region. The object starts with
     * bounds matching the texture's native size, a zero origin, no flipping, no rotation,
     * unit scale, and a white tint. The mesh and world caches are marked dirty so the
     * buffers are generated lazily on first use.
     * </p>
     *
     * @param texture the backing texture
     * @param left    the left border size in pixels
     * @param right   the right border size in pixels
     * @param top     the top border size in pixels
     * @param bottom  the bottom border size in pixels
     * @throws NullPointerException if {@code texture} is {@code null}
     */
    public NinePatchTexture(Texture texture, int left, int right, int top, int bottom) {
        if (texture == null) throw new NullPointerException("Texture cannot be null");

        this.texture = texture;
        this.region = new TextureRegion(texture);
        this.left = left;
        this.right = right;
        this.top = top;
        this.bottom = bottom;
        this.bounds = new Rectangle(0f, 0f, texture.getWidth(), texture.getHeight());
        markDirtyAll();
    }

    /**
     * <p>
     * Marks both mesh data and world-space vertex data as dirty.
     * </p>
     *
     * <p>
     * This method should be used when a property change affects slice layout, UVs,
     * local geometry, or anything that indirectly requires the world-space buffers
     * to be regenerated as well.
     * </p>
     */
    private void markDirtyAll() {
        dirtyMesh = true;
        dirtyWorld = true;
    }

    /**
     * <p>
     * Marks only the world-space transformed vertex data as dirty.
     * </p>
     *
     * <p>
     * This method is used for changes that do not alter the local mesh itself,
     * such as position or rotation updates.
     * </p>
     */
    private void markDirtyWorld() {
        dirtyWorld = true;
    }

    /**
     * <p>
     * Returns the backing texture used by this nine-patch.
     * </p>
     *
     * @return the backing texture
     */
    public Texture getTexture() {
        return texture;
    }

    /**
     * <p>
     * Returns the backing texture data.
     * </p>
     *
     * @return the texture data, or whatever is returned by the backing texture
     */
    public TextureData getData() {
        return texture.getData();
    }

    /**
     * <p>
     * Returns the source region used by this nine-patch.
     * </p>
     *
     * @return the source region
     */
    public TextureRegion getRegion() {
        return region;
    }

    /**
     * <p>
     * Updates the active source region and marks all cached mesh data dirty.
     * </p>
     *
     * <p>
     * Because the UV layout of each slice depends on the region, changing the region
     * requires the nine-patch mesh and UVs to be rebuilt.
     * </p>
     *
     * @param regionX      the source region X position in pixels
     * @param regionY      the source region Y position in pixels
     * @param regionWidth  the source region width in pixels
     * @param regionHeight the source region height in pixels
     */
    public void setRegion(float regionX, float regionY, float regionWidth, float regionHeight) {
        region.setRegion(regionX, regionY, regionWidth, regionHeight);
        markDirtyAll();
    }

    /**
     * <p>
     * Returns the source region X position.
     * </p>
     *
     * @return the source region X position
     */
    public float getRegionX() {
        return region.getRegionX();
    }

    /**
     * <p>
     * Returns the source region Y position.
     * </p>
     *
     * @return the source region Y position
     */
    public float getRegionY() {
        return region.getRegionY();
    }

    /**
     * <p>
     * Returns the source region width.
     * </p>
     *
     * @return the source region width
     */
    public float getRegionWidth() {
        return region.getRegionWidth();
    }

    /**
     * <p>
     * Returns the source region height.
     * </p>
     *
     * @return the source region height
     */
    public float getRegionHeight() {
        return region.getRegionHeight();
    }

    /**
     * <p>
     * Sets the world position of the nine-patch.
     * </p>
     *
     * <p>
     * If the new coordinates match the current position, the method returns early.
     * Otherwise, the bounds are updated and only the world buffer is marked dirty.
     * </p>
     *
     * @param x the new X position
     * @param y the new Y position
     */
    public void setPosition(float x, float y) {
        if (bounds.getX() == x && bounds.getY() == y) return;

        bounds.setX(x);
        bounds.setY(y);
        markDirtyWorld();
    }

    /**
     * <p>
     * Returns the current world X position.
     * </p>
     *
     * @return the X position
     */
    public float getX() {
        return bounds.getX();
    }

    /**
     * <p>
     * Returns the current world Y position.
     * </p>
     *
     * @return the Y position
     */
    public float getY() {
        return bounds.getY();
    }

    /**
     * <p>
     * Returns the current destination width.
     * </p>
     *
     * @return the destination width
     */
    public float getWidth() {
        return bounds.getWidth();
    }

    /**
     * <p>
     * Sets the destination width and marks the mesh dirty if the value changes.
     * </p>
     *
     * @param width the new width
     */
    public void setWidth(float width) {
        if (bounds.getWidth() == width) return;

        bounds.setWidth(width);
        markDirtyAll();
    }

    /**
     * <p>
     * Returns the current destination height.
     * </p>
     *
     * @return the destination height
     */
    public float getHeight() {
        return bounds.getHeight();
    }

    /**
     * <p>
     * Sets the destination height and marks the mesh dirty if the value changes.
     * </p>
     *
     * @param height the new height
     */
    public void setHeight(float height) {
        if (bounds.getHeight() == height) return;

        bounds.setHeight(height);
        markDirtyAll();
    }

    /**
     * <p>
     * Sets the destination size and marks the mesh dirty if either dimension changes.
     * </p>
     *
     * @param width  the new width
     * @param height the new height
     */
    public void setSize(float width, float height) {
        if (bounds.getWidth() == width && bounds.getHeight() == height) return;

        bounds.setWidth(width);
        bounds.setHeight(height);
        markDirtyAll();
    }

    /**
     * <p>
     * Sets the horizontal and vertical scale of the nine-patch.
     * </p>
     *
     * <p>
     * The rotation origin is adjusted proportionally so that an existing origin stays
     * visually aligned with the scaled geometry. After the update, the local mesh and
     * world buffers are marked dirty.
     * </p>
     *
     * @param sx the new horizontal scale
     * @param sy the new vertical scale
     */
    public void setScale(float sx, float sy) {
        float oldScaleX = this.scaleX;
        float oldScaleY = this.scaleY;

        if (oldScaleX != 0f) origin.setX(origin.getX() * (sx / oldScaleX));
        if (oldScaleY != 0f) origin.setY(origin.getY() * (sy / oldScaleY));

        this.scaleX = sx;
        this.scaleY = sy;
        markDirtyAll();
    }

    /**
     * <p>
     * Returns the current horizontal scale.
     * </p>
     *
     * @return the horizontal scale
     */
    public float getScaleX() {
        return scaleX;
    }

    /**
     * <p>
     * Returns the current vertical scale.
     * </p>
     *
     * @return the vertical scale
     */
    public float getScaleY() {
        return scaleY;
    }

    /**
     * <p>
     * Returns the current rotation in degrees.
     * </p>
     *
     * @return the rotation angle in degrees
     */
    public float getRotation() {
        return rotation;
    }

    /**
     * <p>
     * Sets the rotation of the nine-patch in degrees.
     * </p>
     *
     * <p>
     * If the value is unchanged, the method returns immediately. Otherwise only the
     * world-space buffers need to be recomputed.
     * </p>
     *
     * @param degrees the new rotation angle in degrees
     */
    public void setRotation(float degrees) {
        if (this.rotation == degrees) return;

        this.rotation = degrees;
        markDirtyWorld();
    }

    /**
     * <p>
     * Sets the rotation origin used when transforming the local mesh into world space.
     * </p>
     *
     * @param ox the origin X
     * @param oy the origin Y
     */
    public void setRotationOrigin(float ox, float oy) {
        if (origin.getX() == ox && origin.getY() == oy) return;

        origin.set(ox, oy);
        markDirtyAll();
    }

    /**
     * <p>
     * Sets the rotation origin to the visual center of the scaled destination area.
     * </p>
     *
     * <p>
     * Because the origin is stored in local scaled space, this uses the bounds size
     * multiplied by the current scale.
     * </p>
     */
    public void setRotationOriginCenter() {
        origin.set(bounds.getWidth() * scaleX / 2f, bounds.getHeight() * scaleY / 2f);
        markDirtyAll();
    }

    /**
     * <p>
     * Returns the current rotation origin vector.
     * </p>
     *
     * @return the rotation origin
     */
    public Vector2f getRotationOrigin() {
        return origin;
    }

    /**
     * <p>
     * Sets whether the nine-patch should be flipped horizontally.
     * </p>
     *
     * @param flip {@code true} to flip horizontally
     */
    public void setFlipX(boolean flip) {
        if (this.flippedX == flip) return;

        this.flippedX = flip;
        markDirtyAll();
    }

    /**
     * <p>
     * Sets whether the nine-patch should be flipped vertically.
     * </p>
     *
     * @param flip {@code true} to flip vertically
     */
    public void setFlipY(boolean flip) {
        if (this.flippedY == flip) return;

        this.flippedY = flip;
        markDirtyAll();
    }

    /**
     * <p>
     * Sets both horizontal and vertical flip state at once.
     * </p>
     *
     * @param flipX {@code true} to flip horizontally
     * @param flipY {@code true} to flip vertically
     */
    public void setFlip(boolean flipX, boolean flipY) {
        if (this.flippedX == flipX && this.flippedY == flipY) return;

        this.flippedX = flipX;
        this.flippedY = flipY;
        markDirtyAll();
    }

    /**
     * <p>
     * Returns whether the nine-patch is currently flipped horizontally.
     * </p>
     *
     * @return {@code true} if flipped horizontally
     */
    public boolean isFlippedX() {
        return flippedX;
    }

    /**
     * <p>
     * Returns whether the nine-patch is currently flipped vertically.
     * </p>
     *
     * @return {@code true} if flipped vertically
     */
    public boolean isFlippedY() {
        return flippedY;
    }

    /**
     * <p>
     * Returns the current tint color.
     * </p>
     *
     * @return the tint color
     */
    public Color getColor() {
        return color;
    }

    /**
     * <p>
     * Sets the tint color used during rendering.
     * </p>
     *
     * <p>
     * The provided reference is stored directly, so future changes to that color
     * object will affect this nine-patch as well.
     * </p>
     *
     * @param color the new tint color
     * @throws NullPointerException if {@code color} is {@code null}
     */
    public void setColor(Color color) {
        if (color == null) throw new NullPointerException("Color cannot be null");

        this.color = color;
    }

    /**
     * <p>
     * Returns the destination bounds rectangle.
     * </p>
     *
     * @return the bounds rectangle
     */
    public Rectangle getBounds() {
        return bounds;
    }

    /**
     * <p>
     * Caches the normalized base region bounds and whether the region itself is flipped.
     * </p>
     *
     * <p>
     * This method extracts the raw region UVs, normalizes them into ascending ranges,
     * and records whether the source region is inherently reversed on either axis.
     * Those flags are later combined with the explicit flip state of the nine-patch.
     * </p>
     */
    private void cacheBaseRegionAndFlip() {
        float leftU = region.getU();
        float topV = region.getV();
        float rightU = region.getU2();
        float bottomV = region.getV2();

        baseU0 = Math.min(leftU, rightU);
        baseU1 = Math.max(leftU, rightU);
        baseV0 = Math.min(topV, bottomV);
        baseV1 = Math.max(topV, bottomV);

        regionFlipX = leftU > rightU;
        regionFlipY = topV > bottomV;
    }

    /**
     * <p>
     * Rebuilds the local nine-patch mesh and local UV layout.
     * </p>
     *
     * <p>
     * This method computes the final dimensions of each of the nine slices, determines
     * their corresponding source rectangles inside the texture, writes all local-space
     * vertex positions, and writes all local UV coordinates. After the UV data is
     * generated, it is copied into the reusable UV buffer. The world buffer is then
     * marked dirty because any local mesh rebuild invalidates previously transformed
     * world-space vertices.
     * </p>
     */
    private void rebuildLocalMesh() {
        TextureData data = texture.getData();
        int srcW = data.width();
        int srcH = data.height();

        cacheBaseRegionAndFlip();

        float totalW = bounds.getWidth() * scaleX;
        float totalH = bounds.getHeight() * scaleY;

        float stretchW = Math.max(0f, totalW - left - right);
        float stretchH = Math.max(0f, totalH - top - bottom);

        float x0 = 0f;
        float x1 = left;
        float x2 = left + stretchW;

        float y0 = 0f;
        float y1 = bottom;
        float y2 = bottom + stretchH;

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
        sliceH[0] = bottom;
        sliceW[1] = stretchW;
        sliceH[1] = bottom;
        sliceW[2] = right;
        sliceH[2] = bottom;

        sliceW[3] = left;
        sliceH[3] = stretchH;
        sliceW[4] = stretchW;
        sliceH[4] = stretchH;
        sliceW[5] = right;
        sliceH[5] = stretchH;

        sliceW[6] = left;
        sliceH[6] = top;
        sliceW[7] = stretchW;
        sliceH[7] = top;
        sliceW[8] = right;
        sliceH[8] = top;

        setSrc(0, 0, 0, left, bottom);
        setSrc(1, left, 0, srcW - right, bottom);
        setSrc(2, srcW - right, 0, srcW, bottom);

        setSrc(3, 0, bottom, left, srcH - top);
        setSrc(4, left, bottom, srcW - right, srcH - top);
        setSrc(5, srcW - right, bottom, srcW, srcH - top);

        setSrc(6, 0, srcH - top, left, srcH);
        setSrc(7, left, srcH - top, srcW - right, srcH);
        setSrc(8, srcW - right, srcH - top, srcW, srcH);

        int vi = 0;

        for (int i = 0; i < QUADS; i++) {
            float px = sliceX[i];
            float py = sliceY[i];
            float w = sliceW[i];
            float h = sliceH[i];

            float xR = px + w;
            float yT = py + h;

            nineLocalVertices[vi] = px;
            nineLocalVertices[vi + 1] = py;
            nineLocalVertices[vi + 2] = xR;
            nineLocalVertices[vi + 3] = py;
            nineLocalVertices[vi + 4] = xR;
            nineLocalVertices[vi + 5] = yT;
            nineLocalVertices[vi + 6] = px;
            nineLocalVertices[vi + 7] = yT;

            int si = i * 4;
            float u0 = src[si] / (float) srcW;
            float v0 = src[si + 1] / (float) srcH;
            float u1 = src[si + 2] / (float) srcW;
            float v1 = src[si + 3] / (float) srcH;

            float ru0 = baseU0 + (baseU1 - baseU0) * u0;
            float ru1 = baseU0 + (baseU1 - baseU0) * u1;
            float rv0 = baseV0 + (baseV1 - baseV0) * v0;
            float rv1 = baseV0 + (baseV1 - baseV0) * v1;

            if (regionFlipX ^ flippedX) {
                float t = ru0;
                ru0 = ru1;
                ru1 = t;
            }

            if (regionFlipY ^ flippedY) {
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
     * <p>
     * Stores the source rectangle for a given patch slot.
     * </p>
     *
     * <p>
     * The rectangle is stored as left, bottom, right, and top pixel coordinates inside
     * the shared {@code src} array.
     * </p>
     *
     * @param quad the patch index
     * @param l    the source left coordinate
     * @param b    the source bottom coordinate
     * @param r    the source right coordinate
     * @param t    the source top coordinate
     */
    private void setSrc(int quad, int l, int b, int r, int t) {
        int i = quad * 4;
        src[i] = l;
        src[i + 1] = b;
        src[i + 2] = r;
        src[i + 3] = t;
    }

    /**
     * <p>
     * Updates cached sine and cosine values if the rotation changed.
     * </p>
     *
     * <p>
     * Rotation is converted to radians using a negated angle so the resulting transform
     * matches the rendering convention used by the rest of the class.
     * </p>
     */
    private void cacheRotationTrigIfNeeded() {
        if (rotation == cachedRotation) return;

        cachedRotation = rotation;

        float rad = (float) Math.toRadians(-rotation);
        sinRot = (float) Math.sin(rad);
        cosRot = (float) Math.cos(rad);
    }

    /**
     * <p>
     * Rebuilds the world-space vertex buffer from the cached local mesh.
     * </p>
     *
     * <p>
     * Each local vertex is translated relative to the rotation origin, rotated using
     * the cached sine and cosine values, and then translated into world space using
     * the bounds position plus origin. The final positions are written into the
     * reusable vertex buffer.
     * </p>
     */
    private void updateWorldBuffers() {
        cacheRotationTrigIfNeeded();

        float ox = origin.getX();
        float oy = origin.getY();

        float px = bounds.getX() + ox;
        float py = bounds.getY() + oy;

        nineVertexBuffer.position(0);

        for (int v = 0; v < VERTS; v++) {
            int idx = v * 2;

            float lx = nineLocalVertices[idx] - ox;
            float ly = nineLocalVertices[idx + 1] - oy;

            float rx = lx * cosRot - ly * sinRot;
            float ry = lx * sinRot + ly * cosRot;

            nineVertexBuffer.put(idx, rx + px);
            nineVertexBuffer.put(idx + 1, ry + py);
        }

        nineVertexBuffer.position(0);
        dirtyWorld = false;
    }

    /**
     * <p>
     * Draws the nine-patch immediately using the current OpenGL client-state arrays.
     * </p>
     *
     * <p>
     * If the local mesh is dirty, it is rebuilt first. If the world-space buffer is
     * dirty, it is updated next. The current tint color is applied through
     * {@code glColor4f}, the backing texture is bound, the vertex and UV pointers are
     * pointed at the cached buffers, and all nine quads are drawn in one
     * {@code glDrawArrays(GL_QUADS, ...)} call.
     * </p>
     */
    public void draw() {
        if (dirtyMesh) rebuildLocalMesh();
        if (dirtyWorld) updateWorldBuffers();

        glColor4f(color.r(), color.g(), color.b(), color.a());
        glBindTexture(GL_TEXTURE_2D, texture.getTextureID());
        glVertexPointer(2, GL_FLOAT, 0, nineVertexBuffer);
        glTexCoordPointer(2, GL_FLOAT, 0, nineUvBuffer);
        glDrawArrays(GL_QUADS, 0, VERTS);
    }

    /**
     * <p>
     * Returns the left border size in pixels.
     * </p>
     *
     * @return the left border size
     */
    public int getLeft() {
        return left;
    }

    /**
     * <p>
     * Sets the left border size and marks the mesh dirty if the value changes.
     * </p>
     *
     * @param left the new left border size
     */
    public void setLeft(int left) {
        if (this.left == left) return;

        this.left = left;
        markDirtyAll();
    }

    /**
     * <p>
     * Returns the right border size in pixels.
     * </p>
     *
     * @return the right border size
     */
    public int getRight() {
        return right;
    }

    /**
     * <p>
     * Sets the right border size and marks the mesh dirty if the value changes.
     * </p>
     *
     * @param right the new right border size
     */
    public void setRight(int right) {
        if (this.right == right) return;

        this.right = right;
        markDirtyAll();
    }

    /**
     * <p>
     * Returns the top border size in pixels.
     * </p>
     *
     * @return the top border size
     */
    public int getTop() {
        return top;
    }

    /**
     * <p>
     * Sets the top border size and marks the mesh dirty if the value changes.
     * </p>
     *
     * @param top the new top border size
     */
    public void setTop(int top) {
        if (this.top == top) return;

        this.top = top;
        markDirtyAll();
    }

    /**
     * <p>
     * Returns the bottom border size in pixels.
     * </p>
     *
     * @return the bottom border size
     */
    public int getBottom() {
        return bottom;
    }

    /**
     * <p>
     * Sets the bottom border size and marks the mesh dirty if the value changes.
     * </p>
     *
     * @param bottom the new bottom border size
     */
    public void setBottom(int bottom) {
        if (this.bottom == bottom) return;

        this.bottom = bottom;
        markDirtyAll();
    }

    /**
     * <p>
     * Resets this nine-patch to its default pooled state.
     * </p>
     *
     * <p>
     * The position is restored to zero, the bounds size is restored to the backing
     * texture size, the region is reset to cover the full texture, the origin is reset
     * to zero, scale is reset to one, rotation is reset to zero, flipping is disabled,
     * and the cached rotation state is invalidated. The tint is set to
     * {@link Color#WHITE}. After reset, all cached geometry is marked dirty.
     * </p>
     */
    @Override
    public void reset() {
        bounds.setX(0f);
        bounds.setY(0f);
        bounds.setWidth(texture.getWidth());
        bounds.setHeight(texture.getHeight());
        region.setRegion(0f, 0f, texture.getWidth(), texture.getHeight());
        origin.set(0f, 0f);
        scaleX = 1f;
        scaleY = 1f;
        rotation = 0f;
        flippedX = false;
        flippedY = false;
        color = Color.WHITE;
        cachedRotation = Float.NaN;
        markDirtyAll();
    }

    /**
     * <p>
     * Disposes resources and clears references owned by this nine-patch.
     * </p>
     *
     * <p>
     * The backing texture is disposed, the region is cleared, the CPU-side buffers are
     * nulled, and mutable object references such as origin and color are cleared.
     * After this method is called, the instance should no longer be used.
     * </p>
     */
    public void dispose() {
        texture.dispose();
        region.setRegion(0f, 0f, 0f, 0f);
        nineVertexBuffer = null;
        nineUvBuffer = null;
        origin = null;
        color = null;
    }

    /**
     * <p>
     * Sets the filtering mode of the backing texture.
     * </p>
     *
     * <p>
     * This delegates directly to the wrapped texture.
     * </p>
     *
     * @param textureFilter the texture filter to apply
     */
    public void setFilter(TextureFilter textureFilter) {
        texture.setFilter(textureFilter);
    }
}