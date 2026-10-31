package valthorne.graphics.particle;

import valthorne.asset.AssetParameters;
import valthorne.graphics.texture.TextureData;
import valthorne.io.file.ValthorneFiles;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.function.IntFunction;

/**
 * Loading request for a UTF-8 PropertySet particle configuration and its optional
 * image. Path and classpath requests defer reads to the asset worker. Image
 * paths are relative to the configuration file; absolute filesystem paths are
 * also accepted by filesystem requests. Byte requests snapshot supplied encoded files.
 * Only {@link #name()} supplies the shared asset-cache key.
 * A request with an image resolver expects imageID instead of an image path.
 * The callback runs on the asset worker, must be safe on that thread, and lends
 * decoded pixels without transferring ownership. Names still identify the asset
 * across different resolvers, so use distinct names for separate cache variants.
 *
 * <pre>{@code
 * Assets.loadAsync(ParticleParameters.fromClasspath("valthorne/particles/sparks.properties", "sparks"), ParticleData.class)
 *         .thenAccept(data -> JGL.runTask(() -> particles = data.asParticleSystem(texture)));
 * }</pre>
 */
public final class ParticleParameters implements AssetParameters {
    private final String location; // Configuration path, or empty for a byte-backed request.
    private final String name; // Shared asset-cache identity.
    private final boolean classpath; // Whether relative files are classpath resources.
    private final byte[] configBytes; // Owned encoded configuration snapshot, or null for deferred reads.
    private final byte[] imageBytes; // Owned encoded linked-image snapshot, or null for deferred reads.
    private final IntFunction<TextureData> imageResolver; // Borrowed cache lookup, or null for encoded-image loading.

