package valthorne.graphics.animation;

import valthorne.graphics.texture.Texture;
import valthorne.graphics.texture.TextureData;
import valthorne.graphics.texture.TextureFilter;
import valthorne.graphics.texture.TextureRegionDrawable;
import valthorne.utility.properties.PropertySet;
import valthorne.utility.properties.PropertyValue;

import java.util.Map;
import java.util.Set;
import java.util.function.IntFunction;

/**
 * Validated CPU animation asset: one decoded image, source rectangles, durations
 * and initial player settings. Loading performs no OpenGL work. Each asAnimation
 * creates an independent player; its frame arrays and timeline are never shared.
 * Configuration parsing and allocations occur only during loading/construction,
 * leaving the existing Animation update and draw paths unchanged.
 *
 * <h2>PropertySet text configuration</h2>
 * <pre>{@code
 * image:string=../images/hero.png
 * frameWidth:integer=32
 * frameHeight:integer=32
 * startFrame:integer=0
 * endFrame:integer=5
 * duration:float=0.6
 * speed:float=1.0
 * playbackMode:string=FORWARD
 * looping:boolean=true
 * }</pre>
 *
 * <p>Sheet coordinates start at the original image's top-left, independent of
 * decoding orientation. Grid indices are zero-based and row-major. startFrame
 * and endFrame select an inclusive range. Alternatively, frames is a comma-separated
 * list of cell indices, including repeats (for example 0,1,2,1). frameWidth and
 * frameHeight default to the whole image. marginX/marginY default to zero and
 * apply to both outer edges; spacingX/spacingY default to zero. columns/rows may
 * restrict the automatically fitted grid.</p>
 *
 * <p>frameDuration defaults to 0.1 seconds per frame. duration instead specifies
 * the total forward-pass duration, divided across selected frames. They are
 * mutually exclusive. frame.N.duration overrides one selected frame's timing;
 * when duration is present, the remaining time is divided among frames without
 * overrides. frame.N.x/y/width/height can override its source rectangle, including
 * irregular atlas regions. N is the position in the selected sequence, not its
 * original sheet index. All frame durations must be positive and finite.</p>
 *
 * <p>For an entirely irregular atlas, set frameCount and provide all four
 * frame.N.x/y/width/height properties for every frame. This explicit-rectangle
 * mode replaces grid settings and range/order selection.</p>
 *
 * <p>Player defaults: playbackMode=FORWARD, speed=1, loopCount=-1 (unlimited),
 * looping=true, paused=false, visible=true and rotation=0 degrees. loopCount=0
 * defaults to non-looping; positive values use Animation's existing loop-limit
 * semantics. initialFrame selects a sequence-relative frame; initialTime seeks
 * in seconds using the player's playback mode; randomStart=true picks a frame.
 * These three start selectors are mutually exclusive. Without one, reverse
 * playback starts at the last selected frame. x/y/width/height optionally assign
 * instance bounds; missing positions default to zero and missing dimensions to
 * the first selected frame's source size. Without bounds, draw coordinates
 * supplied by the caller remain in control.</p>
 *
 * <p>Image settings: flipVertically=true (normal OpenGL orientation) and
 * filter=NEAREST. filter accepts TextureFilter names. Unknown properties and
 * out-of-image rectangles are rejected. Listener callbacks, completed-loop
 * counters, finished state and ping-pong return direction are runtime state,
 * not serializable settings; assign listeners on each constructed player.</p>
 *
 * <h2>Cache image IDs</h2>
 * <p>Replace image with imageID (a nonnegative int) and supply an image resolver
 * when loading. The callback runs once on the loading thread and returns borrowed
 * TextureData in the configured flipVertically orientation. It must not perform
 * OpenGL work. The cache retains ownership, including when loading fails. Do not
 * specify both image and imageID. With Assets, attach the callback using
 * AnimationParameters.withImageResolver or its byte-source resolver factory.</p>
 * <pre>{@code
 * // Configuration contains imageID:integer=42 instead of image:string=...
 * AnimationData data = AnimationData.load(properties, cache::getImageData);
 * }</pre>
 *
 * <h2>Ownership</h2>
 * <p>Encoded-image loader requests own their decoded pixels; cache-resolver
 * requests borrow theirs. dispose releases only owned pixels and prevents
 * further player creation. Existing uploaded players remain
 * valid. asAnimation() owns a new GPU texture, released by Animation.dispose;
 * asAnimation(texture) borrows the caller's same-sized texture and never changes
 * its filter or disposes it. Direct load(properties, pixels) and resolver loads
 * borrow CPU pixels; disposing their AnimationData never disposes cache images.
 * Do not dispose borrowed data or pixels while another thread constructs a
 * player. Create and dispose GPU objects on the graphics thread, after ending
 * batches that reference them.</p>
 *
 * <pre>{@code
 * AnimationData data = AnimationData.load("assets/hero.properties");
 * Animation hero = data.asAnimation(); // graphics thread
 * data.dispose();                    // uploaded player stays valid
 * hero.update(delta);
 * hero.draw(batch, 100, 100, 64, 64);
 * hero.dispose();                    // after the final batch ends
 * }</pre>
 */
