package valthorne.graphics.texture;

import valthorne.math.MathUtils;

import java.util.*;

/**
 * Builds a GPU {@link Texture} atlas and {@link TextureRegion} mappings using {@link TexturePacker}.
 *
 * <h2>What this class does</h2>
 * <ul>
 *     <li>Accepts {@link Texture}, {@link TextureData}, or {@link TextureRegion} inputs.</li>
 *     <li>Determines an atlas size that can fit all regions.</li>
 *     <li>Finds (x,y) placements and then copies pixels via {@link TexturePacker}.</li>
 *     <li>Creates a final atlas {@link Texture} and returns atlas {@link TextureRegion}s by key.</li>
 * </ul>
 *
 * <h2>Packing algorithm</h2>
 * <ul>
 *     <li>Uses a simple "shelf" packer.</li>
 *     <li>Sorts by height descending.</li>
 *     <li>Places left-to-right in rows.</li>
 *     <li>Starts a new row when needed.</li>
 * </ul>
 *
 * <h2>Coordinates</h2>
 * <ul>
 *     <li>All placements are expressed in <b>top-left</b> origin pixel coordinates.</li>
 *     <li>This matches {@link TexturePacker}'s expectations.</li>
 *     <li>{@link TexturePacker} internally handles bottom-up buffer conversion.</li>
 * </ul>
 *
 * <h2>Requirements</h2>
 * <ul>
 *     <li>{@link Texture} inputs must have CPU-side pixels available via {@link Texture#getData()}.</li>
 *     <li>{@link TextureRegion} inputs require a non-null backing {@link Texture} with CPU pixels.</li>
 * </ul>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * TextureAtlas atlas = new TextureAtlas()
 *     .setPadding(2)
 *     .setBorder(2)
 *     .setPowerOfTwo(true)
 *     .setMaxSize(4096);
 *
 * atlas.add("player", new Texture("assets/player.png"));
 * atlas.add("ui_button", new TextureRegion(new Texture("assets/ui.png"), 16, 16, 64, 32));
 *
 * TextureAtlas.Result result = atlas.build();
 *
 * Texture atlasTexture = result.getAtlasTexture();
 * TextureRegion player = result.getRegion("player");
 * }</pre>
 *
 * @author Albert Beaupre
 * @since February 14th, 2026
 */
public final class TextureAtlas {

    /**
     * One pending input to pack.
     * This stores the source rectangle to copy and its final packed location.
     */
    private static final class Item {
        final String key;

        // Source can be TextureData directly or derived from Texture or TextureRegion.
        final TextureData src;

        // Source rectangle (top-left origin).
        final int sx;
        final int sy;
        final int sw;
        final int sh;

        // Packed destination rectangle (top-left origin).
        int dx;
        int dy;

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
     * Final build output.
     * Holds the atlas texture and a stable mapping of keys to atlas regions.
     */
    public static final class Result {
        private final TextureData atlasData;
        private final Texture atlasTexture;
        private final Map<String, TextureRegion> regions;

        private Result(TextureData atlasData, Texture atlasTexture, Map<String, TextureRegion> regions) {
            this.atlasData = atlasData;
            this.atlasTexture = atlasTexture;
            this.regions = regions;
        }

        /**
         * Returns the atlas CPU data.
         *
         * @return atlas CPU pixels
         */
        public TextureData getAtlasData() {
            return atlasData;
        }

        /**
         * Returns the atlas GPU texture.
         *
         * @return atlas texture
         */
        public Texture getAtlasTexture() {
            return atlasTexture;
        }

        /**
         * Gets a region by key.
         *
         * @param key key used in {@link TextureAtlas#add(String, Texture)},
         *            {@link TextureAtlas#add(String, TextureData)},
         *            or {@link TextureAtlas#add(String, TextureRegion)}
         * @return region, or null if missing
         */
        public TextureRegion getRegion(String key) {
            return regions.get(key);
        }

        /**
         * Returns all regions.
         * This map is unmodifiable.
         *
         * @return map of key -> atlas region
         */
        public Map<String, TextureRegion> getRegions() {
            return regions;
        }
    }

    private final List<Item> items = new ArrayList<>();

