package valthorne.graphics.texture;

import valthorne.asset.AssetParameters;


/**
 * Represents configuration parameters for loading a texture asset.
 * A texture can be sourced either from a file system path or in-memory bytes,
 * is identified by a unique name, and can optionally be vertically flipped during loading.
 * <p>
 * This record implements the {@link AssetParameters} interface, allowing it to
 * define a unique identifier for managing texture assets.
 */
public record TextureParameters(TextureSource source, String name, boolean flipVertically) implements AssetParameters {

    /**
     * Constructs a TextureParameters instance with the specified texture source, name, and flip option.
     * Validates the input arguments to ensure the source and name are not null or invalid.
     *
     * @param source         the source of the texture, must not be null. It can represent either
     *                       a file path or in-memory byte source for the texture.
     * @param name           the unique name of the texture. Must not be null or blank as it is used
     *                       as the identifier for the texture asset.
     * @param flipVertically a boolean indicating if the texture should be flipped vertically during loading.
     *                       This is typically helpful for certain graphical conventions or requirements.
     * @throws IllegalArgumentException if source is null or if name is null/blank.
     */
    public TextureParameters {
        if (source == null) throw new IllegalArgumentException("source cannot be null");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name cannot be null/blank");
    }

    /**
     * Creates a new instance of {@code TextureParameters} using a file system path as the texture source.
     * The texture is automatically assigned the path as its name and is configured to be vertically flipped during loading.
     *
     * @param path the file system path of the texture. Must not be null or blank.
     * @return a {@code TextureParameters} instance configured with the specified path as the texture source and name.
     * @throws IllegalArgumentException if the {@code path} is null or blank.
     */
    public static TextureParameters fromPath(String path) {
        return new TextureParameters(new TextureSource.PathSource(path), path, true);
    }

    /**
     * Creates a new instance of {@code TextureParameters} using a file system path as the texture source.
     * The texture is configured with the specified name and is set to be vertically flipped during loading.
     *
     * @param path the file system path of the texture. Must not be null or blank.
     * @param name the unique name of the texture. Must not be null or blank.
     * @return a {@code TextureParameters} instance configured with the specified path as the texture source
     * and the specified name.
     * @throws IllegalArgumentException if the {@code path} is null or blank, or if the {@code name} is null or blank.
     */
    public static TextureParameters fromPath(String path, String name) {
        return new TextureParameters(new TextureSource.PathSource(path), name, true);
    }

    /**
     * Creates a new instance of {@code TextureParameters} using a file system path as the texture source.
     * The texture is configured with the specified name and the specified flip vertically option during loading.
     *
     * @param path           the file system path of the texture. Must not be null or blank.
     * @param name           the unique name of the texture. Must not be null or blank.
     * @param flipVertically a boolean indicating whether the texture should be flipped vertically during loading.
     * @return a {@code TextureParameters} instance configured with the specified path as the texture source,
     * the specified name, and the specified flip vertically option.
     * @throws IllegalArgumentException if the {@code path} is null or blank, or if the {@code name} is null or blank.
     */
    public static TextureParameters fromPath(String path, String name, boolean flipVertically) {
        return new TextureParameters(new TextureSource.PathSource(path), name, flipVertically);
    }

    /**
     * Creates a new {@code TextureParameters} instance from raw, encoded image bytes.
     * The texture is configured with the specified name and is set to be vertically flipped by default.
     *
     * @param bytes the raw encoded image bytes for the texture (e.g., PNG, JPG). Must not be null or empty.
     * @param name  the unique name of the texture. Must not be null or blank.
     * @return a {@code TextureParameters} instance configured with the given byte source and name.
     * @throws IllegalArgumentException if {@code bytes} is null or empty, or if {@code name} is null or blank.
     */
    public static TextureParameters fromBytes(byte[] bytes, String name) {
        return new TextureParameters(new TextureSource.BytesSource(bytes), name, true);
    }

    /**
     * Creates a new {@code TextureParameters} instance from raw encoded image bytes.
     * The texture is configured with the specified name and the specified flip vertically option.
     *
     * @param bytes          the raw encoded image bytes for the texture (e.g., PNG, JPG). Must not be null or empty.
     * @param name           the unique name of the texture. Must not be null or blank.
     * @param flipVertically a boolean indicating whether the texture should be flipped vertically during loading.
     * @return a {@code TextureParameters} instance configured with the given byte source, name, and flip vertically option.
     * @throws IllegalArgumentException if {@code bytes} is null or empty, or if {@code name} is null or blank.
     */
    public static TextureParameters fromBytes(byte[] bytes, String name, boolean flipVertically) {
        return new TextureParameters(new TextureSource.BytesSource(bytes), name, flipVertically);
    }

    @Override
    public String key() {
        return name;
    }
}