public final class AnimationData {

    /**
     * Supported scalar configuration names; frame-specific names are checked separately.
     */
    private static final Set<String> KEYS = Set.of("image", "imageID", "frameWidth", "frameHeight", "marginX", "marginY", "spacingX", "spacingY", "columns", "rows", "startFrame", "endFrame", "frames", "frameCount", "duration", "frameDuration", "speed", "playbackMode", "looping", "loopCount", "paused", "visible", "initialFrame", "initialTime", "randomStart", "x", "y", "width", "height", "rotation", "flipVertically", "filter");

    /**
     * Grid and selection properties incompatible with explicit rectangle sequences.
     */
    private static final Set<String> GRID_KEYS = Set.of("frameWidth", "frameHeight", "marginX", "marginY", "spacingX", "spacingY", "columns", "rows", "startFrame", "endFrame", "frames");

    private final PropertySet properties; // Private configuration snapshot, never exposed directly.
    private final TextureData textureData; // Owned or borrowed decoded image pixels.
    private final AnimationFrameData[] frames; // Immutable source rectangles in playback sequence order.
    private final boolean ownsPixels; // Whether disposal releases the decoded image allocation.
    private boolean disposed; // Prevents constructing players from released loading data.

    /**
     * Snapshots, compiles and validates a configuration against decoded image bounds.
     * Ownership transfers only after construction succeeds.
     *
     * @param properties non-null animation properties
     * @param textureData non-null decoded image
     * @param ownsPixels whether this data owns the image allocation
     */
    AnimationData(PropertySet properties, TextureData textureData, boolean ownsPixels) {
        if (properties == null || textureData == null) throw new IllegalArgumentException("Properties and image cannot be null");
        if (textureData.width() < 1 || textureData.height() < 1) throw new IllegalArgumentException("Image dimensions must be positive");
        this.properties = copy(properties);
        this.textureData = textureData;
        this.ownsPixels = ownsPixels;
        frames = compileFrames(this.properties, textureData.width(), textureData.height());
        validateSettings(this.properties, frames.length);
    }

    /**
     * Loads a text configuration and its linked image from the filesystem.
     *
     * @param path configuration path
     * @return newly owned decoded asset
     */
    public static AnimationData load(String path) {
        return new AnimationLoader().load(AnimationParameters.fromPath(path));
    }

    /**
     * Reads a filesystem config and borrows its image from a caller-owned cache.
     *
     * @param path configuration containing imageID
     * @param imageResolver non-null CPU-image lookup, invoked on the loading thread
     * @return data borrowing the resolved image
     */
    public static AnimationData load(String path, IntFunction<TextureData> imageResolver) {
        return new AnimationLoader().load(AnimationParameters.fromPath(path).withImageResolver(imageResolver));
    }

    /**
     * Decodes a text configuration and its supplied encoded image without GPU work.
     *
     * @param configBytes UTF-8 PropertySet configuration
     * @param imageBytes  encoded linked image
     * @return newly owned decoded asset
     */
    public static AnimationData load(byte[] configBytes, byte[] imageBytes) {
        return new AnimationLoader().load(AnimationParameters.fromBytes(configBytes, imageBytes, "animation-bytes"));
    }

    /**
     * Parses UTF-8 configuration bytes and resolves their imageID through a cache.
     *
     * @param configBytes UTF-8 PropertySet text
     * @param imageResolver non-null lookup returning borrowed decoded pixels
     * @return data borrowing the cache image
     */
    public static AnimationData load(byte[] configBytes, IntFunction<TextureData> imageResolver) {
        return new AnimationLoader().load(AnimationParameters.fromBytes(configBytes, "animation-bytes", imageResolver));
    }

