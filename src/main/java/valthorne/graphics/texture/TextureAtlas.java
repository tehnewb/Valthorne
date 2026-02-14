package valthorne.graphics.texture;

import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * A lightweight texture atlas that maps string names to {@link TextureRegion} instances
 * within a single backing {@link Texture}.
 *
 * <h2>Purpose</h2>
 * <p>
 * {@code TextureAtlas} allows multiple sprite regions to be defined inside one large texture.
 * Each region is identified by a unique string name and internally represented as a
 * {@link TextureRegion} referencing a sub-rectangle of the atlas texture.
 * </p>
 *
 * <h2>Design Notes</h2>
 * <ul>
 *     <li>The atlas does not load textures. It assumes the {@link Texture} is already created.</li>
 *     <li>Regions are stored in a {@link HashMap} for O(1) name lookup.</li>
 *     <li>Duplicate region names are not allowed.</li>
 *     <li>The atlas does not automatically dispose the texture.</li>
 * </ul>
 *
 * <h2>Example Usage</h2>
 * <pre>{@code
 * Texture texture = new Texture("sprites.png");
 *
 * TextureAtlas atlas = new TextureAtlas(texture)
 *     .add("player_idle", 0, 0, 32, 32)
 *     .add("player_run_1", 32, 0, 32, 32)
 *     .add("player_run_2", 64, 0, 32, 32);
 *
 * TextureRegion idle = atlas.get("player_idle");
 * idle.setPosition(100, 200);
 * idle.draw();
 * }</pre>
 *
 * <p>
 * This structure is ideal for sprite sheets, animation frames, UI icon atlases,
 * and reducing texture bindings during rendering.
 * </p>
 *
 * @author Albert Beaupre
 * @since February 14th, 2026
 */
public final class TextureAtlas {

    private final Texture texture;                         // Backing atlas texture shared by all regions.
    private final Map<String, TextureRegion> regions;      // Map of region name -> TextureRegion.

    /**
     * Creates a new texture atlas using an already-loaded texture.
     *
     * <p>
     * The provided texture becomes the backing atlas. No copy is made.
     * All regions added to this atlas will reference this texture.
     * </p>
     *
     * @param texture backing atlas texture (must not be null)
     * @throws NullPointerException if {@code texture} is null
     */
    public TextureAtlas(Texture texture) {
        this.texture = Objects.requireNonNull(texture, "texture");
        this.regions = new HashMap<>();
    }

    /**
     * Adds a named rectangular region to the atlas.
     *
     * <p>
     * The region is defined in source-texture pixel coordinates.
     * The created {@link TextureRegion} will default its draw size to
     * match the region's width and height.
     * </p>
     *
     * <p>
     * Region names must be unique within this atlas.
     * </p>
     *
     * @param name   unique region name (must not be null)
     * @param x      source x coordinate in pixels
     * @param y      source y coordinate in pixels
     * @param width  region width in pixels
     * @param height region height in pixels
     * @return this atlas (for chaining)
     * @throws NullPointerException     if {@code name} is null
     * @throws IllegalArgumentException if a region with the same name already exists
     */
    public TextureAtlas add(String name, int x, int y, int width, int height) {
        Objects.requireNonNull(name, "name");

        if (regions.containsKey(name)) {
            throw new IllegalArgumentException("Duplicate atlas region: " + name);
        }

        TextureRegion region = new TextureRegion(texture, x, y, width, height);
        region.setSize(width, height); // Default draw size matches source region size.

        regions.put(name, region);
        return this;
    }

    /**
     * Retrieves a region by name.
     *
     * <p>
     * This is an O(1) lookup via {@link HashMap}.
     * </p>
     *
     * @param name region name
     * @return the corresponding {@link TextureRegion}
     * @throws NoSuchElementException if no region exists with the given name
     */
    public TextureRegion get(String name) {
        TextureRegion region = regions.get(name);
        if (region == null) {
            throw new NoSuchElementException("No atlas region named: " + name);
        }
        return region;
    }

    /**
     * Returns whether a region exists in this atlas.
     *
     * @param name region name
     * @return true if the region exists, false otherwise
     */
    public boolean has(String name) {
        return regions.containsKey(name);
    }

    /**
     * Returns the total number of regions stored in this atlas.
     *
     * @return region count
     */
    public int size() {
        return regions.size();
    }

    /**
     * Returns the backing texture used by this atlas.
     *
     * <p>
     * This allows direct access if additional texture-level operations are needed.
     * The atlas does not own the texture lifecycle.
     * </p>
     *
     * @return backing {@link Texture}
     */
    public Texture getTexture() {
        return texture;
    }

    /**
     * Clears all region mappings from this atlas.
     *
     * <p>
     * This does not dispose the backing texture.
     * If you want to dispose the texture, do so manually.
     * </p>
     */
    public void dispose() {
        regions.clear();
    }
}