package valthorne.graphics.particle;

import valthorne.graphics.Color;
import valthorne.graphics.texture.Texture;
import valthorne.graphics.texture.TextureData;
import valthorne.math.physics.PhysicsWorld2D;
import valthorne.utility.properties.PropertySet;
import valthorne.utility.properties.PropertyValue;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.IntFunction;

/**
 * Validated particle-effect configuration loaded from a UTF-8 PropertySet file.
 * Loading is CPU-only. asEmitter creates independent mutable emitter settings;
 * asParticleSystem(texture) constructs the existing instanced GPU renderer on
 * the graphics thread. Textures and optional physics worlds remain caller-owned.
 * Each factory creates fresh state; no live particles, shape instances, colors,
 * emission accumulators or burst progress are shared between systems.
 *
 * <pre>{@code
 * // sparks.properties:
 * // maxParticles:integer=512
 * // emissionRate:float=120
 * // lifeMin:float=0.3
 * // lifeMax:float=0.8
 * // speedMin:float=40
 * // speedMax:float=180
 * // shape:string=CIRCLE
 * // radius:float=12
 * // startColor:string=1,0.8,0.2,1
 * // endColor:string=1,0.2,0,0
 * ParticleData data = ParticleData.load("assets/sparks.properties");
 * ParticleSystem sparks = data.asParticleSystem(texture);
 * sparks.update(delta);
 * sparks.draw();
 * sparks.dispose(); // releases its buffers/shader/bodies, not texture or world
 * data.dispose();
 * }</pre>
 *
 * <h2>Emitter properties and defaults</h2>
 * <p>emissionRate=60; lifeMin=0.3, lifeMax=0.8 seconds; speedMin=20,
 * speedMax=120 units/second; angleMinDeg=0, angleMaxDeg=360;
 * gravityY=-200, windX=0; baseWidth=16, baseHeight=16; startScale=1,
 * endScale=0; startRotMinDeg=0, startRotMaxDeg=0;
 * rotSpeedMinDegPerSec=0, rotSpeedMaxDegPerSec=0. startColor=1,1,1,1
 * and endColor=1,1,1,0 use four comma-separated RGBA channels in [0,1].
 * Ranges must be ordered and all numbers finite; sizes, scales, rates and speeds
 * are nonnegative, and particle lifetimes are positive.</p>
 *
 * <h2>Spawn shapes</h2>
 * <p>shape defaults to POINT. CIRCLE uses radius=16 and edgeOnly=false;
 * BOX and RECT_EDGE use halfWidth=16, halfHeight=16; LINE uses
 * lineX0=-16, lineY0=0, lineX1=16, lineY1=0; CONE uses radius=16,
 * directionDeg=90, spreadDeg=45, edgeOnly=false; RING uses innerRadius=8,
 * outerRadius=16; RADIAL_BURST uses radius=16; SPIRAL uses radius=16, turns=3.
 * Spawn angles affect offsets; emitter angle ranges independently control velocity.
 * Custom distributors can be assigned on the resulting emitter in code.</p>
 *
 * <h2>System properties</h2>
 * <p>maxParticles=1024; x=0, y=0; initialBurst=0, burstSpread=0.1 seconds;
 * optional burstRate overrides the automatically calculated release rate;
 * renderInterval=0, maxDrawPerFrame=2147483647; sizeScaleX=1, sizeScaleY=1;
 * discSoftness=-1 (image sampling; [0,1] selects analytic discs);
 * externalMotion=false. regionLeft/Top/Right/Bottom are optional, all-or-none
 * texture-space pixel edges matching ParticleEmitter.setRegion. Reversed edges
 * flip sampling. They must lie inside the supplied texture. Runtime counters,
 * active particles and integration clocks are never restored from a config.</p>
 *
 * <h2>Physics</h2>
 * <p>physics.enabled=false. Enabling requires asParticleSystem(texture, world)
 * with an open caller-owned world and externalMotion=false. Other keys are
 * physics.maxBodies=256, physics.shape=CIRCLE (or BOX), physics.colliderScale=1,
 * physics.mass=0.1, physics.friction=0.3, physics.restitution=0.5,
 * physics.linearDamping=0.05, physics.angularDamping=0.05,
 * physics.gravityFactor=1, physics.fixedRotation=false, physics.continuous=true,
 * physics.sensor=false. The renderer bounds native slots by particle capacity.
 * The caller steps the shared world; emitter gravity/wind only affect particles
 * without bodies. physicsSettings returns a fresh builder for manual attachment.</p>
 *
 * <h2>Images and ownership</h2>
 * <p>No image reference is required: a texture can be supplied by gameplay code.
 * Alternatively image names a linked file relative to the config, or imageID is
 * a nonnegative int resolved with a supplied IntFunction. flipVertically=true
 * controls encoded image decoding. File/encoded-image data owns its CPU pixels;
 * resolver/direct pixels are borrowed, including on failures. dispose releases
 * only owned pixels and prevents further factory use. Existing systems remain
 * valid. Texture filtering and resource lifetimes stay with the caller. Loading
 * and factories never allocate an owned GPU texture.</p>
 */