    /**
     * Creates an asset from in-memory properties and borrowed decoded pixels.
     * The properties are copied; the pixels remain owned by the caller. An image
     * property is optional here because the dependency is already supplied.
     *
     * @param properties animation configuration
     * @param pixels     borrowed decoded image, in the configured flipVertically orientation
     * @return validated data without GPU allocation
     */
    public static AnimationData load(PropertySet properties, TextureData pixels) {
        return new AnimationData(properties, pixels, false);
    }

    /**
     * Snapshots configuration and resolves its imageID exactly once. No filename
     * is needed. A null result is rejected; callback exceptions propagate unchanged.
     * Neither successful loading, failed validation nor disposal releases the
     * cache-owned pixels. The cache must keep them alive during player construction.
     *
     * @param properties configuration containing imageID and no image path
     * @param imageResolver non-null lookup returning pixels in the configured orientation
     * @return validated data borrowing the resolved image
     * @throws IllegalArgumentException if the ID is missing/invalid or lookup returns null
     */
    public static AnimationData load(PropertySet properties, IntFunction<TextureData> imageResolver) {
        if (properties == null || imageResolver == null)
            throw new IllegalArgumentException("Properties and image resolver cannot be null");
        PropertySet snapshot = copy(properties);
        int id = imageID(snapshot);
        TextureData pixels = imageResolver.apply(id);
        if (pixels == null) throw new IllegalArgumentException("No image data resolved for imageID " + id);
        return new AnimationData(snapshot, pixels, false);
    }

    /**
     * Validates the cache reference without narrowing fractional or overflowing IDs.
     *
     * @param properties configuration containing a cache ID
     * @return nonnegative image ID
     */
    private static int imageID(PropertySet properties) {
        if (properties.contains("image")) throw new IllegalArgumentException("Use image or imageID, not both");
        int id = integer(properties, "imageID", -1);
        if (id < 0) throw new IllegalArgumentException("imageID must be a nonnegative int");
        return id;
    }

    /**
     * Copies scalar property values rather than nesting their wrapper objects.
     *
     * @param source source collection
     * @return independent collection
     */
    private static PropertySet copy(PropertySet source) {
        PropertySet copy = new PropertySet();
        for (Map.Entry<String, PropertyValue<?>> entry : source.asMap().entrySet())
            copy.set(entry.getKey(), entry.getValue().value());
        return copy;
    }

    /**
     * Converts a property to an exact finite integer without narrowing overflow.
     *
     * @param properties source configuration
     * @param key property name
     * @param fallback value when absent
     * @return exact configured integer
     */
    private static int integer(PropertySet properties, String key, int fallback) {
        if (!properties.contains(key)) return fallback;
        double value = properties.getRequired(key).asDouble();
        if (!Double.isFinite(value) || value != Math.rint(value) || value < Integer.MIN_VALUE || value > Integer.MAX_VALUE)
            throw new IllegalArgumentException(key + " must be an integer in the int range");
        return (int) value;
    }

    /**
     * Reads a finite number, rejecting float overflow at the asset boundary.
     *
     * @param properties source configuration
     * @param key property name
     * @param fallback value when absent
     * @return finite float
     */
    private static float number(PropertySet properties, String key, float fallback) {
        float value = properties.getOrDefault(key, fallback).asFloat();
        if (!Float.isFinite(value)) throw new IllegalArgumentException(key + " must be finite");
        return value;
    }

    /**
     * Reads a boolean using PropertySet's supported boolean conversions.
     *
     * @param properties source configuration
     * @param key property name
     * @param fallback value when absent
     * @return configured flag
     */
    private static boolean flag(PropertySet properties, String key, boolean fallback) {
        return properties.getOrDefault(key, fallback).asBoolean();
    }

    /**
     * Counts complete cells between symmetric margins without integer overflow.
     *
     * @param imageSize image width or height
     * @param frameSize positive cell size
     * @param margin nonnegative outer margin
     * @param spacing nonnegative gap between cells
     * @return positive number of fitting cells
     */
    private static int cells(int imageSize, int frameSize, int margin, int spacing) {
        if (frameSize < 1 || margin < 0 || spacing < 0)
            throw new IllegalArgumentException("Frame sizes must be positive; margins and spacing nonnegative");
        long count = (imageSize - 2L * margin + spacing) / ((long) frameSize + spacing);
        if (count < 1) throw new IllegalArgumentException("No complete frame fits the image grid");
        return (int) count;
    }

