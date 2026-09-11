package valthorne.graphics.texture;

import valthorne.math.MathUtils;

import java.util.*;

/**
 * Builds a square GPU texture atlas and keyed region views from borrowed CPU
 * image data. Inputs may be complete textures, decoded images, or rectangular
 * regions, but source pixels must remain readable until build. Adding inputs
 * does not copy pixels or transfer ownership.
 * <p>
 * Build sorts inputs by descending height and packs unrotated shelves from left
 * to right, adding configured region padding and an outer border. It estimates
 * a square size, doubles until packing succeeds, and optionally rounds dimensions
 * to powers of two. Placement and source rectangles follow TexturePacker's
 * top-left pixel convention. Padding is empty space, not edge-pixel extrusion.
 * </p>
 * <p>
 * Each build allocates a separate TextureData and GPU texture and requires a current
 * OpenGL context. The builder retains pending inputs for subsequent builds.
 * The caller owns result cleanup; regions share the result texture. Duplicate
 * keys are accepted during collection and rejected at build time.
 * </p>
 * <pre>{@code
 * TextureAtlas atlas = new TextureAtlas().setPadding(2).setBorder(2);
 * atlas.add("icon", sourceData);
 * TextureAtlas.Result result = atlas.build();
 * try {
 *     TextureRegion icon = result.getRegion("icon");
 *     // Use icon while the result texture remains alive.
 * } finally {
 *     result.getAtlasTexture().dispose();
 *     result.getAtlasData().dispose();
 * }
 * }</pre>
 * @author Albert Beaupre
 * @since February 14th, 2026
 */
public final class TextureAtlas {

    private final List<Item> items = new ArrayList<>(); // Borrowed pending source rectangles retained across builds.
    private int padding = 0;       // Space between regions.
    private int border = 0;        // Space around the entire atlas content.
    private boolean powerOfTwo = false; // Whether attempted square dimensions are rounded to powers of two.
    private int maxSize = 4096; // Maximum attempted dimension for nonempty builds.
    private int startSize = 256; // Minimum initial square dimension before optional rounding.

    /**
     * Sets empty pixel spacing between neighboring shelf items and between shelves.
     * Does not extrude edge colors into the padding.
     *
     * @param padding nonnegative spacing in pixels
     * @return this builder
     * @throws IllegalArgumentException if padding is negative
     */
    public TextureAtlas setPadding(int padding) {
        if (padding < 0) throw new IllegalArgumentException("padding must be >= 0");
        this.padding = padding;
        return this;
    }

    /**
     * Sets the empty border reserved on every side of packed content.
     *
     * @param border nonnegative border width in pixels
     * @return this builder
     * @throws IllegalArgumentException if border is negative
     */
    public TextureAtlas setBorder(int border) {
        if (border < 0) throw new IllegalArgumentException("border must be >= 0");
        this.border = border;
        return this;
    }

    /**
     * Controls rounding of attempted square sizes to the next power of two.
     * The result remains square even when this option is disabled.
     *
     * @param powerOfTwo whether to round attempted dimensions
     * @return this builder
     */
    public TextureAtlas setPowerOfTwo(boolean powerOfTwo) {
        this.powerOfTwo = powerOfTwo;
        return this;
    }

    /**
     * Sets the maximum attempted dimension for nonempty builds. This is an application
     * limit rather than a query of the GPU texture limit. The empty-atlas path uses
     * the sanitized start size without checking this bound.
     *
     * @param maxSize positive dimension limit in pixels
     * @return this builder
     * @throws IllegalArgumentException if maxSize is nonpositive
     */
    public TextureAtlas setMaxSize(int maxSize) {
        if (maxSize <= 0) throw new IllegalArgumentException("maxSize must be > 0");
        this.maxSize = maxSize;
        return this;
    }

    /**
     * Sets the minimum starting dimension before optional power-of-two rounding.
     * Nonempty builds may start larger according to content estimates and grow by
     * doubling when packing fails.
     *
     * @param startSize positive initial dimension in pixels
     * @return this builder
     * @throws IllegalArgumentException if startSize is nonpositive
     */
    public TextureAtlas setStartSize(int startSize) {
        if (startSize <= 0) throw new IllegalArgumentException("startSize must be > 0");
        this.startSize = startSize;
        return this;
    }

