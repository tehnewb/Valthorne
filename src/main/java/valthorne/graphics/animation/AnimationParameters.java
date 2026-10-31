package valthorne.graphics.animation;

import valthorne.asset.AssetParameters;
import valthorne.io.file.ValthorneFiles;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * Loading request for a UTF-8 PropertySet animation configuration and its linked
 * image. Path and classpath requests defer both reads to the asset worker. Image
 * paths are relative to the configuration file; absolute filesystem paths are
 * also accepted by filesystem requests. Byte requests snapshot both encoded files.
 * Only {@link #name()} supplies the shared asset-cache key.
 *
 * <pre>{@code
 * Assets.loadAsync(AnimationParameters.fromClasspath("animations/walk.properties", "walk"), AnimationData.class)
 *         .thenAccept(data -> JGL.runTask(() -> animation = data.asAnimation()));
 * }</pre>
 */
public final class AnimationParameters implements AssetParameters {
    private final String location; // Configuration path, or empty for a byte-backed request.
    private final String name; // Shared asset-cache identity.
    private final boolean classpath; // Whether relative files are classpath resources.
    private final byte[] configBytes; // Owned encoded configuration snapshot, or null for deferred reads.
    private final byte[] imageBytes; // Owned encoded linked-image snapshot, or null for deferred reads.

    /**
     * Stores a validated deferred source or defensively copied byte pair.
     *
     * @param location configuration location, empty only for byte-backed requests
     * @param name nonblank cache key
     * @param classpath whether to read classpath resources
     * @param configBytes encoded configuration, or null for deferred reads
     * @param imageBytes encoded image, or null for deferred reads
     */
    private AnimationParameters(String location, String name, boolean classpath, byte[] configBytes, byte[] imageBytes) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name cannot be blank");
        if (configBytes == null && (location == null || location.isBlank()))
            throw new IllegalArgumentException("Configuration path cannot be blank");
        if (configBytes != null && (configBytes.length == 0 || imageBytes == null || imageBytes.length == 0))
            throw new IllegalArgumentException("Configuration and image bytes cannot be empty");
        this.location = location;
        this.name = name;
        this.classpath = classpath;
        this.configBytes = configBytes == null ? null : Arrays.copyOf(configBytes, configBytes.length);
        this.imageBytes = imageBytes == null ? null : Arrays.copyOf(imageBytes, imageBytes.length);
    }

    /**
     * Defers a filesystem configuration and uses its path as the cache key.
     *
     * @param path configuration path
     * @return immutable request
     */
    public static AnimationParameters fromPath(String path) {
        return fromPath(path, path);
    }

    /**
     * Defers a filesystem configuration and its relative linked image.
     *
     * @param path configuration path
     * @param name nonblank cache key
     * @return immutable request
     */
    public static AnimationParameters fromPath(String path, String name) {
        return new AnimationParameters(path, name, false, null, null);
    }

    /**
     * Defers a classpath configuration and its relative linked image.
     *
     * @param resourcePath configuration resource relative to the classpath root
     * @param name nonblank cache key
     * @return immutable request
     */
    public static AnimationParameters fromClasspath(String resourcePath, String name) {
        return new AnimationParameters(resourcePath, name, true, null, null);
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
    public static AnimationParameters fromBytes(byte[] configBytes, byte[] imageBytes, String name) {
        if (configBytes == null) throw new IllegalArgumentException("Configuration bytes cannot be null");
        return new AnimationParameters("", name, false, configBytes, imageBytes);
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
        Path parent = Path.of(location).getParent();
        Path resolved = parent == null ? Path.of(image) : parent.resolve(image);
        resolved = resolved.normalize();
        return classpath ? ValthorneFiles.readBytes(resolved.toString().replace('\\', '/')) : Files.readAllBytes(resolved);
    }
}