    /**
     * Stores a validated deferred source or defensively copied byte pair.
     *
     * @param location configuration location, empty only for byte-backed requests
     * @param name nonblank cache key
     * @param classpath whether to read classpath resources
     * @param configBytes encoded configuration, or null for deferred reads
     * @param imageBytes encoded image, or null for deferred reads
     * @param imageResolver optional caller-owned decoded-image lookup
     */
    private ParticleParameters(String location, String name, boolean classpath, byte[] configBytes, byte[] imageBytes, IntFunction<TextureData> imageResolver) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name cannot be blank");
        if (configBytes == null && (location == null || location.isBlank()))
            throw new IllegalArgumentException("Configuration path cannot be blank");
        if ((configBytes != null && configBytes.length == 0) || (imageBytes != null && imageBytes.length == 0))
            throw new IllegalArgumentException("Configuration and image bytes cannot be empty");
        if (imageResolver != null && imageBytes != null)
            throw new IllegalArgumentException("Use image bytes or an image resolver, not both");
        this.location = location;
        this.name = name;
        this.classpath = classpath;
        this.configBytes = configBytes == null ? null : Arrays.copyOf(configBytes, configBytes.length);
        this.imageBytes = imageBytes == null ? null : Arrays.copyOf(imageBytes, imageBytes.length);
        this.imageResolver = imageResolver;
    }

    /**
     * Defers a filesystem configuration and uses its path as the cache key.
     *
     * @param path configuration path
     * @return immutable request
     */
    public static ParticleParameters fromPath(String path) {
        return fromPath(path, path);
    }

    /**
     * Defers a filesystem configuration and its relative linked image.
     *
     * @param path configuration path
     * @param name nonblank cache key
     * @return immutable request
     */
    public static ParticleParameters fromPath(String path, String name) {
        return new ParticleParameters(path, name, false, null, null, null);
    }

    /**
     * Defers a classpath configuration and its relative linked image.
     *
     * @param resourcePath configuration resource relative to the classpath root
     * @param name nonblank cache key
     * @return immutable request
     */
    public static ParticleParameters fromClasspath(String resourcePath, String name) {
        return new ParticleParameters(resourcePath, name, true, null, null, null);
    }

    /**
     * Snapshots a config without image bytes. Its texture may be supplied directly
     * when constructing the runtime system; imageID may use withImageResolver.
     *
     * @param configBytes UTF-8 PropertySet text
     * @param name nonblank asset-cache key
     * @return immutable config-only request
     */
    public static ParticleParameters fromBytes(byte[] configBytes, String name) {
        if (configBytes == null) throw new IllegalArgumentException("Configuration bytes cannot be null");
        return new ParticleParameters("", name, false, configBytes, null, null);
    }

    /**
     * Snapshots a configuration and the encoded image it references. The supplied
     * image bytes satisfy the image dependency without filesystem or classpath I/O.
     *
     * @param configBytes UTF-8 PropertySet text
     * @param imageBytes encoded PNG, JPEG or other supported image
     * @param name nonblank cache key
     * @return immutable byte-backed request
     */
    public static ParticleParameters fromBytes(byte[] configBytes, byte[] imageBytes, String name) {
        if (configBytes == null) throw new IllegalArgumentException("Configuration bytes cannot be null");
        return new ParticleParameters("", name, false, configBytes, imageBytes, null);
    }

    /**
     * Snapshots configuration bytes containing imageID; no encoded image is needed.
     *
     * @param configBytes UTF-8 PropertySet text
     * @param name nonblank asset-cache key
     * @param imageResolver non-null lookup returning borrowed decoded pixels
     * @return immutable cache-backed loading request
     */
    public static ParticleParameters fromBytes(byte[] configBytes, String name, IntFunction<TextureData> imageResolver) {
        if (configBytes == null || imageResolver == null)
            throw new IllegalArgumentException("Configuration bytes and image resolver cannot be null");
        return new ParticleParameters("", name, false, configBytes, null, imageResolver);
    }

    /**
     * Creates a request using the same configuration source and a cache-image lookup.
     * The configuration must contain imageID instead of an image path. Requests
     * already containing encoded image bytes cannot also use a resolver.
     *
     * @param imageResolver non-null lookup, called on the asset loading thread
     * @return new request without changing this instance
     */
    public ParticleParameters withImageResolver(IntFunction<TextureData> imageResolver) {
        if (imageResolver == null) throw new IllegalArgumentException("Image resolver cannot be null");
        return new ParticleParameters(location, name, classpath, configBytes, imageBytes, imageResolver);
    }

    /**
     * Supplies the optional cache lookup to the trusted asset loader.
     *
     * @return borrowed resolver, or null for encoded-image loading
     */
    IntFunction<TextureData> imageResolver() {
        return imageResolver;
    }

    /**
     * Returns this request's shared cache identity.
     *
     * @return nonblank asset name
     */
    public String name() {
        return name;
    }

    @Override
    public String key() {
        return name;
    }

    /**
     * Reads the configuration on the loading thread without exposing stored bytes.
     *
     * @return encoded configuration
     * @throws IOException if a filesystem source cannot be read
     */
    byte[] readConfig() throws IOException {
        if (configBytes != null) return Arrays.copyOf(configBytes, configBytes.length);
        return classpath ? ValthorneFiles.readBytes(location) : Files.readAllBytes(Path.of(location));
    }

    /**
     * Resolves the linked image relative to its configuration's directory.
     *
     * @param image configured image reference
     * @return encoded image
     * @throws IOException if a filesystem dependency cannot be read
     */
    byte[] readImage(String image) throws IOException {
        if (imageBytes != null) return Arrays.copyOf(imageBytes, imageBytes.length);
        if (configBytes != null) throw new IllegalArgumentException("An in-memory config referencing image requires image bytes");
        Path parent = Path.of(location).getParent();
        Path resolved = parent == null ? Path.of(image) : parent.resolve(image);
        resolved = resolved.normalize();
        return classpath ? ValthorneFiles.readBytes(resolved.toString().replace('\\', '/')) : Files.readAllBytes(resolved);
    }
}
