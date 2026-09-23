package valthorne.graphics.scene;

import valthorne.graphics.render.BillboardBatch3D;
import valthorne.graphics.model.Material3D;
import valthorne.graphics.render.RenderPass3D;

import org.joml.FrustumIntersection;
import org.joml.Vector3f;
import org.joml.primitives.AABBf;
import valthorne.camera.Camera3D;
import valthorne.graphics.Color;
import valthorne.graphics.texture.TextureRegion;

/**
 * Camera-facing textured quad positioned by an anchor in 3D world space.
 * Defaults are unit width and height, a bottom-center anchor (0.5, 0), white
 * color, cylindrical facing around Z, visibility enabled and no texture region.
 * The initial material uses the translucent pass with depth writes disabled.
 *
 * <p>Size is in world units and anchor coordinates are fractions of those
 * dimensions; anchors outside zero through one are permitted. Cylindrical mode
 * stays upright along Z, while other modes use the batch's camera-facing basis.
 * Bounds conservatively enclose supported orientations rather than just the
 * current camera-facing quad.</p>
 *
 * <p>Position and color getters expose mutable owned values. Region and material
 * references are borrowed, shared and never disposed by this sprite. Bounds are
 * rebuilt into reused storage on each query, including changes made through
 * the position vector. Use on the rendering thread without concurrent mutation.</p>
 *
 * @author Albert Beaupre
 */
public final class BillboardSprite3D implements BillboardRenderable3D {

    private final Vector3f position = new Vector3f(); // Mutable world-space location of the anchor.
    private final AABBf worldBounds = new AABBf(); // Reused conservative bounds rebuilt for visibility queries.
    private final Color color = Color.WHITE.copy(); // Owned per-sprite color, copied from color setters.
    private TextureRegion region; // Borrowed texture region; null suppresses visibility.
    private Material3D material = new Material3D().setRenderPass(RenderPass3D.TRANSLUCENT); // Borrowed material, initially a new translucent configuration.
    private BillboardMode3D mode = BillboardMode3D.CYLINDRICAL; // Camera-facing orientation policy, initially cylindrical.
    private float width = 1f; // Horizontal quad extent in world units.
    private float height = 1f; // Vertical quad extent in world units.
    private float anchorX = 0.5f; // Horizontal anchor fraction, initially centered.
    private float anchorY = 0f; // Vertical anchor fraction, initially at the bottom.
    private boolean visible = true; // Explicit visibility flag, independent of frustum membership.

    /**
     * Chooses an outward margin of at least two world units or five percent of the largest bounds extent, whichever is greater.
     *
     * @param bounds the current world bounds
     * @return the padding applied to every bounds face
     */
    private static float cullPadding(AABBf bounds) {
        float maxExtent = Math.max((bounds.maxX - bounds.minX), Math.max((bounds.maxY - bounds.minY), (bounds.maxZ - bounds.minZ)));
        return Math.max(2f, maxExtent * 0.05f);
    }

    /**
     * Copies position, color and scalar settings from another sprite while sharing its material and region. Cached bounds are not copied; a later query rebuilds them. Self-assignment is supported.
     *
     * @param other the nonnull source sprite
     * @return this sprite
     * @throws NullPointerException if other is null
     */
    public BillboardSprite3D set(BillboardSprite3D other) {
        position.set(other.position);
        color.set(other.color);
        region = other.region;
        material = other.material;
        mode = other.mode;
        width = other.width;
        height = other.height;
        anchorX = other.anchorX;
        anchorY = other.anchorY;
        visible = other.visible;
        return this;
    }

    /**
     * Returns the live material reference used for pass and rendering configuration. Changes affect every sprite sharing that material.
     *
     * @return the nonnull borrowed material
     */
    @Override
    public Material3D getMaterial() {
        return material;
    }

    /**
     * Retains a replacement material without copying it or changing any of its settings. The old material is not disposed.
     *
     * @param material the nonnull shared material
     * @return this sprite
     * @throws NullPointerException if material is null
     */
    public BillboardSprite3D setMaterial(Material3D material) {
        if (material == null) throw new NullPointerException("material");
        this.material = material;
        return this;
    }

