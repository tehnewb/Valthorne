package valthorne.graphics.texture;

import valthorne.asset.AssetParameters;

/**
 * Represents the parameters used to define a texture asset in the system.
 * This record holds the necessary attributes required to identify and manage
 * a texture asset through a unique name key.
 * <p>
 * The class provides an implementation of the AssetParameters interface,
 * ensuring the ability to retrieve the unique key associated with the texture.
 *
 * @param name the unique name of the texture asset, serving as its identifier
 */
public record TextureParameters(String path, String name) implements AssetParameters {

    /**
     * Creates a new instance of TextureParameters with the given path.
     * The texture name will also be set to the same value as the path.
     *
     * @param path the file path of the texture asset, which also serves as its name
     */
    public TextureParameters(String path) {
        this(path, path);
    }

    @Override
    public String key() {
        return name;
    }
}