public final class ParticleData {
    /**
     * Supported config names; unknown keys are rejected to expose typing mistakes.
     */
    private static final Set<String> KEYS = Set.of("image", "imageID", "flipVertically", "maxParticles", "emissionRate", "lifeMin", "lifeMax", "speedMin", "speedMax", "angleMinDeg", "angleMaxDeg", "gravityY", "windX", "baseWidth", "baseHeight", "startScale", "endScale", "startRotMinDeg", "startRotMaxDeg", "rotSpeedMinDegPerSec", "rotSpeedMaxDegPerSec", "startColor", "endColor", "shape", "radius", "edgeOnly", "halfWidth", "halfHeight", "lineX0", "lineY0", "lineX1", "lineY1", "directionDeg", "spreadDeg", "innerRadius", "outerRadius", "turns", "regionLeft", "regionTop", "regionRight", "regionBottom", "x", "y", "initialBurst", "burstSpread", "burstRate", "renderInterval", "maxDrawPerFrame", "sizeScaleX", "sizeScaleY", "discSoftness", "externalMotion", "physics.enabled", "physics.maxBodies", "physics.shape", "physics.colliderScale", "physics.mass", "physics.friction", "physics.restitution", "physics.linearDamping", "physics.angularDamping", "physics.gravityFactor", "physics.fixedRotation", "physics.continuous", "physics.sensor");

    private final PropertySet properties; // Private validated scalar snapshot.
    private final TextureData pixels; // Optional owned or borrowed decoded dependency.
    private final boolean ownsPixels; // Whether disposal releases decoded native pixels.
    private boolean disposed; // Prevents further creation after disposal.

    /**
     * Copies and validates the effect without creating a GPU system or physics world.
     * Ownership transfers only when construction succeeds.
     *
     * @param properties source settings
     * @param pixels optional decoded image
     * @param ownsPixels whether decoded image ownership transfers
     */
    ParticleData(PropertySet properties, TextureData pixels, boolean ownsPixels) {
        if (properties == null) throw new IllegalArgumentException("Properties cannot be null");
        if (pixels != null && (pixels.width() < 1 || pixels.height() < 1))
            throw new IllegalArgumentException("Image dimensions must be positive");
        this.properties = copy(properties);
        this.pixels = pixels;
        this.ownsPixels = ownsPixels;
        validate();
    }

    /**
     * Loads a file and its optional relative image on the calling thread.
     * @param path PropertySet text file
     * @return validated CPU asset
     */
    public static ParticleData load(String path) {
        return new ParticleLoader().load(ParticleParameters.fromPath(path));
    }

    /**
     * Loads a file whose imageID is resolved through a caller-owned cache.
     * @param path PropertySet text file
     * @param imageResolver borrowed CPU-image lookup
     * @return data borrowing the resolved pixels
     */
    public static ParticleData load(String path, IntFunction<TextureData> imageResolver) {
        return new ParticleLoader().load(ParticleParameters.fromPath(path).withImageResolver(imageResolver));
    }

    /**
     * Loads UTF-8 config bytes without an encoded image dependency.
     * @param configBytes PropertySet text
     * @return validated effect settings
     */
    public static ParticleData load(byte[] configBytes) {
        return new ParticleLoader().load(ParticleParameters.fromBytes(configBytes, "particle-bytes"));
    }

    /**
     * Loads UTF-8 config bytes and an encoded image referenced by image.
     * @param configBytes PropertySet text
     * @param imageBytes encoded linked image
     * @return data owning the decoded pixels
     */
    public static ParticleData load(byte[] configBytes, byte[] imageBytes) {
        return new ParticleLoader().load(ParticleParameters.fromBytes(configBytes, imageBytes, "particle-bytes"));
    }

