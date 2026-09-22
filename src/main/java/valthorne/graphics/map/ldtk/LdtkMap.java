package valthorne.graphics.map.ldtk;

import valthorne.graphics.Color;
import valthorne.graphics.texture.Texture;
import valthorne.graphics.texture.TextureBatch;
import valthorne.graphics.texture.TextureRegion;

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
        LdtkBackground bg = level.background();
        Texture texture = backgrounds.get(level.iid());
        if (bg == null || texture == null) return;
        int cropWidth = bg.cropWidth() > 0 ? bg.cropWidth() : texture.getWidth(), cropHeight = bg.cropHeight() > 0 ? bg.cropHeight() : texture.getHeight();
        float scaleX = bg.scaleX() == 0 ? 1 : bg.scaleX(), scaleY = bg.scaleY() == 0 ? scaleX : bg.scaleY();
        float x = level.worldX() + bg.topLeftX(), y = -level.worldY() + level.height() - bg.topLeftY() - cropHeight * scaleY;
        batch.draw(texture, x, y, cropWidth * scaleX, cropHeight * scaleY, bg.cropX(), bg.cropY(), cropWidth, cropHeight);
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
        Objects.requireNonNull(batch, "batch");
        if (!layer.visible()) return;
        Texture texture = textures.get(layer.tilesetUid());
        if (texture == null) return;
        drawTiles(batch, level, layer, texture, layer.gridTiles());
        drawTiles(batch, level, layer, texture, layer.autoTiles());
    }

    /**
     * Emits draw calls for tile placements, applying flips and combined opacity.
     *
     * @param batch active texture batch
     * @param level level providing world placement
     * @param layer layer providing offsets, tile size, and opacity
     * @param texture uploaded tileset texture
     * @param tiles placements to draw in iteration order
     */
    private void drawTiles(TextureBatch batch, LdtkLevel level, LdtkLayer layer, Texture texture, List<LdtkTile> tiles) {
        int size = layer.gridSize();
        for (LdtkTile tile : tiles) {
            float x = level.worldX() + layer.pixelOffsetX() + tile.x();
            float y = -(level.worldY()) + level.height() - layer.pixelOffsetY() - tile.y() - size;
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
            batch.draw(new TextureRegion(texture, sx, sy, sw, sh), x, y, size, size, Color.WHITE.withAlpha(alpha));
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
}