    /**
     * Returns the live region reference used for texture selection and UV coordinates.
     *
     * @return the borrowed region, or null when unset
     */
    @Override
    public TextureRegion getTextureRegion() {
        return region;
    }

    /**
     * Retains an optional region without changing world dimensions. A null region makes visibility queries fail and produces empty bounds.
     *
     * @param region the borrowed texture region, or null
     * @return this sprite
     */
    public BillboardSprite3D setTextureRegion(TextureRegion region) {
        this.region = region;
        return this;
    }

    /**
     * Exposes the mutable world anchor position. Direct changes are observed by drawing and the next bounds query.
     *
     * @return the owned live position vector
     */
    public Vector3f getPosition() {
        return position;
    }

    /**
     * Assigns the world anchor position without finiteness validation. Supply finite values for meaningful bounds and rendering.
     *
     * @param x the world X coordinate
     * @param y the world Y coordinate
     * @param z the world Z coordinate
     * @return this sprite
     */
    public BillboardSprite3D setPosition(float x, float y, float z) {
        position.set(x, y, z);
        return this;
    }

    /**
     * Reads horizontal size in the billboard's local facing basis, independently of texture pixel dimensions.
     *
     * @return the configured world-unit width
     */
    public float getWidth() {
        return width;
    }

    /**
     * Reads vertical size in the billboard's local facing basis, independently of texture pixel dimensions.
     *
     * @return the configured world-unit height
     */
    public float getHeight() {
        return height;
    }

    /**
     * Assigns quad dimensions after rejecting values at or below zero. NaN and positive infinity are not explicitly rejected; callers should supply finite positive extents.
     *
     * @param width  the horizontal extent in world units
     * @param height the vertical extent in world units
     * @return this sprite
     * @throws IllegalArgumentException if either extent compares at or below zero
     */
    public BillboardSprite3D setSize(float width, float height) {
        if (width <= 0f) throw new IllegalArgumentException("width must be > 0");
        if (height <= 0f) throw new IllegalArgumentException("height must be > 0");
        this.width = width;
        this.height = height;
        return this;
    }

    /**
     * Reads the horizontal anchor fraction: zero aligns the left edge and one the right edge with the world position.
     *
     * @return the stored horizontal fraction, not clamped
     */
    public float getAnchorX() {
        return anchorX;
    }

    /**
     * Reads the vertical anchor fraction: zero aligns the bottom edge and one the top edge with the world position.
     *
     * @return the stored vertical fraction, not clamped
     */
    public float getAnchorY() {
        return anchorY;
    }

    /**
     * Assigns anchor fractions without clamping or validation. Local edges are computed by subtracting anchor times size, allowing the pivot to lie outside the quad.
     *
     * @param anchorX the horizontal anchor fraction
     * @param anchorY the vertical anchor fraction
     * @return this sprite
     */
    public BillboardSprite3D setAnchor(float anchorX, float anchorY) {
        this.anchorX = anchorX;
        this.anchorY = anchorY;
        return this;
    }

    /**
     * Returns the orientation policy used by the batch and conservative bounds calculation.
     *
     * @return the nonnull billboard mode
     */
    public BillboardMode3D getMode() {
        return mode;
    }

    /**
     * Changes orientation policy without changing anchor, position or dimensions. Bounds reflect it on the next query.
     *
     * @param mode the nonnull facing mode
     * @return this sprite
     * @throws NullPointerException if mode is null
     */
    public BillboardSprite3D setMode(BillboardMode3D mode) {
        if (mode == null) throw new NullPointerException("mode");
        this.mode = mode;
        return this;
    }

    /**
     * Exposes the mutable per-sprite color, independently of the material's tint. Direct changes affect later draws.
     *
     * @return the owned live color
     */
    public Color getColor() {
        return color;
    }

    /**
     * Copies color components into owned storage without retaining the source object.
     *
     * @param color the nonnull source color
     * @return this sprite
     * @throws NullPointerException if color is null
     */
    public BillboardSprite3D setColor(Color color) {
        if (color == null) throw new NullPointerException("color");
        this.color.set(color);
        return this;
    }