    /**
     * Loads UTF-8 config bytes and borrows pixels resolved through imageID.
     * @param configBytes PropertySet text
     * @param imageResolver non-null CPU lookup
     * @return data borrowing cache pixels
     */
    public static ParticleData load(byte[] configBytes, IntFunction<TextureData> imageResolver) {
        return new ParticleLoader().load(ParticleParameters.fromBytes(configBytes, "particle-bytes", imageResolver));
    }

    /**
     * Snapshots in-memory settings; optional image metadata is not resolved here.
     * @param properties source settings
     * @return validated config-only data
     */
    public static ParticleData load(PropertySet properties) {
        return new ParticleData(properties, null, false);
    }

    /**
     * Snapshots settings while borrowing caller-owned decoded pixels.
     * @param properties source settings
     * @param pixels non-null decoded dependency
     * @return asset that never releases the borrowed pixels
     */
    public static ParticleData load(PropertySet properties, TextureData pixels) {
        if (pixels == null) throw new IllegalArgumentException("Image data cannot be null");
        return new ParticleData(properties, pixels, false);
    }

    /**
     * Snapshots settings and resolves imageID exactly once on the calling thread.
     * Callback failures propagate; no callback result is ever disposed here.
     * With Assets the callback runs on a worker and must support that access.
     * @param properties settings containing imageID and no image path
     * @param imageResolver non-null lookup returning borrowed decoded pixels
     * @return data borrowing cache pixels
     */
    public static ParticleData load(PropertySet properties, IntFunction<TextureData> imageResolver) {
        if (properties == null || imageResolver == null) throw new IllegalArgumentException("Properties and resolver cannot be null");
        PropertySet snapshot = copy(properties);
        int id = imageID(snapshot);
        TextureData pixels = imageResolver.apply(id);
        if (pixels == null) throw new IllegalArgumentException("No image data resolved for imageID " + id);
        return new ParticleData(snapshot, pixels, false);
    }

    /**
     * Copies scalar values instead of retaining caller-owned property wrappers.
     * @param source properties to copy
     * @return independent collection
     */
    private static PropertySet copy(PropertySet source) {
        PropertySet result = new PropertySet();
        for (Map.Entry<String, PropertyValue<?>> entry : source.asMap().entrySet()) result.set(entry.getKey(), entry.getValue().value());
        return result;
    }

    /**
     * Reads a finite numeric property and checks its inclusive allowed range.
     * @param key setting name
     * @param fallback value when absent
     * @param min inclusive minimum
     * @param max inclusive maximum
     * @return validated float
     */
    private float number(String key, float fallback, float min, float max) {
        float value = properties.getOrDefault(key, fallback).asFloat();
        if (!Float.isFinite(value) || value < min || value > max) throw new IllegalArgumentException(key + " is outside its finite range");
        return value;
    }

    /**
     * Reads an exact integer without narrowing or fractional truncation.
     * @param source source settings
     * @param key property name
     * @param fallback value when absent
     * @param min inclusive minimum
     * @param max inclusive maximum
     * @return validated integer
     */
    private static int integer(PropertySet source, String key, int fallback, int min, int max) {
        double value = source.getOrDefault(key, fallback).asDouble();
        if (!Double.isFinite(value) || value != Math.rint(value) || value < min || value > max)
            throw new IllegalArgumentException(key + " must be an integer between " + min + " and " + max);
        return (int) value;
    }

    /**
     * Reads a boolean using PropertySet's accepted boolean conversions.
     * @param key setting name
     * @param fallback value when absent
     * @return configured flag
     */
    private boolean flag(String key, boolean fallback) {
        return properties.getOrDefault(key, fallback).asBoolean();
    }

    /**
     * Reads a case-insensitive mode name without locale-dependent matching.
     * @param key property name
     * @param fallback default name
     * @return normalized mode
     */
    private String mode(String key, String fallback) {
        return properties.getOrDefault(key, fallback).asString().trim().toUpperCase(Locale.ROOT);
    }

    /**
     * Reads a finite signed value for angles, positions or accelerations.
     * @param key property name
     * @param fallback value when absent
     * @return finite signed value
     */
    private float signed(String key, float fallback) {
        return number(key, fallback, -Float.MAX_VALUE, Float.MAX_VALUE);
    }

