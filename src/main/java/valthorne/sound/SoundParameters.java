package valthorne.sound;

import valthorne.asset.AssetParameters;
import valthorne.io.file.ValthorneFiles;

/**
 * Represents the parameters required to initialize or manage a sound resource.
 * This record combines a {@link SoundSource}, which specifies the source of the
 * sound data, and a human-readable name used as an identifier or key for the sound.
 * <p>
 * Instances of this class enforce immutability and validation of input parameters.
 *
 * @param source The source of the sound data, which can be from a path or in-memory bytes.
 *               Must not be null.
 * @param name   The unique name or identifier for the sound. Must not be null or blank.
 */
public record SoundParameters(SoundSource source, String name) implements AssetParameters {

    /**
     * Constructs an instance of {@code SoundParameters} with the specified sound source and name.
     * Validates that the provided parameters are not null or blank to ensure correctness.
     *
     * @param source The source of the sound data, such as a file path or in-memory bytes. Must not be null.
     * @param name   The unique name or identifier for the sound. Must not be null or blank.
     * @throws IllegalArgumentException If {@code source} is null or {@code name} is null/empty/blank.
     */
    public SoundParameters {
        if (source == null) throw new IllegalArgumentException("source cannot be null");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name cannot be null/blank");
    }

    /**
     * Creates a {@link SoundParameters} instance using a file path as the sound source.
     * This method initializes the {@link SoundSource} as a {@link SoundSource.PathSource}
     * with the provided path, and uses the same path as the name for the sound.
     *
     * @param path The path to the sound file, representing the location of the sound resource.
     *             Must not be null or blank.
     * @return A {@code SoundParameters} instance with the {@link SoundSource.PathSource}
     * initialized from the provided path, and the name set to that path.
     * @throws IllegalArgumentException If the provided path is null or blank.
     */
    public static SoundParameters fromPath(String path) {
        return new SoundParameters(new SoundSource.PathSource(path), path);
    }

    /**
     * Creates a {@link SoundParameters} instance using a file path as the sound source and a custom name.
     * This method initializes the {@link SoundSource} as a {@link SoundSource.PathSource}
     * with the provided path and sets the name explicitly.
     *
     * @param path The path to the sound file, representing the location of the sound resource.
     *             Must not be null or blank.
     * @param name A custom name or identifier for the sound. Must not be null or blank.
     * @return A {@code SoundParameters} instance with the {@link SoundSource.PathSource}
     * initialized from the provided path and the name explicitly set to the provided value.
     * @throws IllegalArgumentException If the provided path or name is null or blank.
     */
    public static SoundParameters fromPath(String path, String name) {
        return new SoundParameters(new SoundSource.PathSource(path), name);
    }

    /**
     * Creates a {@code SoundParameters} instance using raw sound data bytes and a custom name.
     * This method initializes the {@link SoundSource} as a {@link SoundSource.BytesSource}
     * with the provided byte array and sets the name explicitly.
     *
     * @param bytes A byte array representing the raw sound data. Must not be null or empty.
     * @param name  A custom name or identifier for the sound. Must not be null or blank.
     * @return A {@code SoundParameters} instance with the {@link SoundSource.BytesSource}
     * initialized from the provided byte array and the name explicitly set to the provided value.
     * @throws IllegalArgumentException If the {@code bytes} array is null or empty,
     *                                  or if {@code name} is null or blank.
     */
    public static SoundParameters fromBytes(byte[] bytes, String name) {
        return new SoundParameters(new SoundSource.BytesSource(bytes), name);
    }

    /**
     * Creates a {@code SoundParameters} instance using a classpath resource as the sound source.
     * This method loads the resource's bytes and uses the resource path as the name of the sound.
     *
     * @param resourcePath The path to the resource file, relative to the classpath root.
     *                     Must not be null or blank.
     * @return A {@code SoundParameters} instance with the sound source initialized from
     * the resource bytes and the name set to the resource path.
     * @throws IllegalArgumentException If the {@code resourcePath} is null or empty,
     *                                  or if the resource could not be loaded.
     */
    public static SoundParameters fromClasspath(String resourcePath) {
        return fromBytes(ValthorneFiles.readBytes(resourcePath), resourcePath);
    }

    /**
     * Creates a {@code SoundParameters} instance using a classpath resource as the sound source
     * and a custom name. This method loads the resource's bytes and sets the name explicitly.
     *
     * @param resourcePath The path to the resource file, relative to the classpath root.
     *                     Must not be null or blank.
     * @param name         A custom name or identifier for the sound. Must not be null or blank.
     * @return A {@code SoundParameters} instance with the sound source initialized from
     * the resource bytes and the name explicitly set to the provided value.
     * @throws IllegalArgumentException If the {@code resourcePath} is null or empty,
     *                                  or if the resource could not be loaded,
     *                                  or if {@code name} is null or blank.
     */
    public static SoundParameters fromClasspath(String resourcePath, String name) {
        return fromBytes(ValthorneFiles.readBytes(resourcePath), name);
    }

    @Override
    public String key() {
        return name;
    }
}