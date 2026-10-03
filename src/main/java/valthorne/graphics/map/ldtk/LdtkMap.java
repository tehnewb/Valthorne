package valthorne.graphics.map.ldtk;

import valthorne.graphics.Color;
import valthorne.graphics.texture.Texture;
import valthorne.graphics.texture.TextureBatch;
import valthorne.math.geometry.Rectangle;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * GPU-backed renderer for an {@link LdtkProject}. Construction uploads decoded
 * tilesets and backgrounds, rendering translates LDtk's top-left coordinates into
 * Valthorne world coordinates, and {@link #dispose()} releases every created texture.
 * All lifecycle and rendering operations belong on the OpenGL thread.
 *
 * <p>A typical render-thread lifecycle is:</p>
 * <pre>{@code
 * LdtkMap map = project.asMap();
 * map.render(batch, "Level_0");
 * map.dispose();
 * }</pre>
 */
public final class LdtkMap {
    private final LdtkProject project; // CPU-side project represented by this renderer.
    private final Map<Integer, Texture> textures = new HashMap<>(); // Tilesets by definition UID.
    private final Map<String, Texture> backgrounds = new HashMap<>(); // Backgrounds by level IID.
    private final float nativeMinX; // Lowest horizontal coordinate among project levels.
    private final float nativeMinY; // Lowest bottom-up vertical coordinate among project levels.
    private final float nativeWidth; // Combined project width before editor scaling.
    private final float nativeHeight; // Combined project height before editor scaling.
    private float originX; // Rendered map's horizontal world origin.
    private float rotation; // Counterclockwise world orientation around the project center.
    private float rotationCosine = 1f; // Cached cosine refreshed only when the map angle changes.
    private float rotationSine; // Cached sine used by the existing per-quad rotation API.
    private float originY; // Rendered map's vertical world origin.
    private float scaleX = 1f; // Horizontal ratio from project pixels to world units.
    private float scaleY = 1f; // Vertical ratio from project pixels to world units.
    private boolean visible = true; // Whether this drawable participates in rendering and animation updates.
    private final Color tileTint = new Color(1f, 1f, 1f, 1f); // Reused white tint for per-tile opacity without temporary colors.
    private boolean disposed; // Whether owned GPU textures have already been released.

    /**
     * Uploads every decoded tileset and background image to a GPU texture.
     *
     * @param project non-null project whose decoded images remain the source data
     * @throws NullPointerException if {@code project} is null
     * @throws RuntimeException if texture creation fails
     */
    public LdtkMap(LdtkProject project) {
        this.project = Objects.requireNonNull(project, "project");
        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        for (LdtkLevel level : project.levels()) {
            minX = Math.min(minX, level.worldX());
            minY = Math.min(minY, -level.worldY());
            maxX = Math.max(maxX, level.worldX() + level.width());
            maxY = Math.max(maxY, -level.worldY() + level.height());
        }
        nativeMinX = Float.isFinite(minX) ? minX : 0f;
        nativeMinY = Float.isFinite(minY) ? minY : 0f;
        nativeWidth = Math.max(1f, Float.isFinite(maxX) ? maxX - nativeMinX : project.defaultLevelWidth());
        nativeHeight = Math.max(1f, Float.isFinite(maxY) ? maxY - nativeMinY : project.defaultLevelHeight());
        for (LdtkTileset tileset : project.tilesets().values())
            if (tileset.textureData() != null) textures.put(tileset.uid(), new Texture(tileset.textureData()));
        for (LdtkLevel level : project.levels())
            if (level.background() != null && level.background().textureData() != null)
                backgrounds.put(level.iid(), new Texture(level.background().textureData()));
    }

    /**
     * Returns the immutable CPU-side project represented by this renderer.
     *
     * @return the project supplied at construction
     */
    public LdtkProject project() {
        return project;
    }

    /**
     * Returns the unscaled width of all project levels combined.
     *
     * @return native width in LDtk pixels
     */
    public float getNativeWidth() {
        return nativeWidth;
    }

    /**
     * Returns the unscaled height of all project levels combined.
     *
     * @return native height in LDtk pixels
     */
    public float getNativeHeight() {
        return nativeHeight;
    }

    /**
     * Returns the original left coordinate of the combined level bounds.
     *
     * @return minimum project X
     */
    public float getNativeMinX() {
        return nativeMinX;
    }

    /**
     * Returns the original bottom coordinate of the combined level bounds.
     *
     * @return minimum bottom-up project Y
     */
    public float getNativeMinY() {
        return nativeMinY;
    }

    /**
     * Moves and scales all levels together without changing the parsed project.
     *
     * @param x rendered left edge in world units
     * @param y rendered bottom edge in world units
     * @param width positive rendered width
     * @param height positive rendered height
     */
    public void setBounds(float x, float y, float width, float height) {
        if (width <= 0f || height <= 0f)
            throw new IllegalArgumentException("Map bounds must have positive width and height");
        originX = x;
        originY = y;
        scaleX = width / nativeWidth;
        scaleY = height / nativeHeight;
    }

    /**
     * Returns the currently rendered world rectangle across all levels.
     *
     * @return current bounds in world units
     */
    public Rectangle getBounds() {
        return new Rectangle(originX, originY, nativeWidth * scaleX, nativeHeight * scaleY);
    }

    /**
     * Returns the project's counterclockwise visual orientation.
     *
     * @return degrees about the map center
     */
    public float getRotation() {return rotation;}

    /**
     * Rotates rendered tiles and backgrounds without rewriting project metadata.
     *
     * @param degrees finite counterclockwise angle
     */
    public void setRotation(float degrees) {
        if (!Float.isFinite(degrees)) throw new IllegalArgumentException("Map rotation must be finite");
        if (rotation == degrees) return;
        rotation = degrees;
        double radians = Math.toRadians(degrees);
        rotationCosine = (float) Math.cos(radians);
        rotationSine = (float) Math.sin(radians);
    }

    /**
     * Finds the uploaded texture for a tileset definition.
     *
     * @param uid tileset definition UID
     * @return the GPU texture, or {@code null} if that tileset had no decoded image
     */
    public Texture tilesetTexture(int uid) {
        return textures.get(uid);
    }

    /**
     * Renders every level in project order.
     *
     * @param batch active texture batch receiving background and tile draw calls
     * @throws NullPointerException if {@code batch} is null
     */
    public void render(TextureBatch batch) {
        if (!visible) return;
        for (LdtkLevel level : project.levels()) render(batch, level);
    }

    /**
     * Renders the first level with the requested identifier.
     *
     * @param batch active texture batch
     * @param levelIdentifier exact level identifier
     * @throws NullPointerException if {@code batch} is null and a level is found
     */
    public void render(TextureBatch batch, String levelIdentifier) {
        if (!visible) return;
        LdtkLevel level = project.level(levelIdentifier);
        if (level != null) render(batch, level);
    }

    /**
     * Renders a level background followed by its visible layers in reverse LDtk order.
     *
     * @param batch active texture batch
     * @param level non-null level, normally from this map's project
     * @throws NullPointerException if either argument is null
     */
    public void render(TextureBatch batch, LdtkLevel level) {
        if (!visible) return;
        Objects.requireNonNull(batch, "batch");
        Objects.requireNonNull(level, "level");
        renderBackground(batch, level);
        List<LdtkLayer> layers = level.layers();
        for (int i = layers.size() - 1; i >= 0; i--) renderLayer(batch, level, layers.get(i));
    }

    /**
     * Draws a level's cropped and scaled background when a texture is available.
     * Missing background metadata or image data is a no-op.
     *
     * @param batch active texture batch
     * @param level level whose background should be drawn
     * @throws NullPointerException if {@code level} is null
     */
    public void renderBackground(TextureBatch batch, LdtkLevel level) {
        if (!visible) return;
        LdtkBackground bg = level.background();
        Texture texture = backgrounds.get(level.iid());
        if (bg == null || texture == null) return;
        int cropWidth = bg.cropWidth() > 0 ? bg.cropWidth() : texture.getWidth(), cropHeight = bg.cropHeight() > 0 ? bg.cropHeight() : texture.getHeight();
        float scaleX = bg.scaleX() == 0 ? 1 : bg.scaleX(), scaleY = bg.scaleY() == 0 ? scaleX : bg.scaleY();
        float x = originX + (level.worldX() + bg.topLeftX() - nativeMinX) * this.scaleX;
        float y = originY + (-level.worldY() + level.height() - bg.topLeftY() - cropHeight * scaleY - nativeMinY) * this.scaleY;
        float pivotX = originX + nativeWidth * this.scaleX * .5f;
        float pivotY = originY + nativeHeight * this.scaleY * .5f;
        float textureWidth = texture.getWidth();
        float textureHeight = texture.getHeight();
        batch.drawUV(texture, x, y, cropWidth * scaleX * this.scaleX, cropHeight * scaleY * this.scaleY, bg.cropX() / textureWidth, bg.cropY() / textureHeight, (bg.cropX() + cropWidth) / textureWidth, (bg.cropY() + cropHeight) / textureHeight, rotation == 0 ? 0 : pivotX - x, rotation == 0 ? 0 : pivotY - y, rotationSine, rotationCosine, null);
    }

    /**
     * Finds and renders one named layer from a level.
     *
     * @param batch active texture batch
     * @param level level containing the layer
     * @param layerIdentifier exact layer identifier
     * @throws NullPointerException if {@code level} is null
     */
    public void renderLayer(TextureBatch batch, LdtkLevel level, String layerIdentifier) {
        if (!visible) return;
        LdtkLayer layer = level.layer(layerIdentifier);
        if (layer != null) renderLayer(batch, level, layer);
    }

    /**
     * Renders one visible tile layer when its tileset texture is available.
     * Entity and IntGrid data are retained for game logic but are not drawn here.
     *
     * @param batch active texture batch
     * @param level level providing world placement
     * @param layer layer whose grid and auto tiles should be drawn
     * @throws NullPointerException if {@code batch}, {@code level}, or {@code layer} is null
     */
    public void renderLayer(TextureBatch batch, LdtkLevel level, LdtkLayer layer) {
        if (!visible) return;
        Objects.requireNonNull(batch, "batch");
        if (!layer.visible()) return;
        Texture texture = textures.get(layer.tilesetUid());
        if (texture == null) return;
        drawTiles(batch, level, layer, texture, layer.gridTiles());
        drawTiles(batch, level, layer, texture, layer.autoTiles());
    }

    /**
     * Emits tile placements under the prepared map orientation.
     *
     * @param batch active destination batch
     * @param level placement origin
     * @param layer tile offsets and opacity
     * @param texture borrowed tileset
     * @param tiles canonical placements
     */
    private void drawTiles(TextureBatch batch, LdtkLevel level, LdtkLayer layer, Texture texture, List<LdtkTile> tiles) {
        int size = layer.gridSize();
        float pivotX = originX + nativeWidth * scaleX * .5f;
        float pivotY = originY + nativeHeight * scaleY * .5f;
        float textureWidth = texture.getWidth();
        float textureHeight = texture.getHeight();
        for (LdtkTile tile : tiles) {
            float x = originX + (level.worldX() + layer.pixelOffsetX() + tile.x() - nativeMinX) * scaleX;
            float y = originY + (-level.worldY() + level.height() - layer.pixelOffsetY() - tile.y() - size - nativeMinY) * scaleY;
            float sx = tile.sourceX(), sy = tile.sourceY(), sw = size, sh = size;
            if (tile.flipX()) {
                sx += size;
                sw = -size;
            }
            if (tile.flipY()) {
                sy += size;
                sh = -size;
            }
            float alpha = Math.max(0, Math.min(1, tile.alpha() * layer.opacity()));
            tileTint.a(alpha);
            batch.drawUV(texture, x, y, size * scaleX, size * scaleY, sx / textureWidth, sy / textureHeight, (sx + sw) / textureWidth, (sy + sh) / textureHeight, rotation == 0 ? 0 : pivotX - x, rotation == 0 ? 0 : pivotY - y, rotationSine, rotationCosine, tileTint);
        }
    }

    /**
     * Releases all GPU textures created by this map and clears its lookup tables.
     * Repeated calls are safe no-ops; rendering after disposal draws no tiles or
     * backgrounds because their texture mappings have been cleared.
     */
    public void dispose() {
        if (disposed) return;
        textures.values().forEach(Texture::dispose);
        backgrounds.values().forEach(Texture::dispose);
        textures.clear();
        backgrounds.clear();
        disposed = true;
    }
    /**
     * Reports whether this object participates in rendering and animation updates.
     *
     * @return current visibility
     */
    public boolean isVisible() {return visible;}

    /**
     * Changes visibility without releasing resources or changing the object's
     * position, frame, or timing state. Hidden objects can be shown again.
     *
     * @param visible whether rendering and animation updates are enabled
     */
    public void setVisible(boolean visible) {this.visible = visible;}
}