    /**
     * Parses exactly four comma-separated color channels during construction only.
     * @param key color property name
     * @param fallback default RGBA string
     * @return fresh mutable color
     */
    private Color color(String key, String fallback) {
        String text = properties.getOrDefault(key, fallback).asString();
        float[] channels = new float[4];
        int start = 0;
        for (int i = 0; i < 4; i++) {
            int end = text.indexOf(',', start);
            if ((i < 3 && end < 0) || (i == 3 && end >= 0)) throw new IllegalArgumentException(key + " requires four RGBA channels");
            if (end < 0) end = text.length();
            float value = Float.parseFloat(text.substring(start, end).trim());
            if (!Float.isFinite(value) || value < 0 || value > 1) throw new IllegalArgumentException(key + " channels must be between zero and one");
            channels[i] = value;
            start = end + 1;
        }
        return new Color(channels[0], channels[1], channels[2], channels[3]);
    }

    /**
     * Validates a cache identifier before invoking application code.
     * @param properties cache-backed settings
     * @return nonnegative int identifier
     */
    private static int imageID(PropertySet properties) {
        if (properties.contains("image") || !properties.contains("imageID")) throw new IllegalArgumentException("Expected imageID without image");
        return integer(properties, "imageID", -1, 0, Integer.MAX_VALUE);
    }

    /**
     * Creates a fresh built-in spawn distributor and validates its dimensions.
     * @return independent spawn strategy
     */
    private SpawnDistributor shape() {
        float radius = number("radius", 16, 0, Float.MAX_VALUE);
        float halfWidth = number("halfWidth", 16, 0, Float.MAX_VALUE);
        float halfHeight = number("halfHeight", 16, 0, Float.MAX_VALUE);
        float inner = number("innerRadius", 8, 0, Float.MAX_VALUE);
        float outer = number("outerRadius", 16, inner, Float.MAX_VALUE);
        float direction = signed("directionDeg", 90);
        float spread = number("spreadDeg", 45, 0, 360);
        float turns = number("turns", 3, 0, Float.MAX_VALUE);
        float x0 = signed("lineX0", -16);
        float y0 = signed("lineY0", 0);
        float x1 = signed("lineX1", 16);
        float y1 = signed("lineY1", 0);
        boolean edge = flag("edgeOnly", false);
        return switch (mode("shape", "POINT")) {
            case "POINT" -> new PointSpawnDistributor();
            case "CIRCLE" -> new CircleSpawnDistributor(radius, edge);
            case "BOX" -> new BoxSpawnDistributor(halfWidth, halfHeight);
            case "LINE" -> new LineSpawnDistributor(x0, y0, x1, y1);
            case "CONE" -> new ConeSpawnDistributor(direction, spread, radius, edge);
            case "RING" -> new RingSpawnDistributor(inner, outer);
            case "RECT_EDGE" -> new RectEdgeSpawnDistributor(halfWidth, halfHeight);
            case "RADIAL_BURST" -> new RadialBurstSpawnDistributor(radius);
            case "SPIRAL" -> new SpiralSpawnDistributor(radius, turns);
            default -> throw new IllegalArgumentException("Unknown particle shape");
        };
    }

    /**
     * Applies validated ranges and appearance to one independent emitter.
     * @param emitter mutable target
     */
    private void configureEmitter(ParticleEmitter emitter) {
        float lifeMin = number("lifeMin", .3f, Float.MIN_VALUE, Float.MAX_VALUE);
        float speedMin = number("speedMin", 20, 0, Float.MAX_VALUE);
        float angleMin = signed("angleMinDeg", 0);
        float rotationMin = signed("startRotMinDeg", 0);
        float rotationSpeedMin = signed("rotSpeedMinDegPerSec", 0);
        emitter.setEmissionRate(number("emissionRate", 60, 0, Float.MAX_VALUE))
                .setLifetime(lifeMin, number("lifeMax", .8f, lifeMin, Float.MAX_VALUE))
                .setVelocity(speedMin, number("speedMax", 120, speedMin, Float.MAX_VALUE))
                .setAngleDeg(angleMin, number("angleMaxDeg", 360, angleMin, Float.MAX_VALUE))
                .setGravity(0, signed("gravityY", -200))
                .setWind(signed("windX", 0), 0)
                .setBaseSize(number("baseWidth", 16, 0, Float.MAX_VALUE), number("baseHeight", 16, 0, Float.MAX_VALUE))
                .setStartEndScale(number("startScale", 1, 0, Float.MAX_VALUE), number("endScale", 0, 0, Float.MAX_VALUE))
                .setStartEndRotation(rotationMin, number("startRotMaxDeg", 0, rotationMin, Float.MAX_VALUE))
                .setRotationSpeed(rotationSpeedMin, number("rotSpeedMaxDegPerSec", 0, rotationSpeedMin, Float.MAX_VALUE))
                .setStartEndColor(color("startColor", "1,1,1,1"), color("endColor", "1,1,1,0"))
                .setShape(shape());
        if (properties.contains("regionLeft")) emitter.setRegion(signed("regionLeft", 0), signed("regionTop", 0), signed("regionRight", 0), signed("regionBottom", 0));
    }