    private int padding = 0;       // Space between regions.
    private int border = 0;        // Space around the entire atlas content.
    private boolean powerOfTwo = false;
    private int maxSize = 4096;
    private int startSize = 256;

    /**
     * Sets padding between packed regions.
     *
     * @param padding pixels (>= 0)
     * @return this
     */
    public TextureAtlas setPadding(int padding) {
        if (padding < 0) throw new IllegalArgumentException("padding must be >= 0");
        this.padding = padding;
        return this;
    }

    /**
     * Sets border padding around packed content.
     *
     * @param border pixels (>= 0)
     * @return this
     */
    public TextureAtlas setBorder(int border) {
        if (border < 0) throw new IllegalArgumentException("border must be >= 0");
        this.border = border;
        return this;
    }

    /**
     * Enables or disables power-of-two atlas sizing.
     *
     * @param powerOfTwo true to round up width/height to next power-of-two
     * @return this
     */
    public TextureAtlas setPowerOfTwo(boolean powerOfTwo) {
        this.powerOfTwo = powerOfTwo;
        return this;
    }

    /**
     * Sets the maximum atlas size.
     *
     * @param maxSize pixels (must be > 0)
     * @return this
     */
    public TextureAtlas setMaxSize(int maxSize) {
        if (maxSize <= 0) throw new IllegalArgumentException("maxSize must be > 0");
        this.maxSize = maxSize;
        return this;
    }

    /**
     * Sets the starting size to attempt when auto-sizing.
     * This value will be increased until all items fit.
     *
     * @param startSize pixels (must be > 0)
     * @return this
     */
    public TextureAtlas setStartSize(int startSize) {
        if (startSize <= 0) throw new IllegalArgumentException("startSize must be > 0");
        this.startSize = startSize;
        return this;
    }

    /**
     * Adds an entire {@link Texture} by key.
     *
     * @param key     unique key
     * @param texture source texture (must have CPU data)
     * @return this
     */
    public TextureAtlas add(String key, Texture texture) {
        Objects.requireNonNull(texture, "texture");
        TextureData data = texture.getData();
        if (data == null) throw new NullPointerException("texture.getData() returned null for key=" + key);
        return add(key, data, 0, 0, data.width(), data.height());
    }

    /**
     * Adds an entire {@link TextureData} by key.
     *
     * @param key  unique key
     * @param data source CPU pixels
     * @return this
     */
    public TextureAtlas add(String key, TextureData data) {
        Objects.requireNonNull(data, "data");
        return add(key, data, 0, 0, data.width(), data.height());
    }

    /**
     * Adds a {@link TextureRegion} by key.
     *
     * @param key    unique key
     * @param region source region
     * @return this
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
     * Adds a source rectangle from {@link TextureData} by key.
     *
     * @param key unique key
     * @param src source data
     * @param sx  source X (top-left origin)
     * @param sy  source Y (top-left origin)
     * @param sw  width (must be > 0)
     * @param sh  height (must be > 0)
     * @return this
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
     * Clears all pending items.
     *
     * @return this
     */
    public TextureAtlas clear() {
        items.clear();
        return this;
    }

    /**
     * Builds the atlas.
     *
     * <p>This method:</p>
     * <ul>
     *     <li>Chooses an atlas size (auto-growing until it fits).</li>
     *     <li>Packs items using the shelf algorithm.</li>
     *     <li>Copies pixels via {@link TexturePacker}.</li>
     *     <li>Creates a new atlas {@link Texture} from the baked {@link TextureData}.</li>
     *     <li>Creates atlas {@link TextureRegion}s for each key.</li>
     * </ul>
     *
     * @return build result
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
     * Attempts to pack all items into width/height using shelves.
     * Writes packed positions into each {@link Item}.
     *
     * @param sorted sorted items
     * @param width  atlas width
     * @param height atlas height
     * @return true if all items fit
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
     * Guesses a good starting size to reduce growth iterations.
     *
     * @param sorted items
     * @return suggested size
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
     * Applies power-of-two rules if enabled.
     *
     * @param size requested size
     * @return sanitized size
     */
    private int sanitizeSize(int size) {
        if (size <= 0) size = 1;
        if (!powerOfTwo) return size;
        return MathUtils.nextPowerOfTwo(size);
    }
}