    /**
     * Resolves the selected sheet indices, preserving explicit order and repeats.
     *
     * @param properties source configuration
     * @param count available sheet cells
     * @return newly owned sequence indices
     */
    private static int[] selection(PropertySet properties, int count) {
        int[] indices;
        if (properties.contains("frameCount")) {
            for (String key : GRID_KEYS)
                if (properties.contains(key)) throw new IllegalArgumentException("frameCount cannot be combined with " + key);
            int length = integer(properties, "frameCount", 0);
            if (length < 1 || length > Short.MAX_VALUE) throw new IllegalArgumentException("frameCount must be between 1 and 32767");
            indices = new int[length];
            for (int i = 0; i < length; i++) {
                String prefix = "frame." + i + ".";
                if (!properties.contains(prefix + "x") || !properties.contains(prefix + "y") || !properties.contains(prefix + "width") || !properties.contains(prefix + "height"))
                    throw new IllegalArgumentException(prefix + "requires x, y, width and height in explicit rectangle mode");
            }
        } else if (properties.contains("frames")) {
            if (properties.contains("startFrame") || properties.contains("endFrame"))
                throw new IllegalArgumentException("Use frames or startFrame/endFrame, not both");
            String text = properties.getRequired("frames", String.class).value();
            int length = 1;
            for (int i = 0; i < text.length(); i++) if (text.charAt(i) == ',') length++;
            if (length > Short.MAX_VALUE) throw new IllegalArgumentException("Too many animation frames");
            indices = new int[length];
            int start = 0;
            for (int i = 0; i < length; i++) {
                int end = text.indexOf(',', start);
                if (end < 0) end = text.length();
                indices[i] = Integer.parseInt(text.substring(start, end).trim());
                start = end + 1;
            }
        } else {
            int first = integer(properties, "startFrame", 0);
            int last = integer(properties, "endFrame", count - 1);
            if (first < 0 || last < first || last >= count || (long) last - first + 1 > Short.MAX_VALUE)
                throw new IllegalArgumentException("Invalid inclusive startFrame/endFrame range");
            indices = new int[last - first + 1];
            for (int i = 0; i < indices.length; i++) indices[i] = first + i;
        }
        for (int index : indices)
            if (index < 0 || index >= count) throw new IllegalArgumentException("Sheet frame index out of range: " + index);
        return indices;
    }

    /**
     * Rejects misspelled names and overrides that refer to nonexistent sequence frames.
     *
     * @param properties configuration keys to inspect
     * @param count selected sequence size
     */
    private static void validateKeys(PropertySet properties, int count) {
        for (String key : properties.asMap().keySet()) {
            if (KEYS.contains(key)) continue;
            int separator = key.lastIndexOf('.');
            if (!key.startsWith("frame.") || separator <= 6)
                throw new IllegalArgumentException("Unknown animation property: " + key);
            int index = Integer.parseInt(key.substring(6, separator));
            String suffix = key.substring(separator + 1);
            if (index < 0 || index >= count || !(suffix.equals("x") || suffix.equals("y") || suffix.equals("width") || suffix.equals("height") || suffix.equals("duration")))
                throw new IllegalArgumentException("Invalid frame property: " + key);
            if (!key.equals("frame." + index + "." + suffix))
                throw new IllegalArgumentException("Frame indices must use canonical decimal notation: " + key);
        }
    }