    /**
     * Checks texture-space region edges against a known source size.
     * @param width image width
     * @param height image height
     */
    private void validateRegion(float width, float height) {
        if (!properties.contains("regionLeft")) return;
        float left = number("regionLeft", 0, 0, width);
        float top = number("regionTop", 0, 0, height);
        float right = number("regionRight", 0, 0, width);
        float bottom = number("regionBottom", 0, 0, height);
        if (left == right || top == bottom) throw new IllegalArgumentException("Particle region cannot have zero area");
    }

    /**
     * Rejects unknown names and invalid ranges before any runtime system allocation.
     */
    private void validate() {
        for (String key : properties.asMap().keySet())
            if (!KEYS.contains(key)) throw new IllegalArgumentException("Unknown particle property: " + key);
        if (properties.contains("imageID")) imageID(properties);
        if (properties.contains("image") && properties.getRequired("image", String.class).value().isBlank())
            throw new IllegalArgumentException("image cannot be blank");
        flag("flipVertically", true);
        integer(properties, "maxParticles", 1024, 1, Integer.MAX_VALUE / 40);
        integer(properties, "initialBurst", 0, 0, Integer.MAX_VALUE);
        integer(properties, "maxDrawPerFrame", Integer.MAX_VALUE, 1, Integer.MAX_VALUE);
        signed("x", 0);
        signed("y", 0);
        number("burstSpread", .1f, 0, Float.MAX_VALUE);
        number("burstRate", 0, 0, Float.MAX_VALUE);
        number("renderInterval", 0, 0, Float.MAX_VALUE);
        number("sizeScaleX", 1, Float.MIN_VALUE, Float.MAX_VALUE);
        number("sizeScaleY", 1, Float.MIN_VALUE, Float.MAX_VALUE);
        number("discSoftness", -1, -1, 1);
        boolean physics = flag("physics.enabled", false);
        if (flag("externalMotion", false) && physics)
            throw new IllegalArgumentException("External motion and particle physics cannot both own motion");
        int edges = (properties.contains("regionLeft") ? 1 : 0) + (properties.contains("regionTop") ? 1 : 0) + (properties.contains("regionRight") ? 1 : 0) + (properties.contains("regionBottom") ? 1 : 0);
        if (edges != 0 && edges != 4) throw new IllegalArgumentException("Supply all four particle region edges");
        validateRegion(pixels == null ? Float.MAX_VALUE : pixels.width(), pixels == null ? Float.MAX_VALUE : pixels.height());
        configureEmitter(new ParticleEmitter());
        physicsSettings();
    }

    /**
     * Creates independent validated native-physics settings without creating bodies.
     * @return fresh mutable settings, also usable when physics.enabled is false
     */
    public ParticlePhysicsSettings physicsSettings() {
        ensureAlive();
        ParticlePhysicsSettings settings = new ParticlePhysicsSettings()
                .maxBodies(integer(properties, "physics.maxBodies", 256, 1, Integer.MAX_VALUE))
                .colliderScale(number("physics.colliderScale", 1, Float.MIN_VALUE, Float.MAX_VALUE))
                .mass(number("physics.mass", .1f, Float.MIN_VALUE, Float.MAX_VALUE))
                .friction(number("physics.friction", .3f, 0, Float.MAX_VALUE))
                .restitution(number("physics.restitution", .5f, 0, 1))
                .damping(number("physics.linearDamping", .05f, 0, 1), number("physics.angularDamping", .05f, 0, 1))
                .gravityFactor(signed("physics.gravityFactor", 1))
                .fixedRotation(flag("physics.fixedRotation", false))
                .continuous(flag("physics.continuous", true))
                .sensor(flag("physics.sensor", false));
        switch (mode("physics.shape", "CIRCLE")) {
            case "CIRCLE" -> settings.circle();
            case "BOX" -> settings.box();
            default -> throw new IllegalArgumentException("physics.shape must be CIRCLE or BOX");
        }
        return settings;
    }