    /**
     * Collects the complete retained CPU image of a texture. The texture is not read
     * back from the GPU; keep its data alive until build.
     *
     * @param key nonnull key, validated for uniqueness during build
     * @param texture nonnull texture with retained CPU data
     * @return this builder
     * @throws NullPointerException if key, texture, or retained data is null
     * @throws IllegalArgumentException if image dimensions are nonpositive
     */
    public TextureAtlas add(String key, Texture texture) {
        Objects.requireNonNull(texture, "texture");
        TextureData data = texture.getData();
        if (data == null) throw new NullPointerException("texture.getData() returned null for key=" + key);
        return add(key, data, 0, 0, data.width(), data.height());
    }

    /**
     * Collects a complete borrowed decoded image without copying its pixels.
     *
     * @param key nonnull key, validated for uniqueness during build
     * @param data nonnull source image retained until build
     * @return this builder
     * @throws NullPointerException if key or data is null
     * @throws IllegalArgumentException if dimensions are nonpositive
     */
    public TextureAtlas add(String key, TextureData data) {
        Objects.requireNonNull(data, "data");
        return add(key, data, 0, 0, data.width(), data.height());
    }

    /**
     * Collects a source region from its backing texture's retained pixels, truncating
     * region coordinates and dimensions to integers. Does not bake UV transformations.
     *
     * @param key nonnull key, validated for uniqueness during build
     * @param region nonnull source region with retained CPU pixels
     * @return this builder
     * @throws NullPointerException if a required source reference or key is null
     * @throws IllegalArgumentException if truncated dimensions are nonpositive
     */
    public TextureAtlas add(String key, TextureRegion region) {
        Objects.requireNonNull(region, "TextureRegion cannot be null");
        TextureData data = region.getTexture().getData();

        int sx = (int) region.getRegionX();
        int sy = (int) region.getRegionY();
        int sw = (int) region.getRegionWidth();
        int sh = (int) region.getRegionHeight();

        return add(key, data, sx, sy, sw, sh);
    }

    /**
     * Stores a borrowed source rectangle. Validates references and positive dimensions
     * now; source-coordinate validity and duplicate keys are handled during building
     * and pixel copying.
     *
     * @param key nonnull result key
     * @param src source image that must remain readable
     * @param sx source X in the packer's top-left convention
     * @param sy source Y in the packer's top-left convention
     * @param sw positive source width
     * @param sh positive source height
     * @return this builder
     * @throws NullPointerException if key or src is null
     * @throws IllegalArgumentException if sw or sh is nonpositive
     */
    public TextureAtlas add(String key, TextureData src, int sx, int sy, int sw, int sh) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(src, "src");
        if (sw <= 0) throw new IllegalArgumentException("sw must be > 0");
        if (sh <= 0) throw new IllegalArgumentException("sh must be > 0");