    /**
     * Compiles the grid, overrides and frame timing exactly once during loading.
     *
     * @param properties immutable loading snapshot
     * @param width decoded image width
     * @param height decoded image height
     * @return validated ordered CPU frame metadata
     */
    private static AnimationFrameData[] compileFrames(PropertySet properties, int width, int height) {
        int frameWidth = integer(properties, "frameWidth", width);
        int frameHeight = integer(properties, "frameHeight", height);
        int marginX = integer(properties, "marginX", 0);
        int marginY = integer(properties, "marginY", 0);
        int spacingX = integer(properties, "spacingX", 0);
        int spacingY = integer(properties, "spacingY", 0);
        int maxColumns = cells(width, frameWidth, marginX, spacingX);
        int maxRows = cells(height, frameHeight, marginY, spacingY);
        int columns = integer(properties, "columns", maxColumns);
        int rows = integer(properties, "rows", maxRows);
        if (columns < 1 || columns > maxColumns || rows < 1 || rows > maxRows || (long) columns * rows > Integer.MAX_VALUE)
            throw new IllegalArgumentException("columns/rows must fit the image grid");
        int[] indices = selection(properties, columns * rows);
        validateKeys(properties, indices.length);
        if (properties.contains("duration") && properties.contains("frameDuration"))
            throw new IllegalArgumentException("Use duration or frameDuration, not both");
        float duration = number(properties, "duration", 0);
        float defaultDuration = number(properties, "frameDuration", .1f);
        if ((properties.contains("duration") && duration <= 0) || defaultDuration <= 0)
            throw new IllegalArgumentException("Durations must be positive");
        double overrides = 0;
        int overridden = 0;
        for (int i = 0; i < indices.length; i++) {
            String key = "frame." + i + ".duration";
            if (!properties.contains(key)) continue;
            float time = number(properties, key, 0);
            if (time <= 0) throw new IllegalArgumentException(key + " must be positive");
            overrides += time;
            overridden++;
        }
        if (properties.contains("duration")) {
            if (overridden == indices.length) {
                if (Math.abs(overrides - duration) > duration * .00001)
                    throw new IllegalArgumentException("Frame durations must sum to duration");
            } else {
                defaultDuration = (float) ((duration - overrides) / (indices.length - overridden));
                if (defaultDuration <= 0) throw new IllegalArgumentException("duration leaves no time for remaining frames");
            }
        }
        AnimationFrameData[] frames = new AnimationFrameData[indices.length];
        double total = 0;
        for (int i = 0; i < frames.length; i++) {
            String prefix = "frame." + i + ".";
            int x = integer(properties, prefix + "x", (int) (marginX + (long) (indices[i] % columns) * (frameWidth + (long) spacingX)));
            int y = integer(properties, prefix + "y", (int) (marginY + (long) (indices[i] / columns) * (frameHeight + (long) spacingY)));
            int w = integer(properties, prefix + "width", frameWidth);
            int h = integer(properties, prefix + "height", frameHeight);
            frames[i] = new AnimationFrameData(x, y, w, h, number(properties, prefix + "duration", defaultDuration));
            if ((long) x + w > width || (long) y + h > height)
                throw new IllegalArgumentException(prefix + "rectangle exceeds image bounds");
            total += frames[i].duration();
        }
        if (total > Float.MAX_VALUE / 2f) throw new IllegalArgumentException("Animation timeline is too long");
        return frames;
    }

    /**
     * Validates all player settings before allowing GPU object construction.
     *
     * @param properties loading snapshot
     * @param count      selected frame count
     */
    private static void validateSettings(PropertySet properties, int count) {
        if (properties.contains("imageID")) imageID(properties);
        properties.getOrDefault("playbackMode", "FORWARD").asEnum(PlaybackMode.class);
        properties.getOrDefault("filter", "NEAREST").asEnum(TextureFilter.class);
        if (number(properties, "speed", 1) < .000001f)
            throw new IllegalArgumentException("speed must be at least 0.000001");
        int loops = integer(properties, "loopCount", -1);
        if (loops < -1 || (loops == 0 && flag(properties, "looping", false)))
            throw new IllegalArgumentException("loopCount must be -1, zero (non-looping), or positive");
        flag(properties, "looping", loops != 0);
        flag(properties, "paused", false);
        flag(properties, "visible", true);
        flag(properties, "flipVertically", true);
        boolean random = flag(properties, "randomStart", false);
        int selectors = (properties.contains("initialFrame") ? 1 : 0) + (properties.contains("initialTime") ? 1 : 0) + (random ? 1 : 0);
        if (selectors > 1) throw new IllegalArgumentException("Use only one initialFrame, initialTime or randomStart");
        int initial = integer(properties, "initialFrame", 0);
        if (initial < 0 || initial >= count) throw new IllegalArgumentException("initialFrame is outside the selected sequence");
        if (number(properties, "initialTime", 0) < 0) throw new IllegalArgumentException("initialTime cannot be negative");
        number(properties, "x", 0);
        number(properties, "y", 0);
        if (number(properties, "width", 0) < 0 || number(properties, "height", 0) < 0)
            throw new IllegalArgumentException("Bounds dimensions cannot be negative");
        number(properties, "rotation", 0);
        if (properties.contains("image") && properties.getRequired("image", String.class).value().isBlank())
            throw new IllegalArgumentException("image cannot be blank");
    }