    /**
     * Creates fresh emitter state without graphics or native physics allocation.
     * @return independently mutable emitter
     */
    public ParticleEmitter asEmitter() {
        ensureAlive();
        ParticleEmitter emitter = new ParticleEmitter();
        configureEmitter(emitter);
        return emitter;
    }

    /**
     * Constructs an instanced GPU system borrowing the supplied texture. An effect
     * with physics.enabled requires the overload that also supplies a world.
     * @param texture caller-owned particle texture
     * @return independently simulated system owning only its renderer resources
     */
    public ParticleSystem asParticleSystem(Texture texture) {
        return asParticleSystem(texture, null);
    }

    /**
     * Constructs a configured runtime system on the graphics thread. Optional
     * physics borrows the supplied open world; callers remain responsible for
     * stepping it. The system's normal dispose releases buffers/shader/bodies,
     * never the texture or world. Factory failures clean up constructed resources.
     * @param texture live caller-owned texture
     * @param world open caller-owned world, required only when physics.enabled
     * @return independently configured runtime system
     */
    public ParticleSystem asParticleSystem(Texture texture, PhysicsWorld2D world) {
        ensureAlive();
        if (texture == null) throw new IllegalArgumentException("Texture cannot be null");
        if (pixels != null && (texture.getWidth() != pixels.width() || texture.getHeight() != pixels.height()))
            throw new IllegalArgumentException("Texture must match the loaded image dimensions");
        validateRegion(texture.getWidth(), texture.getHeight());
        boolean physics = flag("physics.enabled", false);
        if (physics && (world == null || world.isClosed())) throw new IllegalArgumentException("Particle physics requires an open world");
        ParticleSystem system = new ParticleSystem(texture, integer(properties, "maxParticles", 1024, 1, Integer.MAX_VALUE / 40));
        try {
            configureEmitter(system.getEmitter());
            system.setPosition(signed("x", 0), signed("y", 0));
            system.setExternalMotion(flag("externalMotion", false));
            system.setRenderInterval(number("renderInterval", 0, 0, Float.MAX_VALUE));
            system.setMaxDrawPerFrame(integer(properties, "maxDrawPerFrame", Integer.MAX_VALUE, 1, Integer.MAX_VALUE));
            system.setSizeScale(number("sizeScaleX", 1, Float.MIN_VALUE, Float.MAX_VALUE), number("sizeScaleY", 1, Float.MIN_VALUE, Float.MAX_VALUE));
            system.setDiscSoftness(number("discSoftness", -1, -1, 1));
            if (physics) system.enablePhysics(world, physicsSettings());
            system.burst(integer(properties, "initialBurst", 0, 0, Integer.MAX_VALUE), number("burstSpread", .1f, 0, Float.MAX_VALUE));
            if (properties.contains("burstRate")) system.setBurstRatePerSecond(number("burstRate", 0, 0, Float.MAX_VALUE));
            return system;
        } catch (RuntimeException | Error failure) {
            try {
                system.dispose();
            } catch (RuntimeException | Error cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
    }

    /**
     * Returns independent editable settings for creating variants.
     * @return copied PropertySet
     */
    public PropertySet properties() {
        return copy(properties);
    }

    /**
     * Returns the optional loaded image without transferring ownership.
     * @return borrowed decoded pixels
     * @throws IllegalStateException if no image was loaded or the data was disposed
     */
    public TextureData textureData() {
        ensureAlive();
        if (pixels == null) throw new IllegalStateException("This particle config has no loaded image");
        return pixels;
    }

    /**
     * Rejects further factory or image access after disposal.
     */
    private void ensureAlive() {
        if (disposed) throw new IllegalStateException("Particle data has been disposed");
    }

    /**
     * Releases owned CPU pixels once, leaving cache images and existing systems
     * untouched. Do not race disposal with factories or decoded-pixel use.
     */
    public void dispose() {
        if (disposed) return;
        disposed = true;
        if (ownsPixels && pixels != null) pixels.dispose();
    }
}