    /**
     * Reports only the explicit flag, without requiring a region or testing the camera frustum.
     *
     * @return whether the sprite is explicitly enabled for visibility
     */
    public boolean isRenderableVisible() {
        return visible;
    }

    /**
     * Changes the explicit visibility flag without clearing geometry or material state.
     *
     * @param visible the desired visibility flag
     * @return this sprite
     */
    public BillboardSprite3D setVisible(boolean visible) {
        this.visible = visible;
        return this;
    }

    /**
     * Rebuilds and returns conservative world bounds in reused storage. Missing region produces empty bounds. Copy the result if it must survive later queries; do not treat it as independently owned.
     *
     * @return the borrowed, freshly rebuilt bounds
     */
    public AABBf getWorldBounds() {
        rebuildWorldBounds();
        return worldBounds;
    }

    /**
     * Rejects an explicitly hidden sprite, null camera or missing region, then tests the camera's current frustum. Does not rebuild camera matrices.
     *
     * @param camera the camera supplying current frustum planes, possibly null
     * @return whether the sprite is potentially visible
     */
    @Override
    public boolean isVisible(Camera3D camera) {
        if (!visible || camera == null || region == null) {
            return false;
        }
        return isVisible(camera.getFrustum());
    }

    /**
     * Tests conservative bounds with padding against the supplied frustum. Hidden sprites, null frusta and missing regions return false. A positive result need not mean exact quad intersection.
     *
     * @param frustum the current frustum, possibly null
     * @return whether padded bounds survive culling
     */
    public boolean isVisible(FrustumIntersection frustum) {
        if (!visible || frustum == null || region == null) {
            return false;
        }
        AABBf bounds = getWorldBounds();
        float padding = cullPadding(bounds);
        return (bounds.minX <= bounds.maxX && bounds.minY <= bounds.maxY && bounds.minZ <= bounds.maxZ) && frustum.testAab(bounds.minX - padding, bounds.minY - padding, bounds.minZ - padding, bounds.maxX + padding, bounds.maxY + padding, bounds.maxZ + padding);
    }

    /**
     * Delegates geometry emission to the batch without checking this sprite's visible flag. The batch skips a null camera or missing region; otherwise it requires an active batch with the region's texture.
     *
     * @param billboardBatch the nonnull destination batch
     * @param camera         the facing camera, or null to skip
     * @throws NullPointerException     if billboardBatch is null
     * @throws IllegalStateException    if drawing requires a batch that has not begun
     * @throws IllegalArgumentException if the region texture differs from the active batch texture
     */
    @Override
    public void emit(BillboardBatch3D billboardBatch, Camera3D camera) {
        if (billboardBatch == null) throw new NullPointerException("billboardBatch");
        billboardBatch.draw(this, camera);
    }

    /**
     * Clears bounds, then encloses possible quad orientations when a region exists. Cylindrical mode uses the maximum horizontal anchor offset as an XY radius and preserves vertical Z offsets. Other modes use a cube around the anchor with radius equal to the farthest local corner distance. This is recomputed on every request.
     */
    private void rebuildWorldBounds() {
        worldBounds.setMin(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY).setMax(Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY);
        if (region == null) {
            return;
        }

        float left = -anchorX * width;
        float right = left + width;
        float bottom = -anchorY * height;
        float top = bottom + height;

        if (mode == BillboardMode3D.CYLINDRICAL) {
            float horizontalRadius = Math.max(Math.abs(left), Math.abs(right));
            worldBounds.setMin(position.x() - horizontalRadius, position.y() - horizontalRadius, position.z() + bottom).setMax(position.x() + horizontalRadius, position.y() + horizontalRadius, position.z() + top).correctBounds();
            return;
        }

        float radius = (float) Math.sqrt(Math.max(Math.abs(left), Math.abs(right)) * Math.max(Math.abs(left), Math.abs(right)) + Math.max(Math.abs(bottom), Math.abs(top)) * Math.max(Math.abs(bottom), Math.abs(top)));
        worldBounds.setMin(position.x() - radius, position.y() - radius, position.z() - radius).setMax(position.x() + radius, position.y() + radius, position.z() + radius).correctBounds();
    }
}