    /**
     * Returns the borrowed decoded pixels while this asset remains alive.
     *
     * @return decoded image data; do not dispose it separately when owned here
     */
    public TextureData textureData() {
        ensureAlive();
        return textureData;
    }

    /**
     * Returns independent array storage containing immutable frame descriptions.
     *
     * @return ordered selected frames
     */
    public AnimationFrameData[] frames() {
        return frames.clone();
    }

    /**
     * Returns an editable copy of the original config for creating asset variants.
     *
     * @return independent PropertySet
     */
    public PropertySet properties() {
        return copy(properties);
    }

    /**
     * Uploads an independent GPU sheet and creates its owning player. This must
     * run on the graphics thread. Disposing this data later does not invalidate
     * the uploaded player, whose texture is released by Animation.dispose.
     *
     * @return independent configured player owning one GPU texture
     */
    public Animation asAnimation() {
        ensureAlive();
        Texture texture = textureData.asTexture();
        try {
            texture.setFilter(properties.getOrDefault("filter", "NEAREST").asEnum(TextureFilter.class));
            return createAnimation(texture, true);
        } catch (RuntimeException | Error failure) {
            texture.dispose();
            throw failure;
        }
    }

    /**
     * Creates a player borrowing an existing same-sized, same-orientation sheet.
     * This avoids duplicate GPU uploads when many players share a sprite sheet.
     * The caller controls texture filtering and lifetime.
     *
     * @param texture live texture matching this asset's decoded image
     * @return independent player that never disposes the supplied texture
     */
    public Animation asAnimation(Texture texture) {
        ensureAlive();
        if (texture == null || texture.getWidth() != textureData.width() || texture.getHeight() != textureData.height())
            throw new IllegalArgumentException("Texture must match animation image dimensions");
        return createAnimation(texture, false);
    }

    /**
     * Builds independent GPU region wrappers and applies initial playback state.
     *
     * @param texture backing sheet
     * @param owned whether player disposal releases the sheet
     * @return configured player
     */
    private Animation createAnimation(Texture texture, boolean owned) {
        boolean flip = flag(properties, "flipVertically", true);
        AnimationFrame[] runtime = new AnimationFrame[frames.length];
        for (int i = 0; i < runtime.length; i++) {
            AnimationFrameData frame = frames[i];
            int y = flip ? textureData.height() - frame.y() - frame.height() : frame.y();
            runtime[i] = new AnimationFrame(new TextureRegionDrawable(texture, frame.x(), y, frame.width(), frame.height()), frame.duration());
        }
        PlaybackMode mode = properties.getOrDefault("playbackMode", "FORWARD").asEnum(PlaybackMode.class);
        Animation animation = owned ? new Animation(texture, mode, runtime) : new Animation(mode, runtime);
        animation.setSpeed(number(properties, "speed", 1));
        int loops = integer(properties, "loopCount", -1);
        animation.loopCount(loops);
        animation.setLooping(flag(properties, "looping", loops != 0));
        if (properties.contains("initialFrame")) animation.setFrame(integer(properties, "initialFrame", 0));
        else if (properties.contains("initialTime")) animation.time(number(properties, "initialTime", 0));
        else if (flag(properties, "randomStart", false)) animation.setRandomStart();
        animation.setPaused(flag(properties, "paused", false));
        animation.setVisible(flag(properties, "visible", true));
        animation.setRotation(number(properties, "rotation", 0));
        if (properties.contains("x") || properties.contains("y") || properties.contains("width") || properties.contains("height"))
            animation.setBounds(number(properties, "x", 0), number(properties, "y", 0), number(properties, "width", frames[0].width()), number(properties, "height", frames[0].height()));
        return animation;
    }

    /**
     * Rejects player construction or pixel access after decoded data was released.
     */
    private void ensureAlive() {
        if (disposed) throw new IllegalStateException("Animation data has been disposed");
    }

    /**
     * Releases owned decoded pixels exactly once, without touching existing GPU
     * players or borrowed caller pixels. Do not race this with player construction.
     */
    public void dispose() {
        if (disposed) return;
        disposed = true;
        if (ownsPixels) textureData.dispose();
    }
}