        items.add(new Item(key, src, sx, sy, sw, sh));
        return this;
    }

    /**
     * Drops pending source references without disposing them or any previously built
     * result. Packing configuration is retained.
     *
     * @return this builder
     */
    public TextureAtlas clear() {
        items.clear();
        return this;
    }

    /**
     * Packs pending images and creates a new CPU/GPU atlas pair without clearing the
     * builder. Empty input creates a blank atlas at the sanitized start size.
     * Nonempty input validates keys, sorts by descending height, and grows square
     * attempts until they fit or exceed maxSize. Result map iteration follows that
     * height-sorted order.
     *
     * @return newly owned atlas resources and borrowed region views
     * @throws IllegalStateException if keys repeat or content cannot fit within the size limit
     */
    public Result build() {
        if (items.isEmpty()) {
            // Small empty atlas.
            int w = sanitizeSize(startSize);
            int h = sanitizeSize(startSize);
            TexturePacker packer = new TexturePacker(w, h);
            TextureData atlasData = packer.bake();
            Texture atlasTex = new Texture(atlasData);
            return new Result(atlasData, atlasTex, Collections.unmodifiableMap(new LinkedHashMap<>()));
        }

        // Validate unique keys.
        Map<String, Integer> seen = new LinkedHashMap<>();
        for (Item it : items) {
            Integer prev = seen.put(it.key, 1);
            if (prev != null) throw new IllegalStateException("Duplicate atlas key: " + it.key);
        }

        // Sort by height descending for better packing.
        List<Item> sorted = new ArrayList<>(items);
        sorted.sort(Comparator.comparingInt((Item a) -> a.sh).reversed());

        // Pick a starting size based on content heuristics.
        int size = guessStartSize(sorted);
        size = Math.max(size, sanitizeSize(startSize));
        size = sanitizeSize(size);

        // Grow until fit.
        int finalW = -1;
        int finalH = -1;
        while (true) {
            if (size > maxSize) {
                throw new IllegalStateException("Could not pack atlas. Reached maxSize=" + maxSize);
            }

            if (tryPackShelf(sorted, size, size)) {
                finalW = size;
                finalH = size;
                break;
            }

            size *= 2;
            size = sanitizeSize(size);
        }

        // Copy pixels using TexturePacker.
        TexturePacker packer = new TexturePacker(finalW, finalH);
        for (Item it : sorted) {
            packer.addRegion(it.src, it.sx, it.sy, it.sw, it.sh, it.dx, it.dy);
        }

        TextureData atlasData = packer.bake();
        Texture atlasTexture = new Texture(atlasData);

        // Build key->region map.
        Map<String, TextureRegion> regions = new LinkedHashMap<>();
        for (Item it : sorted) {
            // Region coordinates are top-left origin pixel space.
            // TextureRegion is assumed to use the same pixel convention.
            TextureRegion region = new TextureRegion(atlasTexture, it.dx, it.dy, it.sw, it.sh);
            regions.put(it.key, region);
        }

        return new Result(atlasData, atlasTexture, Collections.unmodifiableMap(regions));
    }

    /**
     * Attempts unrotated left-to-right shelf placement with configured border and
     * padding. Mutates each item's destination as it proceeds; a failed attempt may
     * leave partial placements that the next attempt overwrites.
     *
     * @param sorted inputs ordered by descending height
     * @param width attempted atlas width
     * @param height attempted atlas height
     * @return true if all rectangles fit
     */
    private boolean tryPackShelf(List<Item> sorted, int width, int height) {
        int x = border;
        int y = border;
        int rowH = 0;

        int usableW = width - border;
        int usableH = height - border;

        for (Item it : sorted) {
            int iw = it.sw;
            int ih = it.sh;

            if (iw <= 0 || ih <= 0) return false;

            // If a single item cannot ever fit.
            if (border + iw > usableW) return false;
            if (border + ih > usableH) return false;

            // New row if needed.
            if (x + iw > usableW) {
                x = border;
                y += rowH + padding;
                rowH = 0;
            }

            // Check height.
            if (y + ih > usableH) return false;

            // Place.
            it.dx = x;
            it.dy = y;

            x += iw + padding;
            rowH = Math.max(rowH, ih);
        }

        return true;
    }

    /**
     * Estimates a square side from total pixel area and rough padding overhead,
     * then accounts for the largest dimensions plus borders and caps at maxSize.
     * This is a heuristic, not a guarantee that shelf packing will succeed.
     *
     * @param sorted pending items
     * @return initial side-length estimate
     */
    private int guessStartSize(List<Item> sorted) {
        long area = 0;
        int maxW = 0;
        int maxH = 0;

        for (Item it : sorted) {
            maxW = Math.max(maxW, it.sw);
            maxH = Math.max(maxH, it.sh);
            area += (long) it.sw * (long) it.sh;
        }

        // Add rough padding overhead.
        int n = sorted.size();
        long padOverhead = (long) (padding) * (long) (padding) * (long) Math.max(0, n - 1);
        area += padOverhead;

        // Square-ish estimate from area.
        int est = (int) Math.ceil(Math.sqrt(Math.max(1L, area)));

        // Ensure at least largest item plus borders.
        est = Math.max(est, maxW + border * 2);
        est = Math.max(est, maxH + border * 2);

        // Clamp.
        est = Math.min(est, maxSize);

        return est;
    }

    /**
     * Raises nonpositive sizes to one and optionally rounds up to a power of two.
     * Does not enforce maxSize.
     *
     * @param size requested dimension
     * @return adjusted dimension
     */
    private int sanitizeSize(int size) {
        if (size <= 0) size = 1;
        if (!powerOfTwo) return size;
        return MathUtils.nextPowerOfTwo(size);
    }

    /**
     * One borrowed source rectangle and its mutable shelf-packing destination.
     * Failed placement attempts may overwrite only a prefix of the item list; every
     * successful build assigns all final destinations before copying pixels.
     * @author Albert Beaupre
     */
    private static final class Item {
        final String key; // Result-map key, checked for uniqueness during build.

        // Source can be TextureData directly or derived from Texture or TextureRegion.
        final TextureData src; // Borrowed CPU image that must remain readable until copying completes.

        // Source rectangle (top-left origin).
        final int sx; // Source rectangle left coordinate.
        final int sy; // Source rectangle top coordinate.
        final int sw; // Positive source rectangle width.
        final int sh; // Positive source rectangle height.

        // Packed destination rectangle (top-left origin).
        int dx; // Destination left coordinate assigned by the latest packing attempt.
        int dy; // Destination top coordinate assigned by the latest packing attempt.

        /**
         * Retains a keyed source rectangle without copying or validating source bounds.
         *
         * @param key nonnull result key
         * @param src nonnull borrowed CPU pixels
         * @param sx source left coordinate
         * @param sy source top coordinate
         * @param sw source width
         * @param sh source height
         * @throws NullPointerException if key or src is null
         */
        Item(String key, TextureData src, int sx, int sy, int sw, int sh) {
            this.key = Objects.requireNonNull(key, "key");
            this.src = Objects.requireNonNull(src, "src");
            this.sx = sx;
            this.sy = sy;
            this.sw = sw;
            this.sh = sh;
        }
    }

    /**
     * Holds one built atlas's CPU data, GPU texture, and unmodifiable key mapping.
     * Region and pixel objects remain mutable; the wrapper supplies no automatic
     * cleanup. Dispose the atlas texture and its separately owned CPU data after
     * all returned regions are no longer needed; this texture borrows the data.
     * @author Albert Beaupre
     */
    public static final class Result {
        private final TextureData atlasData; // Baked CPU pixels associated with the result texture.
        private final Texture atlasTexture; // Result GPU texture whose lifetime is managed by the caller.
        private final Map<String, TextureRegion> regions; // Unmodifiable mapping of keys to mutable atlas-region views.

        /**
         * Stores the resources and mapping produced by one build without copying them.
         *
         * @param atlasData baked CPU pixels
         * @param atlasTexture uploaded texture using those pixels
         * @param regions unmodifiable key mapping
         */
        private Result(TextureData atlasData, Texture atlasTexture, Map<String, TextureRegion> regions) {
            this.atlasData = atlasData;
            this.atlasTexture = atlasTexture;
            this.regions = regions;
        }

        /**
         * Returns the live baked CPU image associated with the result texture.
         * Changing it does not itself upload replacement GPU pixels.
         *
         * @return result CPU data
         */
        public TextureData getAtlasData() {
            return atlasData;
        }

        /**
         * Returns the result's GPU texture, shared by every region. The caller is
         * responsible for disposing it after the atlas is no longer used.
         *
         * @return result texture
         */
        public Texture getAtlasTexture() {
            return atlasTexture;
        }

        /**
         * Looks up a borrowed region by its original input key.
         *
         * @param key atlas input key
         * @return live region, or null when missing
         */
        public TextureRegion getRegion(String key) {
            return regions.get(key);
        }

        /**
         * Returns the unmodifiable key mapping. Its region values remain mutable and
         * share the atlas texture.
         *
         * @return stable key-to-region map
         */
        public Map<String, TextureRegion> getRegions() {
            return regions;
        }
    }
}
